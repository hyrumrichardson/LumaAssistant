package io.github.hyrumrichardson.luma.schedule

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.hyrumrichardson.luma.luma

/** Fires when a scheduled action is due and hands it to [DeviceActionWorker]. */
class DeviceActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ACTION_ID) ?: return
        val request = OneTimeWorkRequestBuilder<DeviceActionWorker>()
            .setInputData(workDataOf(DeviceActionWorker.KEY_ACTION_ID to id))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("device-action-$id", ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val EXTRA_ACTION_ID = "action_id"
    }
}

/** Re-arms pending actions after a reboot, an app update, or exact alarms being allowed. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // The receiver is exported for these system broadcasts; ignore anything else sent to it.
        if (intent.action !in SYSTEM_ACTIONS) return
        context.luma.scheduler.rescheduleAll()
    }

    private companion object {
        val SYSTEM_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
