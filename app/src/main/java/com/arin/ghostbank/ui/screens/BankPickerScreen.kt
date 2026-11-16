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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arin.ghostbank.data.BankCatalog
import com.arin.ghostbank.model.Validation
import com.arin.ghostbank.ui.UiState
import com.arin.ghostbank.ui.components.EmptyState
import com.arin.ghostbank.ui.components.Field
import com.arin.ghostbank.ui.components.GhostPage
import com.arin.ghostbank.ui.components.KeyValue
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.ScrollColumn
import com.arin.ghostbank.ui.components.StatusPill
import com.arin.ghostbank.ui.theme.GhostGreen
import com.arin.ghostbank.ui.theme.MonoTiny

@Composable
fun BankPickerScreen(
    state: UiState,
    onBack: () -> Unit,
    onPick: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val needle = query.trim()
    val filtered = if (needle.isEmpty()) BankCatalog.all else BankCatalog.all.filter {
        it.name.contains(needle, true) || it.code.contains(needle, true)
    }

    GhostPage(
        title = "Bank",
        subtitle = "${BankCatalog.all.size} banks · own cap and rail quirk",
        onBack = onBack
    ) { modifier ->
        ScrollColumn(modifier) {
            Field("Search", query, { query = it }, hint = "Name or code")
            if (filtered.isEmpty()) {
                EmptyState("No bank matches", "Try another name or the bank code.")
            } else {
                filtered.forEach { bank ->
                    val selected = bank.code == state.selectedBankCode
                    Panel(accent = if (selected) GhostGreen else MaterialTheme.colorScheme.outline) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(bank.name, style = MaterialTheme.typography.titleMedium)
                                Text(bank.rail, style = MonoTiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (selected) StatusPill("PICKED", GhostGreen)
                        }
                        KeyValue("Per-transaction cap", Validation.formatPaise(bank.capPaise), mono = true)
                        KeyValue("Rail floor", Validation.formatPaise(bank.minPaise), mono = true)
                        Text(bank.quirk, style = MonoTiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { onPick(bank.code) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (selected) "Keep this bank" else "Select")
                        }
                    }
                }
            }
        }
    }
}
