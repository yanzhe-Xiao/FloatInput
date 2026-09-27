package com.yxiao.floatinput.util

import android.content.Context
import android.content.SharedPreferences

class PreferencesHelper(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var isDockedLeft: Boolean
        get() = prefs.getBoolean(KEY_DOCK_LEFT, false)
        set(value) = prefs.edit().putBoolean(KEY_DOCK_LEFT, value).apply()

    var dockedY: Int
        get() = prefs.getInt(KEY_DOCKED_Y, 400)
        set(value) = prefs.edit().putInt(KEY_DOCKED_Y, value).apply()

    var cardX: Int
        get() = prefs.getInt(KEY_CARD_X, 100)
        set(value) = prefs.edit().putInt(KEY_CARD_X, value).apply()

    var cardY: Int
        get() = prefs.getInt(KEY_CARD_Y, 300)
        set(value) = prefs.edit().putInt(KEY_CARD_Y, value).apply()

    var isExpanded: Boolean
        get() = prefs.getBoolean(KEY_IS_EXPANDED, true)
        set(value) = prefs.edit().putBoolean(KEY_IS_EXPANDED, value).apply()

    var savedDraftText: String
        get() = prefs.getString(KEY_DRAFT_TEXT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DRAFT_TEXT, value).apply()
    var themeMode: Int
        get() = prefs.getInt(KEY_THEME_MODE, THEME_SYSTEM)
        set(value) = prefs.edit().putInt(KEY_THEME_MODE, value).apply()


    companion object {
        private const val PREF_NAME = "float_input_prefs"
        private const val KEY_DOCK_LEFT = "key_dock_left"
        private const val KEY_DOCKED_Y = "key_docked_y"
        private const val KEY_CARD_X = "key_card_x"
        private const val KEY_CARD_Y = "key_card_y"
        private const val KEY_IS_EXPANDED = "key_is_expanded"
        private const val KEY_DRAFT_TEXT = "key_draft_text"
        const val KEY_THEME_MODE = "key_theme_mode"
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2


        @Volatile
        private var instance: PreferencesHelper? = null

        fun getInstance(context: Context): PreferencesHelper {
            return instance ?: synchronized(this) {
                instance ?: PreferencesHelper(context.applicationContext).also { instance = it }
            }
        }
    }
}
