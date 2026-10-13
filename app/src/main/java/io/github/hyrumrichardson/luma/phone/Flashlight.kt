package io.github.hyrumrichardson.luma.phone

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The phone's flashlight, via the camera's torch mode. No permission is needed, but Android turns
 * the torch off if Luma's process is killed while it's on.
 */
class Flashlight(context: Context) {
    private val cameras = context.getSystemService(CameraManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    sealed interface Result {
        data class Switched(val on: Boolean) : Result
        data object NoFlashlight : Result
        data object CameraBusy : Result
    }

    /** The back camera's flash if there is one, otherwise any flash; null if the phone has none. */
    private val cameraId: String? by lazy {
        try {
            val withFlash = cameras.cameraIdList.filter { id ->
                cameras.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            withFlash.firstOrNull { id ->
                cameras.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
            } ?: withFlash.firstOrNull()
        } catch (_: CameraAccessException) {
            null
        }
    }

    /** Turns the flashlight on or off, or toggles it when [on] is null. */
    suspend fun set(on: Boolean?): Result {
        val id = cameraId ?: return Result.NoFlashlight
        val target = on ?: !(isOn(id) ?: false)
        return try {
            cameras.setTorchMode(id, target)
            Result.Switched(target)
        } catch (_: CameraAccessException) {
            Result.CameraBusy
        } catch (_: IllegalArgumentException) {
            Result.NoFlashlight
        }
    }

    /** The camera service reports the current torch state to each newly registered callback. */
    private suspend fun isOn(id: String): Boolean? = withTimeoutOrNull(STATE_TIMEOUT_MILLIS) {
        suspendCancellableCoroutine { cont ->
            val callback = object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) = report(cameraId, enabled)
                override fun onTorchModeUnavailable(cameraId: String) = report(cameraId, false)

                private fun report(cameraId: String, enabled: Boolean) {
                    if (cameraId != id || !cont.isActive) return
                    cameras.unregisterTorchCallback(this)
                    cont.resume(enabled)
                }
            }
            cameras.registerTorchCallback(callback, main)
            cont.invokeOnCancellation { cameras.unregisterTorchCallback(callback) }
        }
    }

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 1_000L
    }
}
