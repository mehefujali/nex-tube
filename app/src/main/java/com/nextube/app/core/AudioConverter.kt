package com.nextube.app.core

import android.content.Context
import androidx.core.net.toUri
import com.nextube.app.data.DownloadTask
import com.nextube.app.data.MediaSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Offline Video → MP3 converter using the bundled FFmpeg binary (no internet required). */
object AudioConverter {

    suspend fun videoToMp3(context: Context, task: DownloadTask): MediaSaver.Saved = withContext(Dispatchers.IO) {
        val uriStr = task.fileUri ?: error("Video file not found")
        val ffmpegBin = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        check(ffmpegBin.exists()) { "FFmpeg binary not ready" }

        val work = File(context.cacheDir, "conv_${task.id}").apply { deleteRecursively(); mkdirs() }
        try {
            val inFile = File(work, "input.mp4")
            context.contentResolver.openInputStream(uriStr.toUri())?.use { input ->
                inFile.outputStream().use { out -> input.copyTo(out, 1 shl 16) }
            } ?: error("Could not read video file")

            val baseName = (task.fileName?.substringBeforeLast('.') ?: task.title)
                .replace(Regex("""[\\/:*?"<>|]"""), "_")
                .take(120)
                .ifBlank { "audio_${System.currentTimeMillis()}" }
            val outFile = File(work, "$baseName.mp3")

            val ffmpegLib = File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/lib")
            val pythonLib = File(context.noBackupFilesDir, "youtubedl-android/packages/python/usr/lib")
            val pb = ProcessBuilder(
                ffmpegBin.absolutePath,
                "-y",
                "-i", inFile.absolutePath,
                "-vn",
                "-codec:a", "libmp3lame",
                "-b:a", "192k",
                outFile.absolutePath,
            ).redirectErrorStream(true)
            pb.environment()["LD_LIBRARY_PATH"] = "${ffmpegLib.absolutePath}:${pythonLib.absolutePath}"

            val proc = pb.start()
            val output = proc.inputStream.bufferedReader().use { it.readText() }
            val exit = proc.waitFor()
            check(exit == 0 && outFile.exists() && outFile.length() > 0) {
                "Conversion failed (code $exit): ${output.lines().lastOrNull { it.isNotBlank() }.orEmpty()}"
            }

            MediaSaver.save(context, outFile)
        } finally {
            work.deleteRecursively()
        }
    }

    /**
     * Trims a local downloaded video or audio file cleanly with bundled FFmpeg.
     * Re-encodes video with libx264 ultrafast + yuv420p so trimmed clips start on a clean keyframe (no black screen).
     */
    fun trimLocalFile(
        context: Context,
        src: File,
        startSec: Long,
        endSec: Long,
        isVideo: Boolean,
    ): File {
        val durationSec = (endSec - startSec).coerceAtLeast(1L)
        val ffmpegBin = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        check(ffmpegBin.exists()) { "FFmpeg binary not ready" }

        val ext = if (isVideo) "mp4" else src.extension.ifBlank { "mp3" }
        val trimmed = File(src.parentFile, "${src.nameWithoutExtension}.trim.$ext")
        val cmd = if (isVideo) {
            listOf(
                ffmpegBin.absolutePath,
                "-y",
                "-ss", startSec.toString(),
                "-i", src.absolutePath,
                "-t", durationSec.toString(),
                "-c:v", "libx264",
                "-preset", "ultrafast",
                "-crf", "23",
                "-pix_fmt", "yuv420p",
                "-c:a", "aac",
                "-b:a", "128k",
                "-movflags", "+faststart",
                trimmed.absolutePath,
            )
        } else {
            listOf(
                ffmpegBin.absolutePath,
                "-y",
                "-ss", startSec.toString(),
                "-i", src.absolutePath,
                "-t", durationSec.toString(),
                "-c", "copy",
                trimmed.absolutePath,
            )
        }

        val ffmpegLib = File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/lib")
        val pythonLib = File(context.noBackupFilesDir, "youtubedl-android/packages/python/usr/lib")
        val pb = ProcessBuilder(cmd).redirectErrorStream(true)
        pb.environment()["LD_LIBRARY_PATH"] = "${ffmpegLib.absolutePath}:${pythonLib.absolutePath}"

        val proc = pb.start()
        val output = proc.inputStream.bufferedReader().use { it.readText() }
        val exit = proc.waitFor()
        check(exit == 0 && trimmed.exists() && trimmed.length() > 0) {
            "Trim failed (code $exit): ${output.lines().lastOrNull { it.isNotBlank() }.orEmpty()}"
        }

        val finalDest = File(src.parentFile, "${src.nameWithoutExtension}.$ext")
        src.delete()
        if (!trimmed.renameTo(finalDest)) {
            trimmed.copyTo(finalDest, overwrite = true)
            trimmed.delete()
        }
        return finalDest
    }
}
