package com.kroombox.termx.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.kroombox.termx.R
import com.kroombox.termx.os.OsCatalog
import com.kroombox.termx.ui.components.composeColor
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.kroombox.termx.data.HostGroup
import com.kroombox.termx.ssh.SessionState
import com.kroombox.termx.ui.Nav
import com.kroombox.termx.ui.components.ConfirmDialog
import com.kroombox.termx.ui.components.EmptyState
import com.kroombox.termx.ui.components.HostAvatar
import com.kroombox.termx.ui.components.SectionHeader
import com.kroombox.termx.ui.components.StatusDot
import com.kroombox.termx.ui.components.accent
import com.kroombox.termx.ui.components.timeAgo
import com.kroombox.termx.ui.theme.MonoSmall
import com.kroombox.termx.ui.theme.Palette

@Composable
fun HostsScreen(nav: Nav, modifier: Modifier = Modifier) {
    val vault = TermXApp.container.vault
    val data by vault.data.collectAsState()
    val sessions by TermXApp.container.sessions.sessions.collectAsState()
    val stateVersion by TermXApp.container.sessions.stateVersion.collectAsState()
    var query by remember { mutableStateOf("") }
    var groupFilter by remember { mutableStateOf<String?>(null) }
    var newGroup by remember { mutableStateOf(false) }

    val filtered = data.hosts
        .filter { groupFilter == null || it.groupId == groupFilter }
        .filter { h ->
            query.isBlank() || listOf(h.label, h.hostname, h.username, h.tags.joinToString(" "))
                .any { it.contains(query, ignoreCase = true) }
        }
        .sortedWith(compareByDescending<Host> { it.lastConnectedAt }.thenBy { it.label.lowercase() })
    val connectedHosts = remember(sessions, stateVersion) {
        sessions.filter { it.state.value == SessionState.CONNECTED }.map { it.host.id }.toSet()
    }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(Modifier.padding(top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.ic_termx_logo), contentDescription = null, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("TermX", style = MaterialTheme.typography.headlineMedium)
                        Text("Host", style = MaterialTheme.typography.labelLarge, color = Palette.TextDim)
                    }
                }
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Cari host, alamat, tag…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = Palette.SurfaceHigh,
                        unfocusedContainerColor = Palette.Surface,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                GroupChips(data.groups, groupFilter, onSelect = { groupFilter = it }, onAdd = { newGroup = true })
            }
            if (data.hosts.isEmpty()) {
                item {
                    Box(Modifier.height(420.dp)) {
                        EmptyState(
                            Icons.Rounded.Dns,
                            "Belum ada host",
                            "Tambahkan server pertama Anda untuk mulai SSH, SFTP, dan monitoring.",
                        ) {
                            TextButton(onClick = { nav.editHost() }) { Text("Tambah host") }
                        }
                    }
                }
            } else {
                val knownGroups = data.groups.map { it.id }.toSet()
                val grouped = filtered.groupBy { h -> h.groupId?.takeIf { it in knownGroups } }
                val orderedGroups = listOf<String?>(null) + data.groups.map { it.id }
                orderedGroups.forEach { gid ->
                    val list = grouped[gid] ?: return@forEach
                    if (groupFilter == null && data.groups.isNotEmpty()) {
                        item(key = "g-$gid") {
                            SectionHeader(data.groups.firstOrNull { it.id == gid }?.name ?: "Tanpa grup")
                        }
                    }
                    items(list, key = { it.id }) { host ->
                        HostCard(host, connected = host.id in connectedHosts, nav = nav)
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { nav.editHost() },
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("Host baru") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
    }

    if (newGroup) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newGroup = false },
            title = { Text("Grup baru") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Nama grup") }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    vault.saveGroup(HostGroup(name = name.trim(), colorIndex = data.groups.size + 1))
                    newGroup = false
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { newGroup = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun GroupChips(groups: List<HostGroup>, selected: String?, onSelect: (String?) -> Unit, onAdd: () -> Unit) {
    var deleting by remember { mutableStateOf<HostGroup?>(null) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Semua") })
        groups.forEach { g ->
            FilterChip(
                selected = selected == g.id,
                onClick = { onSelect(if (selected == g.id) null else g.id) },
                label = { Text(g.name) },
                leadingIcon = { StatusDot(accent(g.colorIndex)) },
                trailingIcon = if (selected == g.id) {
                    { Icon(Icons.Rounded.Delete, "Hapus grup", Modifier.size(16.dp).clip(RoundedCornerShape(8.dp)).clickable { deleting = g }) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
            )
        }
        FilterChip(selected = false, onClick = onAdd, label = { Text("Grup") }, leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(16.dp)) })
    }
    deleting?.let { g ->
        ConfirmDialog(
            title = "Hapus grup?",
            message = "Grup \"${g.name}\" dihapus. Host di dalamnya tidak ikut terhapus.",
            confirmText = "Hapus", danger = true,
            onConfirm = { TermXApp.container.vault.deleteGroup(g.id); onSelect(null) },
            onDismiss = { deleting = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HostCard(host: Host, connected: Boolean, nav: Nav) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = { nav.connect(host.id) }, onLongClick = { menu = true }),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                HostAvatar(host)
                if (connected) {
                    Box(Modifier.align(Alignment.BottomEnd).padding(1.dp)) { StatusDot(Palette.Accent, 10.dp) }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(host.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(host.address, style = MonoSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OsCatalog.byId(host.osId)?.let { os -> Tag(os.title, os.composeColor()) }
                    if (host.persistentSession) Tag("tmux · ${host.tmuxSessionName}", Palette.Accent)
                    if (host.jumpHostId != null) Tag("via jump", Palette.Blue)
                    host.tags.take(2).forEach { Tag(it, Palette.TextDim) }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Menu") }
                    HostMenu(host, menu, onDismiss = { menu = false }, nav = nav, onDelete = { confirmDelete = true })
                }
                Text(timeAgo(host.lastConnectedAt), style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Hapus host?",
            message = "\"${host.label}\" beserta aturan port forward-nya akan dihapus.",
            confirmText = "Hapus", danger = true,
            onConfirm = { TermXApp.container.vault.deleteHost(host.id) },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
fun Tag(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
        Text(
            text, color = color, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), maxLines = 1,
        )
    }
}

@Composable
private fun HostMenu(host: Host, expanded: Boolean, onDismiss: () -> Unit, nav: Nav, onDelete: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Terminal") }, leadingIcon = { Icon(Icons.Rounded.Terminal, null) },
            onClick = { onDismiss(); nav.connect(host.id) })
        if (host.persistentSession) {
            DropdownMenuItem(text = { Text("Shell biasa (tanpa tmux)") }, leadingIcon = { Icon(Icons.Rounded.Terminal, null) },
                onClick = { onDismiss(); nav.connect(host.id, plain = true) })
        }
        DropdownMenuItem(text = { Text("SFTP") }, leadingIcon = { Icon(Icons.Rounded.Folder, null) },
            onClick = { onDismiss(); nav.sftp(host.id) })
        DropdownMenuItem(text = { Text("Monitor server") }, leadingIcon = { Icon(Icons.Rounded.Speed, null) },
            onClick = { onDismiss(); nav.monitor(host.id) })
        DropdownMenuItem(text = { Text("Port forwarding") }, leadingIcon = { Icon(Icons.Rounded.SwapHoriz, null) },
            onClick = { onDismiss(); nav.forwards(host.id) })
        HorizontalDivider()
        DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
            onClick = { onDismiss(); nav.editHost(host.id) })
        DropdownMenuItem(text = { Text("Duplikat") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
            onClick = {
                onDismiss()
                TermXApp.container.vault.saveHost(
                    host.copy(id = com.kroombox.termx.data.newId(), label = host.label + " (salinan)", lastConnectedAt = 0),
                )
            })
        DropdownMenuItem(text = { Text("Hapus", color = Palette.Danger) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Palette.Danger) },
            onClick = { onDismiss(); onDelete() })
    }
}
