package com.kroombox.termx.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kroombox.termx.BuildConfig
import com.kroombox.termx.R
import com.kroombox.termx.TermXApp
import com.kroombox.termx.ui.components.LabeledSwitch
import com.kroombox.termx.ui.theme.Palette
import com.kroombox.termx.update.UpdateState

/** Dialog "versi baru tersedia": catatan rilis, unduh dengan progres, lalu installer. */
@Composable
fun UpdatePrompt() {
    val updater = TermXApp.container.updater
    val release by updater.prompt.collectAsState()
    val state by updater.state.collectAsState()
    val r = release ?: return
    val context = LocalContext.current
    val downloading = state as? UpdateState.Downloading
    val ready = (state as? UpdateState.Ready)?.takeIf { it.release == r }
    val failed = (state as? UpdateState.Failed)?.takeIf { it.release == r }

    AlertDialog(
        onDismissRequest = { if (downloading == null) updater.dismiss(r) else updater.prompt.value = null },
        title = { Text(stringResource(R.string.update_available_title, r.version)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.update_current_version, updater.currentVersion),
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
                )
                if (r.notes.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        releaseNotes(r.notes),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                    )
                }
                when {
                    downloading != null -> {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(progress = { downloading.progress }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.update_downloading, (downloading.progress * 100).toInt()),
                            style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
                        )
                    }
                    failed != null -> {
                        Spacer(Modifier.height(12.dp))
                        Text(failed.message, style = MaterialTheme.typography.bodySmall, color = Palette.Danger)
                    }
                    ready != null -> {
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.update_ready), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = downloading == null,
                onClick = {
                    if (BuildConfig.DEBUG) {
                        // Build debug memakai kunci lain: arahkan ke halaman rilis saja.
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.pageUrl)))
                    } else {
                        updater.downloadAndInstall(r)
                    }
                },
            ) {
                Text(
                    stringResource(
                        when {
                            downloading != null -> R.string.update_downloading_short
                            ready != null -> R.string.update_install
                            failed != null -> R.string.update_retry
                            else -> R.string.update_now
                        },
                    ),
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { if (downloading == null) updater.dismiss(r) else updater.prompt.value = null }) {
                Text(stringResource(if (downloading == null) R.string.update_later else R.string.update_hide))
            }
        },
    )
}

/** Isi kartu About: versi, status pembaruan, tombol periksa, dan sakelar cek otomatis. */
@Composable
fun UpdateSection() {
    val updater = TermXApp.container.updater
    val vault = TermXApp.container.vault
    val state by updater.state.collectAsState()
    val data by vault.data.collectAsState()
    val status = when (val s = state) {
        UpdateState.Checking -> stringResource(R.string.update_checking)
        UpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
        is UpdateState.Available -> stringResource(R.string.update_available_title, s.release.version)
        is UpdateState.Downloading -> stringResource(R.string.update_downloading, (s.progress * 100).toInt())
        is UpdateState.Ready -> stringResource(R.string.update_ready)
        is UpdateState.Failed -> s.message
        UpdateState.Idle -> null
    }
    status?.let {
        Spacer(Modifier.height(8.dp))
        Text(
            it, style = MaterialTheme.typography.bodySmall,
            color = if (state is UpdateState.Failed) Palette.Danger else Palette.TextDim,
        )
    }
    val release = when (val s = state) {
        is UpdateState.Available -> s.release
        is UpdateState.Ready -> s.release
        is UpdateState.Failed -> s.release
        else -> null
    }
    if (release != null) {
        TextButton(onClick = { updater.prompt.value = release }) { Text(stringResource(R.string.update_view, release.version)) }
    } else {
        TextButton(enabled = state != UpdateState.Checking && state !is UpdateState.Downloading, onClick = { updater.checkInBackground() }) {
            Text(stringResource(R.string.update_check))
        }
    }
    LabeledSwitch(
        stringResource(R.string.update_auto),
        stringResource(R.string.update_auto_desc),
        data.settings.autoUpdateCheck,
    ) { v -> vault.updateSettings { it.copy(autoUpdateCheck = v) } }
}

/** Markdown CHANGELOG jadi teks biasa yang enak dibaca di dialog. */
private fun releaseNotes(md: String): String = md.lineSequence()
    .map { it.removePrefix("### ").replace("**", "").replace("`", "") }
    .joinToString("\n")
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()
