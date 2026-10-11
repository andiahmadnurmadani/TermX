package com.kroombox.konsol.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kroombox.konsol.KonsolApp
import com.kroombox.konsol.data.Host
import com.kroombox.konsol.ssh.TerminalSession
import com.kroombox.konsol.ssh.Tmux
import com.kroombox.konsol.ui.Nav
import com.kroombox.konsol.ui.components.AppCard
import com.kroombox.konsol.ui.components.ConfirmDialog
import com.kroombox.konsol.ui.components.DropdownField
import com.kroombox.konsol.ui.components.EmptyState
import com.kroombox.konsol.ui.components.HostAvatar
import com.kroombox.konsol.ui.components.SectionHeader
import com.kroombox.konsol.ui.components.StatusDot
import com.kroombox.konsol.ui.components.timeAgo
import com.kroombox.konsol.ui.stateColor
import com.kroombox.konsol.ui.stateLabel
import com.kroombox.konsol.ui.theme.MonoSmall
import com.kroombox.konsol.ui.theme.Palette
import kotlinx.coroutines.launch

@Composable
fun SessionsScreen(nav: Nav, modifier: Modifier = Modifier) {
    val mgr = KonsolApp.container.sessions
    val sessions by mgr.sessions.collectAsState()
    val data by KonsolApp.container.vault.data.collectAsState()
    var browseHost by remember { mutableStateOf<Host?>(data.hosts.firstOrNull { it.persistentSession }) }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Sesi", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
            Text(
                "Tab terminal yang terbuka di aplikasi, dan sesi tmux yang tetap hidup di server.",
                style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim,
            )
            SectionHeader("Terbuka di perangkat ini")
        }
        if (sessions.isEmpty()) {
            item {
                AppCard {
                    Text("Belum ada tab terminal. Ketuk host untuk mulai.", color = Palette.TextDim)
                }
            }
        }
        items(sessions, key = { it.id }) { s -> ActiveSessionRow(s, nav) }

        item {
            SectionHeader("Sesi tmux di server")
            if (data.hosts.isEmpty()) {
                AppCard { Text("Tambahkan host terlebih dulu.", color = Palette.TextDim) }
            } else {
                DropdownField(
                    label = "Host",
                    options = data.hosts,
                    selected = browseHost,
                    display = { it?.let { h -> "${h.label} · ${h.address}" } ?: "Pilih host" },
                    onSelect = { browseHost = it },
                )
            }
        }
        item {
            browseHost?.let { h -> RemoteTmuxPanel(h, nav) }
        }
    }
}

@Composable
private fun ActiveSessionRow(s: TerminalSession, nav: Nav) {
    val state by s.state.collectAsState()
    val title by s.title.collectAsState()
    AppCard(onClick = { nav.terminal(s.id) }, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HostAvatar(s.host.label, s.host.colorIndex, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.host.label, style = MaterialTheme.typography.titleMedium)
                Text(s.tmuxName?.let { "tmux: $it" } ?: title, style = MonoSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(stateColor(state))
                    Spacer(Modifier.width(6.dp))
                    Text(stateLabel(state), style = MaterialTheme.typography.labelMedium, color = stateColor(state))
                }
            }
            IconButton(onClick = { KonsolApp.container.sessions.closeTerminal(s.id) }) {
                Icon(Icons.Rounded.Close, "Tutup tab", tint = Palette.TextDim)
            }
        }
    }
}

@Composable
private fun RemoteTmuxPanel(host: Host, nav: Nav) {
    val mgr = KonsolApp.container.sessions
    val scope = rememberCoroutineScope()
    var loading by remember(host.id) { mutableStateOf(false) }
    var error by remember(host.id) { mutableStateOf<String?>(null) }
    var list by remember(host.id) { mutableStateOf<List<Tmux.RemoteSession>?>(null) }
    var noTmux by remember(host.id) { mutableStateOf(false) }
    var newName by remember { mutableStateOf<String?>(null) }
    var killing by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            try {
                val r = mgr.listTmux(host.id)
                noTmux = r == null
                list = r ?: emptyList()
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                loading = false
            }
        }
    }

    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = { refresh() }, enabled = !loading) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (list == null) "Muat sesi" else "Segarkan")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { newName = "" }) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Sesi baru")
            }
            if (loading) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
        error?.let { Text(it, color = Palette.Danger, style = MaterialTheme.typography.bodySmall) }
        if (noTmux) {
            AppCard {
                Text("tmux belum terpasang di server ini.", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(Tmux.installHint(), style = MonoSmall)
            }
        }
        val l = list
        if (l != null && l.isEmpty() && !noTmux) {
            Box(Modifier.height(180.dp)) {
                EmptyState(Icons.Rounded.Layers, "Tidak ada sesi tmux", "Sesi persisten akan dibuat otomatis saat Anda konek.")
            }
        }
        l?.forEach { rs ->
            AppCard(onClick = { nav.connect(host.id, tmux = rs.name) }, contentPadding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Layers, null, tint = Palette.Accent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(rs.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${rs.windows} window · ${if (rs.attachedClients > 0) "${rs.attachedClients} terhubung" else "terlepas"} · aktif ${timeAgo(rs.activityEpoch * 1000)}",
                            style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
                        )
                    }
                    IconButton(onClick = { nav.connect(host.id, tmux = rs.name) }) { Icon(Icons.Rounded.Terminal, "Attach") }
                    IconButton(onClick = { killing = rs.name }) { Icon(Icons.Rounded.Delete, "Akhiri", tint = Palette.Danger) }
                }
            }
        }
    }

    newName?.let { current ->
        var name by remember { mutableStateOf(current) }
        AlertDialog(
            onDismissRequest = { newName = null },
            title = { Text("Sesi tmux baru") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text("Nama sesi") }, singleLine = true)
                    Spacer(Modifier.height(6.dp))
                    Text("Contoh: claude, build, logs", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    newName = null
                    nav.connect(host.id, tmux = Tmux.sanitize(name))
                }) { Text("Buat & buka") }
            },
            dismissButton = { TextButton(onClick = { newName = null }) { Text("Batal") } },
        )
    }

    killing?.let { name ->
        ConfirmDialog(
            title = "Akhiri sesi \"$name\"?",
            message = "Semua proses di dalam sesi tmux ini akan dihentikan di server.",
            confirmText = "Akhiri", danger = true,
            onConfirm = {
                scope.launch {
                    try { mgr.killTmux(host.id, name) } catch (e: Exception) { error = e.message }
                    refresh()
                }
            },
            onDismiss = { killing = null },
        )
    }
}
