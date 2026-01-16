package info.proteo.curtain.domain.service

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import info.proteo.curtain.domain.model.SettingsVariant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsVariantManager private constructor(private val context: Context) {

    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _savedVariants = MutableStateFlow<List<SettingsVariant>>(emptyList())
    val savedVariants: StateFlow<List<SettingsVariant>> = _savedVariants.asStateFlow()

    init {
        loadVariants()
    }

    fun saveVariant(variant: SettingsVariant) {
        val variants = _savedVariants.value.toMutableList()

        val existingIndex = variants.indexOfFirst { it.id == variant.id }
        if (existingIndex != -1) {
            val updatedVariant = variant.copy(
                dateCreated = variants[existingIndex].dateCreated,
                dateModified = System.currentTimeMillis()
            )
            variants[existingIndex] = updatedVariant
        } else {
            variants.add(variant)
        }

        _savedVariants.value = variants
        persistVariants()
    }

    fun deleteVariant(variantId: String) {
        val variants = _savedVariants.value.toMutableList()
        variants.removeAll { it.id == variantId }
        _savedVariants.value = variants
        persistVariants()
    }

    fun deleteVariant(variant: SettingsVariant) {
        deleteVariant(variant.id)
    }

    fun loadVariant(variantId: String): SettingsVariant? {
        return _savedVariants.value.firstOrNull { it.id == variantId }
    }

    fun duplicateVariant(variant: SettingsVariant, newName: String): SettingsVariant {
        val duplicate = variant.copy(
            name = newName,
            dateCreated = System.currentTimeMillis(),
            dateModified = System.currentTimeMillis()
        )
        saveVariant(duplicate)
        return duplicate
    }

    val sortedVariants: List<SettingsVariant>
        get() = _savedVariants.value.sortedByDescending { it.dateModified }

    private fun persistVariants() {
        val json = gson.toJson(_savedVariants.value)
        preferences.edit().putString(VARIANTS_KEY, json).apply()
    }

    private fun loadVariants() {
        val json = preferences.getString(VARIANTS_KEY, null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<SettingsVariant>>() {}.type
                val variants = gson.fromJson<List<SettingsVariant>>(json, type)
                _savedVariants.value = variants ?: emptyList()
            } catch (e: Exception) {
                _savedVariants.value = emptyList()
            }
        }
    }

    fun exportVariant(variant: SettingsVariant): String {
        return gson.toJson(variant)
    }

    fun importVariant(json: String): SettingsVariant? {
        return try {
            val variant = gson.fromJson(json, SettingsVariant::class.java)
            val importedVariant = variant.copy(
                name = "${variant.name} (Imported)",
                dateCreated = System.currentTimeMillis(),
                dateModified = System.currentTimeMillis()
            )
            saveVariant(importedVariant)
            importedVariant
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val PREFS_NAME = "curtain_settings_variants"
        private const val VARIANTS_KEY = "saved_variants"

        @Volatile
        private var instance: SettingsVariantManager? = null

        fun getInstance(context: Context): SettingsVariantManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsVariantManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
