package io.github.hyrumrichardson.luma.assist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/** Thin wrapper over the system [SpeechRecognizer]. Call everything from the main thread. */
class SpeechInput(private val context: Context) {

    interface Listener {
        fun onReady()
        fun onLevel(rmsDb: Float)
        fun onPartial(text: String)
        fun onResults(alternatives: List<String>)
        fun onError(error: Int)
    }

    private var recognizer: SpeechRecognizer? = null

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(listener: Listener) {
        stop()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = listener.onReady()
            override fun onRmsChanged(rmsdB: Float) = listener.onLevel(rmsdB)
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.texts()?.firstOrNull()?.let(listener::onPartial)
            }
            override fun onResults(results: Bundle?) {
                val texts = results?.texts().orEmpty()
                if (texts.isEmpty()) listener.onError(SpeechRecognizer.ERROR_NO_MATCH) else listener.onResults(texts)
            }
            override fun onError(error: Int) = listener.onError(error)
            override fun onBeginningOfSpeech() = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        r.startListening(intent)
        recognizer = r
    }

    fun stop() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun Bundle.texts(): List<String> =
        getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty().filter { it.isNotBlank() }

    companion object {
        fun describeError(error: Int): String = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't catch that."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
                "Speech recognition needs a network connection right now."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Luma needs microphone access."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The microphone is busy. Try again in a moment."
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                "Speech recognition isn't available for your language."
            else -> "Speech recognition failed (error $error)."
        }
    }
}
