package com.nextube.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nextube.app.core.YtDlp
import com.nextube.app.data.MediaKind
import com.nextube.app.data.SearchItem
import com.nextube.app.ui.MainViewModel
import com.nextube.app.ui.PlaylistSheetState
import com.nextube.app.ui.components.DurationBadge
import com.nextube.app.ui.components.EmptyState
import com.nextube.app.ui.components.Thumbnail
import com.nextube.app.util.Fmt

/** Bottom sheet for selecting videos and quality when downloading a YouTube playlist. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSheet(state: PlaylistSheetState, vm: MainViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = vm::dismissPlaylistSheet, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            when (state) {
                is PlaylistSheetState.Loading -> {
                    Row(
                        Modifier.fillMaxWidth().padding(32.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Loading playlist…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                is PlaylistSheetState.Error -> {
                    EmptyState(Icons.Rounded.ErrorOutline, "Couldn't load playlist", state.message) {
                        FilledTonalButton(onClick = vm::retryPlaylist) { Text("Try again") }
                    }
                }
                is PlaylistSheetState.Loaded -> {
                    val pl = state.details
                    if (pl.items.isEmpty()) {
                        EmptyState(
                            Icons.AutoMirrored.Rounded.PlaylistPlay,
                            "Empty playlist",
                            "No downloadable videos were found in this playlist.",
                        )
                        return@Column
                    }

                    val videoPresets = remember { YtDlp.playlistVideoPresets() }
                    val audioPresets = remember { YtDlp.playlistAudioPresets() }
                    var tab by rememberSaveable(pl.url) { mutableIntStateOf(0) }
                    var selectedPresetIdx by rememberSaveable(pl.url, tab) { mutableIntStateOf(if (tab == 0) 1 else 0) }
                    var selectedIds by remember(pl.url) { mutableStateOf(pl.items.map { it.id }.toSet()) }

                    val presets = if (tab == 0) videoPresets else audioPresets
                    val activePreset = presets[selectedPresetIdx.coerceIn(0, presets.lastIndex)]

                    // Header
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            pl.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val subtitle = listOf(pl.channel, "${pl.items.size} videos")
                            .filter { it.isNotEmpty() }
                            .joinToString(" · ")
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // Video vs Audio toggle
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
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

                    // Quality chips
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        presets.forEachIndexed { i, opt ->
                            item(key = opt.label) {
                                FilterChip(
                                    selected = selectedPresetIdx == i,
                                    onClick = { selectedPresetIdx = i },
                                    label = { Text(opt.label) },
                                    shape = CircleShape,
                                )
                            }
                        }
                    }

                    // Select all / count bar
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${selectedIds.size} of ${pl.items.size} selected",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        val allSelected = selectedIds.size == pl.items.size
                        TextButton(
                            onClick = {
                                selectedIds = if (allSelected) emptySet() else pl.items.map { it.id }.toSet()
                            },
                        ) {
                            Text(if (allSelected) "Deselect all" else "Select all")
                        }
                    }

                    HorizontalDivider()

                    // Video checklist
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(pl.items, key = { it.id }) { item ->
                            val checked = item.id in selectedIds
                            PlaylistItemRow(
                                item = item,
                                checked = checked,
                                onToggle = {
                                    selectedIds = if (checked) selectedIds - item.id else selectedIds + item.id
                                },
                            )
                        }
                    }

                    HorizontalDivider()

                    // Primary Download CTA
                    val count = selectedIds.size
                    val unit = if (activePreset.kind == MediaKind.VIDEO) {
                        if (count == 1) "video" else "videos"
                    } else {
                        if (count == 1) "audio" else "audio files"
                    }
                    Button(
                        onClick = {
                            val chosen = pl.items.filter { it.id in selectedIds }
                            vm.startPlaylistDownload(chosen, activePreset)
                        },
                        enabled = count > 0,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Download $count $unit (${activePreset.label})")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistItemRow(item: SearchItem, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Thumbnail(item.thumbnail, Modifier.width(96.dp).height(54.dp), RoundedCornerShape(8.dp)) {
            DurationBadge(Fmt.duration(item.durationSec), Modifier.align(Alignment.BottomEnd).padding(4.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.channel.isNotEmpty()) {
                Text(
                    item.channel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
