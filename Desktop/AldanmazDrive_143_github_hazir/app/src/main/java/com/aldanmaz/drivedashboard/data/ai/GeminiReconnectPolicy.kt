package com.aldanmaz.drivedashboard.data.ai

/** Gemini Live yeniden bağlantılarını sessiz ve kontrollü tutan saf kurallar. */
internal object GeminiReconnectPolicy {
    const val STABLE_SESSION_RESET_MS = 30_000L

    fun reconnectDelayMs(attempt: Int): Long = when (attempt.coerceAtLeast(0).coerceAtMost(5)) {
        0 -> 2_000L
        1 -> 4_000L
        2 -> 8_000L
        3 -> 15_000L
        else -> 30_000L
    }

    fun nextModelIndex(currentIndex: Int, modelCount: Int): Int {
        if (modelCount <= 1) return 0
        return (currentIndex.coerceAtLeast(0) + 1) % modelCount
    }

    fun shouldShowUserError(status: GeminiLiveStatus, isRecoverableError: Boolean): Boolean =
        status == GeminiLiveStatus.NEEDS_SETUP ||
            (status == GeminiLiveStatus.ERROR && !isRecoverableError)
}
