package com.kroombox.termx.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.kroombox.termx.TermXApp
import com.kroombox.termx.data.Host
import com.kroombox.termx.data.KeyManager
import com.kroombox.termx.data.SshKey
import com.kroombox.termx.ui.components.AppCard
import com.kroombox.termx.ui.components.ConfirmDialog
import com.kroombox.termx.ui.components.DropdownField
import com.kroombox.termx.ui.components.EmptyState
import com.kroombox.termx.ui.components.PasswordField
import com.kroombox.termx.ui.theme.Mono
import com.kroombox.termx.ui.theme.MonoSmall
import com.kroombox.termx.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeysScreen(modifier: Modifier = Modifier) {
    val vault = TermXApp.container.vault
    val data by vault.data.collectAsState()
    var fabMenu by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<SshKey?>(null) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("Keychain", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Kunci SSH disimpan terenkripsi dengan Android Keystore.",
                    style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }
            if (data.keys.isEmpty()) {
                item {
                    Box(Modifier.height(400.dp)) {
                        EmptyState(Icons.Rounded.VpnKey, "Belum ada kunci", "Buat kunci Ed25519 baru atau impor kunci yang sudah ada.")
                    }
                }
            }
            items(data.keys, key = { it.id }) { k ->
                AppCard(onClick = { detail = k }, contentPadding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.VpnKey, null, tint = Palette.Accent)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(k.name, style = MaterialTheme.typography.titleMedium)
                            Text(k.type, style = MaterialTheme.typography.labelMedium, color = Palette.Blue)
                            Text(k.fingerprint, style = MonoSmall, maxLines = 1)
                        }
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            ExtendedFloatingActionButton(
                onClick = { fabMenu = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Kunci") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
            DropdownMenu(expanded = fabMenu, onDismissRequest = { fabMenu = false }) {
                DropdownMenuItem(text = { Text("Buat kunci baru") }, leadingIcon = { Icon(Icons.Rounded.VpnKey, null) },
                    onClick = { fabMenu = false; generating = true })
                DropdownMenuItem(text = { Text("Impor kunci privat") }, leadingIcon = { Icon(Icons.Rounded.FolderOpen, null) },
                    onClick = { fabMenu = false; importing = true })
            }
        }
    }

    if (generating) GenerateKeyDialog(onDismiss = { generating = false })
    if (importing) ImportKeyDialog(onDismiss = { importing = false })
    detail?.let { k ->
        ModalBottomSheet(onDismissRequest = { detail = null }, containerColor = Palette.Surface) {
            KeyDetail(k, hosts = data.hosts, onDeleted = { detail = null })
        }
    }
}

@Composable
private fun GenerateKeyDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("Kunci HP") }
    var type by remember { mutableStateOf(KeyManager.GenType.ED25519) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Buat kunci baru") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nama") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Tipe", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KeyManager.GenType.entries.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label.substringBefore(" (")) })
                    }
                }
                if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Membuat kunci…")
                }
                error?.let { Text(it, color = Palette.Danger) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && name.isNotBlank(), onClick = {
                busy = true
                scope.launch {
                    try {
                        val comment = name.trim().replace(Regex("[^A-Za-z0-9@._-]"), "_") + "@termx"
                        val k = withContext(Dispatchers.Default) { KeyManager.generate(name.trim(), type, comment) }
                        TermXApp.container.vault.saveKey(k)
                        onDismiss()
                    } catch (e: Exception) {
                        error = e.message ?: e.javaClass.simpleName
                    } finally {
                        busy = false
                    }
                }
            }) { Text("Buat") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun ImportKeyDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("Kunci impor") }
    var pem by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { pem = it.readBytes().toString(Charsets.UTF_8) }
            } catch (e: Exception) {
                error = e.message
            }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Impor kunci privat") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nama") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    pem, { pem = it }, label = { Text("Tempel kunci privat") },
                    placeholder = { Text("-----BEGIN OPENSSH PRIVATE KEY-----") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    minLines = 4, maxLines = 8, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Pilih file")
                }
                PasswordField(passphrase, { passphrase = it }, "Passphrase (bila ada)")
                Text("Didukung: OpenSSH, PEM (PKCS#1/#8), PuTTY .ppk", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                error?.let { Text(it, color = Palette.Danger) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && (pem.contains("PRIVATE KEY") || pem.contains("PuTTY")), onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        val k = withContext(Dispatchers.Default) {
                            KeyManager.import(name.trim().ifBlank { "Kunci impor" }, pem, passphrase.ifEmpty { null })
                        }
                        TermXApp.container.vault.saveKey(k)
                        onDismiss()
                    } catch (e: Exception) {
                        error = "Gagal membaca kunci: ${e.message ?: e.javaClass.simpleName}"
                    } finally {
                        busy = false
                    }
                }
            }) { Text("Impor") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun KeyDetail(k: SshKey, hosts: List<Host>, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf<Host?>(null) }
    var installMsg by remember { mutableStateOf<String?>(null) }
    var installing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(k.name, style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Fingerprint, null, tint = Palette.TextDim, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(k.fingerprint, style = MonoSmall)
        }
        AppCard(contentPadding = PaddingValues(12.dp)) {
            SelectionContainer {
                Text(k.publicKey, fontFamily = Mono, style = MaterialTheme.typography.bodySmall, modifier = Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState()))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { copy(context, k.publicKey) }) {
                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Salin publik")
            }
            OutlinedButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, k.publicKey)
                context.startActivity(Intent.createChooser(send, "Bagikan public key"))
            }) {
                Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Bagikan")
            }
        }

        Text("Pasang ke server (authorized_keys)", style = MaterialTheme.typography.titleSmall)
        DropdownField(
            label = "Host tujuan",
            options = hosts,
            selected = target,
            display = { it?.let { h -> "${h.label} · ${h.address}" } ?: "Pilih host" },
            onSelect = { target = it },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(enabled = target != null && !installing, onClick = {
                val h = target ?: return@Button
                installing = true
                installMsg = null
                scope.launch {
                    installMsg = try {
                        withContext(Dispatchers.IO) {
                            val mgr = TermXApp.container.sessions
                            val conn = mgr.connectionFor(h.id)
                            mgr.acquire(h.id, "install-key")
                            try {
                                val line = k.publicKey.replace("'", "")
                                val script = "umask 077; mkdir -p \"\$HOME/.ssh\"; touch \"\$HOME/.ssh/authorized_keys\"; " +
                                    "grep -qxF '$line' \"\$HOME/.ssh/authorized_keys\" && echo EXISTS || { echo '$line' >> \"\$HOME/.ssh/authorized_keys\" && echo ADDED; }"
                                val r = conn.exec("sh -s", stdin = script)
                                when {
                                    r.stdout.contains("ADDED") -> "Berhasil dipasang di ${h.label}. Sekarang host bisa diubah ke autentikasi kunci."
                                    r.stdout.contains("EXISTS") -> "Kunci sudah ada di ${h.label}."
                                    else -> "Gagal: ${r.stderr.ifBlank { r.stdout }.take(200)}"
                                }
                            } finally {
                                mgr.release(h.id, "install-key")
                            }
                        }
                    } catch (e: Exception) {
                        "Gagal: ${e.message}"
                    }
                    installing = false
                }
            }) {
                Icon(Icons.Rounded.FileUpload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Pasang")
            }
            if (installing) {
                Spacer(Modifier.width(12.dp))
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
        installMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (it.startsWith("Gagal")) Palette.Danger else Palette.Accent) }

        TextButton(onClick = { confirmDelete = true }) {
            Icon(Icons.Rounded.Delete, null, tint = Palette.Danger); Spacer(Modifier.width(6.dp)); Text("Hapus kunci", color = Palette.Danger)
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Hapus kunci?",
            message = "Host yang memakai kunci ini akan kembali ke autentikasi password. Kunci privat tidak bisa dipulihkan.",
            confirmText = "Hapus", danger = true,
            onConfirm = { TermXApp.container.vault.deleteKey(k.id); onDeleted() },
            onDismiss = { confirmDelete = false },
        )
    }
}
