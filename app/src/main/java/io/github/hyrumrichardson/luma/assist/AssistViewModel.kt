package io.github.hyrumrichardson.luma.assist

import android.app.Application
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
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
    data class Launch(val intent: Intent, val fallback: Intent?, val thenClose: Boolean, val missingApp: String?) : AssistEvent

    /** Ask the user to unlock, then report back with [AssistViewModel.onUnlockResult]. */
    data object Unlock : AssistEvent

    /** Ask for runtime permissions, then report back with [AssistViewModel.onPermissionsResult]. */
    data class RequestPermissions(val permissions: List<String>) : AssistEvent

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

    /** A command waiting for the user to unlock the phone. */
    private var awaitingUnlock: Command? = null

    /** A command waiting for the user to answer a permission prompt. */
    private var awaitingPermissions: Command? = null

    /** A question Luma asked; the next thing the user says answers it instead of being a new command. */
    private var asking: Asking? = null

    private class Asking(val question: String, val followUp: FollowUp)

    /** Whether the last input was typed, so a follow-up question waits for typing rather than listening. */
    private var typed = false

    fun startListening() {
        cancelWork()
        if (!speech.isAvailable) {
            reply("Speech recognition isn't available on this phone. You can type instead.", success = false)
            return
        }
        // While a question is waiting, keep it on screen in place of the example.
        _state.value = AssistUiState(phase = Phase.Starting, reply = asking?.question.orEmpty())
        speech.start(object : SpeechInput.Listener {
            override fun onReady() = _state.update { it.copy(phase = Phase.Listening) }

            override fun onLevel(rmsDb: Float) =
                _state.update { it.copy(level = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f)) }

            override fun onPartial(text: String) = _state.update { it.copy(phase = Phase.Listening, heard = text) }

            override fun onResults(alternatives: List<String>) {
                typed = false
                handle(alternatives)
            }

            override fun onError(error: Int) {
                speech.stop()
                reply(SpeechInput.describeError(error), success = false, speak = false)
            }
        })
    }

    fun startTyping() {
        cancelWork()
        _state.value = AssistUiState(phase = Phase.Typing, reply = asking?.question.orEmpty())
    }

    fun submitText(text: String) {
        if (text.isBlank()) return
        cancelWork()
        typed = true
        handle(listOf(text.trim()))
    }

    private fun handle(alternatives: List<String>) {
        speech.stop()
        asking?.let { pending ->
            asking = null
            _state.value = AssistUiState(phase = Phase.Thinking, heard = alternatives.first())
            work = viewModelScope.launch { deliver(safely { pending.followUp.answer(alternatives) }) }
            return
        }

        // Recognisers return several guesses; use the first one Luma understands, and otherwise
        // search the web for the likeliest one.
        val (heard, command) = alternatives.firstNotNullOfOrNull { text -> CommandParser.parseKnown(text)?.let { text to it } }
            ?: alternatives.first().let { text -> text to CommandParser.parse(text) }
        _state.value = AssistUiState(phase = Phase.Thinking, heard = heard)
        if (command == null) {
            reply(SpeechInput.describeError(SpeechRecognizer.ERROR_NO_MATCH), success = false, speak = false)
            return
        }

        // Anyone can open the assistant over the lock screen, so switching devices and texting need an unlock.
        val unlockReply = unlockReply(command)
        if (unlockReply != null && keyguard.isKeyguardLocked) {
            awaitingUnlock = command
            _state.update { it.copy(reply = unlockReply) }
            viewModelScope.launch { _events.send(AssistEvent.Unlock) }
            return
        }
        runWithPermissions(command)
    }

    fun onUnlockResult(unlocked: Boolean) {
        val command = awaitingUnlock ?: return
        awaitingUnlock = null
        if (unlocked) {
            _state.update { it.copy(reply = "") }
            runWithPermissions(command)
        } else {
            reply(unlockReply(command) ?: "Unlock your phone to continue.", success = false, speak = false)
        }
    }

    /** Asks for any permissions [command] needs that Luma doesn't have yet, then runs it. */
    private fun runWithPermissions(command: Command) {
        val missing = permissionsFor(command).filter {
            ContextCompat.checkSelfPermission(app, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            run(command)
            return
        }
        awaitingPermissions = command
        viewModelScope.launch { _events.send(AssistEvent.RequestPermissions(missing)) }
    }

    /** Runs the command either way; if something's still missing, it explains what. */
    fun onPermissionsResult() {
        val command = awaitingPermissions ?: return
        awaitingPermissions = null
        run(command)
    }

    private fun permissionsFor(command: Command): List<String> = when (command) {
        is Command.SendText -> if (app.texts.hasTelephony) Texting.PERMISSIONS else emptyList()
        else -> emptyList()
    }

    private fun unlockReply(command: Command): String? = when (command) {
        is Command.ControlDevice -> "Unlock your phone to control your devices."
        is Command.SendText -> "Unlock your phone to send texts."
        else -> null
    }

    private fun run(command: Command) {
        work = viewModelScope.launch { deliver(safely { executor.execute(command) }) }
    }

    private suspend fun safely(block: suspend () -> Outcome): Outcome = try {
        block()
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Log.w(TAG, "Command failed", e)
        Outcome("Something went wrong: ${e.message ?: e.javaClass.simpleName}", success = false)
    }

    private suspend fun deliver(outcome: Outcome) {
        outcome.launch?.let {
            _events.send(AssistEvent.Launch(it, outcome.fallbackLaunch, outcome.closeImmediately, outcome.missingApp))
        }
        val followUp = outcome.followUp
        when {
            followUp != null -> ask(outcome.reply, followUp)
            outcome.closeImmediately ->
                _state.update { it.copy(phase = Phase.Replied, reply = outcome.reply, success = outcome.success) }
            else -> reply(outcome.reply, outcome.success)
        }
    }

    /** Shows (and maybe speaks) [question], then listens (or waits for typing) for the answer. */
    private fun ask(question: String, followUp: FollowUp) {
        val pending = Asking(question, followUp)
        asking = pending
        _state.update { it.copy(phase = Phase.Replied, reply = question, success = true, level = 0f) }
        val answer = {
            // Unless the user has already moved on (tapped the keyboard or mic, or answered by typing).
            if (asking === pending && _state.value.phase == Phase.Replied) {
                if (typed) startTyping() else startListening()
            }
        }
        if (app.settings.speakReplies) {
            speaker.speak(question) { answer() }
        } else {
            // After this coroutine ends, since starting to listen cancels it.
            viewModelScope.launch { answer() }
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
        awaitingPermissions = null
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
    }
}
