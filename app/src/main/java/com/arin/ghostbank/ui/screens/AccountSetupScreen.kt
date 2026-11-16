package com.arin.ghostbank.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.arin.ghostbank.model.AccountStatus
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
import com.arin.ghostbank.ui.components.StatusPill
import com.arin.ghostbank.ui.theme.GhostAmber
import com.arin.ghostbank.ui.theme.GhostGreen
import com.arin.ghostbank.ui.theme.GhostRed
import com.arin.ghostbank.ui.theme.MonoTiny

@Composable
fun AccountSetupScreen(
    state: UiState,
    onBack: () -> Unit,
    onPickBank: () -> Unit,
    onSaveDestination: (String, String, String) -> Boolean,
    onAddAccount: (String?, String, String, String) -> Boolean,
    onRemoveAccount: (String) -> Unit,
    onDismissMessage: () -> Unit
) {
    var destNumber by remember { mutableStateOf(state.destination?.number.orEmpty()) }
    var destIfsc by remember { mutableStateOf(state.destination?.ifsc.orEmpty()) }
    var destHolder by remember { mutableStateOf(state.destination?.holderName.orEmpty()) }
    var showDestErrors by remember { mutableStateOf(false) }

    var sourceNumber by remember { mutableStateOf("") }
    var sourceIfsc by remember { mutableStateOf("") }
    var sourceLabel by remember { mutableStateOf("") }
    var showSourceErrors by remember { mutableStateOf(false) }

    val destNumberError = Validation.accountNumber(destNumber)
    val destIfscError = Validation.ifsc(destIfsc)
    val sourceNumberError = Validation.accountNumber(sourceNumber)
    val sourceIfscError = Validation.ifsc(sourceIfsc)
    val bank = state.selectedBankCode?.let { BankCatalog.byCode(it) }

    GhostPage(title = "Accounts", subtitle = "destination once, sources as often as you like", onBack = onBack) { modifier ->
        ScrollColumn(modifier) {
            if (state.message != null) {
                Panel(accent = GhostAmber) {
                    Text(state.message, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onDismissMessage) { Text("Dismiss") }
                }
            }

            SectionHeading("Your destination", "Fixed. Money credited by every run lands here.")
            Panel(accent = GhostGreen) {
                Field("Account number", destNumber, { destNumber = it.filter(Char::isDigit).take(18) }, KeyboardType.Number)
                if (showDestErrors && destNumberError != null) {
                    Text(destNumberError, style = MonoTiny, color = GhostRed)
                }
                Field("IFSC", destIfsc, { destIfsc = it.uppercase().take(11) }, hint = "Format HDFC0001234")
                if (showDestErrors && destIfscError != null) {
                    Text(destIfscError, style = MonoTiny, color = GhostRed)
                }
                Field("Holder name (optional)", destHolder, { destHolder = it })
                PrimaryButton("Save destination") {
                    showDestErrors = true
                    if (destNumberError == null && destIfscError == null) {
                        onSaveDestination(destNumber, destIfsc, destHolder)
                    }
                }
                Text(
                    "The destination bank will always know these digits — it is their ledger. Virtual accounts only mask what travels on the rail.",
                    style = MonoTiny,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionHeading("Source remitter profiles", "Each account is fingerprinted and gets one paisa only.")
            Panel(accent = if (bank == null) GhostAmber else MaterialTheme.colorScheme.outline) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(bank?.name ?: "No bank picked", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = onPickBank) { Text(if (bank == null) "Pick bank" else "Change") }
                }
                if (bank != null) {
                    KeyValue("Transaction cap", Validation.formatPaise(bank.capPaise), mono = true)
                    Text(bank.quirk, style = MonoTiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Field("Account number", sourceNumber, { sourceNumber = it.filter(Char::isDigit).take(18) }, KeyboardType.Number)
                if (showSourceErrors && sourceNumberError != null) {
                    Text(sourceNumberError, style = MonoTiny, color = GhostRed)
                }
                Field("IFSC", sourceIfsc, { sourceIfsc = it.uppercase().take(11) }, hint = "Format HDFC0001234")
                if (showSourceErrors && sourceIfscError != null) {
                    Text(sourceIfscError, style = MonoTiny, color = GhostRed)
                }
                Field("Label (optional)", sourceLabel, { sourceLabel = it })
                PrimaryButton("Add to registry", enabled = bank != null) {
                    showSourceErrors = true
                    if (bank != null && sourceNumberError == null && sourceIfscError == null) {
                        val ok = onAddAccount(bank.code, sourceNumber, sourceIfsc, sourceLabel)
                        if (ok) {
                            sourceNumber = ""
                            sourceIfsc = ""
                            sourceLabel = ""
                            showSourceErrors = false
                        }
                    }
                }
            }

            if (state.accounts.isEmpty()) {
                EmptyState("Registry empty", "Add a source account, or run a transfer straight off the bank's own P2A route.")
            } else {
                state.accounts.forEach { account ->
                    Panel(
                        accent = when (account.status) {
                            AccountStatus.SETTLED -> GhostGreen
                            AccountStatus.FLOOR -> GhostAmber
                            AccountStatus.REJECTED -> GhostRed
                            else -> MaterialTheme.colorScheme.outline
                        }
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(account.label, style = MaterialTheme.typography.titleMedium)
                                Mono(account.maskedNumber, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusPill(
                                    account.status.name,
                                    if (account.status == AccountStatus.PENDING) MaterialTheme.colorScheme.onSurfaceVariant else GhostAmber
                                )
                                IconButton(onClick = { onRemoveAccount(account.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Remove")
                                }
                            }
                        }
                        KeyValue("Bank", account.bankName)
                        KeyValue("IFSC", account.ifsc, mono = true)
                        KeyValue("Fingerprint", account.fingerprint.take(16) + "…", mono = true)
                        if (account.utr != null) KeyValue("UTR", account.utr!!, mono = true, valueColor = GhostGreen)
                    }
                }
            }
        }
    }
}
