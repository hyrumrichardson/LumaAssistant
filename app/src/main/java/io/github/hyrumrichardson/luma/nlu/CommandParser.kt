package io.github.hyrumrichardson.luma.nlu

import java.util.Locale

/**
 * Turns a transcribed utterance into a [Command] with hand-written patterns, in the spirit of
 * Dicio's sentence templates.
 */
object CommandParser {

    /** Parses [utterance]; anything that isn't a known command becomes a web search. */
    fun parse(utterance: String): Command? =
        parseKnown(utterance) ?: utterance.takeIf { it.isNotBlank() }?.let { Command.WebSearch(searchQuery(it)) }

    /** Like [parse], but returns null instead of falling back to a web search. */
    fun parseKnown(utterance: String): Command? {
        parseNavigation(utterance)?.let { return it }
        parseExplicitSearch(utterance)?.let { return it }
        val text = normalize(utterance)
        if (text.isEmpty()) return null
        return parseFlashlight(text)
            ?: parseAlarm(text)
            ?: parseDevice(text, allowLoose = false)
            ?: parseTimer(text)
            ?: parseEvent(text)
            ?: parseDevice(text, allowLoose = true)
    }

    // --- Web search --------------------------------------------------------------------------

    private val explicitSearchRegex = Regex(
        """^(?:(?:hey|hi|ok|okay)\s+)?(?:luma[,\s]+)?(?:(?:please|can you|could you)\s+)*""" +
            """(?:search(?:\s+(?:the web|the internet|online|google|for))*|google|look up|bing)\s+""" +
            """(?<query>.+?)[\s.!?]*$""",
        RegexOption.IGNORE_CASE,
    )
    private val wakePrefix = Regex("""^(?:(?:hey|hi|ok|okay)\s+)?luma\b[,\s]*""", RegexOption.IGNORE_CASE)

    private fun parseExplicitSearch(utterance: String): Command.WebSearch? =
        explicitSearchRegex.find(utterance.trim())?.groups?.get("query")?.value?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(Command::WebSearch)

    /** The utterance as a search query: as spoken, minus "hey Luma" and trailing punctuation. */
    internal fun searchQuery(utterance: String): String {
        val trimmed = utterance.trim()
        return trimmed.replace(wakePrefix, "").trim().trimEnd('.', '!', '?', ',').ifBlank { trimmed }
    }

    // --- Flashlight --------------------------------------------------------------------------

    private const val FLASH = """(?:the\s+|my\s+)?(?:flashlight|flash light|torch|flash|phone light|phones light)"""
    private val flashVerbFirst = Regex("""^(?:turn|switch|put|flip)\s+(on|off)\s+$FLASH$""")
    private val flashVerbAround = Regex("""^(?:(?:turn|switch|put|flip)\s+)?$FLASH\s+(on|off)$""")
    private val flashToggle = Regex("""^(?:toggle\s+)?$FLASH$""")

    private fun parseFlashlight(text: String): Command.Flashlight? {
        (flashVerbFirst.find(text) ?: flashVerbAround.find(text))?.let {
            return Command.Flashlight(turnOn = it.groupValues[1] == "on")
        }
        return if (flashToggle.matches(text)) Command.Flashlight(turnOn = null) else null
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

    // --- Alarms ------------------------------------------------------------------------------

    private val alarmStart = Regex("""^(?:(?:set|create|make|add|schedule|put|start)\b.*\balarm\b|(?:an?\s+)?alarm\b|wake\s+me\b)""")
    private val alarmAt = Regex("""\b(?:at|for|to)\s+${ClockTimes.PATTERN}\b""")
    private val standaloneTime = Regex("""\b${ClockTimes.STANDALONE}\b""")
    private val alarmLabelBefore = Regex(
        """^(?:(?:set|create|make|add|schedule|put|start)\s+)?(?:me\s+)?(?:(?:a|an|the|my)\s+)?(?:new\s+)?(.+?)\s+alarm\b""",
    )
    // Wider than the device parser's day parts, which can't take "night" alone ("the night light").
    private val dayHint = Regex("""\b(tonight|morning|afternoon|evening|night)\b""")
    private val dayWords = Regex(
        """\b(?:today|tomorrow|tonight|(?:this|in the|tomorrow)\s+(?:morning|afternoon|evening)|morning|afternoon|evening|at night)\b""",
    )

    private fun parseAlarm(text: String): Command.SetAlarm? {
        if (!alarmStart.containsMatchIn(text)) return null
        var rest = text
        var time: Timing? = null
        val relative = afterRegex.find(rest) ?: fromNowRegex.find(rest) ?: forRegex.find(rest)
        val delay = relative?.let { Durations.parse(it.groupValues[1]) }
        if (relative != null && delay != null) {
            time = Timing.After(delay)
            rest = rest.removeRange(relative.range)
        } else {
            val m = alarmAt.find(rest) ?: standaloneTime.find(rest)
            val at = m?.let { ClockTimes.at(it, dayHint.find(text)?.value) }
            if (m != null && at != null) {
                time = at
                rest = rest.removeRange(m.range)
            }
        }
        rest = rest.replace(dayWords, " ").replace(Regex("""\s+"""), " ").trim()
        val label = (labelCalled.find(rest) ?: alarmLabelBefore.find(rest))
            ?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotEmpty() && it !in notLabels && "alarm" !in it }
            ?.replaceFirstChar { it.titlecase(Locale.US) }
        return Command.SetAlarm(time, label)
    }

    // --- Calendar events ---------------------------------------------------------------------

    private val eventStart = Regex(
        """^(?:set|create|make|add|schedule|book|put|new)\s+(?:up\s+)?(?:me\s+)?(?:(?:an?|my|the)\s+)?(?:new\s+)?""" +
            """(?<before>.*?)\s*\b(?:calendar\s+)?(?<kind>appointment|event|meeting|reservation)s?\b(?<rest>.*)$""",
    )
    private val calendarSuffix = Regex("""\b(?:on|to|in|into)\s+(?:my\s+|the\s+)?(?:google\s+)?calendar\b""")
    private val eventCalled = Regex("""\b(?:called|named|titled|labeled|labelled)\s+""")
    private val eventTime = Regex("""\b(?:at|from)\s+${ClockTimes.PATTERN}\b""")
    private val eventLength = Regex("""\bfor\s+(?:about\s+)?(${Durations.PATTERN})\b""")
    private val leftoverTitle = Regex("""^(?:for|about)\s+(.+)$""")
    private val prepositions = setOf("for", "on", "at", "from", "in", "to", "and")
    private val notEventTitles = setOf("new", "a", "an", "the", "my", "calendar", "google calendar")

    private fun parseEvent(text: String): Command.CreateEvent? {
        val start = eventStart.find(text) ?: return null
        val kind = start.groups["kind"]!!.value
        val before = start.groups["before"]!!.value.trim()
        var rest = start.groups["rest"]!!.value.replace(calendarSuffix, " ")
        val dayPart = dayHint.find(rest)?.value

        // A title after "called" runs until a date, time or length starts. It keeps at least one
        // word, so "called monday prep for friday" is titled "monday prep".
        var title: String? = null
        val called = eventCalled.find(rest)
        if (called != null) {
            val from = called.range.last + 1
            val firstWordEnd = rest.indexOf(' ', from).let { if (it < 0) rest.length else it }
            val end = listOfNotNull(
                CalendarDates.find(rest, firstWordEnd)?.second?.first,
                eventTime.find(rest, firstWordEnd)?.range?.first,
                standaloneTime.find(rest, firstWordEnd)?.range?.first,
                eventLength.find(rest, firstWordEnd)?.range?.first,
                dayWords.find(rest, firstWordEnd)?.range?.first,
            ).minOrNull() ?: rest.length
            title = rest.substring(from, end).trim()
            rest = rest.removeRange(called.range.first, end)
        }

        var length: kotlin.time.Duration? = null
        val lengthMatch = eventLength.find(rest)
        val parsedLength = lengthMatch?.let { Durations.parse(it.groupValues[1]) }
        if (lengthMatch != null && parsedLength != null) {
            length = parsedLength
            rest = rest.removeRange(lengthMatch.range)
        }

        var time: Timing.At? = null
        val timeMatch = eventTime.find(rest) ?: standaloneTime.find(rest)
        val parsedTime = timeMatch?.let { ClockTimes.at(it, dayPart) }
        if (timeMatch != null && parsedTime != null) {
            time = parsedTime
            rest = rest.removeRange(timeMatch.range)
        }

        val date = CalendarDates.find(rest)?.let { (date, range) ->
            rest = rest.removeRange(range)
            date
        }

        val leftover = rest.replace(dayWords, " ").replace(Regex("""\s+"""), " ").trim()
        val chosenTitle = title?.takeIf { it.isNotEmpty() }
            ?: leftoverTitle.find(leftover)?.groupValues?.get(1)
            ?: leftover.takeIf { it.startsWith("with ") }?.let { "$kind $it" }
            ?: before.takeIf { it.isNotEmpty() && it !in notEventTitles }
        val cleanTitle = chosenTitle
            ?.split(' ')?.dropLastWhile { it in prepositions }?.joinToString(" ")
            ?.takeIf { it.isNotBlank() }
            ?.replaceFirstChar { it.titlecase(Locale.US) }
        return Command.CreateEvent(cleanTitle, date, time, length)
    }

    // --- Device control ----------------------------------------------------------------------

    private const val DEVICE_VERB = """(?:turn|switch|shut|power|flip|put)"""
    private val verbFirst = Regex("""^(?:set (?:a |an )?timer (?:to|that will)\s+)?$DEVICE_VERB\s+(on|off)\s+(.+)$""")
    private val verbAround = Regex("""^(?:set (?:a |an )?timer (?:to|that will)\s+)?$DEVICE_VERB\s+(.+?)\s+(on|off)$""")
    // "turn the lights off in the kitchen"
    private val verbAroundPlace = Regex("""^$DEVICE_VERB\s+(.+?)\s+(on|off)\s+((?:in|at|on)\s+.+)$""")
    private val killRegex = Regex("""^(?:kill|cut)\s+(?:the\s+)?(.+)$""")
    private val enableRegex = Regex("""^(enable|activate|disable|deactivate)\s+(.+)$""")
    private val toggleRegex = Regex("""^(?:toggle|flip)\s+(.+)$""")
    // "lights out", "turn the lamp out", "put out the kitchen lights". Only for lights, since "out" means other things too.
    private val lightsOutRegex = Regex("""^(?:(?:put|turn)\s+)?(.*\b(?:lights?|lamps?))\s+out$""")
    private val putOutRegex = Regex("""^put\s+out\s+(.*\b(?:lights?|lamps?))$""")
    // Loose only, so "start a timer" is still a timer.
    private val startStopRegex = Regex("""^(start|stop)\s+(.+)$""")
    private val looseRegex = Regex("""^(.+?)\s+(on|off)$""")

    private val afterRegex = Regex("""\b(?:in|after)\s+(?:about\s+|another\s+)?(${Durations.PATTERN})(?:\s+from now)?\b""")
    private val fromNowRegex = Regex("""\b(${Durations.PATTERN})\s+from now\b""")
    private val forRegex = Regex("""\bfor\s+(?:the next\s+|another\s+|about\s+)?(${Durations.PATTERN})\b""")
    private val atRegex = Regex("""\b(?:at|by)\s+${ClockTimes.PATTERN}\b""")
    private val dayPartRegex = ClockTimes.dayPartRegex

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
                ClockTimes.at(m, dayPart)?.let { at ->
                    timing = at
                    rest = rest.removeRange(m.range)
                }
            }
        }

        rest = rest.replace(Regex("""\s+"""), " ").trim()
            .removePrefix("and ").removePrefix("then ").removeSuffix(" and").removeSuffix(" then").trim()

        val (target: String, turnOn: Boolean?) = verbFirst.find(rest)?.let { it.groupValues[2] to (it.groupValues[1] == "on") }
            ?: verbAround.find(rest)?.let { it.groupValues[1] to (it.groupValues[2] == "on") }
            ?: verbAroundPlace.find(rest)?.let { "${it.groupValues[1]} ${it.groupValues[3]}" to (it.groupValues[2] == "on") }
            ?: killRegex.find(rest)?.let { it.groupValues[1] to false }
            ?: enableRegex.find(rest)?.let { it.groupValues[2] to (it.groupValues[1] == "enable" || it.groupValues[1] == "activate") }
            ?: toggleRegex.find(rest)?.let { it.groupValues[1] to null }
            ?: (lightsOutRegex.find(rest) ?: putOutRegex.find(rest))?.let { it.groupValues[1] to false }
            ?: (if (allowLoose) startStopRegex.find(rest)?.let { it.groupValues[2] to (it.groupValues[1] == "start") } else null)
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
