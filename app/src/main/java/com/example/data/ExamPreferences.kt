package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.ExamDefaults
import com.example.model.ExamSettings
import com.example.model.SavedExamUrl
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ExamPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("exam_browser_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LAST_URL = "last_url"
        private const val KEY_IS_FULLSCREEN = "is_fullscreen"
        private const val KEY_IS_KEEP_SCREEN_ON = "is_keep_screen_on"
        private const val KEY_IS_LOCK_BACK = "is_lock_back"
        private const val KEY_IS_ALLOW_ZOOM = "is_allow_zoom"
        private const val KEY_IS_DESKTOP_MODE = "is_desktop_mode"
        private const val KEY_SAVED_URLS = "saved_urls_json"
    }

    fun getLastUrl(): String {
        return prefs.getString(KEY_LAST_URL, "") ?: ""
    }

    fun saveLastUrl(url: String) {
        prefs.edit().putString(KEY_LAST_URL, url).apply()
    }

    fun getSettings(): ExamSettings {
        return ExamSettings(
            url = prefs.getString(KEY_LAST_URL, "https://forms.google.com") ?: "https://forms.google.com",
            isFullscreen = prefs.getBoolean(KEY_IS_FULLSCREEN, true),
            isKeepScreenOn = prefs.getBoolean(KEY_IS_KEEP_SCREEN_ON, true),
            isLockBackPress = prefs.getBoolean(KEY_IS_LOCK_BACK, true),
            isAllowZoom = prefs.getBoolean(KEY_IS_ALLOW_ZOOM, true),
            isDesktopMode = prefs.getBoolean(KEY_IS_DESKTOP_MODE, false)
        )
    }

    fun saveSettings(settings: ExamSettings) {
        prefs.edit()
            .putString(KEY_LAST_URL, settings.url)
            .putBoolean(KEY_IS_FULLSCREEN, settings.isFullscreen)
            .putBoolean(KEY_IS_KEEP_SCREEN_ON, settings.isKeepScreenOn)
            .putBoolean(KEY_IS_LOCK_BACK, settings.isLockBackPress)
            .putBoolean(KEY_IS_ALLOW_ZOOM, settings.isAllowZoom)
            .putBoolean(KEY_IS_DESKTOP_MODE, settings.isDesktopMode)
            .apply()
    }

    fun getSavedUrls(): List<SavedExamUrl> {
        val jsonStr = prefs.getString(KEY_SAVED_URLS, null) ?: return defaultSavedList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<SavedExamUrl>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    SavedExamUrl(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        label = obj.optString("label", "Tautan Ujian"),
                        url = obj.optString("url", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            if (list.isEmpty()) defaultSavedList() else list
        } catch (e: Exception) {
            defaultSavedList()
        }
    }

    fun addSavedUrl(label: String, url: String) {
        val current = getSavedUrls().toMutableList()
        val normalized = ExamDefaults.normalizeUrl(url)
        // Remove existing same url if present
        current.removeAll { it.url.equals(normalized, ignoreCase = true) }
        current.add(
            0,
            SavedExamUrl(
                id = UUID.randomUUID().toString(),
                label = label.ifBlank { "Tautan Ujian" },
                url = normalized,
                timestamp = System.currentTimeMillis()
            )
        )
        // Keep max 10
        val trimmed = if (current.size > 10) current.subList(0, 10) else current
        persistSavedUrls(trimmed)
    }

    fun removeSavedUrl(id: String) {
        val current = getSavedUrls().filterNot { it.id == id }
        persistSavedUrls(current)
    }

    private fun persistSavedUrls(list: List<SavedExamUrl>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("label", item.label)
            obj.put("url", item.url)
            obj.put("timestamp", item.timestamp)
            array.put(obj)
        }
        prefs.edit().putString(KEY_SAVED_URLS, array.toString()).apply()
    }

    private fun defaultSavedList(): List<SavedExamUrl> {
        return listOf(
            SavedExamUrl(
                id = "1",
                label = "Google Forms CBT",
                url = "https://forms.google.com"
            ),
            SavedExamUrl(
                id = "2",
                label = "Simulasi Ujian Pusmendik",
                url = "https://pusmendik.kemdikbud.go.id/anbk"
            )
        )
    }
}
