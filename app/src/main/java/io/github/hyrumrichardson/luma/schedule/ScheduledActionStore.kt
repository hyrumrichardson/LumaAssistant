package io.github.hyrumrichardson.luma.schedule

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A device action waiting to run, e.g. "turn off the living room lights at 10:40". */
data class ScheduledAction(
    val id: String,
    val deviceIds: List<String>,
    val description: String,
    val turnOn: Boolean,
    val triggerAtMillis: Long,
)

/** Pending actions, persisted so they survive process death and reboots. */
class ScheduledActionStore(context: Context) {
    private val prefs = context.getSharedPreferences("scheduled_actions", Context.MODE_PRIVATE)
    private val _actions = MutableStateFlow(load())
    val actions: StateFlow<List<ScheduledAction>> = _actions.asStateFlow()

    fun get(id: String): ScheduledAction? = _actions.value.firstOrNull { it.id == id }

    @Synchronized
    fun add(action: ScheduledAction) = save(_actions.value.filterNot { it.id == action.id } + action)

    @Synchronized
    fun remove(id: String) = save(_actions.value.filterNot { it.id == id })

    private fun save(actions: List<ScheduledAction>) {
        val sorted = actions.sortedBy { it.triggerAtMillis }
        val json = JSONArray()
        for (a in sorted) {
            json.put(
                JSONObject()
                    .put("id", a.id)
                    .put("deviceIds", JSONArray(a.deviceIds))
                    .put("description", a.description)
                    .put("turnOn", a.turnOn)
                    .put("triggerAtMillis", a.triggerAtMillis),
            )
        }
        prefs.edit { putString(KEY, json.toString()) }
        _actions.value = sorted
    }

    private fun load(): List<ScheduledAction> {
        val json = JSONArray(prefs.getString(KEY, null) ?: return emptyList())
        return (0 until json.length()).map { i ->
            val o = json.getJSONObject(i)
            val ids = o.getJSONArray("deviceIds")
            ScheduledAction(
                id = o.getString("id"),
                deviceIds = (0 until ids.length()).map(ids::getString),
                description = o.getString("description"),
                turnOn = o.getBoolean("turnOn"),
                triggerAtMillis = o.getLong("triggerAtMillis"),
            )
        }
    }

    private companion object {
        const val KEY = "actions"
    }
}
