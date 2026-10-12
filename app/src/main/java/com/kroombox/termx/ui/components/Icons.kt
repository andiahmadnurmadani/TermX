package com.kroombox.termx.ui.components

import com.kroombox.termx.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kroombox.termx.data.Host
import com.kroombox.termx.os.OsCatalog
import com.kroombox.termx.os.OsIcon
import com.kroombox.termx.ui.theme.Palette

/** Ikon bawaan (Material) yang bisa dipilih untuk host dan snippet. */
object AppIcons {
    val all: List<Pair<String, ImageVector>> = listOf(
        "bolt" to Icons.Rounded.Bolt,
        "terminal" to Icons.Rounded.Terminal,
        "dns" to Icons.Rounded.Dns,
        "storage" to Icons.Rounded.Storage,
        "cloud" to Icons.Rounded.Cloud,
        "router" to Icons.Rounded.Router,
        "lan" to Icons.Rounded.Lan,
        "hub" to Icons.Rounded.Hub,
        "memory" to Icons.Rounded.Memory,
        "computer" to Icons.Rounded.Computer,
        "laptop" to Icons.Rounded.Laptop,
        "home" to Icons.Rounded.Home,
        "work" to Icons.Rounded.Work,
        "public" to Icons.Rounded.Public,
        "code" to Icons.Rounded.Code,
        "rocket" to Icons.Rounded.RocketLaunch,
        "science" to Icons.Rounded.Science,
        "game" to Icons.Rounded.SportsEsports,
        "play" to Icons.Rounded.PlayArrow,
        "refresh" to Icons.Rounded.Refresh,
        "restart" to Icons.Rounded.RestartAlt,
        "power" to Icons.Rounded.PowerSettingsNew,
        "update" to Icons.Rounded.SystemUpdate,
        "clean" to Icons.Rounded.CleaningServices,
        "backup" to Icons.Rounded.Backup,
        "upload" to Icons.Rounded.CloudUpload,
        "download" to Icons.Rounded.CloudDownload,
        "info" to Icons.Rounded.Info,
        "list" to Icons.Rounded.Checklist,
        "history" to Icons.Rounded.History,
        "schedule" to Icons.Rounded.Schedule,
        "search" to Icons.Rounded.Search,
        "speed" to Icons.Rounded.Speed,
        "monitor" to Icons.Rounded.MonitorHeart,
        "layers" to Icons.Rounded.Layers,
        "folder" to Icons.Rounded.Folder,
        "settings" to Icons.Rounded.Settings,
        "build" to Icons.Rounded.Build,
        "bug" to Icons.Rounded.BugReport,
        "lock" to Icons.Rounded.Lock,
        "key" to Icons.Rounded.Key,
        "shield" to Icons.Rounded.Shield,
        "security" to Icons.Rounded.Security,
    )
    private val byKey = all.toMap()
    fun byKey(key: String): ImageVector? = byKey[key]
}

/** Isi spesifikasi ikon ("auto", "initials", "os:<id>", "mi:<key>", "emoji:<teks>"). */
sealed class IconSpec {
    object Auto : IconSpec()
    object Initials : IconSpec()
    data class Os(val id: String) : IconSpec()
    data class Material(val key: String) : IconSpec()
    data class Emoji(val text: String) : IconSpec()

    fun encode(): String = when (this) {
        Auto -> "auto"
        Initials -> "initials"
        is Os -> "os:$id"
        is Material -> "mi:$key"
        is Emoji -> "emoji:$text"
    }

    companion object {
        fun parse(s: String?): IconSpec = when {
            s.isNullOrBlank() || s == "auto" -> Auto
            s == "initials" -> Initials
            s.startsWith("os:") -> Os(s.removePrefix("os:"))
            s.startsWith("mi:") -> Material(s.removePrefix("mi:"))
            s.startsWith("emoji:") -> Emoji(s.removePrefix("emoji:")).takeIf { it.text.isNotBlank() } ?: Auto
            else -> Auto
        }
    }
}

private val osVectors = HashMap<String, ImageVector>()

/** Logo OS sebagai ImageVector (path Simple Icons, viewBox 24). */
fun OsIcon.vector(): ImageVector = synchronized(osVectors) {
    osVectors.getOrPut(id) {
        ImageVector.Builder(
            name = "os_$id", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
        ).addPath(pathData = addPathNodes(path), fill = SolidColor(Color.White)).build()
    }
}

fun OsIcon.composeColor(): Color = Color(color)

/**
 * Kotak ikon serbaguna. [spec] menentukan isinya; [osId] dipakai bila spec "auto". Warna logo OS
 * mengikuti warna merek, ikon lain memakai [accentColor].
 */
@Composable
fun SpecAvatar(
    spec: String,
    label: String,
    accentColor: Color,
    size: Dp = 44.dp,
    osId: String? = null,
    fallback: ImageVector? = null,
) {
    val parsed = IconSpec.parse(spec)
    val resolved = if (parsed == IconSpec.Auto) {
        osId?.let { OsCatalog.byId(it) }?.let { IconSpec.Os(it.id) } ?: if (fallback != null) null else IconSpec.Initials
    } else parsed
    val os = (resolved as? IconSpec.Os)?.let { OsCatalog.byId(it.id) }
    val c = os?.composeColor() ?: accentColor
    val shape = RoundedCornerShape(size * 0.3f)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(c.copy(alpha = 0.16f))
            .border(1.dp, c.copy(alpha = 0.35f), shape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            os != null -> Icon(os.vector(), contentDescription = os.title, tint = c, modifier = Modifier.size(size * 0.56f))
            resolved is IconSpec.Material && AppIcons.byKey(resolved.key) != null ->
                Icon(AppIcons.byKey(resolved.key)!!, contentDescription = null, tint = c, modifier = Modifier.size(size * 0.52f))
            resolved is IconSpec.Emoji -> Text(resolved.text, fontSize = (size.value * 0.48f).sp)
            resolved == null && fallback != null -> Icon(fallback, contentDescription = null, tint = c, modifier = Modifier.size(size * 0.52f))
            else -> {
                val initials = label.split(' ', '-', '_', '.').filter { it.isNotBlank() }.take(2)
                    .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
                Text(initials, color = c, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp)
            }
        }
    }
}

/** Avatar host: logo OS terdeteksi, ikon pilihan, emoji, atau inisial. */
@Composable
fun HostAvatar(host: Host, size: Dp = 44.dp) {
    SpecAvatar(host.icon, host.label, accent(host.colorIndex), size, osId = host.osId)
}

/**
 * Pemilih ikon: logo OS, ikon bawaan, dan emoji. [allowAuto] menambahkan pilihan "Otomatis"
 * (untuk host: logo OS yang terdeteksi) dan "Inisial".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPicker(
    selected: String,
    onSelect: (String) -> Unit,
    accentColor: Color,
    allowAuto: Boolean,
    autoLabel: String = stringResource(R.string.icons_auto),
    showOs: Boolean = true,
) {
    val sel = IconSpec.parse(selected)
    var emoji by remember { mutableStateOf((sel as? IconSpec.Emoji)?.text ?: "") }
    // Grup dilipat secara default; yang berisi pilihan saat ini dibuka.
    var osOpen by remember { mutableStateOf(sel is IconSpec.Os) }
    var iconOpen by remember { mutableStateOf(sel is IconSpec.Material) }

    @Composable
    fun Cell(spec: String, isSelected: Boolean, content: @Composable () -> Unit) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(2.dp, if (isSelected) Palette.Accent else Color.Transparent, RoundedCornerShape(14.dp))
                .clickable { onSelect(spec) },
            contentAlignment = Alignment.Center,
        ) { content() }
    }

    Column {
        if (allowAuto) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(autoLabel, sel == IconSpec.Auto) { onSelect("auto") }
                ChoiceChip(stringResource(R.string.icons_initials), sel == IconSpec.Initials) { onSelect("initials") }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (showOs) {
            GroupHeader(stringResource(R.string.icons_os_logos), OsCatalog.all.size, osOpen) { osOpen = !osOpen }
            AnimatedVisibility(osOpen) {
                FlowRow(
                    Modifier.padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OsCatalog.all.forEach { os ->
                        val spec = "os:${os.id}"
                        Cell(spec, selected == spec) { SpecAvatar(spec, os.title, accentColor, size = 40.dp) }
                    }
                }
            }
        }
        GroupHeader(stringResource(R.string.icons_icons), AppIcons.all.size, iconOpen) { iconOpen = !iconOpen }
        AnimatedVisibility(iconOpen) {
            FlowRow(
                Modifier.padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AppIcons.all.forEach { (key, _) ->
                    val spec = "mi:$key"
                    Cell(spec, selected == spec) { SpecAvatar(spec, key, accentColor, size = 40.dp) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = emoji,
            onValueChange = { v ->
                emoji = v.take(8)
                if (emoji.isNotBlank()) onSelect("emoji:${emoji.trim()}")
            },
            label = { Text(stringResource(R.string.icons_emoji_label)) },
            placeholder = { Text(stringResource(R.string.icons_emoji_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun GroupHeader(title: String, count: Int, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onToggle).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.labelMedium, color = Palette.TextDim)
        Spacer(Modifier.width(6.dp))
        Icon(
            if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = stringResource(if (open) R.string.icons_collapse else R.string.icons_expand), tint = Palette.TextDim,
        )
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}
