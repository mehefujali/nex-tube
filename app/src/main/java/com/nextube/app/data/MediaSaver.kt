package com.nextube.app.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/** Moves a finished file from app cache into the public Download/NexTube folder. */
object MediaSaver {
    const val FOLDER = "NexTube"

    data class Saved(val uri: Uri, val displayName: String, val mime: String, val size: Long)

    fun mimeOf(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: when (file.extension.lowercase()) {
                "mp3" -> "audio/mpeg"; "m4a" -> "audio/mp4"; "opus" -> "audio/ogg"
                "mp4" -> "video/mp4"; "webm" -> "video/webm"; "mkv" -> "video/x-matroska"
                else -> "application/octet-stream"
            }

    suspend fun save(context: Context, src: File): Saved {
        val mime = mimeOf(src)
        val size = src.length()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, src.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create file in Downloads")
            try {
                resolver.openOutputStream(uri)!!.use { out -> src.inputStream().use { it.copyTo(out, 1 shl 16) } }
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }
            Saved(uri, src.name, mime, size)
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
            dir.mkdirs()
            var dest = File(dir, src.name)
            var n = 1
            while (dest.exists()) dest = File(dir, "${src.nameWithoutExtension} ($n).${src.extension}").also { n++ }
            src.copyTo(dest)
            val uri = suspendCancellableCoroutine<Uri?> { cont ->
                MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf(mime)) { _, u -> cont.resume(u) }
            } ?: Uri.fromFile(dest)
            Saved(uri, dest.name, mime, size)
        }
    }

    fun delete(context: Context, uri: String) {
        runCatching { context.contentResolver.delete(Uri.parse(uri), null, null) }
    }
}
