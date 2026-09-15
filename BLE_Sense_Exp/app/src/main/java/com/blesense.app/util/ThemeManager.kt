package com.blesense.app.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Singleton object to manage app-wide theme state
object ThemeManager {
    private const val PREFS_NAME = "blesense_theme_prefs"
    private const val KEY_IS_DARK_MODE = "is_dark_mode"
    private const val KEY_HAS_PREFERENCE = "has_user_preference"

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    private var isInitialized = false

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun toggleDarkMode(context: Context, value: Boolean) {
        _isDarkMode.value = value
        isInitialized = true
        
        getPrefs(context).edit().apply {
            putBoolean(KEY_IS_DARK_MODE, value)
            putBoolean(KEY_HAS_PREFERENCE, true)
            apply()
        }
    }

    fun initializeWithSystemTheme(context: Context, isSystemDark: Boolean) {
        if (!isInitialized) {
            val prefs = getPrefs(context)
            val hasPreference = prefs.getBoolean(KEY_HAS_PREFERENCE, false)
            
            if (hasPreference) {
                _isDarkMode.value = prefs.getBoolean(KEY_IS_DARK_MODE, isSystemDark)
            } else {
                _isDarkMode.value = isSystemDark
            }
            isInitialized = true
        }
    }
}
