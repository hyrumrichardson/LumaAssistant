package io.github.hyrumrichardson.luma.nlu

import java.util.Locale

/** How the user answered a yes/no question like "Would you like to send it?". */
enum class Answer { YES, NO, CHANGE }

object Answers {
    private val change = Regex(
        """\b(?:change|edit|redo|re do|retry|try again|start over|rewrite|re record|rerecord|different)\b""",
    )
    private val no = Regex(
        """^(?:no|nope|nah|negative|don'?t|do not|stop|cancel|never ?mind|forget (?:it|that)|abort|wait|hold on|not now|no thanks)\b""",
    )
    private val yes = Regex(
        """^(?:yes|yeah|yea|yep|yup|ya|sure|ok|okay|send|do it|go|go ahead|correct|right|that'?s right|that'?s it|""" +
            """affirmative|absolutely|definitely|please|perfect|sounds good|looks good|great|yes please)\b""",
    )
    private val cancel = Regex("""^(?:cancel|never ?mind|forget (?:it|that)|stop|abort|don'?t send(?: it)?)$""")

    private fun clean(text: String) =
        text.lowercase(Locale.US).replace('’', '\'').replace(Regex("""[^a-z' ]"""), " ").replace(Regex("""\s+"""), " ").trim()
            .removePrefix("luma ").trim()

    /**
     * The first of the recogniser's [alternatives] that reads as an answer, or null if none do.
     * "No, change it" counts as [Answer.CHANGE], not [Answer.NO].
     */
    fun classify(alternatives: List<String>): Answer? = alternatives.firstNotNullOfOrNull { raw ->
        val text = clean(raw)
        when {
            text.isEmpty() -> null
            change.containsMatchIn(text) -> Answer.CHANGE
            no.containsMatchIn(text) -> Answer.NO
            yes.containsMatchIn(text) -> Answer.YES
            else -> null
        }
    }

    /** True if [text] is just "cancel", "never mind" and the like, said in place of an answer. */
    fun isCancel(text: String): Boolean = cancel.matches(clean(text))
}
