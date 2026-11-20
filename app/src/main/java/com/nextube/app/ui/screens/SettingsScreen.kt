package com.nextube.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nextube.app.BuildConfig
import com.nextube.app.R
import com.nextube.app.core.EngineState
import com.nextube.app.data.AppLanguage
import com.nextube.app.data.AppSettings
import com.nextube.app.data.MediaSaver
import com.nextube.app.data.ThemeMode
import com.nextube.app.ui.MainViewModel
import com.nextube.app.ui.UpdateState
import com.nextube.app.ui.components.SupportDialog
import com.nextube.app.ui.components.SupportHeroCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val theme by AppSettings.theme.collectAsStateWithLifecycle()
    val dynamicColor by AppSettings.dynamicColor.collectAsStateWithLifecycle()
    val language by AppSettings.language.collectAsStateWithLifecycle()
    val maxConcurrent by AppSettings.maxConcurrent.collectAsStateWithLifecycle()
    val autoUpdate by AppSettings.autoUpdate.collectAsStateWithLifecycle()
    val saveFolderLabel by AppSettings.saveFolderLabel.collectAsStateWithLifecycle()
    val showSponsors by AppSettings.showSponsors.collectAsStateWithLifecycle()
    val wifiOnly by AppSettings.wifiOnly.collectAsStateWithLifecycle()
    val speedLimitKbps by AppSettings.speedLimitKbps.collectAsStateWithLifecycle()
    val sponsorBlock by AppSettings.sponsorBlock.collectAsStateWithLifecycle()
    val embedSubs by AppSettings.embedSubs.collectAsStateWithLifecycle()
    val engine by vm.engine.collectAsStateWithLifecycle()
    val updating by vm.updating.collectAsStateWithLifecycle()
    val engineMessage by vm.engineMessage.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val version = remember(engine, updating) { if (engine == EngineState.Ready) vm.engineVersion() else null }
    var showSupport by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            val label = AppSettings.friendlyTreeLabel(uri)
            AppSettings.setSaveFolder(uri.toString(), label)
            Toast.makeText(context, "Save folder set to $label", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.nav_settings), fontWeight = FontWeight.Bold) })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {

            SupportHeroCard(
                onSupport = { showSupport = true },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Section(stringResource(R.string.settings_downloads))
            ListItem(
                modifier = Modifier.clickable { folderPicker.launch(null) },
                headlineContent = { Text(stringResource(R.string.save_location)) },
                supportingContent = {
                    Text(saveFolderLabel ?: "${Environment.DIRECTORY_DOWNLOADS}/${MediaSaver.FOLDER}")
                },
                leadingContent = { Icon(Icons.Rounded.Folder, null) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (saveFolderLabel != null) {
                            IconButton(
                                onClick = {
                                    AppSettings.setSaveFolder(null, null)
                                    Toast.makeText(context, "Reset to default folder", Toast.LENGTH_SHORT).show()
                                },
                            ) {
                                Icon(Icons.Rounded.RestartAlt, "Reset to default")
                            }
                        }
                        FilledTonalButton(onClick = { folderPicker.launch(null) }) { Text(stringResource(R.string.change)) }
                    }
                },
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
            ListItem(
                headlineContent = { Text(stringResource(R.string.wifi_only)) },
                supportingContent = { Text(stringResource(R.string.wifi_only_desc)) },
                leadingContent = { Icon(Icons.Rounded.Wifi, null) },
                trailingContent = { Switch(checked = wifiOnly, onCheckedChange = AppSettings::setWifiOnly) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.speed_limit)) },
                supportingContent = {
                    val limits = listOf(0 to "Max", 1024 to "1M", 2048 to "2M", 5120 to "5M")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        limits.forEachIndexed { i, (kbps, label) ->
                            SegmentedButton(
                                selected = speedLimitKbps == kbps,
                                onClick = { AppSettings.setSpeedLimitKbps(kbps) },
                                shape = SegmentedButtonDefaults.itemShape(i, limits.size),
                            ) { Text(label) }
                        }
                    }
                },
                leadingContent = { Icon(Icons.Rounded.Speed, null) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.sponsor_block)) },
                supportingContent = { Text(stringResource(R.string.sponsor_block_desc)) },
                leadingContent = { Icon(Icons.Rounded.Block, null) },
                trailingContent = { Switch(checked = sponsorBlock, onCheckedChange = AppSettings::setSponsorBlock) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.embed_subs)) },
                supportingContent = { Text(stringResource(R.string.embed_subs_desc)) },
                leadingContent = { Icon(Icons.Rounded.Subtitles, null) },
                trailingContent = { Switch(checked = embedSubs, onCheckedChange = AppSettings::setEmbedSubs) },
            )

            Section(stringResource(R.string.settings_appearance))
            ListItem(
                headlineContent = { Text(stringResource(R.string.language)) },
                supportingContent = {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        AppLanguage.entries.forEachIndexed { i, lang ->
                            SegmentedButton(
                                selected = language == lang,
                                onClick = {
                                    if (language != lang) {
                                        AppSettings.setLanguage(lang)
                                        (context as? Activity)?.recreate()
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(i, AppLanguage.entries.size),
                            ) { Text(lang.label) }
                        }
                    }
                },
                leadingContent = { Icon(Icons.Rounded.Language, null) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.theme)) },
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ListItem(
                    headlineContent = { Text("Material You dynamic colors") },
                    supportingContent = { Text("Match app colors to your phone wallpaper (Android 12+)") },
                    leadingContent = { Icon(Icons.Rounded.Palette, null) },
                    trailingContent = { Switch(checked = dynamicColor, onCheckedChange = AppSettings::setDynamicColor) },
                )
            }
            ListItem(
                headlineContent = { Text("Show sponsor cards") },
                supportingContent = { Text("Privacy-friendly, no tracking. Helps keep NexTube free.") },
                leadingContent = { Icon(Icons.Rounded.NewReleases, null) },
                trailingContent = {
                    Switch(
                        checked = showSponsors,
                        onCheckedChange = {
                            AppSettings.setShowSponsors(it)
                            vm.refreshSponsor()
                        },
                    )
                },
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
