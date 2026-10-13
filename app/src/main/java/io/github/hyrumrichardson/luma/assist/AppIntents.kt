package io.github.hyrumrichardson.luma.assist

import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import androidx.core.net.toUri
import io.github.hyrumrichardson.luma.nlu.TravelMode
import kotlin.time.Duration

/** Intents that hand work off to the Clock and Maps apps. */
object AppIntents {
    private const val MAPS_PACKAGE = "com.google.android.apps.maps"
    const val MAX_TIMER_SECONDS = 24 * 60 * 60

    /** Starts a timer in the default clock app; with no [length], opens its timer screen instead. */
    fun timer(length: Duration?, label: String?): Intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
        if (length != null) {
            putExtra(AlarmClock.EXTRA_LENGTH, length.inWholeSeconds.toInt().coerceIn(1, MAX_TIMER_SECONDS))
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        if (label != null) putExtra(AlarmClock.EXTRA_MESSAGE, label)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Starts turn-by-turn navigation in Google Maps. */
    fun navigation(destination: String, mode: TravelMode): Intent =
        Intent(Intent.ACTION_VIEW, "google.navigation:q=${Uri.encode(destination)}&mode=${mode.mapsCode}".toUri())
            .setPackage(MAPS_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** For when Google Maps isn't installed: a place search in whatever maps app is. */
    fun mapSearch(destination: String): Intent =
        Intent(Intent.ACTION_VIEW, "geo:0,0?q=${Uri.encode(destination)}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
