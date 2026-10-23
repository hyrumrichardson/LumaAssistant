package io.github.hyrumrichardson.luma.nlu

import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.time.Duration

/** Something Luma understood the user asking for. */
sealed interface Command {
    /** [length] is null when the user didn't say how long ("start a timer"). */
    data class SetTimer(val length: Duration?, val label: String?) : Command

    data class Navigate(val destination: String, val mode: TravelMode) : Command

    /**
     * [target] is the device phrase as spoken, e.g. "living room lights" or "the lamp and the fan".
     * [turnOn] is null for "toggle the lamp".
     */
    data class ControlDevice(val target: String, val turnOn: Boolean?, val timing: Timing) : Command

    /** The phone's own flashlight. [turnOn] is null for "toggle the flashlight". */
    data class Flashlight(val turnOn: Boolean?) : Command

    /** An explicit "search for…", and also what anything Luma doesn't understand becomes. */
    data class WebSearch(val query: String) : Command
}

enum class TravelMode(val mapsCode: String) {
    DRIVING("d"),
    WALKING("w"),
    BICYCLING("b"),
}

/** When a device command should take effect. */
sealed interface Timing {
    data object Now : Timing

    /** "turn off the lights in 10 minutes" */
    data class After(val delay: Duration) : Timing

    /** "turn on the fan for 30 minutes": act now, then undo after [duration]. */
    data class For(val duration: Duration) : Timing

    /** "turn off the lights at 10:30 pm". [pm] is null when the user didn't say am or pm. */
    data class At(val hour: Int, val minute: Int, val pm: Boolean?) : Timing {
        /** The first matching time strictly after [now]. Without am/pm, picks whichever comes first. */
        fun nextOccurrence(now: LocalDateTime): LocalDateTime {
            val candidates = when {
                hour > 12 || hour == 0 -> listOf(hour)
                pm == null -> listOf(hour % 12, hour % 12 + 12)
                pm -> listOf(hour % 12 + 12)
                else -> listOf(hour % 12)
            }
            return candidates.map { h ->
                val today = now.toLocalDate().atTime(LocalTime.of(h, minute))
                if (today.isAfter(now)) today else today.plusDays(1)
            }.min()
        }
    }
}
