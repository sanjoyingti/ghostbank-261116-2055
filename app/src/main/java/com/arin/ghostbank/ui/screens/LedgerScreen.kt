package com.arin.ghostbank.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arin.ghostbank.data.Exporter
import com.arin.ghostbank.model.LedgerEntry
import com.arin.ghostbank.model.LegType
import com.arin.ghostbank.model.LiveMode
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LedgerScreen(
    state: UiState,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onAudit: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val grouped = state.ledger.groupBy { it.runId }.toList()
        .sortedByDescending { pair -> pair.second.maxOf { it.at } }

    GhostPage(
        title = "Ledger",
        subtitle = "${state.ledger.size} rows · on-device and encrypted",
        onBack = onBack,
        onAction = {
            OutlinedButton(onClick = {
                scope.launch {
                    val uri = Exporter.ledgerCsv(context, state.ledger)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Export ledger"))
                }
            }) { Text("CSV") }
        }
    ) { modifier ->
        ScrollColumn(modifier) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) { Text("Clear ledger") }
            }

            if (grouped.isEmpty()) {
                EmptyState("Ledger empty", "A booked run writes a debit row and a credit row here, side by side.")
            } else {
                grouped.forEach { (runId, entries) ->
                    val debit = entries.firstOrNull { it.leg == LegType.DEBIT }
                    val credit = entries.firstOrNull { it.leg == LegType.CREDIT }
                    val head = debit ?: credit
                    if (head != null) {
                        Panel(accent = if (head.mode == LiveMode.LIVE) GhostGreen else GhostAmber) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(head.bankName, style = MaterialTheme.typography.titleMedium)
                                StatusPill(head.mode.name, if (head.mode == LiveMode.LIVE) GhostGreen else GhostAmber)
                            }
                            Text(
                                SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US).format(Date(head.at)),
                                style = MonoTiny,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                LedgerLeg("DEBIT", debit, Modifier.weight(1f))
                                LedgerLeg("CREDIT", credit, Modifier.weight(1f))
                            }
                            head.utr?.let { KeyValue("UTR", it, mono = true, valueColor = GhostGreen) }
                            OutlinedButton(onClick = { onAudit(runId) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Trace-Audit")
                            }
                        }
                    }
                }
            }

            Panel(accent = MaterialTheme.colorScheme.outline) {
                Text(
                    "LIVE means the bank server answered. SIMULATED means it did not and the leg ran locally.",
                    style = MonoTiny,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LedgerLeg(label: String, entry: LedgerEntry?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionHeading(label)
        if (entry == null) {
            Text("—", style = MonoTiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Mono(
                Validation.formatPaise(entry.amountPaise),
                color = when (entry.status) {
                    "REVERSED" -> GhostRed
                    "RECEIVED", "BOOKED", "SETTLED" -> GhostGreen
                    else -> GhostAmber
                }
            )
            Text(entry.status, style = MonoTiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(entry.note, style = MonoTiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
