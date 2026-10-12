package com.kroombox.termx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kroombox.termx.TermXApp
import com.kroombox.termx.data.Host
import com.kroombox.termx.ssh.TerminalSession
import com.kroombox.termx.ssh.Tmux
import com.kroombox.termx.ui.components.HostAvatar
import com.kroombox.termx.ui.components.StatusDot
import com.kroombox.termx.ui.components.timeAgo
import com.kroombox.termx.ui.stateColor
import com.kroombox.termx.ui.stateLabel
import com.kroombox.termx.ui.theme.MonoSmall
import com.kroombox.termx.ui.theme.Palette

/**
 * Panel sesi di dalam terminal (ala Termius): pindah antar sesi aktif, tutup sesi, buka sesi baru ke
 * host mana pun (terminal biasa atau tmux), dan masuk ke sesi tmux yang hidup di server.
 */
@Composable
fun SessionSwitcher(
    sessions: List<TerminalSession>,
    current: TerminalSession,
    onSelect: (TerminalSession) -> Unit,
    onClose: (TerminalSession) -> Unit,
    onNew: (hostId: String, tmux: String?) -> Unit,
    onAttachTmux: (hostId: String, name: String) -> Unit,
    initialTab: Int = 0,
) {
    val data by TermXApp.container.vault.data.collectAsState()
    var tab by remember { mutableIntStateOf(initialTab) }
    var query by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Sesi", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("${sessions.size} aktif", style = MaterialTheme.typography.labelLarge, color = Palette.Accent)
        }
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Aktif") })
            FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Sesi baru") })
            FilterChip(selected = tab == 2, onClick = { tab = 2 }, label = { Text("tmux · ${current.host.label}") })
        }

        when (tab) {
            0 -> LazyColumn(Modifier.heightIn(max = 520.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                items(sessions, key = { it.id }) { s ->
                    ActiveSessionItem(s, selected = s.id == current.id, onClick = { onSelect(s) }, onClose = { onClose(s) })
                }
            }
            1 -> Column {
                TextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Cari host…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = Palette.SurfaceHigh, unfocusedContainerColor = Palette.SurfaceHigh,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                val hosts = data.hosts
                    .filter { h -> query.isBlank() || listOf(h.label, h.hostname, h.username).any { it.contains(query, true) } }
                    .sortedByDescending { it.lastConnectedAt }
                LazyColumn(Modifier.heightIn(max = 460.dp), contentPadding = PaddingValues(12.dp)) {
                    items(hosts, key = { it.id }) { h -> NewSessionItem(h, onNew) }
                }
            }
            else -> RemoteTmuxList(current.host, onAttachTmux)
        }
    }
}

@Composable
private fun ActiveSessionItem(s: TerminalSession, selected: Boolean, onClick: () -> Unit, onClose: () -> Unit) {
    val state by s.state.collectAsState()
    val title by s.title.collectAsState()
    val host = TermXApp.container.vault.host(s.host.id) ?: s.host
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (selected) Palette.SurfaceHighest else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            HostAvatar(host, size = 40.dp)
            Box(Modifier.align(Alignment.BottomEnd)) { StatusDot(stateColor(state), 10.dp) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(host.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (s.tmuxName?.let { "tmux: $it" } ?: "Terminal") + " · " + stateLabel(state) +
                    (if (title.isNotBlank() && title != host.label) " · $title" else ""),
                style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Tutup sesi", tint = Palette.TextDim) }
    }
}

@Composable
private fun NewSessionItem(h: Host, onNew: (String, String?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onNew(h.id, null) }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HostAvatar(h, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(h.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(h.address, style = MonoSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { onNew(h.id, null) }) { Icon(Icons.Rounded.Terminal, "Terminal", tint = Palette.Accent) }
        IconButton(onClick = { onNew(h.id, h.tmuxSessionName) }) { Icon(Icons.Rounded.Layers, "Terminal tmux", tint = Palette.Blue) }
    }
}

@Composable
private fun RemoteTmuxList(host: Host, onAttach: (String, String) -> Unit) {
    val mgr = TermXApp.container.sessions
    var loading by remember(host.id) { mutableStateOf(true) }
    var error by remember(host.id) { mutableStateOf<String?>(null) }
    var list by remember(host.id) { mutableStateOf<List<Tmux.RemoteSession>?>(null) }
    LaunchedEffect(host.id) {
        loading = true
        try {
            list = mgr.listTmux(host.id) ?: emptyList()
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        } finally {
            loading = false
        }
    }
    Column(Modifier.padding(horizontal = 12.dp)) {
        if (loading) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Memuat sesi tmux…", color = Palette.TextDim)
            }
        }
        error?.let { Text(it, color = Palette.Danger, modifier = Modifier.padding(12.dp)) }
        if (list?.isEmpty() == true) {
            Text("Belum ada sesi tmux di server ini. Buka lewat tab \"Sesi baru\" → ikon tmux.", color = Palette.TextDim, modifier = Modifier.padding(12.dp))
        }
        list?.forEach { rs ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onAttach(host.id, rs.name) }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Layers, null, tint = Palette.Accent)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(rs.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${rs.windows} window · ${if (rs.attachedClients > 0) "${rs.attachedClients} terhubung" else "terlepas"} · aktif ${timeAgo(rs.activityEpoch * 1000)}",
                        style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
                    )
                }
            }
        }
    }
}
