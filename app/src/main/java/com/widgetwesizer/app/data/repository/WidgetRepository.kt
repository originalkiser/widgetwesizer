package com.widgetwesizer.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.widgetwesizer.app.data.model.GridSelection
import com.widgetwesizer.app.data.model.ViewportEntry
import com.widgetwesizer.app.data.model.WidgetEntry
import com.widgetwesizer.app.data.model.WidgetInstanceConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "widget_wesizer_prefs")

class WidgetRepository(private val context: Context) {

    private val widgetEntriesKey = stringPreferencesKey("widget_board_entries")
    private val viewportKey = stringPreferencesKey("board_viewport")
    private val gridSelectionsKey = stringPreferencesKey("grid_selections")
    private val widgetInstanceConfigsKey = stringPreferencesKey("widget_instance_configs")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun saveWidgets(entries: List<WidgetEntry>) {
        context.dataStore.edit { prefs ->
            prefs[widgetEntriesKey] = json.encodeToString(entries)
        }
    }

    fun getWidgets(): Flow<List<WidgetEntry>> {
        return context.dataStore.data.map { prefs ->
            val raw = prefs[widgetEntriesKey] ?: return@map emptyList()
            try { json.decodeFromString<List<WidgetEntry>>(raw) } catch (e: Exception) { emptyList() }
        }
    }

    suspend fun saveGridSelections(selections: List<GridSelection>) {
        context.dataStore.edit { prefs ->
            prefs[gridSelectionsKey] = json.encodeToString(selections)
        }
    }

    fun getGridSelections(): Flow<List<GridSelection>> {
        return context.dataStore.data.map { prefs ->
            val raw = prefs[gridSelectionsKey]
            if (raw != null) {
                try { json.decodeFromString<List<GridSelection>>(raw) } catch (e: Exception) { defaultSelections() }
            } else {
                // Migrate from legacy single ViewportEntry if it exists
                val vpRaw = prefs[viewportKey]
                if (vpRaw != null) {
                    try {
                        val vp = json.decodeFromString<ViewportEntry>(vpRaw)
                        val cols = (vp.width / 100f).roundToInt().coerceIn(1, 4)
                        val rows = (vp.height / 100f).roundToInt().coerceIn(1, 5)
                        listOf(GridSelection(1, cols, rows, vp.x, vp.y))
                    } catch (e: Exception) { defaultSelections() }
                } else {
                    defaultSelections()
                }
            }
        }
    }

    suspend fun saveWidgetInstanceConfigs(configs: List<WidgetInstanceConfig>) {
        context.dataStore.edit { prefs ->
            prefs[widgetInstanceConfigsKey] = json.encodeToString(configs)
        }
    }

    fun getWidgetInstanceConfigs(): Flow<List<WidgetInstanceConfig>> {
        return context.dataStore.data.map { prefs ->
            val raw = prefs[widgetInstanceConfigsKey] ?: return@map emptyList()
            try { json.decodeFromString<List<WidgetInstanceConfig>>(raw) } catch (e: Exception) { emptyList() }
        }
    }

    private fun defaultSelections() = listOf(GridSelection(id = 1, cols = 2, rows = 2))
}
