package com.kroombox.termx.ui.screens

import com.kroombox.termx.i18n.S
import com.kroombox.termx.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kroombox.termx.TermXApp
import com.kroombox.termx.data.ForwardRule
import com.kroombox.termx.data.ForwardType
import com.kroombox.termx.ssh.ForwardStatus
import com.kroombox.termx.ui.Nav
import com.kroombox.termx.ui.components.AppCard
import com.kroombox.termx.ui.components.ConfirmDialog
import com.kroombox.termx.ui.components.DropdownField
import com.kroombox.termx.ui.components.EmptyState
import com.kroombox.termx.ui.components.LabeledSwitch
import com.kroombox.termx.ui.components.StatusDot
import com.kroombox.termx.ui.theme.MonoSmall
import com.kroombox.termx.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun describe(r: ForwardRule): String = when (r.type) {
    ForwardType.LOCAL -> S.get(R.string.fwd_desc_local, "${r.bindHost}:${r.bindPort}", "${r.destHost}:${r.destPort}")
    ForwardType.REMOTE -> S.get(R.string.fwd_desc_remote, "${r.bindHost}:${r.bindPort}", "${r.destHost}:${r.destPort}")
    ForwardType.DYNAMIC -> S.get(R.string.fwd_desc_dynamic, "${r.bindHost}:${r.bindPort}")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardsScreen(nav: Nav, hostFilter: String?) {
    val vault = TermXApp.container.vault
    val mgr = TermXApp.container.sessions
    val data by vault.data.collectAsState()
    val status by mgr.forwards.status.collectAsState()
    var editing by remember { mutableStateOf<ForwardRule?>(null) }
    var creating by remember { mutableStateOf(false) }

    val rules = data.forwards.filter { hostFilter == null || it.hostId == hostFilter }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fwd_title)) },
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.fwd_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if (data.hosts.isNotEmpty()) {
                FloatingActionButton(onClick = { creating = true }, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Rounded.Add, stringResource(R.string.fwd_add))
                }
            }
        },
    ) { pad ->
        if (rules.isEmpty()) {
            Box(Modifier.padding(pad)) {
                EmptyState(
                    Icons.Rounded.SwapHoriz, stringResource(R.string.fwd_empty_title),
                    stringResource(R.string.fwd_empty_msg),
                )
            }
        } else {
            LazyColumn(
                Modifier.padding(pad),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(rules, key = { it.id }) { r ->
                    ForwardCard(r, status[r.id], data.hosts.firstOrNull { it.id == r.hostId }?.label ?: "?", onEdit = { editing = r })
                }
            }
        }
    }

    if (creating || editing != null) {
        ForwardEditor(
            initial = editing,
            defaultHost = hostFilter,
            onDismiss = { creating = false; editing = null },
        )
    }
}

@Composable
private fun ForwardCard(r: ForwardRule, st: ForwardStatus?, hostLabel: String, onEdit: () -> Unit) {
    val mgr = TermXApp.container.sessions
    var confirmDelete by remember { mutableStateOf(false) }
    val running = st?.running == true
    AppCard(contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(if (running) Palette.Accent else if (st?.error != null) Palette.Danger else Palette.TextDim, 10.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.label.ifBlank { "${r.type.name.lowercase()} :${r.bindPort}" }, style = MaterialTheme.typography.titleMedium)
                Text(hostLabel, style = MaterialTheme.typography.labelMedium, color = Palette.Blue)
                Text(describe(r), style = MonoSmall)
                st?.error?.let { Text(it, color = Palette.Danger, style = MaterialTheme.typography.bodySmall) }
                if (running && r.type == ForwardType.DYNAMIC) {
                    Text(stringResource(R.string.fwd_active_connections, st?.connections ?: 0), style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
                }
            }
            Switch(checked = running, onCheckedChange = { on ->
                mgr.scope.launch(Dispatchers.IO) { if (on) mgr.forwards.start(r) else mgr.forwards.stop(r.id) }
            })
        }
        Row {
            TextButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.fwd_edit)) }
            TextButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Rounded.Delete, null, tint = Palette.Danger); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.fwd_delete), color = Palette.Danger)
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.fwd_delete_title), message = describe(r), confirmText = stringResource(R.string.fwd_delete), danger = true,
            onConfirm = {
                mgr.scope.launch(Dispatchers.IO) {
                    mgr.forwards.stop(r.id)
                    TermXApp.container.vault.deleteForward(r.id)
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun ForwardEditor(initial: ForwardRule?, defaultHost: String?, onDismiss: () -> Unit) {
    val chooseHost = stringResource(R.string.fwd_choose_host)
    val vault = TermXApp.container.vault
    val hosts = vault.current.hosts
    var hostId by remember { mutableStateOf(initial?.hostId ?: defaultHost ?: hosts.firstOrNull()?.id) }
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: ForwardType.LOCAL) }
    var bindHost by remember { mutableStateOf(initial?.bindHost ?: "127.0.0.1") }
    var bindPort by remember { mutableStateOf(initial?.bindPort?.toString() ?: "") }
    var destHost by remember { mutableStateOf(initial?.destHost ?: "127.0.0.1") }
    var destPort by remember { mutableStateOf(initial?.destPort?.takeIf { it > 0 }?.toString() ?: "") }
    var autoStart by remember { mutableStateOf(initial?.autoStart ?: false) }

    val bp = bindPort.toIntOrNull()
    val destP = destPort.toIntOrNull()
    val valid = hostId != null && bp != null && bp in 1..65535 && (type == ForwardType.DYNAMIC || (destP != null && destP in 1..65535 && destHost.isNotBlank()))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.fwd_new_rule else R.string.fwd_edit_rule)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownField(
                    label = stringResource(R.string.fwd_ssh_host), options = hosts, selected = hosts.firstOrNull { it.id == hostId },
                    display = { it?.label ?: chooseHost }, onSelect = { hostId = it?.id },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(type == ForwardType.LOCAL, { type = ForwardType.LOCAL }, { Text(stringResource(R.string.fwd_type_local)) })
                    FilterChip(type == ForwardType.REMOTE, { type = ForwardType.REMOTE }, { Text("Remote") })
                    FilterChip(type == ForwardType.DYNAMIC, { type = ForwardType.DYNAMIC }, { Text("SOCKS") })
                }
                Text(
                    when (type) {
                        ForwardType.LOCAL -> stringResource(R.string.fwd_help_local)
                        ForwardType.REMOTE -> stringResource(R.string.fwd_help_remote)
                        ForwardType.DYNAMIC -> stringResource(R.string.fwd_help_dynamic)
                    },
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
                )
                OutlinedTextField(label, { label = it }, label = { Text(stringResource(R.string.fwd_name_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row {
                    OutlinedTextField(bindHost, { bindHost = it.trim() }, label = { Text(stringResource(if (type == ForwardType.REMOTE) R.string.fwd_bind_server else R.string.fwd_bind_phone)) }, singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(bindPort, { bindPort = it.filter(Char::isDigit).take(5) }, label = { Text("Port") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(96.dp))
                }
                if (type != ForwardType.DYNAMIC) {
                    Row {
                        OutlinedTextField(destHost, { destHost = it.trim() }, label = { Text(stringResource(R.string.fwd_dest_host)) }, singleLine = true, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(destPort, { destPort = it.filter(Char::isDigit).take(5) }, label = { Text("Port") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(96.dp))
                    }
                }
                if (bp != null && bp < 1024 && type != ForwardType.REMOTE) {
                    Text(stringResource(R.string.fwd_low_port_warning), color = Palette.Warn, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(2.dp))
                LabeledSwitch(stringResource(R.string.fwd_autostart), stringResource(R.string.fwd_autostart_desc), autoStart) { autoStart = it }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val base = initial ?: ForwardRule(hostId = hostId!!, bindPort = bp!!)
                vault.saveForward(
                    base.copy(
                        hostId = hostId!!, label = label.trim(), type = type,
                        bindHost = bindHost.ifBlank { "127.0.0.1" }, bindPort = bp!!,
                        destHost = destHost.ifBlank { "127.0.0.1" }, destPort = destP ?: 0, autoStart = autoStart,
                    ),
                )
                onDismiss()
            }) { Text(stringResource(R.string.fwd_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.fwd_cancel)) } },
    )
}
