package com.nextube.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Fast YouTube autocomplete suggestions (no API key, no server). */
object SearchSuggest {
    suspend fun suggest(query: String): List<String> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        runCatching {
            val encoded = URLEncoder.encode(q, "UTF-8")
            val conn = (URL("https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=$encoded")
                .openConnection() as HttpURLConnection).apply {
                connectTimeout = 4_000
                readTimeout = 4_000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            val body = try {
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
            val arr = JSONArray(body).optJSONArray(1) ?: return@runCatching emptyList()
            List(minOf(arr.length(), 7)) { arr.optString(it) }.filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
    }
}
