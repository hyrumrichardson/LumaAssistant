package io.github.hyrumrichardson.luma.phone

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sends SMS messages straight from Luma. Needs [Manifest.permission.SEND_SMS]. Android copies them
 * into the default messaging app's conversation, as it does for any app that isn't the default one.
 */
class TextMessages(private val context: Context) {

    sealed interface Result {
        data object Sent : Result

        /** Handed to the radio, but it hadn't confirmed sending before Luma stopped waiting. */
        data object Queued : Result

        data class Failed(val reason: String) : Result
    }

    val canSend: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    /** False on tablets and other devices that can't send SMS at all. */
    val hasTelephony: Boolean
        get() = context.packageManager.hasSystemFeature(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) PackageManager.FEATURE_TELEPHONY_MESSAGING
            else PackageManager.FEATURE_TELEPHONY,
        )

    /** Sends [message] to [number] and waits up to 30 seconds for each part to go out. */
    suspend fun send(number: String, message: String): Result {
        val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        } ?: return Result.Failed("this phone can't send texts.")

        val parts = sms.divideMessage(message)
        if (parts.isEmpty()) return Result.Failed("the message is empty.")
        val action = "${context.packageName}.SMS_SENT.${UUID.randomUUID()}"
        val results = Channel<Int>(parts.size)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                results.trySend(resultCode)
            }
        }
        // The radio reports back through our own PendingIntents, so the receiver needn't be exported.
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            val sentIntents = ArrayList(
                parts.indices.map { i ->
                    PendingIntent.getBroadcast(
                        context, i, Intent(action).setPackage(context.packageName),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
                    )
                },
            )
            try {
                sms.sendMultipartTextMessage(number, null, parts, sentIntents, null)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't send a text", e)
                return Result.Failed(e.message ?: e.javaClass.simpleName)
            }
            return withTimeoutOrNull(30.seconds) {
                repeat(parts.size) {
                    val code = results.receive()
                    if (code != Activity.RESULT_OK) return@withTimeoutOrNull Result.Failed(describe(code))
                }
                Result.Sent
            } ?: Result.Queued
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    private fun describe(code: Int): String = when (code) {
        SmsManager.RESULT_ERROR_NO_SERVICE -> "there's no mobile service right now."
        SmsManager.RESULT_ERROR_RADIO_OFF -> "the phone's radio is off. Is airplane mode on?"
        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED, SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED,
        SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> "Android blocked it (error $code)."
        else -> "the phone reported error $code."
    }

    private companion object {
        const val TAG = "TextMessages"
    }
}
