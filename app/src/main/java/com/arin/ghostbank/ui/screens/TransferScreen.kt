package com.arin.ghostbank.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.arin.ghostbank.data.BankCatalog
import com.arin.ghostbank.model.RunStatus
import com.arin.ghostbank.model.StageState
import com.arin.ghostbank.model.Validation
import com.arin.ghostbank.ui.UiState
import com.arin.ghostbank.ui.components.Dot
import com.arin.ghostbank.ui.components.EmptyState
import com.arin.ghostbank.ui.components.Field
import com.arin.ghostbank.ui.components.GhostPage
import com.arin.ghostbank.ui.components.KeyValue
import com.arin.ghostbank.ui.components.Mono
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.PrimaryButton
import com.arin.ghostbank.ui.components.ScrollColumn
import com.arin.ghostbank.ui.components.SectionHeading
import com.arin.ghostbank.ui.components.StatusPill
import com.arin.ghostbank.ui.theme.GhostAmber
import com.arin.ghostbank.ui.theme.GhostGreen
import com.arin.ghostbank.ui.theme.GhostRed
import com.arin.ghostbank.ui.theme.MonoTiny

private val presets = listOf("0.01", "1.00", "10.00", "100.00")

@Composable
fun TransferScreen(
    state: UiState,
    onBack: () -> Unit,
    onPickBank: () -> Unit,
    onSelectSource: (String?) -> Unit,
    onStart: (Long, String, String?) -> Unit,
    onReset: () -> Unit,
    onAudit: (String) -> Unit,
    onScore: (String) -> Unit,
    onDismissMessage: () -> Unit
) {
    var amount by remember { mutableStateOf("1.00") }
    var showErrors by remember { mutableStateOf(false) }
    val amountError = Validation.amountRupees(amount)
    val bank = state.selectedBankCode?.let { BankCatalog.byCode(it) }
    val paise = Validation.toPaise(amount)
    val run = state.activeRun
    val finished = run != null && run.status != RunStatus.RUNNING

    GhostPage(title = "Transfer", subtitle = "IMPS P2A · debit leg then credit leg", onBack = onBack) { modifier ->
        ScrollColumn(modifier) {
            if (state.message != null) {
                Panel(accent = GhostAmber) {
                    Text(state.message, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onDismissMessage) { Text("Dismiss") }
                }
            }

            if (state.destination == null) {
                Panel(accent = GhostAmber) {
                    SectionHeading("No destination yet", "Set your account number and IFSC in Accounts first.")
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to home") }
                }
            } else {
                Panel(accent = GhostGreen) {
                    SectionHeading("Destination (fixed)", "Money lands here, narrated PAYMENT")
                    KeyValue("Account", state.destination.maskedNumber, mono = true)
                    KeyValue("IFSC", state.destination.ifsc, mono = true)
                }
            }

            Panel(accent = if (bank == null) GhostAmber else MaterialTheme.colorScheme.outline) {
                SectionHeading(
                    "Source bank",
                    bank?.let { "cap ${Validation.formatPaise(it.capPaise)} · floor ${Validation.formatPaise(it.minPaise)}" }
                        ?: "Pick the bank the money leaves from"
                )
                if (bank != null) {
                    Text(bank.quirk, style = MonoTiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = onPickBank, modifier = Modifier.fillMaxWidth()) {
                    Text(if (bank == null) "Pick bank" else "Change bank")
                }
            }

            Panel {
                SectionHeading("Amount", "One paisa is allowed — the cap is the bank's, not ours")
                Field("Rupees", amount, { amount = it.filter { c -> c.isDigit() || c == '.' }.take(10) }, KeyboardType.Decimal)
                if (showErrors && amountError != null) {
                    Text(amountError, style = MonoTiny, color = GhostRed)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { preset ->
                        OutlinedButton(onClick = { amount = preset }, modifier = Modifier.weight(1f)) {
                            Text("₹$preset", style = MonoTiny)
                        }
                    }
                }
            }

            Panel {
                SectionHeading("Source profile", "The bank's own P2A route, or a saved remitter")
                OutlinedButton(
                    onClick = { onSelectSource(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.transferSourceId == null) "● Bank P2A route" else "Bank P2A route")
                }
                if (state.accounts.isEmpty()) {
                    Text(
                        "No saved remitter profile — the bank's own route is used.",
                        style = MonoTiny,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    state.accounts.forEach { account ->
                        OutlinedButton(
                            onClick = { onSelectSource(account.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                (if (state.transferSourceId == account.id) "● " else "") +
                                    "${account.label} · ${account.maskedNumber}"
                            )
                        }
                    }
                }
            }

            PrimaryButton(
                text = if (state.running) "Pipeline running…" else "Start pipeline",
                enabled = !state.running && bank != null && state.destination != null && amountError == null
            ) {
                showErrors = true
                if (amountError == null && bank != null) onStart(paise, bank.code, state.transferSourceId)
            }

            run?.let { active ->
                Panel(
                    accent = when (active.status) {
                        RunStatus.BOOKED -> GhostGreen
                        RunStatus.FAILED, RunStatus.ABORTED -> GhostRed
                        else -> GhostAmber
                    }
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SectionHeading("Pipeline", "key ${active.idempotencyKey.take(10)}…")
                        StatusPill(active.status.name, if (active.status == RunStatus.BOOKED) GhostGreen else GhostAmber)
                    }
                    active.stages.forEach { record ->
                        val color = when (record.state) {
                            StageState.OK -> GhostGreen
                            StageState.SIMULATED -> GhostAmber
                            StageState.FAILED -> GhostRed
                            StageState.RUNNING -> GhostGreen
                            else -> MaterialTheme.colorScheme.outline
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Dot(color)
                                Text(record.stage.label, style = MaterialTheme.typography.bodyMedium)
                                Text(record.state.name, style = MonoTiny, color = color)
                            }
                            Text(
                                record.durationMs?.let { "${it}ms" } ?: "—",
                                style = MonoTiny,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        record.note?.let { Text(it, style = MonoTiny, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    if (active.utr != null) KeyValue("UTR", active.utr, mono = true, valueColor = GhostGreen)
                    active.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = GhostAmber) }
                    if (finished) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { onAudit(active.id) }, modifier = Modifier.weight(1f)) { Text("Trace-Audit") }
                            OutlinedButton(onClick = { onScore(active.id) }, modifier = Modifier.weight(1f)) { Text("Stealth score") }
                        }
                        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("Clear pipeline") }
                    }
                }

                if (state.rawLog.isNotEmpty()) {
                    Panel {
                        SectionHeading("Raw attempts", "Logged so a stuck run is visible")
                        state.rawLog.take(12).forEach { line ->
                            Mono(line, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (run == null) {
                EmptyState("Nothing running", "The stages, their ticks and timings appear here once you start.")
            }
        }
    }
}
