package com.nextube.app.core

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Stores optional Netscape-format cookies locally on-device (noBackupFilesDir) so yt-dlp
 * can fetch Instagram Reels/Posts/Stories when Instagram requires sign-in or rate-limits guests.
 */
object CookieStore {
    private var file: File? = null
    private val _hasInstagram = MutableStateFlow(false)
    val hasInstagram: StateFlow<Boolean> = _hasInstagram.asStateFlow()

    fun init(context: Context) {
        file = File(context.noBackupFilesDir, "cookies.txt")
        refreshState()
    }

    fun cookiesFile(): File? = file?.takeIf { it.exists() && it.length() > 30 }

    private fun refreshState() {
        val f = file
        _hasInstagram.value = f != null && f.exists() && runCatching {
            f.readText().contains("instagram.com") && f.readText().contains("sessionid")
        }.getOrDefault(false)
    }

    /** Exports cookies from Android WebView's CookieManager into Netscape cookies.txt format. */
    fun saveInstagramFromWebView(): Boolean {
        val f = file ?: return false
        val cm = CookieManager.getInstance()
        cm.flush()
        val raw = cm.getCookie("https://www.instagram.com").orEmpty()
        if (!raw.contains("sessionid=")) return false

        val expiry = (System.currentTimeMillis() / 1000L) + 365L * 24 * 3600
        val lines = buildString {
            appendLine("# Netscape HTTP Cookie File")
            appendLine("# Generated locally by NexTube")
            raw.split(';').forEach { pair ->
                val idx = pair.indexOf('=')
                if (idx > 0) {
                    val name = pair.substring(0, idx).trim()
                    val value = pair.substring(idx + 1).trim()
                    if (name.isNotEmpty() && value.isNotEmpty()) {
                        appendLine(".instagram.com\tTRUE\t/\tTRUE\t$expiry\t$name\t$value")
                    }
                }
            }
        }
        f.writeText(lines)
        refreshState()
        return _hasInstagram.value
    }

    fun clear() {
        runCatching { file?.delete() }
        runCatching {
            val cm = CookieManager.getInstance()
            cm.removeAllCookies(null)
            cm.flush()
        }
        _hasInstagram.value = false
    }
}
