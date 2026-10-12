package com.kroombox.termx.ui.screens

import com.kroombox.termx.i18n.S
import com.kroombox.termx.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.WrapText
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kroombox.termx.TermXApp
import com.kroombox.termx.ssh.Sudo
import com.kroombox.termx.terminal.TerminalFonts
import com.kroombox.termx.ui.Nav
import com.kroombox.termx.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.Response
import net.schmizz.sshj.sftp.SFTPException
import java.util.EnumSet

/** Operasi file untuk editor: SFTP, dengan jalur sudo untuk file milik root. */
private object RemoteFiles {
    const val MAX_BYTES = 2L * 1024 * 1024

    class PermissionDenied : Exception(S.get(R.string.editor_permission_denied))

    private fun denied(e: Exception) =
        e is SFTPException && (e.statusCode == Response.StatusCode.PERMISSION_DENIED)

    /** Ubah "~" / "~/x" menjadi path absolut di home user. */
    fun resolve(hostId: String, path: String): String {
        val sftp = TermXApp.container.sessions.connectionFor(hostId).sftp()
        val home = sftp.canonicalize(".")
        return when {
            path == "~" -> home
            path.startsWith("~/") -> home.trimEnd('/') + "/" + path.removePrefix("~/")
            path.startsWith("/") -> path
            else -> home.trimEnd('/') + "/" + path
        }
    }

    fun read(hostId: String, path: String): String {
        val sftp = TermXApp.container.sessions.connectionFor(hostId).sftp()
        try {
            val size = sftp.stat(path).size
            if (size > MAX_BYTES) throw java.io.IOException(S.get(R.string.editor_err_too_large))
            val rf = sftp.open(path, EnumSet.of(OpenMode.READ))
            val bytes = try { rf.RemoteFileInputStream(0).readBytes() } finally { rf.close() }
            return decode(bytes)
        } catch (e: Exception) {
            if (denied(e)) throw PermissionDenied()
            throw e
        }
    }

    suspend fun readSudo(hostId: String, path: String): String {
        val r = TermXApp.container.sessions.execSudo(hostId, "cat -- ${Sudo.shQuote(path)}", timeoutSec = 30)
        if (r.exitCode != null && r.exitCode != 0) throw java.io.IOException(r.stderr.ifBlank { S.get(R.string.editor_err_read_sudo) }.trim())
        return decode(r.stdout.toByteArray(Charsets.UTF_8))
    }

    private fun decode(bytes: ByteArray): String {
        if (bytes.take(8000).any { it == 0.toByte() }) throw java.io.IOException(S.get(R.string.editor_err_binary))
        return String(bytes, Charsets.UTF_8)
    }

    fun write(hostId: String, path: String, text: String) {
        val sftp = TermXApp.container.sessions.connectionFor(hostId).sftp()
        try {
            val rf = sftp.open(path, EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC))
            try {
                val out = rf.RemoteFileOutputStream(0, 16)
                out.write(text.toByteArray(Charsets.UTF_8))
                out.close()
            } finally {
                rf.close()
            }
        } catch (e: Exception) {
            if (denied(e)) throw PermissionDenied()
            throw e
        }
    }

    /** Tulis sebagai root. Isi dikirim base64 lewat heredoc (bukan argumen), jadi aman untuk file besar. */
    suspend fun writeSudo(hostId: String, path: String, text: String) {
        val b64 = java.util.Base64.getMimeEncoder(76, "\n".toByteArray()).encodeToString(text.toByteArray(Charsets.UTF_8))
        val script = "(base64 -d 2>/dev/null || base64 -D) > ${Sudo.shQuote(path)} <<'TERMX_EOF'\n$b64\nTERMX_EOF\n"
        val r = TermXApp.container.sessions.execSudo(hostId, script, timeoutSec = 60)
        if (r.exitCode != null && r.exitCode != 0) throw java.io.IOException(r.stderr.ifBlank { S.get(R.string.editor_err_save_sudo) }.trim())
    }
}

/**
 * Editor teks layar penuh untuk file di server: nomor baris, undo/redo, cari & ganti, auto-indent,
 * baris simbol untuk koding, ukuran huruf, wrap, dan simpan (dengan sudo bila file milik root).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(nav: Nav, hostId: String, rawPath: String, isNew: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mgr = TermXApp.container.sessions
    val data by TermXApp.container.vault.data.collectAsState()
    val host = data.hosts.firstOrNull { it.id == hostId }

    var path by remember { mutableStateOf(rawPath) }
    var value by remember { mutableStateOf(TextFieldValue("")) }
    var original by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var needSudoRead by remember { mutableStateOf(false) }
    var needSudoWrite by remember { mutableStateOf(false) }
    var viaSudo by remember { mutableStateOf(false) }
    var wrap by remember { mutableStateOf(false) }
    var fontSp by remember { mutableFloatStateOf(13f) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    val undo = remember { mutableStateListOf<TextFieldValue>() }
    val redo = remember { mutableStateListOf<TextFieldValue>() }
    var lastPush by remember { mutableStateOf(0L) }

    val dirty = value.text != original
    val name = path.substringAfterLast('/').ifBlank { path }
    val useTabs = remember(original) { original.contains("\n\t") }

    DisposableEffect(hostId) {
        mgr.acquire(hostId, "editor")
        onDispose { mgr.release(hostId, "editor") }
    }

    fun load(sudo: Boolean) {
        loading = true
        error = null
        scope.launch {
            try {
                val abs = withContext(Dispatchers.IO) { RemoteFiles.resolve(hostId, rawPath) }
                path = abs
                val text = when {
                    isNew -> ""
                    sudo -> RemoteFiles.readSudo(hostId, abs)
                    else -> withContext(Dispatchers.IO) { RemoteFiles.read(hostId, abs) }
                }
                original = text
                value = TextFieldValue(text)
                undo.clear(); redo.clear()
                viaSudo = sudo
                needSudoRead = false
            } catch (e: RemoteFiles.PermissionDenied) {
                needSudoRead = true
                error = S.get(R.string.editor_read_denied)
            } catch (e: Exception) {
                // File baru yang belum ada di server bukan error.
                if (e is SFTPException && e.statusCode == Response.StatusCode.NO_SUCH_FILE) {
                    original = ""; value = TextFieldValue(""); info = S.get(R.string.editor_new_file_info)
                } else error = e.message ?: e.javaClass.simpleName
            } finally {
                loading = false
            }
        }
    }

    fun save(sudo: Boolean, then: (() -> Unit)? = null) {
        saving = true
        error = null
        val text = value.text
        scope.launch {
            try {
                if (sudo) RemoteFiles.writeSudo(hostId, path, text)
                else withContext(Dispatchers.IO) { RemoteFiles.write(hostId, path, text) }
                original = text
                needSudoWrite = false
                info = S.get(if (sudo) R.string.editor_saved_sudo else R.string.editor_saved)
                then?.invoke()
            } catch (e: RemoteFiles.PermissionDenied) {
                needSudoWrite = true
                error = S.get(R.string.editor_write_denied)
            } catch (e: Exception) {
                error = S.get(R.string.editor_save_failed, e.message ?: e.javaClass.simpleName)
            } finally {
                saving = false
            }
        }
    }

    LaunchedEffect(hostId, rawPath) { load(false) }
    LaunchedEffect(info) { if (info != null) { kotlinx.coroutines.delay(2500); info = null } }

    fun setText(new: TextFieldValue, recordUndo: Boolean = true) {
        if (recordUndo && new.text != value.text) {
            val now = System.currentTimeMillis()
            // Ketikan beruntun digabung jadi satu langkah undo.
            if (undo.isEmpty() || now - lastPush > 700) {
                undo.add(value)
                if (undo.size > 300) undo.removeAt(0)
                lastPush = now
            }
            redo.clear()
        }
        value = new
    }

    /** Auto-indent: baris baru mengikuti indentasi baris sebelumnya (+1 tingkat setelah { [ ( atau :). */
    fun onEdit(new: TextFieldValue) {
        val old = value
        val pos = new.selection.start
        if (new.selection.collapsed && new.text.length == old.text.length + 1 && pos > 0 && new.text[pos - 1] == '\n' &&
            new.text.removeRange(pos - 1, pos) == old.text
        ) {
            val lineStart = new.text.lastIndexOf('\n', pos - 2) + 1
            val prev = new.text.substring(lineStart, pos - 1)
            var indent = prev.takeWhile { it == ' ' || it == '\t' }
            val trimmed = prev.trimEnd()
            if (trimmed.endsWith("{") || trimmed.endsWith("[") || trimmed.endsWith("(") || trimmed.endsWith(":")) {
                indent += if (useTabs) "\t" else "    "
            }
            if (indent.isNotEmpty()) {
                val t = new.text.substring(0, pos) + indent + new.text.substring(pos)
                setText(TextFieldValue(t, TextRange(pos + indent.length)))
                return
            }
        }
        setText(new)
    }

    fun insert(s: String) {
        val v = value
        val start = v.selection.min
        val end = v.selection.max
        val t = v.text.substring(0, start) + s + v.text.substring(end)
        setText(TextFieldValue(t, TextRange(start + s.length)))
    }

    fun find(forward: Boolean) {
        if (query.isEmpty()) return
        val t = value.text
        val idx = if (forward) {
            t.indexOf(query, value.selection.max, ignoreCase = true).takeIf { it >= 0 } ?: t.indexOf(query, 0, ignoreCase = true)
        } else {
            t.lastIndexOf(query, (value.selection.min - 1).coerceAtLeast(0), ignoreCase = true).takeIf { it >= 0 && it < value.selection.min }
                ?: t.lastIndexOf(query, ignoreCase = true)
        }
        if (idx >= 0) value = value.copy(selection = TextRange(idx, idx + query.length))
        else info = S.get(R.string.editor_not_found)
    }

    fun replaceOne() {
        val sel = value.text.substring(value.selection.min, value.selection.max)
        if (query.isNotEmpty() && sel.equals(query, ignoreCase = true)) insert(replacement)
        find(true)
    }

    fun replaceAll() {
        if (query.isEmpty()) return
        val count = Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(value.text).count()
        if (count == 0) { info = S.get(R.string.editor_not_found); return }
        setText(TextFieldValue(value.text.replace(query, replacement, ignoreCase = true)))
        info = S.get(R.string.editor_replaced_count, count)
    }

    fun exit() { if (dirty) confirmExit = true else nav.back() }
    BackHandler(enabled = dirty) { confirmExit = true }

    val fontFamily = remember(data.settings.fontId, data.settings.customFonts) {
        FontFamily(TerminalFonts.load(context, data.settings).regular)
    }
    val style = TextStyle(fontFamily = fontFamily, fontSize = fontSp.sp, lineHeight = (fontSp * 1.45f).sp, color = Palette.Text)
    val matches = remember(query, value.text) {
        if (query.isEmpty()) 0 else Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(value.text).count()
    }

    Scaffold(
        containerColor = Palette.Bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            (if (dirty) "● " else "") + name, style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            (host?.label?.let { "$it · " } ?: "") + path + if (viaSudo) " · sudo" else "",
                            style = MaterialTheme.typography.labelSmall, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = { exit() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.editor_back)) } },
                actions = {
                    IconButton(onClick = { undo.removeLastOrNull()?.let { redo.add(value); value = it } }, enabled = undo.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.editor_undo))
                    }
                    IconButton(onClick = { redo.removeLastOrNull()?.let { undo.add(value); value = it } }, enabled = redo.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Rounded.Redo, stringResource(R.string.editor_redo))
                    }
                    IconButton(onClick = { searchOpen = !searchOpen }) { Icon(Icons.Rounded.Search, stringResource(R.string.editor_search), tint = if (searchOpen) Palette.Accent else Palette.Text) }
                    IconButton(onClick = { save(viaSudo || needSudoWrite) }, enabled = !saving && !loading && (dirty || isNew)) {
                        if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Save, stringResource(R.string.editor_save), tint = if (dirty) Palette.Accent else Palette.TextDim)
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.editor_menu)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(if (wrap) R.string.editor_wrap_off else R.string.editor_wrap_on)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.WrapText, null) },
                                onClick = { wrap = !wrap; menu = false },
                            )
                            DropdownMenuItem(text = { Text(stringResource(R.string.editor_font_bigger)) }, leadingIcon = { Icon(Icons.Rounded.ZoomIn, null) },
                                onClick = { fontSp = (fontSp + 1).coerceAtMost(28f) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.editor_font_smaller)) }, leadingIcon = { Icon(Icons.Rounded.ZoomOut, null) },
                                onClick = { fontSp = (fontSp - 1).coerceAtLeast(8f) })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text(stringResource(R.string.editor_save_sudo)) }, leadingIcon = { Icon(Icons.Rounded.Save, null) },
                                onClick = { menu = false; save(true) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.editor_reload)) }, leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                                onClick = { menu = false; load(viaSudo) })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.Surface),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { msg ->
                Row(
                    Modifier.fillMaxWidth().background(Palette.Danger.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = Palette.Danger, modifier = Modifier.weight(1f))
                    if (needSudoRead) TextButton(onClick = { load(true) }) { Text(stringResource(R.string.editor_open_sudo)) }
                    if (needSudoWrite) TextButton(onClick = { save(true) }) { Text(stringResource(R.string.editor_save_sudo)) }
                }
            }
            info?.let {
                Text(
                    it, style = MaterialTheme.typography.bodySmall, color = Palette.Accent,
                    modifier = Modifier.fillMaxWidth().background(Palette.Accent.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            if (searchOpen) {
                SearchPanel(
                    query = query, onQuery = { query = it },
                    replacement = replacement, onReplacement = { replacement = it },
                    matches = matches,
                    onNext = { find(true) }, onPrev = { find(false) },
                    onReplace = { replaceOne() }, onReplaceAll = { replaceAll() },
                    onClose = { searchOpen = false },
                )
            }

            // Area teks: gutter nomor baris + isi. Satu scroll vertikal untuk keduanya agar sejajar.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val viewportW = maxWidth
                val viewportH = maxHeight
                val lineCount = remember(value.text) { value.text.count { it == '\n' } + 1 }
                val digits = lineCount.toString().length.coerceAtLeast(2)
                Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    if (!wrap) {
                        Text(
                            remember(lineCount) { (1..lineCount).joinToString("\n") },
                            style = style.copy(color = Palette.TextDim.copy(alpha = 0.6f)),
                            modifier = Modifier.background(Palette.Surface).padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
                                .width((digits * fontSp * 0.62f + 2).dp),
                            softWrap = false,
                        )
                    }
                    val fieldMod = if (wrap) Modifier.weight(1f) else Modifier.weight(1f).horizontalScroll(rememberScrollState())
                    Box(fieldMod) {
                        BasicTextField(
                            value = value,
                            onValueChange = { onEdit(it) },
                            textStyle = style,
                            cursorBrush = SolidColor(Palette.Accent),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                keyboardType = KeyboardType.Ascii,
                            ),
                            modifier = Modifier
                                .then(if (wrap) Modifier.fillMaxWidth() else Modifier.widthIn(min = viewportW))
                                .heightIn(min = viewportH)
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                        )
                    }
                }
            }

            SymbolBar(onInsert = { insert(it) }, tab = if (useTabs) "\t" else "    ")
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(stringResource(R.string.editor_save_changes_title)) },
            text = { Text(stringResource(R.string.editor_unsaved_message, name)) },
            confirmButton = {
                Button(onClick = { confirmExit = false; save(viaSudo || needSudoWrite) { nav.back() } }) { Text(stringResource(R.string.editor_save_and_exit)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmExit = false; nav.back() }) { Text(stringResource(R.string.editor_discard), color = Palette.Danger) }
                    TextButton(onClick = { confirmExit = false }) { Text(stringResource(R.string.editor_cancel)) }
                }
            },
        )
    }
}

@Composable
private fun SearchPanel(
    query: String, onQuery: (String) -> Unit,
    replacement: String, onReplacement: (String) -> Unit,
    matches: Int,
    onNext: () -> Unit, onPrev: () -> Unit,
    onReplace: () -> Unit, onReplaceAll: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        focusedContainerColor = Palette.SurfaceHigh, unfocusedContainerColor = Palette.SurfaceHigh,
    )
    Column(Modifier.fillMaxWidth().background(Palette.Surface).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                query, onQuery, placeholder = { Text(stringResource(R.string.editor_search)) }, singleLine = true, colors = colors,
                shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f),
                suffix = { if (query.isNotEmpty()) Text("$matches", color = Palette.TextDim) },
            )
            IconButton(onClick = onPrev) { Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.editor_previous)) }
            IconButton(onClick = onNext) { Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.editor_next)) }
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.editor_close_search)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                replacement, onReplacement, placeholder = { Text(stringResource(R.string.editor_replace_with)) }, singleLine = true, colors = colors,
                shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onReplace) { Text(stringResource(R.string.editor_replace)) }
            TextButton(onClick = onReplaceAll) { Text(stringResource(R.string.editor_replace_all)) }
        }
    }
}

/** Baris simbol di atas keyboard: karakter yang sering dipakai di kode dan konfigurasi. */
@Composable
private fun SymbolBar(onInsert: (String) -> Unit, tab: String) {
    val keys = listOf("⇥" to tab, "{" to "{", "}" to "}", "[" to "[", "]" to "]", "(" to "(", ")" to ")", "<" to "<", ">" to ">",
        "/" to "/", "\\" to "\\", "|" to "|", ";" to ";", ":" to ":", "=" to "=", "\"" to "\"", "'" to "'", "`" to "`",
        "$" to "$", "#" to "#", "~" to "~", "-" to "-", "_" to "_", "*" to "*", "&" to "&", "!" to "!", "%" to "%")
    Row(
        Modifier.fillMaxWidth().background(Palette.Surface).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        keys.forEach { (label, text) ->
            Surface(
                color = Palette.SurfaceHigh, shape = RoundedCornerShape(8.dp),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onInsert(text) },
            ) {
                Box(Modifier.widthIn(min = 38.dp).padding(horizontal = 10.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
                    Text(label, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelLarge, color = Palette.Text)
                }
            }
        }
        Spacer(Modifier.width(4.dp))
    }
}
