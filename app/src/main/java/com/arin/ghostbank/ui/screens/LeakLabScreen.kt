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
import com.arin.ghostbank.ui.UiState
import com.arin.ghostbank.ui.components.EmptyState
import com.arin.ghostbank.ui.components.GhostPage
import com.arin.ghostbank.ui.components.KeyValue
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
fun LeakLabScreen(
    state: UiState,
    onBack: () -> Unit,
    onCheck: () -> Unit
) {
    val report = state.leak

    GhostPage(
        title = "Leak Lab",
        subtitle = "what the exit really shows",
        onBack = onBack,
        onAction = {
            if (report != null) {
                StatusPill(if (report.ok) "CLEAN" else "LEAKY", if (report.ok) GhostGreen else GhostRed)
            }
        }
    ) { modifier ->
        ScrollColumn(modifier) {
            Panel(accent = GhostAmber) {
                SectionHeading("How to read this", "Two live lookups: an IP echo and a TLS fingerprint service.")
                Text(
                    "Both are real requests through your configured exit. If the kill-switch is on and no exit is configured, they fail — and that is the correct answer.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            PrimaryButton(
                text = if (state.leakRunning) "Checking…" else "Run leak check",
                enabled = !state.leakRunning,
                onClick = onCheck
            )

            if (report == null) {
                EmptyState("Never checked", "Run a check before a harvest so you know what leaves the device.")
            } else {
                val accent = if (report.ok) GhostGreen else GhostRed
                Panel(accent = accent) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Verdict", style = MaterialTheme.typography.titleMedium)
                        StatusPill(if (report.ok) "HIDDEN" else "EXPOSED", accent)
                    }
                    Text(
                        SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.US).format(Date(report.checkedAt)),
                        style = MonoTiny,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (report.error != null) {
                        Text(report.error, style = MaterialTheme.typography.bodyMedium, color = GhostRed)
                    }
                }

                Panel {
                    SectionHeading("Exit as seen from outside")
                    KeyValue("IP", report.ip, mono = true, valueColor = if (report.exitLabel == "direct") GhostAmber else GhostGreen)
                    KeyValue("Country", report.country, mono = true)
                    KeyValue("Exit used", report.exitLabel, mono = true)
                    KeyValue(
                        "TLS fingerprint",
                        report.tlsFingerprint,
                        mono = true,
                        valueColor = if (report.ok) GhostGreen else GhostAmber
                    )
                    KeyValue("DNS path", report.dnsPath)
                    KeyValue(
                        "Time zone skew",
                        "${report.timeZoneSkewMin} min",
                        mono = true,
                        valueColor = if (report.timeZoneSkewMin > 120) GhostAmber else GhostGreen
                    )
                }

                Panel(accent = MaterialTheme.colorScheme.outline) {
                    Text(
                        "Amber is physics winning: a direct exit shows your mobile IP, and a large time-zone skew against the exit country is itself a tell.",
                        style = MonoTiny,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(Modifier.fillMaxWidth()) {}
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
            Text("", style = MonoTiny, color = GhostGreen)
        }
    }
}
