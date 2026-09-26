package com.aldanmaz.drivedashboard.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Uygulama ekrandayken yalnızca "Hey Car" uyandırma sözünü bekler.
 * Uyandırma algılandığında normal komut dinleme akışını başlatması için onWake çağrılır.
 */
class HandsFreeWakeWordController(
    context: Context,
    private val requireWakeWord: Boolean = true,
    private val onReady: () -> Unit = {},
    private val onUnavailable: (Int) -> Unit = {},
    private val onCommand: (String) -> Unit
) : RecognitionListener {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var wakeDelivered = false
    private var awaitingCommand = !requireWakeWord

    private val listenIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 60_000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 8_000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4_000L)
    }

    fun start() {
        if (running || !SpeechRecognizer.isRecognitionAvailable(appContext)) return
        running = true
        wakeDelivered = false
        awaitingCommand = !requireWakeWord
        recognizer = (
            SpeechRecognizer.createSpeechRecognizer(appContext)
        ).also {
            it.setRecognitionListener(this)
        }
        beginListening(250L)
    }

    fun stop() {
        running = false
        mainHandler.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    private fun beginListening(delayMs: Long) {
        if (!running || wakeDelivered) return
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (!running || wakeDelivered) return@postDelayed
            runCatching { recognizer?.startListening(listenIntent) }
                .onFailure { scheduleRestart(1_200L) }
        }, delayMs)
    }

    private fun scheduleRestart(delayMs: Long = 1_200L) {
        if (!running || wakeDelivered) return
        runCatching { recognizer?.cancel() }
        beginListening(delayMs)
    }

    private fun inspect(bundle: Bundle?, finalResult: Boolean) {
        if (!running || wakeDelivered) return
        val phrases = bundle
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            .orEmpty()
        val cleanedPhrases = phrases.map { phrase ->
            phrase.lowercase(Locale("tr", "TR"))
                .replace(Regex("[^a-zçğıöşü ]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }.filter(String::isNotBlank)

        if (awaitingCommand) {
            if (finalResult) cleanedPhrases.firstOrNull()?.let { deliver(it) }
            return
        }

        val wakeMatch = cleanedPhrases.firstNotNullOfOrNull { text ->
            val hasWake = text.contains("hey") || text.startsWith("ey ") || text.startsWith("hey")
            val carWord = listOf("car", "kar", "kâr", "kart", "kal", "kaar").firstOrNull(text::contains)
            if (hasWake && carWord != null) text to carWord else null
        }

        if (wakeMatch != null && finalResult) {
            val (text, carWord) = wakeMatch
            val remainder = text.substringAfter(carWord, "").trim()
            recognizer?.cancel()
            if (remainder.isNotBlank()) {
                deliver(remainder)
            } else {
                awaitingCommand = true
                beginListening(300L)
            }
        }
    }

    private fun deliver(command: String) {
        if (command.isBlank()) return
        wakeDelivered = true
        mainHandler.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        onCommand(command)
        // Tek Google motoru: kullanıcı çift dokunarak kapatana kadar yeni komuta hazırlan.
        if (!requireWakeWord && running) {
            wakeDelivered = false
            awaitingCommand = true
            beginListening(900L)
        } else {
            running = false
        }
    }

    override fun onPartialResults(partialResults: Bundle?) = inspect(partialResults, finalResult = false)
    override fun onResults(results: Bundle?) {
        inspect(results, finalResult = true)
        if (!wakeDelivered) scheduleRestart()
    }
    override fun onError(error: Int) {
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
            error == SpeechRecognizer.ERROR_AUDIO ||
            error == SpeechRecognizer.ERROR_CLIENT ||
            error == SpeechRecognizer.ERROR_SERVER ||
            error == SpeechRecognizer.ERROR_NETWORK ||
            error == SpeechRecognizer.ERROR_NO_MATCH ||
            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
        ) {
            onUnavailable(error)
            stop()
        } else {
            // Sessizlik/no-match için hızlı bip döngüsü oluşturma.
            scheduleRestart(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 2_500L else 4_000L)
        }
    }
    override fun onEndOfSpeech() = Unit
    override fun onReadyForSpeech(params: Bundle?) = onReady()
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
