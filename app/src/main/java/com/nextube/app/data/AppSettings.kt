package com.nextube.app.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

object AppSettings {
    private lateinit var prefs: SharedPreferences

    private val _theme = MutableStateFlow(ThemeMode.SYSTEM)
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _maxConcurrent = MutableStateFlow(2)
    val maxConcurrent: StateFlow<Int> = _maxConcurrent.asStateFlow()

    private val _autoUpdate = MutableStateFlow(true)
    val autoUpdate: StateFlow<Boolean> = _autoUpdate.asStateFlow()

    private val _saveFolderUri = MutableStateFlow<String?>(null)
    val saveFolderUri: StateFlow<String?> = _saveFolderUri.asStateFlow()

    private val _saveFolderLabel = MutableStateFlow<String?>(null)
    val saveFolderLabel: StateFlow<String?> = _saveFolderLabel.asStateFlow()

    private val _supportBannerDismissed = MutableStateFlow(false)
    val supportBannerDismissed: StateFlow<Boolean> = _supportBannerDismissed.asStateFlow()

    private val _showSponsors = MutableStateFlow(true)
    val showSponsors: StateFlow<Boolean> = _showSponsors.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _theme.value = runCatching { ThemeMode.valueOf(prefs.getString("theme", null)!!) }.getOrDefault(ThemeMode.SYSTEM)
        _maxConcurrent.value = prefs.getInt("max_concurrent", 2).coerceIn(1, 3)
        _autoUpdate.value = prefs.getBoolean("auto_update", true)
        _saveFolderUri.value = prefs.getString("save_folder_uri", null)?.takeIf { it.isNotBlank() }
        _saveFolderLabel.value = prefs.getString("save_folder_label", null)?.takeIf { it.isNotBlank() }
        _supportBannerDismissed.value = prefs.getBoolean("support_banner_dismissed", false)
        _showSponsors.value = prefs.getBoolean("show_sponsors", true)
    }

    fun setShowSponsors(on: Boolean) {
        _showSponsors.value = on
        prefs.edit().putBoolean("show_sponsors", on).apply()
    }

    fun dismissSupportBanner() {
        _supportBannerDismissed.value = true
        prefs.edit().putBoolean("support_banner_dismissed", true).apply()
    }

    fun setTheme(mode: ThemeMode) { _theme.value = mode; prefs.edit().putString("theme", mode.name).apply() }
    fun setMaxConcurrent(n: Int) { _maxConcurrent.value = n; prefs.edit().putInt("max_concurrent", n).apply() }
    fun setAutoUpdate(on: Boolean) { _autoUpdate.value = on; prefs.edit().putBoolean("auto_update", on).apply() }

    fun setSaveFolder(uri: String?, label: String?) {
        _saveFolderUri.value = uri
        _saveFolderLabel.value = label
        prefs.edit().apply {
            if (uri == null) {
                remove("save_folder_uri")
                remove("save_folder_label")
            } else {
                putString("save_folder_uri", uri)
                putString("save_folder_label", label)
            }
        }.apply()
    }

    /** Turns a SAF tree URI like `primary:Movies/NexTube` into a clean readable folder label. */
    fun friendlyTreeLabel(treeUri: Uri): String {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return treeUri.lastPathSegment ?: "Custom folder"
        val parts = docId.split(':', limit = 2)
        val volume = parts.getOrNull(0).orEmpty()
        val path = parts.getOrNull(1).orEmpty().trim('/')
        return when {
            volume.equals("primary", ignoreCase = true) -> path.ifEmpty { "Internal Storage" }
            volume.isNotEmpty() && path.isNotEmpty() -> "SD Card/$path"
            volume.isNotEmpty() -> "SD Card"
            else -> docId
        }
    }

    var lastEngineUpdate: Long
        get() = prefs.getLong("last_engine_update", 0)
        set(v) { prefs.edit().putLong("last_engine_update", v).apply() }
}
