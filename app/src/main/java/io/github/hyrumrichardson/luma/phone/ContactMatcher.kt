package io.github.hyrumrichardson.luma.phone

import java.text.Normalizer
import java.util.Locale

/** A contact with at least one phone number. [numbers] are ordered best first (see [Contacts]). */
data class Contact(val id: Long, val name: String, val numbers: List<String>)

sealed interface ContactMatch {
    /** [leftover] is what came after the name, when the name ran on into the message. */
    data class Found(val contact: Contact, val leftover: String?) : ContactMatch

    data class Ambiguous(val candidates: List<Contact>, val leftover: String?) : ContactMatch

    data object NotFound : ContactMatch
}

/** Finds the contact a spoken name refers to. */
object ContactMatcher {
    private val ignoredLeading = Regex("""^(?:my|the|to)\s+""")

    private fun clean(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("""\p{Mn}+"""), "") // José -> Jose
            .lowercase(Locale.US)
            .replace('’', '\'')
            .replace(Regex("""'s\b"""), "") // "mom's phone"
            .replace("'", "") // "O'Brien" -> "obrien"
            .replace(Regex("""[^a-z0-9 ]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .replace(ignoredLeading, "")

    /**
     * Matches all of [spoken] against [contacts]. When nothing matches and [allowLeftover] is set, it
     * tries shorter and shorter runs of words from the start, so "mom running late" finds "Mom" with
     * "running late" left over.
     */
    fun match(spoken: String, contacts: List<Contact>, allowLeftover: Boolean = false): ContactMatch {
        whole(spoken, contacts)?.let { return it.toMatch(null) }
        if (!allowLeftover) return ContactMatch.NotFound
        val words = spoken.trim().split(Regex("""\s+"""))
        for (n in words.size - 1 downTo 1) {
            val found = whole(words.take(n).joinToString(" "), contacts) ?: continue
            return found.toMatch(words.drop(n).joinToString(" "))
        }
        return ContactMatch.NotFound
    }

    private fun List<Contact>.toMatch(leftover: String?): ContactMatch =
        if (size == 1) ContactMatch.Found(single(), leftover) else ContactMatch.Ambiguous(this, leftover)

    /**
     * The contacts [spoken] names, from the strictest kind of match that finds any: the whole name,
     * then the first name or a nickname-like single word, then every spoken word starting a word of
     * the name ("john s" for "John Smith"). Null if none match.
     */
    private fun whole(spoken: String, contacts: List<Contact>): List<Contact>? {
        val said = clean(spoken)
        if (said.isEmpty()) return null
        val saidWords = said.split(' ')
        val named = contacts.map { it to clean(it.name) }
        val tiers: List<(String) -> Boolean> = listOf(
            { name -> name == said },
            { name -> saidWords.size == 1 && name.split(' ').first() == said },
            { name -> saidWords.size == 1 && said in name.split(' ') },
            { name ->
                val nameWords = name.split(' ')
                saidWords.size > 1 && saidWords.size <= nameWords.size &&
                    saidWords.zip(nameWords).all { (s, n) -> n.startsWith(s) }
            },
        )
        for (tier in tiers) {
            val hits = named.filter { (_, name) -> tier(name) }.map { it.first }.distinctBy { it.id }
            if (hits.isNotEmpty()) return hits
        }
        return null
    }
}
