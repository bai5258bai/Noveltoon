package com.noveltoon.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class AppPreferences(private val context: Context) {

    companion object {
        val THEME_MODE = intPreferencesKey("theme_mode") // 0=light, 1=dark, 2=system
        val NOVEL_FONT_SIZE = floatPreferencesKey("novel_font_size")
        val NOVEL_LINE_SPACING = floatPreferencesKey("novel_line_spacing")
        val NOVEL_PAGE_MARGIN = intPreferencesKey("novel_page_margin")
        val NOVEL_BACKGROUND = intPreferencesKey("novel_background") // 0=white, 1=parchment, 2=gray, 3=black
        val NOVEL_READING_MODE = intPreferencesKey("novel_reading_mode") // 0=scroll, 1=page
        val COMIC_READING_DIRECTION = intPreferencesKey("comic_reading_direction") // 0=LTR, 1=RTL, 2=vertical
        val WIFI_ONLY_ORIGINAL = booleanPreferencesKey("wifi_only_original")
        val AUTO_CLEAR_CACHE_DAYS = intPreferencesKey("auto_clear_cache_days")
    }

    val themeMode: Flow<Int> = context.dataStore.data.map { it[THEME_MODE] ?: 2 }
    val novelFontSize: Flow<Float> = context.dataStore.data.map { it[NOVEL_FONT_SIZE] ?: 18f }
    val novelLineSpacing: Flow<Float> = context.dataStore.data.map { it[NOVEL_LINE_SPACING] ?: 1.5f }
    val novelPageMargin: Flow<Int> = context.dataStore.data.map { it[NOVEL_PAGE_MARGIN] ?: 16 }
    val novelBackground: Flow<Int> = context.dataStore.data.map { it[NOVEL_BACKGROUND] ?: 0 }
    val novelReadingMode: Flow<Int> = context.dataStore.data.map { it[NOVEL_READING_MODE] ?: 0 }
    val comicReadingDirection: Flow<Int> = context.dataStore.data.map { it[COMIC_READING_DIRECTION] ?: 2 }
    val wifiOnlyOriginal: Flow<Boolean> = context.dataStore.data.map { it[WIFI_ONLY_ORIGINAL] ?: false }
    val autoClearCacheDays: Flow<Int> = context.dataStore.data.map { it[AUTO_CLEAR_CACHE_DAYS] ?: 7 }

    suspend fun setThemeMode(value: Int) {
        context.dataStore.edit { it[THEME_MODE] = value }
    }

    suspend fun setNovelFontSize(value: Float) {
        context.dataStore.edit { it[NOVEL_FONT_SIZE] = value }
    }

    suspend fun setNovelLineSpacing(value: Float) {
        context.dataStore.edit { it[NOVEL_LINE_SPACING] = value }
    }

    suspend fun setNovelPageMargin(value: Int) {
        context.dataStore.edit { it[NOVEL_PAGE_MARGIN] = value }
    }

    suspend fun setNovelBackground(value: Int) {
        context.dataStore.edit { it[NOVEL_BACKGROUND] = value }
    }

    suspend fun setNovelReadingMode(value: Int) {
        context.dataStore.edit { it[NOVEL_READING_MODE] = value }
    }

    suspend fun setComicReadingDirection(value: Int) {
        context.dataStore.edit { it[COMIC_READING_DIRECTION] = value }
    }

    suspend fun setWifiOnlyOriginal(value: Boolean) {
        context.dataStore.edit { it[WIFI_ONLY_ORIGINAL] = value }
    }

    suspend fun setAutoClearCacheDays(value: Int) {
        context.dataStore.edit { it[AUTO_CLEAR_CACHE_DAYS] = value }
    }
}
