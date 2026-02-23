package com.app.ralaunch.feature.main.update

import android.content.Context
import com.app.ralaunch.shared.core.platform.AppConstants

object LauncherUpdatePreferences {
    private const val KEY_IGNORED_UPDATE_VERSION = "ignored_update_version"

    fun getIgnoredUpdateVersion(context: Context): String? {
        return context
            .getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_IGNORED_UPDATE_VERSION, null)
            ?.trim()
            ?.ifEmpty { null }
    }

    fun setIgnoredUpdateVersion(context: Context, version: String) {
        context
            .getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_IGNORED_UPDATE_VERSION, version.trim())
            .apply()
    }

    fun clearIgnoredUpdateVersion(context: Context) {
        context
            .getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_IGNORED_UPDATE_VERSION)
            .apply()
    }
}
