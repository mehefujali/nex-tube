package com.nextube.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nextube.app.R
import com.nextube.app.data.AppSettings
import com.nextube.app.data.DownloadStatus
import com.nextube.app.data.DownloadTask
import com.nextube.app.data.MediaKind
import com.nextube.app.data.MediaSaver
import com.nextube.app.ui.MainViewModel
import com.nextube.app.ui.PlayerActivity
import com.nextube.app.ui.components.EmptyState
import com.nextube.app.ui.components.SupportDialog
import com.nextube.app.ui.components.SupportSlimBanner
import com.nextube.app.ui.components.Thumbnail
import com.nextube.app.util.Fmt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(vm: MainViewModel, onBrowse: () -> Unit) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val saveFolderLabel by AppSettings.saveFolderLabel.collectAsStateWithLifecycle()
    val bannerDismissed by AppSettings.supportBannerDismissed.collectAsStateWithLifecycle()
    val active = tasks.filter { it.status != DownloadStatus.COMPLETED }
    val done = tasks.filter { it.status == DownloadStatus.COMPLETED }
    val pagerState = rememberPagerState(initialPage = if (active.any { it.status.isActive }) 0 else 1) { 2 }
    val scope = rememberCoroutineScope()
    var toDelete by remember { mutableStateOf<DownloadTask?>(null) }
    var showSupport by remember { mutableStateOf(false) }
    var filterQuery by remember { mutableStateOf("") }
    var filterKind by remember { mutableStateOf<MediaKind?>(null) }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.nav_downloads), fontWeight = FontWeight.Bold) },
            actions = {
                if (pagerState.currentPage == 0 && active.any { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED }) {
                    IconButton(onClick = vm::clearFinished) { Icon(Icons.Rounded.DeleteSweep, "Clear failed") }
                }
            },
        )
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                text = { Text(stringResource(R.string.tab_in_progress, active.size)) },
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                text = { Text(stringResource(R.string.tab_completed, done.size)) },
            )
        }
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val rawList = if (page == 0) active else done
            val filteredDone = if (page == 1) {
                val q = filterQuery.trim()
                done.filter { item ->
                    (filterKind == null || item.kind == filterKind) &&
                        (q.isEmpty() || item.title.contains(q, ignoreCase = true) || item.channel.contains(q, ignoreCase = true))
                }
            } else rawList

            if (rawList.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (page == 0) EmptyState(Icons.Rounded.Downloading, "Nothing downloading", "Find a video on Home or share a link to NexTube.") {
                        FilledTonalButton(onClick = onBrowse) { Text("Browse videos") }
                    } else EmptyState(
                        Icons.Rounded.DownloadDone,
                        "No downloads yet",
                        "Finished files are saved to ${saveFolderLabel ?: "Download/${MediaSaver.FOLDER}"}.",
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                    if (page == 1 && done.size >= 5 && !bannerDismissed) {
                        item(key = "support_banner") {
                            SupportSlimBanner(
                                onSupport = { showSupport = true },
                                onDismiss = AppSettings::dismissSupportBanner,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                    }
                    if (page == 1 && done.size >= 2) {
                        item(key = "done_filter") {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                                TextField(
                                    value = filterQuery,
                                    onValueChange = { filterQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("Search downloads…") },
                                    leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(18.dp)) },
                                    trailingIcon = {
                                        if (filterQuery.isNotEmpty()) {
                                            IconButton(onClick = { filterQuery = "" }) {
                                                Icon(Icons.Rounded.Close, "Clear", Modifier.size(18.dp))
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(24.dp),
                                    colors = TextFieldDefaults.colors(
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ),
                                )
                                Row(
                                    Modifier.padding(top = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FilterChip(
                                        selected = filterKind == null,
                                        onClick = { filterKind = null },
                                        label = { Text("All") },
                                        shape = CircleShape,
                                    )
                                    FilterChip(
                                        selected = filterKind == MediaKind.VIDEO,
                                        onClick = { filterKind = MediaKind.VIDEO },
                                        label = { Text("Video") },
                                        shape = CircleShape,
                                    )
                                    FilterChip(
                                        selected = filterKind == MediaKind.AUDIO,
                                        onClick = { filterKind = MediaKind.AUDIO },
                                        label = { Text("Audio") },
                                        shape = CircleShape,
                                    )
                                }
                            }
                        }
                    }
                    items(filteredDone, key = { it.id }) { t ->
                        if (page == 0) ActiveRow(
                            t,
                            onPause = { vm.pause(t) },
                            onResume = { vm.resume(t) },
                            onCancel = { vm.cancel(t) },
                            onRetry = { vm.retry(t) },
                            onRemove = { vm.delete(t, false) },
                        )
                        else DoneRow(
                            t,
                            onPlay = { PlayerActivity.start(context, t.id) },
                            onExtractMp3 = { vm.convertToMp3(t) },
                            onOpenExternal = { open(context, t) },
                            onShare = { share(context, t) },
                            onDelete = { toDelete = t },
                        )
                    }
                }
            }
        }
    }

    if (showSupport) {
        SupportDialog(onDismiss = { showSupport = false })
    }

    toDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete download?") },
            text = { Text("\"${t.title}\" will be removed from your phone.") },
            confirmButton = { TextButton(onClick = { vm.delete(t, true); toDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RowThumb(t: DownloadTask) {
    Thumbnail(t.thumbnail, Modifier.width(120.dp).height(68.dp), RoundedCornerShape(12.dp)) {
        if (t.kind == MediaKind.AUDIO) {
            Box(
                Modifier.align(Alignment.Center).size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.MusicNote, null, Modifier.size(16.dp), tint = Color.White) }
        }
    }
}

@Composable
private fun ActiveRow(
    t: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RowThumb(t)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            when (t.status) {
                DownloadStatus.QUEUED -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape))
                    Caption("Waiting · ${t.optionLabel}")
                }
                DownloadStatus.DOWNLOADING -> {
                    LinearProgressIndicator(progress = { t.progress }, modifier = Modifier.fillMaxWidth().clip(CircleShape))
                    val eta = if (t.etaSec > 0) "${Fmt.duration(t.etaSec)} left" else ""
                    Caption(listOf("${(t.progress * 100).toInt()}%", t.speed, eta).filter { it.isNotEmpty() }.joinToString(" · "))
                }
                DownloadStatus.PROCESSING -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape))
                    Caption("${t.note.ifEmpty { "Processing" }} · ${t.optionLabel}")
                }
                DownloadStatus.PAUSED -> {
                    LinearProgressIndicator(
                        progress = { t.progress },
                        modifier = Modifier.fillMaxWidth().clip(CircleShape),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val note = t.error ?: "Paused · ${(t.progress * 100).toInt()}%"
                    Caption("$note · ${t.optionLabel}")
                }
                DownloadStatus.FAILED -> Text(
                    t.error ?: "Failed", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                DownloadStatus.CANCELLED -> Caption("Cancelled")
                DownloadStatus.COMPLETED -> Unit
            }
        }
        when {
            t.status == DownloadStatus.DOWNLOADING || t.status == DownloadStatus.QUEUED -> {
                IconButton(onClick = onPause) { Icon(Icons.Rounded.Pause, "Pause") }
                IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, "Cancel") }
            }
            t.status == DownloadStatus.PROCESSING -> {
                IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, "Cancel") }
            }
            t.status == DownloadStatus.PAUSED -> {
                IconButton(onClick = onResume) {
                    Icon(Icons.Rounded.PlayArrow, "Resume", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, "Cancel") }
            }
            else -> {
                IconButton(onClick = onRetry) { Icon(Icons.Rounded.Refresh, "Retry") }
                IconButton(onClick = onRemove) { Icon(Icons.Rounded.Delete, "Remove") }
            }
        }
    }
}

@Composable
private fun DoneRow(
    t: DownloadTask,
    onPlay: () -> Unit,
    onExtractMp3: () -> Unit,
    onOpenExternal: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowThumb(t)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Caption(listOf(t.optionLabel, Fmt.bytes(t.sizeBytes)).joinToString(" · "))
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Play") }, leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) }, onClick = { menu = false; onPlay() })
                if (t.kind == MediaKind.VIDEO) {
                    DropdownMenuItem(text = { Text("Extract MP3") }, leadingIcon = { Icon(Icons.Rounded.MusicNote, null) }, onClick = { menu = false; onExtractMp3() })
                }
                DropdownMenuItem(text = { Text("Open with…") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) }, onClick = { menu = false; onOpenExternal() })
                DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Rounded.Share, null) }, onClick = { menu = false; onShare() })
                DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
    )
}

private fun open(context: Context, t: DownloadTask) {
    val uri = t.fileUri?.let(Uri::parse) ?: return
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, t.mimeType ?: "*/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app can play this file", Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, "File was moved or deleted", Toast.LENGTH_SHORT).show()
    }
}

private fun share(context: Context, t: DownloadTask) {
    val uri = t.fileUri?.let(Uri::parse) ?: return
    val send = Intent(Intent.ACTION_SEND).setType(t.mimeType ?: "*/*")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
