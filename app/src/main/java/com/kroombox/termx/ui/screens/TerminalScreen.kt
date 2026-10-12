package com.kroombox.termx.ui.screens

import androidx.compose.material.icons.rounded.Key
import androidx.compose.runtime.rememberCoroutineScope
import com.kroombox.termx.ui.components.SpecAvatar
import kotlinx.coroutines.launch
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kroombox.termx.TermXApp
import com.kroombox.termx.MainActivity
import com.kroombox.termx.data.AppSettings
import com.kroombox.termx.data.Snippet
import com.kroombox.termx.ssh.SessionState
import com.kroombox.termx.ssh.TerminalSession
import com.kroombox.termx.terminal.Mods
import com.kroombox.termx.terminal.TermKey
import com.kroombox.termx.terminal.TerminalClient
import com.kroombox.termx.terminal.TerminalFonts
import com.kroombox.termx.terminal.TerminalTheme
import com.kroombox.termx.terminal.TerminalView
import com.kroombox.termx.ui.Nav
import com.kroombox.termx.ui.components.ConfirmDialog
import com.kroombox.termx.ui.components.StatusDot
import com.kroombox.termx.ui.stateColor
import com.kroombox.termx.ui.stateLabel
import com.kroombox.termx.ui.theme.Mono
import com.kroombox.termx.ui.theme.Palette
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(nav: Nav, initialSessionId: String?) {
    val mgr = TermXApp.container.sessions
    val vault = TermXApp.container.vault
    val sessions by mgr.sessions.collectAsState()
    val data by vault.data.collectAsState()
    val settings = data.settings
    val context = LocalContext.current

    var selectedId by remember { mutableStateOf(initialSessionId) }
    val current = sessions.firstOrNull { it.id == selectedId } ?: sessions.lastOrNull()

    LaunchedEffect(sessions.isEmpty()) {
        if (sessions.isEmpty()) nav.back()
    }
    if (current == null) {
        Box(Modifier.fillMaxSize().background(Palette.Bg))
        return
    }

    val theme = TerminalTheme.byId(settings.themeId)
    val font = remember(settings.fontId, settings.customFonts) { TerminalFonts.load(context, settings) }
    var fontSize by remember { mutableFloatStateOf(settings.fontSizeSp) }
    LaunchedEffect(fontSize) {
        delay(800)
        if (fontSize != vault.current.settings.fontSizeSp) vault.updateSettings { it.copy(fontSizeSp = fontSize) }
    }

    // Modifier sticky dari bar tombol tambahan
    var stickyMods by remember { mutableIntStateOf(0) }
    var selecting by remember { mutableStateOf(false) }
    var showSnippets by remember { mutableStateOf(false) }
    var confirmKill by remember { mutableStateOf<TerminalSession?>(null) }
    val viewHolder = remember { arrayOfNulls<TerminalView>(1) }
    val uiScope = rememberCoroutineScope()

    DisposableEffect(settings.keepScreenOn) {
        (context as? MainActivity)?.setKeepScreenOn(settings.keepScreenOn)
        onDispose { (context as? MainActivity)?.setKeepScreenOn(false) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(theme.background))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        TerminalTopBar(
            sessions = sessions,
            current = current,
            onBack = { nav.back() },
            onSelect = { selectedId = it.id },
            onClose = { s -> mgr.closeTerminal(s.id) },
            onSnippets = { showSnippets = true },
            menu = { dismiss ->
                val view = viewHolder[0]
                DropdownMenuItem(text = { Text("Tempel") }, leadingIcon = { Icon(Icons.Rounded.ContentPaste, null) }, onClick = {
                    dismiss(); clipboardText(context)?.let { view?.paste(it) }
                })
                DropdownMenuItem(text = { Text("Salin layar") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = {
                    dismiss(); copy(context, current.emulator.screenText())
                })
                DropdownMenuItem(text = { Text("Perbesar huruf") }, leadingIcon = { Icon(Icons.Rounded.ZoomIn, null) }, onClick = {
                    fontSize = (fontSize + 1f).coerceAtMost(32f)
                })
                DropdownMenuItem(text = { Text("Perkecil huruf") }, leadingIcon = { Icon(Icons.Rounded.ZoomOut, null) }, onClick = {
                    fontSize = (fontSize - 1f).coerceAtLeast(6f)
                })
                DropdownMenuItem(text = { Text("Tema: ${theme.name}") }, leadingIcon = { Icon(Icons.Rounded.Palette, null) }, onClick = {
                    val all = TerminalTheme.all
                    val next = all[(all.indexOf(theme) + 1) % all.size]
                    vault.updateSettings { it.copy(themeId = next.id) }
                })
                DropdownMenuItem(
                    text = { Text("Font: ${TerminalFonts.option(settings).name}") },
                    leadingIcon = { Icon(Icons.Rounded.FontDownload, null) },
                    onClick = {
                        val all = TerminalFonts.options(settings)
                        val idx = all.indexOfFirst { it.id == settings.fontId }
                        val next = all[(idx + 1).mod(all.size)]
                        vault.updateSettings { it.copy(fontId = next.id) }
                    },
                )
                HorizontalDivider()
                DropdownMenuItem(text = { Text("SFTP") }, leadingIcon = { Icon(Icons.Rounded.Folder, null) }, onClick = {
                    dismiss(); nav.sftp(current.host.id)
                })
                DropdownMenuItem(text = { Text("Monitor server") }, leadingIcon = { Icon(Icons.Rounded.Speed, null) }, onClick = {
                    dismiss(); nav.monitor(current.host.id)
                })
                DropdownMenuItem(text = { Text("Tab baru (sesi tmux baru)") }, leadingIcon = { Icon(Icons.Rounded.Add, null) }, onClick = {
                    dismiss()
                    val base = current.tmuxName ?: current.host.tmuxSessionName
                    val existing = sessions.mapNotNull { it.tmuxName }.toSet()
                    var i = 2
                    while ("$base-$i" in existing) i++
                    mgr.openTerminal(current.host.id, if (current.host.persistentSession || current.tmuxName != null) "$base-$i" else null)
                        ?.let { selectedId = it.id }
                })
                DropdownMenuItem(text = { Text("Ketik password sudo") }, leadingIcon = { Icon(Icons.Rounded.Key, null) }, onClick = {
                    dismiss()
                    uiScope.launch { if (!current.typeSudoPassword()) Toast.makeText(context, "Password sudo dibatalkan", Toast.LENGTH_SHORT).show() }
                })
                DropdownMenuItem(text = { Text("Sambung ulang") }, leadingIcon = { Icon(Icons.Rounded.Refresh, null) }, onClick = {
                    dismiss(); current.reconnectNow()
                })
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Tutup tab") }, leadingIcon = { Icon(Icons.Rounded.Close, null) }, onClick = {
                    dismiss(); mgr.closeTerminal(current.id)
                })
                if (current.tmuxName != null) {
                    DropdownMenuItem(
                        text = { Text("Tutup & akhiri sesi tmux", color = Palette.Danger) },
                        leadingIcon = { Icon(Icons.Rounded.Close, null, tint = Palette.Danger) },
                        onClick = { dismiss(); confirmKill = current },
                    )
                }
            },
        )

        StatusBanner(current)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            key(current.id) {
                AndroidView(
                    factory = { ctx ->
                        TerminalView(ctx).apply {
                            setPadding(8, 4, 8, 4)
                            this.theme = theme
                            fontSizeSp = fontSize
                            setFont(font.id, font.regular, font.bold, settings.ligatures)
                            stickyModsProvider = { stickyMods }
                            onStickyModsConsumed = { stickyMods = 0 }
                            client = object : TerminalClient {
                                override fun sendBytes(bytes: ByteArray) = current.write(bytes)
                                override fun onTerminalResize(cols: Int, rows: Int) = current.resize(cols, rows)
                                override fun onSelectionChanged(active: Boolean) { selecting = active }
                                override fun onFontSizeChanged(sp: Float) { fontSize = sp }
                            }
                            emulator = current.emulator
                            viewHolder[0] = this
                            post { showKeyboard() }
                        }
                    },
                    update = { v ->
                        v.theme = theme
                        v.setFont(font.id, font.regular, font.bold, settings.ligatures)
                        if (v.fontSizeSp != fontSize) v.fontSizeSp = fontSize
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            DisposableEffect(current.id, settings.vibrateOnBell) {
                current.onScreenUpdate = { viewHolder[0]?.postInvalidateOnAnimation() }
                current.onBell = if (settings.vibrateOnBell) ({ viewHolder[0]?.post { viewHolder[0]?.onBell() } }) else null
                viewHolder[0]?.postInvalidate()
                onDispose {
                    current.onScreenUpdate = null
                    current.onBell = null
                }
            }
        }

        if (selecting) {
            SelectionBar(
                onCopy = {
                    viewHolder[0]?.selectedText()?.let { copy(context, it) }
                    viewHolder[0]?.clearSelection()
                },
                onSelectAll = { viewHolder[0]?.selectAllVisible() },
                onPaste = {
                    viewHolder[0]?.clearSelection()
                    clipboardText(context)?.let { viewHolder[0]?.paste(it) }
                },
                onClose = { viewHolder[0]?.clearSelection() },
            )
        }

        ExtraKeysBar(
            layout = settings.extraKeys,
            stickyMods = stickyMods,
            onToggleMod = { m -> stickyMods = stickyMods xor m },
            onKey = { k -> viewHolder[0]?.sendKey(k) },
            onText = { t -> viewHolder[0]?.sendText(t) },
            onKeyboard = {
                val v = viewHolder[0]
                if (v != null) v.showKeyboard()
            },
            onPaste = { clipboardText(context)?.let { viewHolder[0]?.paste(it) } },
        )
    }

    if (showSnippets) {
        ModalBottomSheet(onDismissRequest = { showSnippets = false }, containerColor = Palette.Surface) {
            SnippetPicker(data.snippets) { sn ->
                showSnippets = false
                current.runCommand(sn.command, sn.autoRun, sn.useSudo)
                viewHolder[0]?.scrollToBottom()
            }
        }
    }

    confirmKill?.let { s ->
        ConfirmDialog(
            title = "Akhiri sesi tmux?",
            message = "Sesi tmux \"${s.tmuxName}\" di ${s.host.label} akan dihentikan beserta semua proses di dalamnya.",
            confirmText = "Akhiri", danger = true,
            onConfirm = { mgr.closeTerminal(s.id, killTmux = true) },
            onDismiss = { confirmKill = null },
        )
    }
}

@Composable
private fun TerminalTopBar(
    sessions: List<TerminalSession>,
    current: TerminalSession,
    onBack: () -> Unit,
    onSelect: (TerminalSession) -> Unit,
    onClose: (TerminalSession) -> Unit,
    onSnippets: () -> Unit,
    menu: @Composable (dismiss: () -> Unit) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(Palette.Surface).padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") }
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            sessions.forEach { s ->
                SessionTab(s, selected = s.id == current.id, onClick = { onSelect(s) }, onClose = { onClose(s) })
            }
        }
        IconButton(onClick = onSnippets) { Icon(Icons.Rounded.Bolt, "Snippet", tint = Palette.Warn) }
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "Menu") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                menu { menuOpen = false }
            }
        }
    }
}

@Composable
private fun SessionTab(s: TerminalSession, selected: Boolean, onClick: () -> Unit, onClose: () -> Unit) {
    val state by s.state.collectAsState()
    val title by s.title.collectAsState()
    Surface(
        color = if (selected) Palette.SurfaceHighest else Color.Transparent,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(stateColor(state))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.widthIn(max = 160.dp)) {
                Text(s.host.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = s.tmuxName?.let { "tmux:$it" } ?: title
                Text(sub, style = MaterialTheme.typography.labelSmall, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (selected) {
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Close, "Tutup", Modifier.size(16.dp), tint = Palette.TextDim)
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

@Composable
private fun StatusBanner(s: TerminalSession) {
    val state by s.state.collectAsState()
    val error by s.error.collectAsState()
    if (state == SessionState.CONNECTED) return
    val (bg, text) = when (state) {
        SessionState.CONNECTING -> Palette.SurfaceHigh to "Menyambung ke ${s.host.address}…"
        SessionState.RECONNECTING -> Palette.Warn.copy(alpha = 0.15f) to
            ("Koneksi terputus. Menyambung ulang…" + if (s.isPersistent) " Proses di tmux tetap berjalan di server." else "")
        SessionState.FAILED -> Palette.Danger.copy(alpha = 0.15f) to (error ?: "Gagal tersambung")
        SessionState.ENDED -> Palette.SurfaceHigh to "Sesi selesai."
        else -> Palette.SurfaceHigh to stateLabel(state)
    }
    Row(
        Modifier.fillMaxWidth().background(bg).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state == SessionState.CONNECTING || state == SessionState.RECONNECTING) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        if (state != SessionState.CONNECTING) {
            TextButton(onClick = { s.reconnectNow() }) {
                Text(if (state == SessionState.RECONNECTING) "Sekarang" else "Sambung ulang")
            }
        }
    }
}

@Composable
private fun SelectionBar(onCopy: () -> Unit, onSelectAll: () -> Unit, onPaste: () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Palette.SurfaceHigh).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Seret untuk memilih", style = MaterialTheme.typography.labelMedium, color = Palette.TextDim, modifier = Modifier.weight(1f).padding(start = 8.dp))
        IconButton(onClick = onSelectAll) { Icon(Icons.Rounded.SelectAll, "Pilih layar") }
        IconButton(onClick = onPaste) { Icon(Icons.Rounded.ContentPaste, "Tempel") }
        TextButton(onClick = onCopy) {
            Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Salin")
        }
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Batal") }
    }
}

private sealed class ExtraKey(val label: String) {
    class Key(label: String, val key: TermKey) : ExtraKey(label)
    class Mod(label: String, val mod: Int) : ExtraKey(label)
    class Txt(label: String, val text: String) : ExtraKey(label)
    object Paste : ExtraKey("⎘")
}

private fun parseExtraKeys(layout: String): List<ExtraKey> {
    val tokens = layout.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        .ifEmpty { AppSettings.DEFAULT_EXTRA_KEYS.split(' ') }
    return tokens.map { t ->
        when (t.uppercase()) {
            "ESC" -> ExtraKey.Key("ESC", TermKey.ESC)
            "TAB" -> ExtraKey.Key("TAB", TermKey.TAB)
            "CTRL" -> ExtraKey.Mod("CTRL", Mods.CTRL)
            "ALT" -> ExtraKey.Mod("ALT", Mods.ALT)
            "SHIFT" -> ExtraKey.Mod("SHIFT", Mods.SHIFT)
            "UP" -> ExtraKey.Key("↑", TermKey.UP)
            "DOWN" -> ExtraKey.Key("↓", TermKey.DOWN)
            "LEFT" -> ExtraKey.Key("←", TermKey.LEFT)
            "RIGHT" -> ExtraKey.Key("→", TermKey.RIGHT)
            "HOME" -> ExtraKey.Key("HOME", TermKey.HOME)
            "END" -> ExtraKey.Key("END", TermKey.END)
            "PGUP" -> ExtraKey.Key("PGUP", TermKey.PAGE_UP)
            "PGDN" -> ExtraKey.Key("PGDN", TermKey.PAGE_DOWN)
            "INS" -> ExtraKey.Key("INS", TermKey.INSERT)
            "DEL" -> ExtraKey.Key("DEL", TermKey.DELETE)
            "ENTER" -> ExtraKey.Key("⏎", TermKey.ENTER)
            "BKSP" -> ExtraKey.Key("⌫", TermKey.BACKSPACE)
            "PASTE" -> ExtraKey.Paste
            "F1" -> ExtraKey.Key("F1", TermKey.F1)
            "F2" -> ExtraKey.Key("F2", TermKey.F2)
            "F3" -> ExtraKey.Key("F3", TermKey.F3)
            "F4" -> ExtraKey.Key("F4", TermKey.F4)
            "F5" -> ExtraKey.Key("F5", TermKey.F5)
            "F6" -> ExtraKey.Key("F6", TermKey.F6)
            "F7" -> ExtraKey.Key("F7", TermKey.F7)
            "F8" -> ExtraKey.Key("F8", TermKey.F8)
            "F9" -> ExtraKey.Key("F9", TermKey.F9)
            "F10" -> ExtraKey.Key("F10", TermKey.F10)
            "F11" -> ExtraKey.Key("F11", TermKey.F11)
            "F12" -> ExtraKey.Key("F12", TermKey.F12)
            else -> ExtraKey.Txt(t, t)
        }
    }
}

@Composable
private fun ExtraKeysBar(
    layout: String,
    stickyMods: Int,
    onToggleMod: (Int) -> Unit,
    onKey: (TermKey) -> Unit,
    onText: (String) -> Unit,
    onKeyboard: () -> Unit,
    onPaste: () -> Unit,
) {
    val keys = remember(layout) { parseExtraKeys(layout) }
    Row(
        Modifier.fillMaxWidth().background(Palette.Surface).padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeyButton(icon = true, label = "", active = false, onClick = onKeyboard)
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            keys.forEach { k ->
                when (k) {
                    is ExtraKey.Key -> KeyButton(label = k.label, onClick = { onKey(k.key) })
                    is ExtraKey.Mod -> KeyButton(label = k.label, active = stickyMods and k.mod != 0, onClick = { onToggleMod(k.mod) })
                    is ExtraKey.Txt -> KeyButton(label = k.label, onClick = { onText(k.text) })
                    ExtraKey.Paste -> KeyButton(label = k.label, onClick = onPaste)
                }
            }
        }
    }
}

@Composable
private fun KeyButton(label: String, active: Boolean = false, icon: Boolean = false, onClick: () -> Unit) {
    Surface(
        color = if (active) MaterialTheme.colorScheme.primary else Palette.SurfaceHigh,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimary else Palette.Text,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.heightIn(min = 40.dp).widthIn(min = 44.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick),
    ) {
        Box(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            if (icon) Icon(Icons.Rounded.Keyboard, "Keyboard", Modifier.size(20.dp))
            else Text(label, fontFamily = Mono, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SnippetPicker(snippets: List<Snippet>, onPick: (Snippet) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text("Snippet", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        if (snippets.isEmpty()) {
            Text("Belum ada snippet. Tambahkan di tab Snippet.", color = Palette.TextDim, modifier = Modifier.padding(20.dp))
        }
        LazyColumn(Modifier.heightIn(max = 480.dp)) {
            items(snippets, key = { it.id }) { sn ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(sn) }.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpecAvatar(sn.icon, sn.name, Palette.Warn, size = 36.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(sn.name, style = MaterialTheme.typography.titleSmall)
                            if (sn.useSudo) {
                                Spacer(Modifier.width(6.dp))
                                Tag("sudo", Palette.Danger)
                            }
                        }
                        Text(sn.command, fontFamily = Mono, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (!sn.autoRun) Text("tanpa ⏎", style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("terminal", text))
    Toast.makeText(context, "Disalin", Toast.LENGTH_SHORT).show()
}

fun clipboardText(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}
