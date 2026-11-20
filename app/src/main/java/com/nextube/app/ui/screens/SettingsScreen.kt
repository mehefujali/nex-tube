package com.nextube.app.ui.screens

import android.os.Build
import android.os.Environment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Image
import androidx.compose.material.icons.rounded.BrandingWatermark
import androidx.compose.material.icons.rounded.Place
import androidx.compose.ui.res.painterResource
import com.nextube.app.BuildConfig
import com.nextube.app.R
import com.nextube.app.data.WatermarkPosition
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
    val watermark by AppSettings.watermark.collectAsStateWithLifecycle()
    val watermarkPosition by AppSettings.watermarkPosition.collectAsStateWithLifecycle()
    val engine by vm.engine.collectAsStateWithLifecycle()
    val updating by vm.updating.collectAsStateWithLifecycle()
    val engineMessage by vm.engineMessage.collectAsStateWithLifecycle()
    val version = remember(engine, updating) { if (engine == EngineState.Ready) vm.engineVersion() else null }

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

            Section("Watermark")
            ListItem(
                headlineContent = { Text("Add NexTube logo to videos") },
                supportingContent = {
                    Text("Re-encodes each video after download, so saving takes a little longer. Audio downloads are not changed.")
                },
                leadingContent = { Icon(Icons.Rounded.BrandingWatermark, null) },
                trailingContent = { Switch(checked = watermark, onCheckedChange = AppSettings::setWatermark) },
            )
            if (watermark) {
                ListItem(
                    headlineContent = { Text("Logo position") },
                    supportingContent = {
                        Column {
                            Image(
                                painterResource(R.drawable.watermark), null,
                                Modifier.padding(top = 8.dp).height(28.dp),
                            )
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                WatermarkPosition.entries.forEachIndexed { i, p ->
                                    SegmentedButton(
                                        selected = watermarkPosition == p,
                                        onClick = { AppSettings.setWatermarkPosition(p) },
                                        shape = SegmentedButtonDefaults.itemShape(i, WatermarkPosition.entries.size),
                                    ) { Text(p.label) }
                                }
                            }
                        }
                    },
                    leadingContent = { Icon(Icons.Rounded.Place, null) },
                )
            }

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
