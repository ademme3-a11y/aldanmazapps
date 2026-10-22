package com.aldfinans.app.data.sms

import android.content.Context
import android.provider.Telephony
import com.aldfinans.app.data.AlDFinansDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MessageImportResult(
    val scanned: Int,
    val matched: Int,
    val inserted: Int
)

object MessageImportService {
    suspend fun importInbox(context: Context): MessageImportResult = withContext(Dispatchers.IO) {
        val db = AlDFinansDatabase.get(context)
        var scanned = 0
        var matched = 0
        var inserted = 0
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            null,
            null,
            Telephony.Sms.DATE + " DESC"
        )?.use { cursor ->
            val senderIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE)
            while (cursor.moveToNext()) {
                scanned++
                val sender = if (senderIndex >= 0) cursor.getString(senderIndex).orEmpty() else ""
                val body = if (bodyIndex >= 0) cursor.getString(bodyIndex).orEmpty() else ""
                val timestamp = if (dateIndex >= 0) cursor.getLong(dateIndex) else continue
                if (sender.isBlank() || body.isBlank()) continue
                val transaction = TransactionParser.parse(sender, body, timestamp) ?: continue
                matched++
                if (db.financialDao().insertTransaction(transaction) != -1L) inserted++
            }
        }
        MessageImportResult(scanned, matched, inserted)
    }
}
