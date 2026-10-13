package io.github.hyrumrichardson.luma.assist

import android.app.Application
import android.app.KeyguardManager
import android.content.Intent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.hyrumrichardson.luma.luma
import io.github.hyrumrichardson.luma.nlu.Command
import io.github.hyrumrichardson.luma.nlu.CommandParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Phase { Starting, Listening, Thinking, Replied, Typing }

data class AssistUiState(
    val phase: Phase = Phase.Starting,
    val heard: String = "",
    val reply: String = "",
    val success: Boolean = true,
    /** Microphone input level from 0 to 1, for the listening animation. */
    val level: Float = 0f,
)

sealed interface AssistEvent {
    data class Launch(val intent: Intent, val fallback: Intent?, val thenClose: Boolean) : AssistEvent

    /** Ask the user to unlock, then report back with [AssistViewModel.onUnlockResult]. */
    data object Unlock : AssistEvent

    data object Close : AssistEvent
}

class AssistViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.luma
    private val speech = SpeechInput(application)
    private val speaker = Speaker(application)
    private val executor = CommandExecutor(app)
    private val keyguard = application.getSystemService(KeyguardManager::class.java)

    private val _state = MutableStateFlow(AssistUiState())
    val state: StateFlow<AssistUiState> = _state.asStateFlow()

    private val _events = Channel<AssistEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var work: Job? = null

    /** A device command waiting for the user to unlock the phone. */
    private var awaitingUnlock: Command? = null

    fun startListening() {
        cancelWork()
        if (!speech.isAvailable) {
            reply("Speech recognition isn't available on this phone. You can type instead.", success = false)
            return
        }
        _state.value = AssistUiState(phase = Phase.Starting)
        speech.start(object : SpeechInput.Listener {
            override fun onReady() = _state.update { it.copy(phase = Phase.Listening) }

            override fun onLevel(rmsDb: Float) =
                _state.update { it.copy(level = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f)) }

            override fun onPartial(text: String) = _state.update { it.copy(phase = Phase.Listening, heard = text) }

            override fun onResults(alternatives: List<String>) = handle(alternatives)

            override fun onError(error: Int) {
                speech.stop()
                reply(SpeechInput.describeError(error), success = false, speak = false)
            }
        })
    }

    fun startTyping() {
        cancelWork()
        _state.value = AssistUiState(phase = Phase.Typing)
    }

    fun submitText(text: String) {
        if (text.isBlank()) return
        cancelWork()
        handle(listOf(text.trim()))
    }

    private fun handle(alternatives: List<String>) {
        speech.stop()
        // Recognisers return several guesses; use the first one Luma understands, and otherwise
        // search the web for the likeliest one.
        val (heard, command) = alternatives.firstNotNullOfOrNull { text -> CommandParser.parseKnown(text)?.let { text to it } }
            ?: alternatives.first().let { text -> text to CommandParser.parse(text) }
        _state.value = AssistUiState(phase = Phase.Thinking, heard = heard)
        if (command == null) {
            reply(SpeechInput.describeError(SpeechRecognizer.ERROR_NO_MATCH), success = false, speak = false)
            return
        }

        // Anyone can open the assistant over the lock screen, so switching devices needs an unlock.
        if (command is Command.ControlDevice && keyguard.isKeyguardLocked) {
            awaitingUnlock = command
            _state.update { it.copy(reply = UNLOCK_REPLY) }
            viewModelScope.launch { _events.send(AssistEvent.Unlock) }
            return
        }
        run(command)
    }

    fun onUnlockResult(unlocked: Boolean) {
        val command = awaitingUnlock ?: return
        awaitingUnlock = null
        if (unlocked) {
            _state.update { it.copy(reply = "") }
            run(command)
        } else {
            reply(UNLOCK_REPLY, success = false, speak = false)
        }
    }

    private fun run(command: Command) {
        work = viewModelScope.launch {
            val outcome = try {
                executor.execute(command)
            } catch (e: Exception) {
                Log.w(TAG, "Command failed", e)
                Outcome("Something went wrong: ${e.message ?: e.javaClass.simpleName}", success = false)
            }
            outcome.launch?.let { _events.send(AssistEvent.Launch(it, outcome.fallbackLaunch, outcome.closeImmediately)) }
            if (outcome.closeImmediately) {
                _state.update { it.copy(phase = Phase.Replied, reply = outcome.reply, success = outcome.success) }
            } else {
                reply(outcome.reply, outcome.success)
            }
        }
    }

    /** Shows (and maybe speaks) a reply; successful replies close the popup afterwards. */
    private fun reply(text: String, success: Boolean, speak: Boolean = true) {
        _state.update { it.copy(phase = Phase.Replied, reply = text, success = success, level = 0f) }
        val closeAfter = {
            if (success) {
                viewModelScope.launch {
                    delay(CLOSE_DELAY_MILLIS)
                    _events.send(AssistEvent.Close)
                }
            }
        }
        if (speak && app.settings.speakReplies) {
            speaker.speak(text) { closeAfter() }
        } else {
            viewModelScope.launch {
                delay(READ_DELAY_MILLIS)
                closeAfter()
            }
        }
    }

    /** Called when the launched app (Maps, Clock) couldn't be found or the phone stayed locked. */
    fun launchFailed(message: String) = reply(message, success = false)

    private fun cancelWork() {
        work?.cancel()
        awaitingUnlock = null
        speaker.stop()
        speech.stop()
    }

    override fun onCleared() {
        speech.stop()
        speaker.shutdown()
    }

    private companion object {
        const val TAG = "AssistViewModel"
        const val CLOSE_DELAY_MILLIS = 600L
        const val READ_DELAY_MILLIS = 1800L
        const val UNLOCK_REPLY = "Unlock your phone to control your devices."
    }
}
