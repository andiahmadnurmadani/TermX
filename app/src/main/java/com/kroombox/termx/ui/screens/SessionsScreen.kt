package com.kroombox.termx.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kroombox.termx.TermXApp
import com.kroombox.termx.ssh.TerminalSession
import com.kroombox.termx.terminal.TerminalTheme
import com.kroombox.termx.ui.Nav
import com.kroombox.termx.ui.components.AppCard
import com.kroombox.termx.ui.components.EmptyState
import com.kroombox.termx.ui.components.HostAvatar
import com.kroombox.termx.ui.components.StatusDot
import com.kroombox.termx.ui.stateColor
import com.kroombox.termx.ui.stateLabel
import com.kroombox.termx.ui.theme.Palette
import kotlinx.coroutines.delay

/**
 * Tab Sesi (ala Termius): hanya sesi yang sedang tersambung, masing-masing dengan pratinjau layar
 * terminalnya. Ketuk untuk masuk, ✕ untuk menutup. Sesi baru dibuka dari tab Host atau tombol + di terminal.
 */
@Composable
fun SessionsScreen(nav: Nav, modifier: Modifier = Modifier) {
    val sessions by TermXApp.container.sessions.sessions.collectAsState()
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Sesi", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
            Text(
                if (sessions.isEmpty()) "Tidak ada sesi yang tersambung." else "${sessions.size} sesi tersambung. Ketuk untuk masuk.",
                style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim,
            )
        }
        if (sessions.isEmpty()) {
            item {
                Box(Modifier.height(380.dp)) {
                    EmptyState(Icons.Rounded.Terminal, "Belum ada sesi", "Ketuk host di tab Host untuk membuka terminal.")
                }
            }
        }
        items(sessions, key = { it.id }) { s -> SessionCard(s, nav) }
    }
}

@Composable
private fun SessionCard(s: TerminalSession, nav: Nav) {
    val state by s.state.collectAsState()
    val title by s.title.collectAsState()
    val data by TermXApp.container.vault.data.collectAsState()
    val host = data.hosts.firstOrNull { it.id == s.host.id } ?: s.host
    val theme = TerminalTheme.byId(data.settings.themeId)
    // Pratinjau layar, diperbarui tiap detik selama kartu terlihat.
    val preview by produceState(initialValue = s.emulator.previewLines(8), s.id) {
        while (true) {
            delay(1000)
            value = s.emulator.previewLines(8)
        }
    }

    AppCard(onClick = { nav.terminal(s.id) }, contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                HostAvatar(host, size = 38.dp)
                Box(Modifier.align(Alignment.BottomEnd)) { StatusDot(stateColor(state), 10.dp) }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(host.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (s.tmuxName?.let { "tmux: $it" } ?: "Terminal") + " · " + stateLabel(state) +
                        (if (title.isNotBlank() && title != host.label) " · $title" else ""),
                    style = MaterialTheme.typography.bodySmall, color = stateColor(state), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { TermXApp.container.sessions.closeTerminal(s.id) }) {
                Icon(Icons.Rounded.Close, "Tutup sesi", tint = Palette.TextDim)
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(10.dp))
                .background(Color(theme.background)).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            val lines = preview.ifEmpty { listOf("") }
            lines.forEach { l ->
                Text(
                    l.ifEmpty { " " },
                    color = Color(theme.foreground).copy(alpha = 0.85f),
                    fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                )
            }
        }
    }
}
