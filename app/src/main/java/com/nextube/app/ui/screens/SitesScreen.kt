package com.nextube.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nextube.app.core.CookieStore
import com.nextube.app.ui.MainViewModel
import com.nextube.app.ui.components.InstagramLoginDialog
import com.nextube.app.ui.components.PlatformLogo
import com.nextube.app.ui.components.SupportDialog
import com.nextube.app.ui.components.SupportHeroCard
import com.nextube.app.util.Fmt

private data class SupportedSite(
    val id: String,
    val name: String,
    val subtitle: String,
    val url: String,
)

private val SUPPORTED_SITES = listOf(
    SupportedSite("instagram", "Instagram", "Reels, Posts & Stories", "https://www.instagram.com/"),
    SupportedSite("facebook", "Facebook", "Reels, Watch & Videos", "https://www.facebook.com/watch/"),
    SupportedSite("x", "X / Twitter", "Videos & GIFs", "https://x.com/"),
    SupportedSite("tiktok", "TikTok", "Short Videos", "https://www.tiktok.com/"),
    SupportedSite("soundcloud", "SoundCloud", "Tracks & Playlists", "https://soundcloud.com/"),
    SupportedSite("reddit", "Reddit", "Video Posts", "https://www.reddit.com/"),
    SupportedSite("vimeo", "Vimeo", "HD & 4K Videos", "https://vimeo.com/"),
    SupportedSite("pinterest", "Pinterest", "Video Pins", "https://www.pinterest.com/"),
)

/**
 * Multi-site social downloader tab supporting 1,000+ platforms via yt-dlp:
 * Instagram, Facebook, X/Twitter, TikTok, SoundCloud, Reddit, Vimeo, Pinterest, and more.
 */
@Composable
fun SitesScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var linkInput by rememberSaveable { mutableStateOf("") }
    var dismissedClip by rememberSaveable { mutableStateOf<String?>(null) }
    val igConnected by CookieStore.hasInstagram.collectAsStateWithLifecycle()
    var showIgLogin by rememberSaveable { mutableStateOf(false) }
    var showSupport by remember { mutableStateOf(false) }

    val focused = LocalWindowInfo.current.isWindowFocused
    var clipboardLink by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(focused) {
        if (!focused) return@LaunchedEffect
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = runCatching { cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull()
        clipboardLink = Fmt.extractUrl(text)
    }

    fun submitLink(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        val url = Fmt.extractUrl(text) ?: if (Fmt.isUrl(text)) Fmt.normalizeUrl(text) else null
        if (url != null) {
            focus.clearFocus()
            vm.openUrl(url)
        } else {
            Toast.makeText(context, "Paste a valid video link (Instagram, Facebook, X, etc.)", Toast.LENGTH_SHORT).show()
        }
    }

    fun openExternalSite(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app or browser found", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Explore", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            "1000+ sites",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Paste any video link from Instagram, Facebook, X, TikTok, SoundCloud, or 1000+ other sites to download.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))

                // Universal Link Box
                TextField(
                    value = linkInput,
                    onValueChange = { linkInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Paste Instagram, FB, X, TikTok link…") },
                    leadingIcon = { Icon(Icons.Rounded.Link, null) },
                    trailingIcon = {
                        if (linkInput.isNotEmpty()) {
                            IconButton(onClick = { linkInput = "" }) { Icon(Icons.Rounded.Close, "Clear") }
                        } else {
                            IconButton(onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val text = runCatching { cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull()
                                val found = Fmt.extractUrl(text)
                                if (found != null) {
                                    linkInput = found
                                    submitLink(found)
                                } else {
                                    Toast.makeText(context, "No link found in clipboard", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Rounded.ContentPaste, "Paste link")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submitLink(linkInput) }),
                )

                if (linkInput.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    val p = Fmt.platformOf(linkInput)
                    FilledTonalButton(
                        onClick = { submitLink(linkInput) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Download from ${p.name}")
                    }
                }
            }
        }

        // Clipboard Smart Banner with Platform Badge
        item {
            val clip = clipboardLink
            AnimatedVisibility(clip != null && clip != dismissedClip && clip != linkInput) {
                if (clip != null) {
                    val platform = Fmt.platformOf(clip)
                    val pColor = Color(platform.colorHex)
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    ) {
                        Row(
                            Modifier
                                .background(Brush.linearGradient(listOf(pColor, pColor.copy(alpha = 0.72f))))
                                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${platform.name} link copied", color = Color.White, fontWeight = FontWeight.Bold)
                                Text(
                                    clip,
                                    color = Color.White.copy(alpha = 0.88f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            FilledTonalButton(onClick = { submitLink(clip) }) { Text("Download") }
                            IconButton(onClick = { dismissedClip = clip }) {
                                Icon(Icons.Rounded.Close, "Dismiss", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Popular platforms",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp),
            )
        }

        // 2-column grid of supported platforms
        items(SUPPORTED_SITES.chunked(2).size) { rowIndex ->
            val pair = SUPPORTED_SITES.chunked(2)[rowIndex]
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                pair.forEach { site ->
                    SiteCard(
                        site = site,
                        onClick = { openExternalSite(site.url) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlatformLogo(id = "instagram", size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (igConnected) "Instagram connected" else "Instagram sign-in",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (igConnected) "Reels, Posts & Stories are unlocked (stored locally)"
                            else "Sign in once if Instagram blocks guest downloads",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = {
                            if (igConnected) {
                                CookieStore.clear()
                                Toast.makeText(context, "Instagram signed out", Toast.LENGTH_SHORT).show()
                            } else {
                                showIgLogin = true
                            }
                        },
                    ) {
                        Text(if (igConnected) "Sign out" else "Sign in")
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Language, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Tip: Share straight from any app", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "In Instagram, Facebook, X, or TikTok, tap Share → NexTube to download immediately in one tap.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SupportHeroCard(
                onSupport = { showSupport = true },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }

    if (showIgLogin) {
        InstagramLoginDialog(
            onSuccess = { showIgLogin = false },
            onDismiss = { showIgLogin = false },
        )
    }

    if (showSupport) {
        SupportDialog(onDismiss = { showSupport = false })
    }
}

@Composable
private fun SiteCard(site: SupportedSite, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlatformLogo(id = site.id, size = 42.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        site.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Rounded.OpenInNew,
                        null,
                        Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    site.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
