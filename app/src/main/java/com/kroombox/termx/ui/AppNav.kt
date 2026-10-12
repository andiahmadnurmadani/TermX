package com.kroombox.termx.ui

import com.kroombox.termx.i18n.S
import com.kroombox.termx.R
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kroombox.termx.TermXApp
import com.kroombox.termx.ssh.PasswordAnswer
import com.kroombox.termx.ssh.Prompt
import com.kroombox.termx.ssh.Prompts
import com.kroombox.termx.ssh.SessionState
import com.kroombox.termx.ui.components.PasswordField
import com.kroombox.termx.ui.screens.EditorScreen
import com.kroombox.termx.ui.screens.ForwardsScreen
import com.kroombox.termx.ui.screens.HostEditScreen
import com.kroombox.termx.ui.screens.HostsScreen
import com.kroombox.termx.ui.screens.KeysScreen
import com.kroombox.termx.ui.screens.KnownHostsScreen
import com.kroombox.termx.ui.screens.MonitorScreen
import com.kroombox.termx.ui.screens.MoreScreen
import com.kroombox.termx.ui.screens.SessionsScreen
import com.kroombox.termx.ui.screens.SettingsScreen
import com.kroombox.termx.ui.screens.SftpScreen
import com.kroombox.termx.ui.screens.SnippetsScreen
import com.kroombox.termx.ui.screens.TerminalScreen
import com.kroombox.termx.ui.theme.Mono
import com.kroombox.termx.ui.theme.Palette

object Routes {
    const val HOME = "home"
    const val HOST_EDIT = "host_edit?id={id}"
    const val TERMINAL = "terminal?session={session}"
    const val SFTP = "sftp/{hostId}?path={path}&sid={sid}"
    const val EDITOR = "editor/{hostId}?path={path}&new={new}"
    const val MONITOR = "monitor/{hostId}"
    const val FORWARDS = "forwards?hostId={hostId}"
    const val KNOWN_HOSTS = "known_hosts"
    const val SETTINGS = "settings"

    fun hostEdit(id: String? = null) = if (id == null) "host_edit" else "host_edit?id=$id"
    fun terminal(sessionId: String) = "terminal?session=$sessionId"
    fun sftp(hostId: String, path: String? = null, sid: String? = null): String {
        val q = listOfNotNull(path?.let { "path=${android.net.Uri.encode(it)}" }, sid?.let { "sid=$it" })
        return "sftp/$hostId" + if (q.isEmpty()) "" else "?" + q.joinToString("&")
    }
    fun editor(hostId: String, path: String, isNew: Boolean = false) =
        "editor/$hostId?path=${android.net.Uri.encode(path)}&new=$isNew"
    fun monitor(hostId: String) = "monitor/$hostId"
    fun forwards(hostId: String? = null) = if (hostId == null) "forwards" else "forwards?hostId=$hostId"
}

/** Aksi navigasi yang dibagikan ke semua layar. */
class Nav(private val nav: NavHostController) {
    fun back() { if (nav.previousBackStackEntry != null) nav.popBackStack() }
    fun editHost(id: String? = null) = nav.navigate(Routes.hostEdit(id))
    fun terminal(sessionId: String) = nav.navigate(Routes.terminal(sessionId)) { launchSingleTop = true }
    /** Buka (atau lanjutkan) sesi SFTP ke host; sesi tampil di tab Sesi sampai ditutup. */
    fun sftp(hostId: String, path: String? = null, sid: String? = null) {
        val s = TermXApp.container.sessions.openSftp(hostId, path, sid)
        nav.navigate(Routes.sftp(hostId, s.path.ifEmpty { path ?: "" }.ifEmpty { null }, s.id)) { launchSingleTop = true }
    }
    fun editor(hostId: String, path: String, isNew: Boolean = false) = nav.navigate(Routes.editor(hostId, path, isNew))
    fun monitor(hostId: String) = nav.navigate(Routes.monitor(hostId))
    fun forwards(hostId: String? = null) = nav.navigate(Routes.forwards(hostId))
    fun knownHosts() = nav.navigate(Routes.KNOWN_HOSTS)
    fun settings() = nav.navigate(Routes.SETTINGS)

    /** Buka sesi terminal baru ke host lalu pindah ke layar terminal. */
    fun connect(
        hostId: String,
        tmux: String? = null,
        plain: Boolean = false,
        historyKey: String? = null,
        loadHistory: Boolean = false,
    ) {
        val s = TermXApp.container.sessions.openTerminal(hostId, tmux, plain, historyKey, loadHistory) ?: return
        terminal(s.id)
    }
}

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val nav = remember(navController) { Nav(navController) }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScaffold(nav) }
        composable(
            Routes.HOST_EDIT,
            arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { HostEditScreen(nav, it.arguments?.getString("id")) }
        composable(
            Routes.TERMINAL,
            arguments = listOf(navArgument("session") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { TerminalScreen(nav, it.arguments?.getString("session")) }
        composable(
            Routes.SFTP,
            arguments = listOf(
                navArgument("hostId") { type = NavType.StringType },
                navArgument("path") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("sid") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) {
            SftpScreen(nav, it.arguments?.getString("hostId") ?: "", it.arguments?.getString("path"), it.arguments?.getString("sid"))
        }
        composable(
            Routes.EDITOR,
            arguments = listOf(
                navArgument("hostId") { type = NavType.StringType },
                navArgument("path") { type = NavType.StringType; defaultValue = "~" },
                navArgument("new") { type = NavType.BoolType; defaultValue = false },
            ),
        ) {
            EditorScreen(
                nav, it.arguments?.getString("hostId") ?: "", it.arguments?.getString("path") ?: "~",
                it.arguments?.getBoolean("new") ?: false,
            )
        }
        composable(Routes.MONITOR, arguments = listOf(navArgument("hostId") { type = NavType.StringType })) {
            MonitorScreen(nav, it.arguments?.getString("hostId") ?: "")
        }
        composable(
            Routes.FORWARDS,
            arguments = listOf(navArgument("hostId") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { ForwardsScreen(nav, it.arguments?.getString("hostId")) }
        composable(Routes.KNOWN_HOSTS) { KnownHostsScreen(nav) }
        composable(Routes.SETTINGS) { SettingsScreen(nav) }
    }

    PromptHost()
    BatteryPrompt()
    DisconnectAllPrompt()
}

@Composable
private fun DisconnectAllPrompt() {
    val mgr = TermXApp.container.sessions
    val requested by mgr.disconnectRequested.collectAsState()
    if (!requested) return
    val sessions by mgr.sessions.collectAsState()
    AlertDialog(
        onDismissRequest = { mgr.disconnectRequested.value = false },
        title = { Text(stringResource(R.string.nav_disconnect_all_title)) },
        text = {
            Text(stringResource(R.string.nav_disconnect_all_message, sessions.size))
        },
        confirmButton = {
            TextButton(onClick = {
                mgr.disconnectRequested.value = false
                mgr.closeAll()
            }) { Text(stringResource(R.string.nav_disconnect_all), color = Palette.Danger, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = { mgr.disconnectRequested.value = false }) { Text(stringResource(R.string.nav_cancel)) } },
    )
}

/**
 * Sekali saja, saat sesi pertama dibuka: minta pengecualian optimasi baterai agar Android tidak
 * membekukan koneksi di latar belakang. Tanpa ini sesi bisa terputus walau pengguna tidak memutuskannya.
 */
@Composable
private fun BatteryPrompt() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sessions by TermXApp.container.sessions.sessions.collectAsState()
    val data by TermXApp.container.vault.data.collectAsState()
    val needed = sessions.isNotEmpty() && !data.settings.batteryPromptShown &&
        !com.kroombox.termx.service.BatteryOptimization.isIgnoring(context)
    if (!needed) return
    fun done() = TermXApp.container.vault.updateSettings { it.copy(batteryPromptShown = true) }
    AlertDialog(
        onDismissRequest = { done() },
        title = { Text(stringResource(R.string.nav_battery_title)) },
        text = {
            Text(stringResource(R.string.nav_battery_message))
        },
        confirmButton = {
            TextButton(onClick = {
                done()
                com.kroombox.termx.service.BatteryOptimization.request(context)
            }) { Text(stringResource(R.string.nav_allow), fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = { done() }) { Text(stringResource(R.string.nav_later)) } },
    )
}

private data class Tab(@StringRes val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(R.string.nav_tab_hosts, Icons.Rounded.Dns),
    Tab(R.string.nav_tab_sessions, Icons.Rounded.Terminal),
    Tab(R.string.nav_tab_snippets, Icons.Rounded.Bolt),
    Tab(R.string.nav_tab_keychain, Icons.Rounded.VpnKey),
    Tab(R.string.nav_tab_more, Icons.Rounded.MoreHoriz),
)

@Composable
private fun HomeScaffold(nav: Nav) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val sessions by TermXApp.container.sessions.sessions.collectAsState()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = Palette.Surface, tonalElevation = 0.dp) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = {
                            if (i == 1 && sessions.isNotEmpty()) {
                                BadgedBox(badge = { Badge { Text("${sessions.size}") } }) { Icon(t.icon, stringResource(t.label)) }
                            } else {
                                Icon(t.icon, stringResource(t.label))
                            }
                        },
                        // Satu baris, agar label yang lebih panjang (mis. bahasa Inggris) tidak turun ke baris
                        // kedua dan mengubah tinggi bar navigasi.
                        label = {
                            Text(
                                stringResource(t.label), maxLines = 1, softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        val mod = Modifier.padding(pad)
        when (tab) {
            0 -> HostsScreen(nav, mod)
            1 -> SessionsScreen(nav, mod)
            2 -> SnippetsScreen(mod)
            3 -> KeysScreen(mod)
            else -> MoreScreen(nav, mod)
        }
    }
}

/** Menampilkan dialog pertanyaan dari koneksi SSH (host key, password). */
@Composable
fun PromptHost() {
    val pending by Prompts.pending.collectAsState()
    val p = pending.firstOrNull() ?: return
    when (p) {
        is Prompt.HostKey -> HostKeyDialog(p)
        is Prompt.Password -> PasswordDialog(p)
    }
}

@Composable
private fun HostKeyDialog(p: Prompt.HostKey) {
    AlertDialog(
        onDismissRequest = { Prompts.answer(p, false) },
        icon = { if (p.changed) Icon(Icons.Rounded.Warning, null, tint = Palette.Danger) },
        title = { Text(stringResource(if (p.changed) R.string.nav_hostkey_changed_title else R.string.nav_hostkey_new_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (p.changed) {
                    Text(
                        stringResource(R.string.nav_hostkey_changed_message, p.hostPort),
                        color = Palette.Danger,
                    )
                } else {
                    Text(stringResource(R.string.nav_hostkey_new_message, p.hostPort))
                }
                Text(p.keyType, style = MaterialTheme.typography.labelLarge)
                Text(p.fingerprint, fontFamily = Mono, style = MaterialTheme.typography.bodySmall)
                if (p.changed && p.oldFingerprint != null) {
                    Text(stringResource(R.string.nav_hostkey_previous), style = MaterialTheme.typography.labelMedium, color = Palette.TextDim)
                    Text(p.oldFingerprint, fontFamily = Mono, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { Prompts.answer(p, true) }) {
                Text(stringResource(if (p.changed) R.string.nav_hostkey_trust_new else R.string.nav_hostkey_trust_connect), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = { Prompts.answer(p, false) }) { Text(stringResource(R.string.nav_reject)) } },
    )
}

@Composable
private fun PasswordDialog(p: Prompt.Password) {
    var pw by remember(p.id) { mutableStateOf("") }
    var save by remember(p.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { Prompts.answer(p, null) },
        title = { Text(p.title) },
        text = {
            Column {
                Text(p.message, fontFamily = Mono, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                Spacer(Modifier.height(12.dp))
                PasswordField(pw, { pw = it }, stringResource(R.string.nav_password))
                if (p.allowSave) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = save, onCheckedChange = { save = it })
                        Text(stringResource(R.string.nav_save_encrypted))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { Prompts.answer(p, PasswordAnswer(pw, save)) }, enabled = pw.isNotEmpty()) { Text(stringResource(R.string.nav_sign_in)) }
        },
        dismissButton = { TextButton(onClick = { Prompts.answer(p, null) }) { Text(stringResource(R.string.nav_cancel)) } },
    )
}

/** Warna indikator status sesi. */
fun stateColor(s: SessionState) = when (s) {
    SessionState.CONNECTED -> Palette.Accent
    SessionState.CONNECTING, SessionState.RECONNECTING -> Palette.Warn
    SessionState.FAILED -> Palette.Danger
    SessionState.ENDED, SessionState.CLOSED -> Palette.TextDim
}

fun stateLabel(s: SessionState) = when (s) {
    SessionState.CONNECTED -> S.get(R.string.nav_state_connected)
    SessionState.CONNECTING -> S.get(R.string.nav_state_connecting)
    SessionState.RECONNECTING -> S.get(R.string.nav_state_reconnecting)
    SessionState.FAILED -> S.get(R.string.nav_state_failed)
    SessionState.ENDED -> S.get(R.string.nav_state_ended)
    SessionState.CLOSED -> S.get(R.string.nav_state_closed)
}
