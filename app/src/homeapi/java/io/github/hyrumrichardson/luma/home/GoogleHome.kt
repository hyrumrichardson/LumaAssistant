package io.github.hyrumrichardson.luma.home

import android.content.Context
import androidx.activity.ComponentActivity
import com.google.home.DeviceType
import com.google.home.DeviceTypeFactory
import com.google.home.FactoryRegistry
import com.google.home.ForcePermissionFlow
import com.google.home.Home
import com.google.home.HomeClient
import com.google.home.HomeConfig
import com.google.home.HomeDevice
import com.google.home.HomeException
import com.google.home.Id
import com.google.home.PermissionsResultStatus
import com.google.home.PermissionsState
import com.google.home.Trait
import com.google.home.TraitFactory
import com.google.home.matter.standard.ColorTemperatureLightDevice
import com.google.home.matter.standard.DimmableLightDevice
import com.google.home.matter.standard.ExtendedColorLightDevice
import com.google.home.matter.standard.FanDevice
import com.google.home.matter.standard.OnOff
import com.google.home.matter.standard.OnOffLightDevice
import com.google.home.matter.standard.OnOffLightSwitchDevice
import com.google.home.matter.standard.OnOffPluginUnitDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/** [SmartHome] backed by the Google Home APIs. */
class GoogleHome(context: Context) : SmartHome {

    private val client: HomeClient = Home.getClient(
        context = context.applicationContext,
        homeConfig = HomeConfig(
            coroutineContext = Dispatchers.IO,
            factoryRegistry = FactoryRegistry(types = SUPPORTED_TYPES, traits = SUPPORTED_TRAITS),
        ),
    )

    override fun registerPermissionCaller(activity: ComponentActivity) {
        client.registerActivityResultCallerForPermissions(activity)
    }

    override suspend fun status(): HomeStatus = try {
        val state = withTimeout(10_000) {
            client.hasPermissions().first { it != PermissionsState.PERMISSIONS_STATE_UNINITIALIZED }
        }
        when (state) {
            PermissionsState.GRANTED -> HomeStatus.Ready
            PermissionsState.NOT_GRANTED -> HomeStatus.NeedsPermission
            else -> HomeStatus.Unavailable("Google Home permissions are unavailable ($state)")
        }
    } catch (e: HomeException) {
        HomeStatus.Unavailable(e.message ?: "Google Home error")
    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        HomeStatus.Unavailable("Google Home didn't respond")
    }

    override suspend fun requestPermissions(): String? = try {
        // Matches the 17.1.0 sample app's call; always shows the home picker so users can switch homes.
        val result = client.requestPermissions(ForcePermissionFlow.FORCE_LAUNCH)
        when (result.status) {
            PermissionsResultStatus.SUCCESS -> null
            PermissionsResultStatus.CANCELLED -> "Cancelled"
            else -> result.errorMessage ?: "Google Home sign-in failed"
        }
    } catch (e: HomeException) {
        e.message ?: "Google Home sign-in failed"
    }

    override suspend fun devices(): List<SmartDevice> = coroutineScope {
        client.structures().list().flatMap { structure ->
            val roomNames = structure.rooms().list().associate { it.id to it.name }
            structure.devices().list().map { device ->
                async { describe(device, device.roomId?.let(roomNames::get)) }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun describe(device: HomeDevice, room: String?): SmartDevice? {
        val types = withTimeoutOrNull(5_000) { device.types().first() } ?: return null
        if (types.none { type -> type.traits().any { it is OnOff } }) return null
        return SmartDevice(
            id = device.id.id,
            name = device.name,
            room = room,
            isLight = types.any {
                it is OnOffLightDevice || it is DimmableLightDevice ||
                    it is ColorTemperatureLightDevice || it is ExtendedColorLightDevice
            },
        )
    }

    override suspend fun setOnOff(deviceIds: Collection<String>, on: Boolean): Map<String, String> = coroutineScope {
        deviceIds.map { id ->
            async {
                val error = try {
                    val device = client.devices().get(Id.of(id))
                    val onOff = device?.let { onOffTrait(it) }
                    when {
                        device == null -> "not found"
                        onOff == null -> "can't be switched on or off"
                        else -> {
                            if (on) onOff.on() else onOff.off()
                            null
                        }
                    }
                } catch (e: HomeException) {
                    e.message ?: "Google Home error"
                }
                error?.let { id to it }
            }
        }.awaitAll().filterNotNull().toMap()
    }

    override suspend fun onStates(deviceIds: Collection<String>): Map<String, Boolean> = coroutineScope {
        deviceIds.map { id ->
            async {
                try {
                    client.devices().get(Id.of(id))?.let { onOffTrait(it) }?.onOff?.let { id to it }
                } catch (e: HomeException) {
                    null
                }
            }
        }.awaitAll().filterNotNull().toMap()
    }

    /** The device's OnOff trait, or null if it has none or doesn't answer in time. */
    private suspend fun onOffTrait(device: HomeDevice): OnOff? =
        withTimeoutOrNull(5_000) { device.types().first() }
            ?.flatMap { type -> type.traits() }
            ?.filterIsInstance<OnOff>()
            ?.firstOrNull()

    private companion object {
        val SUPPORTED_TYPES: List<DeviceTypeFactory<out DeviceType>> = listOf(
            OnOffLightDevice,
            DimmableLightDevice,
            ColorTemperatureLightDevice,
            ExtendedColorLightDevice,
            OnOffPluginUnitDevice,
            OnOffLightSwitchDevice,
            FanDevice,
        )
        val SUPPORTED_TRAITS: List<TraitFactory<out Trait>> = listOf(OnOff)
    }
}
