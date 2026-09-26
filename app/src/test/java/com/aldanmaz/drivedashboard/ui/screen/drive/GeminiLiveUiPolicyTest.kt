package com.aldanmaz.drivedashboard.ui.screen.drive

import com.aldanmaz.drivedashboard.data.ai.GeminiLiveStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiLiveUiPolicyTest {

    @Test
    fun `gemini panel collapses four seconds after listening starts`() {
        assertEquals(4_000L, GeminiLiveUiPolicy.AUTO_COLLAPSE_DELAY_MS)
        assertEquals(14_000, GeminiLiveUiPolicy.COMPACT_ROTATION_DURATION_MS)
        assertTrue(
            GeminiLiveUiPolicy.shouldAutoCollapse(
                selectedId = "gemini",
                status = GeminiLiveStatus.LISTENING,
            )
        )
    }

    @Test
    fun `panel stays visible while connecting or showing another media item`() {
        assertFalse(
            GeminiLiveUiPolicy.shouldAutoCollapse(
                selectedId = "gemini",
                status = GeminiLiveStatus.CONNECTING,
            )
        )
        assertFalse(
            GeminiLiveUiPolicy.shouldAutoCollapse(
                selectedId = "music",
                status = GeminiLiveStatus.LISTENING,
            )
        )
    }
}
