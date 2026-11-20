package com.nextube.app.core

import android.content.Context
import com.nextube.app.data.DownloadOption
import com.nextube.app.data.DownloadTask
import com.nextube.app.data.MediaKind
import com.nextube.app.data.PlaylistDetails
import com.nextube.app.data.PreviewStream
import com.nextube.app.data.SearchItem
import com.nextube.app.data.VideoDetails
import com.nextube.app.util.Fmt
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoFormat
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

sealed interface EngineState {
    data object Initializing : EngineState
    data object Ready : EngineState
    data class Failed(val message: String) : EngineState
}

/** Coroutine-friendly wrapper around the bundled yt-dlp + ffmpeg. Everything runs on the phone, no server. */
object YtDlp {
    private val _state = MutableStateFlow<EngineState>(EngineState.Initializing)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    /** Prevents yt-dlp self-update from colliding with an in-flight search or video info lookup. */
    private val updateMutex = Mutex()

    private val ytdl get() = YoutubeDL.getInstance()

    /**
     * yt-dlp's cache keeps YouTube's player code and solved challenges between runs. Without it
     * (the library passes --no-cache-dir by default) every lookup re-downloads and re-solves them.
     */
    private var cacheDir: File? = null

    private fun YoutubeDLRequest.withCache(): YoutubeDLRequest = apply {
        cacheDir?.let { addOption("--cache-dir", it.absolutePath) }
    }

    /** Recently fetched details, so re-opening a video is instant. */
    private val detailsCache = object : LinkedHashMap<String, Pair<Long, VideoDetails>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, VideoDetails>>?) = size > 40
    }
    private const val DETAILS_TTL = 20 * 60 * 1000L

    /**
     * YouTube's default web clients need a JavaScript challenge solved, which QuickJS takes ~20 s
     * to do on a phone. This client needs no JS and returns the same qualities in ~6 s. If YouTube
     * ever breaks it, we fall back to yt-dlp's defaults automatically.
     */
    private const val FAST_CLIENT = "youtube:player_client=visionos"

    private fun isYouTube(url: String) = Regex("""(^|\.|//)(youtube\.com|youtu\.be)""").containsMatchIn(url)

    /** Machine-readable progress line we ask yt-dlp to print (see [download]). */
    const val PROGRESS_PREFIX = "NXP|"

    /** Unpacks Python/yt-dlp/ffmpeg on first launch (a few seconds), instant afterwards. Call off the main thread. */
    fun init(context: Context) {
        cacheDir = File(context.noBackupFilesDir, "yt-dlp-cache").apply { mkdirs() }
        try {
            ytdl.init(context)
            FFmpeg.getInstance().init(context)
            _state.value = EngineState.Ready
        } catch (t: Throwable) {
            android.util.Log.e("NexTube", "Engine init failed", t)
            _state.value = EngineState.Failed(t.message ?: t.cause?.message ?: t.javaClass.simpleName)
        }
    }

    private suspend fun awaitReady() {
        val s = state.first { it !is EngineState.Initializing }
        if (s is EngineState.Failed) throw IllegalStateException(s.message)
    }

    suspend fun search(query: String, limit: Int = 25): List<SearchItem> = withContext(Dispatchers.IO) {
        awaitReady()
        val req = YoutubeDLRequest("ytsearch$limit:$query")
            .addOption("--flat-playlist")
            .addOption("-J")
            .addOption("--no-warnings")
            .withCache()
        val out = ytdl.execute(req, null).out
        val entries = JSONObject(out).optJSONArray("entries") ?: return@withContext emptyList()
        (0 until entries.length()).mapNotNull { i ->
            val e = entries.optJSONObject(i) ?: return@mapNotNull null
            val id = e.optString("id")
            if (id.isEmpty()) return@mapNotNull null
            // Skip channels/playlists that sometimes appear in search results.
            val ieKey = e.optString("ie_key")
            if (ieKey.isNotEmpty() && ieKey != "Youtube") return@mapNotNull null
            val dur = e.optDouble("duration", 0.0)
            SearchItem(
                id = id,
                title = e.optString("title"),
                channel = e.optString("channel").ifEmpty { e.optString("uploader") },
                durationSec = if (dur.isNaN()) 0L else dur.toLong(),
                views = e.optLong("view_count", 0L),
                url = e.optString("url").ifEmpty { "https://www.youtube.com/watch?v=$id" },
            )
        }
    }

    suspend fun details(url: String): VideoDetails = withContext(Dispatchers.IO) {
        synchronized(detailsCache) { detailsCache[url] }?.let { (at, d) ->
            if (System.currentTimeMillis() - at < DETAILS_TTL) return@withContext d
        }
        awaitReady()
        // Wait if a background engine update is currently replacing the yt-dlp binary.
        if (updateMutex.isLocked) updateMutex.withLock { }
        val started = System.currentTimeMillis()
        fun fetch(fast: Boolean, useCache: Boolean = true) = ytdl.getInfo(
            YoutubeDLRequest(url).addOption("--no-playlist").addOption("--no-warnings").apply {
                if (useCache) withCache() else addOption("--no-cache-dir")
                if (fast) addOption("--extractor-args", FAST_CLIENT)
            }
        )
        suspend fun resolveInfo(): VideoInfo {
            if (isYouTube(url)) {
                val fastInfo = runCatching { fetch(fast = true, useCache = true) }
                    .getOrNull()
                    ?.takeIf { i -> i.formats.orEmpty().any { it.hasVideo() } }
                if (fastInfo != null) return fastInfo
            }
            return try {
                fetch(fast = false, useCache = true)
            } catch (e: CancellationException) {
                throw e
            } catch (firstErr: Throwable) {
                // Retry once without cache in case a cold socket or stale cache entry caused a 1st-click error.
                android.util.Log.w("NexTube", "details 1st attempt failed, retrying without cache", firstErr)
                delay(300)
                fetch(fast = false, useCache = false)
            }
        }
        val info = resolveInfo()
        android.util.Log.i("NexTube", "details fetched in ${System.currentTimeMillis() - started} ms")
        val duration = info.duration.toLong()
        VideoDetails(
            url = info.webpageUrl ?: url,
            id = info.id ?: "",
            title = info.title ?: info.fulltitle ?: "Untitled",
            channel = info.uploader ?: "",
            durationSec = duration,
            thumbnail = info.thumbnail ?: "",
            views = info.viewCount?.toLongOrNull() ?: 0,
            videoOptions = videoOptions(info),
            audioOptions = audioOptions(info, duration),
            preview = previewStream(info),
            playlistUrl = Fmt.extractPlaylistUrl(url),
        ).also { d -> synchronized(detailsCache) { detailsCache[url] = System.currentTimeMillis() to d } }
    }

    /** Fetches playlist metadata and video list quickly via --flat-playlist. */
    suspend fun playlist(url: String): PlaylistDetails = withContext(Dispatchers.IO) {
        awaitReady()
        val req = YoutubeDLRequest(url)
            .addOption("--flat-playlist")
            .addOption("-J")
            .addOption("--no-warnings")
            .withCache()
        val out = ytdl.execute(req, null).out
        val root = JSONObject(out)
        val entries = root.optJSONArray("entries")
        val items = if (entries != null) {
            (0 until entries.length()).mapNotNull { i ->
                val e = entries.optJSONObject(i) ?: return@mapNotNull null
                val id = e.optString("id")
                val title = e.optString("title")
                if (id.isEmpty() || title == "[Private video]" || title == "[Deleted video]") return@mapNotNull null
                val dur = e.optDouble("duration", 0.0)
                SearchItem(
                    id = id,
                    title = title.ifEmpty { "Untitled" },
                    channel = e.optString("channel").ifEmpty { e.optString("uploader") },
                    durationSec = if (dur.isNaN()) 0L else dur.toLong(),
                    views = e.optLong("view_count", 0L),
                    url = e.optString("url").takeIf { it.startsWith("http") }
                        ?: "https://www.youtube.com/watch?v=$id",
                )
            }
        } else emptyList()
        PlaylistDetails(
            url = url,
            title = root.optString("title").ifEmpty { "Playlist" },
            channel = root.optString("channel").ifEmpty { root.optString("uploader") },
            items = items,
        )
    }

    /** Preset options used when queueing an entire playlist (no per-video info fetch needed). */
    fun playlistVideoPresets(): List<DownloadOption> = listOf(1080, 720, 480, 360).map { h ->
        DownloadOption(
            kind = MediaKind.VIDEO,
            label = "${h}p",
            detail = "MP4",
            sizeBytes = 0L,
            badge = when {
                h >= 1080 -> "FHD"; h >= 720 -> "HD"; else -> null
            },
            args = listOf("-f", "bv*+ba/b", "-S", "res:$h,ext:mp4:m4a", "--merge-output-format", "mp4"),
        )
    }

    fun playlistAudioPresets(): List<DownloadOption> = listOf(
        DownloadOption(
            MediaKind.AUDIO, "MP3 · 320 kbps", "MP3", 0L, "HQ",
            listOf("-f", "ba/b", "-x", "--audio-format", "mp3", "--audio-quality", "320K", "--embed-thumbnail"),
        ),
        DownloadOption(
            MediaKind.AUDIO, "MP3 · 128 kbps", "MP3", 0L, null,
            listOf("-f", "ba/b", "-x", "--audio-format", "mp3", "--audio-quality", "128K", "--embed-thumbnail"),
        ),
        DownloadOption(
            MediaKind.AUDIO, "M4A · Original", "M4A", 0L, "Fast",
            listOf("-f", "ba[ext=m4a]/ba/b", "-x", "--audio-format", "m4a"),
        ),
    )

    /** Picks a fast, ExoPlayer-friendly stream (prefers <=720p muxed, falls back to merged video+audio). */
    private fun previewStream(info: VideoInfo): PreviewStream? {
        val formats = info.formats.orEmpty().filter { !it.url.isNullOrBlank() }
        // 1. Prefer a muxed stream (video + audio together, <= 720p for fast start).
        val muxed = formats.filter { it.hasVideo() && it.hasAudio() }
            .sortedWith(
                compareByDescending<VideoFormat> { it.height in 360..720 }
                    .thenByDescending { it.ext == "mp4" }
                    .thenBy { kotlin.math.abs(it.height - 480) }
            )
            .firstOrNull()
        if (muxed != null) {
            return PreviewStream(
                videoUrl = muxed.url!!,
                audioUrl = null,
                headers = muxed.httpHeaders.orEmpty(),
            )
        }
        // 2. Otherwise pair a <=720p video stream with the best audio stream (ExoPlayer MergingMediaSource).
        val video = formats.filter { it.hasVideo() }
            .sortedWith(
                compareByDescending<VideoFormat> { it.height in 360..720 }
                    .thenByDescending { it.ext == "mp4" }
                    .thenBy { kotlin.math.abs(it.height - 480) }
            )
            .firstOrNull() ?: return null
        val audio = bestAudio(info)?.takeIf { !it.url.isNullOrBlank() }
        return PreviewStream(
            videoUrl = video.url!!,
            audioUrl = audio?.url,
            headers = video.httpHeaders.orEmpty(),
        )
    }

    private val STANDARD = listOf(4320, 2160, 1440, 1080, 720, 480, 360, 240, 144)

    private fun VideoFormat.size(): Long = if (fileSize > 0) fileSize else fileSizeApproximate
    private fun VideoFormat.hasVideo() = height > 0 && vcodec != "none"
    private fun VideoFormat.hasAudio() = acodec != null && acodec != "none"

    private fun bestAudio(info: VideoInfo): VideoFormat? =
        info.formats.orEmpty().filter { it.hasAudio() && !it.hasVideo() }
            .sortedWith(
                compareByDescending<VideoFormat> { it.ext == "m4a" }
                    .thenByDescending { it.abr }
                    .thenByDescending { it.size() }
            )
            .firstOrNull()

    private fun videoOptions(info: VideoInfo): List<DownloadOption> {
        val formats = info.formats.orEmpty().filter { it.hasVideo() }
        if (formats.isEmpty()) {
            // Non-YouTube sites often expose a single muxed stream.
            val size = if (info.fileSize > 0) info.fileSize else info.fileSizeApproximate
            return listOf(
                DownloadOption(MediaKind.VIDEO, "Best", info.ext?.uppercase() ?: "Video", size, null, listOf("-f", "bv*+ba/b"))
            )
        }
        val audioSize = bestAudio(info)?.size() ?: 0L
        return formats
            .groupBy { f ->
                val res = if (f.width > 0) minOf(f.width, f.height) else f.height // portrait/Shorts safe
                STANDARD.firstOrNull { res >= it * 0.92 } ?: res
            }
            .toSortedMap(compareByDescending { it })
            .map { (h, group) ->
                val best = group.sortedWith(
                    compareByDescending<VideoFormat> { it.ext == "mp4" }.thenByDescending { it.size() }
                ).first()
                val size = best.size().let { if (it > 0 && !best.hasAudio()) it + audioSize else it }
                val fps = group.maxOf { it.fps }
                DownloadOption(
                    kind = MediaKind.VIDEO,
                    label = "${h}p" + if (fps > 30) fps.toString() else "",
                    detail = "MP4",
                    sizeBytes = size,
                    badge = when {
                        h >= 4320 -> "8K"; h >= 2160 -> "4K"; h >= 1440 -> "2K"
                        h >= 1080 -> "FHD"; h >= 720 -> "HD"; else -> null
                    },
                    args = listOf("-f", "bv*+ba/b", "-S", "res:$h,ext:mp4:m4a", "--merge-output-format", "mp4"),
                )
            }
    }

    private fun audioOptions(info: VideoInfo, duration: Long): List<DownloadOption> {
        val m4a = bestAudio(info)
        fun mp3(kbps: Int, badge: String?) = DownloadOption(
            MediaKind.AUDIO, "MP3 · $kbps kbps", "MP3", duration * kbps * 125, badge,
            listOf("-f", "ba/b", "-x", "--audio-format", "mp3", "--audio-quality", "${kbps}K", "--embed-thumbnail"),
        )
        return listOf(
            mp3(320, "HQ"),
            mp3(128, null),
            DownloadOption(
                MediaKind.AUDIO, "M4A · Original", "M4A", m4a?.size() ?: (duration * 16_000), "Fast",
                listOf("-f", "ba[ext=m4a]/ba/b", "-x", "--audio-format", "m4a"),
            ),
        )
    }

    /** One parsed progress tick for a single stream (video or audio part). */
    data class Progress(val formatId: String, val downloaded: Long, val total: Long, val speed: Double, val eta: Long)

    fun parseProgress(line: String): Progress? {
        if (!line.startsWith(PROGRESS_PREFIX)) return null
        val p = line.removePrefix(PROGRESS_PREFIX).split('|')
        if (p.size < 6) return null
        fun num(i: Int) = p[i].trim().toDoubleOrNull()
        val total = num(1) ?: num(2) ?: 0.0
        return Progress(
            formatId = p[5].trim(),
            downloaded = (num(0) ?: 0.0).toLong(),
            total = total.toLong(),
            speed = num(3) ?: 0.0,
            eta = num(4)?.toLong() ?: -1,
        )
    }

    /**
     * Blocking; run on a background thread. Returns the finished file inside [outDir].
     * [onLine] receives every output line; progress lines start with [PROGRESS_PREFIX].
     */
    fun download(task: DownloadTask, outDir: File, onLine: (String) -> Unit): File {
        if (isYouTube(task.url)) {
            try {
                return runDownload(task, outDir, fast = true, onLine)
            } catch (e: YoutubeDL.CanceledException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("NexTube", "fast client failed, retrying with defaults", e)
            }
        }
        return runDownload(task, outDir, fast = false, onLine)
    }

    private fun runDownload(task: DownloadTask, outDir: File, fast: Boolean, onLine: (String) -> Unit): File {
        outDir.mkdirs()
        val req = YoutubeDLRequest(task.url)
            .addCommands(task.args)
            .addOption("--continue")
            .addOption("--no-playlist")
            .addOption("--no-mtime")
            .addOption("--embed-metadata")
            .addOption("-N", 4)
            .addOption("--newline")
            // The library's own progress regex doesn't match current yt-dlp output, so print our own format.
            .addOption(
                "--progress-template",
                "download:$PROGRESS_PREFIX%(progress.downloaded_bytes)s|%(progress.total_bytes)s|" +
                    "%(progress.total_bytes_estimate)s|%(progress.speed)s|%(progress.eta)s|%(info.format_id)s",
            )
            .addOption("-o", File(outDir, "%(title).150B.%(ext)s").absolutePath)
            .withCache()
        if (fast) req.addOption("--extractor-args", FAST_CLIENT)
        ytdl.execute(req, task.id) { _, _, line -> onLine(line) }
        val junk = setOf("part", "ytdl", "temp", "jpg", "jpeg", "webp", "png", "json")
        return outDir.listFiles().orEmpty()
            .filter { it.isFile && it.extension.lowercase() !in junk && !it.name.contains(".part") }
            .maxByOrNull { it.length() }
            ?: error("Download finished but no file was produced")
    }

    fun cancel(taskId: String) {
        runCatching { ytdl.destroyProcessById(taskId) }
    }

    /** YouTube changes often; updating yt-dlp fixes most failures. Returns a human message. */
    suspend fun update(context: Context): String = withContext(Dispatchers.IO) {
        awaitReady()
        updateMutex.withLock {
            when (ytdl.updateYoutubeDL(context, YoutubeDL.UpdateChannel._STABLE)) {
                YoutubeDL.UpdateStatus.DONE -> "Updated to ${version(context)}"
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "Already up to date"
                else -> "Update finished"
            }
        }
    }

    fun version(context: Context): String? = runCatching { ytdl.version(context) }.getOrNull()

    /** Turns yt-dlp's noisy stderr into one readable line. */
    fun friendlyError(t: Throwable): String {
        val raw = t.message.orEmpty()
        val line = raw.lines().lastOrNull { it.contains("ERROR:") }?.substringAfter("ERROR:")?.trim()
            ?: raw.lines().lastOrNull { it.isNotBlank() }?.trim()
            ?: "Something went wrong"
        return when {
            line.contains("Sign in to confirm", true) ->
                "YouTube blocked this request. Update the engine in Settings and try again."
            line.contains("Private video", true) -> "This video is private."
            line.contains("unavailable", true) -> "This video is unavailable."
            line.contains("Unable to download webpage", true) || line.contains("Failed to resolve", true) ->
                "No internet connection."
            else -> line.take(220)
        }
    }
}
