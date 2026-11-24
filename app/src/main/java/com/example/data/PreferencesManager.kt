package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "openv_prefs")

data class UserPrefs(
  val editMode: Boolean = false,
  val wordWrap: Boolean = false,
  val showLineNumbers: Boolean = true,
  val recentLocalPaths: List<String> = emptyList(),
)

class PreferencesManager(private val context: Context) {
  private val keyEditMode = booleanPreferencesKey("edit_mode")
  private val keyWordWrap = booleanPreferencesKey("word_wrap")
  private val keyLineNumbers = booleanPreferencesKey("line_numbers")
  private val keyRecentPaths = stringPreferencesKey("recent_paths")

  val prefsFlow: Flow<UserPrefs> =
    context.dataStore.data.map { prefs ->
      val rawRecent = prefs[keyRecentPaths].orEmpty()
      UserPrefs(
        editMode = prefs[keyEditMode] ?: false,
        wordWrap = prefs[keyWordWrap] ?: false,
        showLineNumbers = prefs[keyLineNumbers] ?: true,
        recentLocalPaths =
          rawRecent.split('|').filter { it.isNotBlank() }.take(8),
      )
    }

  suspend fun setEditMode(enabled: Boolean) {
    context.dataStore.edit { it[keyEditMode] = enabled }
  }

  suspend fun setWordWrap(enabled: Boolean) {
    context.dataStore.edit { it[keyWordWrap] = enabled }
  }

  suspend fun setShowLineNumbers(enabled: Boolean) {
    context.dataStore.edit { it[keyLineNumbers] = enabled }
  }

  suspend fun addRecentPath(path: String) {
    context.dataStore.edit { prefs ->
      val current =
        prefs[keyRecentPaths].orEmpty().split('|').filter { it.isNotBlank() && it != path }
      val updated = (listOf(path) + current).take(8)
      prefs[keyRecentPaths] = updated.joinToString("|")
    }
  }
}
