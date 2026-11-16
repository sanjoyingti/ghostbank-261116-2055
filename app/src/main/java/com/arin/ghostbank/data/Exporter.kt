package com.arin.ghostbank.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.arin.ghostbank.model.LedgerEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Exporter {

    suspend fun ledgerCsv(context: Context, entries: List<LedgerEntry>): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "ghostbank_ledger_$stamp.csv")
        val header = "time,run,bank,amount_rupees,mode,leg,status,utr,note\n"
        val rows = entries.joinToString("\n") { entry ->
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(entry.at))
            listOf(
                time,
                entry.runId.take(8),
                entry.bankName.replace(",", " "),
                String.format(Locale.US, "%.2f", entry.amountPaise / 100.0),
                entry.mode.name,
                entry.leg.name,
                entry.status,
                entry.utr ?: "",
                entry.note.replace(",", " ").replace("\n", " ")
            ).joinToString(",")
        }
        file.writeText(header + rows)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
