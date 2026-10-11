package com.kroombox.konsol.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kroombox.konsol.KonsolApp
import com.kroombox.konsol.data.AccentColors
import com.kroombox.konsol.data.AuthType
import com.kroombox.konsol.data.Host
import com.kroombox.konsol.data.SecretBox
import com.kroombox.konsol.ssh.Tmux
import com.kroombox.konsol.ui.Nav
import com.kroombox.konsol.ui.components.AppCard
import com.kroombox.konsol.ui.components.DropdownField
import com.kroombox.konsol.ui.components.LabeledSwitch
import com.kroombox.konsol.ui.components.PasswordField
import com.kroombox.konsol.ui.components.SectionHeader
import com.kroombox.konsol.ui.components.accent
import com.kroombox.konsol.ui.theme.Palette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostEditScreen(nav: Nav, hostId: String?) {
    val vault = KonsolApp.container.vault
    val data by vault.data.collectAsState()
    val original = remember(hostId) { vault.host(hostId) }
    val defaults = data.settings

    var label by remember { mutableStateOf(original?.label ?: "") }
    var hostname by remember { mutableStateOf(original?.hostname ?: "") }
    var port by remember { mutableStateOf((original?.port ?: 22).toString()) }
    var username by remember { mutableStateOf(original?.username ?: "root") }
    var authType by remember { mutableStateOf(original?.authType ?: AuthType.PASSWORD) }
    var password by remember { mutableStateOf("") }
    var keepSavedPassword by remember { mutableStateOf(original?.passwordEnc != null) }
    var keyId by remember { mutableStateOf(original?.keyId) }
    var groupId by remember { mutableStateOf(original?.groupId) }
    var colorIndex by remember { mutableStateOf(original?.colorIndex ?: data.hosts.size % AccentColors.size) }
    var tags by remember { mutableStateOf(original?.tags?.joinToString(", ") ?: "") }
    var persistent by remember { mutableStateOf(original?.persistentSession ?: defaults.defaultPersistent) }
    var tmuxName by remember { mutableStateOf(original?.tmuxSessionName ?: "konsol") }
    var tmuxMouse by remember { mutableStateOf(original?.tmuxMouse ?: true) }
    var jumpHostId by remember { mutableStateOf(original?.jumpHostId) }
    var keepAlive by remember { mutableStateOf((original?.keepAliveSec ?: 20).toString()) }
    var autoReconnect by remember { mutableStateOf(original?.autoReconnect ?: true) }
    var startup by remember { mutableStateOf(original?.startupCommand ?: "") }
    var notes by remember { mutableStateOf(original?.notes ?: "") }

    val portInt = port.toIntOrNull()
    val valid = hostname.isNotBlank() && username.isNotBlank() && portInt != null && portInt in 1..65535 &&
        (authType == AuthType.PASSWORD || keyId != null)

    fun save() {
        val base = original ?: Host(label = "", hostname = "", username = "")
        val passwordEnc = when {
            authType != AuthType.PASSWORD -> null
            password.isNotEmpty() -> SecretBox.encrypt(password)
            keepSavedPassword -> original?.passwordEnc
            else -> null
        }
        val h = base.copy(
            label = label.trim().ifBlank { hostname.trim() },
            hostname = hostname.trim(),
            port = portInt ?: 22,
            username = username.trim(),
            authType = authType,
            passwordEnc = passwordEnc,
            keyId = if (authType == AuthType.KEY) keyId else null,
            groupId = groupId,
            colorIndex = colorIndex,
            tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
            persistentSession = persistent,
            tmuxSessionName = Tmux.sanitize(tmuxName),
            tmuxMouse = tmuxMouse,
            jumpHostId = jumpHostId?.takeIf { it != base.id },
            keepAliveSec = keepAlive.toIntOrNull()?.coerceIn(5, 300) ?: 20,
            autoReconnect = autoReconnect,
            startupCommand = startup,
            notes = notes,
        )
        vault.saveHost(h)
        nav.back()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (original == null) "Host baru" else "Edit host") },
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") } },
                actions = {
                    IconButton(onClick = { save() }, enabled = valid) { Icon(Icons.Rounded.Check, "Simpan") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            SectionHeader("Koneksi")
            AppCard {
                OutlinedTextField(label, { label = it }, label = { Text("Nama (opsional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        hostname, { hostname = it.trim() }, label = { Text("Hostname / IP") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        port, { port = it.filter(Char::isDigit).take(5) }, label = { Text("Port") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(96.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(username, { username = it.trim() }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }

            SectionHeader("Autentikasi")
            AppCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = authType == AuthType.PASSWORD, onClick = { authType = AuthType.PASSWORD }, label = { Text("Password") })
                    FilterChip(selected = authType == AuthType.KEY, onClick = { authType = AuthType.KEY }, label = { Text("Kunci SSH") })
                }
                Spacer(Modifier.height(10.dp))
                if (authType == AuthType.PASSWORD) {
                    PasswordField(
                        password, { password = it },
                        label = if (original?.passwordEnc != null && keepSavedPassword) "Password (tersimpan — isi untuk mengganti)" else "Password",
                        placeholder = "Kosongkan untuk ditanya saat konek",
                    )
                    if (original?.passwordEnc != null) {
                        LabeledSwitch("Simpan password lama", "Matikan untuk menghapus password tersimpan", keepSavedPassword) { keepSavedPassword = it }
                    }
                    Text(
                        "Password dienkripsi dengan Android Keystore dan tidak pernah meninggalkan perangkat.",
                        style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
                    )
                } else {
                    DropdownField(
                        label = "Kunci",
                        options = data.keys,
                        selected = data.keys.firstOrNull { it.id == keyId },
                        display = { it?.let { k -> "${k.name} · ${k.type}" } ?: "Pilih kunci" },
                        onSelect = { keyId = it?.id },
                    )
                    if (data.keys.isEmpty()) {
                        Text("Belum ada kunci. Buat atau impor di tab Keychain.", style = MaterialTheme.typography.bodySmall, color = Palette.Warn)
                    }
                }
            }

            SectionHeader("Sesi persisten (tmux)")
            AppCard {
                LabeledSwitch(
                    "Jalankan di tmux",
                    "Proses tetap berjalan di server saat HP terputus; tersambung lagi otomatis ke sesi yang sama.",
                    persistent,
                ) { persistent = it }
                if (persistent) {
                    OutlinedTextField(tmuxName, { tmuxName = it }, label = { Text("Nama sesi tmux") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    LabeledSwitch("Mouse tmux", "Geser layar untuk menggulir riwayat tmux", tmuxMouse) { tmuxMouse = it }
                }
                LabeledSwitch("Sambung ulang otomatis", "Coba lagi saat jaringan putus atau berganti", autoReconnect) { autoReconnect = it }
            }

            SectionHeader("Organisasi")
            AppCard {
                DropdownField(
                    label = "Grup",
                    options = data.groups,
                    selected = data.groups.firstOrNull { it.id == groupId },
                    display = { it?.name ?: "Tanpa grup" },
                    onSelect = { groupId = it?.id },
                    allowNone = true, noneLabel = "Tanpa grup",
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(tags, { tags = it }, label = { Text("Tag (pisahkan dengan koma)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text("Warna", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AccentColors.indices.forEach { i ->
                        val c = accent(i)
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(c)
                                .border(3.dp, if (i == colorIndex) Color.White else Color.Transparent, CircleShape)
                                .clickable { colorIndex = i },
                        )
                    }
                }
            }

            SectionHeader("Lanjutan")
            AppCard {
                DropdownField(
                    label = "Jump host (bastion)",
                    options = data.hosts.filter { it.id != original?.id },
                    selected = data.hosts.firstOrNull { it.id == jumpHostId },
                    display = { it?.let { h -> "${h.label} (${h.address})" } ?: "Langsung" },
                    onSelect = { jumpHostId = it?.id },
                    allowNone = true, noneLabel = "Langsung (tanpa jump host)",
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    keepAlive, { keepAlive = it.filter(Char::isDigit).take(3) }, label = { Text("Keep-alive (detik)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    startup, { startup = it }, label = { Text("Perintah awal (opsional)") },
                    placeholder = { Text("mis. cd /srv/app && claude") }, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(notes, { notes = it }, label = { Text("Catatan") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }

            Spacer(Modifier.height(20.dp))
            Button(onClick = { save() }, enabled = valid, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (original == null) "Simpan host" else "Simpan perubahan")
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
