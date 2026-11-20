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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nextube.app.ui.screens.DownloadsScreen
import com.nextube.app.ui.screens.FormatSheet
import com.nextube.app.ui.screens.HomeScreen
import com.nextube.app.ui.screens.SettingsScreen

private data class Dest(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

@Composable
fun AppRoot(vm: MainViewModel, tab: Int, onTabChange: (Int) -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val sheet by vm.sheet.collectAsStateWithLifecycle()
    val activeCount = tasks.count { it.status.isActive }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
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
}
