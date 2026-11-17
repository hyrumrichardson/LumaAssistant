package io.github.hyrumrichardson.luma.assist

import android.Manifest
import io.github.hyrumrichardson.luma.LumaApp
import io.github.hyrumrichardson.luma.nlu.Answer
import io.github.hyrumrichardson.luma.nlu.Answers
import io.github.hyrumrichardson.luma.nlu.Command
import io.github.hyrumrichardson.luma.phone.Contact
import io.github.hyrumrichardson.luma.phone.ContactMatch
import io.github.hyrumrichardson.luma.phone.ContactMatcher
import io.github.hyrumrichardson.luma.phone.TextMessages
import java.util.Locale

/**
 * The texting conversation: "Text mom" → "What's the message?" → "I'm on my way" →
 * "You said "I'm on my way". Would you like to send it to Mom?" → "Yes" → sent.
 * Nothing is sent without a yes.
 */
class Texting(private val app: LumaApp) {

    suspend fun start(command: Command.SendText): Outcome {
        if (!app.texts.hasTelephony) return Outcome("This phone can't send texts.", success = false)
        if (!app.contacts.canRead || !app.texts.canSend) {
            return Outcome(
                "Luma needs permission to read your contacts and send texts. You can allow it in the Luma app.",
                success = false,
            )
        }
        val contacts = app.contacts.withPhoneNumbers()
        if (contacts.isEmpty()) return Outcome("I couldn't find any contacts with phone numbers.", success = false)
        // Without "saying", the name may run on into the message: "text mom running late".
        return when (val match = ContactMatcher.match(command.recipient, contacts, allowLeftover = command.message == null)) {
            ContactMatch.NotFound -> Outcome("I couldn't find \"${command.recipient}\" in your contacts.", success = false)
            is ContactMatch.Found -> next(match.contact, command.message ?: match.leftover)
            is ContactMatch.Ambiguous -> choose(match.candidates, command.message ?: match.leftover, command.recipient)
        }
    }

    private fun next(contact: Contact, message: String?): Outcome =
        if (message.isNullOrBlank()) askMessage(contact) else confirm(contact, tidy(message))

    private fun choose(candidates: List<Contact>, message: String?, spoken: String, retry: Boolean = false): Outcome {
        val question = when {
            candidates.size > MAX_LISTED -> "I found ${candidates.size} contacts called \"$spoken\". What's their full name?"
            else -> "Which one: ${listing(candidates.map { it.name })}?"
        }
        return Outcome(
            reply = if (retry) "Sorry, I didn't get that. $question" else question,
            success = true,
            followUp = { answers ->
                when {
                    answers.any(Answers::isCancel) -> cancelled()
                    else -> pick(answers, candidates)?.let { next(it, message) }
                        ?: choose(candidates, message, spoken, retry = true)
                }
            },
        )
    }

    /** The candidate the answer names, by name or by position ("the second one"). */
    private fun pick(answers: List<String>, candidates: List<Contact>): Contact? {
        answers.forEach { answer ->
            (ContactMatcher.match(answer, candidates) as? ContactMatch.Found)?.let { return it.contact }
        }
        if (candidates.size > MAX_LISTED) return null
        val words = answers.first().lowercase(Locale.US)
        val index = ORDINALS.entries.firstOrNull { (pattern, _) -> Regex("""\b$pattern\b""").containsMatchIn(words) }?.value
        return when (index) {
            null -> null
            -1 -> candidates.last()
            else -> candidates.getOrNull(index)
        }
    }

    private fun askMessage(contact: Contact, question: String = "What's the message?"): Outcome = Outcome(
        reply = question,
        success = true,
        followUp = { answers ->
            val text = answers.first().trim()
            when {
                Answers.isCancel(text) -> cancelled()
                text.isEmpty() -> askMessage(contact, question)
                else -> confirm(contact, tidy(text))
            }
        },
    )

    private fun confirm(contact: Contact, message: String, question: String? = null): Outcome = Outcome(
        reply = question ?: "You said \"$message\". Would you like to send it to ${contact.name}?",
        success = true,
        followUp = { answers ->
            when (Answers.classify(answers)) {
                Answer.YES -> send(contact, message)
                Answer.CHANGE -> askMessage(contact, "OK, what's the new message?")
                Answer.NO -> cancelled()
                null -> confirm(contact, message, "Should I send it to ${contact.name}? Say yes, no, or change it.")
            }
        },
    )

    private suspend fun send(contact: Contact, message: String): Outcome {
        if (!app.texts.canSend) return Outcome("Luma isn't allowed to send texts any more.", success = false)
        return when (val result = app.texts.send(contact.numbers.first(), message)) {
            TextMessages.Result.Sent -> Outcome("Sent to ${contact.name}.", success = true)
            TextMessages.Result.Queued -> Outcome(
                "Sending to ${contact.name}. It hasn't gone out yet, so check Messages if it's important.",
                success = true,
            )
            is TextMessages.Result.Failed -> Outcome("I couldn't send it: ${result.reason}", success = false)
        }
    }

    private fun cancelled() = Outcome("OK, I won't send it.", success = true)

    /** Messages start with a capital, like the keyboard would give them. */
    private fun tidy(message: String) = message.trim().replaceFirstChar { it.titlecase(Locale.getDefault()) }

    private fun listing(names: List<String>): String =
        if (names.size <= 1) names.joinToString() else names.dropLast(1).joinToString(", ") + " or " + names.last()

    companion object {
        /** What Luma asks for before it starts, if it doesn't have them yet. */
        val PERMISSIONS = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.SEND_SMS)

        private const val MAX_LISTED = 4

        // "the second one" -> index 1; -1 for "the last one". No bare "one": it's in "the second one".
        private val ORDINALS = linkedMapOf(
            "(?:first|1st|number one|number 1)" to 0,
            "(?:second|2nd|number two|number 2)" to 1,
            "(?:third|3rd|number three|number 3)" to 2,
            "(?:fourth|4th|number four|number 4)" to 3,
            "(?:last)" to -1,
        )
    }
}
