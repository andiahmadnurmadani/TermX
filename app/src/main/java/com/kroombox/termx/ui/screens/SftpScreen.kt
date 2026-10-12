package com.kroombox.termx.ui.screens

import com.kroombox.termx.i18n.S
import com.kroombox.termx.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.rounded.Close
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kroombox.termx.TermXApp
import com.kroombox.termx.ui.Nav
import com.kroombox.termx.ui.components.ConfirmDialog
import com.kroombox.termx.ui.components.formatBytes
import com.kroombox.termx.ui.theme.Mono
import com.kroombox.termx.ui.theme.MonoSmall
import com.kroombox.termx.ui.theme.Palette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.SFTPClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.EnumSet
import java.util.Locale

data class RemoteEntry(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val isLink: Boolean,
    val size: Long,
    val mtime: Long,
    val perms: Int,
)

data class Transfer(val name: String, val upload: Boolean, val done: Long, val total: Long)

/** Status & operasi SFTP untuk satu host. */
class SftpController(private val hostId: String, private val scope: CoroutineScope) {
    private val mgr = TermXApp.container.sessions
    private var sftp: SFTPClient? = null

    var path by mutableStateOf("")
    var entries by mutableStateOf<List<RemoteEntry>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var transfer by mutableStateOf<Transfer?>(null)
    var showHidden by mutableStateOf(false)

    private suspend fun client(): SFTPClient = withContext(Dispatchers.IO) {
        sftp ?: run {
            val conn = mgr.connectionFor(hostId)
            mgr.acquire(hostId, "sftp")
            conn.resetSftp()
            conn.sftp().also { sftp = it }
        }
    }

    private fun task(block: suspend () -> Unit) {
        scope.launch {
            loading = true
            error = null
            try {
                block()
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
                // Koneksi mungkin putus: paksa buat ulang di aksi berikutnya.
                if (e is java.io.IOException) sftp = null
            } finally {
                loading = false
            }
        }
    }

    fun open(initial: String?) = task {
        val c = client()
        path = withContext(Dispatchers.IO) { c.canonicalize(initial?.ifBlank { null } ?: ".") }
        listNow()
    }

    private suspend fun listNow() {
        val c = client()
        val p = path
        val list = withContext(Dispatchers.IO) {
            c.ls(p).filter { it.name != "." && it.name != ".." }.map { r ->
                val a = r.attributes
                val type = a.type
                RemoteEntry(
                    name = r.name,
                    path = r.path,
                    isDir = type == FileMode.Type.DIRECTORY,
                    isLink = type == FileMode.Type.SYMLINK,
                    size = a.size,
                    mtime = a.mtime * 1000,
                    perms = a.mode?.permissionsMask ?: 0,
                )
            }
        }
        entries = list.sortedWith(compareByDescending<RemoteEntry> { it.isDir }.thenBy { it.name.lowercase() })
    }

    fun refresh() = task { listNow() }

    fun cd(p: String) = task {
        val c = client()
        val target = withContext(Dispatchers.IO) { c.canonicalize(p) }
        path = target
        listNow()
    }

    /** Ikuti symlink: bila menunjuk ke direktori, masuk; bila tidak, kembalikan false. */
    fun openEntry(e: RemoteEntry, onFile: (RemoteEntry) -> Unit) {
        if (e.isDir) { cd(e.path); return }
        if (e.isLink) {
            task {
                val c = client()
                val isDir = withContext(Dispatchers.IO) {
                    try { c.stat(e.path).type == FileMode.Type.DIRECTORY } catch (_: Exception) { false }
                }
                if (isDir) { path = withContext(Dispatchers.IO) { c.canonicalize(e.path) }; listNow() } else onFile(e)
            }
            return
        }
        onFile(e)
    }

    fun up() {
        if (path == "/" || path.isEmpty()) return
        cd(path.substringBeforeLast('/', "").ifEmpty { "/" })
    }

    fun join(name: String) = if (path.endsWith("/")) "$path$name" else "$path/$name"

    fun mkdir(name: String) = task {
        val c = client()
        withContext(Dispatchers.IO) { c.mkdir(join(name)) }
        listNow()
    }

    fun rename(e: RemoteEntry, newName: String) = task {
        val c = client()
        withContext(Dispatchers.IO) { c.rename(e.path, join(newName)) }
        listNow()
    }

    fun delete(e: RemoteEntry) = task {
        val c = client()
        withContext(Dispatchers.IO) { deleteRecursive(c, e.path, e.isDir) }
        listNow()
    }

    private fun deleteRecursive(c: SFTPClient, p: String, dir: Boolean) {
        if (!dir) { c.rm(p); return }
        for (child in c.ls(p)) {
            if (child.name == "." || child.name == "..") continue
            val isDir = child.attributes.type == FileMode.Type.DIRECTORY
            deleteRecursive(c, child.path, isDir)
        }
        c.rmdir(p)
    }

    fun download(e: RemoteEntry, target: Uri, resolver: ContentResolver, onDone: (String) -> Unit) = task {
        val c = client()
        withContext(Dispatchers.IO) {
            val rf = c.open(e.path, EnumSet.of(OpenMode.READ))
            try {
                val total = rf.length()
                resolver.openOutputStream(target)?.use { out ->
                    val input = rf.RemoteFileInputStream(0)
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastUi = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (done - lastUi > 256 * 1024 || done == total) {
                            lastUi = done
                            transfer = Transfer(e.name, false, done, total)
                        }
                    }
                } ?: throw java.io.IOException(S.get(R.string.sftp_err_cannot_write_target))
            } finally {
                rf.close()
                transfer = null
            }
        }
        onDone(S.get(R.string.sftp_downloaded, e.name))
    }

    fun upload(source: Uri, resolver: ContentResolver, onDone: (String) -> Unit) = task {
        val c = client()
        val name = displayName(resolver, source) ?: "upload-${System.currentTimeMillis()}"
        val dest = join(name)
        withContext(Dispatchers.IO) {
            val total = resolver.query(source, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cur ->
                if (cur.moveToFirst() && !cur.isNull(0)) cur.getLong(0) else -1L
            } ?: -1L
            val rf = c.open(dest, EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC))
            try {
                resolver.openInputStream(source)?.use { input ->
                    val out = rf.RemoteFileOutputStream(0, 16)
                    val buf = ByteArray(32 * 1024)
                    var done = 0L
                    var lastUi = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (done - lastUi > 256 * 1024) {
                            lastUi = done
                            transfer = Transfer(name, true, done, total)
                        }
                    }
                    out.close()
                } ?: throw java.io.IOException(S.get(R.string.sftp_err_cannot_read_source))
            } finally {
                rf.close()
                transfer = null
            }
        }
        listNow()
        onDone(S.get(R.string.sftp_uploaded, name))
    }

    fun readText(e: RemoteEntry, onLoaded: (String) -> Unit) = task {
        if (e.size > 512 * 1024) throw java.io.IOException(S.get(R.string.sftp_err_too_large))
        val c = client()
        val text = withContext(Dispatchers.IO) {
            val rf = c.open(e.path, EnumSet.of(OpenMode.READ))
            try { rf.RemoteFileInputStream(0).readBytes().toString(Charsets.UTF_8) } finally { rf.close() }
        }
        onLoaded(text)
    }

    fun writeText(e: RemoteEntry, text: String, onDone: (String) -> Unit) = task {
        val c = client()
        withContext(Dispatchers.IO) {
            val rf = c.open(e.path, EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC))
            try {
                val out = rf.RemoteFileOutputStream(0, 16)
                out.write(text.toByteArray(Charsets.UTF_8))
                out.close()
            } finally {
                rf.close()
            }
        }
        listNow()
        onDone(S.get(R.string.sftp_saved, e.name))
    }

    fun close() {
        sftp = null
        mgr.scope.launch(Dispatchers.IO) {
            mgr.existingConnection(hostId)?.resetSftp()
            mgr.release(hostId, "sftp")
        }
    }

    companion object {
        fun displayName(resolver: ContentResolver, uri: Uri): String? =
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
    }
}

/** Format tanggal mengikuti bahasa aplikasi. */
private val dateFmt: SimpleDateFormat
    get() = SimpleDateFormat("dd MMM yyyy HH:mm", com.kroombox.termx.i18n.Lang.context().resources.configuration.locales[0])

fun permString(mask: Int): String {
    val chars = "rwxrwxrwx"
    return buildString {
        for (i in 0 until 9) append(if (mask and (1 shl (8 - i)) != 0) chars[i] else '-')
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SftpScreen(nav: Nav, hostId: String, initialPath: String?, sessionId: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val host = remember(hostId) { TermXApp.container.vault.host(hostId) }
    val ctl = remember(hostId) { SftpController(hostId, scope) }
    var toast by remember { mutableStateOf<String?>(null) }
    var pendingDownload by remember { mutableStateOf<RemoteEntry?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<RemoteEntry?>(null) }
    var deleting by remember { mutableStateOf<RemoteEntry?>(null) }
    var newFile by remember { mutableStateOf(false) }
    var topMenu by remember { mutableStateOf(false) }
    // Folder terakhir disimpan, agar kembali dari editor tidak melompat ke folder awal.
    var lastPath by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialPath) }
    LaunchedEffect(ctl.path) { if (ctl.path.isNotEmpty()) lastPath = ctl.path }
    // Sesi SFTP di tab Sesi ikut diperbarui: folder sekarang + beberapa entrinya untuk pratinjau.
    LaunchedEffect(ctl.path, ctl.entries) {
        if (sessionId != null && ctl.path.isNotEmpty()) {
            val preview = ctl.entries.filterNot { it.name.startsWith(".") }.take(8).map { (if (it.isDir) "📁 " else "📄 ") + it.name }
            TermXApp.container.sessions.updateSftp(sessionId, ctl.path, preview)
        }
    }

    LaunchedEffect(hostId) { ctl.open(lastPath) }
    DisposableEffect(hostId) { onDispose { ctl.close() } }
    LaunchedEffect(toast) {
        toast?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show(); toast = null }
    }

    val downloader = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val e = pendingDownload
        pendingDownload = null
        if (uri != null && e != null) ctl.download(e, uri, context.contentResolver) { toast = it }
    }
    val uploader = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) ctl.upload(uri, context.contentResolver) { toast = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SFTP", style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
                        Text(
                            host?.label ?: "", style = MaterialTheme.typography.labelMedium, color = Palette.TextDim,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.sftp_back)) } },
                // Hanya aksi utama di bar; sisanya di menu ⋮ supaya judul tidak terdesak.
                actions = {
                    IconButton(onClick = { ctl.refresh() }) { Icon(Icons.Rounded.Refresh, stringResource(R.string.sftp_refresh)) }
                    IconButton(onClick = { newFile = true }) { Icon(Icons.AutoMirrored.Rounded.NoteAdd, stringResource(R.string.sftp_new_file)) }
                    Box {
                        IconButton(onClick = { topMenu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.sftp_menu)) }
                        DropdownMenu(expanded = topMenu, onDismissRequest = { topMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sftp_parent_folder)) }, leadingIcon = { Icon(Icons.Rounded.ArrowUpward, null) },
                                onClick = { topMenu = false; ctl.up() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sftp_new_folder)) }, leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, null) },
                                onClick = { topMenu = false; newFolder = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(if (ctl.showHidden) R.string.sftp_hide_hidden else R.string.sftp_show_hidden)) },
                                leadingIcon = { Icon(Icons.Rounded.Visibility, null, tint = if (ctl.showHidden) Palette.Accent else Palette.TextDim) },
                                onClick = { topMenu = false; ctl.showHidden = !ctl.showHidden },
                            )
                            if (sessionId != null) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sftp_close_session), color = Palette.Danger) },
                                    leadingIcon = { Icon(Icons.Rounded.Close, null, tint = Palette.Danger) },
                                    onClick = { topMenu = false; TermXApp.container.sessions.closeSftp(sessionId); nav.back() },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { uploader.launch(arrayOf("*/*")) }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Rounded.FileUpload, stringResource(R.string.sftp_upload))
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Breadcrumb(ctl.path) { ctl.cd(it) }
            if (ctl.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            ctl.transfer?.let { t ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        stringResource(if (t.upload) R.string.sftp_uploading else R.string.sftp_downloading, t.name) +
                            " — ${formatBytes(t.done)}${if (t.total > 0) " / ${formatBytes(t.total)}" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (t.total > 0) LinearProgressIndicator(progress = { t.done.toFloat() / t.total }, modifier = Modifier.fillMaxWidth())
                }
            }
            ctl.error?.let { Text(it, color = Palette.Danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp)) }
            val visible = ctl.entries.filter { ctl.showHidden || !it.name.startsWith(".") }
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(visible, key = { it.path }) { e ->
                    EntryRow(
                        e,
                        // File teks/kecil dibuka di editor; file besar diunduh.
                        onOpen = {
                            ctl.openEntry(e) { f ->
                                if (f.size <= 2L * 1024 * 1024) nav.editor(hostId, f.path)
                                else { pendingDownload = f; downloader.launch(f.name) }
                            }
                        },
                        onDownload = { pendingDownload = e; downloader.launch(e.name) },
                        onView = { nav.editor(hostId, e.path) },
                        onRename = { renaming = e },
                        onDelete = { deleting = e },
                        onCopyPath = { copy(context, e.path) },
                    )
                }
            }
        }
    }

    if (newFolder) {
        NameDialog(stringResource(R.string.sftp_new_folder), "", onDismiss = { newFolder = false }) { ctl.mkdir(it) }
    }
    renaming?.let { e -> NameDialog(stringResource(R.string.sftp_rename), e.name, onDismiss = { renaming = null }) { ctl.rename(e, it) } }
    deleting?.let { e ->
        ConfirmDialog(
            title = stringResource(if (e.isDir) R.string.sftp_delete_folder_title else R.string.sftp_delete_file_title),
            message = stringResource(if (e.isDir) R.string.sftp_delete_folder_message else R.string.sftp_delete_file_message, e.name),
            confirmText = stringResource(R.string.sftp_delete), danger = true,
            onConfirm = { ctl.delete(e) }, onDismiss = { deleting = null },
        )
    }
    if (newFile) {
        NameDialog(stringResource(R.string.sftp_new_file), "", onDismiss = { newFile = false }) { name -> nav.editor(hostId, ctl.join(name), isNew = true) }
    }
}

@Composable
private fun Breadcrumb(path: String, onNavigate: (String) -> Unit) {
    val parts = path.split('/').filter { it.isNotEmpty() }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("/", fontFamily = Mono, color = Palette.Accent, modifier = Modifier.clickable { onNavigate("/") }.padding(4.dp))
        var acc = ""
        parts.forEach { p ->
            acc += "/$p"
            val target = acc
            Text(p, fontFamily = Mono, color = Palette.Text, modifier = Modifier.clickable { onNavigate(target) }.padding(4.dp))
            Text("/", fontFamily = Mono, color = Palette.TextDim)
        }
    }
}

@Composable
private fun EntryRow(
    e: RemoteEntry,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onView: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCopyPath: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when {
                e.isDir -> Icons.Rounded.Folder
                e.isLink -> Icons.Rounded.Link
                else -> Icons.Rounded.Description
            },
            null,
            tint = if (e.isDir) Palette.Blue else Palette.TextDim,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(e.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    if (!e.isDir) formatBytes(e.size) else null,
                    if (e.mtime > 0) dateFmt.format(Date(e.mtime)) else null,
                    permString(e.perms),
                ).joinToString("  ·  "),
                style = MonoSmall,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.sftp_menu)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (!e.isDir) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.sftp_download)) }, leadingIcon = { Icon(Icons.Rounded.FileDownload, null) }, onClick = { menu = false; onDownload() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.sftp_view_edit)) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onView() })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.sftp_rename)) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text(stringResource(R.string.sftp_copy_path)) }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = { menu = false; onCopyPath() })
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sftp_delete), color = Palette.Danger) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Palette.Danger) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.sftp_name)) }) },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && !name.contains('/'), onClick = { onDismiss(); onConfirm(name.trim()) }) { Text(stringResource(R.string.sftp_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.sftp_cancel)) } },
    )
}
