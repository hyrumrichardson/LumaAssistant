package io.github.hyrumrichardson.luma.nlu

import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Parses and formats spoken durations like "an hour and a half" or "1 hour 20 minutes". */
object Durations {
    private const val AMOUNT = """(?:\d+(?:\.\d+)?|an?)"""
    private const val UNIT = """(?:hours?|hrs?|minutes?|mins?|seconds?|secs?)"""
    private const val PART = """(?:$AMOUNT\s+(?:and\s+a\s+half\s+)?$UNIT(?:\s+and\s+a\s+half)?""" +
        """|half\s+(?:an?\s+)?hour|(?:a\s+)?quarter\s+(?:of\s+)?(?:an?\s+)?hour)"""

    /**
     * A regex fragment (no capturing groups) matching a whole duration phrase, for embedding in
     * other patterns. It expects text that has been through [NumberWords.normalize].
     */
    const val PATTERN = """$PART(?:(?:\s*,\s*|\s+and\s+|\s+)$PART)*"""

    private val partRegex = Regex(
        """(?<amount>\d+(?:\.\d+)?|an?)\s+(?<preHalf>and\s+a\s+half\s+)?(?<unit>$UNIT)(?<postHalf>\s+and\s+a\s+half)?""" +
            """|(?<halfHour>half\s+(?:an?\s+)?hour)|(?<quarterHour>(?:a\s+)?quarter\s+(?:of\s+)?(?:an?\s+)?hour)""",
    )
    private val wholeRegex = Regex("""\b$PATTERN\b""")

    /** Finds the first duration phrase in [text] and returns its value and location. */
    fun find(text: String): Pair<Duration, IntRange>? {
        val match = wholeRegex.find(text) ?: return null
        val value = parse(match.value) ?: return null
        return value to match.range
    }

    /** Parses a string that is entirely a duration phrase, e.g. "1 hour and 30 minutes". */
    fun parse(text: String): Duration? {
        var total = Duration.ZERO
        var any = false
        for (m in partRegex.findAll(text)) {
            any = true
            val g = m.groups
            total += when {
                g["halfHour"] != null -> 30.minutes
                g["quarterHour"] != null -> 15.minutes
                else -> {
                    val amountText = g["amount"]!!.value
                    var amount = if (amountText == "a" || amountText == "an") 1.0 else amountText.toDouble()
                    if (g["preHalf"] != null || g["postHalf"] != null) amount += 0.5
                    val unitSeconds = when (g["unit"]!!.value.first()) {
                        'h' -> 3600.0
                        'm' -> 60.0
                        else -> 1.0
                    }
                    (amount * unitSeconds).roundToLong().seconds
                }
            }
        }
        return total.takeIf { any && it > Duration.ZERO }
    }

    /** Formats a duration the way it would be said, e.g. "1 hour and 30 minutes". */
    fun format(duration: Duration): String {
        val totalSeconds = duration.inWholeSeconds
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        val parts = buildList {
            if (h > 0) add(plural(h, "hour"))
            if (m > 0) add(plural(m, "minute"))
            if (s > 0 || isEmpty()) add(plural(s, "second"))
        }
        return when (parts.size) {
            1 -> parts[0]
            else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
        }
    }

    private fun plural(n: Long, unit: String) = if (n == 1L) "1 $unit" else "$n ${unit}s"

    // Convenience for tests and callers that build durations from parts.
    internal fun of(hours: Int = 0, minutes: Int = 0, seconds: Int = 0): Duration =
        hours.hours + minutes.minutes + seconds.seconds
}
