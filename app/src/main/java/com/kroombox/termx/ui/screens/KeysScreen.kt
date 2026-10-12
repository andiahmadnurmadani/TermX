package com.kroombox.termx.ui.screens

import com.kroombox.termx.i18n.S
import com.kroombox.termx.R
import androidx.compose.ui.res.stringResource
import com.kroombox.termx.ui.components.timeAgo
import com.kroombox.termx.ui.components.HostAvatar
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
                Text(stringResource(R.string.keys_title), style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.keys_subtitle),
                    style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }
            if (data.keys.isEmpty()) {
                item {
                    Box(Modifier.height(400.dp)) {
                        EmptyState(Icons.Rounded.VpnKey, stringResource(R.string.keys_empty_title), stringResource(R.string.keys_empty_message))
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
                            val names = k.installedOn.mapNotNull { i -> data.hosts.firstOrNull { it.id == i.hostId }?.label }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (names.isEmpty()) stringResource(R.string.keys_not_installed_anywhere)
                                else stringResource(R.string.keys_installed_on_count, names.size) + " " +
                                    names.take(3).joinToString(", ") + if (names.size > 3) ", …" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (names.isEmpty()) Palette.TextDim else Palette.Accent,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            ExtendedFloatingActionButton(
                onClick = { fabMenu = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text(stringResource(R.string.keys_fab)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
            DropdownMenu(expanded = fabMenu, onDismissRequest = { fabMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.keys_generate_new)) }, leadingIcon = { Icon(Icons.Rounded.VpnKey, null) },
                    onClick = { fabMenu = false; generating = true })
                DropdownMenuItem(text = { Text(stringResource(R.string.keys_import_private)) }, leadingIcon = { Icon(Icons.Rounded.FolderOpen, null) },
                    onClick = { fabMenu = false; importing = true })
            }
        }
    }

    if (generating) GenerateKeyDialog(onDismiss = { generating = false })
    if (importing) ImportKeyDialog(onDismiss = { importing = false })
    detail?.let { k ->
        ModalBottomSheet(onDismissRequest = { detail = null }, containerColor = Palette.Surface) {
            KeyDetail(data.keys.firstOrNull { it.id == k.id } ?: k, hosts = data.hosts, onDeleted = { detail = null })
        }
    }
}

@Composable
private fun GenerateKeyDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val defaultName = stringResource(R.string.keys_default_name_phone)
    var name by remember { mutableStateOf(defaultName) }
    var type by remember { mutableStateOf(KeyManager.GenType.ED25519) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.keys_generate_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.keys_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.keys_type), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KeyManager.GenType.entries.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(stringResource(t.label).substringBefore(" (")) })
                    }
                }
                if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.keys_generating))
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
            }) { Text(stringResource(R.string.keys_generate)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.keys_cancel)) } },
    )
}

@Composable
private fun ImportKeyDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val defaultImportName = stringResource(R.string.keys_default_name_import)
    var name by remember { mutableStateOf(defaultImportName) }
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
        title = { Text(stringResource(R.string.keys_import_private)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.keys_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    pem, { pem = it }, label = { Text(stringResource(R.string.keys_paste_private)) },
                    placeholder = { Text("-----BEGIN OPENSSH PRIVATE KEY-----") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    minLines = 4, maxLines = 8, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.keys_choose_file))
                }
                PasswordField(passphrase, { passphrase = it }, stringResource(R.string.keys_passphrase_optional))
                Text(stringResource(R.string.keys_supported_formats), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
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
                            KeyManager.import(name.trim().ifBlank { defaultImportName }, pem, passphrase.ifEmpty { null })
                        }
                        TermXApp.container.vault.saveKey(k)
                        onDismiss()
                    } catch (e: Exception) {
                        error = S.get(R.string.keys_err_read_key, e.message ?: e.javaClass.simpleName)
                    } finally {
                        busy = false
                    }
                }
            }) { Text(stringResource(R.string.keys_import)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.keys_cancel)) } },
    )
}

@Composable
private fun KeyDetail(k: SshKey, hosts: List<Host>, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf<Host?>(null) }
    var installMsg by remember { mutableStateOf<String?>(null) }
    var installErr by remember { mutableStateOf(false) }
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
        val shareTitle = stringResource(R.string.keys_share_public_key)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { copy(context, k.publicKey) }) {
                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.keys_copy_public))
            }
            OutlinedButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, k.publicKey)
                context.startActivity(Intent.createChooser(send, shareTitle))
            }) {
                Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.keys_share))
            }
        }

        Text(stringResource(R.string.keys_install_to_server), style = MaterialTheme.typography.titleSmall)
        val pickHost = stringResource(R.string.keys_pick_host)
        DropdownField(
            label = stringResource(R.string.keys_target_host),
            options = hosts,
            selected = target,
            display = { it?.let { h -> "${h.label} · ${h.address}" } ?: pickHost },
            onSelect = { target = it },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(enabled = target != null && !installing, onClick = {
                val h = target ?: return@Button
                installing = true
                installMsg = null
                installErr = false
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
                                if (r.stdout.contains("ADDED") || r.stdout.contains("EXISTS")) {
                                    TermXApp.container.vault.markKeyInstalled(k.id, h.id)
                                }
                                when {
                                    r.stdout.contains("ADDED") -> S.get(R.string.keys_install_added, h.label)
                                    r.stdout.contains("EXISTS") -> S.get(R.string.keys_install_exists, h.label)
                                    else -> { installErr = true; S.get(R.string.keys_failed, r.stderr.ifBlank { r.stdout }.take(200)) }
                                }
                            } finally {
                                mgr.release(h.id, "install-key")
                            }
                        }
                    } catch (e: Exception) {
                        installErr = true
                        S.get(R.string.keys_failed, e.message ?: e.javaClass.simpleName)
                    }
                    installing = false
                }
            }) {
                Icon(Icons.Rounded.FileUpload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.keys_install))
            }
            if (installing) {
                Spacer(Modifier.width(12.dp))
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
        installMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (installErr) Palette.Danger else Palette.Accent) }

        InstalledOnSection(k, hosts)

        TextButton(onClick = { confirmDelete = true }) {
            Icon(Icons.Rounded.Delete, null, tint = Palette.Danger); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.keys_delete_key), color = Palette.Danger)
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.keys_delete_confirm_title),
            message = stringResource(R.string.keys_delete_confirm_message),
            confirmText = stringResource(R.string.keys_delete), danger = true,
            onConfirm = { TermXApp.container.vault.deleteKey(k.id); onDeleted() },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Daftar server tempat kunci ini terpasang, dengan cek ulang dan lepas dari server. */
@Composable
private fun InstalledOnSection(k: SshKey, hosts: List<Host>) {
    val scope = rememberCoroutineScope()
    val vault = TermXApp.container.vault
    var busy by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var msgErr by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Host?>(null) }
    // Dicocokkan dari tipe + blob kunci (tanpa komentar), jadi kunci yang dipasang manual pun dikenali.
    val line = k.publicKey.replace("'", "").trim().split(Regex("\\s+")).take(2).joinToString(" ")
    val usedBy = hosts.filter { it.authType == com.kroombox.termx.data.AuthType.KEY && it.keyId == k.id }
    val installed = k.installedOn.mapNotNull { i -> hosts.firstOrNull { it.id == i.hostId }?.let { it to i } }
        .sortedByDescending { it.second.at }

    suspend fun run(h: Host, script: String): String = withContext(Dispatchers.IO) {
        val mgr = TermXApp.container.sessions
        val conn = mgr.connectionFor(h.id)
        mgr.acquire(h.id, "key-check")
        try { conn.exec("sh -s", stdin = script).stdout } finally { mgr.release(h.id, "key-check") }
    }

    fun check(h: Host) {
        busy = h.id
        scope.launch {
            msgErr = false
            msg = try {
                val out = run(h, "grep -qF '$line' \"\$HOME/.ssh/authorized_keys\" 2>/dev/null && echo YES || echo NO")
                if (out.contains("YES")) { vault.markKeyInstalled(k.id, h.id); S.get(R.string.keys_check_still_installed, h.label) }
                else { vault.unmarkKeyInstalled(k.id, h.id); S.get(R.string.keys_check_not_found, h.label) }
            } catch (e: Exception) { msgErr = true; S.get(R.string.keys_check_failed, h.label, e.message ?: e.javaClass.simpleName) }
            busy = null
        }
    }

    Text(stringResource(R.string.keys_installed_on_servers), style = MaterialTheme.typography.titleSmall)
    if (installed.isEmpty()) {
        Text(
            stringResource(R.string.keys_installed_none),
            style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
        )
    }
    installed.forEach { (h, inst) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            HostAvatar(h, size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(h.label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    "${h.address} · ${timeAgo(inst.at)}" + if (h in usedBy) " · " + stringResource(R.string.keys_used_for_login) else "",
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, maxLines = 1,
                )
            }
            if (busy == h.id) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = { check(h) }) { Text(stringResource(R.string.keys_check)) }
                TextButton(onClick = { removing = h }) { Text(stringResource(R.string.keys_remove), color = Palette.Danger) }
            }
        }
    }
    val notListed = usedBy.filter { u -> installed.none { it.first.id == u.id } }
    if (notListed.isNotEmpty()) {
        Text(
            stringResource(R.string.keys_used_by_not_listed, notListed.joinToString(", ") { it.label }),
            style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
        )
    }
    msg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (msgErr) Palette.Danger else Palette.Accent) }

    removing?.let { h ->
        ConfirmDialog(
            title = stringResource(R.string.keys_remove_confirm_title, h.label),
            message = stringResource(R.string.keys_remove_confirm_message, h.label) +
                (if (h in usedBy) " " + stringResource(R.string.keys_remove_confirm_used) else ""),
            confirmText = stringResource(R.string.keys_remove), danger = true,
            onConfirm = {
                busy = h.id
                scope.launch {
                    msgErr = false
                    msg = try {
                        val out = run(
                            h,
                            "f=\"\$HOME/.ssh/authorized_keys\"; [ -f \"\$f\" ] || { echo REMOVED; exit 0; }; " +
                                "grep -vF '$line' \"\$f\" > \"\$f.termx.tmp\"; cat \"\$f.termx.tmp\" > \"\$f\" && rm -f \"\$f.termx.tmp\" && echo REMOVED",
                        )
                        if (out.contains("REMOVED")) { vault.unmarkKeyInstalled(k.id, h.id); S.get(R.string.keys_removed_from, h.label) }
                        else { msgErr = true; S.get(R.string.keys_remove_failed, h.label) }
                    } catch (e: Exception) { msgErr = true; S.get(R.string.keys_failed, e.message ?: e.javaClass.simpleName) }
                    busy = null
                }
            },
            onDismiss = { removing = null },
        )
    }
}
