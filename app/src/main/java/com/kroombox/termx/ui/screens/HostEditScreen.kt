package com.kroombox.termx.ui.screens

import com.kroombox.termx.R
import androidx.compose.ui.res.stringResource
import com.kroombox.termx.os.OsCatalog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.TextButton
import com.kroombox.termx.ui.components.HostAvatar
import com.kroombox.termx.ui.components.IconPicker
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
import com.kroombox.termx.TermXApp
import com.kroombox.termx.data.AccentColors
import com.kroombox.termx.data.AuthType
import com.kroombox.termx.data.Host
import com.kroombox.termx.data.SecretBox
import com.kroombox.termx.ssh.Tmux
import com.kroombox.termx.ui.Nav
import com.kroombox.termx.ui.components.AppCard
import com.kroombox.termx.ui.components.DropdownField
import com.kroombox.termx.ui.components.LabeledSwitch
import com.kroombox.termx.ui.components.PasswordField
import com.kroombox.termx.ui.components.SectionHeader
import com.kroombox.termx.ui.components.accent
import com.kroombox.termx.ui.theme.Palette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostEditScreen(nav: Nav, hostId: String?) {
    val vault = TermXApp.container.vault
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
    var tmuxName by remember { mutableStateOf(original?.tmuxSessionName ?: "termx") }
    var tmuxMouse by remember { mutableStateOf(original?.tmuxMouse ?: true) }
    var jumpHostId by remember { mutableStateOf(original?.jumpHostId) }
    var keepAlive by remember { mutableStateOf((original?.keepAliveSec ?: 20).toString()) }
    var autoReconnect by remember { mutableStateOf(original?.autoReconnect ?: true) }
    var startup by remember { mutableStateOf(original?.startupCommand ?: "") }
    var notes by remember { mutableStateOf(original?.notes ?: "") }
    var icon by remember { mutableStateOf(original?.icon ?: "auto") }
    var pickIcon by remember { mutableStateOf(false) }
    var redetect by remember { mutableStateOf(false) }
    var sudoPassword by remember { mutableStateOf("") }
    var keepSudoPassword by remember { mutableStateOf(original?.sudoPasswordEnc != null) }
    var sudoUseLogin by remember { mutableStateOf(original?.sudoUseLoginPassword ?: true) }
    var monitorSudo by remember { mutableStateOf(original?.monitorSudo ?: false) }

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
            icon = icon,
            osId = if (redetect) null else base.osId,
            osName = if (redetect) null else base.osName,
            sudoPasswordEnc = when {
                sudoPassword.isNotEmpty() -> SecretBox.encrypt(sudoPassword)
                keepSudoPassword -> original?.sudoPasswordEnc
                else -> null
            },
            sudoUseLoginPassword = sudoUseLogin,
            monitorSudo = monitorSudo,
        )
        vault.saveHost(h)
        nav.back()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (original == null) stringResource(R.string.hostedit_title_new) else stringResource(R.string.hostedit_title_edit)) },
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.hostedit_back)) } },
                actions = {
                    IconButton(onClick = { save() }, enabled = valid) { Icon(Icons.Rounded.Check, stringResource(R.string.hostedit_save)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            SectionHeader(stringResource(R.string.hostedit_section_connection))
            AppCard {
                OutlinedTextField(label, { label = it }, label = { Text(stringResource(R.string.hostedit_name_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        hostname, { hostname = it.trim() }, label = { Text(stringResource(R.string.hostedit_hostname)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        port, { port = it.filter(Char::isDigit).take(5) }, label = { Text(stringResource(R.string.hostedit_port)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(96.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(username, { username = it.trim() }, label = { Text(stringResource(R.string.hostedit_username)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }

            SectionHeader(stringResource(R.string.hostedit_section_auth))
            AppCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = authType == AuthType.PASSWORD, onClick = { authType = AuthType.PASSWORD }, label = { Text(stringResource(R.string.hostedit_auth_password)) })
                    FilterChip(selected = authType == AuthType.KEY, onClick = { authType = AuthType.KEY }, label = { Text(stringResource(R.string.hostedit_auth_key)) })
                }
                Spacer(Modifier.height(10.dp))
                if (authType == AuthType.PASSWORD) {
                    PasswordField(
                        password, { password = it },
                        label = if (original?.passwordEnc != null && keepSavedPassword) stringResource(R.string.hostedit_password_saved) else stringResource(R.string.hostedit_auth_password),
                        placeholder = stringResource(R.string.hostedit_password_placeholder),
                    )
                    if (original?.passwordEnc != null) {
                        LabeledSwitch(stringResource(R.string.hostedit_keep_password), stringResource(R.string.hostedit_keep_password_sub), keepSavedPassword) { keepSavedPassword = it }
                    }
                    Text(
                        stringResource(R.string.hostedit_password_note),
                        style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
                    )
                } else {
                    val pickKeyText = stringResource(R.string.hostedit_pick_key)
                    DropdownField(
                        label = stringResource(R.string.hostedit_key),
                        options = data.keys,
                        selected = data.keys.firstOrNull { it.id == keyId },
                        display = { it?.let { k -> "${k.name} · ${k.type}" } ?: pickKeyText },
                        onSelect = { keyId = it?.id },
                    )
                    if (data.keys.isEmpty()) {
                        Text(stringResource(R.string.hostedit_no_keys), style = MaterialTheme.typography.bodySmall, color = Palette.Warn)
                    }
                }
            }

            SectionHeader(stringResource(R.string.hostedit_section_sudo))
            AppCard {
                Text(
                    stringResource(R.string.hostedit_sudo_note),
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
                )
                if (authType == AuthType.PASSWORD) {
                    LabeledSwitch(stringResource(R.string.hostedit_sudo_use_login), stringResource(R.string.hostedit_sudo_use_login_sub), sudoUseLogin) { sudoUseLogin = it }
                }
                PasswordField(
                    sudoPassword, { sudoPassword = it },
                    label = if (original?.sudoPasswordEnc != null && keepSudoPassword) stringResource(R.string.hostedit_sudo_password_saved) else stringResource(R.string.hostedit_sudo_password_optional),
                    placeholder = stringResource(R.string.hostedit_sudo_password_placeholder),
                )
                if (original?.sudoPasswordEnc != null) {
                    LabeledSwitch(stringResource(R.string.hostedit_keep_sudo_password), stringResource(R.string.hostedit_keep_sudo_password_sub), keepSudoPassword) { keepSudoPassword = it }
                }
                LabeledSwitch(stringResource(R.string.hostedit_monitor_sudo), stringResource(R.string.hostedit_monitor_sudo_sub), monitorSudo) { monitorSudo = it }
            }

            SectionHeader(stringResource(R.string.hostedit_section_tmux))
            AppCard {
                LabeledSwitch(
                    stringResource(R.string.hostedit_always_tmux),
                    stringResource(R.string.hostedit_always_tmux_sub),
                    persistent,
                ) { persistent = it }
                run {
                    OutlinedTextField(tmuxName, { tmuxName = it }, label = { Text(stringResource(R.string.hostedit_tmux_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    LabeledSwitch(stringResource(R.string.hostedit_tmux_mouse), stringResource(R.string.hostedit_tmux_mouse_sub), tmuxMouse) { tmuxMouse = it }
                }
                LabeledSwitch(stringResource(R.string.hostedit_auto_reconnect), stringResource(R.string.hostedit_auto_reconnect_sub), autoReconnect) { autoReconnect = it }
            }

            SectionHeader(stringResource(R.string.hostedit_section_icon))
            AppCard {
                val preview = (original ?: Host(label = "", hostname = "", username = "")).copy(
                    label = label.ifBlank { hostname.ifBlank { "?" } }, icon = icon, colorIndex = colorIndex,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HostAvatar(preview, size = 56.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        val os = OsCatalog.byId(original?.osId)
                        Text(
                            when {
                                icon == "auto" && os != null -> stringResource(R.string.hostedit_icon_auto_os, os.title)
                                icon == "auto" -> stringResource(R.string.hostedit_icon_auto)
                                else -> stringResource(R.string.hostedit_icon_custom)
                            },
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (redetect) {
                            Text(stringResource(R.string.hostedit_redetect_pending), style = MaterialTheme.typography.bodySmall, color = Palette.Warn)
                        } else {
                            original?.osName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim) }
                        }
                        if (original?.osId != null && !redetect) {
                            TextButton(onClick = { redetect = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                Text(stringResource(R.string.hostedit_redetect))
                            }
                        }
                    }
                }
                TextButton(onClick = { pickIcon = !pickIcon }) {
                    Text(if (pickIcon) stringResource(R.string.hostedit_close_icon_picker) else stringResource(R.string.hostedit_change_icon))
                }
                AnimatedVisibility(pickIcon) {
                    IconPicker(
                        selected = icon, onSelect = { icon = it }, accentColor = accent(colorIndex),
                        allowAuto = true, autoLabel = stringResource(R.string.hostedit_icon_auto_label),
                    )
                }
            }

            SectionHeader(stringResource(R.string.hostedit_section_organize))
            AppCard {
                val noGroupText = stringResource(R.string.hostedit_no_group)
                DropdownField(
                    label = stringResource(R.string.hostedit_group),
                    options = data.groups,
                    selected = data.groups.firstOrNull { it.id == groupId },
                    display = { it?.name ?: noGroupText },
                    onSelect = { groupId = it?.id },
                    allowNone = true, noneLabel = noGroupText,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(tags, { tags = it }, label = { Text(stringResource(R.string.hostedit_tags)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.hostedit_color), style = MaterialTheme.typography.labelLarge)
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

            SectionHeader(stringResource(R.string.hostedit_section_advanced))
            AppCard {
                val directText = stringResource(R.string.hostedit_direct)
                DropdownField(
                    label = stringResource(R.string.hostedit_jump_host),
                    options = data.hosts.filter { it.id != original?.id },
                    selected = data.hosts.firstOrNull { it.id == jumpHostId },
                    display = { it?.let { h -> "${h.label} (${h.address})" } ?: directText },
                    onSelect = { jumpHostId = it?.id },
                    allowNone = true, noneLabel = stringResource(R.string.hostedit_direct_no_jump),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    keepAlive, { keepAlive = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.hostedit_keepalive)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    startup, { startup = it }, label = { Text(stringResource(R.string.hostedit_startup)) },
                    placeholder = { Text(stringResource(R.string.hostedit_startup_placeholder)) }, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.hostedit_notes)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }

            Spacer(Modifier.height(20.dp))
            Button(onClick = { save() }, enabled = valid, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (original == null) stringResource(R.string.hostedit_save_host) else stringResource(R.string.hostedit_save_changes))
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
