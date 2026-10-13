package io.github.hyrumrichardson.luma.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import java.util.UUID

/**
 * Schedules device actions with AlarmManager. Exact alarms are used when the user has allowed
 * them (Settings > Alarms & reminders); otherwise Android may run the action a few minutes late.
 */
class DeviceActionScheduler(private val context: Context, val store: ScheduledActionStore) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    val canScheduleExact: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun schedule(deviceIds: List<String>, description: String, turnOn: Boolean, triggerAtMillis: Long): ScheduledAction {
        val action = ScheduledAction(UUID.randomUUID().toString(), deviceIds, description, turnOn, triggerAtMillis)
        store.add(action)
        setAlarm(action)
        return action
    }

    fun cancel(id: String) {
        alarmManager.cancel(pendingIntent(id))
        store.remove(id)
    }

    /** Alarms don't survive reboots, and exact alarms need re-arming once they're allowed. */
    fun rescheduleAll() {
        val now = System.currentTimeMillis()
        for (action in store.actions.value) {
            if (now - action.triggerAtMillis > STALE_AFTER_MILLIS) {
                // Phone was off when this was due; acting hours late would be surprising.
                Log.i(TAG, "Dropping stale action ${action.description}")
                store.remove(action.id)
            } else {
                setAlarm(action)
            }
        }
    }

    private fun setAlarm(action: ScheduledAction) {
        val pi = pendingIntent(action.id)
        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, action.triggerAtMillis, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, action.triggerAtMillis, pi)
        }
    }

    private fun pendingIntent(id: String): PendingIntent {
        val intent = Intent(context, DeviceActionReceiver::class.java)
            .setData("luma://scheduled-action/$id".toUri()) // distinct per action
            .putExtra(DeviceActionReceiver.EXTRA_ACTION_ID, id)
        return PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val TAG = "DeviceActionScheduler"
        const val STALE_AFTER_MILLIS = 60 * 60 * 1000L
    }
}
