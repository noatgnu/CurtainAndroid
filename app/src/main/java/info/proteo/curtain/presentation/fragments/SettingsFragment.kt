package info.proteo.curtain.presentation.fragments

import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import info.proteo.curtain.R
import info.proteo.curtain.utils.ThemeHelper

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val themePreference = findPreference<ListPreference>("theme_preference")
        themePreference?.setOnPreferenceChangeListener { _, newValue ->
            val themeMode = when (newValue as String) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                "system" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            
            ThemeHelper.applyTheme(themeMode)
            
            // Recreate the activity to apply the theme change immediately
            requireActivity().recreate()
            true
        }
    }
}