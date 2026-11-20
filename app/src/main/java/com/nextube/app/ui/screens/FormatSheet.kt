package com.nextube.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nextube.app.data.DownloadOption
import com.nextube.app.data.MediaKind
import com.nextube.app.ui.MainViewModel
import com.nextube.app.ui.SheetState
import com.nextube.app.ui.components.DurationBadge
import com.nextube.app.ui.components.EmptyState
import com.nextube.app.ui.components.InstagramLoginDialog
import com.nextube.app.ui.components.PreviewPlayer
import com.nextube.app.ui.components.QualityBadge
import com.nextube.app.ui.components.Skeleton
import com.nextube.app.ui.components.Thumbnail
import com.nextube.app.util.Fmt

/** Quality picker shown after tapping a video or pasting a link. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatSheet(state: SheetState, vm: MainViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = vm::dismissSheet, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            when (state) {
                is SheetState.Loading -> {
                    Header(
                        title = state.preview?.title, channel = state.preview?.channel,
                        thumbnail = state.preview?.thumbnail.orEmpty(), duration = state.preview?.durationSec ?: 0,
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Finding available qualities…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                is SheetState.Error -> {
                    var showIgLogin by rememberSaveable { mutableStateOf(false) }
                    val isIg = Fmt.platformOf(state.url).name == "Instagram"
                    if (state.preview != null) Header(
                        state.preview.title, state.preview.channel, state.preview.thumbnail, state.preview.durationSec,
                    )
                    EmptyState(Icons.Rounded.ErrorOutline, "Couldn't read this video", state.message) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (isIg) {
                                FilledTonalButton(onClick = { showIgLogin = true }) { Text("Sign in to Instagram") }
                            }
                            FilledTonalButton(onClick = vm::retryDetails) { Text("Try again") }
                        }
                    }
                    if (showIgLogin) {
                        InstagramLoginDialog(
                            onSuccess = {
                                showIgLogin = false
                                vm.retryDetails()
                            },
                            onDismiss = { showIgLogin = false },
                        )
                    }
                }
                is SheetState.Loaded -> {
                    val d = state.details
                    var showPreview by rememberSaveable(d.url) { mutableStateOf(false) }
                    if (showPreview && d.preview != null) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            PreviewPlayer(
                                stream = d.preview,
                                onClose = { showPreview = false },
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                d.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (d.channel.isNotEmpty()) {
                                Text(
                                    d.channel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    } else {
                        Header(
                            title = d.title,
                            channel = d.channel,
                            thumbnail = d.thumbnail,
                            duration = d.durationSec,
                            platform = Fmt.platformOf(d.url),
                            onPreview = if (d.preview != null) { { showPreview = true } } else null,
                        )
                    }
                    if (d.playlistUrl != null) {
                        FilledTonalButton(
                            onClick = { vm.openPlaylist(d.playlistUrl) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Download full playlist instead")
                        }
                    }
                    var tab by rememberSaveable { mutableIntStateOf(0) }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        listOf("Video" to Icons.Rounded.Videocam, "Audio / MP3" to Icons.Rounded.MusicNote)
                            .forEachIndexed { i, (label, icon) ->
                                SegmentedButton(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                                    icon = { Icon(icon, null, Modifier.size(18.dp)) },
                                ) { Text(label) }
                            }
                    }
                    val options = if (tab == 0) d.videoOptions else d.audioOptions
                    LazyColumn(Modifier.heightIn(max = if (showPreview) 260.dp else 420.dp)) {
                        items(options, key = { it.label + it.detail }) { opt ->
                            OptionRow(opt, onClick = { vm.startDownload(d, opt) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    title: String?,
    channel: String?,
    thumbnail: String,
    duration: Long,
    platform: Fmt.PlatformInfo? = null,
    onPreview: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Thumbnail(
            thumbnail,
            Modifier
                .width(128.dp)
                .height(72.dp)
                .let { if (onPreview != null) it.clickable(onClick = onPreview) else it },
            RoundedCornerShape(12.dp),
        ) {
            if (onPreview != null) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PlayArrow, "Preview", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            DurationBadge(Fmt.duration(duration), Modifier.align(Alignment.BottomEnd).padding(4.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else {
                Skeleton(Modifier.fillMaxWidth().height(14.dp))
                Spacer(Modifier.height(6.dp))
                Skeleton(Modifier.fillMaxWidth(0.6f).height(14.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                if (platform != null) {
                    Text(
                        platform.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(platform.colorHex))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                    if (!channel.isNullOrEmpty()) Spacer(Modifier.width(6.dp))
                }
                if (!channel.isNullOrEmpty()) {
                    Text(
                        channel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onPreview != null) {
                Text(
                    "Tap thumbnail to preview",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun OptionRow(opt: DownloadOption, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Icon(
                    if (opt.kind == MediaKind.VIDEO) Icons.Rounded.Videocam else Icons.Rounded.MusicNote, null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(opt.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (opt.badge != null) {
                    Spacer(Modifier.width(8.dp))
                    QualityBadge(opt.badge)
                }
            }
            Text(
                listOf(opt.detail, if (opt.sizeBytes > 0) "≈ ${Fmt.bytes(opt.sizeBytes)}" else "").filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.Rounded.Download, "Download", tint = MaterialTheme.colorScheme.primary)
    }
}
