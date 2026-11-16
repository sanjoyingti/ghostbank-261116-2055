package com.arin.ghostbank.model

import kotlinx.serialization.Serializable

enum class StealthLevel(val label: String, val blurb: String) {
    STANDARD("Standard", "Chrome-like headers, jittered pacing, no exit required."),
    DEEP("Deep", "Everything in Standard plus decoy browsing, ghost idle and tighter velocity caps."),
    GHOST("Ghost", "Proxy-mandatory. No exit configured means no traffic at all.")
}

enum class ProxyScheme { SOCKS5, HTTPS }

enum class LiveMode { LIVE, SIMULATED }

enum class LegType { DEBIT, CREDIT }

enum class RunKind { TRANSFER, HARVEST }

enum class RunStatus { RUNNING, BOOKED, FAILED, PAUSED, ABORTED }

enum class AccountStatus { PENDING, SENT, SETTLED, FLOOR, REJECTED }

enum class StageState { PENDING, RUNNING, OK, SIMULATED, FAILED, SKIPPED }

enum class Stage(val label: String, val detail: String) {
    CANARY("Canary probe", "Innocuous GET first — a blocked run never starts."),
    INITIATED("Initiated", "Idempotency key minted and reused on every retry."),
    FINGERPRINT("Fingerprint", "Frozen ghost profile bound to this leg."),
    RESOLVE_PAYEE("Resolve payee", "Destination handle resolved against the rail."),
    DEBIT_LEG("Debit leg", "Source account instructed through IMPS P2A."),
    CREDIT_LEG("Credit leg", "Instant credit to the masked destination handle."),
    SETTLE("Settle", "Polling the reference until the bank returns a final status."),
    BOOKED("Booked", "UTR issued and written to the encrypted ledger.")
}

@Serializable
data class Bank(
    val code: String,
    val name: String,
    val baseUrl: String,
    val capPaise: Long,
    val minPaise: Long,
    val quirk: String,
    val rail: String = "IMPS P2A"
)

@Serializable
data class Destination(
    val number: String,
    val ifsc: String,
    val holderName: String = ""
) {
    val maskedNumber: String
        get() = if (number.length > 4) "XXXX" + number.takeLast(4) else number
}

@Serializable
data class HarvestAccount(
    val id: String,
    val bankCode: String,
    val bankName: String,
    val number: String,
    val ifsc: String,
    val label: String,
    val fingerprint: String,
    val status: AccountStatus = AccountStatus.PENDING,
    val amountPaise: Long = 1,
    val utr: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val maskedNumber: String
        get() = if (number.length > 4) "XXXX" + number.takeLast(4) else number
}

@Serializable
data class ProxyConfig(
    val scheme: ProxyScheme,
    val host: String,
    val port: Int,
    val country: String = "",
    val timeZone: String = ""
)

@Serializable
data class VirtualAccount(
    val id: String,
    val bankCode: String,
    val number: String,
    val ifsc: String,
    val label: String
)

@Serializable
data class StageRecord(
    val stage: Stage,
    val state: StageState,
    val startedAt: Long,
    val endedAt: Long? = null,
    val raw: String? = null,
    val note: String? = null
) {
    val durationMs: Long?
        get() = endedAt?.let { it - startedAt }
}

@Serializable
data class Fragment(
    val amountPaise: Long,
    val handle: String
)

@Serializable
data class TraceAudit(
    val ipUsed: String = "—",
    val exitLabel: String = "direct",
    val cookiesPersisted: Int = 0,
    val digitsExposed: String = "masked",
    val utrChain: List<String> = emptyList(),
    val timeZoneSkewMin: Int = 0,
    val notes: List<String> = emptyList()
)

@Serializable
data class LedgerEntry(
    val id: String,
    val runId: String,
    val at: Long,
    val bankName: String,
    val amountPaise: Long,
    val utr: String?,
    val status: String,
    val mode: LiveMode,
    val leg: LegType,
    val note: String = ""
)

@Serializable
data class LeakReport(
    val checkedAt: Long = System.currentTimeMillis(),
    val ip: String = "—",
    val country: String = "—",
    val tlsFingerprint: String = "—",
    val dnsPath: String = "—",
    val timeZoneSkewMin: Int = 0,
    val exitLabel: String = "direct",
    val ok: Boolean = false,
    val error: String? = null
)

@Serializable
data class Run(
    val id: String,
    val idempotencyKey: String,
    val kind: RunKind,
    val bankCode: String,
    val bankName: String,
    val amountPaise: Long,
    val sourceAccountId: String? = null,
    val sourceLabel: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val status: RunStatus = RunStatus.RUNNING,
    val mode: LiveMode = LiveMode.SIMULATED,
    val stages: List<StageRecord> = emptyList(),
    val fragments: List<Fragment> = emptyList(),
    val stealthScore: Int = 0,
    val scoreReasons: List<String> = emptyList(),
    val audit: TraceAudit = TraceAudit(),
    val error: String? = null,
    val utr: String? = null
)

@Serializable
data class Settings(
    val onboarded: Boolean = false,
    val stealthLevel: StealthLevel = StealthLevel.STANDARD,
    val torMode: Boolean = false,
    val doh: Boolean = true,
    val killSwitch: Boolean = true,
    val geoMatch: Boolean = true,
    val decoyBrowsing: Boolean = true,
    val ghostIdle: Boolean = true,
    val diurnal: Boolean = false,
    val poisson: Boolean = true,
    val fragmentTransfer: Boolean = false,
    val floorUpgrade: Boolean = true,
    val maxRequestsPerMinute: Int = 30,
    val maxAccountsPerHour: Int = 40,
    val entropyBudget: Int = 240,
    val proxies: List<ProxyConfig> = emptyList(),
    val virtualAccounts: List<VirtualAccount> = emptyList()
)
