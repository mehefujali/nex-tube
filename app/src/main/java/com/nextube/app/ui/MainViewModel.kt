package com.nextube.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nextube.app.core.EngineState
import com.nextube.app.core.UpdateInfo
import com.nextube.app.core.Updater
import com.nextube.app.core.YtDlp
import com.nextube.app.data.AppSettings
import com.nextube.app.data.DownloadOption
import com.nextube.app.data.DownloadRepository
import com.nextube.app.data.DownloadStatus
import com.nextube.app.data.DownloadTask
import com.nextube.app.data.MediaKind
import com.nextube.app.data.MediaSaver
import com.nextube.app.data.PlaylistDetails
import com.nextube.app.data.SearchItem
import com.nextube.app.data.VideoDetails
import com.nextube.app.service.DownloadService
import com.nextube.app.util.Fmt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface FeedState {
    data object Idle : FeedState
    data object Loading : FeedState
    data class Results(val items: List<SearchItem>) : FeedState
    data class Error(val message: String) : FeedState
}

sealed interface SheetState {
    val url: String
    data class Loading(override val url: String, val preview: SearchItem?) : SheetState
    data class Loaded(override val url: String, val details: VideoDetails) : SheetState
    data class Error(override val url: String, val preview: SearchItem?, val message: String) : SheetState
}

sealed interface PlaylistSheetState {
    val url: String
    data class Loading(override val url: String) : PlaylistSheetState
    data class Loaded(override val url: String, val details: PlaylistDetails) : PlaylistSheetState
    data class Error(override val url: String, val message: String) : PlaylistSheetState
}

data class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val openDownloads: Boolean = false,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Ready(val info: UpdateInfo, val apk: java.io.File) : UpdateState
    data class Failed(val info: UpdateInfo?, val message: String) : UpdateState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val engine: StateFlow<EngineState> = YtDlp.state
    val tasks: StateFlow<List<DownloadTask>> = DownloadRepository.tasks

    val categories = listOf(
        "Trending", "Music", "Bangla Song", "Natok", "Gaming", "News", "Cricket", "Tech", "Comedy", "Islamic", "Kids",
    )

    private val _selectedCategory = MutableStateFlow<String?>(categories.first())
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _feed = MutableStateFlow<FeedState>(FeedState.Idle)
    val feed: StateFlow<FeedState> = _feed.asStateFlow()

    private val _sheet = MutableStateFlow<SheetState?>(null)
    val sheet: StateFlow<SheetState?> = _sheet.asStateFlow()

    private val _playlistSheet = MutableStateFlow<PlaylistSheetState?>(null)
    val playlistSheet: StateFlow<PlaylistSheetState?> = _playlistSheet.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private val _engineMessage = MutableStateFlow<String?>(null)
    val engineMessage: StateFlow<String?> = _engineMessage.asStateFlow()

    private val _updating = MutableStateFlow(false)
    val updating: StateFlow<Boolean> = _updating.asStateFlow()

    private var searchJob: Job? = null
    private var detailsJob: Job? = null
    private var playlistJob: Job? = null
    private var lastQuery = categories.first()

    // ---- self-update ----

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    /** Optional updates can be postponed for this session; mandatory ones can't. */
    private val _updateDismissed = MutableStateFlow(false)
    val updateDismissed: StateFlow<Boolean> = _updateDismissed.asStateFlow()

    private var updateJob: Job? = null
    private var installAfterPermission = false

    fun checkForUpdate(manual: Boolean = false) {
        if (_update.value is UpdateState.Downloading || _update.value is UpdateState.Checking) return
        if (manual) _updateDismissed.value = false
        _update.value = UpdateState.Checking
        viewModelScope.launch {
            _update.value = runCatching { Updater.check() }.fold(
                { info -> if (info != null) UpdateState.Available(info) else UpdateState.UpToDate },
                { if (manual) UpdateState.Failed(null, "Couldn't check for updates. Check your internet.") else UpdateState.Idle },
            )
        }
    }

    fun dismissUpdate() {
        val info = currentUpdateInfo() ?: return
        if (info.isMandatory) return
        updateJob?.cancel()
        if (_update.value is UpdateState.Downloading) _update.value = UpdateState.Available(info)
        _updateDismissed.value = true
    }

    fun downloadUpdate() {
        val info = currentUpdateInfo() ?: return
        updateJob?.cancel()
        _update.value = UpdateState.Downloading(info, 0f)
        updateJob = viewModelScope.launch {
            try {
                val apk = Updater.download(getApplication(), info) { p ->
                    _update.value = UpdateState.Downloading(info, p)
                }
                _update.value = UpdateState.Ready(info, apk)
                if (!_updateDismissed.value || info.isMandatory) install()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _update.value = UpdateState.Failed(info, "Download failed: ${t.message ?: "network error"}")
            }
        }
    }

    fun install() {
        val ready = _update.value as? UpdateState.Ready ?: return
        val app = getApplication<Application>()
        if (!Updater.canInstall(app)) {
            installAfterPermission = true
            Updater.openInstallPermissionSettings(app)
            return
        }
        Updater.install(app, ready.apk)
    }

    /** Called when the user comes back, e.g. after allowing "Install unknown apps". */
    fun onAppResumed() {
        if (installAfterPermission && Updater.canInstall(getApplication())) {
            installAfterPermission = false
            install()
        }
        // A mandatory update must not be skippable by leaving and reopening the app.
        if (_update.value == UpdateState.Idle) checkForUpdate()
    }

    private fun currentUpdateInfo(): UpdateInfo? = when (val s = _update.value) {
        is UpdateState.Available -> s.info
        is UpdateState.Downloading -> s.info
        is UpdateState.Ready -> s.info
        is UpdateState.Failed -> s.info
        else -> null
    }

    init {
        // Fill the home feed once the engine has unpacked.
        viewModelScope.launch {
            engine.first { it is EngineState.Ready }
            if (_feed.value == FeedState.Idle) runSearch(lastQuery)
        }
    }

    /** Search box submit: a link opens the quality or playlist picker, anything else searches YouTube. */
    fun submit(input: String) {
        val text = input.trim()
        if (text.isEmpty()) return
        val url = Fmt.extractUrl(text) ?: if (Fmt.isUrl(text)) Fmt.normalizeUrl(text) else null
        if (url != null) openUrl(url) else {
            _selectedCategory.value = null
            runSearch(text)
        }
    }

    fun openUrl(url: String) {
        if (Fmt.isPlaylistUrl(url)) openPlaylist(url) else openDetails(url)
    }

    fun selectCategory(c: String) {
        _selectedCategory.value = c
        runSearch(if (c == "Trending") "trending today" else c)
    }

    fun retrySearch() = runSearch(lastQuery)

    private fun runSearch(q: String) {
        lastQuery = q
        searchJob?.cancel()
        _feed.value = FeedState.Loading
        searchJob = viewModelScope.launch {
            _feed.value = runCatching { YtDlp.search(q) }
                .fold({ FeedState.Results(it) }, { FeedState.Error(YtDlp.friendlyError(it)) })
        }
    }

    fun openDetails(url: String, preview: SearchItem? = null) {
        detailsJob?.cancel()
        _sheet.value = SheetState.Loading(url, preview)
        detailsJob = viewModelScope.launch {
            _sheet.value = runCatching { YtDlp.details(url) }
                .fold({ SheetState.Loaded(url, it) }, { SheetState.Error(url, preview, YtDlp.friendlyError(it)) })
        }
    }

    fun retryDetails() {
        val s = _sheet.value ?: return
        openDetails(s.url, (s as? SheetState.Error)?.preview)
    }

    fun dismissSheet() {
        detailsJob?.cancel()
        _sheet.value = null
    }

    fun openPlaylist(url: String) {
        dismissSheet()
        playlistJob?.cancel()
        _playlistSheet.value = PlaylistSheetState.Loading(url)
        playlistJob = viewModelScope.launch {
            _playlistSheet.value = runCatching { YtDlp.playlist(url) }
                .fold({ PlaylistSheetState.Loaded(url, it) }, { PlaylistSheetState.Error(url, YtDlp.friendlyError(it)) })
        }
    }

    fun retryPlaylist() {
        val s = _playlistSheet.value ?: return
        openPlaylist(s.url)
    }

    fun dismissPlaylistSheet() {
        playlistJob?.cancel()
        _playlistSheet.value = null
    }

    fun startDownload(details: VideoDetails, option: DownloadOption) {
        val task = DownloadTask(
            id = UUID.randomUUID().toString(),
            url = details.url,
            title = details.title,
            channel = details.channel,
            thumbnail = details.thumbnail.ifEmpty {
                if (details.id.isNotEmpty()) "https://i.ytimg.com/vi/${details.id}/hqdefault.jpg" else ""
            },
            kind = option.kind,
            optionLabel = if (option.kind == MediaKind.VIDEO) "${option.label} · ${option.detail}" else option.label,
            args = option.args,
            sizeBytes = option.sizeBytes, // estimate for the progress bar; replaced with the real size when done
        )
        DownloadRepository.add(task)
        DownloadService.enqueue(getApplication(), task.id)
        dismissSheet()
        _messages.tryEmit(UiMessage("Download started", actionLabel = "View", openDownloads = true))
    }

    fun startPlaylistDownload(items: List<SearchItem>, option: DownloadOption) {
        if (items.isEmpty()) return
        val label = if (option.kind == MediaKind.VIDEO) "${option.label} · ${option.detail}" else option.label
        val newTasks = items.map { item ->
            DownloadTask(
                id = UUID.randomUUID().toString(),
                url = item.url,
                title = item.title,
                channel = item.channel,
                thumbnail = item.thumbnail,
                kind = option.kind,
                optionLabel = label,
                args = option.args,
            )
        }
        DownloadRepository.addAll(newTasks)
        val app = getApplication<Application>()
        newTasks.forEach { DownloadService.enqueue(app, it.id) }
        dismissPlaylistSheet()
        val msg = if (newTasks.size == 1) "Download started" else "Queued ${newTasks.size} downloads"
        _messages.tryEmit(UiMessage(msg, actionLabel = "View", openDownloads = true))
    }

    fun cancel(task: DownloadTask) = DownloadService.cancel(getApplication(), task.id)

    fun retry(task: DownloadTask) {
        DownloadRepository.update(task.id) {
            it.copy(status = DownloadStatus.QUEUED, progress = 0f, error = null, speed = "", etaSec = -1)
        }
        DownloadService.enqueue(getApplication(), task.id)
    }

    fun delete(task: DownloadTask, deleteFile: Boolean) {
        if (task.status.isActive) DownloadService.cancel(getApplication(), task.id)
        if (deleteFile) task.fileUri?.let { MediaSaver.delete(getApplication(), it) }
        DownloadRepository.remove(task.id)
    }

    fun clearFinished() {
        val ids = tasks.value
            .filter { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED }
            .map { it.id }
            .toSet()
        DownloadRepository.removeAll(ids)
    }

    fun engineVersion(): String? = YtDlp.version(getApplication())

    fun updateEngine() {
        if (_updating.value) return
        _updating.value = true
        _engineMessage.value = "Checking for updates…"
        viewModelScope.launch {
            _engineMessage.value = runCatching { YtDlp.update(getApplication()) }
                .onSuccess { AppSettings.lastEngineUpdate = System.currentTimeMillis() }
                .getOrElse { "Update failed: ${YtDlp.friendlyError(it)}" }
            _updating.value = false
        }
    }
}
