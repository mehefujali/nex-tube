package com.nextube.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nextube.app.core.EngineState
import com.nextube.app.core.YtDlp
import com.nextube.app.data.AppSettings
import com.nextube.app.data.DownloadOption
import com.nextube.app.data.DownloadRepository
import com.nextube.app.data.DownloadStatus
import com.nextube.app.data.DownloadTask
import com.nextube.app.data.MediaKind
import com.nextube.app.data.MediaSaver
import com.nextube.app.data.SearchItem
import com.nextube.app.data.VideoDetails
import com.nextube.app.service.DownloadService
import com.nextube.app.util.Fmt
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

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private val _engineMessage = MutableStateFlow<String?>(null)
    val engineMessage: StateFlow<String?> = _engineMessage.asStateFlow()

    private val _updating = MutableStateFlow(false)
    val updating: StateFlow<Boolean> = _updating.asStateFlow()

    private var searchJob: Job? = null
    private var detailsJob: Job? = null
    private var lastQuery = categories.first()

    init {
        // Fill the home feed once the engine has unpacked.
        viewModelScope.launch {
            engine.first { it is EngineState.Ready }
            if (_feed.value == FeedState.Idle) runSearch(lastQuery)
        }
    }

    /** Search box submit: a link opens the quality picker, anything else searches YouTube. */
    fun submit(input: String) {
        val text = input.trim()
        if (text.isEmpty()) return
        val url = Fmt.extractUrl(text) ?: if (Fmt.isUrl(text)) Fmt.normalizeUrl(text) else null
        if (url != null) openDetails(url) else {
            _selectedCategory.value = null
            runSearch(text)
        }
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
        _messages.tryEmit("Download started")
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
        tasks.value.filter { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED }
            .forEach { DownloadRepository.remove(it.id) }
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
