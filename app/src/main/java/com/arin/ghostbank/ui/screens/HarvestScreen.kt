package com.arin.ghostbank.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arin.ghostbank.model.AccountStatus
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

@Composable
fun HarvestScreen(
    state: UiState,
    onBack: () -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onStart: () -> Unit,
    onAccounts: () -> Unit,
    onAudit: (String) -> Unit,
    onDismissMessage: () -> Unit
) {
    GhostPage(title = "Harvest", subtitle = "exact one paisa per account, once", onBack = onBack) { modifier ->
        ScrollColumn(modifier) {
            if (state.message != null) {
                Panel(accent = GhostAmber) {
                    Text(state.message, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onDismissMessage) { Text("Dismiss") }
                }
            }

            Panel(accent = GhostGreen) {
                SectionHeading("Paise harvested", "Summed as paise — 25 accounts is 25 paise, never rounded away")
                Mono(Validation.formatPaise(state.harvestedPaise), color = GhostGreen)
                KeyValue("Settled", state.accounts.count { it.status == AccountStatus.SETTLED }.toString(), mono = true)
                KeyValue("Floored to rail minimum", state.accounts.count { it.status == AccountStatus.FLOOR }.toString(), mono = true)
                KeyValue("Rejected (retryable)", state.accounts.count { it.status == AccountStatus.REJECTED }.toString(), mono = true)
                Text(
                    "The registry is written before a request leaves, so a crash cannot double-hit an account.",
                    style = MonoTiny,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.accounts.isEmpty()) {
                EmptyState("Registry empty", "Add source accounts first — each one gets a single paisa.")
                PrimaryButton("Go to accounts", onClick = onAccounts)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onSelectAll, modifier = Modifier.weight(1f)) { Text("All pending") }
                    OutlinedButton(onClick = onClearSelection, modifier = Modifier.weight(1f)) { Text("Clear") }
                }

                PrimaryButton(
                    text = if (state.running) "Harvesting…" else "Start harvest (${state.harvestSelection.size.let { if (it == 0) state.pendingAccounts.size else it }} account(s))",
                    enabled = !state.running && state.pendingAccounts.isNotEmpty()
                ) { onStart() }

                state.accounts.forEach { account ->
                    val selected = account.id in state.harvestSelection ||
                        (state.harvestSelection.isEmpty() && (account.status == AccountStatus.PENDING || account.status == AccountStatus.REJECTED))
                    Panel(
                        accent = when (account.status) {
                            AccountStatus.SETTLED -> GhostGreen
                            AccountStatus.FLOOR -> GhostAmber
                            AccountStatus.REJECTED -> GhostRed
                            AccountStatus.SENT -> GhostAmber
                            else -> MaterialTheme.colorScheme.outline
                        }
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Dot(
                                    if (account.status == AccountStatus.SETTLED || account.status == AccountStatus.FLOOR) GhostGreen
                                    else if (selected) GhostAmber else MaterialTheme.colorScheme.outline
                                )
                                Column {
                                    Text(account.label, style = MaterialTheme.typography.titleMedium)
                                    Mono(account.maskedNumber, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            StatusPill(
                                account.status.name,
                                when (account.status) {
                                    AccountStatus.SETTLED -> GhostGreen
                                    AccountStatus.FLOOR, AccountStatus.SENT -> GhostAmber
                                    AccountStatus.REJECTED -> GhostRed
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        KeyValue("Bank", account.bankName)
                        KeyValue("Paise", account.amountPaise.toString(), mono = true)
                        account.utr?.let { KeyValue("UTR", it, mono = true, valueColor = GhostGreen) }
                        if (account.status == AccountStatus.PENDING || account.status == AccountStatus.REJECTED) {
                            OutlinedButton(onClick = { onToggle(account.id) }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (account.id in state.harvestSelection) "Selected — tap to drop" else "Tap to select")
                            }
                        }
                    }
                }

                state.activeRun?.takeIf { it.kind == com.arin.ghostbank.model.RunKind.HARVEST }?.let { run ->
                    Panel(accent = GhostAmber) {
                        SectionHeading("Harvest run", "score ${run.stealthScore}/100")
                        run.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        OutlinedButton(onClick = { onAudit(run.id) }, modifier = Modifier.fillMaxWidth()) { Text("Trace-Audit") }
                    }
                }

                if (state.rawLog.isNotEmpty()) {
                    Panel {
                        SectionHeading("Raw attempts", "Every try is logged, none is silent")
                        state.rawLog.take(10).forEach { line ->
                            Mono(line, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
