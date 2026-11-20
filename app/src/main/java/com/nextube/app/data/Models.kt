package com.nextube.app.data

import org.json.JSONArray
import org.json.JSONObject

data class SearchItem(
    val id: String,
    val title: String,
    val channel: String,
    val durationSec: Long,
    val views: Long,
    val url: String,
) {
    val thumbnail: String get() = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
}

enum class MediaKind { VIDEO, AUDIO }

/** One choice in the quality picker, e.g. "1080p MP4" or "MP3 320 kbps". */
data class DownloadOption(
    val kind: MediaKind,
    val label: String,
    val detail: String,
    val sizeBytes: Long,
    val badge: String?,
    val args: List<String>,
)

data class VideoDetails(
    val url: String,
    val id: String,
    val title: String,
    val channel: String,
    val durationSec: Long,
    val thumbnail: String,
    val views: Long,
    val videoOptions: List<DownloadOption>,
    val audioOptions: List<DownloadOption>,
)

enum class DownloadStatus { QUEUED, DOWNLOADING, PROCESSING, COMPLETED, FAILED, CANCELLED;
    val isActive get() = this == QUEUED || this == DOWNLOADING || this == PROCESSING
}

data class DownloadTask(
    val id: String,
    val url: String,
    val title: String,
    val channel: String,
    val thumbnail: String,
    val kind: MediaKind,
    val optionLabel: String,
    val args: List<String>,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val speed: String = "",
    val etaSec: Long = -1,
    val error: String? = null,
    val fileUri: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** Short live status such as "Adding watermark 40%"; not persisted. */
    val note: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("url", url).put("title", title).put("channel", channel)
        .put("thumbnail", thumbnail).put("kind", kind.name).put("optionLabel", optionLabel)
        .put("args", JSONArray(args)).put("status", status.name).put("progress", progress.toDouble())
        .put("error", error).put("fileUri", fileUri).put("fileName", fileName)
        .put("mimeType", mimeType).put("sizeBytes", sizeBytes).put("createdAt", createdAt)

    companion object {
        fun fromJson(o: JSONObject): DownloadTask {
            val argsJson = o.optJSONArray("args") ?: JSONArray()
            return DownloadTask(
                id = o.getString("id"),
                url = o.getString("url"),
                title = o.optString("title"),
                channel = o.optString("channel"),
                thumbnail = o.optString("thumbnail"),
                kind = runCatching { MediaKind.valueOf(o.optString("kind")) }.getOrDefault(MediaKind.VIDEO),
                optionLabel = o.optString("optionLabel"),
                args = List(argsJson.length()) { argsJson.getString(it) },
                status = runCatching { DownloadStatus.valueOf(o.optString("status")) }.getOrDefault(DownloadStatus.FAILED),
                progress = o.optDouble("progress", 0.0).toFloat(),
                error = o.optString("error").takeIf { it.isNotEmpty() && it != "null" },
                fileUri = o.optString("fileUri").takeIf { it.isNotEmpty() && it != "null" },
                fileName = o.optString("fileName").takeIf { it.isNotEmpty() && it != "null" },
                mimeType = o.optString("mimeType").takeIf { it.isNotEmpty() && it != "null" },
                sizeBytes = o.optLong("sizeBytes"),
                createdAt = o.optLong("createdAt"),
            )
        }
    }
}
