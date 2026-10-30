package io.github.hyrumrichardson.luma.nlu

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** A spoken date like "tomorrow", "friday" or "march 3rd", worked out against today's date when it's used. */
sealed interface CalendarDate {
    /** The date this means, counting from [today], or null if there's no such date ("february 30"). */
    fun resolve(today: LocalDate): LocalDate?

    /** "today", "tomorrow", "in 3 days". */
    data class InDays(val days: Int) : CalendarDate {
        override fun resolve(today: LocalDate): LocalDate = today.plusDays(days.toLong())
    }

    /** "friday" is the coming one, today included; "next friday" leaves today out. */
    data class Weekday(val day: DayOfWeek, val next: Boolean) : CalendarDate {
        override fun resolve(today: LocalDate): LocalDate =
            today.with(if (next) TemporalAdjusters.next(day) else TemporalAdjusters.nextOrSame(day))
    }

    /** "march 3rd": this year's, or next year's once it has passed. */
    data class MonthDay(val month: Month, val day: Int) : CalendarDate {
        override fun resolve(today: LocalDate): LocalDate? =
            (today.year..today.year + 4).firstNotNullOfOrNull { year ->
                // Leap days only exist some years.
                YearMonth.of(year, month).takeIf { day <= it.lengthOfMonth() }?.atDay(day)?.takeUnless { it.isBefore(today) }
            }
    }

    /** "the 15th": this month's, or the next month's that has one once it has passed. */
    data class DayOfMonth(val day: Int) : CalendarDate {
        override fun resolve(today: LocalDate): LocalDate? =
            (0L..12L).firstNotNullOfOrNull { ahead ->
                YearMonth.from(today).plusMonths(ahead)
                    .takeIf { day <= it.lengthOfMonth() }?.atDay(day)?.takeUnless { it.isBefore(today) }
            }
    }
}

/** Finds spoken dates in [NumberWords.normalize]d text. */
object CalendarDates {
    private const val ON = """(?:\b(?:on|for)\s+)?"""
    private const val MONTH =
        """(?<month>january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sept|sep|oct|nov|dec)"""
    private const val DAY =
        """(?:(?<num>\d{1,2})(?:st|nd|rd|th)?|(?:(?<tens>20|30)\s+)?(?<word>first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|eleventh|twelfth|thirteenth|fourteenth|fifteenth|sixteenth|seventeenth|eighteenth|nineteenth|twentieth|thirtieth))"""
    private val ordinals = listOf(
        "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth",
        "eleventh", "twelfth", "thirteenth", "fourteenth", "fifteenth", "sixteenth", "seventeenth",
        "eighteenth", "nineteenth", "twentieth",
    ).withIndex().associate { (i, word) -> word to i + 1 } + ("thirtieth" to 30)

    private val forms: List<Pair<Regex, (MatchResult) -> CalendarDate?>> = listOf(
        Regex("""$ON\b(?:the\s+)?day after tomorrow\b""") to { _ -> CalendarDate.InDays(2) },
        Regex("""$ON\b(?:today|tonight|this (?:morning|afternoon|evening))\b""") to { _ -> CalendarDate.InDays(0) },
        Regex("""$ON\btomorrow\b""") to { _ -> CalendarDate.InDays(1) },
        Regex("""\bin\s+(?<n>\d+|an?)\s+(?<unit>days?|weeks?)\b""") to { m ->
            val n = m.groups["n"]!!.value.toIntOrNull() ?: 1
            CalendarDate.InDays(if (m.groups["unit"]!!.value.startsWith("week")) n * 7 else n)
        },
        Regex("""$ON\b$MONTH\s+(?:the\s+)?$DAY\b""") to { m -> monthDay(m) },
        Regex("""$ON\b(?:the\s+)?$DAY\s+(?:of\s+)?$MONTH\b""") to { m -> monthDay(m) },
        Regex("""$ON\b(?:(?<which>this|next|coming)\s+)?(?<weekday>monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""") to { m ->
            CalendarDate.Weekday(DayOfWeek.valueOf(m.groups["weekday"]!!.value.uppercase()), next = m.groups["which"]?.value == "next")
        },
        // Without a month, only "on the 15th", so a stray number isn't read as a date.
        Regex("""\b(?:on|for)\s+the\s+$DAY\b""") to { m -> day(m)?.let { CalendarDate.DayOfMonth(it) } },
    )

    /** The earliest date phrase in [text] at or after [startIndex], with a leading "on" or "for", and where it is. */
    fun find(text: String, startIndex: Int = 0): Pair<CalendarDate, IntRange>? =
        forms.mapNotNull { (regex, read) ->
            regex.find(text, startIndex)?.let { m -> read(m)?.let { it to m.range } }
        }.minWithOrNull(compareBy<Pair<CalendarDate, IntRange>> { it.second.first }.thenByDescending { it.second.last })

    private fun monthDay(m: MatchResult): CalendarDate? {
        val month = m.groups["month"]!!.value.take(3).let { prefix -> Month.values().first { it.name.lowercase().startsWith(prefix) } }
        return day(m)?.let { CalendarDate.MonthDay(month, it) }
    }

    private fun day(m: MatchResult): Int? {
        val g = m.groups
        val day = g["num"]?.value?.toInt()
            ?: ((g["tens"]?.value?.toInt() ?: 0) + (ordinals[g["word"]?.value] ?: return null))
        return day.takeIf { it in 1..31 }
    }
}
