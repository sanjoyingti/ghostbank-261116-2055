package com.arin.ghostbank.data

import com.arin.ghostbank.model.Bank

object BankCatalog {

    val all: List<Bank> = listOf(
        Bank("SBI", "State Bank of India", "https://www.onlinesbi.sbi/", 500000L, 100L, "P2A accepted; payee name echoed in narration. Heavy Akamai WAF — expect a JS challenge on bursts."),
        Bank("HDFC", "HDFC Bank", "https://netbanking.hdfcbank.com/", 500000L, 100L, "Strict header validation; rejects mismatched sec-ch-ua. Keep one probe per stage."),
        Bank("ICICI", "ICICI Bank", "https://www.icicibank.com/", 500000L, 100L, "Resolves payee before debit; returns 429 quickly under burst."),
        Bank("AXIS", "Axis Bank", "https://www.axisbank.com/", 500000L, 100L, "Distinct WAF cookie set per handshake — never reuse a cookie across runs."),
        Bank("KOTAK", "Kotak Mahindra Bank", "https://www.kotak.com/", 500000L, 100L, "Accepts sub-rupee on its UPI rail; IMPS leg still floors to 1 rupee."),
        Bank("PNB", "Punjab National Bank", "https://netpnb.com/", 500000L, 100L, "Slow TLS handshake, long settle window. Poll sparingly."),
        Bank("BOB", "Bank of Baroda", "https://www.bobibanking.com/", 500000L, 100L, "Narration field truncated — neutral PAYMENT string survives."),
        Bank("CANARA", "Canara Bank", "https://canarabank.com/", 500000L, 100L, "Returns 403 on missing Upgrade-Insecure-Requests."),
        Bank("UNION", "Union Bank of India", "https://www.unionbankofindia.co.in/", 500000L, 100L, "Old TLS stack — 1.2 only, no session tickets."),
        Bank("IDBI", "IDBI Bank", "https://www.idbibank.in/", 500000L, 100L, "Settle returns PENDING for several polls before a final status."),
        Bank("IDFC", "IDFC First Bank", "https://www.idfcfirstbank.com/", 500000L, 100L, "Modern stack, HTTP/2 only. Header order must stay inside the Chrome set."),
        Bank("YES", "Yes Bank", "https://www.yesbank.in/", 500000L, 100L, "Rate-limits per IP aggressively — rotate the exit."),
        Bank("INDUSIND", "IndusInd Bank", "https://www.indusind.com/", 500000L, 100L, "Payee resolve requires the IFSC prefix to belong to the bank."),
        Bank("FEDERAL", "Federal Bank", "https://www.federalbank.co.in/", 500000L, 100L, "Accepts exact paisa amounts on the micro rail; floors otherwise."),
        Bank("RBL", "RBL Bank", "https://www.rblbank.com/", 500000L, 100L, "Short session lifetime — deliberate close after each stage."),
        Bank("BANDHAN", "Bandhan Bank", "https://www.bandhanbank.com/", 500000L, 100L, "Rejects unknown Accept-Encoding tokens."),
        Bank("CSB", "CSB Bank", "https://www.csb.co.in/", 500000L, 100L, "Narration is mandatory; PAYMENT is accepted."),
        Bank("DCB", "DCB Bank", "https://www.dcbbank.com/", 500000L, 100L, "Returns 302 chains — redirects must be followed like a browser."),
        Bank("DHAN", "Dhanlaxmi Bank", "https://www.dhanbank.com/", 500000L, 100L, "Very small cap window; settle can lag."),
        Bank("JKBANK", "J&K Bank", "https://www.jkbank.com/", 500000L, 100L, "TLS resumption unsupported."),
        Bank("KARNATAKA", "Karnataka Bank", "https://www.karnatakabank.com/", 500000L, 100L, "Strict content-type sniffing on resolve."),
        Bank("SOUTHINDIAN", "South Indian Bank", "https://www.southindianbank.com/", 500000L, 100L, "Long settle; UTR issued late in the poll chain."),
        Bank("TMB", "Tamilnad Mercantile Bank", "https://www.tmb.in/", 500000L, 100L, "Header order sensitive — key order permutation stays in the Chrome set."),
        Bank("UJJIVAN", "Ujjivan Small Finance Bank", "https://www.ujjivansfb.in/", 500000L, 100L, "Small finance rails — lower practical burst tolerance."),
        Bank("AU", "AU Small Finance Bank", "https://www.aubank.in/", 500000L, 100L, "Rejects duplicate Accept headers."),
        Bank("EQUITAS", "Equitas Small Finance Bank", "https://www.equitasbank.com/", 500000L, 100L, "Canary often 403s from datacentre exits — residential or Tor only."),
        Bank("JANA", "Jana Small Finance Bank", "https://www.janabank.com/", 500000L, 100L, "Settle returns 200 with a soft failure body — parse the shape."),
        Bank("SURYODAY", "Suryoday Small Finance Bank", "https://www.suryodaybank.com/", 500000L, 100L, "Narration echo truncated to 18 chars.")
    )

    private val index: Map<String, Bank> = all.associateBy { it.code }

    fun byCode(code: String): Bank? = index[code]

    fun name(code: String): String = index[code]?.name ?: code

    fun cap(code: String): Long = index[code]?.capPaise ?: 500000L

    fun min(code: String): Long = index[code]?.minPaise ?: 100L
}
