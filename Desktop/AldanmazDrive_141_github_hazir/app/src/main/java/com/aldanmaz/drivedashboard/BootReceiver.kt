package com.aldanmaz.drivedashboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.content.pm.PackageManager
import android.os.Looper

/** Araç multimedya sistemi açıldığında Aldanmaz Drive ana ekranını başlatır. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_BOOT_ACTIONS) return

        // Telefon cihazında araç dashboard'unu açılışta zorla öne getirmeyiz.
        // Arama/SMS için kullanılan Aldanmaz Phone Bridge 97 düzeltmesinde kaldırıldı.
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return
        }

        // 66: Aynı APK Galaxy A25 üzerinde OBD köprüsü olarak da kullanılabilir.
        // Telefon köprü modunda telefon açılışında araç dashboard'unu zorla öne getirmeyiz.
        val obdMode = context.getSharedPreferences("obd_settings", 0)
            .getString("connection_mode", "DIRECT_BLUETOOTH")
        if (obdMode == "PHONE_BRIDGE") return

        val pendingResult = goAsync()
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching {
                context.startActivity(
                    Intent(context, MainActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )
                    }
                )
            }
            pendingResult.finish()
        }, START_DELAY_MS)
    }

    private companion object {
        const val START_DELAY_MS = 2_000L

        val SUPPORTED_BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )
    }
}
