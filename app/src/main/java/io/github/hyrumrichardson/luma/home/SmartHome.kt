package io.github.hyrumrichardson.luma.home

import androidx.activity.ComponentActivity

/** A device that can be switched on and off. */
data class SmartDevice(
    val id: String,
    val name: String,
    val room: String?,
    val isLight: Boolean,
)

sealed interface HomeStatus {
    /** Luma was built without the Home APIs SDK (see README). */
    data object SdkMissing : HomeStatus

    /** The user hasn't granted Luma access to a home yet. */
    data object NeedsPermission : HomeStatus

    data object Ready : HomeStatus

    data class Unavailable(val reason: String) : HomeStatus
}

/** A smart home backend. The Google Home implementation lives in src/homeapi. */
interface SmartHome {
    /** Must be called from the activity's onCreate, before it's started. */
    fun registerPermissionCaller(activity: ComponentActivity)

    suspend fun status(): HomeStatus

    /** Shows the Google Home consent screen. Returns null on success, or an error message. */
    suspend fun requestPermissions(): String?

    /** All on/off-capable devices in the homes Luma has access to. */
    suspend fun devices(): List<SmartDevice>

    /** Returns the IDs of devices that couldn't be switched, mapped to the reason. */
    suspend fun setOnOff(deviceIds: Collection<String>, on: Boolean): Map<String, String>

    /** Whether each device is on. Devices whose state couldn't be read are left out. */
    suspend fun onStates(deviceIds: Collection<String>): Map<String, Boolean>
}
