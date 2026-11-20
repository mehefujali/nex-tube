package com.nextube.app.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.nextube.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/** What `update.json` in the GitHub repo says about the newest release. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    /** Versions below this must update before they can be used. */
    val minVersionCode: Int,
    val changelog: String,
    val apkUrl: String,
) {
    val isNewer get() = versionCode > BuildConfig.VERSION_CODE
    val isMandatory get() = BuildConfig.VERSION_CODE < minVersionCode
}

/**
 * Serverless self-update: reads update.json from the repo, downloads the APK from
 * GitHub Releases and hands it to Android's installer (the user taps "Install" once).
 */
object Updater {

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        // Cache-buster so we don't get a stale copy from GitHub's CDN.
        val url = BuildConfig.UPDATE_URL + "?t=" + System.currentTimeMillis() / 60_000
        val json = JSONObject(httpGet(url))
        val apks = json.optJSONObject("apk") ?: JSONObject()
        // Pick the APK built for this phone's CPU, falling back to the universal one.
        val apkUrl = Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abi -> apks.optString(abi).ifEmpty { null } }
            ?: apks.optString("universal").ifEmpty { null }
            ?: return@withContext null
        UpdateInfo(
            versionCode = json.getInt("versionCode"),
            versionName = json.optString("versionName"),
            minVersionCode = json.optInt("minVersionCode", 0),
            changelog = json.optString("changelog"),
            apkUrl = apkUrl,
        ).takeIf { it.isNewer }
    }

    /** Downloads the APK to app cache, reporting 0..1 progress. Re-uses a finished earlier download. */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(dir, "nextube-${info.versionCode}.apk")
            if (target.exists() && isValidApk(context, target, info.versionCode)) return@withContext target
            dir.listFiles()?.forEach { it.delete() }

            val part = File(dir, target.name + ".part")
            val conn = open(info.apkUrl)
            try {
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastEmit = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            val now = System.currentTimeMillis()
                            if (total > 0 && (now - lastEmit >= 100 || done == total)) {
                                lastEmit = now
                                onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            check(part.renameTo(target)) { "Could not save update file" }
            if (!isValidApk(context, target, info.versionCode)) {
                target.delete()
                error("Downloaded file is not a valid NexTube update")
            }
            target
        }

    /** Android requires the user to allow "Install unknown apps" for NexTube once. */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Checks the file really is a NexTube APK of the expected version before offering to install it. */
    private fun isValidApk(context: Context, file: File, expectedVersion: Int): Boolean {
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_META_DATA)
            ?: return false
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toInt()
        else @Suppress("DEPRECATION") info.versionCode
        return info.packageName == context.packageName && code >= expectedVersion
    }

    private fun open(url: String): HttpURLConnection {
        var current = url
        // Follow redirects manually: GitHub release assets redirect to another host.
        repeat(5) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "NexTube/${BuildConfig.VERSION_NAME}")
            }
            when (conn.responseCode) {
                in 200..299 -> return conn
                in 300..399 -> {
                    val loc = conn.getHeaderField("Location")
                    conn.disconnect()
                    check(!loc.isNullOrBlank()) { "Redirect missing Location header" }
                    current = URL(URL(current), loc).toString()
                }
                else -> {
                    val code = conn.responseCode
                    conn.disconnect()
                    error("Update server returned HTTP $code")
                }
            }
        }
        error("Too many redirects")
    }

    private fun httpGet(url: String): String {
        val conn = open(url)
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
