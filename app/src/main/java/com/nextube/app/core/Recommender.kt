package com.nextube.app.core

import com.nextube.app.data.DownloadTask
import com.nextube.app.data.MediaKind
import com.nextube.app.data.SearchItem

/**
 * On-device personalized feed recommender: infers the user's interests from their local
 * download history (channels, title keywords, audio/video preference) with zero server or tracking.
 */
object Recommender {

    data class RecommendationQuery(val query: String, val reason: String)

    private val stopWords = setOf(
        "official", "video", "audio", "song", "songs", "full", "hd", "4k", "8k", "uhd",
        "lyrics", "lyric", "music", "mv", "new", "latest", "2024", "2025", "2026", "live",
        "feat", "ft", "vs", "the", "and", "for", "with", "from", "this", "that", "your",
        "you", "are", "how", "what", "why", "best", "top", "status", "shorts", "reel",
        "reels", "whatsapp", "episode", "ep", "part", "trailer", "teaser", "clip", "clips",
    )

    /**
     * Builds a personalized YouTube search query from recent downloads.
     * [seed] rotates across the user's top interests when they pull-to-refresh.
     */
    fun buildQuery(tasks: List<DownloadTask>, seed: Int = 0): RecommendationQuery? {
        if (tasks.isEmpty()) return null

        val recent = tasks.take(25)
        val candidates = mutableListOf<RecommendationQuery>()

        // 1. Top channels from recent downloads (weighted by frequency + recency)
        val channelScores = LinkedHashMap<String, Double>()
        recent.forEachIndexed { idx, t ->
            val ch = t.channel.trim()
            if (ch.length >= 2) {
                val weight = 1.0 / (1.0 + idx * 0.15)
                channelScores[ch] = (channelScores[ch] ?: 0.0) + weight
            }
        }
        val topChannels = channelScores.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(4)

        val prefersAudio = recent.count { it.kind == MediaKind.AUDIO } > recent.size / 2
        val suffix = if (prefersAudio) "song" else ""

        topChannels.forEach { ch ->
            val q = listOf(ch, suffix).filter { it.isNotEmpty() }.joinToString(" ")
            candidates += RecommendationQuery(query = q, reason = ch)
        }

        // 2. Top recurring keywords from downloaded titles
        val wordScores = LinkedHashMap<String, Double>()
        recent.forEachIndexed { idx, t ->
            val weight = 1.0 / (1.0 + idx * 0.12)
            t.title
                .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
                .split(Regex("""\s+"""))
                .map { it.trim() }
                .filter { it.length >= 3 && it.lowercase() !in stopWords }
                .distinctBy { it.lowercase() }
                .forEach { w ->
                    val key = w.lowercase()
                    wordScores[key] = (wordScores[key] ?: 0.0) + weight
                }
        }
        val topWords = wordScores.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(4)

        if (topWords.size >= 2) {
            val pair = "${topWords[0]} ${topWords[1]}"
            candidates += RecommendationQuery(
                query = if (prefersAudio) "$pair song" else pair,
                reason = topWords.take(2).joinToString(" & ") { it.replaceFirstChar { c -> c.uppercase() } },
            )
        }
        topWords.firstOrNull()?.let { w ->
            candidates += RecommendationQuery(
                query = if (prefersAudio) "$w music" else "$w new video",
                reason = w.replaceFirstChar { it.uppercase() },
            )
        }

        if (candidates.isEmpty()) return null
        val pick = candidates[seed.mod(candidates.size)]
        return pick
    }

    /** Filters out videos the user has already downloaded so the feed stays fresh. */
    fun filterFresh(items: List<SearchItem>, tasks: List<DownloadTask>): List<SearchItem> {
        if (tasks.isEmpty()) return items
        val downloadedUrls = tasks.map { it.url }.toSet()
        val downloadedTitles = tasks.map { it.title.lowercase().trim() }.toSet()
        val fresh = items.filterNot { it.url in downloadedUrls || it.title.lowercase().trim() in downloadedTitles }
        return fresh.ifEmpty { items }
    }
}
