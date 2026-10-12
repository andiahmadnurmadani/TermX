package com.kroombox.termx.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import com.kroombox.termx.ssh.Sudo
import com.kroombox.termx.ui.components.IconPicker
import com.kroombox.termx.ui.components.SpecAvatar
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kroombox.termx.TermXApp
import com.kroombox.termx.data.Snippet
import com.kroombox.termx.ssh.SessionState
import com.kroombox.termx.ui.components.AppCard
import com.kroombox.termx.ui.components.ConfirmDialog
import com.kroombox.termx.ui.components.EmptyState
import com.kroombox.termx.ui.components.LabeledSwitch
import com.kroombox.termx.ui.theme.Mono
import com.kroombox.termx.ui.theme.Palette

@Composable
fun SnippetsScreen(modifier: Modifier = Modifier) {
    val vault = TermXApp.container.vault
    val data by vault.data.collectAsState()
    var editing by remember { mutableStateOf<Snippet?>(null) }
    var creating by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("Snippet", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Kelola daftar perintah di sini. Untuk menjalankannya, buka terminal lalu ketuk ⚡ (di bar atas atau bar tombol bawah). Snippet bertanda sudo mengetik password sudo otomatis saat diminta.",
                    style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }
            if (data.snippets.isEmpty()) {
                item { Box(Modifier.height(400.dp)) { EmptyState(Icons.Rounded.Bolt, "Belum ada snippet", "Simpan perintah favorit Anda.") } }
            }
            items(data.snippets, key = { it.id }) { sn -> SnippetCard(sn, onEdit = { editing = sn }) }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("Snippet") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
    }

    if (creating || editing != null) {
        SnippetEditor(
            initial = editing,
            onSave = { vault.saveSnippet(it); creating = false; editing = null },
            onDismiss = { creating = false; editing = null },
        )
    }
}

@Composable
private fun SnippetCard(sn: Snippet, onEdit: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    AppCard(onClick = onEdit, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SpecAvatar(sn.icon, sn.name, Palette.Warn, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sn.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                    if (sn.useSudo) {
                        Spacer(Modifier.width(6.dp))
                        Tag("sudo", Palette.Danger)
                    }
                }
                if (sn.description.isNotBlank()) Text(sn.description, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                Spacer(Modifier.height(4.dp))
                Text(sn.command, fontFamily = Mono, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Hapus", tint = Palette.TextDim) }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Hapus snippet?", message = "\"${sn.name}\" akan dihapus.", confirmText = "Hapus", danger = true,
            onConfirm = { TermXApp.container.vault.deleteSnippet(sn.id) }, onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun SnippetEditor(initial: Snippet?, onSave: (Snippet) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var command by remember { mutableStateOf(initial?.command ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var autoRun by remember { mutableStateOf(initial?.autoRun ?: true) }
    var useSudo by remember { mutableStateOf(initial?.useSudo ?: false) }
    var icon by remember { mutableStateOf(initial?.icon ?: "mi:bolt") }
    var pickIcon by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Snippet baru" else "Edit snippet") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(RoundedCornerShape(14.dp)).clickable { pickIcon = !pickIcon }) {
                        SpecAvatar(icon, name.ifBlank { "?" }, Palette.Warn, size = 52.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(name, { name = it }, label = { Text("Nama") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                TextButton(onClick = { pickIcon = !pickIcon }) { Text(if (pickIcon) "Tutup pilihan ikon" else "Ganti ikon") }
                if (pickIcon) {
                    IconPicker(selected = icon, onSelect = { icon = it }, accentColor = Palette.Warn, allowAuto = false)
                }
                OutlinedTextField(
                    command, { command = it }, label = { Text("Perintah") }, minLines = 3,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(description, { description = it }, label = { Text("Deskripsi (opsional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LabeledSwitch("Tekan Enter otomatis", null, autoRun) { autoRun = it }
                LabeledSwitch(
                    "Jalankan dengan sudo",
                    "Untuk perintah yang butuh root (docker, apt, journalctl…). Password sudo host diketik otomatis saat diminta.",
                    useSudo,
                ) { useSudo = it }
                if (useSudo && command.isNotBlank()) {
                    Text("Dikirim: ${Sudo.wrap(command)}", fontFamily = Mono, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && command.isNotBlank(), onClick = {
                val base = initial ?: Snippet(name = "", command = "")
                onSave(
                    base.copy(
                        name = name.trim(), command = command, description = description.trim(), autoRun = autoRun,
                        useSudo = useSudo, icon = icon,
                    ),
                )
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
