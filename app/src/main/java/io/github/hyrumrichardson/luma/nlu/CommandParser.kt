package io.github.hyrumrichardson.luma.nlu

import java.util.Locale

/**
 * Turns a transcribed utterance into a [Command] with hand-written patterns, in the spirit of
 * Dicio's sentence templates. Returns null for anything Luma doesn't handle.
 */
object CommandParser {

    fun parse(utterance: String): Command? {
        parseNavigation(utterance)?.let { return it }
        val text = normalize(utterance)
        if (text.isEmpty()) return null
        return parseDevice(text, allowLoose = false)
            ?: parseTimer(text)
            ?: parseDevice(text, allowLoose = true)
    }

    // --- Text cleanup ------------------------------------------------------------------------

    private val leadingFiller = Regex(
        """^(?:(?:hey|hi|ok|okay)\s+)?(?:luma\s+)?(?:(?:please|can you|could you|would you|will you|""" +
            """go ahead and|i want you to|i'd like you to|i would like you to|id like you to)\s+)*""",
    )
    private val trailingFiller = Regex("""(?:\s+(?:please|for me|right now|now|thanks|thank you))+$""")

    /** Lowercases, strips punctuation and filler words, and converts number words to digits. */
    internal fun normalize(utterance: String): String {
        var t = utterance.lowercase(Locale.US)
            .replace('’', '\'')
            .replace(Regex("""\b([ap])\.\s?m\b\.?"""), "$1m") // "p.m." -> "pm"
            .replace(Regex("""(?<!\d)\.|\.(?!\d)"""), " ") // keep decimal points only
            .replace("'", "")
            .replace(Regex("""[^a-z0-9:.\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        t = NumberWords.normalize(t)
        t = t.replace(leadingFiller, "").replace(trailingFiller, "").trim()
        return t
    }

    // --- Navigation --------------------------------------------------------------------------

    private val navigationRegex = Regex(
        """^(?:(?:hey|hi|ok|okay)\s+)?(?:luma[,\s]+)?(?:(?:please|can you|could you|would you)\s+)*""" +
            """(?:""" +
            """(?:give me |get me |get |show me |find |find me |what are the |i need |start )?""" +
            """(?:(?<modeWord>walking|driving|biking|cycling|bicycling|bike)\s+)?directions""" +
            """|start navigation|start navigating|navigate(?:\s+me)?|navigation""" +
            """|take me|guide me|route me|show me the way""" +
            """|how (?:do|can) i get|how (?:do|can) i (?<modeVerb2>drive|walk|bike|cycle)""" +
            // Bare movement verbs only count when followed by a destination ("walk the dog" isn't one).
            """|(?<modeVerb>drive|walk|bike|cycle|ride)(?:\s+me)?(?=\s+(?:to|towards|toward|home)\b)""" +
            """)""" +
            """(?:\s+(?:to|towards|toward|for|over to))?\s+(?:the\s+)?""" +
            """(?<dest>.+?)""" +
            """(?:\s+(?<modeSuffix>by car|by bike|by bicycle|on foot|by foot|on a bike|walking|biking|cycling|driving))?""" +
            """(?:\s+(?:using|with|on|in|via)\s+(?:google\s+)?maps)?""" +
            """(?:\s+please)?[\s.!?]*$""",
        RegexOption.IGNORE_CASE,
    )

    private fun parseNavigation(utterance: String): Command.Navigate? {
        val match = navigationRegex.find(utterance.trim()) ?: return null
        val g = match.groups
        val destination = g["dest"]!!.value.trim().trimEnd('.', '!', '?', ',')
        if (destination.isEmpty()) return null
        val modeHint = (g["modeSuffix"] ?: g["modeWord"] ?: g["modeVerb"] ?: g["modeVerb2"])
            ?.value?.lowercase(Locale.US)
        val mode = when {
            modeHint == null -> TravelMode.DRIVING
            modeHint.contains("walk") || modeHint.contains("foot") -> TravelMode.WALKING
            modeHint.contains("bike") || modeHint.contains("cycl") || modeHint.contains("bicycl") ||
                modeHint == "ride" -> TravelMode.BICYCLING
            else -> TravelMode.DRIVING
        }
        return Command.Navigate(destination, mode)
    }

    // --- Timers ------------------------------------------------------------------------------

    private val timerWord = Regex("""\b(?:timer|countdown|count down)\b""")
    private val labelCalled = Regex("""\b(?:called|named|labelled|labeled|titled)\s+(.+)$""")
    private val labelFor = Regex("""\b(?:timer|countdown)\s+for\s+(?:the\s+|my\s+)?(.+)$""")
    private val labelBefore = Regex(
        """^(?:(?:set|start|create|make|begin|put on|add)\s+)?(?:me\s+)?(?:(?:a|an|the|my)\s+)?(.+?)\s+(?:timer|countdown)\b""",
    )
    private val notLabels = setOf("new", "quick", "another", "a", "an", "the", "set", "set a", "start a", "me a")

    private fun parseTimer(text: String): Command.SetTimer? {
        if (!timerWord.containsMatchIn(text)) return null
        val found = Durations.find(text)
        var rest = text
        if (found != null) {
            val (_, range) = found
            val before = text.substring(0, range.first).trimEnd()
            val beforeWithoutLink = before.removeSuffix(" for").removeSuffix(" of")
                .let { if (it == "for" || it == "of") "" else it }
            rest = (beforeWithoutLink + " " + text.substring(range.last + 1)).replace(Regex("""\s+"""), " ").trim()
            // "set a 10 minute timer" leaves "set a timer"
        }
        val label = (labelCalled.find(rest) ?: labelFor.find(rest) ?: labelBefore.find(rest))
            ?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotEmpty() && it !in notLabels && !timerWord.containsMatchIn(it) }
            ?.replaceFirstChar { it.titlecase(Locale.US) }
        return Command.SetTimer(found?.first, label)
    }

    // --- Device control ----------------------------------------------------------------------

    private const val DEVICE_VERB = """(?:turn|switch|shut|power|flip|put)"""
    private val verbFirst = Regex("""^(?:set (?:a |an )?timer (?:to|that will)\s+)?$DEVICE_VERB\s+(on|off)\s+(.+)$""")
    private val verbAround = Regex("""^(?:set (?:a |an )?timer (?:to|that will)\s+)?$DEVICE_VERB\s+(.+?)\s+(on|off)$""")
    // "turn the lights off in the kitchen"
    private val verbAroundPlace = Regex("""^$DEVICE_VERB\s+(.+?)\s+(on|off)\s+((?:in|at|on)\s+.+)$""")
    private val killRegex = Regex("""^(?:kill|cut)\s+(?:the\s+)?(.+)$""")
    private val looseRegex = Regex("""^(.+?)\s+(on|off)$""")

    private val afterRegex = Regex("""\b(?:in|after)\s+(?:about\s+|another\s+)?(${Durations.PATTERN})(?:\s+from now)?\b""")
    private val fromNowRegex = Regex("""\b(${Durations.PATTERN})\s+from now\b""")
    private val forRegex = Regex("""\bfor\s+(?:the next\s+|another\s+|about\s+)?(${Durations.PATTERN})\b""")
    private val atRegex = Regex(
        """\b(?:at|by)\s+(?:(\d{1,2})(?:(?::|\s)(\d{2}))?(?:\s*(am|pm|oclock|o clock))?|(noon|midnight))\b""",
    )
    private val dayPartRegex = Regex("""\b(tonight|this evening|this afternoon|this morning|in the morning|in the evening)\b""")

    private fun parseDevice(text: String, allowLoose: Boolean): Command.ControlDevice? {
        var rest = text
        var timing: Timing = Timing.Now

        // Day-part words only hint at am/pm for an "at" time; strip them either way.
        val dayPart = dayPartRegex.find(rest)?.value
        if (dayPart != null) rest = rest.replace(dayPartRegex, " ")

        fun takeDuration(regex: Regex, wrap: (kotlin.time.Duration) -> Timing): Boolean {
            val m = regex.find(rest) ?: return false
            val duration = Durations.parse(m.groupValues[1]) ?: return false
            timing = wrap(duration)
            rest = rest.removeRange(m.range)
            return true
        }

        val gotTiming = takeDuration(afterRegex) { Timing.After(it) } ||
            takeDuration(fromNowRegex) { Timing.After(it) } ||
            takeDuration(forRegex) { Timing.For(it) }
        if (!gotTiming) {
            atRegex.find(rest)?.let { m ->
                val (hourText, minuteText, suffix, named) = m.destructured
                val at = when (named) {
                    "noon" -> Timing.At(12, 0, true)
                    "midnight" -> Timing.At(0, 0, null)
                    else -> {
                        val hour = hourText.toInt()
                        val minute = minuteText.ifEmpty { "0" }.toInt()
                        val pm = when {
                            suffix == "am" -> false
                            suffix == "pm" -> true
                            dayPart == null -> null
                            dayPart.contains("morning") -> false
                            else -> true
                        }
                        if (hour > 23 || minute > 59) null else Timing.At(hour, minute, pm)
                    }
                }
                if (at != null) {
                    timing = at
                    rest = rest.removeRange(m.range)
                }
            }
        }

        rest = rest.replace(Regex("""\s+"""), " ").trim()
            .removePrefix("and ").removePrefix("then ").removeSuffix(" and").removeSuffix(" then").trim()

        val (target, turnOn) = verbFirst.find(rest)?.let { it.groupValues[2] to (it.groupValues[1] == "on") }
            ?: verbAround.find(rest)?.let { it.groupValues[1] to (it.groupValues[2] == "on") }
            ?: verbAroundPlace.find(rest)?.let { "${it.groupValues[1]} ${it.groupValues[3]}" to (it.groupValues[2] == "on") }
            ?: killRegex.find(rest)?.let { it.groupValues[1] to false }
            ?: (if (allowLoose) looseRegex.find(rest)?.let { it.groupValues[1] to (it.groupValues[2] == "on") } else null)
            ?: return null

        val cleanTarget = target.trim()
            .replace(Regex("""^(?:the|my|all the|all of the|all my|all)\s+"""), "")
            .trim()
        if (cleanTarget.isEmpty()) return null
        // "lights on" style phrasing would otherwise match sentences like "carry on".
        if (allowLoose && cleanTarget.split(' ').size > 5) return null
        return Command.ControlDevice(cleanTarget, turnOn, timing)
    }
}
