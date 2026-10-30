package io.github.hyrumrichardson.luma.nlu

/** Parses spoken clock times like "10:30 pm", "7 30", "6 oclock" or "noon" into [Timing.At]. */
object ClockTimes {
    /**
     * A regex fragment for a time that follows a word like "at": "7", "7:30", "7 30 pm", "noon". It
     * has the named groups [at] reads, so use it once per regex, on [NumberWords.normalize]d text.
     */
    const val PATTERN =
        """(?:(?<hour>\d{1,2})(?:(?::|\s)(?<minute>\d{2}))?(?:\s*(?<suffix>am|pm|oclock|o clock))?|(?<named>noon|midnight))"""

    /** Like [PATTERN], but only times that read as times on their own: "7 pm", "6:30", "noon". Not "7". */
    const val STANDALONE =
        """(?:(?<hour>\d{1,2})(?=:\d{2}|(?:\s\d{2})?\s*(?:am|pm|oclock|o clock))(?:(?::|\s)(?<minute>\d{2}))?""" +
            """(?:\s*(?<suffix>am|pm|oclock|o clock))?|(?<named>noon|midnight))"""

    /** Phrases that say am or pm without naming a time, like "tonight" or "in the morning". */
    val dayPartRegex = Regex("""\b(tonight|this evening|this afternoon|this morning|in the morning|in the evening)\b""")

    /**
     * The time a [PATTERN] or [STANDALONE] match names, or null if it isn't a real time. [dayPart],
     * like "tonight" or "morning", settles am or pm when the time doesn't say.
     */
    fun at(match: MatchResult, dayPart: String?): Timing.At? {
        val g = match.groups
        when (g["named"]?.value) {
            "noon" -> return Timing.At(12, 0, true)
            "midnight" -> return Timing.At(0, 0, null)
        }
        val hour = g["hour"]?.value?.toInt() ?: return null
        val minute = g["minute"]?.value?.toInt() ?: 0
        val suffix = g["suffix"]?.value
        val pm = when {
            suffix == "am" -> false
            suffix == "pm" -> true
            dayPart == null -> null
            dayPart.contains("morning") -> false
            else -> true
        }
        return if (hour > 23 || minute > 59) null else Timing.At(hour, minute, pm)
    }
}
