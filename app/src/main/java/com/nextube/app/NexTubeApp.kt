package com.nextube.app

import android.app.Application
import com.nextube.app.core.CookieStore
import com.nextube.app.core.EngineState
import com.nextube.app.core.YtDlp
import com.nextube.app.data.AppSettings
import com.nextube.app.data.DownloadRepository
import com.nextube.app.service.DownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NexTubeApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        CookieStore.init(this)
        DownloadRepository.init(this)
        DownloadService.createChannels(this)

        appScope.launch {
            YtDlp.init(this@NexTubeApp)
            // YouTube changes frequently; keep yt-dlp fresh (at most once a day).
            val day = 24 * 60 * 60 * 1000L
            if (YtDlp.state.value == EngineState.Ready && AppSettings.autoUpdate.value &&
                System.currentTimeMillis() - AppSettings.lastEngineUpdate > day
            ) {
                runCatching { YtDlp.update(this@NexTubeApp) }
                    .onSuccess { AppSettings.lastEngineUpdate = System.currentTimeMillis() }
            }
        }
    }
}
