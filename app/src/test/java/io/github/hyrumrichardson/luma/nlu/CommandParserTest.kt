package io.github.hyrumrichardson.luma.nlu

import java.time.LocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandParserTest {
    private fun parse(text: String) = CommandParser.parse(text)

    // --- Timers ---

    @Test fun simpleTimer() =
        assertEquals(Command.SetTimer(10.minutes, null), parse("Set a timer for 10 minutes"))

    @Test fun spelledOutTimer() =
        assertEquals(Command.SetTimer(25.minutes, null), parse("set a timer for twenty five minutes"))

    @Test fun compoundTimer() =
        assertEquals(Command.SetTimer(1.hours + 30.minutes, null), parse("timer for 1 hour and 30 minutes"))

    @Test fun hourAndAHalf() =
        assertEquals(Command.SetTimer(90.minutes, null), parse("set a timer for an hour and a half"))

    @Test fun halfAnHour() =
        assertEquals(Command.SetTimer(30.minutes, null), parse("start a timer for half an hour"))

    @Test fun adjectiveTimer() =
        assertEquals(Command.SetTimer(10.minutes, null), parse("Set a 10 minute timer."))

    @Test fun secondsTimer() =
        assertEquals(Command.SetTimer(90.seconds, null), parse("set a timer for ninety seconds"))

    @Test fun labelledTimerBefore() =
        assertEquals(Command.SetTimer(8.minutes, "Pasta"), parse("set a pasta timer for 8 minutes"))

    @Test fun labelledTimerCalled() =
        assertEquals(Command.SetTimer(5.minutes, "Eggs"), parse("set a timer for 5 minutes called eggs"))

    @Test fun labelledTimerFor() =
        assertEquals(Command.SetTimer(20.minutes, "Laundry"), parse("set a 20 minute timer for the laundry"))

    @Test fun timerWithoutLength() =
        assertEquals(Command.SetTimer(null, null), parse("start a timer"))

    @Test fun politeTimer() =
        assertEquals(Command.SetTimer(3.minutes, null), parse("Hey Luma, can you set a timer for three minutes please"))

    // --- Devices ---

    @Test fun turnOff() = assertEquals(
        Command.ControlDevice("living room lights", false, Timing.Now),
        parse("Turn off the living room lights"),
    )

    @Test fun turnOnTrailing() = assertEquals(
        Command.ControlDevice("kitchen light", true, Timing.Now),
        parse("turn the kitchen light on"),
    )

    @Test fun delayed() = assertEquals(
        Command.ControlDevice("living room lights", false, Timing.After(10.minutes)),
        parse("Turn off the Living Room Lights in 10 minutes"),
    )

    @Test fun delayedFirst() = assertEquals(
        Command.ControlDevice("bedroom lamp", false, Timing.After(5.minutes)),
        parse("in five minutes, turn off the bedroom lamp"),
    )

    @Test fun delayedTrailingVerb() = assertEquals(
        Command.ControlDevice("porch light", true, Timing.After(1.hours)),
        parse("switch the porch light on in an hour"),
    )

    @Test fun forDuration() = assertEquals(
        Command.ControlDevice("fan", true, Timing.For(30.minutes)),
        parse("turn on the fan for half an hour"),
    )

    @Test fun atTime() = assertEquals(
        Command.ControlDevice("lights", false, Timing.At(10, 30, true)),
        parse("turn off all the lights at 10:30 p.m."),
    )

    @Test fun atTimeTonight() = assertEquals(
        Command.ControlDevice("porch light", false, Timing.At(11, 0, true)),
        parse("turn off the porch light at 11 tonight"),
    )

    @Test fun everythingInRoom() = assertEquals(
        Command.ControlDevice("everything in the bedroom", false, Timing.Now),
        parse("turn off everything in the bedroom"),
    )

    @Test fun lightsOffInPlace() = assertEquals(
        Command.ControlDevice("lights in the kitchen", false, Timing.Now),
        parse("turn the lights off in the kitchen"),
    )

    @Test fun loose() = assertEquals(
        Command.ControlDevice("living room lights", false, Timing.Now),
        parse("living room lights off"),
    )

    @Test fun timerPhrasedDeviceAction() = assertEquals(
        Command.ControlDevice("tv", false, Timing.After(20.minutes)),
        parse("set a timer to turn off the TV in 20 minutes"),
    )

    // --- Navigation ---

    @Test fun navigate() = assertEquals(
        Command.Navigate("Central Park", TravelMode.DRIVING),
        parse("Navigate to Central Park"),
    )

    @Test fun takeMeHome() = assertEquals(Command.Navigate("home", TravelMode.DRIVING), parse("take me home"))

    @Test fun directions() = assertEquals(
        Command.Navigate("123 Main St", TravelMode.DRIVING),
        parse("get directions to 123 Main St."),
    )

    @Test fun walkingDirections() = assertEquals(
        Command.Navigate("library", TravelMode.WALKING),
        parse("walking directions to the library"),
    )

    @Test fun walkTo() = assertEquals(
        Command.Navigate("Starbucks", TravelMode.WALKING),
        parse("walk me to Starbucks"),
    )

    @Test fun navigateByBike() = assertEquals(
        Command.Navigate("beach", TravelMode.BICYCLING),
        parse("navigate to the beach by bike"),
    )

    @Test fun navigateWithMaps() = assertEquals(
        Command.Navigate("Seattle Center", TravelMode.DRIVING),
        parse("OK Luma, navigate to Seattle Center using Google Maps"),
    )

    @Test fun howDoIGet() = assertEquals(
        Command.Navigate("airport", TravelMode.DRIVING),
        parse("how do I get to the airport"),
    )

    // --- Flashlight ---

    @Test fun flashlightOn() = assertEquals(Command.Flashlight(true), parse("Turn on the flashlight"))

    @Test fun flashlightOffAfter() = assertEquals(Command.Flashlight(false), parse("turn the flashlight off"))

    @Test fun torchOff() = assertEquals(Command.Flashlight(false), parse("torch off"))

    @Test fun flashlightToggle() = assertEquals(Command.Flashlight(null), parse("flashlight"))

    @Test fun flashlightPolite() = assertEquals(Command.Flashlight(true), parse("Hey Luma, can you turn on my flashlight please"))

    @Test fun flashlightIsNotASmartDevice() = assertEquals(Command.Flashlight(true), parse("switch on the flash"))

    // --- Web search ---

    @Test fun explicitSearch() = assertEquals(Command.WebSearch("cheap flights to Denver"), parse("Search for cheap flights to Denver"))

    @Test fun googleIt() = assertEquals(Command.WebSearch("how tall is Mount Everest"), parse("google how tall is Mount Everest?"))

    @Test fun lookUp() = assertEquals(Command.WebSearch("the Luma bakery hours"), parse("look up the Luma bakery hours"))

    @Test fun unknownBecomesSearch() =
        assertEquals(Command.WebSearch("what's the weather like"), parse("what's the weather like?"))

    @Test fun unknownDropsWakeWord() =
        assertEquals(Command.WebSearch("who won the World Series"), parse("Hey Luma, who won the World Series"))

    @Test fun walkTheDogIsASearch() = assertEquals(Command.WebSearch("walk the dog"), parse("walk the dog"))

    @Test fun parseKnownSkipsSearchFallback() = assertNull(CommandParser.parseKnown("what's the weather like"))

    @Test fun empty() = assertNull(parse("   "))
}

class TimingTest {
    private val evening = LocalDateTime.of(2026, 10, 13, 21, 0)

    @Test fun pmLaterToday() =
        assertEquals(LocalDateTime.of(2026, 10, 13, 22, 30), Timing.At(10, 30, true).nextOccurrence(evening))

    @Test fun amTomorrow() =
        assertEquals(LocalDateTime.of(2026, 10, 14, 7, 0), Timing.At(7, 0, false).nextOccurrence(evening))

    @Test fun ambiguousPicksSoonest() =
        assertEquals(LocalDateTime.of(2026, 10, 13, 23, 0), Timing.At(11, 0, null).nextOccurrence(evening))

    @Test fun ambiguousPastBothWraps() =
        assertEquals(LocalDateTime.of(2026, 10, 14, 8, 0), Timing.At(8, 0, null).nextOccurrence(evening))

    @Test fun midnight() =
        assertEquals(LocalDateTime.of(2026, 10, 14, 0, 0), Timing.At(0, 0, null).nextOccurrence(evening))
}

class DurationsTest {
    @Test fun format() {
        assertEquals("10 minutes", Durations.format(10.minutes))
        assertEquals("1 hour and 30 minutes", Durations.format(90.minutes))
        assertEquals("1 minute and 30 seconds", Durations.format(90.seconds))
        assertEquals("2 hours, 5 minutes and 10 seconds", Durations.format(2.hours + 5.minutes + 10.seconds))
    }

    @Test fun numberWords() {
        assertEquals("25 minutes", NumberWords.normalize("twenty five minutes"))
        assertEquals("105", NumberWords.normalize("one hundred and five"))
        assertEquals("1 2", NumberWords.normalize("one two"))
        assertEquals("turn on lamp 1", NumberWords.normalize("turn on lamp one"))
    }
}
