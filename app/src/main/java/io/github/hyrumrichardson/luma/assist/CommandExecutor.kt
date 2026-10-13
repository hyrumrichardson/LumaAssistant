package io.github.hyrumrichardson.luma.assist

import android.content.Intent
import io.github.hyrumrichardson.luma.LumaApp
import io.github.hyrumrichardson.luma.home.DeviceMatcher
import io.github.hyrumrichardson.luma.home.HomeStatus
import io.github.hyrumrichardson.luma.nlu.Command
import io.github.hyrumrichardson.luma.nlu.Durations
import io.github.hyrumrichardson.luma.nlu.Timing
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

/** What the popup should do once a command has been handled. */
data class Outcome(
    val reply: String,
    val success: Boolean,
    /** An activity to hand off to, e.g. Maps. */
    val launch: Intent? = null,
    /** Tried if [launch] has no app to handle it. */
    val fallbackLaunch: Intent? = null,
    /** Close as soon as [launch] starts, without waiting to speak. */
    val closeImmediately: Boolean = false,
)

class CommandExecutor(private val app: LumaApp) {

    suspend fun execute(command: Command): Outcome = when (command) {
        is Command.SetTimer -> timer(command)
        is Command.Navigate -> Outcome(
            reply = "Navigating to ${command.destination}",
            success = true,
            launch = AppIntents.navigation(command.destination, command.mode),
            fallbackLaunch = AppIntents.mapSearch(command.destination),
            closeImmediately = true,
        )
        is Command.ControlDevice -> device(command)
    }

    private fun timer(command: Command.SetTimer): Outcome {
        val length = command.length
            ?: return Outcome(
                reply = "Opening your timers",
                success = true,
                launch = AppIntents.timer(null, command.label),
                closeImmediately = true,
            )
        if (length > AppIntents.MAX_TIMER_SECONDS.seconds) {
            return Outcome("Timers can be at most 24 hours long.", success = false)
        }
        val name = command.label?.let { "$it timer" } ?: "Timer"
        return Outcome(
            reply = "$name set for ${Durations.format(length)}.",
            success = true,
            launch = AppIntents.timer(length, command.label),
        )
    }

    private suspend fun device(command: Command.ControlDevice): Outcome {
        val home = app.home
        when (val status = home.status()) {
            HomeStatus.Ready -> Unit
            HomeStatus.SdkMissing -> return Outcome(
                "Google Home isn't included in this build of Luma. The README explains how to add it.",
                success = false,
            )
            HomeStatus.NeedsPermission -> return Outcome(
                "Open the Luma app and connect Google Home first.",
                success = false,
            )
            is HomeStatus.Unavailable -> return Outcome(status.reason, success = false)
        }

        val devices = home.devices()
        if (devices.isEmpty()) {
            return Outcome("I couldn't find any devices Luma can switch in your Google Home.", success = false)
        }
        val match = DeviceMatcher.match(command.target, devices)
            ?: return Outcome("I couldn't find anything called \"${command.target}\".", success = false)

        val ids = match.devices.map { it.id }
        val names = match.devices.associate { it.id to it.name }
        val what = spoken(match.description)
        val verb = if (command.turnOn) "on" else "off"

        return when (val timing = command.timing) {
            Timing.Now -> {
                val failures = home.setOnOff(ids, command.turnOn)
                switchReply(failures, ids.size, names, "Turning $verb $what.", verb, what)
            }
            is Timing.After -> {
                app.scheduler.schedule(ids, match.description, command.turnOn, System.currentTimeMillis() + timing.delay.inWholeMilliseconds)
                Outcome("OK, I'll turn $verb $what in ${Durations.format(timing.delay)}.", success = true)
            }
            is Timing.At -> {
                val now = LocalDateTime.now()
                val at = timing.nextOccurrence(now)
                val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                app.scheduler.schedule(ids, match.description, command.turnOn, millis)
                val day = if (at.toLocalDate() != now.toLocalDate()) " tomorrow" else ""
                Outcome("OK, I'll turn $verb $what at ${at.format(TIME_FORMAT)}$day.", success = true)
            }
            is Timing.For -> {
                val failures = home.setOnOff(ids, command.turnOn)
                val outcome = switchReply(
                    failures, ids.size, names,
                    "Turning $verb $what for ${Durations.format(timing.duration)}.", verb, what,
                )
                val succeeded = ids.filterNot { it in failures }
                if (succeeded.isNotEmpty()) {
                    app.scheduler.schedule(
                        succeeded, match.description, !command.turnOn,
                        System.currentTimeMillis() + timing.duration.inWholeMilliseconds,
                    )
                }
                outcome
            }
        }
    }

    private fun switchReply(
        failures: Map<String, String>,
        total: Int,
        names: Map<String, String>,
        successReply: String,
        verb: String,
        what: String,
    ): Outcome = when {
        failures.isEmpty() -> Outcome(successReply, success = true)
        failures.size == total -> Outcome("I couldn't turn $verb $what: ${failures.values.first()}", success = false)
        else -> {
            val missed = failures.keys.mapNotNull(names::get).joinToString(", ")
            Outcome("Turned $verb ${total - failures.size} of $total, but $missed didn't respond.", success = false)
        }
    }

    /** "Living Room lights" -> "the Living Room lights"; "everything" stays as is. */
    private fun spoken(description: String): String =
        if (description.startsWith("everything") || description.first().isDigit()) description else "the $description"

    private companion object {
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    }
}
