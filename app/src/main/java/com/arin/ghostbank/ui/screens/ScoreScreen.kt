package com.arin.ghostbank.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arin.ghostbank.ui.UiState
import com.arin.ghostbank.ui.components.EmptyState
import com.arin.ghostbank.ui.components.GhostPage
import com.arin.ghostbank.ui.components.Mono
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.ScrollColumn
import com.arin.ghostbank.ui.components.SectionHeading
import com.arin.ghostbank.ui.theme.GhostAmber
import com.arin.ghostbank.ui.theme.GhostGreen
import com.arin.ghostbank.ui.theme.GhostRed
import com.arin.ghostbank.ui.theme.MonoTiny

@Composable
fun ScoreScreen(
    state: UiState,
    runId: String,
    onBack: () -> Unit
) {
    val run = state.runs.firstOrNull { it.id == runId } ?: state.activeRun?.takeIf { it.id == runId }
    val score = run?.stealthScore ?: 0
    val color = when {
        score >= 70 -> GhostGreen
        score >= 40 -> GhostAmber
        else -> GhostRed
    }

    GhostPage(title = "Stealth score", subtitle = "every point explained", onBack = onBack) { modifier ->
        ScrollColumn(modifier) {
            if (run == null) {
                EmptyState("Run not found", "The record may have been trimmed from the last 120 runs.")
            } else {
                Panel(accent = color) {
                    Mono("$score / 100", color = color)
                    LinearProgressIndicator(
                        progress = { score / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = color
                    )
                    Text(
                        when {
                            score >= 70 -> "Hard to fingerprint: the transport is shadowed and the pacing is human."
                            score >= 40 -> "Partly shadowed. An exit, DoH and the kill-switch would lift this."
                            else -> "Exposed. The raw IP carries the traffic and nothing shadows the stack."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                SectionHeading("Reasons", "Positive and negative, in the order they were weighed.")
                Panel {
                    if (run.scoreReasons.isEmpty()) {
                        Text("No reasons recorded.", style = MonoTiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    run.scoreReasons.forEach { reason ->
                        Text("• $reason", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Panel(accent = MaterialTheme.colorScheme.outline) {
                    Text(
                        "Stealth score measures the transport, not the money. It never implies the bank cannot see the account number.",
                        style = MonoTiny,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(Modifier.fillMaxWidth()) {}
        }
    }
}
