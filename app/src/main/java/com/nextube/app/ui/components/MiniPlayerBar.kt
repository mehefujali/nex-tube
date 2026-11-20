package com.nextube.app.ui.components

import android.content.ComponentName
import android.content.Intent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.nextube.app.service.PlaybackService
import com.nextube.app.ui.PlayerActivity

/**
 * Spotify / YouTube Music style Mini Now-Playing bar docked above the bottom NavigationBar
 * whenever PlaybackService has an active track.
 */
@OptIn(UnstableApi::class)
@Composable
fun MiniPlayerBar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var visible by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var artwork by remember { mutableStateOf("") }
    var isPlaying by remember { mutableStateOf(false) }

    fun sync(p: Player) {
        val item = p.currentMediaItem
        visible = item != null && p.playbackState != Player.STATE_IDLE && p.mediaItemCount > 0
        if (item != null) {
            title = item.mediaMetadata.title?.toString().orEmpty()
            artist = item.mediaMetadata.artist?.toString().orEmpty()
            artwork = item.mediaMetadata.artworkUri?.toString().orEmpty()
        }
        isPlaying = p.isPlaying
    }

    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        var listener: Player.Listener? = null
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            sync(c)
            val l = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) = sync(c)
                override fun onPlaybackStateChanged(playbackState: Int) = sync(c)
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = sync(c)
            }
            listener = l
            c.addListener(l)
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            listener?.let { controller?.removeListener(it) }
            MediaController.releaseFuture(future)
            controller = null
        }
    }

    AnimatedVisibility(
        visible = visible && title.isNotEmpty(),
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    context.startActivity(
                        Intent(context, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    )
                },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Thumbnail(artwork, Modifier.size(42.dp), RoundedCornerShape(8.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (artist.isNotEmpty()) {
                        Text(
                            artist,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(
                    onClick = {
                        val c = controller ?: return@IconButton
                        if (c.isPlaying) c.pause() else c.play()
                    },
                ) {
                    Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pause")
                }
                IconButton(
                    onClick = {
                        val c = controller ?: return@IconButton
                        if (c.hasNextMediaItem()) c.seekToNextMediaItem()
                    },
                ) {
                    Icon(Icons.Rounded.SkipNext, "Next")
                }
                IconButton(
                    onClick = {
                        val c = controller ?: return@IconButton
                        c.stop()
                        c.clearMediaItems()
                        visible = false
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.Rounded.Close, "Close", Modifier.size(18.dp))
                }
            }
        }
    }
}
