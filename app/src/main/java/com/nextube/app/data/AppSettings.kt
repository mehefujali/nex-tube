package com.nextube.app.data

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.util.Locale

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AppLanguage(val code: String?, val label: String) {
    SYSTEM(null, "System"),
    EN("en", "EN"),
    BN("bn", "বাংলা"),
    HI("hi", "हिन्दी"),
}

object AppSettings {
    private lateinit var prefs: SharedPreferences

    private val _theme = MutableStateFlow(ThemeMode.SYSTEM)
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _dynamicColor = MutableStateFlow(false)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _language = MutableStateFlow(AppLanguage.SYSTEM)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

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

    private val _wifiOnly = MutableStateFlow(false)
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    /** 0 = Unlimited, otherwise KB/s (1024 = 1 MB/s, 2048 = 2 MB/s, 5120 = 5 MB/s). */
    private val _speedLimitKbps = MutableStateFlow(0)
    val speedLimitKbps: StateFlow<Int> = _speedLimitKbps.asStateFlow()

    private val _sponsorBlock = MutableStateFlow(false)
    val sponsorBlock: StateFlow<Boolean> = _sponsorBlock.asStateFlow()

    private val _embedSubs = MutableStateFlow(false)
    val embedSubs: StateFlow<Boolean> = _embedSubs.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _theme.value = runCatching { ThemeMode.valueOf(prefs.getString("theme", null)!!) }.getOrDefault(ThemeMode.SYSTEM)
        _dynamicColor.value = prefs.getBoolean("dynamic_color", false)
        _language.value = runCatching { AppLanguage.valueOf(prefs.getString("language", null)!!) }.getOrDefault(AppLanguage.SYSTEM)
        _maxConcurrent.value = prefs.getInt("max_concurrent", 2).coerceIn(1, 3)
        _autoUpdate.value = prefs.getBoolean("auto_update", true)
        _saveFolderUri.value = prefs.getString("save_folder_uri", null)?.takeIf { it.isNotBlank() }
        _saveFolderLabel.value = prefs.getString("save_folder_label", null)?.takeIf { it.isNotBlank() }
        _supportBannerDismissed.value = prefs.getBoolean("support_banner_dismissed", false)
        _showSponsors.value = prefs.getBoolean("show_sponsors", true)
        _wifiOnly.value = prefs.getBoolean("wifi_only", false)
        _speedLimitKbps.value = prefs.getInt("speed_limit_kbps", 0)
        _sponsorBlock.value = prefs.getBoolean("sponsor_block", false)
        _embedSubs.value = prefs.getBoolean("embed_subs", false)
        _searchHistory.value = runCatching {
            val arr = JSONArray(prefs.getString("search_history", "[]"))
            List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    fun setLanguage(lang: AppLanguage) { _language.value = lang; prefs.edit().putString("language", lang.name).apply() }
    fun setWifiOnly(on: Boolean) { _wifiOnly.value = on; prefs.edit().putBoolean("wifi_only", on).apply() }
    fun setSpeedLimitKbps(kbps: Int) { _speedLimitKbps.value = kbps; prefs.edit().putInt("speed_limit_kbps", kbps).apply() }
    fun setSponsorBlock(on: Boolean) { _sponsorBlock.value = on; prefs.edit().putBoolean("sponsor_block", on).apply() }
    fun setEmbedSubs(on: Boolean) { _embedSubs.value = on; prefs.edit().putBoolean("embed_subs", on).apply() }

    fun addSearchHistory(query: String) {
        val q = query.trim()
        if (q.length < 2) return
        val updated = (listOf(q) + _searchHistory.value.filterNot { it.equals(q, ignoreCase = true) }).take(10)
        _searchHistory.value = updated
        prefs.edit().putString("search_history", JSONArray(updated).toString()).apply()
    }

    fun removeSearchHistory(query: String) {
        val updated = _searchHistory.value.filterNot { it == query }
        _searchHistory.value = updated
        prefs.edit().putString("search_history", JSONArray(updated).toString()).apply()
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        prefs.edit().remove("search_history").apply()
    }

    fun wrapLocale(base: Context): Context {
        val sp = base.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val lang = runCatching { AppLanguage.valueOf(sp.getString("language", null)!!) }.getOrDefault(AppLanguage.SYSTEM)
        val code = lang.code ?: return base
        val locale = Locale.forLanguageTag(code)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
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
    fun setDynamicColor(on: Boolean) { _dynamicColor.value = on; prefs.edit().putBoolean("dynamic_color", on).apply() }
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
