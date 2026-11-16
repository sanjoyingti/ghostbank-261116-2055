package com.arin.ghostbank.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arin.ghostbank.model.LiveMode
import com.arin.ghostbank.model.RunStatus
import com.arin.ghostbank.model.Validation
import com.arin.ghostbank.ui.UiState
import com.arin.ghostbank.ui.components.EmptyState
import com.arin.ghostbank.ui.components.GhostPage
import com.arin.ghostbank.ui.components.KeyValue
import com.arin.ghostbank.ui.components.Mono
import com.arin.ghostbank.ui.components.Panel
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
fun AuditScreen(
    state: UiState,
    runId: String,
    onBack: () -> Unit,
    onScore: (String) -> Unit
) {
    val run = state.runs.firstOrNull { it.id == runId } ?: state.activeRun?.takeIf { it.id == runId }

    GhostPage(title = "Trace-Audit", subtitle = "green where hidden, amber where physics wins", onBack = onBack) { modifier ->
        ScrollColumn(modifier) {
            if (run == null) {
                EmptyState("Run not found", "The record may have been trimmed from the last 120 runs.")
            } else {
                val audit = run.audit
                Panel(accent = if (run.mode == LiveMode.LIVE) GhostGreen else GhostAmber) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(run.bankName, style = MaterialTheme.typography.titleMedium)
                        StatusPill(run.status.name, if (run.status == RunStatus.BOOKED) GhostGreen else GhostAmber)
                    }
                    Text(
                        SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.US).format(Date(run.createdAt)),
                        style = MonoTiny,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    KeyValue("Amount", Validation.formatPaise(run.amountPaise), mono = true)
                    KeyValue("Mode", run.mode.name, valueColor = if (run.mode == LiveMode.LIVE) GhostGreen else GhostAmber)
                    KeyValue("Stealth score", "${run.stealthScore}/100", valueColor = if (run.stealthScore >= 60) GhostGreen else GhostAmber)
                    OutlinedButton(onClick = { onScore(run.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Why this score")
                    }
                }

                Panel(accent = if (audit.exitLabel == "direct") GhostAmber else GhostGreen) {
                    SectionHeading("Transport", "What the bank could see on the wire.")
                    KeyValue("IP used", audit.ipUsed, mono = true, valueColor = if (audit.exitLabel == "direct") GhostAmber else GhostGreen)
                    KeyValue("Exit", audit.exitLabel, mono = true)
                    KeyValue("Cookies persisted", audit.cookiesPersisted.toString(), mono = true)
                    KeyValue("Time zone skew", "${audit.timeZoneSkewMin} min", mono = true)
                }

                Panel(accent = GhostAmber) {
                    SectionHeading("Digits", "The destination bank always knows the account number.")
                    KeyValue("Exposed on rail", audit.digitsExposed)
                }

                Panel {
                    SectionHeading("UTR chain", "Only an issuer's final SUCCESS produces one.")
                    if (audit.utrChain.isEmpty()) {
                        Mono("no UTR issued", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        audit.utrChain.forEach { utr -> Mono(utr, color = GhostGreen) }
                    }
                }

                if (audit.notes.isNotEmpty()) {
                    Panel(accent = MaterialTheme.colorScheme.outline) {
                        SectionHeading("Notes")
                        audit.notes.forEach { note ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("•", style = MonoTiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(note, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                if (run.scoreReasons.isNotEmpty()) {
                    Panel {
                        SectionHeading("Score reasons")
                        run.scoreReasons.forEach { reason ->
                            Text("• $reason", style = MonoTiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (run.error != null) {
                    Panel(accent = GhostRed) {
                        Text(run.error, style = MaterialTheme.typography.bodyMedium, color = GhostRed)
                    }
                }
            }
            Column(Modifier.fillMaxWidth()) {}
        }
    }
}
