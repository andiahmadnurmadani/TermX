package com.kroombox.termx.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kroombox.termx.TermXApp
import com.kroombox.termx.data.Host
import com.kroombox.termx.data.SavedSession
import com.kroombox.termx.ssh.SessionState
import com.kroombox.termx.ssh.TerminalSession
import com.kroombox.termx.ssh.Tmux
import com.kroombox.termx.ui.Nav
import com.kroombox.termx.ui.components.AppCard
import com.kroombox.termx.ui.components.ConfirmDialog
import com.kroombox.termx.ui.components.EmptyState
import com.kroombox.termx.ui.components.HostAvatar
import com.kroombox.termx.ui.components.StatusDot
import com.kroombox.termx.ui.components.timeAgo
import com.kroombox.termx.ui.stateColor
import com.kroombox.termx.ui.stateLabel
import com.kroombox.termx.ui.theme.MonoSmall
import com.kroombox.termx.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * Tab Sesi: satu baris per host yang bisa dibuka-tutup. Di dalamnya ada tab terminal yang sedang
 * terbuka, sesi tmux yang hidup di server (dimuat saat dibuka), dan tab yang baru ditutup. Tidak ada
 * yang perlu dibuat di sini — terminal dibuka langsung dari host atau dari sesi yang sudah ada.
 */
@Composable
fun SessionsScreen(nav: Nav, modifier: Modifier = Modifier) {
    val mgr = TermXApp.container.sessions
    val sessions by mgr.sessions.collectAsState()
    val stateVersion by mgr.stateVersion.collectAsState()
    val data by TermXApp.container.vault.data.collectAsState()

    // Host dengan tab terbuka di atas, sisanya menurut terakhir dipakai.
    val hosts = remember(data.hosts, sessions, stateVersion) {
        val open = sessions.map { it.host.id }.toSet()
        data.hosts.sortedWith(
            compareByDescending<Host> { it.id in open }.thenByDescending { it.lastConnectedAt }.thenBy { it.label.lowercase() },
        )
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Sesi", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
            Text(
                "Pilih host untuk melihat tab yang terbuka, sesi tmux di server, dan tab yang baru ditutup.",
                style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        if (hosts.isEmpty()) {
            item {
                Column(Modifier.height(360.dp)) {
                    EmptyState(Icons.Rounded.Dns, "Belum ada host", "Tambahkan host di tab Host untuk mulai.")
                }
            }
        }
        items(hosts, key = { it.id }) { h ->
            HostSessionsCard(
                host = h,
                open = sessions.filter { it.host.id == h.id },
                recent = data.recentSessions.filter { r ->
                    r.hostId == h.id && sessions.none { it.host.id == r.hostId && it.tmuxName == r.tmuxName }
                },
                nav = nav,
            )
        }
    }
}

@Composable
private fun HostSessionsCard(host: Host, open: List<TerminalSession>, recent: List<SavedSession>, nav: Nav) {
    var expanded by rememberSaveable(host.id) { mutableStateOf(open.isNotEmpty()) }
    val live = open.count { it.state.value == SessionState.CONNECTED }
    AppCard(contentPadding = PaddingValues(0.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HostAvatar(host, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(host.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(host.address, style = MonoSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (open.isNotEmpty()) {
                Tag(if (live > 0) "$live aktif" else "${open.size} tab", if (live > 0) Palette.Accent else Palette.Warn)
                Spacer(Modifier.width(6.dp))
            }
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (expanded) "Tutup" else "Buka", tint = Palette.TextDim)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { nav.connect(host.id, plain = true) }) {
                        Icon(Icons.Rounded.Terminal, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Terminal")
                    }
                    OutlinedButton(onClick = { nav.connect(host.id, tmux = host.tmuxSessionName) }) {
                        Icon(Icons.Rounded.Layers, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("tmux")
                    }
                }

                if (open.isNotEmpty()) {
                    SubHeader("Tab terbuka")
                    open.forEach { s -> OpenTabRow(s, nav) }
                }

                SubHeader("Sesi tmux di server")
                RemoteTmuxPanel(host, nav)

                if (recent.isNotEmpty()) {
                    SubHeader("Baru ditutup")
                    recent.forEach { r -> RecentRow(host, r, nav) }
                }
            }
        }
    }
}

@Composable
private fun SubHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.TextDim, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun OpenTabRow(s: TerminalSession, nav: Nav) {
    val state by s.state.collectAsState()
    val title by s.title.collectAsState()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { nav.terminal(s.id) }.padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(stateColor(state), 10.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(s.tmuxName?.let { "tmux: $it" } ?: "Terminal", style = MaterialTheme.typography.titleSmall)
            Text(
                "${stateLabel(state)}${if (title.isNotBlank() && title != s.host.label) " · $title" else ""}",
                style = MaterialTheme.typography.bodySmall, color = stateColor(state), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { TermXApp.container.sessions.closeTerminal(s.id) }) {
            Icon(Icons.Rounded.Close, "Tutup tab", tint = Palette.TextDim)
        }
    }
}

@Composable
private fun RecentRow(host: Host, r: SavedSession, nav: Nav) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .clickable { nav.connect(host.id, tmux = r.tmuxName, plain = r.tmuxName == null) }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.History, null, tint = Palette.TextDim, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(r.tmuxName?.let { "tmux: $it" } ?: "Terminal", style = MaterialTheme.typography.titleSmall)
            Text("ditutup ${timeAgo(r.closedAt)} · ketuk untuk buka lagi beserta riwayatnya", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
        }
        IconButton(onClick = { TermXApp.container.sessions.forgetRecent(r) }) {
            Icon(Icons.Rounded.Close, "Hapus dari daftar", tint = Palette.TextDim)
        }
    }
}

@Composable
private fun RemoteTmuxPanel(host: Host, nav: Nav) {
    val mgr = TermXApp.container.sessions
    val scope = rememberCoroutineScope()
    var loading by remember(host.id) { mutableStateOf(false) }
    var error by remember(host.id) { mutableStateOf<String?>(null) }
    var list by remember(host.id) { mutableStateOf<List<Tmux.RemoteSession>?>(null) }
    var noTmux by remember(host.id) { mutableStateOf(false) }
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

    // Dimuat otomatis saat host dibuka di daftar.
    LaunchedEffect(host.id) { refresh() }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    loading && list == null -> "Memuat…"
                    noTmux -> "tmux belum terpasang di server ini."
                    list?.isEmpty() == true -> "Tidak ada sesi tmux."
                    list != null -> "${list!!.size} sesi"
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, modifier = Modifier.weight(1f),
            )
            if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            else TextButton(onClick = { refresh() }) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Segarkan")
            }
        }
        error?.let { Text(it, color = Palette.Danger, style = MaterialTheme.typography.bodySmall) }
        if (noTmux) Text(Tmux.installHint(), style = MonoSmall)
        list?.forEach { rs ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .clickable { nav.connect(host.id, tmux = rs.name) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Layers, null, tint = Palette.Accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(rs.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${rs.windows} window · ${if (rs.attachedClients > 0) "${rs.attachedClients} terhubung" else "terlepas"} · aktif ${timeAgo(rs.activityEpoch * 1000)}",
                        style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
                    )
                }
                IconButton(onClick = { killing = rs.name }) { Icon(Icons.Rounded.Delete, "Akhiri", tint = Palette.Danger) }
            }
        }
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
