package io.github.hyrumrichardson.luma.nlu

/** Converts spelled-out numbers ("twenty five", "one hundred and five") into digits. */
object NumberWords {
    private val small = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
        "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19,
    )
    private val tens = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60,
        "seventy" to 70, "eighty" to 80, "ninety" to 90,
    )
    private val isNumberWord = { word: String -> word in small || word in tens || word == "hundred" }

    private enum class Last { NONE, UNIT, TEEN, TENS, HUNDRED }

    /** Expects lowercase, space-separated text; returns it with number words replaced by digits. */
    fun normalize(text: String): String {
        val tokens = text.split(' ').filter { it.isNotEmpty() }
        val out = ArrayList<String>(tokens.size)
        var i = 0
        while (i < tokens.size) {
            val (value, used) = readNumber(tokens, i)
            if (used > 0) {
                out += value.toString()
                i += used
            } else {
                out += tokens[i]
                i++
            }
        }
        return out.joinToString(" ")
    }

    private fun readNumber(tokens: List<String>, start: Int): Pair<Int, Int> {
        var value = 0
        var last = Last.NONE
        var i = start
        while (i < tokens.size) {
            val word = tokens[i]
            val n = small[word]
            val t = tens[word]
            when {
                n != null -> {
                    // "one two" is two numbers, and "twenty twelve" isn't a number at all.
                    if (last == Last.UNIT || last == Last.TEEN || (last == Last.TENS && n >= 10)) break
                    value += n
                    last = if (n < 10) Last.UNIT else Last.TEEN
                }
                t != null -> {
                    if (last != Last.NONE && last != Last.HUNDRED) break
                    value += t
                    last = Last.TENS
                }
                word == "hundred" -> {
                    if (last == Last.NONE || last == Last.HUNDRED) break
                    value *= 100
                    last = Last.HUNDRED
                }
                // "one hundred and five"
                word == "and" && last == Last.HUNDRED &&
                    tokens.getOrNull(i + 1)?.let { it != "hundred" && isNumberWord(it) } == true -> Unit
                else -> break
            }
            i++
        }
        return value to (i - start)
    }
}
