package com.nextube.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nextube.app.core.EngineState
import com.nextube.app.data.SearchItem
import com.nextube.app.ui.FeedState
import com.nextube.app.ui.MainViewModel
import com.nextube.app.ui.components.DurationBadge
import com.nextube.app.ui.components.EmptyState
import com.nextube.app.ui.components.Skeleton
import com.nextube.app.ui.components.Thumbnail
import com.nextube.app.ui.components.Wordmark
import com.nextube.app.ui.theme.BrandGradient
import com.nextube.app.util.Fmt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel) {
    val feed by vm.feed.collectAsStateWithLifecycle()
    val engine by vm.engine.collectAsStateWithLifecycle()
    val selected by vm.selectedCategory.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val clipboardLink = rememberClipboardLink()
    var dismissedLink by rememberSaveable { mutableStateOf<String?>(null) }

    fun submit(text: String) {
        focus.clearFocus()
        vm.submit(text)
    }

    PullToRefreshBox(
        isRefreshing = feed is FeedState.Loading,
        onRefresh = vm::retrySearch,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Wordmark()
                    Spacer(Modifier.weight(1f))
                    EngineChip(engine)
                }
                Spacer(Modifier.height(12.dp))
                SearchBox(
                    value = query,
                    onChange = { query = it },
                    onSubmit = { submit(query) },
                    onPaste = { clipboardLink?.let { query = it; submit(it) } },
                )
            }
        }

        item {
            val link = clipboardLink
            AnimatedVisibility(link != null && link != dismissedLink && query != link) {
                if (link != null) ClipboardCard(link, onDownload = { submit(link) }, onDismiss = { dismissedLink = link })
            }
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                items(vm.categories) { c ->
                    FilterChip(
                        selected = selected == c,
                        onClick = { focus.clearFocus(); vm.selectCategory(c) },
                        label = { Text(c) },
                        shape = CircleShape,
                    )
                }
            }
        }

        when (val f = feed) {
            FeedState.Idle, FeedState.Loading -> items(5) { VideoCardSkeleton() }
            is FeedState.Error -> item {
                EmptyState(Icons.Rounded.WifiOff, "Couldn't load videos", f.message) {
                    FilledTonalButton(onClick = vm::retrySearch) { Text("Try again") }
                }
            }
            is FeedState.Results -> {
                if (f.items.isEmpty()) item {
                    EmptyState(Icons.Rounded.Search, "No results", "Try different words or paste a video link.")
                }
                items(f.items, key = { it.id }) { item ->
                    VideoCard(item, onClick = { vm.openDetails(item.url, item) })
                }
            }
        }
        }
    }
}

@Composable
private fun EngineChip(engine: EngineState) {
    when (engine) {
        EngineState.Initializing -> Row(
            Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(6.dp))
            Text("Preparing engine", style = MaterialTheme.typography.labelMedium)
        }
        is EngineState.Failed -> Text(
            "Engine error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium,
        )
        EngineState.Ready -> Unit
    }
}

@Composable
private fun SearchBox(value: String, onChange: (String) -> Unit, onSubmit: () -> Unit, onPaste: () -> Unit) {
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search or paste a video link") },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, "Clear") }
            else IconButton(onClick = onPaste) { Icon(Icons.Rounded.ContentPaste, "Paste link") }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
    )
}

@Composable
private fun ClipboardCard(link: String, onDownload: () -> Unit, onDismiss: () -> Unit) {
    val platform = Fmt.platformOf(link)
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).padding(bottom = 8.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Row(
            Modifier.background(BrandGradient).padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Link, null, tint = Color.White)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${platform.name} link copied", color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    link, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            FilledTonalButton(onClick = onDownload) { Text("Download") }
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Dismiss", tint = Color.White) }
        }
    }
}

@Composable
private fun VideoCard(item: SearchItem, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Thumbnail(item.thumbnail, Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            DurationBadge(Fmt.duration(item.durationSec), Modifier.align(Alignment.BottomEnd).padding(8.dp))
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    listOf(item.channel, Fmt.views(item.views)).filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = onClick) { Icon(Icons.Rounded.Download, "Download") }
        }
    }
}

@Composable
private fun VideoCardSkeleton() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Skeleton(Modifier.fillMaxWidth().aspectRatio(16f / 9f), MaterialTheme.shapes.medium)
        Spacer(Modifier.height(10.dp))
        Skeleton(Modifier.fillMaxWidth(0.9f).height(14.dp))
        Spacer(Modifier.height(6.dp))
        Skeleton(Modifier.fillMaxWidth(0.5f).height(12.dp))
    }
}

/** Reads the clipboard whenever the window regains focus (Android only allows it while focused). */
@Composable
private fun rememberClipboardLink(): String? {
    val context = LocalContext.current
    val focused = LocalWindowInfo.current.isWindowFocused
    var link by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(focused) {
        if (!focused) return@LaunchedEffect
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = runCatching { cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull()
        link = Fmt.extractUrl(text)
    }
    return link
}
