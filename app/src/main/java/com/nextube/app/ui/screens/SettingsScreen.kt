package com.nextube.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nextube.app.BuildConfig
import com.nextube.app.ui.UpdateState
import com.nextube.app.core.EngineState
import com.nextube.app.data.AppSettings
import com.nextube.app.data.MediaSaver
import com.nextube.app.data.ThemeMode
import com.nextube.app.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val theme by AppSettings.theme.collectAsStateWithLifecycle()
    val maxConcurrent by AppSettings.maxConcurrent.collectAsStateWithLifecycle()
    val autoUpdate by AppSettings.autoUpdate.collectAsStateWithLifecycle()
    val engine by vm.engine.collectAsStateWithLifecycle()
    val updating by vm.updating.collectAsStateWithLifecycle()
    val engineMessage by vm.engineMessage.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val version = remember(engine, updating) { if (engine == EngineState.Ready) vm.engineVersion() else null }
    var showSupport by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Settings", fontWeight = FontWeight.Bold) })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {

            Section("Downloads")
            ListItem(
                headlineContent = { Text("Save location") },
                supportingContent = { Text("${Environment.DIRECTORY_DOWNLOADS}/${MediaSaver.FOLDER}") },
                leadingContent = { Icon(Icons.Rounded.Folder, null) },
            )
            ListItem(
                headlineContent = { Text("Downloads at the same time") },
                supportingContent = {
                    Column {
                        Text("Takes effect for new downloads")
                        Spacer(Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            (1..3).forEachIndexed { i, n ->
                                SegmentedButton(
                                    selected = maxConcurrent == n,
                                    onClick = { AppSettings.setMaxConcurrent(n) },
                                    shape = SegmentedButtonDefaults.itemShape(i, 3),
                                ) { Text("$n") }
                            }
                        }
                    }
                },
                leadingContent = { Icon(Icons.Rounded.Bolt, null) },
            )

            Section("Appearance")
            ListItem(
                headlineContent = { Text("Theme") },
                supportingContent = {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        ThemeMode.entries.forEachIndexed { i, m ->
                            SegmentedButton(
                                selected = theme == m,
                                onClick = { AppSettings.setTheme(m) },
                                shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                            ) { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        }
                    }
                },
                leadingContent = { Icon(Icons.Rounded.Palette, null) },
            )

            Section("Download engine (yt-dlp)")
            ListItem(
                headlineContent = { Text("Engine version") },
                supportingContent = {
                    Text(
                        when (val e = engine) {
                            EngineState.Initializing -> "Preparing…"
                            is EngineState.Failed -> "Error: ${e.message}"
                            EngineState.Ready -> version ?: "Bundled"
                        } + (engineMessage?.let { "\n$it" } ?: "")
                    )
                },
                leadingContent = { Icon(Icons.Rounded.SystemUpdate, null) },
                trailingContent = {
                    FilledTonalButton(onClick = vm::updateEngine, enabled = engine == EngineState.Ready && !updating) {
                        if (updating) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Update")
                    }
                },
            )
            ListItem(
                headlineContent = { Text("Auto-update engine") },
                supportingContent = { Text("Checks once a day. Keeps downloads working when YouTube changes.") },
                leadingContent = { Icon(Icons.Rounded.Update, null) },
                trailingContent = { Switch(checked = autoUpdate, onCheckedChange = AppSettings::setAutoUpdate) },
            )

            Section("App updates")
            ListItem(
                headlineContent = { Text("NexTube ${BuildConfig.VERSION_NAME}") },
                supportingContent = {
                    Text(
                        when (val u = update) {
                            UpdateState.Checking -> "Checking…"
                            UpdateState.UpToDate -> "You have the latest version"
                            is UpdateState.Available -> "Version ${u.info.versionName} is available"
                            is UpdateState.Downloading -> "Downloading ${(u.progress * 100).toInt()}%"
                            is UpdateState.Ready -> "Ready to install"
                            is UpdateState.Failed -> u.message
                            UpdateState.Idle -> "Updates come straight from GitHub Releases"
                        }
                    )
                },
                leadingContent = { Icon(Icons.Rounded.NewReleases, null) },
                trailingContent = {
                    FilledTonalButton(
                        onClick = { vm.checkForUpdate(manual = true) },
                        enabled = update !is UpdateState.Checking && update !is UpdateState.Downloading,
                    ) { Text("Check") }
                },
            )

            Section("Support")
            ListItem(
                modifier = Modifier.clickable { showSupport = true },
                headlineContent = { Text("Support NexTube") },
                supportingContent = { Text("Free, open-source & ad-free. Support development via UPI or GitHub Sponsors.") },
                leadingContent = { Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.primary) },
                trailingContent = {
                    FilledTonalButton(onClick = { showSupport = true }) { Text("Donate") }
                },
            )

            Section("About")
            ListItem(
                headlineContent = { Text("NexTube ${BuildConfig.VERSION_NAME}") },
                supportingContent = {
                    Text(
                        "Runs fully on your phone, no server. Android ${Build.VERSION.RELEASE}.\n" +
                            "Only download videos you own or have permission to save, and respect copyright and the platform's terms."
                    )
                },
                leadingContent = { Icon(Icons.Rounded.Info, null) },
            )
        }
    }

    if (showSupport) {
        SupportDialog(onDismiss = { showSupport = false })
    }
}

private const val SUPPORT_UPI_ID = "nextube@ybl"
private const val SUPPORT_SPONSORS_URL = "https://github.com/sponsors/mehefujali"

@Composable
private fun SupportDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    fun copyUpi() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("NexTube UPI ID", SUPPORT_UPI_ID))
        Toast.makeText(context, "UPI ID copied: $SUPPORT_UPI_ID", Toast.LENGTH_SHORT).show()
    }
    fun payWithUpi() {
        val uri = "upi://pay?pa=$SUPPORT_UPI_ID&pn=NexTube&tn=Support%20NexTube&cu=INR".toUri()
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            copyUpi()
            Toast.makeText(context, "No UPI app found — UPI ID copied", Toast.LENGTH_SHORT).show()
        }
    }
    fun openSponsors() {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, SUPPORT_SPONSORS_URL.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Support NexTube", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "NexTube is 100% free, open-source, and has no ads or tracking. If you find it useful, you can support its development:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(14.dp))
                Text("UPI (India)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            SUPPORT_UPI_ID,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = ::copyUpi) {
                            Icon(Icons.Rounded.ContentCopy, "Copy UPI ID", Modifier.size(18.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = ::payWithUpi, modifier = Modifier.fillMaxWidth()) {
                    Text("Pay with UPI app")
                }
                Spacer(Modifier.height(12.dp))
                Text("Global", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = ::openSponsors, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("GitHub Sponsors")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
}
