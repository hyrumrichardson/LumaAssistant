package io.github.hyrumrichardson.luma.assist

import android.Manifest
import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.hyrumrichardson.luma.ui.LumaTheme
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * The popup shown when Luma is invoked as the device assistant (long-press home/power, corner
 * swipe, etc.). It listens once, acts, replies, and gets out of the way.
 */
class AssistActivity : ComponentActivity() {
    private val vm: AssistViewModel by viewModels()
    /** True while a system prompt (mic permission, unlock) is showing, so onStop shouldn't close the popup. */
    private var awaitingSystemUi = false

    private var windowShown = false
    private var unlockAfterEnter: ((Boolean) -> Unit)? = null

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        awaitingSystemUi = false
        if (granted) vm.startListening() else vm.startTyping()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LumaTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                AssistScreen(
                    state = state,
                    onDismiss = ::finish,
                    onMic = ::listen,
                    onKeyboard = vm::startTyping,
                    onSubmit = vm::submitText,
                )
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.events.collect { event ->
                    when (event) {
                        is AssistEvent.Launch -> launchApp(event.intent, event.fallback, event.thenClose)
                        AssistEvent.Unlock -> whenUnlocked(vm::onUnlockResult)
                        AssistEvent.Close -> finish()
                    }
                }
            }
        }
        if (savedInstanceState == null) begin(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        begin(intent)
    }

    override fun onStop() {
        super.onStop()
        // A popup shouldn't linger behind other apps.
        if (!isChangingConfigurations && !awaitingSystemUi) finish()
    }

    private fun begin(intent: Intent) {
        val query = intent.getStringExtra(EXTRA_QUERY)
        val trusted = intent.component?.className == DEBUG_QUERY_ALIAS ||
            intent.getStringExtra(EXTRA_TOKEN)?.let(::consumeToken) == true
        if (query != null && trusted) vm.submitText(query) else listen()
    }

    private fun listen() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            vm.startListening()
        } else {
            awaitingSystemUi = true
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun launchApp(intent: Intent, fallback: Intent?, thenClose: Boolean) = whenUnlocked { unlocked ->
        if (!unlocked) {
            vm.launchFailed("Unlock your phone to continue.")
            return@whenUnlocked
        }
        val started = tryStart(intent) || (fallback != null && tryStart(fallback))
        if (!started) {
            vm.launchFailed("I couldn't find an app on this phone to do that.")
            return@whenUnlocked
        }
        if (thenClose) finish()
    }

    override fun onEnterAnimationComplete() {
        super.onEnterAnimationComplete()
        windowShown = true
        unlockAfterEnter?.let {
            unlockAfterEnter = null
            whenUnlocked(it)
        }
    }

    /** Runs [then] right away if the phone is unlocked; otherwise shows the unlock prompt first. */
    private fun whenUnlocked(then: (unlocked: Boolean) -> Unit) {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isKeyguardLocked) {
            then(true)
            return
        }
        awaitingSystemUi = true
        // Over the lock screen, Android cancels an unlock prompt requested while this window is still
        // opening (it does so once the window covers the lock screen), so wait for that to finish.
        if (!windowShown) {
            unlockAfterEnter = then
            return
        }
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = done(true)
            override fun onDismissCancelled() = done(false)
            override fun onDismissError() {
                Log.w(TAG, "Couldn't show the unlock prompt")
                done(false)
            }

            private fun done(unlocked: Boolean) {
                awaitingSystemUi = false
                then(unlocked)
            }
        })
    }

    private fun tryStart(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    companion object {
        /** Runs this text instead of listening (from Luma's own "try it" box, or adb in debug builds). */
        const val EXTRA_QUERY = "io.github.hyrumrichardson.luma.QUERY"
        private const val TAG = "AssistActivity"
        private const val EXTRA_TOKEN = "io.github.hyrumrichardson.luma.TOKEN"

        // Declared only in src/debug, and only startable by the adb shell.
        private const val DEBUG_QUERY_ALIAS = "io.github.hyrumrichardson.luma.assist.DebugQuery"

        // The activity is exported for ASSIST, so other apps must not be able to inject commands.
        private val tokens = mutableSetOf<String>()

        @Synchronized
        private fun consumeToken(token: String) = tokens.remove(token)

        @Synchronized
        fun queryIntent(context: Context, query: String): Intent {
            val token = UUID.randomUUID().toString()
            tokens += token
            return Intent(context, AssistActivity::class.java)
                .putExtra(EXTRA_QUERY, query)
                .putExtra(EXTRA_TOKEN, token)
        }
    }
}
