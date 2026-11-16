package com.arin.ghostbank.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arin.ghostbank.ui.components.Dot
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.PrimaryButton
import com.arin.ghostbank.ui.components.ScrollColumn
import com.arin.ghostbank.ui.components.SectionHeading
import com.arin.ghostbank.ui.components.SpacerV
import com.arin.ghostbank.ui.theme.GhostGreen
import com.arin.ghostbank.ui.theme.MonoTiny

private data class Page(val title: String, val body: String)

private val pages = listOf(
    Page(
        "What this really is",
        "GhostBank is not a scraper demo. Every run resolves a payee, fires the debit leg, fires the credit leg over IMPS P2A and then polls the reference until the bank returns a final status. The 12-digit UTR is issued and booked to a local encrypted ledger.\n\nWhere the bank server does not answer, the leg falls back to local simulation and is tagged SIMULATED — the app never blocks you."
    ),
    Page(
        "You only enter one thing",
        "You type your own destination account once: number plus IFSC. It becomes the fixed destination forever. Sources are either remitter profiles you save yourself, or the bank's own P2A route.\n\nThe pipeline is CANARY → INITIATED → FINGERPRINT → RESOLVE_PAYEE → DEBIT_LEG → CREDIT_LEG → SETTLE → BOOKED. A stage that does not confirm cannot advance, an idempotency key stops a double debit, and a failed debit auto-reverses."
    ),
    Page(
        "Not detected",
        "Requests carry your device's real Chrome user agent, matching sec-ch-ua client hints and Chrome's exact header order. HTTP/2 pseudo-header order, ALPN and cipher order follow Chrome; connections are reused then deliberately closed. No X-App header, no app version, no custom headers, no analytics."
    ),
    Page(
        "Trace-Shield, honestly",
        "IP, DNS and timezone can be hidden behind a proxy pool or Tor, with DoH and a kill-switch so a request never escapes on your raw IP.\n\nYour account number cannot be hidden from the destination bank — it is in their own ledger. What GhostBank does instead is masking: rotating virtual accounts forward to you, so your real digits never appear on the rail."
    ),
    Page(
        "One paisa per account",
        "Accounts are fingerprinted and marked HARVESTED so an account is never touched twice. Exact 1-paisa mode is hard-capped. The rail floor is ₹1, so if a bank rejects sub-rupee the leg upgrades to the rail minimum and is tagged FLOOR — never silently rounded. The balance is summed in paise, never rounded away."
    ),
    Page(
        "What is still true",
        "The destination bank always knows the account number. The source bank always knows which account was debited. A raw mobile IP is Android's own TCP stack, which only a proxy or Tor can shadow — Ghost profile burns, decoy browsing, Poisson jitter and anomaly auto-pause reduce the pattern, they do not erase physics."
    )
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    val page = pages[index]

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            pages.indices.forEach { i ->
                Dot(if (i == index) GhostGreen else MaterialTheme.colorScheme.outline, 10)
            }
        }
        ScrollColumn(Modifier.fillMaxWidth().weight(1f)) {
            SectionHeading(page.title, "page ${index + 1} of ${pages.size}")
            Panel {
                Text(page.body, style = MaterialTheme.typography.bodyLarge)
            }
            SpacerV(4)
            Panel(accent = MaterialTheme.colorScheme.tertiary) {
                Text(
                    "LIVE means the server answered. SIMULATED means it did not and the leg ran locally.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("Both are always badged, never blurred.", style = MonoTiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (index > 0) {
                PrimaryButton("Back", Modifier.weight(1f)) { index -= 1 }
            }
            PrimaryButton(
                text = if (index == pages.lastIndex) "Enter GhostBank" else "Next",
                modifier = Modifier.weight(1f)
            ) {
                if (index == pages.lastIndex) onFinish() else index += 1
            }
        }
    }
}
