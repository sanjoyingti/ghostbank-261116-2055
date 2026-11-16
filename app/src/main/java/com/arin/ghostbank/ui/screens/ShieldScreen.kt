package com.arin.ghostbank.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import com.arin.ghostbank.model.ProxyScheme
import com.arin.ghostbank.model.Settings
import com.arin.ghostbank.model.Validation
import com.arin.ghostbank.ui.UiState
import com.arin.ghostbank.ui.components.EmptyState
import com.arin.ghostbank.ui.components.Field
import com.arin.ghostbank.ui.components.GhostPage
import com.arin.ghostbank.ui.components.KeyValue
import com.arin.ghostbank.ui.components.Mono
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.PrimaryButton
import com.arin.ghostbank.ui.components.ScrollColumn
import com.arin.ghostbank.ui.components.SectionHeading
import com.arin.ghostbank.ui.theme.GhostAmber
import com.arin.ghostbank.ui.theme.GhostGreen
import com.arin.ghostbank.ui.theme.GhostRed
import com.arin.ghostbank.ui.theme.MonoTiny

@Composable
fun ShieldScreen(
    state: UiState,
    onBack: () -> Unit,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onAddProxy: (ProxyScheme, String, Int, String, String) -> Boolean,
    onRemoveProxy: (Int) -> Unit,
    onAddVirtualAccount: (String, String, String, String) -> Boolean,
    onRemoveVirtualAccount: (String) -> Unit,
    onLeak: () -> Unit,
    onPickBank: () -> Unit
) {
    var scheme by remember { mutableStateOf(ProxyScheme.SOCKS5) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var timeZone by remember { mutableStateOf("") }
    var showProxyErrors by remember { mutableStateOf(false) }

    var vaNumber by remember { mutableStateOf("") }
    var vaIfsc by remember { mutableStateOf("") }
    var vaLabel by remember { mutableStateOf("") }
    var showVaErrors by remember { mutableStateOf(false) }

    val hostError = Validation.proxyHost(host)
    val portError = Validation.proxyPort(port)
    val vaNumberError = Validation.accountNumber(vaNumber)
    val vaIfscError = Validation.ifsc(vaIfsc)
    val settings = state.settings
    val bank = state.selectedBankCode?.let { BankCatalog.byCode(it) }

    GhostPage(
        title = "Trace-Shield",
        subtitle = "unlinkability, not magic",
        onBack = onBack,
        onAction = {
            OutlinedButton(onClick = onLeak) { Text("Leak Lab") }
        }
    ) { modifier ->
        ScrollColumn(modifier) {
            Panel(accent = GhostAmber) {
                SectionHeading("The honest part", "What cannot be hidden, and why.")
                Text(
                    "The destination bank always knows your account number — it is in their own ledger. The source bank always knows the debited account. What Trace-Shield removes is the link between the two and the IP that carried them.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            SectionHeading("Tunnel", "Ghost level is proxy-mandatory: no exit means no traffic.")
            Panel {
                SwitchRow("Tor mode", "Route through a local Tor SOCKS port 9050", settings.torMode) { value ->
                    onUpdate { it.copy(torMode = value) }
                }
                SwitchRow("DNS over HTTPS", "Keeps lookups inside the tunnel", settings.doh) { value ->
                    onUpdate { it.copy(doh = value) }
                }
                SwitchRow("Kill-switch", "A dropped exit abandons the request instead of leaking", settings.killSwitch) { value ->
                    onUpdate { it.copy(killSwitch = value) }
                }
                SwitchRow("Geo-matched exit", "Re-pick an exit whose country and timezone do not match", settings.geoMatch) { value ->
                    onUpdate { it.copy(geoMatch = value) }
                }
                KeyValue("Current exit", if (settings.torMode) "Tor" else if (settings.proxies.isEmpty()) "direct" else "${settings.proxies.size} in pool")
            }

            SectionHeading("Exit pool", "Rotated per request. SOCKS5 or HTTPS only.")
            if (settings.proxies.isEmpty()) {
                EmptyState("No exit configured", "With Ghost level and the kill-switch on, no exit means no traffic at all.")
            }
            settings.proxies.forEachIndexed { index, proxy ->
                Panel {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Mono("${proxy.scheme.name} ${proxy.host}:${proxy.port}")
                        IconButton(onClick = { onRemoveProxy(index) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove exit")
                        }
                    }
                    KeyValue("Country", proxy.country.ifBlank { "unset" })
                    KeyValue("Time zone", proxy.timeZone.ifBlank { "unset" })
                }
            }
            Panel {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = scheme == ProxyScheme.SOCKS5,
                        onClick = { scheme = ProxyScheme.SOCKS5 },
                        label = { Text("SOCKS5") }
                    )
                    FilterChip(
                        selected = scheme == ProxyScheme.HTTPS,
                        onClick = { scheme = ProxyScheme.HTTPS },
                        label = { Text("HTTPS") }
                    )
                }
                Field("Host", host, { host = it })
                if (showProxyErrors && hostError != null) Text(hostError, style = MonoTiny, color = GhostRed)
                Field("Port", port, { port = it.filter(Char::isDigit).take(5) }, KeyboardType.Number)
                if (showProxyErrors && portError != null) Text(portError, style = MonoTiny, color = GhostRed)
                Field("Exit country (ISO-2, optional)", country, { country = it.uppercase().take(2) })
                Field("Exit time zone (optional)", timeZone, { timeZone = it.take(40) }, hint = "e.g. Asia/Kolkata")
                PrimaryButton("Add exit") {
                    showProxyErrors = true
                    if (hostError == null && portError == null) {
                        val ok = onAddProxy(scheme, host, port.toIntOrNull() ?: 0, country, timeZone)
                        if (ok) {
                            host = ""
                            port = ""
                            country = ""
                            timeZone = ""
                            showProxyErrors = false
                        }
                    }
                }
            }

            SectionHeading("Masking virtual accounts", "The credit leg uses a VA that forwards to you, so real digits stay off the rail.")
            if (settings.virtualAccounts.isEmpty()) {
                EmptyState("No virtual account", "Without a VA the real destination digits travel on the rail.")
            }
            settings.virtualAccounts.forEach { va ->
                Panel {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(va.label, style = MaterialTheme.typography.titleMedium)
                            Mono(va.number, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onRemoveVirtualAccount(va.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove virtual account")
                        }
                    }
                    KeyValue("Bank", BankCatalog.name(va.bankCode))
                    KeyValue("IFSC", va.ifsc, mono = true)
                }
            }
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(bank?.name ?: "No bank picked", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = onPickBank) { Text(if (bank == null) "Pick bank" else "Change") }
                }
                Field("Virtual account number", vaNumber, { vaNumber = it.filter(Char::isDigit).take(18) }, KeyboardType.Number)
                if (showVaErrors && vaNumberError != null) Text(vaNumberError, style = MonoTiny, color = GhostRed)
                Field("IFSC", vaIfsc, { vaIfsc = it.uppercase().take(11) })
                if (showVaErrors && vaIfscError != null) Text(vaIfscError, style = MonoTiny, color = GhostRed)
                Field("Label (optional)", vaLabel, { vaLabel = it })
                PrimaryButton("Add virtual account", enabled = bank != null) {
                    showVaErrors = true
                    if (bank != null && vaNumberError == null && vaIfscError == null) {
                        val ok = onAddVirtualAccount(bank.code, vaNumber, vaIfsc, vaLabel)
                        if (ok) {
                            vaNumber = ""
                            vaIfsc = ""
                            vaLabel = ""
                            showVaErrors = false
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
            Row(Modifier.fillMaxWidth()) { OutlinedButton(onClick = onLeak, modifier = Modifier.fillMaxWidth()) { Text("Open Leak Lab") } }
            Column(Modifier.fillMaxWidth()) {}
            Text("", style = MonoTiny, color = GhostGreen)
        }
    }
}

@Composable
private fun SwitchRow(
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
