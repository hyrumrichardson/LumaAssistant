package io.github.hyrumrichardson.luma

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notifications {
    private const val CHANNEL_DEVICE_ACTIONS = "device_actions"
    const val WORKING_ID = 1

    fun createChannels(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_DEVICE_ACTIONS,
            context.getString(R.string.channel_device_actions),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = context.getString(R.string.channel_device_actions_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun working(context: Context): Notification =
        builder(context).setContentText("Controlling your devices…").setOngoing(true).build()

    fun showActionResult(context: Context, id: Int, message: String) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        NotificationManagerCompat.from(context).notify(id, builder(context).setContentText(message).build())
    }

    private fun builder(context: Context): NotificationCompat.Builder {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_DEVICE_ACTIONS)
            .setSmallIcon(R.drawable.ic_stat_luma)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentIntent(open)
            .setAutoCancel(true)
    }
}
