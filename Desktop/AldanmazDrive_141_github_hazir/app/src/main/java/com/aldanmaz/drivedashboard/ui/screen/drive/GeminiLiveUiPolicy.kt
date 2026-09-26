package com.aldanmaz.drivedashboard.ui.screen.drive

import com.aldanmaz.drivedashboard.data.ai.GeminiLiveStatus

/** Gemini Live açılış panelinin kısa ve öngörülebilir kalmasını sağlar. */
internal object GeminiLiveUiPolicy {
    const val AUTO_COLLAPSE_DELAY_MS = 4_000L
    const val COMPACT_ROTATION_DURATION_MS = 14_000

    fun shouldAutoCollapse(selectedId: String?, status: GeminiLiveStatus): Boolean =
        selectedId == "gemini" && status == GeminiLiveStatus.LISTENING
}
