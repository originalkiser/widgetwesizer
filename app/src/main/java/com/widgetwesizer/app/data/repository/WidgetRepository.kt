package com.widgetwesizer.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.widgetwesizer.app.data.model.ViewportEntry
import com.widgetwesizer.app.data.model.WidgetEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "widget_wesizer_prefs")

class WidgetRepository(private val context: Context) {

    private val widgetEntriesKey = stringPreferencesKey("widget_board_entries")
    private val viewportKey = stringPreferencesKey("board_viewport")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun saveWidgets(entries: List<WidgetEntry>) {
        context.dataStore.edit { prefs ->
            prefs[widgetEntriesKey] = json.encodeToString(entries)
        }
    }

    fun getWidgets(): Flow<List<WidgetEntry>> {
        return context.dataStore.data.map { prefs ->
            val raw = prefs[widgetEntriesKey] ?: return@map emptyList()
            try {
                json.decodeFromString<List<WidgetEntry>>(raw)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun saveViewport(viewport: ViewportEntry) {
        context.dataStore.edit { prefs ->
            prefs[viewportKey] = json.encodeToString(viewport)
        }
    }

    fun getViewport(): Flow<ViewportEntry> {
        return context.dataStore.data.map { prefs ->
            val raw = prefs[viewportKey] ?: return@map ViewportEntry()
            try { json.decodeFromString<ViewportEntry>(raw) } catch (e: Exception) { ViewportEntry() }
        }
    }
}
