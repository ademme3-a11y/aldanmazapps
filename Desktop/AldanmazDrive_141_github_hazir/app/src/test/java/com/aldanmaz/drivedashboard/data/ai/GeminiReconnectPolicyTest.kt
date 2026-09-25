package com.aldanmaz.drivedashboard.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiReconnectPolicyTest {

    @Test
    fun `recoverable reconnect does not show repeated user error`() {
        assertFalse(
            GeminiReconnectPolicy.shouldShowUserError(
                status = GeminiLiveStatus.ERROR,
                isRecoverableError = true,
            )
        )
        assertTrue(
            GeminiReconnectPolicy.shouldShowUserError(
                status = GeminiLiveStatus.ERROR,
                isRecoverableError = false,
            )
        )
        assertTrue(
            GeminiReconnectPolicy.shouldShowUserError(
                status = GeminiLiveStatus.NEEDS_SETUP,
                isRecoverableError = false,
            )
        )
    }

    @Test
    fun `reconnect backoff grows and caps at thirty seconds`() {
        assertEquals(2_000L, GeminiReconnectPolicy.reconnectDelayMs(0))
        assertEquals(4_000L, GeminiReconnectPolicy.reconnectDelayMs(1))
        assertEquals(8_000L, GeminiReconnectPolicy.reconnectDelayMs(2))
        assertEquals(15_000L, GeminiReconnectPolicy.reconnectDelayMs(3))
        assertEquals(30_000L, GeminiReconnectPolicy.reconnectDelayMs(8))
    }

    @Test
    fun `unstable live model switches to fallback and wraps safely`() {
        assertEquals(1, GeminiReconnectPolicy.nextModelIndex(currentIndex = 0, modelCount = 2))
        assertEquals(0, GeminiReconnectPolicy.nextModelIndex(currentIndex = 1, modelCount = 2))
        assertEquals(0, GeminiReconnectPolicy.nextModelIndex(currentIndex = 0, modelCount = 1))
    }
}
