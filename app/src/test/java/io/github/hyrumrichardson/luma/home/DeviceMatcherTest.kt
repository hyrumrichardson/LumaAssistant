package io.github.hyrumrichardson.luma.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceMatcherTest {
    private val ceiling = SmartDevice("1", "Ceiling Light", "Living Room", isLight = true)
    private val floorLamp = SmartDevice("2", "Floor Lamp", "Living Room", isLight = true)
    private val tv = SmartDevice("3", "TV Plug", "Living Room", isLight = false)
    private val kitchen = SmartDevice("4", "Kitchen Lights", "Kitchen", isLight = true)
    private val fan = SmartDevice("5", "Fan", "Bedroom", isLight = false)
    private val bedLamp = SmartDevice("6", "Lamp", "Bedroom", isLight = true)
    private val officeLamp = SmartDevice("7", "Lamp", "Office", isLight = true)
    private val porch = SmartDevice("8", "Porch Light", null, isLight = true)
    private val all = listOf(ceiling, floorLamp, tv, kitchen, fan, bedLamp, officeLamp, porch)

    private fun ids(phrase: String) = DeviceMatcher.match(phrase, all)?.devices?.map { it.id }?.sorted()

    @Test fun roomLights() {
        val match = DeviceMatcher.match("living room lights", all)!!
        assertEquals(listOf("1", "2"), match.devices.map { it.id }.sorted())
        assertEquals("Living Room lights", match.description)
    }

    @Test fun exactName() = assertEquals(listOf("4"), ids("kitchen lights"))

    @Test fun singularPlural() = assertEquals(listOf("4"), ids("kitchen light"))

    @Test fun roomAndDevice() = assertEquals(listOf("6"), ids("bedroom lamp"))

    @Test fun deviceInRoomPhrase() = assertEquals(listOf("5"), ids("fan in the bedroom"))

    @Test fun duplicateNamesActOnAll() = assertEquals(listOf("6", "7"), ids("lamp"))

    @Test fun wholeRoom() = assertEquals(listOf("5", "6"), ids("everything in the bedroom"))

    @Test fun justRoom() = assertEquals(listOf("1", "2", "3"), ids("living room"))

    @Test fun allLights() = assertEquals(listOf("1", "2", "4", "6", "7", "8"), ids("lights"))

    @Test fun everything() = assertEquals(all.map { it.id }.sorted(), ids("everything"))

    @Test fun fuzzyTranscription() = assertEquals(listOf("1"), ids("celing light"))

    @Test fun deviceWithoutRoom() = assertEquals(listOf("3"), ids("tv plug"))

    @Test fun lightsInRoomPhrase() = assertEquals(listOf("4"), ids("lights in the kitchen"))

    @Test fun noMatch() = assertNull(ids("garage door"))

    @Test fun roomWithoutLights() = assertNull(DeviceMatcher.match("garage lights", listOf(SmartDevice("9", "Opener", "Garage", false))))

    // --- Several targets ---

    private fun allIds(phrase: String, devices: List<SmartDevice> = all) =
        (DeviceMatcher.matchAll(phrase, devices) as? MatchResult.Found)?.match?.devices?.map { it.id }?.sorted()

    @Test fun singleTarget() = assertEquals(listOf("4"), allIds("kitchen lights"))

    @Test fun twoDevices() {
        val result = DeviceMatcher.matchAll("floor lamp and the fan", all) as MatchResult.Found
        assertEquals(listOf("2", "5"), result.match.devices.map { it.id }.sorted())
        assertEquals("Floor Lamp and Fan", result.match.description)
    }

    @Test fun threeDevicesDescription() = assertEquals(
        "Floor Lamp, Fan and Porch Light",
        (DeviceMatcher.matchAll("floor lamp and fan and porch light", all) as MatchResult.Found).match.description,
    )

    @Test fun sharedTrailingLights() = assertEquals(listOf("1", "2", "4"), allIds("kitchen and living room lights"))

    @Test fun sharedLeadingLights() = assertEquals(listOf("4", "6"), allIds("lights in the kitchen and the bedroom"))

    @Test fun bareRoomsStayWhole() = assertEquals(listOf("1", "2", "3", "5", "6"), allIds("living room and bedroom"))

    @Test fun overlappingTargetsDedupe() = assertEquals(listOf("6"), allIds("bedroom lamp and the lamp in the bedroom"))

    @Test fun missingPartIsNamed() =
        assertEquals(MatchResult.NotFound("garage door"), DeviceMatcher.matchAll("fan and garage door", all))

    @Test fun nameWithAnd() {
        val saltAndPepper = SmartDevice("9", "Salt and Pepper Lamp", "Kitchen", isLight = true)
        assertEquals(listOf("9"), allIds("salt and pepper lamp", all + saltAndPepper))
    }

    // --- Light words are interchangeable ---

    private val noOffice = all - officeLamp

    @Test fun lightWordsInterchangeable() {
        val misses = mutableListOf<String>()
        for (room in listOf(null, "Study", "Office")) {
            for (name in listOf("Office", "Office Lamp", "Office Light")) {
                val office = SmartDevice("9", name, room, isLight = true)
                for (phrase in listOf("office", "office lamp", "office light", "office lights", "the office lamps")) {
                    val found = allIds(phrase, noOffice + office)
                    if (found != listOf("9")) misses += "\"$phrase\" for \"$name\" in room $room found $found"
                }
            }
        }
        assertEquals("", misses.joinToString("\n"))
    }

    @Test fun lightWordsNeedALight() {
        val plug = SmartDevice("9", "Office", null, isLight = false)
        assertEquals(listOf("9"), allIds("office", noOffice + plug))
        assertEquals(MatchResult.NotFound("office light"), DeviceMatcher.matchAll("office light", noOffice + plug))
    }

    @Test fun exactNameBeatsLightWords() {
        val plug = SmartDevice("9", "Office", null, isLight = false)
        val lamp = SmartDevice("10", "Office Lamp", null, isLight = true)
        assertEquals(listOf("9"), allIds("office", noOffice + plug + lamp))
        assertEquals(listOf("10"), allIds("office light", noOffice + plug + lamp))
    }

    @Test fun lightWordsInsideRoom() {
        val desk = SmartDevice("9", "Desk Lamp", "Office", isLight = true)
        val ceilingFan = SmartDevice("10", "Ceiling Fan", "Office", isLight = false)
        assertEquals(listOf("9"), allIds("office desk light", noOffice + desk + ceilingFan))
        assertEquals(listOf("9"), allIds("desk light in the office", noOffice + desk + ceilingFan))
    }

    @Test fun noMatchNamesWholePhrase() =
        assertEquals(MatchResult.NotFound("garage door"), DeviceMatcher.matchAll("garage door", all))
}
