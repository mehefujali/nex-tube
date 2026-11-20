package com.nextube.app.core

import android.content.Context
import com.nextube.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A first-party, privacy-friendly sponsored card loaded from `sponsors.json` on GitHub. */
data class SponsorItem(
    val id: String,
    val badge: String,
    val title: String,
    val subtitle: String,
    val cta: String,
    val url: String,
    val imageUrl: String = "",
)

/**
 * Serverless sponsor feed: fetches `sponsors.json` directly from the GitHub repo so sponsors
 * can be updated remotely without requiring an app update. Zero tracking, zero 3rd-party SDKs.
 */
object SponsorFeed {

    private fun cacheFile(context: Context) = File(context.cacheDir, "sponsors_cache.json")

    /** Returns the active sponsor card (if enabled), falling back to local cache if offline. */
    suspend fun fetch(context: Context): SponsorItem? = withContext(Dispatchers.IO) {
        val raw = runCatching {
            // 5-minute cache-buster so edits to sponsors.json on GitHub appear quickly.
            val url = BuildConfig.SPONSORS_URL + "?t=" + (System.currentTimeMillis() / 300_000)
            val text = httpGet(url)
            runCatching { cacheFile(context).writeText(text) }
            text
        }.getOrElse {
            runCatching { cacheFile(context).takeIf { it.exists() }?.readText() }.getOrNull()
        } ?: return@withContext null

        parseFirst(raw)
    }

    private fun parseFirst(raw: String): SponsorItem? = runCatching {
        val root = JSONObject(raw)
        if (!root.optBoolean("enabled", false)) return@runCatching null
        val arr = root.optJSONArray("sponsors") ?: return@runCatching null
        val list = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id")
            val title = o.optString("title")
            val url = o.optString("url")
            if (id.isEmpty() || title.isEmpty() || url.isEmpty()) return@mapNotNull null
            SponsorItem(
                id = id,
                badge = o.optString("badge").ifEmpty { "Sponsored" },
                title = title,
                subtitle = o.optString("subtitle"),
                cta = o.optString("cta").ifEmpty { "Learn more" },
                url = url,
                imageUrl = o.optString("imageUrl"),
            )
        }
        if (list.isEmpty()) null else list.random()
    }.getOrNull()

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", "NexTube/${BuildConfig.VERSION_NAME}")
        }
        return try {
            check(conn.responseCode in 200..299) { "HTTP ${conn.responseCode}" }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
