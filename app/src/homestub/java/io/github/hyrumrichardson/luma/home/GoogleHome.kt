package io.github.hyrumrichardson.luma.home

import android.content.Context
import androidx.activity.ComponentActivity

/**
 * Stand-in used when Luma is built without the Google Home APIs SDK. The real implementation
 * is in src/homeapi; see the README for installing the SDK.
 */
@Suppress("UNUSED_PARAMETER")
class GoogleHome(context: Context) : SmartHome {
    override fun registerPermissionCaller(activity: ComponentActivity) = Unit

    override suspend fun status(): HomeStatus = HomeStatus.SdkMissing

    override suspend fun requestPermissions(): String = MESSAGE

    override suspend fun devices(): List<SmartDevice> = emptyList()

    override suspend fun setOnOff(deviceIds: Collection<String>, on: Boolean): Map<String, String> =
        deviceIds.associateWith { MESSAGE }

    override suspend fun onStates(deviceIds: Collection<String>): Map<String, Boolean> = emptyMap()

    private companion object {
        const val MESSAGE = "This build of Luma doesn't include the Google Home APIs SDK."
    }
}
