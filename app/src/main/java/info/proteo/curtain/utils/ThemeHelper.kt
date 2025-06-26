package info.proteo.curtain.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager

object ThemeHelper {
    private const val THEME_PREFERENCE_KEY = "theme_preference"
    private const val DEFAULT_THEME = "system"

    fun applyTheme(themeMode: Int) {
        AppCompatDelegate.setDefaultNightMode(themeMode)
    }

    fun applyTheme(context: Context) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val themePreference = preferences.getString(THEME_PREFERENCE_KEY, DEFAULT_THEME)
        
        val themeMode = when (themePreference) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            "system" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        
        applyTheme(themeMode)
    }

    fun getCurrentTheme(context: Context): String {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        return preferences.getString(THEME_PREFERENCE_KEY, DEFAULT_THEME) ?: DEFAULT_THEME
    }
}