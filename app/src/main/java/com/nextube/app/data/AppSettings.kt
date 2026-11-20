package com.nextube.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class WatermarkPosition(val label: String) { BOTTOM_LEFT("Left"), BOTTOM_CENTER("Center"), BOTTOM_RIGHT("Right") }

object AppSettings {
    private lateinit var prefs: SharedPreferences

    private val _theme = MutableStateFlow(ThemeMode.SYSTEM)
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _maxConcurrent = MutableStateFlow(2)
    val maxConcurrent: StateFlow<Int> = _maxConcurrent.asStateFlow()

    private val _autoUpdate = MutableStateFlow(true)
    val autoUpdate: StateFlow<Boolean> = _autoUpdate.asStateFlow()

    private val _watermark = MutableStateFlow(false)
    val watermark: StateFlow<Boolean> = _watermark.asStateFlow()

    private val _watermarkPosition = MutableStateFlow(WatermarkPosition.BOTTOM_RIGHT)
    val watermarkPosition: StateFlow<WatermarkPosition> = _watermarkPosition.asStateFlow()

    fun setWatermark(on: Boolean) { _watermark.value = on; prefs.edit().putBoolean("watermark", on).apply() }
    fun setWatermarkPosition(p: WatermarkPosition) {
        _watermarkPosition.value = p; prefs.edit().putString("watermark_position", p.name).apply()
    }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _watermark.value = prefs.getBoolean("watermark", false)
        _watermarkPosition.value = runCatching { WatermarkPosition.valueOf(prefs.getString("watermark_position", null)!!) }
            .getOrDefault(WatermarkPosition.BOTTOM_RIGHT)
        _theme.value = runCatching { ThemeMode.valueOf(prefs.getString("theme", null)!!) }.getOrDefault(ThemeMode.SYSTEM)
        _maxConcurrent.value = prefs.getInt("max_concurrent", 2).coerceIn(1, 3)
        _autoUpdate.value = prefs.getBoolean("auto_update", true)
    }

    fun setTheme(mode: ThemeMode) { _theme.value = mode; prefs.edit().putString("theme", mode.name).apply() }
    fun setMaxConcurrent(n: Int) { _maxConcurrent.value = n; prefs.edit().putInt("max_concurrent", n).apply() }
    fun setAutoUpdate(on: Boolean) { _autoUpdate.value = on; prefs.edit().putBoolean("auto_update", on).apply() }

    var lastEngineUpdate: Long
        get() = prefs.getLong("last_engine_update", 0)
        set(v) { prefs.edit().putLong("last_engine_update", v).apply() }
}
