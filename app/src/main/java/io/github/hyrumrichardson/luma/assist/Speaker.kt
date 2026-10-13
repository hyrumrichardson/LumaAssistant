package io.github.hyrumrichardson.luma.assist

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener

/** Speaks short replies. [onDone] always runs on the main thread, even if TTS is unavailable. */
class Speaker(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var ready: Boolean? = null // null until the engine has initialised
    private var pending: Pair<String, () -> Unit>? = null
    private var onDone: (() -> Unit)? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        main.post {
            ready = status == TextToSpeech.SUCCESS
            pending?.let { (text, done) -> speak(text, done) }
            pending = null
        }
    }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finish()
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish()
            override fun onError(utteranceId: String?, errorCode: Int) = finish()
        })
    }

    fun speak(text: String, onDone: () -> Unit) {
        when (ready) {
            null -> pending = text to onDone
            false -> onDone()
            true -> {
                this.onDone = onDone
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
            }
        }
    }

    fun stop() {
        pending = null
        onDone = null
        tts.stop()
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }

    private fun finish() {
        main.post {
            val done = onDone
            onDone = null
            done?.invoke()
        }
    }

    private companion object {
        const val UTTERANCE_ID = "luma-reply"
    }
}
