package com.nextube.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nextube.app.ui.components.UpdateDialog
import com.nextube.app.ui.screens.DownloadsScreen
import com.nextube.app.ui.screens.FormatSheet
import com.nextube.app.ui.screens.HomeScreen
import com.nextube.app.ui.screens.PlaylistSheet
import com.nextube.app.ui.screens.SettingsScreen

private data class Dest(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

@Composable
fun AppRoot(vm: MainViewModel, tab: Int, onTabChange: (Int) -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val sheet by vm.sheet.collectAsStateWithLifecycle()
    val playlistSheet by vm.playlistSheet.collectAsStateWithLifecycle()
    val activeCount = tasks.count { it.status.isActive }

    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            val res = snackbar.showSnackbar(
                message = msg.text,
                actionLabel = msg.actionLabel,
                duration = SnackbarDuration.Short,
            )
            if (res == SnackbarResult.ActionPerformed && msg.openDownloads) {
                onTabChange(1)
            }
        }
    }

    val dests = listOf(
        Dest("Home", Icons.Outlined.Home, Icons.Rounded.Home),
        Dest("Downloads", Icons.Outlined.Download, Icons.Rounded.Download),
        Dest("Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                dests.forEachIndexed { i, d ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { onTabChange(i) },
                        label = { Text(d.label) },
                        icon = {
                            BadgedBox(badge = { if (i == 1 && activeCount > 0) Badge { Text("$activeCount") } }) {
                                Icon(if (tab == i) d.selectedIcon else d.icon, contentDescription = d.label)
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
        ) {
            when (tab) {
                0 -> HomeScreen(vm)
                1 -> DownloadsScreen(vm, onBrowse = { onTabChange(0) })
                else -> SettingsScreen(vm)
            }
        }
    }

    sheet?.let { FormatSheet(state = it, vm = vm) }
    playlistSheet?.let { PlaylistSheet(state = it, vm = vm) }

    val update by vm.update.collectAsStateWithLifecycle()
    val updateDismissed by vm.updateDismissed.collectAsStateWithLifecycle()
    val mandatory = when (val u = update) {
        is UpdateState.Available -> u.info.isMandatory
        is UpdateState.Downloading -> u.info.isMandatory
        is UpdateState.Ready -> u.info.isMandatory
        is UpdateState.Failed -> u.info?.isMandatory == true
        else -> false
    }
    if (mandatory || !updateDismissed) {
        UpdateDialog(
            state = update,
            onUpdate = vm::downloadUpdate,
            onInstall = vm::install,
            onLater = vm::dismissUpdate,
        )
    }
}
