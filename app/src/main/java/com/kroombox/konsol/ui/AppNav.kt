package com.kroombox.konsol.ui

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
import com.kroombox.konsol.KonsolApp
import com.kroombox.konsol.ssh.PasswordAnswer
import com.kroombox.konsol.ssh.Prompt
import com.kroombox.konsol.ssh.Prompts
import com.kroombox.konsol.ssh.SessionState
import com.kroombox.konsol.ui.components.PasswordField
import com.kroombox.konsol.ui.screens.ForwardsScreen
import com.kroombox.konsol.ui.screens.HostEditScreen
import com.kroombox.konsol.ui.screens.HostsScreen
import com.kroombox.konsol.ui.screens.KeysScreen
import com.kroombox.konsol.ui.screens.KnownHostsScreen
import com.kroombox.konsol.ui.screens.MonitorScreen
import com.kroombox.konsol.ui.screens.MoreScreen
import com.kroombox.konsol.ui.screens.SessionsScreen
import com.kroombox.konsol.ui.screens.SettingsScreen
import com.kroombox.konsol.ui.screens.SftpScreen
import com.kroombox.konsol.ui.screens.SnippetsScreen
import com.kroombox.konsol.ui.screens.TerminalScreen
import com.kroombox.konsol.ui.theme.Mono
import com.kroombox.konsol.ui.theme.Palette

object Routes {
    const val HOME = "home"
    const val HOST_EDIT = "host_edit?id={id}"
    const val TERMINAL = "terminal?session={session}"
    const val SFTP = "sftp/{hostId}?path={path}"
    const val MONITOR = "monitor/{hostId}"
    const val FORWARDS = "forwards?hostId={hostId}"
    const val KNOWN_HOSTS = "known_hosts"
    const val SETTINGS = "settings"

    fun hostEdit(id: String? = null) = if (id == null) "host_edit" else "host_edit?id=$id"
    fun terminal(sessionId: String) = "terminal?session=$sessionId"
    fun sftp(hostId: String, path: String? = null) = if (path == null) "sftp/$hostId" else "sftp/$hostId?path=${android.net.Uri.encode(path)}"
    fun monitor(hostId: String) = "monitor/$hostId"
    fun forwards(hostId: String? = null) = if (hostId == null) "forwards" else "forwards?hostId=$hostId"
}

/** Aksi navigasi yang dibagikan ke semua layar. */
class Nav(private val nav: NavHostController) {
    fun back() { if (nav.previousBackStackEntry != null) nav.popBackStack() }
    fun editHost(id: String? = null) = nav.navigate(Routes.hostEdit(id))
    fun terminal(sessionId: String) = nav.navigate(Routes.terminal(sessionId)) { launchSingleTop = true }
    fun sftp(hostId: String, path: String? = null) = nav.navigate(Routes.sftp(hostId, path))
    fun monitor(hostId: String) = nav.navigate(Routes.monitor(hostId))
    fun forwards(hostId: String? = null) = nav.navigate(Routes.forwards(hostId))
    fun knownHosts() = nav.navigate(Routes.KNOWN_HOSTS)
    fun settings() = nav.navigate(Routes.SETTINGS)

    /** Buka sesi terminal baru ke host lalu pindah ke layar terminal. */
    fun connect(hostId: String, tmux: String? = null, plain: Boolean = false) {
        val s = KonsolApp.container.sessions.openTerminal(hostId, tmux, plain) ?: return
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
            ),
        ) { SftpScreen(nav, it.arguments?.getString("hostId") ?: "", it.arguments?.getString("path")) }
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
}

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Host", Icons.Rounded.Dns),
    Tab("Sesi", Icons.Rounded.Terminal),
    Tab("Snippet", Icons.Rounded.Bolt),
    Tab("Keychain", Icons.Rounded.VpnKey),
    Tab("Lainnya", Icons.Rounded.MoreHoriz),
)

@Composable
private fun HomeScaffold(nav: Nav) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val sessions by KonsolApp.container.sessions.sessions.collectAsState()
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
                                BadgedBox(badge = { Badge { Text("${sessions.size}") } }) { Icon(t.icon, t.label) }
                            } else {
                                Icon(t.icon, t.label)
                            }
                        },
                        label = { Text(t.label) },
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
        title = { Text(if (p.changed) "Host key BERUBAH!" else "Host baru") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (p.changed) {
                    Text(
                        "Kunci server ${p.hostPort} berbeda dari yang tersimpan. Ini bisa berarti server di-reinstall — " +
                            "atau ada serangan man-in-the-middle. Lanjutkan hanya bila Anda yakin.",
                        color = Palette.Danger,
                    )
                } else {
                    Text("Keaslian ${p.hostPort} belum pernah diverifikasi. Cocokkan fingerprint berikut dengan server:")
                }
                Text(p.keyType, style = MaterialTheme.typography.labelLarge)
                Text(p.fingerprint, fontFamily = Mono, style = MaterialTheme.typography.bodySmall)
                if (p.changed && p.oldFingerprint != null) {
                    Text("Sebelumnya:", style = MaterialTheme.typography.labelMedium, color = Palette.TextDim)
                    Text(p.oldFingerprint, fontFamily = Mono, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { Prompts.answer(p, true) }) {
                Text(if (p.changed) "Percayai kunci baru" else "Percayai & sambung", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = { Prompts.answer(p, false) }) { Text("Tolak") } },
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
                PasswordField(pw, { pw = it }, "Password")
                if (p.allowSave) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = save, onCheckedChange = { save = it })
                        Text("Simpan (terenkripsi di perangkat)")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { Prompts.answer(p, PasswordAnswer(pw, save)) }, enabled = pw.isNotEmpty()) { Text("Masuk") }
        },
        dismissButton = { TextButton(onClick = { Prompts.answer(p, null) }) { Text("Batal") } },
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
    SessionState.CONNECTED -> "Tersambung"
    SessionState.CONNECTING -> "Menyambung…"
    SessionState.RECONNECTING -> "Menyambung ulang…"
    SessionState.FAILED -> "Gagal"
    SessionState.ENDED -> "Selesai"
    SessionState.CLOSED -> "Ditutup"
}
