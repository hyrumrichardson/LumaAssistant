package io.github.hyrumrichardson.luma.schedule

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import io.github.hyrumrichardson.luma.Notifications
import io.github.hyrumrichardson.luma.luma

/** Runs a [ScheduledAction] against the smart home and reports the result in a notification. */
class DeviceActionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext.luma
        val id = inputData.getString(KEY_ACTION_ID) ?: return Result.failure()
        // Missing means it was cancelled after the alarm fired.
        val action = app.scheduler.store.get(id) ?: return Result.success()

        val failures = try {
            app.home.setOnOff(action.deviceIds, action.turnOn)
        } catch (e: Exception) {
            Log.w(TAG, "Scheduled action failed", e)
            if (runAttemptCount < MAX_ATTEMPTS) return Result.retry()
            action.deviceIds.associateWith { e.message ?: "error" }
        }

        if (failures.isNotEmpty() && failures.size == action.deviceIds.size && runAttemptCount < MAX_ATTEMPTS) {
            return Result.retry()
        }
        app.scheduler.store.remove(id)

        val verb = if (action.turnOn) "on" else "off"
        val message = when {
            failures.isEmpty() -> "Turned $verb ${action.description}."
            failures.size == action.deviceIds.size -> "Couldn't turn $verb ${action.description}: ${failures.values.first()}"
            else -> "Turned $verb ${action.description}, but ${failures.size} device(s) didn't respond."
        }
        Notifications.showActionResult(applicationContext, id.hashCode(), message)
        return Result.success()
    }

    // Only used before Android 12, where expedited work runs as a foreground service.
    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(Notifications.WORKING_ID, Notifications.working(applicationContext))

    companion object {
        const val KEY_ACTION_ID = "action_id"
        private const val TAG = "DeviceActionWorker"
        private const val MAX_ATTEMPTS = 3
    }
}
