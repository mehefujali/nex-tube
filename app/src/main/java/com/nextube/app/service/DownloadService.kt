package com.nextube.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.nextube.app.R
import com.nextube.app.core.Watermarker
import com.nextube.app.core.YtDlp
import com.nextube.app.data.AppSettings
import com.nextube.app.data.DownloadRepository
import com.nextube.app.data.DownloadStatus
import com.nextube.app.data.MediaKind
import com.nextube.app.data.MediaSaver
import com.nextube.app.ui.MainActivity
import com.nextube.app.util.Fmt
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Foreground service that runs the download queue so downloads survive leaving the app. */
class DownloadService : Service() {

    companion object {
        const val CHANNEL_PROGRESS = "downloads"
        const val CHANNEL_DONE = "downloads_done"
        private const val ONGOING_ID = 1
        private const val ACTION_ENQUEUE = "enqueue"
        private const val ACTION_CANCEL = "cancel"
        private const val EXTRA_ID = "id"

        fun enqueue(context: Context, taskId: String) = send(context, ACTION_ENQUEUE, taskId)
        fun cancel(context: Context, taskId: String) = send(context, ACTION_CANCEL, taskId)

        private fun send(context: Context, action: String, id: String) {
            val i = Intent(context, DownloadService::class.java).setAction(action).putExtra(EXTRA_ID, id)
            ContextCompat.startForegroundService(context, i)
        }

        fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_PROGRESS, "Download progress", NotificationManager.IMPORTANCE_LOW)
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_DONE, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val limiter = Semaphore(AppSettings.maxConcurrent.value)
    @Volatile private var lastNotify = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        val id = intent?.getStringExtra(EXTRA_ID)
        when (intent?.action) {
            ACTION_ENQUEUE -> if (id != null) start(id)
            ACTION_CANCEL -> if (id != null) cancelTask(id)
        }
        stopIfIdle()
        return START_NOT_STICKY
    }

    private fun startInForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, ONGOING_ID, buildOngoing(), type)
    }

    private fun start(id: String) {
        if (jobs.containsKey(id)) return
        jobs[id] = scope.launch {
            try {
                limiter.withPermit { run(id) }
            } finally {
                jobs.remove(id)
                refreshOngoing(force = true)
                stopIfIdle()
            }
        }
    }

    private suspend fun run(id: String) {
        val task = DownloadRepository.get(id) ?: return
        if (task.status == DownloadStatus.CANCELLED) return
        val work = File(cacheDir, "dl/$id").apply { deleteRecursively(); mkdirs() }
        DownloadRepository.update(id) { it.copy(status = DownloadStatus.DOWNLOADING, progress = 0f, error = null) }
        refreshOngoing(force = true)
        try {
            // Video downloads come as two streams (video, then audio); combine them into one bar.
            val streams = LinkedHashMap<String, YtDlp.Progress>()
            // MP3 estimates describe the converted file, not the stream being downloaded, so skip them.
            val estimate = if (task.kind == MediaKind.VIDEO) task.sizeBytes else 0L
            val file = YtDlp.download(task, work) { line ->
                val p = YtDlp.parseProgress(line)
                if (p != null) {
                    streams[p.formatId] = p
                    val done = streams.values.sumOf { it.downloaded }
                    val total = maxOf(streams.values.sumOf { it.total }, estimate)
                    val fraction = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
                    DownloadRepository.update(id, persist = false) {
                        if (it.status == DownloadStatus.CANCELLED) it else it.copy(
                            status = DownloadStatus.DOWNLOADING,
                            progress = maxOf(fraction, it.progress), // never jump backwards
                            etaSec = p.eta,
                            speed = if (p.speed > 0) "${Fmt.bytes(p.speed.toLong())}/s" else it.speed,
                        )
                    }
                    refreshOngoing()
                } else if (line.startsWith("[Merger]") || line.startsWith("[ExtractAudio]") ||
                    line.startsWith("[EmbedThumbnail]") || line.startsWith("[FixupM")
                ) {
                    DownloadRepository.update(id, persist = false) {
                        if (it.status == DownloadStatus.CANCELLED) it
                        else it.copy(status = DownloadStatus.PROCESSING, speed = "", etaSec = -1)
                    }
                    refreshOngoing(force = true)
                }
            }
            DownloadRepository.update(id) { it.copy(status = DownloadStatus.PROCESSING, progress = 1f) }
            refreshOngoing(force = true)
            val finalFile = if (task.kind == MediaKind.VIDEO && AppSettings.watermark.value) watermark(id, file) else file
            DownloadRepository.update(id, persist = false) { it.copy(note = "Saving") }
            val saved = MediaSaver.save(applicationContext, finalFile)
            DownloadRepository.update(id) {
                it.copy(
                    status = DownloadStatus.COMPLETED, progress = 1f, speed = "", etaSec = -1,
                    fileUri = saved.uri.toString(), fileName = saved.displayName,
                    mimeType = saved.mime, sizeBytes = saved.size,
                )
            }
            notifyDone(id, saved.displayName, saved.uri, saved.mime)
        } catch (e: YoutubeDL.CanceledException) {
            DownloadRepository.update(id) { it.copy(status = DownloadStatus.CANCELLED, speed = "", etaSec = -1) }
        } catch (t: Throwable) {
            val current = DownloadRepository.get(id)
            if (current?.status != DownloadStatus.CANCELLED) {
                val msg = YtDlp.friendlyError(t)
                DownloadRepository.update(id) { it.copy(status = DownloadStatus.FAILED, error = msg, speed = "", etaSec = -1) }
                notifyFailed(id, task.title, msg)
            }
        } finally {
            work.deleteRecursively()
        }
    }

    /** Re-encodes with the logo. If anything goes wrong the user still gets the original video. */
    private suspend fun watermark(id: String, file: File): File {
        val out = File(file.parentFile, file.nameWithoutExtension + ".wm.mp4")
        DownloadRepository.update(id, persist = false) { it.copy(note = "Adding watermark") }
        refreshOngoing(force = true)
        return try {
            Watermarker.apply(applicationContext, file, out, AppSettings.watermarkPosition.value) { pct ->
                DownloadRepository.update(id, persist = false) { it.copy(note = "Adding watermark $pct%") }
                refreshOngoing()
            }
            // Keep the original file name.
            File(file.parentFile, file.nameWithoutExtension + ".mp4").also { dest ->
                file.delete()
                out.renameTo(dest)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("NexTube", "watermark failed, saving original", e)
            out.delete()
            file
        }
    }

    private fun cancelTask(id: String) {
        DownloadRepository.update(id) { it.copy(status = DownloadStatus.CANCELLED, speed = "", etaSec = -1) }
        YtDlp.cancel(id)
        // Stops queued tasks waiting on the semaphore and an in-progress watermark step.
        jobs[id]?.cancel()
    }

    private fun stopIfIdle() {
        if (jobs.isEmpty()) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // ---- notifications ----

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_DOWNLOADS, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildOngoing(): Notification {
        val active = DownloadRepository.tasks.value.filter { it.status.isActive }
        val current = active.firstOrNull { it.status != DownloadStatus.QUEUED } ?: active.firstOrNull()
        val b = NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (current == null) {
            return b.setContentTitle("NexTube").setContentText("Preparing download…").setProgress(0, 0, true).build()
        }
        val pct = (current.progress * 100).toInt()
        val sub = when (current.status) {
            DownloadStatus.PROCESSING -> current.note.ifEmpty { "Processing…" }
            DownloadStatus.QUEUED -> "Waiting…"
            else -> listOfNotNull("$pct%", current.speed.ifEmpty { null }).joinToString(" · ")
        }
        val more = if (active.size > 1) " (+${active.size - 1} more)" else ""
        val cancel = PendingIntent.getService(
            this, current.id.hashCode(),
            Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_ID, current.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return b.setContentTitle(current.title + more)
            .setContentText(sub)
            .setProgress(100, pct, current.status != DownloadStatus.DOWNLOADING || pct == 0)
            .addAction(0, "Cancel", cancel)
            .build()
    }

    private fun refreshOngoing(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotify < 700) return
        lastNotify = now
        if (jobs.isEmpty()) return
        notifySafe(ONGOING_ID, buildOngoing())
    }

    private fun notifyDone(id: String, name: String, uri: Uri, mime: String) {
        val open = PendingIntent.getActivity(
            this, id.hashCode(),
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Download complete")
            .setContentText(name)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        notifySafe(id.hashCode(), n)
    }

    private fun notifyFailed(id: String, title: String, msg: String) {
        val n = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Download failed: $title")
            .setContentText(msg)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        notifySafe(id.hashCode(), n)
    }

    /** Posts only when the user allowed notifications (required on Android 13+). */
    private fun notifySafe(notificationId: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        try {
            NotificationManagerCompat.from(this).notify(notificationId, notification)
        } catch (_: SecurityException) {
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
