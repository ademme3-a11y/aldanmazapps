package com.aldanmaz.drivedashboard.camera

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * 70mai uygulamasi acildiginda yalnizca acikca "arka/rear/back" kamerayi belirten
 * tiklanabilir erisilebilirlik dugmesini bir kez secer. Dugme bulunamazsa on kamerayi
 * degistirmeden birakir; ekrana tahmini koordinatla dokunmaz.
 */
class SevenMaiRearCameraAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var activeCameraPackage: String? = null
    private var switchedInCurrentVisit = false
    private var retries = 0

    private val attemptSwitch = Runnable { trySelectRearCamera() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "70mai arka kamera yardimcisi hazir")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (!packageName.startsWith(PACKAGE_PREFIX)) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && activeCameraPackage != null) {
                activeCameraPackage = null
                switchedInCurrentVisit = false
                retries = 0
                handler.removeCallbacks(attemptSwitch)
            }
            return
        }

        if (activeCameraPackage != packageName) {
            activeCameraPackage = packageName
            switchedInCurrentVisit = false
            retries = 0
        }
        if (switchedInCurrentVisit) return
        if (!handler.hasCallbacks(attemptSwitch)) handler.postDelayed(attemptSwitch, 900L)
    }

    private fun trySelectRearCamera() {
        if (switchedInCurrentVisit || activeCameraPackage == null) return
        val root = rootInActiveWindow
        val target = root?.let { findRearButton(it) }
        if (target != null) {
            val clicked = runCatching { target.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
            if (clicked) {
                switchedInCurrentVisit = true
                Log.i(TAG, "70mai arka kamera dugmesi secildi")
                Toast.makeText(this, "70mai arka kamera seçildi", Toast.LENGTH_SHORT).show()
                return
            }
        }

        retries++
        if (retries < MAX_RETRIES) {
            handler.postDelayed(attemptSwitch, RETRY_DELAY_MS)
        } else {
            switchedInCurrentVisit = true
            Log.w(TAG, "70mai ekraninda erisilebilir arka kamera dugmesi bulunamadi")
            Toast.makeText(this, "Arka kamera düğmesi otomatik bulunamadı; 70mai ekranı açık.", Toast.LENGTH_LONG).show()
        }
    }

    private fun findRearButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val labels = listOf(
            "arka kamera", "rear camera", "back camera",
            "switch camera", "camera switch", "change camera", "flip camera",
            "kamerayi degistir", "kamerayı değiştir", "kamera degistir", "kamera değiştir"
        )
        fun visit(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            val label = buildString {
                append(node.text?.toString().orEmpty())
                append(' ')
                append(node.contentDescription?.toString().orEmpty())
            }.lowercase()
            val matches = labels.any { label.contains(it) } || label.trim() == "arka" || label.trim() == "rear"
            if (matches) {
                if (node.isClickable) return node
                var parent = node.parent
                while (parent != null) {
                    if (parent.isClickable) return parent
                    val next = parent.parent
                    parent.recycle()
                    parent = next
                }
            }
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                val found = visit(child)
                if (found != null) return found
                child.recycle()
            }
            return null
        }
        return visit(root)
    }

    override fun onInterrupt() {
        handler.removeCallbacks(attemptSwitch)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AldDrive70mai"
        private const val PACKAGE_PREFIX = "com.banyac.midrive.app"
        private const val MAX_RETRIES = 5
        private const val RETRY_DELAY_MS = 850L
    }
}

/** Multimedya ekranindaki 70mai ARKA dugmesinin guvenli baslatma noktasi. */
fun open70MaiRearCamera(context: Context) {
    val component = ComponentName(context, SevenMaiRearCameraAccessibilityService::class.java)
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ).orEmpty()
    val serviceEnabled = enabledServices.split(':').any { enabled ->
        ComponentName.unflattenFromString(enabled)?.let { it == component } == true
    }

    if (!serviceEnabled) {
        Toast.makeText(
            context,
            "Tek dokunuşla arka kamera için Aldanmaz Drive erişilebilirlik hizmetini açın.",
            Toast.LENGTH_LONG
        ).show()
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        return
    }

    val packages = listOf(
        "com.banyac.midrive.app.intl",
        "com.banyac.midrive.app.eu",
        "com.banyac.midrive.app.na"
    )
    val launchIntent = packages.firstNotNullOfOrNull { packageName ->
        runCatching { context.packageManager.getLaunchIntentForPackage(packageName) }.getOrNull()
    }
    if (launchIntent == null) {
        Toast.makeText(context, "70mai uygulaması bulunamadı.", Toast.LENGTH_LONG).show()
        return
    }
    runCatching {
        context.startActivity(
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }.onFailure {
        Toast.makeText(context, "70mai açılamadı: ${it.javaClass.simpleName}", Toast.LENGTH_LONG).show()
    }
}
