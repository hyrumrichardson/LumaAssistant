package io.github.hyrumrichardson.luma

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import io.github.hyrumrichardson.luma.home.GoogleHome
import io.github.hyrumrichardson.luma.home.SmartHome
import io.github.hyrumrichardson.luma.phone.Flashlight
import io.github.hyrumrichardson.luma.schedule.DeviceActionScheduler
import io.github.hyrumrichardson.luma.schedule.ScheduledActionStore

class LumaApp : Application() {
    val home: SmartHome by lazy { GoogleHome(this) }
    val scheduler by lazy { DeviceActionScheduler(this, ScheduledActionStore(this)) }
    val settings by lazy { LumaSettings(this) }
    val flashlight by lazy { Flashlight(this) }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}

val Context.luma: LumaApp get() = applicationContext as LumaApp

class LumaSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var speakReplies: Boolean
        get() = prefs.getBoolean("speak_replies", true)
        set(value) = prefs.edit { putBoolean("speak_replies", value) }

    var searchEngine: SearchEngine
        get() = prefs.getString("search_engine", null)
            ?.let { name -> SearchEngine.entries.firstOrNull { it.name == name } }
            ?: SearchEngine.GOOGLE
        set(value) = prefs.edit { putString("search_engine", value.name) }
}
