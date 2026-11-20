package com.nextube.app.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.effect.TextureOverlay
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import com.nextube.app.R
import com.nextube.app.data.WatermarkPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Burns the NexTube logo into a video using Media3 Transformer, which re-encodes on the
 * phone's hardware encoder (much faster and cooler than ffmpeg on the CPU).
 */
@OptIn(UnstableApi::class)
object Watermarker {

    /** Logo height as a fraction of the video's short side. */
    private const val LOGO_SCALE = 0.075f

    suspend fun apply(
        context: Context,
        input: File,
        output: File,
        position: WatermarkPosition,
        onProgress: (Int) -> Unit,
    ) {
        val shortSide = videoShortSide(input)
        val logo = BitmapFactory.decodeResource(context.resources, R.drawable.watermark)
        val h = max(24, (shortSide * LOGO_SCALE).roundToInt())
        val scaled = Bitmap.createScaledBitmap(logo, logo.width * h / logo.height, h, true)

        // Anchors are in normalised coordinates: -1 = left/bottom edge, 1 = right/top edge.
        val (frameX, logoX) = when (position) {
            WatermarkPosition.BOTTOM_LEFT -> -0.95f to -1f
            WatermarkPosition.BOTTOM_CENTER -> 0f to 0f
            WatermarkPosition.BOTTOM_RIGHT -> 0.95f to 1f
        }
        val settings = StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(frameX, -0.92f)
            .setOverlayFrameAnchor(logoX, -1f)
            .setAlphaScale(0.9f)
            .build()
        val overlay = BitmapOverlay.createStaticBitmapOverlay(scaled, settings)
        val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(input)))
            .setEffects(
                Effects(
                    ImmutableList.of(),
                    ImmutableList.of<Effect>(OverlayEffect(ImmutableList.of<TextureOverlay>(overlay))),
                )
            )
            .build()

        // Transformer must be driven from a thread with a Looper.
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val handler = Handler(Looper.getMainLooper())
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resume(Unit)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException,
                        ) {
                            if (cont.isActive) cont.resumeWithException(exportException)
                        }
                    })
                    .build()
                transformer.start(item, output.absolutePath)

                val holder = ProgressHolder()
                val poll = object : Runnable {
                    override fun run() {
                        if (!cont.isActive) return
                        if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(holder.progress)
                        }
                        handler.postDelayed(this, 500)
                    }
                }
                handler.post(poll)
                cont.invokeOnCancellation { handler.post { transformer.cancel() } }
            }
        }
    }

    private fun videoShortSide(file: File): Int {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 720
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
            min(w, h)
        } catch (_: Exception) {
            720
        } finally {
            r.release()
        }
    }
}
