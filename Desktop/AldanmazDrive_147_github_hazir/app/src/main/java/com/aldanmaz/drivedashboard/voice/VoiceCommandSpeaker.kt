package com.aldanmaz.drivedashboard.voice

import android.content.Context
import com.aldanmaz.drivedashboard.data.alert.CentralVoiceAlertManager
import com.aldanmaz.drivedashboard.data.alert.VoiceAlertPriority
import com.aldanmaz.drivedashboard.data.alert.VoiceAlertType

object VoiceCommandSpeaker {
    fun speak(context: Context, text: String) {
        CentralVoiceAlertManager.getInstance(context).speak(
            type = VoiceAlertType.COMMAND,
            priority = VoiceAlertPriority.NORMAL,
            message = text,
            cooldownKey = "voice_command_${System.nanoTime()}",
            cooldownMs = 0L
        )
    }
}
