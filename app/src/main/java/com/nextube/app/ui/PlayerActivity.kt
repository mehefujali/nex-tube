package com.nextube.app.ui

import android.app.PictureInPictureParams
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import com.nextube.app.data.AppSettings
import com.nextube.app.data.DownloadRepository
import com.nextube.app.data.DownloadStatus
import com.nextube.app.data.DownloadTask
import com.nextube.app.data.MediaKind
import com.nextube.app.data.ThemeMode
import com.nextube.app.service.PlaybackService
import com.nextube.app.ui.components.Thumbnail
import com.nextube.app.ui.theme.BrandRed
import com.nextube.app.ui.theme.NexTubeTheme
import com.nextube.app.util.Fmt
import kotlinx.coroutines.delay

/**
 * Full-screen pro media player for downloaded videos & audio:
 * - Gestures: double-tap +/-10s seek, left-swipe brightness, right-swipe volume
 * - Speed selector (0.5x..2x), aspect ratio cycle (Fit/Fill/Zoom), screen lock, rotation toggle
 * - Picture-in-Picture (PiP), repeat modes, sleep timer, and full Downloads queue (Next/Prev)
 */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_TASK_ID = "task_id"

        fun start(context: Context, taskId: String) {
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra(EXTRA_TASK_ID, taskId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val activePlayer = mutableStateOf<Player?>(null)
    private var queue: List<DownloadTask> = emptyList()
    private val currentIndex = mutableIntStateOf(0)
    private val inPipMode = mutableStateOf(false)
    private var pendingTaskId: String? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppSettings.wrapLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        pendingTaskId = intent?.getStringExtra(EXTRA_TASK_ID)
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync().also { future ->
            future.addListener({
                val controller = runCatching { future.get() }.getOrNull() ?: return@addListener
                activePlayer.value = controller
                loadQueueAndPlay(controller, pendingTaskId)
            }, ContextCompat.getMainExecutor(this))
        }

        setContent {
            NexTubeTheme(ThemeMode.DARK) {
                val p = activePlayer.value
                val idx = currentIndex.intValue
                val current = queue.getOrNull(idx)
                if (p == null || current == null) {
                    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BrandRed)
                    }
                } else {
                    ProPlayerScreen(
                        activity = this,
                        player = p,
                        queue = queue,
                        currentIndex = idx,
                        inPip = inPipMode.value,
                        onSelectTrack = { i -> seekToTrack(p, i) },
                        onNext = { if (idx + 1 < queue.size) seekToTrack(p, idx + 1) },
                        onPrev = {
                            if (p.currentPosition > 3000) p.seekTo(0)
                            else if (idx > 0) seekToTrack(p, idx - 1)
                        },
                        onEnterPip = { enterPip() },
                        onToggleOrientation = { toggleOrientation() },
                        onOpenExternal = { openExternal(current) },
                        onShare = { shareFile(current) },
                        onClose = { finish() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val id = intent.getStringExtra(EXTRA_TASK_ID)
        pendingTaskId = id
        val p = activePlayer.value
        if (p != null && id != null) loadQueueAndPlay(p, id)
    }

    private fun loadQueueAndPlay(p: Player, taskId: String?) {
        val completed = DownloadRepository.tasks.value.filter {
            it.status == DownloadStatus.COMPLETED && !it.fileUri.isNullOrBlank()
        }
        if (completed.isEmpty()) {
            finish()
            return
        }
        queue = completed
        val idx = if (taskId != null) {
            completed.indexOfFirst { it.id == taskId }.coerceAtLeast(0)
        } else {
            p.currentMediaItemIndex.coerceIn(0, completed.lastIndex)
        }
        val mediaItems = completed.map { t ->
            MediaItem.Builder()
                .setMediaId(t.id)
                .setUri(t.fileUri!!.toUri())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(t.title)
                        .setArtist(t.channel.ifEmpty { "NexTube" })
                        .setArtworkUri(t.thumbnail.takeIf { it.isNotBlank() }?.toUri())
                        .build()
                )
                .build()
        }
        val alreadyPlayingSame = taskId == null && p.mediaItemCount == mediaItems.size
        if (!alreadyPlayingSame) {
            p.setMediaItems(mediaItems, idx, 0L)
            p.playWhenReady = true
            p.prepare()
        }
        currentIndex.intValue = idx
        applyImmersive(completed[idx].kind == MediaKind.VIDEO)
    }

    private fun seekToTrack(p: Player, index: Int) {
        val task = queue.getOrNull(index) ?: return
        currentIndex.intValue = index
        p.seekTo(index, 0L)
        p.playWhenReady = true
        applyImmersive(task.kind == MediaKind.VIDEO)
    }

    private fun applyImmersive(hideBars: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hideBars) controller.hide(WindowInsetsCompat.Type.systemBars())
        else controller.show(WindowInsetsCompat.Type.systemBars())
    }

    private fun toggleOrientation() {
        val isLand = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        requestedOrientation = if (isLand) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    private fun enterPip() {
        val p = activePlayer.value ?: return
        val task = queue.getOrNull(currentIndex.intValue) ?: return
        if (task.kind != MediaKind.VIDEO) return
        val size = p.videoSize
        val aspect = if (size.width > 0 && size.height > 0) {
            val ratio = size.width.toFloat() / size.height.toFloat()
            if (ratio in 0.42f..2.38f) Rational(size.width, size.height) else Rational(16, 9)
        } else Rational(16, 9)
        runCatching {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(aspect).build())
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val p = activePlayer.value ?: return
        val task = queue.getOrNull(currentIndex.intValue)
        if (task?.kind == MediaKind.VIDEO && p.isPlaying) {
            enterPip()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPipMode.value = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        val p = activePlayer.value ?: return
        val task = queue.getOrNull(currentIndex.intValue)
        // Pause videos when leaving the app (unless in PiP), but let audio/music play in the background!
        if (!isInPictureInPictureMode && task?.kind == MediaKind.VIDEO) {
            p.pause()
        }
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }

    private fun openExternal(t: DownloadTask) {
        val uri = t.fileUri?.toUri() ?: return
        activePlayer.value?.pause()
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, t.mimeType ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No app can play this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFile(t: DownloadTask) {
        val uri = t.fileUri?.toUri() ?: return
        activePlayer.value?.pause()
        val send = Intent(Intent.ACTION_SEND).setType(t.mimeType ?: "*/*")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, "Share"))
    }
}

private enum class RepeatSetting { OFF, ONE, ALL }

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProPlayerScreen(
    activity: ComponentActivity,
    player: Player,
    queue: List<DownloadTask>,
    currentIndex: Int,
    inPip: Boolean,
    onSelectTrack: (Int) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleOrientation: () -> Unit,
    onOpenExternal: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    val task = queue[currentIndex]
    val isVideo = task.kind == MediaKind.VIDEO

    var isPlaying by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(false) }
    var failed by remember(task.id) { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }

    var showControls by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var repeatMode by remember { mutableStateOf(RepeatSetting.OFF) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    // Sheets
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showSleepSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }

    // Sleep timer (remaining seconds, -1 = off)
    var sleepSecLeft by remember { mutableIntStateOf(-1) }

    // Gesture HUD overlay ("+10s", "-10s", "Volume 70%", "Brightness 55%", "Fit")
    var hudText by remember { mutableStateOf<String?>(null) }
    var hudIcon by remember { mutableStateOf<String?>(null) }
    var hudTick by remember { mutableIntStateOf(0) }

    fun flashHud(text: String, icon: String? = null) {
        hudText = text
        hudIcon = icon
        hudTick++
    }

    LaunchedEffect(hudTick) {
        if (hudText != null) {
            delay(900)
            hudText = null
            hudIcon = null
        }
    }

    // Auto-hide controls in video mode
    LaunchedEffect(showControls, isPlaying, locked, isVideo, isScrubbing) {
        if (isVideo && showControls && isPlaying && !locked && !isScrubbing) {
            delay(3500)
            showControls = false
        }
    }

    // Sleep timer countdown
    LaunchedEffect(sleepSecLeft) {
        if (sleepSecLeft > 0) {
            delay(1000)
            sleepSecLeft--
            if (sleepSecLeft == 0) {
                player.pause()
                sleepSecLeft = -1
                flashHud("Sleep timer finished")
            }
        }
    }

    // Sync player events (including track changes from lockscreen/Bluetooth!)
    DisposableEffect(player, currentIndex, repeatMode) {
        player.repeatMode = when (repeatMode) {
            RepeatSetting.ONE -> Player.REPEAT_MODE_ONE
            RepeatSetting.ALL -> Player.REPEAT_MODE_ALL
            RepeatSetting.OFF -> Player.REPEAT_MODE_OFF
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val newIdx = player.currentMediaItemIndex
                if (newIdx in queue.indices && newIdx != currentIndex) {
                    onSelectTrack(newIdx)
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY && player.duration > 0) {
                    durationMs = player.duration
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                buffering = false
                failed = true
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Position ticker
    LaunchedEffect(player) {
        while (true) {
            if (!isScrubbing) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                if (player.duration > 0) durationMs = player.duration
            }
            delay(250)
        }
    }

    val audioManager = remember { activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { boxSize = it },
    ) {
        if (failed) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Couldn't play this file", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text(
                    "The file may have been moved or deleted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        } else if (isVideo) {
            // Video Surface
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                        this.resizeMode = resizeMode
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                update = { view ->
                    view.player = player
                    view.resizeMode = resizeMode
                },
                modifier = Modifier.fillMaxSize(),
            )

            // Gesture Layer (disabled in PiP)
            if (!inPip) {
                var dragVolume by remember { mutableFloatStateOf(0f) }
                var dragBrightness by remember { mutableFloatStateOf(0.5f) }
                var isLeftDrag by remember { mutableStateOf(true) }

                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(locked, boxSize) {
                            detectTapGestures(
                                onTap = { showControls = !showControls },
                                onDoubleTap = { offset ->
                                    if (locked) return@detectTapGestures
                                    val w = boxSize.width.coerceAtLeast(1)
                                    if (offset.x < w / 2f) {
                                        val target = (player.currentPosition - 10_000).coerceAtLeast(0L)
                                        player.seekTo(target)
                                        positionMs = target
                                        flashHud("-10s", "back")
                                    } else {
                                        val max = player.duration.coerceAtLeast(0L)
                                        val target = (player.currentPosition + 10_000).coerceAtMost(max)
                                        player.seekTo(target)
                                        positionMs = target
                                        flashHud("+10s", "fwd")
                                    }
                                },
                            )
                        }
                        .pointerInput(locked, boxSize) {
                            detectVerticalDragGestures(
                                onDragStart = { start ->
                                    if (locked) return@detectVerticalDragGestures
                                    isLeftDrag = start.x < (boxSize.width / 2f)
                                    if (isLeftDrag) {
                                        val cur = activity.window.attributes.screenBrightness
                                        dragBrightness = if (cur in 0f..1f) cur else 0.5f
                                    } else {
                                        dragVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
                                    }
                                },
                                onVerticalDrag = { _, dragAmount ->
                                    if (locked) return@detectVerticalDragGestures
                                    val h = boxSize.height.coerceAtLeast(400).toFloat()
                                    val delta = -dragAmount / (h * 0.65f)
                                    if (isLeftDrag) {
                                        dragBrightness = (dragBrightness + delta).coerceIn(0.02f, 1f)
                                        val lp = activity.window.attributes
                                        lp.screenBrightness = dragBrightness
                                        activity.window.attributes = lp
                                        flashHud("Brightness ${(dragBrightness * 100).toInt()}%", "bright")
                                    } else {
                                        dragVolume = (dragVolume + delta).coerceIn(0f, 1f)
                                        val vol = (dragVolume * maxVolume).toInt().coerceIn(0, maxVolume)
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
                                        flashHud("Volume ${(dragVolume * 100).toInt()}%", "vol")
                                    }
                                },
                            )
                        },
                )
            }
        } else {
            // Full-Screen Pro Audio Player UI
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF2B0B10), Color(0xFF120D0E), Color(0xFF090708))
                        )
                    ),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Top bar
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Now Playing",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { showQueueSheet = true }) {
                            Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Queue", tint = Color.White)
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // Album Art
                    Thumbnail(
                        url = task.thumbnail,
                        modifier = Modifier.size(250.dp),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(Modifier.height(28.dp))

                    Text(
                        task.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOf(task.channel, task.optionLabel, Fmt.bytes(task.sizeBytes))
                            .filter { it.isNotEmpty() }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.height(24.dp))

                    // Seek bar
                    val maxDur = durationMs.coerceAtLeast(1L)
                    Slider(
                        value = (positionMs.toFloat() / maxDur).coerceIn(0f, 1f),
                        onValueChange = { frac ->
                            isScrubbing = true
                            positionMs = (frac * maxDur).toLong()
                        },
                        onValueChangeFinished = {
                            player.seekTo(positionMs)
                            isScrubbing = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = BrandRed,
                            activeTrackColor = BrandRed,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(Fmt.duration(positionMs / 1000).ifEmpty { "0:00" }, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
                        Text(Fmt.duration(durationMs / 1000).ifEmpty { "0:00" }, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
                    }

                    Spacer(Modifier.height(16.dp))

                    // Main Transport Controls
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onPrev, enabled = currentIndex > 0 || positionMs > 3000) {
                            Icon(Icons.Rounded.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                        IconButton(onClick = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) }) {
                            Icon(Icons.Rounded.Replay10, "-10s", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        FilledIconButton(
                            onClick = {
                                if (player.playbackState == Player.STATE_ENDED) {
                                    player.seekTo(0); player.play()
                                } else if (isPlaying) player.pause() else player.play()
                            },
                            modifier = Modifier.size(64.dp),
                        ) {
                            Icon(
                                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(34.dp),
                            )
                        }
                        IconButton(onClick = { player.seekTo((player.currentPosition + 10_000).coerceAtMost(maxDur)) }) {
                            Icon(Icons.Rounded.Forward10, "+10s", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        IconButton(onClick = onNext, enabled = currentIndex + 1 < queue.size) {
                            Icon(Icons.Rounded.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // Bottom Pro Tool Strip
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = {
                            repeatMode = when (repeatMode) {
                                RepeatSetting.OFF -> RepeatSetting.ONE
                                RepeatSetting.ONE -> RepeatSetting.ALL
                                RepeatSetting.ALL -> RepeatSetting.OFF
                            }
                            flashHud("Repeat: ${repeatMode.name.lowercase().replaceFirstChar { it.uppercase() }}")
                        }) {
                            Icon(
                                if (repeatMode == RepeatSetting.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                "Repeat",
                                tint = if (repeatMode != RepeatSetting.OFF) BrandRed else Color.White.copy(alpha = 0.75f),
                            )
                        }
                        IconButton(onClick = { showSpeedSheet = true }) {
                            Text(
                                "${if (speed == 1f) "1" else speed}x",
                                color = if (speed != 1f) BrandRed else Color.White.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        IconButton(onClick = { showSleepSheet = true }) {
                            Icon(
                                Icons.Rounded.Bedtime,
                                "Sleep timer",
                                tint = if (sleepSecLeft > 0) BrandRed else Color.White.copy(alpha = 0.75f),
                            )
                        }
                        IconButton(onClick = onShare) {
                            Icon(Icons.Rounded.Share, "Share", tint = Color.White.copy(alpha = 0.75f))
                        }
                        IconButton(onClick = onOpenExternal) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open with", tint = Color.White.copy(alpha = 0.75f))
                        }
                    }
                }
            }
        }

        // Buffering spinner
        if (buffering && !failed) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(42.dp),
                color = BrandRed,
                strokeWidth = 3.dp,
            )
        }

        // Gesture / Status HUD pill
        AnimatedVisibility(
            visible = hudText != null && !inPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.78f),
            ) {
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (hudIcon) {
                        "bright" -> Icon(Icons.Rounded.BrightnessHigh, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        "vol" -> Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        "back" -> Icon(Icons.Rounded.Replay10, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        "fwd" -> Icon(Icons.Rounded.Forward10, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    if (hudIcon != null) Spacer(Modifier.width(8.dp))
                    Text(hudText.orEmpty(), color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Video Overlay Controls (hidden in PiP)
        if (isVideo && !inPip) {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (locked) {
                    Box(Modifier.fillMaxSize().padding(24.dp)) {
                        IconButton(
                            onClick = { locked = false; flashHud("Unlocked") },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f)),
                        ) {
                            Icon(Icons.Rounded.LockOpen, "Unlock", tint = Color.White)
                        }
                    }
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.42f)),
                    ) {
                        // Top bar
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = onClose) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    task.title,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    listOf(
                                        task.optionLabel,
                                        if (sleepSecLeft > 0) "Sleep ${Fmt.duration(sleepSecLeft.toLong())}" else "",
                                    ).filter { it.isNotEmpty() }.joinToString(" · "),
                                    color = Color.White.copy(alpha = 0.75f),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            IconButton(onClick = onEnterPip) {
                                Icon(Icons.Rounded.PictureInPictureAlt, "PiP", tint = Color.White)
                            }
                            IconButton(onClick = { showQueueSheet = true }) {
                                Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Queue", tint = Color.White)
                            }
                            IconButton(onClick = onShare) {
                                Icon(Icons.Rounded.Share, "Share", tint = Color.White)
                            }
                        }

                        // Center transport controls
                        Row(
                            Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = onPrev, enabled = currentIndex > 0 || positionMs > 3000) {
                                Icon(Icons.Rounded.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(34.dp))
                            }
                            IconButton(onClick = {
                                val t = (player.currentPosition - 10_000).coerceAtLeast(0L)
                                player.seekTo(t); positionMs = t
                            }) {
                                Icon(Icons.Rounded.Replay10, "-10s", tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                            Box(
                                Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(BrandRed.copy(alpha = 0.92f))
                                    .clickable {
                                        if (player.playbackState == Player.STATE_ENDED) {
                                            player.seekTo(0); player.play()
                                        } else if (isPlaying) player.pause() else player.play()
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                            IconButton(onClick = {
                                val t = (player.currentPosition + 10_000).coerceAtMost(durationMs.coerceAtLeast(0L))
                                player.seekTo(t); positionMs = t
                            }) {
                                Icon(Icons.Rounded.Forward10, "+10s", tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                            IconButton(onClick = onNext, enabled = currentIndex + 1 < queue.size) {
                                Icon(Icons.Rounded.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(34.dp))
                            }
                        }

                        // Bottom seek bar + pro controls
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            val maxDur = durationMs.coerceAtLeast(1L)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    Fmt.duration(positionMs / 1000).ifEmpty { "0:00" },
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Slider(
                                    value = (positionMs.toFloat() / maxDur).coerceIn(0f, 1f),
                                    onValueChange = { frac ->
                                        isScrubbing = true
                                        positionMs = (frac * maxDur).toLong()
                                    },
                                    onValueChangeFinished = {
                                        player.seekTo(positionMs)
                                        isScrubbing = false
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = BrandRed,
                                        activeTrackColor = BrandRed,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                    ),
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                )
                                Text(
                                    Fmt.duration(durationMs / 1000).ifEmpty { "0:00" },
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(onClick = { locked = true; flashHud("Screen locked") }) {
                                    Icon(Icons.Rounded.Lock, "Lock", tint = Color.White)
                                }
                                IconButton(onClick = { showSpeedSheet = true }) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Speed, "Speed", tint = if (speed != 1f) BrandRed else Color.White, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text("${if (speed == 1f) "1" else speed}x", color = if (speed != 1f) BrandRed else Color.White, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                IconButton(onClick = {
                                    repeatMode = when (repeatMode) {
                                        RepeatSetting.OFF -> RepeatSetting.ONE
                                        RepeatSetting.ONE -> RepeatSetting.ALL
                                        RepeatSetting.ALL -> RepeatSetting.OFF
                                    }
                                    flashHud("Repeat: ${repeatMode.name.lowercase().replaceFirstChar { it.uppercase() }}")
                                }) {
                                    Icon(
                                        if (repeatMode == RepeatSetting.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                        "Repeat",
                                        tint = if (repeatMode != RepeatSetting.OFF) BrandRed else Color.White,
                                    )
                                }
                                IconButton(onClick = { showSleepSheet = true }) {
                                    Icon(Icons.Rounded.Bedtime, "Sleep", tint = if (sleepSecLeft > 0) BrandRed else Color.White)
                                }
                                IconButton(onClick = {
                                    resizeMode = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                    val label = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Crop / Zoom"
                                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch"
                                        else -> "Fit"
                                    }
                                    flashHud(label)
                                }) {
                                    Icon(Icons.Rounded.AspectRatio, "Aspect ratio", tint = Color.White)
                                }
                                IconButton(onClick = onToggleOrientation) {
                                    Icon(Icons.Rounded.ScreenRotation, "Rotate", tint = Color.White)
                                }
                                IconButton(onClick = onOpenExternal) {
                                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open externally", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Speed Picker Sheet
    if (showSpeedSheet) {
        ModalBottomSheet(onDismissRequest = { showSpeedSheet = false }, sheetState = rememberModalBottomSheetState(true)) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("Playback speed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    speeds.take(4).forEach { s ->
                        FilterChip(
                            selected = speed == s,
                            onClick = {
                                speed = s
                                player.playbackParameters = PlaybackParameters(s)
                                showSpeedSheet = false
                            },
                            label = { Text("${if (s == 1f) "1" else s}x") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    speeds.drop(4).forEach { s ->
                        FilterChip(
                            selected = speed == s,
                            onClick = {
                                speed = s
                                player.playbackParameters = PlaybackParameters(s)
                                showSpeedSheet = false
                            },
                            label = { Text("${s}x") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    // Sleep Timer Sheet
    if (showSleepSheet) {
        ModalBottomSheet(onDismissRequest = { showSleepSheet = false }, sheetState = rememberModalBottomSheetState(true)) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("Sleep timer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                listOf(0 to "Off", 15 to "15 minutes", 30 to "30 minutes", 45 to "45 minutes", 60 to "60 minutes").forEach { (mins, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                sleepSecLeft = if (mins == 0) -1 else mins * 60
                                flashHud(if (mins == 0) "Sleep timer off" else "Sleep in $mins min")
                                showSleepSheet = false
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // Up Next Queue Sheet
    if (showQueueSheet) {
        ModalBottomSheet(onDismissRequest = { showQueueSheet = false }, sheetState = rememberModalBottomSheetState(true)) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                Text(
                    "Downloads queue (${queue.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    itemsIndexed(queue, key = { _, item -> item.id }) { i, item ->
                        val active = i == currentIndex
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (active) BrandRed.copy(alpha = 0.14f) else Color.Transparent)
                                .clickable {
                                    onSelectTrack(i)
                                    showQueueSheet = false
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Thumbnail(item.thumbnail, Modifier.width(88.dp).height(50.dp), RoundedCornerShape(8.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    color = if (active) BrandRed else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    listOf(item.optionLabel, Fmt.bytes(item.sizeBytes)).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
