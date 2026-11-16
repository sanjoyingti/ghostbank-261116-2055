package com.arin.ghostbank.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arin.ghostbank.model.Settings
import com.arin.ghostbank.model.StealthLevel
import com.arin.ghostbank.ui.UiState
import com.arin.ghostbank.ui.components.Dot
import com.arin.ghostbank.ui.components.GhostPage
import com.arin.ghostbank.ui.components.KeyValue
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.ScrollColumn
import com.arin.ghostbank.ui.components.SectionHeading
import com.arin.ghostbank.ui.theme.GhostAmber
import com.arin.ghostbank.ui.theme.GhostGreen
import com.arin.ghostbank.ui.theme.MonoTiny

@Composable
fun StealthScreen(
    state: UiState,
    onBack: () -> Unit,
    onLevel: (StealthLevel) -> Unit,
    onUpdate: ((Settings) -> Settings) -> Unit
) {
    val settings = state.settings

    GhostPage(
        title = "Stealth",
        subtitle = "level dial and velocity caps",
        onBack = onBack
    ) { modifier ->
        ScrollColumn(modifier) {
            SectionHeading("Level dial", "Ghost is proxy-mandatory: no exit configured means no traffic.")
            StealthLevel.entries.forEach { level ->
                val selected = settings.stealthLevel == level
                Panel(accent = if (selected) GhostGreen else MaterialTheme.colorScheme.outline) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.fillMaxWidth(0.8f)) {
                            Text(level.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                level.blurb,
                                style = MonoTiny,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (selected) Dot(GhostGreen, 10)
                    }
                    OutlinedButton(
                        onClick = { onLevel(level) },
                        border = BorderStroke(1.dp, if (selected) GhostGreen else MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (selected) "Active" else "Switch to ${level.label}") }
                }
            }

            SectionHeading("Behaviour", "Timing that is not a metronome.")
            Panel {
                SwitchLine("Poisson jitter", "Gaps cluster like a human instead of a cron", settings.poisson) { value ->
                    onUpdate { it.copy(poisson = value) }
                }
                SwitchLine("Diurnal window", "Slow down between 23:00 and 09:00", settings.diurnal) { value ->
                    onUpdate { it.copy(diurnal = value) }
                }
                SwitchLine("Decoy browsing", "Visit an unrelated bank before each leg", settings.decoyBrowsing) { value ->
                    onUpdate { it.copy(decoyBrowsing = value) }
                }
                SwitchLine("Ghost idle", "1-3 junk keep-alives, then a clean close", settings.ghostIdle) { value ->
                    onUpdate { it.copy(ghostIdle = value) }
                }
                SwitchLine("Floor upgrade", "Lift a rejected sub-rupee leg to the rail minimum, tagged FLOOR", settings.floorUpgrade) { value ->
                    onUpdate { it.copy(floorUpgrade = value) }
                }
                SwitchLine("Fragment transfers", "Split a pull across accounts where the amount allows", settings.fragmentTransfer) { value ->
                    onUpdate { it.copy(fragmentTransfer = value) }
                }
            }

            SectionHeading("Velocity caps", "You can lower these, not exceed them.")
            Panel {
                CapSlider(
                    label = "Max requests per minute",
                    value = settings.maxRequestsPerMinute,
                    range = 1f..60f
                ) { onUpdate { s -> s.copy(maxRequestsPerMinute = it) } }
                CapSlider(
                    label = "Max accounts per hour",
                    value = settings.maxAccountsPerHour,
                    range = 1f..120f
                ) { onUpdate { s -> s.copy(maxAccountsPerHour = it) } }
                CapSlider(
                    label = "Entropy budget (requests per run)",
                    value = settings.entropyBudget,
                    range = 20f..400f
                ) { onUpdate { s -> s.copy(entropyBudget = it) } }
            }

            SectionHeading("Traffic shape", "Padding, body chunk variation and a randomised Accept-Encoding negotiation.")
            Panel {
                SwitchLine("Request padding", "Add browser-like body chunk variance", true) { }
                SwitchLine("Session resumption", "Reuse the TLS ticket, then close deliberately", true) { }
                KeyValue("Header order", "permuted inside Chrome's valid set", mono = true)
                KeyValue("Signature headers", "none (no X-App, no version)", mono = true)
            }

            Panel(accent = GhostAmber) {
                Text(
                    "The TCP stack is Android's own. Only a proxy or Tor can shadow it — which is why Ghost level refuses to send without one.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Column(Modifier.fillMaxWidth()) {}
            Text("", style = MonoTiny, color = GhostGreen)
        }
    }
}

@Composable
private fun SwitchLine(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.fillMaxWidth(0.78f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MonoTiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CapSlider(
    label: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Int) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value.toString(), style = MonoTiny, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.toFloat().coerceIn(range),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range
        )
    }
}
