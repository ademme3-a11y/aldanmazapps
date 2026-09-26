package com.aldanmaz.drivedashboard.data.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiConversationPolicyTest {

    @Test
    fun cabinConversationRule_requiresDirectAddressAndSilenceOtherwise() {
        val instruction = GeminiConversationPolicy.SYSTEM_INSTRUCTION

        assertTrue(instruction.contains("Gemini"))
        assertTrue(instruction.contains("Aldanmaz"))
        assertTrue(instruction.contains("Emin değilsen tamamen sessiz kal"))
        assertTrue(instruction.contains("telefon görüşmesini"))
    }
}
