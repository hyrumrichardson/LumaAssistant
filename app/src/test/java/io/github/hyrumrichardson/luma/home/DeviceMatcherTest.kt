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
}
