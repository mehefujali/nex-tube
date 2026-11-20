package com.nextube.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.nextube.app.BuildConfig
import com.nextube.app.R
import com.nextube.app.core.UpdateInfo
import com.nextube.app.ui.UpdateState

/**
 * Update popup. A mandatory update can't be dismissed (no "Later", back and outside taps ignored),
 * so the app stays blocked until the user installs the new version.
 */
@Composable
fun UpdateDialog(
    state: UpdateState,
    onUpdate: () -> Unit,
    onInstall: () -> Unit,
    onLater: () -> Unit,
) {
    val info: UpdateInfo = when (state) {
        is UpdateState.Available -> state.info
        is UpdateState.Downloading -> state.info
        is UpdateState.Ready -> state.info
        is UpdateState.Failed -> state.info ?: return
        else -> return
    }
    val mandatory = info.isMandatory

    AlertDialog(
        onDismissRequest = { if (!mandatory) onLater() },
        properties = DialogProperties(dismissOnBackPress = !mandatory, dismissOnClickOutside = !mandatory),
        icon = {
            Image(painterResource(R.drawable.logo_mark), null, Modifier.size(48.dp))
        },
        title = {
            Text(if (mandatory) "Update required" else "Update available", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    "Version ${info.versionName} is ready (you have ${BuildConfig.VERSION_NAME}).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (mandatory) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "This version no longer works correctly. Please update to keep using NexTube.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (info.changelog.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text("What's new", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        info.changelog,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()),
                    )
                }
                when (state) {
                    is UpdateState.Downloading -> {
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth().clip(CircleShape),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Downloading… ${(state.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    is UpdateState.Failed -> {
                        Spacer(Modifier.height(12.dp))
                        Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    else -> Unit
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.Downloading -> Button(onClick = {}, enabled = false) { Text("Downloading…") }
                is UpdateState.Ready -> Button(onClick = onInstall) { Text("Install") }
                is UpdateState.Failed -> Button(onClick = onUpdate) { Text("Try again") }
                else -> Button(onClick = onUpdate) { Text("Update now") }
            }
        },
        dismissButton = if (mandatory) null else {
            { TextButton(onClick = onLater) { Text(if (state is UpdateState.Downloading) "Cancel" else "Later") } }
        },
    )
}
