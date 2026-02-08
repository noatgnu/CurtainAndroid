package info.proteo.curtain.domain.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "theme_preferences")

class ThemePreference(private val context: Context) {

    companion object {
        private val THEME_KEY = stringPreferencesKey("app_theme")
        private val CURTAIN_TYPE_FILTER_KEY = stringPreferencesKey("curtain_type_filter")
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val THEME_SYSTEM = "system"
        const val FILTER_ALL = "all"
        const val FILTER_TP = "TP"
        const val FILTER_PTM = "PTM"
    }

    val themeMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_KEY] ?: THEME_SYSTEM
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = mode
        }
    }

    val curtainTypeFilter: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CURTAIN_TYPE_FILTER_KEY] ?: FILTER_ALL
    }

    suspend fun setCurtainTypeFilter(filter: String) {
        context.dataStore.edit { preferences ->
            preferences[CURTAIN_TYPE_FILTER_KEY] = filter
        }
    }
}
