package io.github.hyrumrichardson.luma.assist

import android.content.Intent
import android.util.Log
import io.github.hyrumrichardson.luma.LumaApp
import io.github.hyrumrichardson.luma.Notifications
import io.github.hyrumrichardson.luma.home.SwitchFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The fallback for devices Google Home has "no route" to. Without a Google hub that can reach them,
 * Matter devices only answer once the Google Home app has connected to them over Wi-Fi, and opening
 * the app does that. So Luma opens Google Home, retries the devices for a few seconds, and reports
 * the result by voice and notification, since the popup has closed by then.
 */
class GoogleHomeRetry(private val app: LumaApp) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    /** Opens the Google Home app, or null if it isn't installed. */
    fun openIntent(): Intent? =
        app.packageManager.getLaunchIntentForPackage(GOOGLE_HOME_PACKAGE)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Retries switching [deviceIds] while Google Home opens, until none of them fails with no route
     * or time runs out. [report] gets the devices that still failed and returns the reply to give.
     */
    fun retry(deviceIds: Collection<String>, turnOn: Boolean, report: (failures: Map<String, SwitchFailure>) -> String) {
        job?.cancel()
        job = scope.launch {
            val failed = mutableMapOf<String, SwitchFailure>()
            var remaining = deviceIds.toList()
            for (attempt in 1..ATTEMPTS) {
                delay(RETRY_DELAY_MILLIS)
                val failures = try {
                    app.home.setOnOff(remaining, turnOn)
                } catch (e: Exception) {
                    Log.w(TAG, "Retry $attempt failed", e)
                    remaining.associateWith { SwitchFailure(e.message ?: e.javaClass.simpleName) }
                }
                // Other errors won't be fixed by waiting, so only no-route failures are tried again.
                failures.filterValues { !it.noRoute }.let(failed::putAll)
                remaining = remaining.filter { failures[it]?.noRoute == true }
                if (remaining.isEmpty()) break
                if (attempt == ATTEMPTS) remaining.forEach { failed[it] = failures.getValue(it) }
            }
            tell(report(failed))
        }
    }

    private fun tell(message: String) {
        Notifications.showActionResult(app, Notifications.GOOGLE_HOME_RETRY_ID, message)
        if (!app.settings.speakReplies) return
        val speaker = Speaker(app)
        speaker.speak(message) { speaker.shutdown() }
    }

    private companion object {
        const val TAG = "GoogleHomeRetry"
        const val GOOGLE_HOME_PACKAGE = "com.google.android.apps.chromecast.app"

        // About 12 seconds in all, enough for Google Home to start and connect.
        const val ATTEMPTS = 6
        const val RETRY_DELAY_MILLIS = 2_000L
    }
}
