package com.kroombox.termx.ui.screens

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Switch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kroombox.termx.R
import com.kroombox.termx.TermXApp
import com.kroombox.termx.agent.AgentInfo
import com.kroombox.termx.agent.ChatItem
import com.kroombox.termx.agent.ChatMeta
import com.kroombox.termx.agent.ChatState
import com.kroombox.termx.agent.Choice
import com.kroombox.termx.agent.LimitWindow
import com.kroombox.termx.agent.Link
import com.kroombox.termx.agent.NoteItem
import com.kroombox.termx.agent.PermItem
import com.kroombox.termx.agent.RunningAgent
import com.kroombox.termx.agent.ScreenItem
import com.kroombox.termx.agent.RunningInfo
import com.kroombox.termx.agent.SlashCommand
import com.kroombox.termx.agent.TextItem
import com.kroombox.termx.agent.ToolItem
import com.kroombox.termx.agent.TurnItem
import com.kroombox.termx.agent.UserItem
import com.kroombox.termx.i18n.S
import com.kroombox.termx.ui.Nav
import com.kroombox.termx.ui.components.AppCard
import com.kroombox.termx.ui.components.ConfirmDialog
import com.kroombox.termx.ui.components.EmptyState
import com.kroombox.termx.ui.components.MarkdownParser
import com.kroombox.termx.ui.components.MarkdownText
import com.kroombox.termx.ui.components.SectionHeader
import com.kroombox.termx.ui.components.StatusDot
import com.kroombox.termx.ui.components.UsageBar
import com.kroombox.termx.ui.components.timeAgo
import com.kroombox.termx.ui.components.usageColor
import com.kroombox.termx.ui.theme.Mono
import com.kroombox.termx.ui.theme.Palette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

// ======================================================================== helper

/** Nama agent untuk tampilan (katalog di server, atau id bila belum terdeteksi). */
private fun agentName(id: String, agents: List<AgentInfo>): String =
    agents.firstOrNull { it.id == id }?.name ?: id.replaceFirstChar { it.uppercase() }

private fun shortPath(p: String): String {
    val home = Regex("^/(home/[^/]+|root)(?=/|$)")
    val s = home.replace(p, "~")
    return if (s.length <= 34) s else "…" + s.takeLast(32)
}

private fun modeLabel(id: String): String = when (id) {
    "manual", "default" -> S.get(R.string.agent_mode_manual)
    "acceptEdits" -> S.get(R.string.agent_mode_accept_edits)
    "plan" -> S.get(R.string.agent_mode_plan)
    "auto" -> S.get(R.string.agent_mode_auto)
    "bypassPermissions" -> S.get(R.string.agent_mode_bypass)
    "workspace-write" -> S.get(R.string.agent_mode_workspace_write)
    "read-only" -> S.get(R.string.agent_mode_read_only)
    "" -> S.get(R.string.agent_default)
    else -> id.replaceFirstChar { it.uppercase() }
}

private fun modelLabel(id: String, choices: List<Choice>): String =
    choices.firstOrNull { it.id == id }?.name?.ifBlank { null } ?: id.ifBlank { S.get(R.string.agent_default) }

private fun formatTokens(n: Long): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
    n >= 1_000 -> String.format(Locale.US, "%.1fk", n / 1_000.0)
    else -> n.toString()
}

private fun formatCost(c: Double): String = if (c < 0.01) String.format(Locale.US, "$%.4f", c) else String.format(Locale.US, "$%.2f", c)

/** Persen 0..100 dari nilai utilization provider (pecahan 0..1 atau persen). */
private fun limitPercent(w: LimitWindow): Double? = w.used?.let { if (it <= 1.0) it * 100 else it }

private fun limitLabel(key: String): String = when (key) {
    "five_hour" -> S.get(R.string.agent_limit_5h)
    "seven_day" -> S.get(R.string.agent_limit_7d)
    "seven_day_opus" -> S.get(R.string.agent_limit_7d_opus)
    "seven_day_sonnet" -> S.get(R.string.agent_limit_7d_sonnet)
    else -> key.replace('_', ' ')
}

private fun resetIn(epochSec: Long?): String {
    if (epochSec == null) return ""
    val sec = epochSec - System.currentTimeMillis() / 1000
    if (sec <= 0) return S.get(R.string.agent_limit_reset_now)
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val d = h / 24
    return when {
        d > 0 -> S.get(R.string.agent_limit_resets_dh, d, h % 24)
        h > 0 -> S.get(R.string.agent_limit_resets_hm, h, m)
        else -> S.get(R.string.agent_limit_resets_m, m)
    }
}

/** Error yang biasanya berarti agent belum login / belum punya API key. */
private fun looksLikeLogin(msg: String): Boolean {
    val m = msg.lowercase()
    return listOf(
        "401", "unauthorized", "api key", "api_key", "apikey", "not logged in", "log in", "login", "/login",
        "authentication", "no llm provider", "not configured", "no model", "credentials", "invalid x-api-key",
    ).any { m.contains(it) }
}

private fun dayBucket(epochMs: Long): Int {
    val now = Calendar.getInstance()
    val c = Calendar.getInstance().apply { timeInMillis = epochMs }
    val sameYear = now.get(Calendar.YEAR) == c.get(Calendar.YEAR)
    val dn = now.get(Calendar.DAY_OF_YEAR) - c.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dn == 0 -> 0
        sameYear && dn == 1 -> 1
        sameYear && dn < 7 -> 2
        else -> 3
    }
}

// ======================================================================== beranda agent

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AgentHomeScreen(nav: Nav, hostId: String) {
    val mgr = TermXApp.container.agents
    val host = remember(hostId) { TermXApp.container.vault.host(hostId) }
    var agents by remember { mutableStateOf(mgr.cachedAgents(hostId)) }
    var chats by remember { mutableStateOf<List<ChatMeta>?>(null) }
    var running by remember { mutableStateOf<RunningInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var noPythonOs by remember { mutableStateOf<String?>(null) }
    var pyInstalling by remember { mutableStateOf(false) }
    var pyError by remember { mutableStateOf<String?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var renaming by remember { mutableStateOf<ChatMeta?>(null) }
    var deleting by remember { mutableStateOf<ChatMeta?>(null) }
    var opening by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var usage by remember { mutableStateOf<List<com.kroombox.termx.agent.ProviderUsage>?>(null) }
    var newAgent by remember { mutableStateOf<String?>(null) }
    var openError by remember { mutableStateOf<String?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var hermesSetup by remember { mutableStateOf(false) }
    var hermes by remember { mutableStateOf<com.kroombox.termx.agent.HermesStatus?>(null) }
    val hasHermes = agents.any { it.id == "hermes" }
    val scope = rememberCoroutineScope()
    val live by mgr.chats.collectAsState()
    val limits = remember(refreshKey, chats) { mgr.savedLimits(hostId) }

    DisposableEffect(hostId) {
        TermXApp.container.sessions.acquire(hostId, "agents:home")
        onDispose { TermXApp.container.sessions.release(hostId, "agents:home") }
    }

    LaunchedEffect(hasHermes, refreshKey) {
        if (hasHermes) runCatching { hermes = mgr.hermesStatus(hostId) }
    }

    LaunchedEffect(refreshKey) {
        loading = true
        error = null
        noPythonOs = null
        try {
            coroutineScope {
                val a = async { mgr.detect(hostId) }
                val c = async { mgr.list(hostId) }
                val r = async { runCatching { mgr.running(hostId) }.getOrNull() }
                agents = a.await()
                chats = c.await()
                running = r.await()
            }
            // Akun & kuota: hasil tersimpan dulu (seketika), lalu yang segar di latar.
            launch {
                if (usage == null) runCatching { usage = mgr.usage(hostId, cached = true) }
                runCatching { usage = mgr.usage(hostId) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            noPythonOs = (e as? com.kroombox.termx.agent.NoPythonException)?.os
            error = e.message ?: e.toString()
        }
        loading = false
        // Status percakapan diperbarui diam-diam selama layar terbuka.
        var tick = 0
        while (error == null) {
            delay(6000)
            runCatching { chats = mgr.list(hostId) }
            runCatching { running = mgr.running(hostId) }
            if (++tick % 10 == 0) runCatching { usage = mgr.usage(hostId) }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.agent_title), style = MaterialTheme.typography.titleMedium)
                        Text(host?.label ?: "", style = MaterialTheme.typography.labelMedium, color = Palette.TextDim)
                    }
                },
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.mon_back)) } },
                actions = {
                    IconButton(onClick = { refreshKey++ }, enabled = !loading) { Icon(Icons.Rounded.Refresh, stringResource(R.string.agent_refresh)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if (agents.any { it.isChat }) {
                ExtendedFloatingActionButton(
                    onClick = { showNew = true },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text(stringResource(R.string.agent_new_chat)) },
                    containerColor = Palette.Accent,
                    contentColor = Palette.Bg,
                )
            }
        },
    ) { pad ->
        val chatAgents = agents.filter { it.isChat }
        val termAgents = agents.filter { !it.isChat }
        when {
            loading && agents.isEmpty() && chats == null -> Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                com.kroombox.termx.ui.components.RadarLoader(
                    stringResource(R.string.agent_detect_title),
                    stringResource(R.string.agent_detect_sub, host?.label ?: ""),
                    logos = DETECT_LOGOS,
                )
            }
            error != null && agents.isEmpty() && noPythonOs != null -> Box(Modifier.padding(pad)) {
                val os = noPythonOs ?: ""
                val how = when (os) {
                    com.kroombox.termx.agent.AgentManager.OS_WINDOWS -> stringResource(R.string.agent_python_how_windows)
                    com.kroombox.termx.agent.AgentManager.OS_LINUX -> stringResource(R.string.agent_python_how_linux)
                    com.kroombox.termx.agent.AgentManager.OS_MAC -> stringResource(R.string.agent_python_how_mac)
                    else -> stringResource(R.string.agent_python_unknown_os)
                }
                EmptyState(Icons.Rounded.CloudOff, stringResource(R.string.agent_python_title), "${error ?: ""}\n\n$how") {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (pyInstalling) {
                            com.kroombox.termx.ui.components.WaveDots()
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.agent_python_installing), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                        } else {
                            if (os != com.kroombox.termx.agent.AgentManager.OS_UNKNOWN) {
                                Button(onClick = {
                                    pyInstalling = true
                                    pyError = null
                                    scope.launch {
                                        try {
                                            mgr.installPython(hostId, os)
                                            refreshKey++
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            pyError = e.message ?: e.toString()
                                        } finally {
                                            pyInstalling = false
                                        }
                                    }
                                }) { Text(stringResource(R.string.agent_python_install)) }
                                Spacer(Modifier.height(8.dp))
                            }
                            androidx.compose.material3.TextButton(onClick = { refreshKey++ }) { Text(stringResource(R.string.agent_retry)) }
                        }
                        pyError?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
            error != null && agents.isEmpty() -> Box(Modifier.padding(pad)) {
                EmptyState(Icons.Rounded.CloudOff, stringResource(R.string.agent_error_title), error ?: "") {
                    Button(onClick = { refreshKey++ }) { Text(stringResource(R.string.agent_retry)) }
                }
            }
            !loading && agents.isEmpty() -> Box(Modifier.padding(pad)) {
                EmptyState(Icons.Rounded.AutoAwesome, stringResource(R.string.agent_none_title), stringResource(R.string.agent_none_message))
            }
            else -> LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            ) {
                val run = running?.agents.orEmpty()
                item("dock") {
                    AgentDock(
                        chatAgents + termAgents,
                        busy = (run.filter { (it.herdrStatus.ifBlank { it.status }) == "working" }.map { it.agent } +
                            chats.orEmpty().filter { it.status == "working" }.map { it.agent }).toSet(),
                    ) { a ->
                        if (a.isChat) { newAgent = a.id; showNew = true }
                        else nav.agentTerminal(hostId, a.id, a.bin, mgr.recentFolders(hostId).firstOrNull() ?: "~")
                    }
                }
                if (hasHermes) item("hermes") { HermesCard(hermes) { hermesSetup = true } }
                if (termAgents.isNotEmpty()) item("dock-hint") {
                    Text(
                        stringResource(R.string.agent_terminal_hint), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
                    )
                }

                if (run.isNotEmpty()) {
                    item("run-h") { AgentSection(stringResource(R.string.agent_running)) }
                    run.forEach { r ->
                        item("run-${r.where}-${r.pid}-${r.herdrPane}") {
                            RunningRow(
                                r, agentName(r.agent, agents), busy = opening == r.pid to r.herdrPane,
                                onChat = {
                                    if (r.where == "termx" && r.chat.isNotBlank()) nav.agentChat(hostId, r.chat)
                                    else if (opening == null) {
                                        opening = r.pid to r.herdrPane
                                        scope.launch {
                                            runCatching { mgr.mirror(hostId, r) }
                                                .onSuccess { nav.agentChat(hostId, it) }
                                                .onFailure { e -> openError = e.message ?: e.toString() }
                                            opening = null
                                        }
                                    }
                                },
                                onTerminal = { takeover ->
                                    when (r.where) {
                                        "tmux" -> scope.launch { mgr.selectTmuxPane(hostId, r); nav.attachTmux(hostId, r.tmuxSession) }
                                        "herdr" -> nav.attachHerdr(hostId, running?.herdrBin ?: "", r.herdrPane, takeover)
                                    }
                                },
                            )
                        }
                    }
                }

                item("usage-h") { AgentSection(stringResource(R.string.agent_usage_section)) }
                item("usage") { UsageSection(usage, limits) }

                val list = chats.orEmpty()
                item("chats-h") {
                    AgentSection(stringResource(R.string.agent_conversations), trailing = {
                        if (loading) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Palette.TextDim)
                    })
                }
                if (list.isEmpty() && chats != null) {
                    item("chats-empty") {
                        Text(
                            stringResource(R.string.agent_no_chats), color = Palette.TextDim,
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        )
                    }
                }
                var lastBucket = -1
                list.forEach { m ->
                    val b = dayBucket(m.updated)
                    if (b != lastBucket) {
                        lastBucket = b
                        item("b$b") {
                            Text(
                                stringResource(
                                    when (b) { 0 -> R.string.agent_today; 1 -> R.string.agent_yesterday; 2 -> R.string.agent_this_week; else -> R.string.agent_older },
                                ),
                                style = MaterialTheme.typography.labelMedium, color = Palette.TextDim,
                                modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 6.dp),
                            )
                        }
                    }
                    item(m.id) {
                        ChatRow(
                            m, agentName(m.agent, agents), live.containsKey(m.id),
                            onOpen = { nav.agentChat(hostId, m.id) },
                            onRename = { renaming = m },
                            onDelete = { deleting = m },
                        )
                    }
                }
            }
        }
    }

    openError?.let { msg ->
        AlertDialog(
            onDismissRequest = { openError = null },
            title = { Text(stringResource(R.string.agent_running_open_failed)) },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { openError = null; refreshKey++ }) { Text(stringResource(R.string.agent_refresh)) } },
        )
    }

    if (showNew) {
        NewChatSheet(
            hostId = hostId,
            agents = agents.filter { it.isChat },
            preselect = newAgent,
            onDismiss = { showNew = false; newAgent = null },
            onCreated = { id ->
                showNew = false
                nav.agentChat(hostId, id)
            },
        )
    }
    renaming?.let { m ->
        RenameDialog(m.title, onDismiss = { renaming = null }) { title ->
            renaming = null
            scope.launch { runCatching { mgr.rename(hostId, m.id, title) }; refreshKey++ }
        }
    }
    deleteError?.let { msg ->
        AlertDialog(
            onDismissRequest = { deleteError = null },
            icon = { Icon(Icons.Rounded.ErrorOutline, null, tint = Palette.Danger) },
            title = { Text(stringResource(R.string.agent_delete_failed)) },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { deleteError = null; refreshKey++ }) { Text(stringResource(R.string.agent_refresh)) } },
        )
    }

    if (hermesSetup) {
        HermesSetupSheet(
            hostId, onDismiss = { hermesSetup = false },
            onSaved = { scope.launch { runCatching { hermes = mgr.hermesStatus(hostId) } } },
            onTerminal = {
                val bin = agents.firstOrNull { it.id == "hermes" }?.bin?.ifBlank { null } ?: "hermes"
                nav.agentTerminal(hostId, "hermes-setup", "$bin model", "~")
            },
        )
    }

    deleting?.let { m ->
        ConfirmDialog(
            title = stringResource(R.string.agent_delete_title),
            message = stringResource(R.string.agent_delete_message),
            confirmText = stringResource(R.string.agent_delete),
            danger = true,
            onConfirm = {
                deleting = null
                chats = chats?.filter { it.id != m.id }  // langsung hilang dari daftar
                running = running?.let { ri -> ri.copy(agents = ri.agents.filter { it.chat != m.id || it.where != "termx" }) }
                scope.launch {
                    runCatching { mgr.delete(hostId, m.id) }
                        .onFailure { e -> deleteError = (e.message ?: e.toString()) }
                    runCatching { chats = mgr.list(hostId) }
                    runCatching { running = mgr.running(hostId) }
                }
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
internal fun UsageCard(limits: List<LimitWindow>, at: Long) {
    AppCard(modifier = Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Timelapse, null, tint = Palette.TextDim, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.agent_usage_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (at > 0) Text(timeAgo(at), style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
        }
        limits.forEach { w ->
            val p = limitPercent(w)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(limitLabel(w.key), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    if (p == null) "–" else "${p.toInt()}%",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    color = if (p == null) Palette.TextDim else usageColor(p),
                )
            }
            Spacer(Modifier.height(6.dp))
            UsageBar(((p ?: 0.0) / 100).toFloat(), if (p == null) Palette.TextDim else usageColor(p))
            val r = resetIn(w.resetEpochSec)
            if (r.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(r, style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
            }
        }
    }
}

/** Judul bagian di layar agent: kalimat biasa, bukan huruf kapital berjarak. */
@Composable
internal fun AgentSection(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 26.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Palette.Text, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/**
 * Deretan agent yang terpasang. Agent yang sedang bekerja diberi cincin warna mereknya — satu-satunya
 * aksen kuat di beranda, sehingga sekilas terlihat siapa yang sedang sibuk.
 */
@Composable
internal fun AgentDock(agents: List<AgentInfo>, busy: Set<String>, onTap: (AgentInfo) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        agents.forEach { a ->
            val color = agentColor(a.id)
            val working = a.id in busy
            Surface(
                onClick = { onTap(a) },
                shape = RoundedCornerShape(20.dp),
                color = Palette.Surface,
                border = if (working) androidx.compose.foundation.BorderStroke(1.5.dp, color.copy(alpha = 0.85f)) else null,
                modifier = Modifier.width(100.dp),
            ) {
                Column(Modifier.padding(top = 14.dp, bottom = 12.dp, start = 6.dp, end = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        AgentGlyph(a.id, 44.dp)
                        if (working) Box(Modifier.offset(x = 3.dp, y = (-3).dp)) { PulseDot(Palette.Accent) }
                    }
                    Spacer(Modifier.height(9.dp))
                    Text(a.name.removeSuffix(" Agent"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                        color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (working) stringResource(R.string.agent_working_short)
                        else if (a.isChat) a.version.substringBefore(' ').ifBlank { stringResource(R.string.agent_kind_chat) }
                        else stringResource(R.string.agent_kind_terminal),
                        style = MaterialTheme.typography.labelSmall, color = if (working) Palette.Accent else Palette.TextDim,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun authLabel(u: com.kroombox.termx.agent.ProviderUsage): String? = when {
    u.agent == "claude" && u.loggedIn == false -> S.get(R.string.agent_acct_signed_out)
    u.agent == "claude" && u.authMethod == "claude.ai" ->
        if (u.plan.isNotBlank()) S.get(R.string.agent_acct_plan, u.plan.replaceFirstChar { it.uppercase() }) else S.get(R.string.agent_acct_subscription)
    u.agent == "claude" && u.provider.isNotBlank() && u.provider != "firstParty" -> S.get(R.string.agent_acct_cloud, u.provider)
    u.agent == "claude" && u.authMethod.isNotBlank() -> S.get(R.string.agent_acct_api_key)
    u.agent == "codex" -> u.authMethod.ifBlank { null }
    else -> null
}

/** Penjelasan sumber angka (atau mengapa tidak ada), agar pengguna tahu apa yang bisa dipercaya. */
private fun usageNote(u: com.kroombox.termx.agent.ProviderUsage): String? = when {
    u.agent == "claude" && u.authMethod == "claude.ai" && u.limits.isEmpty() -> S.get(R.string.agent_note_claude_limits_pending)
    u.agent == "claude" && u.authMethod == "claude.ai" -> S.get(R.string.agent_note_claude_limits)
    u.agent == "claude" && u.provider.isNotBlank() && u.provider != "firstParty" -> S.get(R.string.agent_note_cloud_billing)
    u.agent == "claude" && u.loggedIn == true -> S.get(R.string.agent_note_api_credit)
    u.agent == "codex" && u.loggedIn == false -> S.get(R.string.agent_note_codex_login)
    u.agent == "codex" && u.limits.isEmpty() -> S.get(R.string.agent_note_codex_pending)
    u.agent == "codex" -> S.get(R.string.agent_note_codex_limits)
    u.agent == "opencode" -> S.get(R.string.agent_note_opencode)
    else -> null
}

@Composable
internal fun UsageSection(usage: List<com.kroombox.termx.agent.ProviderUsage>?, saved: Pair<List<LimitWindow>, Long>) {
    if (usage == null) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Palette.TextDim)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.agent_usage_loading), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
        }
        return
    }
    val rich = usage.filter { it.reported || it.cost > 0 }
    val quiet = usage.filter { it !in rich }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rich.forEach { u ->
            // Kuota Claude yang tersimpan di HP dipakai bila server belum punya catatan.
            val limits = u.limits.ifEmpty { if (u.agent == "claude") saved.first else emptyList() }
            val at = if (u.limits.isNotEmpty()) u.limitsAt else saved.second
            ProviderCard(u, limits, at)
        }
        if (quiet.isNotEmpty()) {
            Text(
                stringResource(R.string.agent_usage_unreported, quiet.joinToString(", ") { it.name }),
                style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
internal fun ProviderCard(u: com.kroombox.termx.agent.ProviderUsage, limits: List<LimitWindow>, at: Long) {
    val color = agentColor(u.agent)
    Surface(shape = RoundedCornerShape(18.dp), color = Palette.Surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AgentGlyph(u.agent, 34.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(u.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Palette.Text)
                    val acct = authLabel(u)
                    if (acct != null) {
                        Text(
                            acct, style = MaterialTheme.typography.bodySmall,
                            color = if (u.loggedIn == false) Palette.Warn else Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (at > 0 && limits.isNotEmpty()) Text(timeAgo(at), style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
            }
            if (u.account.isNotBlank() || u.org.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    listOf(u.account, u.org).filter { it.isNotBlank() }.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            limits.forEach { w ->
                val p = limitPercent(w)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(limitLabel(w.key), style = MaterialTheme.typography.bodyMedium, color = Palette.Text, modifier = Modifier.weight(1f))
                    Text(
                        if (p == null) "–" else stringResource(R.string.agent_limit_left, (100 - p).coerceIn(0.0, 100.0).toInt()),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                        color = if (p == null) Palette.TextDim else usageColor(p),
                    )
                }
                Spacer(Modifier.height(7.dp))
                UsageBar(((p ?: 0.0) / 100).toFloat(), if (p == null) Palette.TextDim else usageColor(p))
                val r = resetIn(w.resetEpochSec)
                if (r.isNotEmpty()) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        if (p != null) stringResource(R.string.agent_limit_used_reset, p.toInt(), r) else r,
                        style = MaterialTheme.typography.labelSmall, color = Palette.TextDim,
                    )
                }
            }
            if (u.stats.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                u.stats.take(6).forEach { (k, v) ->
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text(k, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, modifier = Modifier.weight(1f))
                        Text(v, style = MaterialTheme.typography.bodySmall, color = Palette.Text)
                    }
                }
            }
            if (u.cost > 0 || u.tokensOut > 0) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 9.dp)) {
                    Text(stringResource(R.string.agent_usage_termx, u.chats), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, modifier = Modifier.weight(1f))
                    Text(
                        if (u.cost > 0) formatCost(u.cost) else formatTokens(u.tokensIn + u.tokensOut) + " tok",
                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Palette.Text,
                    )
                }
            }
            usageNote(u)?.let { n ->
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Info, null, tint = Palette.TextDim, modifier = Modifier.padding(top = 1.dp).size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(n, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FlowAgents(chat: List<AgentInfo>, term: List<AgentInfo>, onTap: (AgentInfo) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (chat + term).forEach { a ->
                Surface(
                    onClick = { onTap(a) },
                    shape = RoundedCornerShape(14.dp),
                    color = Palette.SurfaceHigh,
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        AgentGlyph(a.id, size = 26.dp)
                        Spacer(Modifier.width(9.dp))
                        Column {
                            Text(a.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(a.version.ifBlank { null }, if (a.isChat) null else S.get(R.string.agent_kind_terminal)).joinToString("  "),
                                style = MaterialTheme.typography.labelSmall, color = Palette.TextDim, maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        if (term.isNotEmpty()) {
            Text(stringResource(R.string.agent_terminal_hint), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

/** Warna merek agent (dipakai untuk logo & monogram). */
internal fun agentColor(id: String): Color = when (id) {
    "claude" -> Color(0xFFD97757)
    "codex", "opencode", "goose" -> Color(0xFFE6EDF3)
    "antigravity" -> Color(0xFF4F8DF7)
    "hermes" -> Color(0xFFF2C14E)
    "gemini" -> Color(0xFF8AB4F8)
    "qwen" -> Color(0xFF8B7BFF)
    "pi" -> Color(0xFF7FD1B9)
    "omp" -> Color(0xFFC79BF2)
    else -> Palette.TextDim
}

private val DETECT_LOGOS = listOf("claude", "opencode", "hermes", "antigravity", "codex", "gemini").map {
    com.kroombox.termx.ui.components.OrbitLogo(agentLogo(it)!!, agentColor(it).let { c -> if (c == Color(0xFFE6EDF3)) Palette.Text else c })
}

/** Logo resmi agent (Lobe Icons) bila ada; selain itu monogram. */
internal fun agentLogo(id: String): Int? = when (id) {
    "claude" -> R.drawable.ic_agent_claude
    "codex" -> R.drawable.ic_agent_codex
    "opencode" -> R.drawable.ic_agent_opencode
    "hermes" -> R.drawable.ic_agent_hermes
    "gemini" -> R.drawable.ic_agent_gemini
    "qwen" -> R.drawable.ic_agent_qwen
    "goose" -> R.drawable.ic_agent_goose
    "antigravity" -> R.drawable.ic_agent_antigravity
    else -> null
}

@Composable
internal fun AgentGlyph(id: String, size: androidx.compose.ui.unit.Dp = 32.dp) {
    val color = agentColor(id)
    val logo = agentLogo(id)
    val neutral = color == Color(0xFFE6EDF3)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.32f))
            .background(if (neutral) Palette.SurfaceHighest else color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            Icon(androidx.compose.ui.res.painterResource(logo), null, tint = color, modifier = Modifier.size(size * 0.58f))
        } else {
            Text(
                if (id == "omp") "π+" else if (id == "pi") "π" else id.take(1).uppercase(),
                color = color, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.46f).sp,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChatRow(m: ChatMeta, agent: String, connected: Boolean, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val working = m.status == "working"
    Box {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AgentGlyph(m.agent, 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    m.title.ifBlank { stringResource(R.string.agent_untitled) },
                    style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (m.title.isBlank()) Palette.TextDim else Palette.Text,
                )
                Text(
                    "$agent  ·  ${shortPath(m.cwd)}",
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(timeAgo(m.updated), style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
                Spacer(Modifier.height(4.dp))
                when {
                    working -> PulseDot(Palette.Accent)
                    connected || m.alive -> StatusDot(Palette.Blue, 7.dp)
                    else -> Spacer(Modifier.size(7.dp))
                }
            }
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, null, tint = Palette.TextDim) }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.agent_rename)) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                onClick = { menu = false; onRename() })
            DropdownMenuItem(text = { Text(stringResource(R.string.agent_delete), color = Palette.Danger) },
                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Palette.Danger) }, onClick = { menu = false; onDelete() })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RunningRow(
    r: RunningAgent,
    agent: String,
    busy: Boolean = false,
    onChat: () -> Unit,
    onTerminal: (takeover: Boolean) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val canTerminal = r.where == "tmux" || (r.where == "herdr" && r.herdrPane.isNotBlank())
    val place = when (r.where) {
        "tmux" -> "tmux"
        "herdr" -> "herdr"
        "termx" -> "TermX"
        "wincon" -> "Windows"
        else -> null   // terminal lain (pts/…): tidak ditampilkan
    }
    val placeIcon = when (r.where) {
        "tmux" -> Icons.Rounded.Terminal
        "herdr" -> Icons.Rounded.Dashboard
        "termx" -> Icons.Rounded.PhoneAndroid
        "wincon" -> Icons.Rounded.Terminal
        else -> Icons.Rounded.Folder
    }
    val status = r.herdrStatus.ifBlank { r.status }
    val (statusText, statusColor) = when (status) {
        "working" -> stringResource(R.string.agent_working_short) to Palette.Accent
        "blocked", "waiting" -> stringResource(R.string.agent_waiting_short) to Palette.Warn
        "idle", "done" -> stringResource(R.string.agent_idle_short) to Palette.TextDim
        else -> null to Palette.TextDim
    }
    Box(Modifier.padding(bottom = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Surface)
                .combinedClickable(onClick = onChat, onLongClick = { menu = true })
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(agentColor(r.agent).copy(alpha = if (status == "working") 1f else 0.45f)))
            Spacer(Modifier.width(11.dp))
            Row(Modifier.weight(1f).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            AgentGlyph(r.agent, 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    r.title.ifBlank { agent }, style = MaterialTheme.typography.bodyLarge, color = Palette.Text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (place != null) {
                        Icon(placeIcon, null, tint = Palette.TextDim, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(place, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, maxLines = 1)
                        Spacer(Modifier.width(10.dp))
                    }
                    Icon(Icons.Rounded.Folder, null, tint = Palette.TextDim, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        shortPath(r.cwd), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (statusText != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (status == "working") PulseDot(statusColor) else StatusDot(statusColor, 6.dp)
                        Spacer(Modifier.width(5.dp))
                        Text(statusText, style = MaterialTheme.typography.labelSmall, color = statusColor)
                    }
                    Spacer(Modifier.height(3.dp))
                }
                if (r.startedSec > 0) Text(timeAgo(r.startedSec * 1000), style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
            }
            Box(Modifier.padding(start = 12.dp, end = 12.dp).size(20.dp), contentAlignment = Alignment.Center) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Palette.Accent)
                else Icon(Icons.AutoMirrored.Rounded.Chat, null, tint = if (r.chat.isNotBlank()) Palette.Accent else Palette.TextDim, modifier = Modifier.size(20.dp))
            }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.agent_running_chat)) }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Chat, null) },
                onClick = { menu = false; onChat() })
            if (canTerminal) {
                DropdownMenuItem(text = { Text(stringResource(R.string.agent_running_open)) }, leadingIcon = { Icon(Icons.Rounded.Terminal, null) },
                    onClick = { menu = false; onTerminal(false) })
            }
            if (r.where == "herdr" && r.herdrPane.isNotBlank()) {
                DropdownMenuItem(text = { Text(stringResource(R.string.agent_running_takeover)) }, leadingIcon = { Icon(Icons.Rounded.Key, null) },
                    onClick = { menu = false; onTerminal(true) })
            }
        }
    }
}

@Composable
private fun PulseDot(color: Color) {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
    StatusDot(color.copy(alpha = a), 7.dp)
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.agent_rename)) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.agent_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.nav_cancel)) } },
    )
}

// ======================================================================== chat baru

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NewChatSheet(hostId: String, agents: List<AgentInfo>, preselect: String? = null, onDismiss: () -> Unit, onCreated: (String) -> Unit) {
    val mgr = TermXApp.container.agents
    val recent = remember(hostId) { mgr.recentFolders(hostId) }
    var agentId by remember {
        mutableStateOf(preselect?.takeIf { id -> agents.any { it.id == id } }
            ?: mgr.lastAgent(hostId)?.takeIf { id -> agents.any { it.id == id } } ?: agents.firstOrNull()?.id ?: "")
    }
    var inTerminal by remember(agentId) { mutableStateOf(mgr.lastInTerminal(agentId)) }
    val agent = agents.firstOrNull { it.id == agentId }
    var folder by remember { mutableStateOf(recent.firstOrNull() ?: "~") }
    var model by remember(agentId) { mutableStateOf(mgr.lastModel(agentId).takeIf { m -> agent?.models?.contains(m) == true } ?: "") }
    var mode by remember(agentId) { mutableStateOf(agent?.modes?.firstOrNull() ?: "") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Palette.Surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding(),
        ) {
            Text(stringResource(R.string.agent_new_chat), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(18.dp))

            FieldLabel(stringResource(R.string.agent_field_agent))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                agents.forEach { a ->
                    val sel = a.id == agentId
                    Surface(
                        onClick = { agentId = a.id },
                        shape = RoundedCornerShape(14.dp),
                        color = if (sel) Palette.Accent.copy(alpha = 0.16f) else Palette.SurfaceHigh,
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            AgentGlyph(a.id, 24.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(a.name, color = if (sel) Palette.Accent else Palette.Text, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            FieldLabel(stringResource(R.string.agent_field_folder))
            OutlinedTextField(
                folder, { folder = it; error = null },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Rounded.Folder, null, tint = Palette.TextDim) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                isError = error != null,
                supportingText = error?.let { e -> { Text(e) } },
            )
            if (recent.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    recent.forEach { r -> SmallChip(shortPath(r), r == folder) { folder = r; error = null } }
                }
            }

            if (agent != null && agent.models.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                FieldLabel(stringResource(R.string.agent_field_model))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallChip(stringResource(R.string.agent_default), model.isEmpty()) { model = "" }
                    agent.models.forEach { m -> SmallChip(m, model == m) { model = m } }
                }
            }
            if (agent != null && agent.modes.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                FieldLabel(stringResource(R.string.agent_field_mode))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    agent.modes.forEach { m -> SmallChip(modeLabel(m), mode == m) { mode = m } }
                }
            }
            Spacer(Modifier.height(18.dp))
            Surface(
                onClick = { inTerminal = !inTerminal }, shape = RoundedCornerShape(16.dp),
                color = if (inTerminal) Palette.Accent.copy(alpha = 0.10f) else Palette.SurfaceHigh, modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Terminal, null, tint = if (inTerminal) Palette.Accent else Palette.TextDim, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.agent_in_terminal), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Palette.Text)
                        Text(stringResource(R.string.agent_in_terminal_desc), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                    }
                    Switch(checked = inTerminal, onCheckedChange = { inTerminal = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Palette.Bg, checkedTrackColor = Palette.Accent))
                }
            }
            if (agent?.id == "pi" || agent?.id == "omp") {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.agent_pi_note), style = MaterialTheme.typography.bodySmall, color = Palette.Warn)
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val a = agent ?: return@Button
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val dir = folder.trim().ifEmpty { "~" }
                            if (!mgr.folderExists(hostId, dir)) {
                                error = S.get(R.string.agent_folder_missing)
                            } else {
                                mgr.rememberChoice(hostId, a.id, model)
                                onCreated(mgr.newChat(hostId, a.id, dir, model, mode, inTerminal))
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message ?: e.toString()
                        }
                        busy = false
                    }
                },
                enabled = !busy && agent != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.Bg),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Palette.Bg)
                else Text(stringResource(R.string.agent_start), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.TextDim, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun SmallChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Palette.Accent.copy(alpha = 0.16f) else Palette.SurfaceHigh,
    ) {
        Text(
            text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge, color = if (selected) Palette.Accent else Palette.Text, maxLines = 1,
        )
    }
}

// ======================================================================== layar chat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentChatScreen(nav: Nav, hostId: String, chatId: String) {
    val mgr = TermXApp.container.agents
    val chat = remember(chatId) { mgr.open(hostId, chatId) }
    val state by chat.state.collectAsState()
    var meta by remember { mutableStateOf(mgr.cachedMeta(chatId)) }
    var agents by remember { mutableStateOf(mgr.cachedAgents(hostId)) }
    val agent = agents.firstOrNull { it.id == meta?.agent }
    var input by rememberSaveable(chatId) { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var showUsage by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(chatId) {
        if (meta == null) runCatching { mgr.list(hostId) }.onSuccess { meta = mgr.cachedMeta(chatId) }
        if (agents.isEmpty()) runCatching { agents = mgr.detect(hostId) }
    }
    // Judul diisi agentd dari pesan pertama; ambil ulang setelah giliran pertama selesai.
    LaunchedEffect(state.items.count { it is TurnItem }) {
        if (meta?.title.isNullOrBlank() && state.items.any { it is TurnItem }) {
            runCatching { mgr.list(hostId) }.onSuccess { meta = mgr.cachedMeta(chatId) ?: meta }
        }
    }

    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(chatId, lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> { mgr.visibleChat = chatId; mgr.clearNotification(chatId) }
                Lifecycle.Event.ON_STOP -> if (mgr.visibleChat == chatId) mgr.visibleChat = null
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) { mgr.visibleChat = chatId; mgr.clearNotification(chatId) }
        onDispose {
            lifecycle.removeObserver(obs)
            mgr.leave(chatId)
        }
    }

    // Buka di pesan terbaru, dan ikuti pesan baru selama pengguna sedang di bagian bawah.
    var landed by remember(chatId) { mutableStateOf(false) }
    var lastCount by remember(chatId) { mutableStateOf(0) }
    LaunchedEffect(state.items.size, state.items.lastOrNull()?.key, state.working) {
        if (state.items.isEmpty()) { lastCount = 0; return@LaunchedEffect }
        val jump = state.items.size - lastCount > 3   // riwayat yang baru dimuat: langsung, tanpa animasi
        lastCount = state.items.size
        if (!landed) {
            listState.scrollToItem(0)
            landed = true
        } else if (listState.firstVisibleItemIndex <= 2) {
            if (jump) listState.scrollToItem(0) else listState.animateScrollToItem(0)
        }
    }
    val loadingOlder by chat.loadingOlder.collectAsState()
    // Animasi muat tampil sampai riwayat awal diterima; batas waktu agar tidak pernah tertahan.
    var loadTimedOut by remember(chatId) { mutableStateOf(false) }
    LaunchedEffect(chatId, state.synced) {
        loadTimedOut = false
        if (!state.synced) { delay(10_000); loadTimedOut = true }
    }

    val modelChoices = state.models.ifEmpty { agent?.models?.map { Choice(it, it) } ?: emptyList() }
    val modeChoices = state.modes.ifEmpty { agent?.modes?.map { Choice(it, modeLabel(it)) } ?: emptyList() }
    val currentModel = state.model.ifBlank { meta?.model ?: "" }
    val currentMode = state.mode.ifBlank { meta?.mode ?: "" }
    val cwd = meta?.cwd ?: "~"

    val mirror = meta?.mirror?.takeIf { !state.mirrorEnded }

    fun openTerminal(login: Boolean) {
        if (!login && mirror != null) {
            when (mirror.where) {
                "tmux" -> { nav.attachTmux(hostId, mirror.tmuxTarget.removePrefix("=").substringBefore(':')); return }
                "herdr" -> { nav.attachHerdr(hostId, mirror.herdrBin, mirror.herdrPane, false); return }
            }
        }
        val a = agent ?: return
        val cmd = when {
            login -> a.loginCmd
            state.session.isNotBlank() -> a.resumeCmd.replace("{session}", state.session)
            else -> a.bin
        }
        nav.agentTerminal(hostId, a.id + if (login) "-login" else "", cmd, cwd)
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        chat.send(t)
        input = ""
        scope.launch { listState.animateScrollToItem(0) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                    meta?.agent?.let { AgentGlyph(it, 32.dp); Spacer(Modifier.width(12.dp)) }
                    Column {
                        Text(
                            meta?.title?.ifBlank { null } ?: agent?.name ?: stringResource(R.string.agent_title),
                            style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val (dot, label) = when (state.link) {
                                Link.LIVE -> (if (state.working) Palette.Accent else Palette.Blue) to null
                                Link.CONNECTING -> Palette.Warn to stringResource(R.string.agent_link_connecting)
                                Link.RECONNECTING -> Palette.Warn to stringResource(R.string.agent_link_reconnecting)
                                Link.FAILED -> Palette.Danger to stringResource(R.string.agent_link_failed)
                            }
                            StatusDot(dot, 6.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                label ?: listOfNotNull(agent?.name ?: meta?.agent, shortPath(cwd)).joinToString("  ·  "),
                                style = MaterialTheme.typography.labelMedium, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    }
                },
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.mon_back)) } },
                actions = {
                    if (mirror != null && (mirror.where == "tmux" || mirror.where == "herdr")) {
                        IconButton(onClick = { openTerminal(false) }) { Icon(Icons.Rounded.Terminal, stringResource(R.string.agent_open_terminal), tint = Palette.TextDim) }
                    }
                    val five = state.limits.firstOrNull { it.key == "five_hour" }?.let(::limitPercent)
                    if (five != null || state.cost > 0 || state.tokensOut > 0) {
                        Surface(onClick = { showUsage = true }, shape = RoundedCornerShape(10.dp), color = Palette.SurfaceHigh) {
                            Text(
                                if (five != null) "${five.toInt()}%" else formatCost(state.cost),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                                color = if (five != null) usageColor(five) else Palette.Text,
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, null) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.agent_usage_title)) }, leadingIcon = { Icon(Icons.Rounded.Timelapse, null) },
                                onClick = { menu = false; showUsage = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.agent_rename)) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                onClick = { menu = false; renaming = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.agent_open_terminal)) }, leadingIcon = { Icon(Icons.Rounded.Terminal, null) },
                                enabled = agent != null, onClick = { menu = false; openTerminal(false) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.agent_login_terminal)) }, leadingIcon = { Icon(Icons.Rounded.Key, null) },
                                enabled = agent != null, onClick = { menu = false; openTerminal(true) })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text(stringResource(R.string.agent_delete), color = Palette.Danger) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Palette.Danger) }, onClick = { menu = false; deleting = true })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Composer(
                text = input,
                onText = { input = it },
                state = state,
                agentName = agent?.name ?: meta?.agent ?: "",
                // Sesi Claude yang diikuti: ganti model diketik sebagai /model di sesinya sendiri.
                models = if (mirror == null) modelChoices
                else if (meta?.agent == "claude") (agent?.models?.map { Choice(it, it) } ?: emptyList()) else emptyList(),
                model = if (mirror != null && meta?.agent != "claude") "" else currentModel,
                modes = if (mirror != null) emptyList() else modeChoices, mode = currentMode,
                onModel = { chat.setModel(it); mgr.rememberChoice(hostId, meta?.agent ?: "", it) },
                onMode = { chat.setMode(it) },
                onSend = { send(input) },
                onStop = { chat.interrupt() },
                canCompact = mirror != null || state.commands.any { it.name == "compact" },
                onCompact = { send("/compact") },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (state.link == Link.FAILED) {
                LinkBanner(state.linkError ?: stringResource(R.string.agent_link_failed)) { chat.retry() }
            }
            if (mirror != null) MirrorBanner(mirror)
            if (!state.synced && !loadTimedOut && state.link != Link.FAILED) {
                ChatLoading(meta?.agent, agent?.name ?: meta?.agent ?: "", state.items.count { it is UserItem }, Modifier.weight(1f))
            } else if (state.items.isEmpty() && !state.working) {
                ChatEmpty(agent?.name ?: meta?.agent ?: "", cwd, meta?.agent, Modifier.weight(1f)) { send(it) }
            } else {
                val reversed = remember(state.items) { state.items.asReversed() }
                val showJump by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
                ) {
                    if (state.working || state.retry != null) item("~working") { WorkingRow(state, meta?.agent) }
                    items(reversed.size, key = { reversed[it].key }, contentType = { reversed[it]::class }) { i ->
                        val item = reversed[i]
                        // Nama agent di awal tiap jawaban (setelah prompt pengguna).
                        val head = item !is UserItem && item !is TurnItem && (i + 1 >= reversed.size || reversed[i + 1] is UserItem)
                        Column {
                            if (head) AssistantHeader(meta?.agent, agent?.name ?: meta?.agent ?: "")
                            ChatItemView(
                                item,
                                onAnswer = { p, allow, always -> chat.answer(p.key.removePrefix("p"), allow, always) },
                                onLogin = if (agent != null) ({ openTerminal(true) }) else null,
                            )
                        }
                    }
                    if (state.olderTurns > 0) item("~older") { OlderButton(state.olderTurns, loadingOlder) { chat.loadOlder() } }
                }
                // Langsung ke pesan terbaru tanpa menggulir manual.
                androidx.compose.animation.AnimatedVisibility(
                    visible = showJump,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 14.dp),
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f),
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            scope.launch {
                                if (listState.firstVisibleItemIndex > 40) listState.scrollToItem(6)
                                listState.animateScrollToItem(0)
                            }
                        },
                        containerColor = Palette.SurfaceHighest,
                        contentColor = Palette.Text,
                    ) { Icon(Icons.Rounded.KeyboardDoubleArrowDown, stringResource(R.string.agent_jump_latest)) }
                }
                }
            }
        }
    }

    if (showUsage) UsageSheet(state, agent?.name ?: "", currentModel) { showUsage = false }
    if (renaming) RenameDialog(meta?.title ?: "", onDismiss = { renaming = false }) { title ->
        renaming = false
        scope.launch { runCatching { mgr.rename(hostId, chatId, title) }; meta = meta?.copy(title = title) }
    }
    if (deleting) ConfirmDialog(
        title = stringResource(R.string.agent_delete_title),
        message = stringResource(R.string.agent_delete_message),
        confirmText = stringResource(R.string.agent_delete),
        danger = true,
        onConfirm = {
            deleting = false
            scope.launch { runCatching { mgr.delete(hostId, chatId) }; nav.back() }
        },
        onDismiss = { deleting = false },
    )
}

@Composable
private fun MirrorBanner(m: com.kroombox.termx.agent.MirrorInfo) {
    if (m.where != "tmux" && m.where != "herdr") return   // terminal lain: tidak perlu penanda
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Palette.Accent.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Sensors, null, tint = Palette.Accent, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.agent_mirror_live_short), color = Palette.Accent, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/**
 * Animasi muat chat: logo agent di tengah cincin berputar berwarna merek agent, lalu kerangka percakapan
 * yang berkilau seperti pesan yang sedang tersusun.
 */
@Composable
internal fun ChatLoading(agentId: String?, name: String, prompts: Int, modifier: Modifier) {
    val tint = agentId?.let { agentColor(it) }?.takeIf { it != Color(0xFFE6EDF3) && it != Palette.TextDim } ?: Palette.Accent
    val t = rememberInfiniteTransition(label = "load")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = androidx.compose.animation.core.LinearEasing)), label = "spin")
    val breathe by t.animateFloat(0.92f, 1.04f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "breathe")
    val shimmer by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1300, easing = androidx.compose.animation.core.LinearEasing)), label = "shimmer")
    Column(
        modifier.fillMaxWidth().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val stroke = 3.dp.toPx()
                val inset = stroke / 2 + 2.dp.toPx()
                val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
                val tl = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(tint.copy(alpha = 0.12f), 0f, 360f, false, tl, arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                drawArc(
                    androidx.compose.ui.graphics.Brush.sweepGradient(listOf(Color.Transparent, tint.copy(alpha = 0.35f), tint), center),
                    spin, 270f, false, tl, arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
            Box(Modifier.size(60.dp).graphicsLayer { scaleX = breathe; scaleY = breathe }, contentAlignment = Alignment.Center) {
                if (agentId != null) AgentGlyph(agentId, 44.dp)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.agent_loading_title), style = MaterialTheme.typography.titleMedium, color = Palette.Text, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            if (prompts > 0) androidx.compose.ui.res.pluralStringResource(R.plurals.agent_loading_count, prompts, prompts)
            else stringResource(R.string.agent_loading_sub, name),
            style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
        )
        Spacer(Modifier.height(28.dp))
        // Kerangka percakapan dengan kilau berjalan.
        val brush = androidx.compose.ui.graphics.Brush.linearGradient(
            listOf(Palette.Surface, Palette.SurfaceHighest, Palette.Surface),
            start = androidx.compose.ui.geometry.Offset(shimmer * 600f, 0f),
            end = androidx.compose.ui.geometry.Offset(shimmer * 600f + 400f, 120f),
        )
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.align(Alignment.End).fillMaxWidth(0.55f).height(34.dp).clip(RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp)).background(brush))
            Box(Modifier.fillMaxWidth(0.85f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(brush))
            Box(Modifier.fillMaxWidth(0.7f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(brush))
            Box(Modifier.fillMaxWidth(0.9f).height(36.dp).clip(RoundedCornerShape(12.dp)).background(brush))
            Box(Modifier.fillMaxWidth(0.45f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(brush))
        }
    }
}

@Composable
internal fun AssistantHeader(agentId: String?, name: String) {
    Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (agentId != null) AgentGlyph(agentId, 22.dp)
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Palette.TextDim, maxLines = 1)
    }
}

@Composable
internal fun OlderButton(turns: Int, loading: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Surface(onClick = onClick, enabled = !loading, shape = RoundedCornerShape(50), color = Palette.SurfaceHigh) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (loading) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = Palette.Accent)
                else Icon(Icons.Rounded.History, null, tint = Palette.Accent, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    androidx.compose.ui.res.pluralStringResource(R.plurals.agent_load_older, turns, turns),
                    style = MaterialTheme.typography.labelLarge, color = Palette.Text,
                )
            }
        }
    }
}

@Composable
private fun LinkBanner(msg: String, onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Palette.Danger.copy(alpha = 0.12f)).padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(msg, color = Palette.Danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.agent_retry)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChatEmpty(name: String, cwd: String, agentId: String?, modifier: Modifier, onPick: (String) -> Unit) {
    val ideas = listOf(
        stringResource(R.string.agent_idea_explain),
        stringResource(R.string.agent_idea_tests),
        stringResource(R.string.agent_idea_git),
    )
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(24.dp))
        AgentGlyph(agentId ?: "", 52.dp)
        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.agent_empty_title, name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = Palette.Text)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Folder, null, tint = Palette.TextDim, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(shortPath(cwd), fontFamily = Mono, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
        }
        if (agentId == "pi" || agentId == "omp") {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.agent_pi_note), style = MaterialTheme.typography.bodySmall, color = Palette.Warn)
        }
        Spacer(Modifier.height(24.dp))
        ideas.forEach { idea ->
            Surface(
                onClick = { onPick(idea) },
                shape = RoundedCornerShape(14.dp),
                color = Palette.Surface,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                Text(idea, modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun WorkingRow(state: ChatState, agentId: String? = null) {
    val tint = agentId?.let { agentColor(it) }?.takeIf { it != Color(0xFFE6EDF3) && it != Palette.TextDim } ?: Palette.Accent
    val t = rememberInfiniteTransition(label = "dots")
    val phase by t.animateFloat(0f, 3f, infiniteRepeatable(tween(1200)), label = "p")
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val on = phase.toInt() == i
            Box(
                Modifier.padding(end = 4.dp).size(7.dp).clip(CircleShape)
                    .background(tint.copy(alpha = if (on) 1f else 0.3f)),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            state.retry ?: stringResource(if (state.pendingPermissions > 0) R.string.agent_waiting_you else if (state.thinking) R.string.agent_thinking else R.string.agent_working),
            style = MaterialTheme.typography.bodySmall, color = if (state.retry != null) Palette.Warn else Palette.TextDim,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

// ------------------------------------------------------------------------ item chat

@Composable
internal fun ChatItemView(item: ChatItem, onAnswer: (PermItem, Boolean, Boolean) -> Unit, onLogin: (() -> Unit)?) {
    when (item) {
        is UserItem -> UserBubble(item)
        is TextItem -> {
            val blocks = remember(item.text) { MarkdownParser.parse(item.text) }
            SelectionContainer { MarkdownText(blocks, Modifier.fillMaxWidth()) }
        }
        is ToolItem -> ToolRow(item)
        is PermItem -> PermCard(item, onAnswer)
        is NoteItem -> NoteRow(item, onLogin)
        is TurnItem -> TurnFooter(item, onLogin)
        is ScreenItem -> ScreenCard(item)
    }
}

@Composable
private fun ScreenCard(item: ScreenItem) {
    Surface(shape = RoundedCornerShape(14.dp), color = Palette.Surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.agent_screen_title), style = MaterialTheme.typography.labelMedium, color = Palette.TextDim)
            Spacer(Modifier.height(6.dp))
            SelectionContainer {
                Text(
                    item.text.lines().takeLast(30).joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    color = Palette.Text,
                )
            }
        }
    }
}

@Composable
private fun UserBubble(item: UserItem) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp),
            color = Palette.SurfaceHighest,
            modifier = Modifier.widthIn(max = 320.dp).alpha(if (item.key.startsWith("local")) 0.7f else 1f),
        ) {
            SelectionContainer {
                Text(item.text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private fun toolIcon(name: String): ImageVector {
    val n = name.lowercase()
    return when {
        n in setOf("bash", "shell", "exec", "command", "command_execution", "terminal") || n.startsWith("bash") || n.contains("run") -> Icons.Rounded.Terminal
        n.contains("todo") || n.contains("plan") -> Icons.Rounded.Checklist
        n.contains("edit") || n.contains("write") || n.contains("patch") || n.contains("file_change") -> Icons.Rounded.EditNote
        n.contains("read") || n.contains("view") || n.contains("cat") -> Icons.Rounded.Description
        n.contains("grep") || n.contains("glob") || n.contains("search") || n.contains("find") || n == "ls" || n.contains("list") -> Icons.Rounded.Search
        n.contains("web") || n.contains("fetch") || n.contains("http") -> Icons.Rounded.Public
        n.contains("task") || n.contains("agent") -> Icons.Rounded.AccountTree
        else -> Icons.Rounded.Build
    }
}

private fun toolSubject(input: Map<String, String>): String =
    listOf("command", "file_path", "path", "notebook_path", "pattern", "url", "query", "description", "summary", "prompt")
        .firstNotNullOfOrNull { k -> input[k]?.takeIf { it.isNotBlank() } }
        ?.lineSequence()?.firstOrNull()?.let { if (it.startsWith("/")) shortPath(it) else it } ?: ""

@Composable
private fun ToolRow(item: ToolItem) {
    var open by rememberSaveable(item.key) { mutableStateOf(false) }
    val subject = toolSubject(item.input)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Surface)) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(toolIcon(item.name), null, tint = Palette.TextDim, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(10.dp))
            Text(item.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, color = Palette.Text)
            if (subject.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(subject, fontFamily = Mono, fontSize = 12.sp, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            } else Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            when (item.ok) {
                null -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Palette.Accent)
                true -> Icon(Icons.Rounded.Check, null, tint = Palette.Accent, modifier = Modifier.size(16.dp))
                false -> Icon(Icons.Rounded.Close, null, tint = Palette.Danger, modifier = Modifier.size(16.dp))
            }
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Palette.TextDim, modifier = Modifier.padding(start = 4.dp).size(18.dp))
        }
        if (item.todos.isNotEmpty()) TodoList(item.todos)
        AnimatedVisibility(open, enter = expandVertically(), exit = shrinkVertically()) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item.input["command"]?.let { MonoBox(it) }
                item.input["plan"]?.let { p -> MarkdownText(remember(p) { MarkdownParser.parse(p) }) }
                val old = item.input["old"]
                val new = item.input["new"]
                if (old != null || new != null) DiffBox(old.orEmpty(), new.orEmpty())
                val rest = item.input.filterKeys { it !in setOf("command", "plan", "old", "new") }
                if (rest.isNotEmpty() && item.input["command"] == null) {
                    rest.forEach { (k, v) -> Text("$k: $v", fontFamily = Mono, fontSize = 12.sp, color = Palette.TextDim) }
                }
                item.output?.takeIf { it.isNotBlank() }?.let { out ->
                    Text(stringResource(R.string.agent_output), style = MaterialTheme.typography.labelMedium, color = Palette.TextDim)
                    MonoBox(out.take(6000), if (item.ok == false) Palette.Danger else Palette.Text)
                }
            }
        }
    }
}

@Composable
private fun TodoList(todos: List<Pair<String, String>>) {
    Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        todos.forEach { (t, s) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, tint) = when (s) {
                    "completed" -> Icons.Rounded.CheckCircle to Palette.Accent
                    "in_progress" -> Icons.Rounded.Timelapse to Palette.Blue
                    else -> Icons.Rounded.RadioButtonUnchecked to Palette.TextDim
                }
                Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(t, style = MaterialTheme.typography.bodySmall, color = if (s == "completed") Palette.TextDim else Palette.Text)
            }
        }
    }
}

@Composable
private fun MonoBox(text: String, color: Color = Palette.Text) {
    Box(
        Modifier.fillMaxWidth().heightIn(max = 280.dp).clip(RoundedCornerShape(8.dp)).background(Palette.Bg)
            .verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(10.dp),
    ) {
        SelectionContainer { Text(text, fontFamily = Mono, fontSize = 12.sp, color = color, lineHeight = 17.sp) }
    }
}

@Composable
private fun DiffBox(old: String, new: String) {
    Column(
        Modifier.fillMaxWidth().heightIn(max = 320.dp).clip(RoundedCornerShape(8.dp)).background(Palette.Bg)
            .verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
    ) {
        if (old.isNotEmpty()) old.lines().forEach { DiffLine("−", it, Palette.Danger) }
        if (new.isNotEmpty()) new.lines().forEach { DiffLine("+", it, Palette.Accent) }
    }
}

@Composable
private fun DiffLine(sign: String, text: String, color: Color) {
    Row(Modifier.background(color.copy(alpha = 0.10f)).padding(horizontal = 10.dp)) {
        Text(sign, fontFamily = Mono, fontSize = 12.sp, color = color, modifier = Modifier.width(14.dp))
        Text(text, fontFamily = Mono, fontSize = 12.sp, color = Palette.Text, softWrap = false, lineHeight = 17.sp)
    }
}

@Composable
private fun PermCard(item: PermItem, onAnswer: (PermItem, Boolean, Boolean) -> Unit) {
    if (item.allowed != null) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Shield, null, tint = if (item.allowed) Palette.Accent else Palette.TextDim, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(if (item.allowed) R.string.agent_perm_allowed else R.string.agent_perm_denied, item.tool),
                style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
            )
        }
        return
    }
    val isPlan = item.input["plan"] != null
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Warn.copy(alpha = 0.10f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Shield, null, tint = Palette.Warn, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(if (isPlan) R.string.agent_perm_plan else R.string.agent_perm_title, item.tool),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Palette.Text,
            )
        }
        if (item.desc.isNotBlank()) Text(item.desc, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
        item.input["plan"]?.let { p ->
            Box(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) { MarkdownText(remember(p) { MarkdownParser.parse(p) }) }
        }
        item.input["command"]?.let { MonoBox(it) }
        (item.input["file_path"] ?: item.input["path"])?.let { Text(shortPath(it), fontFamily = Mono, fontSize = 12.sp, color = Palette.Text) }
        if (item.input["old"] != null || item.input["new"] != null) DiffBox(item.input["old"].orEmpty(), item.input["new"].orEmpty())
        if (item.input.keys.none { it in setOf("plan", "command", "file_path", "path", "old", "new") }) {
            toolSubject(item.input).takeIf { it.isNotEmpty() }?.let { Text(it, fontFamily = Mono, fontSize = 12.sp, color = Palette.TextDim) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onAnswer(item, false, false) }) { Text(stringResource(R.string.agent_perm_deny), color = Palette.Danger) }
            Spacer(Modifier.weight(1f))
            if (item.canAlways) {
                OutlinedButton(onClick = { onAnswer(item, true, true) }, shape = RoundedCornerShape(12.dp)) {
                    Text(stringResource(R.string.agent_perm_always))
                }
            }
            Button(
                onClick = { onAnswer(item, true, false) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.Bg),
            ) { Text(stringResource(if (isPlan) R.string.agent_perm_approve else R.string.agent_perm_allow), fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun NoteRow(item: NoteItem, onLogin: (() -> Unit)?) {
    if (item.error && looksLikeLogin(item.text)) {
        LoginHint(item.text, onLogin)
        return
    }
    if (item.error) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Danger.copy(alpha = 0.10f)).padding(10.dp),
        ) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = Palette.Danger, modifier = Modifier.padding(end = 8.dp).size(17.dp))
            Text(item.text, style = MaterialTheme.typography.bodySmall, color = Palette.Danger)
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Info, null, tint = Palette.TextDim, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(item.text, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun LoginHint(detail: String, onLogin: (() -> Unit)?) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Blue.copy(alpha = 0.10f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Key, null, tint = Palette.Blue, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.agent_login_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Palette.Text)
        }
        Text(stringResource(R.string.agent_login_message), style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
        Text(detail.take(300), fontFamily = Mono, fontSize = 11.sp, color = Palette.TextDim, maxLines = 4, overflow = TextOverflow.Ellipsis)
        if (onLogin != null) {
            Button(
                onClick = onLogin, shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Blue, contentColor = Palette.Bg),
            ) {
                Icon(Icons.Rounded.Terminal, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.agent_login_terminal))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TurnFooter(item: TurnItem, onLogin: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth()) {
        if (!item.ok && item.error != null) {
            if (looksLikeLogin(item.error)) LoginHint(item.error, onLogin)
            else NoteRow(NoteItem(item.key + "e", item.error, true), onLogin)
            Spacer(Modifier.height(6.dp))
        }
        val parts = buildList {
            item.model?.let { add(Icons.Rounded.Memory to it) }
            if (item.tokensIn > 0 || item.tokensOut > 0) add(Icons.Rounded.SwapVert to "${formatTokens(item.tokensIn)} / ${formatTokens(item.tokensOut)}")
            if (item.cost > 0) add(Icons.Rounded.Payments to formatCost(item.cost))
            if (item.ms > 0) add(Icons.Rounded.Schedule to String.format(Locale.US, "%.1fs", item.ms / 1000.0))
        }
        if (parts.isNotEmpty()) {
            FlowRow(Modifier.padding(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for ((icon, label) in parts) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = Palette.TextDim.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(label, fontFamily = Mono, fontSize = 11.sp, color = Palette.TextDim.copy(alpha = 0.85f), maxLines = 1)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------ composer

@Composable
internal fun Composer(
    text: String,
    onText: (String) -> Unit,
    state: ChatState,
    agentName: String,
    models: List<Choice>, model: String,
    modes: List<Choice>, mode: String,
    onModel: (String) -> Unit,
    onMode: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    canCompact: Boolean = false,
    onCompact: () -> Unit = {},
) {
    val slash = remember(text, state.commands) {
        if (text.startsWith("/") && !text.contains(' ') && !text.contains('\n')) {
            val q = text.removePrefix("/").lowercase()
            state.commands.filter { it.name.lowercase().startsWith(q) || it.name.lowercase().contains(q) }.take(8)
        } else emptyList()
    }
    Surface(color = Palette.Surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)) {
            if (slash.isNotEmpty()) SlashList(slash) { onText("/" + it.name + " ") }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (models.isNotEmpty() || model.isNotBlank()) {
                    PickerChip(Icons.Rounded.AutoAwesome, modelLabel(model, models), models, model, onModel)
                }
                if (modes.isNotEmpty()) PickerChip(Icons.Rounded.Tune, modeLabel(mode).let { l -> modes.firstOrNull { it.id == mode }?.name ?: l }, modes, mode, onMode)
                if (state.contextUsed > 0) ContextChip(state.contextUsed, state.contextSize, canCompact && !state.working, onCompact)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                TextField(
                    value = text,
                    onValueChange = onText,
                    placeholder = { Text(stringResource(R.string.agent_message_hint, agentName), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    maxLines = 6,
                    shape = RoundedCornerShape(22.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Palette.SurfaceHigh, unfocusedContainerColor = Palette.SurfaceHigh,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                val canSend = text.isNotBlank()
                when {
                    state.working && !canSend -> RoundAction(Icons.Rounded.Stop, stringResource(R.string.agent_stop), Palette.Danger, onStop)
                    else -> RoundAction(
                        Icons.AutoMirrored.Rounded.Send, stringResource(R.string.agent_send),
                        if (canSend) Palette.Accent else Palette.SurfaceHighest, onSend, enabled = canSend,
                    )
                }
            }
        }
    }
}

/** Pemakaian konteks sebagai cincin kecil; ketuk untuk rincian dan ringkas (/compact). */
@Composable
private fun ContextChip(used: Long, size: Long, canCompact: Boolean, onCompact: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val p = if (size > 0) (used * 100.0 / size).coerceIn(0.0, 100.0) else null
    val color = if (p != null) usageColor(p) else Palette.TextDim
    val sweep by animateFloatAsState(((p ?: 0.0) / 100).toFloat(), tween(600), label = "ctx")
    Box {
        Surface(
            onClick = { open = true }, shape = RoundedCornerShape(10.dp), color = Palette.SurfaceHigh,
            modifier = Modifier.semantics { contentDescription = "context" },
        ) {
            Row(Modifier.padding(start = 6.dp, end = 9.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        val st = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        drawArc(color.copy(alpha = 0.22f), 0f, 360f, false, style = st)
                        drawArc(color, -90f, 360f * sweep, false, style = st)
                    }
                    Icon(Icons.Rounded.Memory, null, tint = color, modifier = Modifier.size(11.dp))
                }
                Spacer(Modifier.width(5.dp))
                Text(if (p != null) "${p.toInt()}%" else formatTokens(used), style = MaterialTheme.typography.labelMedium, color = color)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(min = 200.dp)) {
                Text(stringResource(R.string.agent_ctx_title), style = MaterialTheme.typography.titleSmall, color = Palette.Text)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (size > 0) stringResource(R.string.agent_ctx_detail, formatTokens(used), formatTokens(size), p!!.toInt())
                    else stringResource(R.string.agent_ctx_used, formatTokens(used)),
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
                )
                if (p != null) {
                    Spacer(Modifier.height(8.dp))
                    UsageBar((p / 100).toFloat(), color)
                }
            }
            if (canCompact) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Palette.SurfaceHighest)
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(stringResource(R.string.agent_ctx_compact))
                            Text(stringResource(R.string.agent_ctx_compact_sub), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                        }
                    },
                    leadingIcon = { Icon(Icons.Rounded.Compress, null, tint = Palette.Accent) },
                    onClick = { open = false; onCompact() },
                )
            }
        }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, desc: String, color: Color, onClick: () -> Unit, enabled: Boolean = true) {
    Surface(onClick = onClick, enabled = enabled, shape = CircleShape, color = color, modifier = Modifier.size(48.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, desc, tint = if (enabled) Palette.Bg else Palette.TextDim, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun PickerChip(icon: ImageVector, label: String, choices: List<Choice>, selected: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(onClick = { if (choices.isNotEmpty()) open = true }, shape = RoundedCornerShape(10.dp), color = Palette.SurfaceHigh) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Palette.TextDim, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
                if (choices.isNotEmpty()) Icon(Icons.Rounded.ExpandMore, null, tint = Palette.TextDim, modifier = Modifier.size(14.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 420.dp)) {
            choices.forEach { c ->
                DropdownMenuItem(
                    text = { Text(c.name.ifBlank { c.id }, fontWeight = if (c.id == selected) FontWeight.SemiBold else FontWeight.Normal) },
                    trailingIcon = { if (c.id == selected) Icon(Icons.Rounded.Check, null, tint = Palette.Accent) },
                    onClick = { open = false; if (c.id != selected) onPick(c.id) },
                )
            }
        }
    }
}

@Composable
private fun SlashList(list: List<SlashCommand>, onPick: (SlashCommand) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp)).background(Palette.SurfaceHigh)
            .heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
    ) {
        list.forEach { c ->
            Row(Modifier.fillMaxWidth().clickable { onPick(c) }.padding(horizontal = 14.dp, vertical = 9.dp)) {
                Text("/" + c.name, fontFamily = Mono, fontSize = 13.sp, color = Palette.Accent)
                if (c.hint.isNotBlank()) Text(" " + c.hint, fontFamily = Mono, fontSize = 13.sp, color = Palette.TextDim, maxLines = 1)
                Spacer(Modifier.width(10.dp))
                Text(c.desc, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ------------------------------------------------------------------------ usage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsageSheet(state: ChatState, agentName: String, model: String, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.agent_usage_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(stringResource(R.string.agent_stat_cost), if (state.cost > 0) formatCost(state.cost) else "–", Modifier.weight(1f))
                Stat(stringResource(R.string.agent_stat_in), formatTokens(state.tokensIn), Modifier.weight(1f))
                Stat(stringResource(R.string.agent_stat_out), formatTokens(state.tokensOut), Modifier.weight(1f))
            }
            if (state.contextSize > 0) {
                val p = state.contextUsed * 100.0 / state.contextSize
                Column {
                    Row {
                        Text(stringResource(R.string.agent_stat_context), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("${formatTokens(state.contextUsed)} / ${formatTokens(state.contextSize)}", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                    }
                    Spacer(Modifier.height(6.dp))
                    UsageBar((p / 100).toFloat(), usageColor(p))
                }
            }
            if (state.limits.isNotEmpty()) {
                state.limits.forEach { w ->
                    val p = limitPercent(w)
                    Column {
                        Row {
                            Text(limitLabel(w.key), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(if (p == null) "–" else "${p.toInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(6.dp))
                        UsageBar(((p ?: 0.0) / 100).toFloat(), if (p == null) Palette.TextDim else usageColor(p))
                        resetIn(w.resetEpochSec).takeIf { it.isNotEmpty() }?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = Palette.TextDim, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            } else {
                Text(stringResource(R.string.agent_usage_no_limits), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
            }
            Text(
                listOf(agentName, model).filter { it.isNotBlank() }.joinToString("  ·  "),
                style = MaterialTheme.typography.labelMedium, color = Palette.TextDim,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Palette.SurfaceHigh).padding(12.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
    }
}
