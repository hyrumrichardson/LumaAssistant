package io.github.hyrumrichardson.luma.assist

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.core.net.toUri
import io.github.hyrumrichardson.luma.nlu.TravelMode
import kotlin.time.Duration

/** Intents that hand work off to the Clock, Calendar, Maps and browser apps. */
object AppIntents {
    private const val MAPS_PACKAGE = "com.google.android.apps.maps"
    private const val CALENDAR_PACKAGE = "com.google.android.calendar"
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

    /** Sets an alarm in the default clock app for the next time it's [hour]:[minute] (0 to 23). */
    fun alarm(hour: Int, minute: Int, label: String?): Intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
        putExtra(AlarmClock.EXTRA_HOUR, hour)
        putExtra(AlarmClock.EXTRA_MINUTES, minute)
        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (label != null) putExtra(AlarmClock.EXTRA_MESSAGE, label)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Opens the default clock app's list of alarms. */
    fun alarms(): Intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Opens a new calendar event, filled in, for the user to check and save; in Google Calendar when
     * [googleCalendar] is set. Times are in epoch milliseconds.
     */
    fun calendarEvent(title: String?, begin: Long?, end: Long?, allDay: Boolean, googleCalendar: Boolean): Intent =
        Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
            if (title != null) putExtra(CalendarContract.Events.TITLE, title)
            if (begin != null) putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            if (end != null) putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            if (allDay) putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
            if (googleCalendar) setPackage(CALENDAR_PACKAGE)
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

    /** Opens [url] in a browser, but not in an app that claims the site's links. */
    fun browse(url: String): Intent =
        Intent(Intent.ACTION_VIEW, url.toUri())
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** [browse], pinned to the user's default browser when they've chosen one. */
    fun browseInDefaultBrowser(context: Context, url: String): Intent {
        val probe = Intent(Intent.ACTION_VIEW, "https://example.com".toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
        val browser = context.packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
            // With no default chosen, Android resolves to its own app picker.
            ?.takeIf { it != "android" }
        return browse(url).apply { if (browser != null) setPackage(browser) }
    }
}
