package com.thaiprompt.smschecker.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.thaiprompt.smschecker.data.model.BankTransaction
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    fun exportAndShare(context: Context, transactions: List<BankTransaction>) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val fileName = "transactions_${fileNameFormat.format(Date())}.csv"

        val csv = buildString {
            appendLine("Bank,Type,Amount,Account,Sender/Receiver,Reference,Date,Synced")
            transactions.forEach { tx ->
                val escapedSender = "\"${tx.senderOrReceiver.replace("\"", "\"\"")}\""
                appendLine(
                    "${tx.bank},${tx.type},${tx.amount},${tx.accountNumber}," +
                            "$escapedSender,${tx.referenceNumber}," +
                            "${dateFormat.format(Date(tx.timestamp))},${tx.isSynced}"
                )
            }
        }

        val file = File(context.cacheDir, fileName)
        file.writeText(csv)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "SMS Checker Transactions Export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Transactions"))
    }
}
