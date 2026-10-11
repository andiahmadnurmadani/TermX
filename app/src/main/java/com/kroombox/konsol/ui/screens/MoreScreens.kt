package com.kroombox.konsol.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kroombox.konsol.BuildConfig
import com.kroombox.konsol.KonsolApp
import com.kroombox.konsol.data.AppSettings
import com.kroombox.konsol.terminal.TerminalTheme
import com.kroombox.konsol.ui.Nav
import com.kroombox.konsol.ui.components.AppCard
import com.kroombox.konsol.ui.components.ConfirmDialog
import com.kroombox.konsol.ui.components.EmptyState
import com.kroombox.konsol.ui.components.LabeledSwitch
import com.kroombox.konsol.ui.components.SectionHeader
import com.kroombox.konsol.ui.components.timeAgo
import com.kroombox.konsol.ui.theme.MonoSmall
import com.kroombox.konsol.ui.theme.Palette

@Composable
fun MoreScreen(nav: Nav, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vault = KonsolApp.container.vault
    var message by remember { mutableStateOf<String?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            message = try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(vault.exportWithoutSecrets().toByteArray()) }
                "Konfigurasi diekspor (tanpa password & kunci privat)."
            } catch (e: Exception) {
                "Gagal ekspor: ${e.message}"
            }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            message = try {
                val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                val n = vault.importConfig(text)
                "$n host baru diimpor."
            } catch (e: Exception) {
                "Gagal impor: ${e.message}"
            }
        }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Lainnya", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        MenuRow(Icons.Rounded.SwapHoriz, "Port forwarding", "Lokal, remote, dan SOCKS5") { nav.forwards() }
        MenuRow(Icons.Rounded.Shield, "Known hosts", "Sidik jari server yang sudah dipercaya") { nav.knownHosts() }
        MenuRow(Icons.Rounded.Settings, "Pengaturan", "Tema terminal, huruf, keyboard, latar belakang") { nav.settings() }
        SectionHeader("Cadangan")
        MenuRow(Icons.Rounded.FileUpload, "Ekspor konfigurasi", "Host, grup, snippet, forward — tanpa rahasia") {
            exporter.launch("konsol-backup.json")
        }
        MenuRow(Icons.Rounded.FileDownload, "Impor konfigurasi", "Gabungkan dari file cadangan") {
            importer.launch(arrayOf("application/json", "text/*", "*/*"))
        }
        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Accent, modifier = Modifier.padding(8.dp))
        }
        SectionHeader("Tentang")
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, null, tint = Palette.Accent)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Konsol ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                    Text("Klien SSH, SFTP & sesi persisten. Semua data tersimpan lokal di perangkat.", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                }
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Palette.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Palette.Accent) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Palette.TextDim)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: Nav) {
    val vault = KonsolApp.container.vault
    val data by vault.data.collectAsState()
    val s = data.settings
    var extraKeys by remember { mutableStateOf(s.extraKeys) }
    var scrollback by remember { mutableStateOf(s.scrollbackLines.toString()) }
    LaunchedEffect(extraKeys) { if (extraKeys != vault.current.settings.extraKeys) vault.updateSettings { it.copy(extraKeys = extraKeys) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan") },
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionHeader("Tema terminal")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TerminalTheme.all.forEach { t -> ThemePreview(t, selected = t.id == s.themeId) { vault.updateSettings { it.copy(themeId = t.id) } } }
            }

            SectionHeader("Huruf")
            AppCard {
                Text("Ukuran: ${s.fontSizeSp.toInt()} sp", style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = s.fontSizeSp,
                    onValueChange = { v -> vault.updateSettings { it.copy(fontSizeSp = v) } },
                    valueRange = 8f..24f, steps = 15,
                )
                Text("Tips: cubit layar terminal untuk memperbesar/memperkecil.", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
            }

            SectionHeader("Keyboard")
            AppCard {
                OutlinedTextField(
                    extraKeys, { extraKeys = it }, label = { Text("Tombol tambahan") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Pisahkan dengan spasi. Khusus: ESC TAB CTRL ALT SHIFT UP DOWN LEFT RIGHT HOME END PGUP PGDN INS DEL ENTER BKSP PASTE F1–F12. Lainnya dikirim apa adanya.",
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
                )
                TextButton(onClick = { extraKeys = AppSettings.DEFAULT_EXTRA_KEYS }) { Text("Kembalikan default") }
            }

            SectionHeader("Sesi & latar belakang")
            AppCard {
                LabeledSwitch("Tmux default untuk host baru", "Sesi persisten aktif saat menambah host", s.defaultPersistent) { v ->
                    vault.updateSettings { it.copy(defaultPersistent = v) }
                }
                LabeledSwitch(
                    "Jaga koneksi saat layar mati",
                    "Layar terminal tetap menyala dan wake-lock dipakai selama ada sesi. Menambah pemakaian baterai.",
                    s.keepScreenOn,
                ) { v -> vault.updateSettings { it.copy(keepScreenOn = v) } }
                LabeledSwitch("Getar saat bell", null, s.vibrateOnBell) { v -> vault.updateSettings { it.copy(vibrateOnBell = v) } }
                OutlinedTextField(
                    scrollback,
                    { v ->
                        scrollback = v.filter(Char::isDigit).take(6)
                        scrollback.toIntOrNull()?.let { n -> vault.updateSettings { it.copy(scrollbackLines = n.coerceIn(100, 100_000)) } }
                    },
                    label = { Text("Baris scrollback (tab baru)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ThemePreview(t: TerminalTheme, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(2.dp, if (selected) Palette.Accent else Color.Transparent, RoundedCornerShape(14.dp))
            .background(Color(t.background))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(t.name, color = Color(t.foreground), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row {
            Text("user@srv", color = Color(t.ansi[2]), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            Text(":", color = Color(t.foreground), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            Text("~$ ", color = Color(t.ansi[4]), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
        Text("ls -la", color = Color(t.foreground), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 6.dp)) {
            for (i in 1..6) Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(Color(t.ansi[i])))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnownHostsScreen(nav: Nav) {
    val vault = KonsolApp.container.vault
    val data by vault.data.collectAsState()
    var deleting by remember { mutableStateOf<com.kroombox.konsol.data.KnownHost?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Known hosts") },
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        if (data.knownHosts.isEmpty()) {
            Box(Modifier.padding(pad)) {
                EmptyState(Icons.Rounded.Shield, "Kosong", "Sidik jari server akan tersimpan saat pertama kali Anda konek.")
            }
        } else {
            LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(data.knownHosts, key = { it.hostPort + it.keyType }) { k ->
                    AppCard(contentPadding = PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(k.hostPort, style = MaterialTheme.typography.titleMedium)
                                Text(k.keyType, style = MaterialTheme.typography.labelMedium, color = Palette.Blue)
                                Text(k.fingerprint, style = MonoSmall)
                                Text("ditambahkan ${timeAgo(k.addedAt)}", style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
                            }
                            IconButton(onClick = { deleting = k }) { Icon(Icons.Rounded.Delete, "Hapus", tint = Palette.TextDim) }
                        }
                    }
                }
            }
        }
    }
    deleting?.let { k ->
        ConfirmDialog(
            title = "Lupakan host key?",
            message = "Saat konek berikutnya ke ${k.hostPort}, Anda akan diminta memverifikasi sidik jari lagi.",
            confirmText = "Lupakan", danger = true,
            onConfirm = { vault.deleteKnownHost(k.hostPort, k.keyType) },
            onDismiss = { deleting = null },
        )
    }
}
