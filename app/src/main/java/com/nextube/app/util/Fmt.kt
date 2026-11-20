package com.nextube.app.util

import java.util.Locale

object Fmt {
    fun bytes(b: Long): String {
        if (b <= 0) return "—"
        val units = arrayOf("B", "KB", "MB", "GB")
        var v = b.toDouble()
        var i = 0
        while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
        return if (i <= 1) "%.0f %s".format(Locale.US, v, units[i])
        else "%.1f %s".format(Locale.US, v, units[i])
    }

    fun duration(sec: Long): String {
        if (sec <= 0) return ""
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) "%d:%02d:%02d".format(Locale.US, h, m, s)
        else "%d:%02d".format(Locale.US, m, s)
    }

    fun views(n: Long): String = when {
        n <= 0 -> ""
        n >= 1_000_000_000 -> "%.1fB views".format(Locale.US, n / 1e9)
        n >= 1_000_000 -> "%.1fM views".format(Locale.US, n / 1e6)
        n >= 1_000 -> "%.1fK views".format(Locale.US, n / 1e3)
        else -> "$n views"
    }.replace(".0", "")

    private val urlRegex = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    /** Pulls the first http(s) link out of shared text such as "Check this out https://youtu.be/xyz". */
    fun extractUrl(text: String?): String? = text?.let { urlRegex.find(it)?.value?.trimEnd('.', ',', ')', '"') }

    fun isUrl(text: String): Boolean {
        val t = text.trim()
        return t.startsWith("http://", true) || t.startsWith("https://", true) ||
            Regex("""^(www\.|m\.)?(youtube\.com|youtu\.be)/""", RegexOption.IGNORE_CASE).containsMatchIn(t)
    }

    fun normalizeUrl(text: String): String {
        val t = text.trim()
        return if (t.startsWith("http", true)) t else "https://$t"
    }

    /** True for pure playlist URLs such as `youtube.com/playlist?list=...`. */
    fun isPlaylistUrl(url: String): Boolean =
        Regex("""youtube\.com/playlist\?.*list=([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE).containsMatchIn(url)

    /** Extracts a non-mix playlist URL from a watch URL like `watch?v=...&list=PL...`. */
    fun extractPlaylistUrl(url: String): String? {
        val match = Regex("""[?&]list=([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE).find(url) ?: return null
        val listId = match.groupValues[1]
        // Skip auto-generated YouTube radio mixes (RD...) which aren't real downloadable playlists.
        if (listId.startsWith("RD", ignoreCase = true)) return null
        return "https://www.youtube.com/playlist?list=$listId"
    }
}
