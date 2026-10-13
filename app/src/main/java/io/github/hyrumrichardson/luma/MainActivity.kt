package io.github.hyrumrichardson.luma

import android.Manifest
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hyrumrichardson.luma.assist.AssistActivity
import io.github.hyrumrichardson.luma.home.HomeStatus
import io.github.hyrumrichardson.luma.home.SmartDevice
import io.github.hyrumrichardson.luma.schedule.ScheduledAction
import io.github.hyrumrichardson.luma.ui.LumaAvatar
import io.github.hyrumrichardson.luma.ui.LumaTheme
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The Home APIs consent screen needs an activity-result caller registered before onStart.
        luma.home.registerPermissionCaller(this)
        setContent { LumaTheme { SetupScreen() } }
    }
}

private data class Checks(
    val isAssistant: Boolean = false,
    val mic: Boolean = false,
    val notifications: Boolean = false,
    val exactAlarms: Boolean = false,
)

private fun Context.granted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.readChecks() = Checks(
    isAssistant = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_ASSISTANT),
    mic = granted(Manifest.permission.RECORD_AUDIO),
    notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(Manifest.permission.POST_NOTIFICATIONS),
    exactAlarms = luma.scheduler.canScheduleExact,
)

private fun Context.startFirst(vararg intents: Intent) {
    for (intent in intents) {
        try {
            startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupScreen() {
    val context = LocalContext.current
    val app = context.luma
    val scope = rememberCoroutineScope()

    var checks by remember { mutableStateOf(Checks()) }
    var homeStatus by remember { mutableStateOf<HomeStatus?>(null) }
    var devices by remember { mutableStateOf<List<SmartDevice>?>(null) }
    var homeBusy by remember { mutableStateOf(false) }
    var homeMessage by remember { mutableStateOf<String?>(null) }
    val scheduled by app.scheduler.store.actions.collectAsStateWithLifecycle()

    fun loadDevices() {
        scope.launch {
            homeBusy = true
            homeMessage = null
            devices = try {
                app.home.devices()
            } catch (e: Exception) {
                homeMessage = e.message ?: "Couldn't load devices"
                null
            }
            homeBusy = false
        }
    }

    fun refreshHome() {
        scope.launch {
            val status = app.home.status()
            homeStatus = status
            if (status == HomeStatus.Ready && devices == null) loadDevices()
        }
    }

    LifecycleResumeEffect(Unit) {
        checks = context.readChecks()
        refreshHome()
        onPauseOrDispose { }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        checks = context.readChecks()
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LumaAvatar(40.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Luma")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Section("Set up") {
                    StatusRow(
                        ok = checks.isAssistant,
                        title = "Default assistant",
                        detail = if (checks.isAssistant) "Luma opens when you invoke the assistant." else
                            "Choose Luma under Digital assistant app.",
                        action = "Open settings".takeUnless { checks.isAssistant },
                    ) {
                        context.startFirst(
                            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
                            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
                        )
                    }
                    StatusRow(
                        ok = checks.mic,
                        title = "Microphone",
                        detail = if (checks.mic) "Allowed" else "Needed to hear your commands.",
                        action = "Allow".takeUnless { checks.mic },
                    ) { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                    // These two only matter for scheduled Google Home actions.
                    if (BuildConfig.HOME_APIS_ENABLED) ScheduledActionPermissions(checks) { permissionLauncher.launch(it) }
                }
            }

            item {
                HomeSection(
                    status = homeStatus,
                    devices = devices,
                    busy = homeBusy,
                    message = homeMessage,
                    onConnect = {
                        scope.launch {
                            homeBusy = true
                            homeMessage = app.home.requestPermissions()
                            homeBusy = false
                            devices = null
                            refreshHome()
                        }
                    },
                    onRefresh = ::loadDevices,
                )
            }

            if (scheduled.isNotEmpty()) {
                item { ScheduledSection(scheduled) { app.scheduler.cancel(it.id) } }
            }

            item { TryItSection() }

            item {
                var speak by rememberSaveable { mutableStateOf(app.settings.speakReplies) }
                Section("Preferences") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Speak replies", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Read confirmations out loud",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = speak, onCheckedChange = { speak = it; app.settings.speakReplies = it })
                    }
                    SearchEngineRow()
                }
            }

            item { ExamplesSection() }
        }
    }
}

@Composable
private fun ScheduledActionPermissions(checks: Checks, requestPermission: (String) -> Unit) {
    val context = LocalContext.current
    StatusRow(
        ok = checks.exactAlarms,
        title = "Alarms & reminders",
        detail = if (checks.exactAlarms) "Scheduled device actions run on time." else
            "Without it, \"in 10 minutes\" actions may run a few minutes late.",
        action = "Allow".takeUnless { checks.exactAlarms },
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.startFirst(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()),
            )
        }
    }
    StatusRow(
        ok = checks.notifications,
        title = "Notifications",
        detail = if (checks.notifications) "You'll hear back when scheduled actions run." else
            "Optional: get told when scheduled actions run.",
        action = "Allow".takeUnless { checks.notifications },
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) requestPermission(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun StatusRow(ok: Boolean, title: String, detail: String, action: String?, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null) {
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun HomeSection(
    status: HomeStatus?,
    devices: List<SmartDevice>?,
    busy: Boolean,
    message: String?,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
) {
    Section("Google Home") {
        when (status) {
            null -> CircularProgressIndicator(Modifier.size(24.dp))
            HomeStatus.SdkMissing -> Text(
                "This build doesn't include Google Home device control. Everything else works. " +
                    "To control your devices, build Luma yourself with the Google Home APIs SDK " +
                    "(the README on GitHub explains how).",
                style = MaterialTheme.typography.bodyMedium,
            )
            HomeStatus.NeedsPermission -> {
                Text(
                    "Give Luma access to a home so it can switch your lights and plugs.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onConnect, enabled = !busy) { Text("Connect Google Home") }
            }
            is HomeStatus.Unavailable -> {
                Text(status.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onConnect, enabled = !busy) { Text("Try connecting again") }
            }
            HomeStatus.Ready -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            devices == null -> "Loading devices…"
                            devices.isEmpty() -> "Connected, but no switchable devices were found."
                            else -> "${devices.size} devices Luma can switch"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (busy) CircularProgressIndicator(Modifier.size(24.dp)) else
                        IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, contentDescription = "Refresh") }
                }
                devices?.sortedWith(compareBy({ it.room ?: "￿" }, { it.name }))?.forEach { device ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (device.isLight) Icons.Rounded.Lightbulb else Icons.Rounded.Power,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(device.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(device.room ?: "No room", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = onConnect, enabled = !busy) { Text("Change home") }
            }
        }
        if (message != null && status != null) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ScheduledSection(actions: List<ScheduledAction>, onCancel: (ScheduledAction) -> Unit) {
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    Section("Scheduled") {
        actions.forEachIndexed { i, action ->
            if (i > 0) HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Turn ${if (action.turnOn) "on" else "off"} ${action.description}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "at ${timeFormat.format(Date(action.triggerAtMillis))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onCancel(action) }) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun TryItSection() {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    val run = {
        if (text.isNotBlank()) context.startActivity(AssistActivity.queryIntent(context, text.trim()))
    }
    Section("Try it") {
        Button(onClick = { context.startActivity(Intent(context, AssistActivity::class.java)) }) {
            Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Talk to Luma")
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Or type a command") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { run() }),
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = run, enabled = text.isNotBlank()) { Text("Run") }
    }
}

@Composable
private fun SearchEngineRow() {
    val settings = LocalContext.current.luma.settings
    var engine by remember { mutableStateOf(settings.searchEngine) }
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Search engine", style = MaterialTheme.typography.titleSmall)
            Text(
                "For anything Luma doesn't know how to do. Opens in your default browser.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            TextButton(onClick = { expanded = true }) { Text(engine.label) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                SearchEngine.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            engine = option
                            settings.searchEngine = option
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExamplesSection() {
    Section("Things to say") {
        listOf(
            "Set a timer for 10 minutes",
            "Set a pasta timer for 8 minutes",
            "Turn on the flashlight",
            "Turn off the living room lights",
            "Turn off the living room lights in 10 minutes",
            "Turn on the fan for half an hour",
            "Turn off everything in the bedroom at 11 pm",
            "Navigate to the nearest coffee shop",
            "Walking directions to Central Park",
            "How tall is Mount Everest? (anything else is a web search)",
        ).forEach {
            Text("“$it”", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
