package io.github.hyrumrichardson.luma.assist

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import io.github.hyrumrichardson.luma.luma
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Speaks short replies. [onDone] always runs on the main thread, even if TTS is unavailable. */
class Speaker(context: Context) {
    private val settings = context.luma.settings
    private val main = Handler(Looper.getMainLooper())
    private var ready: Boolean? = null // null until the engine has initialised
    private var pending: Pending? = null
    private var onDone: (() -> Unit)? = null

    private val _voices = MutableStateFlow<List<Voice>?>(null)

    /** The installed voices for the phone's language once the engine is ready, or empty if it isn't available. */
    val voices: StateFlow<List<Voice>?> = _voices.asStateFlow()

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        main.post {
            ready = status == TextToSpeech.SUCCESS
            _voices.value = if (ready == true) installedVoices() else emptyList()
            pending?.let { speak(it.text, it.voiceName, it.onDone) }
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

    /** Speaks [text] in the voice named [voiceName], or the phone's default voice if it's null or gone. */
    fun speak(text: String, voiceName: String? = settings.voiceName, onDone: () -> Unit) {
        when (ready) {
            null -> pending = Pending(text, voiceName, onDone)
            false -> onDone()
            true -> {
                this.onDone = onDone
                val voice = voiceName?.let { name -> installedVoices().firstOrNull { it.name == name } } ?: tts.defaultVoice
                if (voice != null) tts.setVoice(voice)
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

    private fun installedVoices(): List<Voice> {
        val language = Locale.getDefault().language
        // Some engines throw here rather than returning nothing.
        val all = try {
            tts.voices.orEmpty()
        } catch (e: Exception) {
            emptySet()
        }
        return all
            .filter { it.locale.language == language && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
            .sortedWith(compareBy({ it.locale.toLanguageTag() }, { it.isNetworkConnectionRequired }, { it.name }))
    }

    private fun finish() {
        main.post {
            val done = onDone
            onDone = null
            done?.invoke()
        }
    }

    private class Pending(val text: String, val voiceName: String?, val onDone: () -> Unit)

    private companion object {
        const val UTTERANCE_ID = "luma-reply"
    }
}
