package com.aldfinans.app.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.aldfinans.app.data.AlDFinansDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val pending = goAsync()
        val appContext = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val db = AlDFinansDatabase.get(appContext)
                Telephony.Sms.Intents.getMessagesFromIntent(intent).forEach { message ->
                    val body = message.messageBody.orEmpty()
                    val sender = message.originatingAddress.orEmpty()
                    if (body.isBlank() || sender.isBlank()) return@forEach
                    val parsed = TransactionParser.parse(sender, body, message.timestampMillis) ?: return@forEach
                    db.financialDao().insertTransaction(parsed)
                }
            } finally {
                pending.finish()
                scope.cancel()
            }
        }
    }
}
