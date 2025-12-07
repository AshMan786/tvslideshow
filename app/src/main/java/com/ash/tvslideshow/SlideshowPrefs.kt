package com.ash.tvslideshow

import android.content.Context
import android.content.SharedPreferences

/**
 * Simple preferences helper for slideshow settings.
 */
object SlideshowPrefs {
    private const val PREFS_NAME = "slideshow_prefs"
    private const val KEY_FOLDER_PATH = "folder_path"
    private const val KEY_DELAY_SECONDS = "delay_seconds"
    private const val KEY_INCLUDE_SUBFOLDERS = "include_subfolders"
    private const val KEY_DISPLAY_ORDER = "display_order"
    private const val KEY_MUTE_VIDEOS = "mute_videos"

    private const val DEFAULT_DELAY = 5
    private const val DEFAULT_INCLUDE_SUBFOLDERS = true
    private const val DEFAULT_MUTE_VIDEOS = false
    const val ORDER_RANDOM = 0
    const val ORDER_DATE_ASC = 1
    const val ORDER_DATE_DESC = 2
    private const val DEFAULT_ORDER = ORDER_RANDOM

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getFolderPath(context: Context): String =
        prefs(context).getString(KEY_FOLDER_PATH, "") ?: ""

    fun setFolderPath(context: Context, path: String) {
        prefs(context).edit().putString(KEY_FOLDER_PATH, path).apply()
    }

    fun getDelaySeconds(context: Context): Int =
        prefs(context).getInt(KEY_DELAY_SECONDS, DEFAULT_DELAY)

    fun setDelaySeconds(context: Context, seconds: Int) {
        prefs(context).edit().putInt(KEY_DELAY_SECONDS, seconds).apply()
    }

    fun getIncludeSubfolders(context: Context): Boolean =
        prefs(context).getBoolean(KEY_INCLUDE_SUBFOLDERS, DEFAULT_INCLUDE_SUBFOLDERS)

    fun setIncludeSubfolders(context: Context, include: Boolean) {
        prefs(context).edit().putBoolean(KEY_INCLUDE_SUBFOLDERS, include).apply()
    }

    fun getDisplayOrder(context: Context): Int =
        prefs(context).getInt(KEY_DISPLAY_ORDER, DEFAULT_ORDER)

    fun setDisplayOrder(context: Context, order: Int) {
        prefs(context).edit().putInt(KEY_DISPLAY_ORDER, order).apply()
    }

    fun getMuteVideos(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MUTE_VIDEOS, DEFAULT_MUTE_VIDEOS)

    fun setMuteVideos(context: Context, mute: Boolean) {
        prefs(context).edit().putBoolean(KEY_MUTE_VIDEOS, mute).apply()
    }
}
