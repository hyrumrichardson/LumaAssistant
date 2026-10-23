package io.github.hyrumrichardson.luma.home

import io.github.hyrumrichardson.luma.nlu.NumberWords
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** The devices a spoken phrase refers to, plus a natural way to describe them in a reply. */
data class DeviceMatch(val devices: List<SmartDevice>, val description: String)

/** What [DeviceMatcher.matchAll] found: the devices, or the part of the phrase that matched nothing. */
sealed interface MatchResult {
    data class Found(val match: DeviceMatch) : MatchResult

    data class NotFound(val part: String) : MatchResult
}

/**
 * Resolves phrases like "living room lights", "the lamp" or "everything in the bedroom" against
 * the user's devices. Room names and device names both count, and small transcription
 * differences ("lamps" vs "Lamp") are tolerated.
 */
object DeviceMatcher {
    private val filler = setOf("the", "my", "a", "an", "all", "of", "in", "on", "at", "inside", "both")
    private val everythingWords = setOf("everything", "device", "thing", "stuff", "every", "anything")
    private val lightWords = setOf("light", "lamp", "bulb", "lighting")
    private const val FUZZY_THRESHOLD = 0.78
    private val andRegex = Regex("""\s+and\s+""", RegexOption.IGNORE_CASE)

    /**
     * Like [match], but also takes several targets: "the lamp and the fan". A light word at either end
     * applies to bare rooms in the list, so "kitchen and living room lights" and "the lights in the
     * kitchen and the bedroom" mean those rooms' lights rather than everything in them.
     */
    fun matchAll(phrase: String, devices: List<SmartDevice>): MatchResult {
        val parts = phrase.split(andRegex).map { it.trim() }.filter { it.isNotEmpty() }
        // A name with "and" in it ("Salt and Pepper Lamp") is one target.
        val namesHaveAnd = devices.any { d -> "and" in tokens(d.name) || d.room?.let { "and" in tokens(it) } == true }
        if (parts.size < 2 || namesHaveAnd) {
            match(phrase, devices)?.let { return MatchResult.Found(it) }
            if (parts.size < 2) return MatchResult.NotFound(phrase)
        }

        val sharedLight = tokens(parts.last()).lastOrNull()?.takeIf { it in lightWords }
            ?: tokens(parts.first()).firstOrNull()?.takeIf { it in lightWords }
        val rooms = devices.mapNotNull { it.room }.map(::tokens).toSet()
        val matches = parts.map { part ->
            val target = if (sharedLight != null && tokens(part) in rooms) "$part $sharedLight" else part
            match(target, devices) ?: return MatchResult.NotFound(part)
        }
        val names = matches.map { it.description }
        val description = names.dropLast(1).joinToString(", ") + " and " + names.last()
        return MatchResult.Found(DeviceMatch(matches.flatMap { it.devices }.distinctBy { it.id }, description))
    }

    fun match(phrase: String, devices: List<SmartDevice>): DeviceMatch? {
        if (devices.isEmpty()) return null
        val target = tokens(phrase)

        if (target.isEmpty() || target.all { it in everythingWords }) {
            return DeviceMatch(devices, "everything")
        }

        // An exact device name ("Desk Lamp"), or room + name ("bedroom lamp").
        devices.filter { tokens(it.name) == target }.takeIf { it.isNotEmpty() }?.let { return describe(it) }
        devices.filter { d -> d.room != null && (tokens(d.room) + tokens(d.name) == target) }
            .takeIf { it.isNotEmpty() }?.let { return describe(it) }

        // A room, optionally followed by what's in it ("kitchen", "kitchen lights", "kitchen fan").
        val rooms = devices.mapNotNull { it.room }.distinct()
        val room = rooms
            .map { it to tokens(it) }
            .filter { (_, roomTokens) -> roomTokens.isNotEmpty() && containsRun(target, roomTokens) }
            .maxByOrNull { (_, roomTokens) -> roomTokens.size }
        if (room != null) {
            val (roomName, roomTokens) = room
            val inRoom = devices.filter { it.room == roomName }
            val rest = removeRun(target, roomTokens)
            return when {
                rest.isEmpty() || rest.all { it in everythingWords } -> DeviceMatch(inRoom, "everything in the $roomName")
                rest.all { it in lightWords } -> lightsIn(inRoom)?.let { DeviceMatch(it, "$roomName lights") }
                else -> (byNameIgnoringLightWords(rest, inRoom) ?: bestFuzzy(rest, inRoom))?.let(::describe)
            }
                // The room word may be part of a device's name instead ("Office Lamp" in the Study).
                ?: byNameIgnoringLightWords(target, devices)?.let(::describe)
        }

        if (target.all { it in lightWords }) {
            return lightsIn(devices)?.let { DeviceMatch(it, "lights") }
        }

        return (byNameIgnoringLightWords(target, devices) ?: bestFuzzy(target, devices))?.let(::describe)
    }

    private fun isLight(device: SmartDevice): Boolean = device.isLight || tokens(device.name).any { it in lightWords }

    private fun lightsIn(devices: List<SmartDevice>): List<SmartDevice>? = devices.filter(::isLight).takeIf { it.isNotEmpty() }

    /**
     * Devices whose name, or room + name, matches [target] once words like "lamp" and "light" are
     * dropped from both, so "office", "office lamp" and "office light" all find a light named
     * "Office", "Office Lamp" or "Office Light". A light word in [target] only matches lights.
     */
    private fun byNameIgnoringLightWords(target: List<String>, devices: List<SmartDevice>): List<SmartDevice>? {
        val core = target.filterNot { it in lightWords }
        if (core.isEmpty()) return null
        val wantsLight = core.size < target.size
        return devices.filter { d ->
            val name = tokens(d.name).filterNot { it in lightWords }
            (!wantsLight || isLight(d)) && name.isNotEmpty() &&
                (core == name || (d.room != null && core == tokens(d.room) + name))
        }.takeIf { it.isNotEmpty() }
    }

    /** The devices whose names best match [target], if the best match is close enough. */
    private fun bestFuzzy(target: List<String>, devices: List<SmartDevice>): List<SmartDevice>? {
        val scored = devices.map { d ->
            val byName = similarity(target, tokens(d.name))
            val byRoomAndName = d.room?.let { similarity(target, tokens(it) + tokens(d.name)) } ?: 0.0
            d to max(byName, byRoomAndName)
        }
        val best = scored.maxOfOrNull { it.second } ?: return null
        if (best < FUZZY_THRESHOLD) return null
        // Several devices can share a name ("Lamp" in two rooms); act on all of them.
        return scored.filter { it.second >= best - 0.001 }.map { it.first }
    }

    private fun describe(matched: List<SmartDevice>): DeviceMatch {
        val description = when {
            matched.size == 1 -> matched[0].name
            matched.map { it.name.lowercase(Locale.US) }.distinct().size == 1 -> "${matched.size} ${matched[0].name}s"
            else -> "${matched.size} devices"
        }
        return DeviceMatch(matched, description)
    }

    // --- Text helpers ------------------------------------------------------------------------

    internal fun tokens(text: String): List<String> =
        NumberWords.normalize(
            text.lowercase(Locale.US).replace("'", "").replace('’', ' ').replace(Regex("""[^a-z0-9]+"""), " ").trim(),
        )
            .split(' ')
            .filter { it.isNotEmpty() && it !in filler }
            .map(::singular)

    private fun singular(word: String): String = when {
        word.length > 4 && word.endsWith("ies") -> word.dropLast(3) + "y"
        word.length > 3 && word.endsWith("s") && !word.endsWith("ss") -> word.dropLast(1)
        else -> word
    }

    private fun containsRun(haystack: List<String>, run: List<String>): Boolean =
        indexOfRun(haystack, run) >= 0

    private fun indexOfRun(haystack: List<String>, run: List<String>): Int {
        if (run.size > haystack.size) return -1
        for (i in 0..haystack.size - run.size) {
            if (haystack.subList(i, i + run.size) == run) return i
        }
        return -1
    }

    private fun removeRun(haystack: List<String>, run: List<String>): List<String> {
        val i = indexOfRun(haystack, run)
        return if (i < 0) haystack else haystack.subList(0, i) + haystack.subList(i + run.size, haystack.size)
    }

    /** Symmetric average of each token's best match on the other side, from 0 to 1. */
    private fun similarity(a: List<String>, b: List<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val aToB = a.sumOf { x -> b.maxOf { y -> wordSimilarity(x, y) } }
        val bToA = b.sumOf { y -> a.maxOf { x -> wordSimilarity(x, y) } }
        return (aToB + bToA) / (a.size + b.size)
    }

    private fun wordSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val longest = max(a.length, b.length)
        return 1.0 - levenshtein(a, b).toDouble() / longest
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }
}
