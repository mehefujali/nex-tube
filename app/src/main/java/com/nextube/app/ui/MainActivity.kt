package com.nextube.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nextube.app.data.AppSettings
import com.nextube.app.ui.theme.NexTubeTheme
import com.nextube.app.util.Fmt

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_DOWNLOADS = "open_downloads"
    }

    private val vm: MainViewModel by viewModels()
    private val tab = mutableIntStateOf(0)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestPermissionsIfNeeded()
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val theme by AppSettings.theme.collectAsStateWithLifecycle()
            NexTubeTheme(theme) {
                AppRoot(vm = vm, tab = tab.intValue, onTabChange = { tab.intValue = it })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onAppResumed()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Handles "Share → NexTube", opened YouTube links and taps on the download notification. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        if (intent.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false)) {
            tab.intValue = 1
            return
        }
        val url = when (intent.action) {
            Intent.ACTION_SEND -> Fmt.extractUrl(intent.getStringExtra(Intent.EXTRA_TEXT))
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        } ?: return
        tab.intValue = 0
        vm.openUrl(url)
    }

    private fun requestPermissionsIfNeeded() {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) permissionLauncher.launch(wanted.toTypedArray())
    }
}
