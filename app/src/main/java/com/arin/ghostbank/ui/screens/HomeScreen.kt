package com.arin.ghostbank.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arin.ghostbank.data.BankCatalog
import com.arin.ghostbank.data.Profiles
import com.arin.ghostbank.model.AccountStatus
import com.arin.ghostbank.model.LiveMode
import com.arin.ghostbank.model.RunStatus
import com.arin.ghostbank.model.StealthLevel
import com.arin.ghostbank.model.Validation
import com.arin.ghostbank.ui.UiState
import com.arin.ghostbank.ui.components.Dot
import com.arin.ghostbank.ui.components.EmptyState
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    state: UiState,
    onTransfer: () -> Unit,
    onHarvest: () -> Unit,
    onLedger: () -> Unit,
    onAccounts: () -> Unit,
    onShield: () -> Unit,
    onLeak: () -> Unit,
    onStealth: () -> Unit,
    onAudit: (String) -> Unit,
    onDismissMessage: () -> Unit
) {
    val (score, _) = Profiles.score(state.settings, state.leak, state.settings.proxies.size, state.settings.torMode)
    val ghost = state.settings.stealthLevel == StealthLevel.GHOST

    GhostPage(
        title = "GhostBank",
        subtitle = "stealth ${state.settings.stealthLevel.label} · score $score/100",
        onAction = {
            StatusPill(if (ghost) "GHOST" else "ARMED", if (ghost) GhostGreen else GhostAmber)
        }
    ) { modifier ->
        ScrollColumn(modifier) {
            if (state.message != null) {
                Panel(accent = GhostAmber) {
                    Text(state.message, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onDismissMessage) { Text("Dismiss") }
                }
            }

            Panel(accent = if (state.destination == null) GhostAmber else GhostGreen) {
                SectionHeading(
                    "Destination (fixed)",
                    if (state.destination == null) "No destination yet — nothing can be booked until you set one."
                    else "Money lands here. The bank always knows these digits."
                )
                if (state.destination == null) {
                    PrimaryButton("Set destination account", onClick = onAccounts)
                } else {
                    KeyValue("Account", state.destination.maskedNumber, mono = true)
                    KeyValue("IFSC", state.destination.ifsc, mono = true)
                    if (state.destination.holderName.isNotBlank()) KeyValue("Holder", state.destination.holderName)
                    OutlinedButton(onClick = onAccounts, modifier = Modifier.fillMaxWidth()) { Text("Edit accounts") }
                }
            }

            Panel {
                SectionHeading("Harvest balance", "Summed in paise — never rounded away.")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Mono(Validation.formatPaise(state.harvestedPaise), color = GhostGreen)
                    Text(
                        "from ${state.accounts.count { it.status == AccountStatus.SETTLED || it.status == AccountStatus.FLOOR }} settled account(s)",
                        style = MonoTiny,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                KeyValue(
                    "Registry",
                    "${state.accounts.size} account(s), ${state.pendingAccounts.size} still pending",
                    mono = true
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Transfer", modifier = Modifier.weight(1f), onClick = onTransfer)
                PrimaryButton("Harvest", modifier = Modifier.weight(1f), onClick = onHarvest)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onLedger, modifier = Modifier.weight(1f)) { Text("Ledger") }
                OutlinedButton(onClick = onShield, modifier = Modifier.weight(1f)) { Text("Trace-Shield") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onLeak, modifier = Modifier.weight(1f)) { Text("Leak Lab") }
                OutlinedButton(onClick = onStealth, modifier = Modifier.weight(1f)) { Text("Stealth") }
            }

            Panel {
                SectionHeading("Exit status", "What the bank would see right now.")
                KeyValue(
                    "Exit",
                    if (state.settings.torMode) "Tor"
                    else if (state.settings.proxies.isEmpty()) "direct (raw IP)"
                    else "${state.settings.proxies.size} proxy exits"
                )
                KeyValue("Last Leak Lab", state.leak?.let { if (it.ok) "verified ${it.ip}" else "failed" } ?: "never run")
                KeyValue("DNS", if (state.settings.doh) "DoH" else "ISP resolver")
                KeyValue("Kill-switch", if (state.settings.killSwitch) "on" else "off")
            }

            SectionHeading("Recent runs", "Every attempt keeps its raw responses.")
            if (state.runs.isEmpty()) {
                EmptyState("No runs yet", "Start a transfer or a harvest and the pipeline stages appear here.")
            } else {
                state.runs.take(6).forEach { run ->
                    Panel(
                        accent = when (run.status) {
                            RunStatus.BOOKED -> GhostGreen
                            RunStatus.FAILED, RunStatus.ABORTED -> GhostRed
                            else -> GhostAmber
                        }
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(BankCatalog.name(run.bankCode), style = MaterialTheme.typography.titleMedium)
                            StatusPill(
                                run.status.name,
                                if (run.status == RunStatus.BOOKED) GhostGreen else GhostAmber
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Dot(if (run.mode == LiveMode.LIVE) GhostGreen else GhostAmber)
                            Mono(Validation.formatPaise(run.amountPaise))
                            Text(run.mode.name, style = MonoTiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                SimpleDateFormat("dd MMM HH:mm", Locale.US).format(Date(run.createdAt)),
                                style = MonoTiny,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = { onAudit(run.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Trace-Audit")
                        }
                    }
                }
            }

            Panel(accent = MaterialTheme.colorScheme.outline) {
                Text(
                    "On-device only: encrypted ledger, no analytics, no cloud, no third-party SDK.",
                    style = MonoTiny,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
