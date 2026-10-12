package com.kroombox.termx.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import com.kroombox.termx.ui.components.StatusDot
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kroombox.termx.TermXApp
import com.kroombox.termx.ssh.ServerStats
import com.kroombox.termx.ui.Nav
import com.kroombox.termx.ui.components.AppCard
import com.kroombox.termx.ui.components.RingGauge
import com.kroombox.termx.ui.components.SectionHeader
import com.kroombox.termx.ui.components.UsageBar
import com.kroombox.termx.ui.components.formatBytes
import com.kroombox.termx.ui.components.formatDuration
import com.kroombox.termx.ui.components.usageColor
import com.kroombox.termx.ui.theme.Mono
import com.kroombox.termx.ui.theme.MonoSmall
import com.kroombox.termx.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(nav: Nav, hostId: String) {
    val mgr = TermXApp.container.sessions
    val vault = TermXApp.container.vault
    val data by vault.data.collectAsState()
    val host = data.hosts.firstOrNull { it.id == hostId }
    val useSudo = host?.monitorSudo == true
    var stats by remember { mutableStateOf<ServerStats?>(null) }
    var prev by remember { mutableStateOf<ServerStats?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var live by remember { mutableStateOf(true) }
    // Naik saat pengguna meminta coba lagi (mis. setelah membatalkan dialog password sudo).
    var retry by remember { mutableIntStateOf(0) }

    fun setSudo(on: Boolean) {
        vault.host(hostId)?.let { vault.saveHost(it.copy(monitorSudo = on)) }
    }

    LaunchedEffect(hostId, live, useSudo, retry) {
        mgr.acquire(hostId, "monitor")
        try {
        while (true) {
            try {
                val s = withContext(Dispatchers.IO) {
                    val r = if (useSudo) {
                        // Perintah monitor dijalankan sebagai root: sudo -n (NOPASSWD) atau password
                        // sudo yang dikirim lewat stdin. Password ditanya sekali bila belum tersimpan.
                        mgr.execSudo(hostId, ServerStats.SCRIPT, timeoutSec = 20)
                    } else {
                        mgr.connectionFor(hostId).exec("sh -s", timeoutSec = 15, stdin = ServerStats.SCRIPT)
                    }
                    ServerStats.parse(r.stdout)
                }
                prev = stats
                stats = s
                error = null
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
                // Kesalahan sudo (password dibatalkan/salah) tidak diulang tiap 3 detik.
                if (useSudo && (error?.contains("sudo", ignoreCase = true) == true)) break
            }
            if (!live) break
            delay(3000)
        }
        } finally {
            // Dilepas juga saat layar ditutup di tengah proses konek.
            mgr.release(hostId, "monitor")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Monitor", style = MaterialTheme.typography.titleMedium)
                        Text(host?.label ?: "", style = MaterialTheme.typography.labelMedium, color = Palette.TextDim)
                    }
                },
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") } },
                actions = {
                    FilterChip(
                        selected = useSudo,
                        onClick = { setSudo(!useSudo) },
                        label = { Text("sudo") },
                        leadingIcon = { Icon(Icons.Rounded.AdminPanelSettings, null, Modifier.size(18.dp)) },
                    )
                    IconButton(onClick = { live = !live }) {
                        Icon(if (live) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (live) "Jeda" else "Lanjut")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        val s = stats
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            error?.let { msg ->
                AppCard(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(msg, color = Palette.Danger, style = MaterialTheme.typography.bodyMedium)
                    if (useSudo && msg.contains("sudo", ignoreCase = true)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { retry++ }) { Text("Coba lagi") }
                            TextButton(onClick = { setSudo(false) }) { Text("Tanpa sudo") }
                        }
                    }
                }
            }
            if (s == null) {
                if (error == null) {
                    Spacer(Modifier.height(24.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(if (useSudo) "Mengambil data dari server (read-only, sudo)…" else "Mengambil data dari server (read-only)…", color = Palette.TextDim, modifier = Modifier.padding(top = 12.dp))
                }
                return@Column
            }
            AppCard {
                Text(s.hostname, style = MaterialTheme.typography.titleLarge)
                Text(s.os, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                Text("kernel ${s.kernel}", style = MonoSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Stat("Uptime", formatDuration(s.uptimeSec))
                    Stat("CPU", "${s.cpus} core")
                    if (s.dockerAccess == "ok") s.dockerRunning?.let { Stat("Docker", "$it jalan") }
                    if (s.asRoot) Stat("Mode", "root (sudo)")
                }
            }
            if (s.dockerAccess == "denied" && !s.asRoot) {
                Spacer(Modifier.height(12.dp))
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AdminPanelSettings, null, tint = Palette.Warn)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Docker butuh sudo", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "User ${host?.username ?: ""} tidak punya akses ke Docker. Aktifkan sudo untuk melihat container.",
                                style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
                            )
                        }
                        TextButton(onClick = { setSudo(true) }) { Text("Pakai sudo") }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            AppCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RingGauge(s.cpuPercent, "CPU", "load ${fmt(s.load1)}", usageColor(s.cpuPercent ?: 0.0))
                    RingGauge(
                        s.memUsedPercent, "RAM",
                        "${formatBytes((s.memTotalKb - s.memAvailKb) * 1024)} / ${formatBytes(s.memTotalKb * 1024)}",
                        usageColor(s.memUsedPercent),
                    )
                    if (s.swapTotalKb > 0) {
                        RingGauge(s.swapUsedPercent, "Swap", formatBytes((s.swapTotalKb - s.swapFreeKb) * 1024), usageColor(s.swapUsedPercent))
                    }
                }
            }

            SectionHeader("Beban (1 / 5 / 15 menit)")
            AppCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(s.load1, s.load5, s.load15).forEach { l ->
                        val pct = if (s.cpus > 0) l / s.cpus * 100 else 0.0
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(fmt(l), style = MaterialTheme.typography.headlineSmall, color = usageColor(pct))
                            Text("${pct.toInt()}% kapasitas", style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
                        }
                    }
                }
            }

            val p = prev
            if (p != null && s.timestamp > p.timestamp) {
                val dt = (s.timestamp - p.timestamp) / 1000.0
                val rx = ((s.netRxBytes - p.netRxBytes) / dt).coerceAtLeast(0.0)
                val tx = ((s.netTxBytes - p.netTxBytes) / dt).coerceAtLeast(0.0)
                SectionHeader("Jaringan")
                AppCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat("↓ Masuk", "${formatBytes(rx.toLong())}/s")
                        Stat("↑ Keluar", "${formatBytes(tx.toLong())}/s")
                        Stat("Total ↓", formatBytes(s.netRxBytes))
                    }
                }
            }

            if (s.disks.isNotEmpty()) {
                SectionHeader("Disk")
                AppCard {
                    s.disks.forEachIndexed { i, d ->
                        if (i > 0) Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Text(d.mount, fontFamily = Mono, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${formatBytes(d.usedBytes)} / ${formatBytes(d.totalBytes)}", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                        }
                        Spacer(Modifier.height(6.dp))
                        UsageBar((d.percent / 100).toFloat(), usageColor(d.percent))
                    }
                }
            }

            if (s.dockerAccess == "ok" && s.containers.isNotEmpty()) {
                SectionHeader("Container Docker (${s.containers.count { it.running }}/${s.containers.size} jalan)")
                AppCard {
                    s.containers.forEachIndexed { i, c ->
                        if (i > 0) Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(if (c.running) Palette.Accent else Palette.TextDim, 10.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(c.image, style = MonoSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                c.status, style = MaterialTheme.typography.labelSmall,
                                color = if (c.running) Palette.Accent else Palette.TextDim, maxLines = 1,
                            )
                        }
                    }
                }
            }

            if (s.processes.isNotEmpty()) {
                SectionHeader("Proses teratas (CPU)")
                AppCard {
                    Row {
                        Text("PID", style = MaterialTheme.typography.labelSmall, color = Palette.TextDim, modifier = Modifier.width(64.dp))
                        Text("PERINTAH", style = MaterialTheme.typography.labelSmall, color = Palette.TextDim, modifier = Modifier.weight(1f))
                        Text("CPU", style = MaterialTheme.typography.labelSmall, color = Palette.TextDim, modifier = Modifier.width(56.dp))
                        Text("MEM", style = MaterialTheme.typography.labelSmall, color = Palette.TextDim, modifier = Modifier.width(56.dp))
                    }
                    s.processes.forEach { pr ->
                        Row(Modifier.padding(top = 6.dp)) {
                            Text("${pr.pid}", style = MonoSmall, modifier = Modifier.width(64.dp))
                            Text(pr.command, fontFamily = Mono, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${fmt(pr.cpu)}%", style = MonoSmall, color = usageColor(pr.cpu), modifier = Modifier.width(56.dp))
                            Text("${fmt(pr.mem)}%", style = MonoSmall, modifier = Modifier.width(56.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

private fun fmt(d: Double) = String.format(Locale.US, "%.2f", d)
