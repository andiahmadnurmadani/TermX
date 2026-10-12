package com.kroombox.termx.ui.screens

import com.kroombox.termx.i18n.S
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.kroombox.termx.R
import com.kroombox.termx.service.BatteryOptimization
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FontDownload
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
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kroombox.termx.BuildConfig
import com.kroombox.termx.TermXApp
import com.kroombox.termx.data.AppSettings
import com.kroombox.termx.data.CustomFont
import com.kroombox.termx.terminal.TerminalFonts
import com.kroombox.termx.terminal.TerminalTheme
import com.kroombox.termx.ui.Nav
import com.kroombox.termx.ui.components.AppCard
import com.kroombox.termx.ui.components.ConfirmDialog
import com.kroombox.termx.ui.components.EmptyState
import com.kroombox.termx.ui.components.LabeledSwitch
import com.kroombox.termx.ui.components.SectionHeader
import com.kroombox.termx.ui.components.timeAgo
import com.kroombox.termx.ui.theme.MonoSmall
import com.kroombox.termx.ui.theme.Palette

@Composable
fun MoreScreen(nav: Nav, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vault = TermXApp.container.vault
    var message by remember { mutableStateOf<String?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            message = try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(vault.exportWithoutSecrets().toByteArray()) }
                S.get(R.string.more_export_done)
            } catch (e: Exception) {
                S.get(R.string.more_export_failed, e.message ?: "")
            }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            message = try {
                val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                val n = vault.importConfig(text)
                S.get(R.string.more_import_done, n)
            } catch (e: Exception) {
                S.get(R.string.more_import_failed, e.message ?: "")
            }
        }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(stringResource(R.string.more_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        MenuRow(Icons.Rounded.SwapHoriz, stringResource(R.string.more_forwards), stringResource(R.string.more_forwards_desc)) { nav.forwards() }
        MenuRow(Icons.Rounded.Shield, stringResource(R.string.more_known_hosts), stringResource(R.string.more_known_hosts_desc)) { nav.knownHosts() }
        MenuRow(Icons.Rounded.Settings, stringResource(R.string.more_settings), stringResource(R.string.more_settings_desc)) { nav.settings() }
        SectionHeader(stringResource(R.string.more_backup))
        MenuRow(Icons.Rounded.FileUpload, stringResource(R.string.more_export), stringResource(R.string.more_export_desc)) {
            exporter.launch("termx-backup.json")
        }
        MenuRow(Icons.Rounded.FileDownload, stringResource(R.string.more_import), stringResource(R.string.more_import_desc)) {
            importer.launch(arrayOf("application/json", "text/*", "*/*"))
        }
        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Accent, modifier = Modifier.padding(8.dp))
        }
        SectionHeader(stringResource(R.string.more_about))
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ic_termx_logo), contentDescription = null, modifier = Modifier.size(52.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("TermX ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.more_about_desc), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                    Text(stringResource(R.string.more_about_logos), style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
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
    val context = LocalContext.current
    val vault = TermXApp.container.vault
    val data by vault.data.collectAsState()
    val s = data.settings
    var extraKeys by remember { mutableStateOf(s.extraKeys) }
    var scrollback by remember { mutableStateOf(s.scrollbackLines.toString()) }
    LaunchedEffect(extraKeys) { if (extraKeys != vault.current.settings.extraKeys) vault.updateSettings { it.copy(extraKeys = extraKeys) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.more_settings)) },
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.more_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionHeader(stringResource(R.string.more_language))
            AppCard(contentPadding = PaddingValues(8.dp)) {
                listOf(
                    com.kroombox.termx.i18n.Lang.ENGLISH to stringResource(R.string.more_lang_english),
                    com.kroombox.termx.i18n.Lang.INDONESIAN to stringResource(R.string.more_lang_indonesian),
                    com.kroombox.termx.i18n.Lang.SYSTEM to stringResource(R.string.more_lang_system),
                ).forEach { (v, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable {
                                if (s.language != v) {
                                    vault.updateSettings { it.copy(language = v) }
                                    com.kroombox.termx.i18n.Lang.apply(context.applicationContext, v)
                                    (context as? android.app.Activity)?.recreate()
                                }
                            }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = s.language == v,
                            onClick = {
                                if (s.language != v) {
                                    vault.updateSettings { it.copy(language = v) }
                                    com.kroombox.termx.i18n.Lang.apply(context.applicationContext, v)
                                    (context as? android.app.Activity)?.recreate()
                                }
                            },
                        )
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            SectionHeader(stringResource(R.string.more_terminal_theme))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TerminalTheme.all.forEach { t -> ThemePreview(t, selected = t.id == s.themeId) { vault.updateSettings { it.copy(themeId = t.id) } } }
            }

            SectionHeader(stringResource(R.string.more_terminal_font))
            FontSection(s)

            SectionHeader(stringResource(R.string.more_font_size))
            AppCard {
                Text(stringResource(R.string.more_font_size_value, s.fontSizeSp.toInt()), style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = s.fontSizeSp,
                    onValueChange = { v -> vault.updateSettings { it.copy(fontSizeSp = v) } },
                    valueRange = 8f..24f, steps = 15,
                )
                Text(stringResource(R.string.more_font_size_tip), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
            }

            SectionHeader(stringResource(R.string.more_keyboard))
            AppCard {
                LabeledSwitch(
                    stringResource(R.string.more_autocomplete),
                    stringResource(R.string.more_autocomplete_desc),
                    s.autocomplete,
                ) { v -> vault.updateSettings { it.copy(autocomplete = v) } }
                LabeledSwitch(
                    stringResource(R.string.more_sudo_autofill),
                    stringResource(R.string.more_sudo_autofill_desc),
                    s.sudoAutofill,
                ) { v -> vault.updateSettings { it.copy(sudoAutofill = v) } }
                var cleared by remember { mutableStateOf(false) }
                TextButton(onClick = { TermXApp.container.sessions.commandHistory.clearAll(); cleared = true }) {
                    Text(stringResource(if (cleared) R.string.more_history_cleared else R.string.more_clear_history))
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.more_keyboard_type), style = MaterialTheme.typography.bodyLarge)
                listOf(
                    "standard" to (stringResource(R.string.more_kb_standard) to stringResource(R.string.more_kb_standard_desc)),
                    "text" to (stringResource(R.string.more_kb_text) to stringResource(R.string.more_kb_text_desc)),
                    "nosuggest" to (stringResource(R.string.more_kb_nosuggest) to stringResource(R.string.more_kb_nosuggest_desc)),
                ).forEach { (id, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable { vault.updateSettings { it.copy(keyboardMode = id) } }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = s.keyboardMode == id, onClick = { vault.updateSettings { it.copy(keyboardMode = id) } })
                        Column(Modifier.weight(1f)) {
                            Text(label.first, style = MaterialTheme.typography.bodyMedium)
                            Text(label.second, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    extraKeys, { extraKeys = it }, label = { Text(stringResource(R.string.more_extra_keys)) },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.more_extra_keys_help),
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
                )
                TextButton(onClick = { extraKeys = AppSettings.DEFAULT_EXTRA_KEYS }) { Text(stringResource(R.string.more_restore_default)) }
            }

            SectionHeader(stringResource(R.string.more_sessions_background))
            AppCard {
                LabeledSwitch(stringResource(R.string.more_default_tmux), stringResource(R.string.more_default_tmux_desc), s.defaultPersistent) { v ->
                    vault.updateSettings { it.copy(defaultPersistent = v) }
                }
                LabeledSwitch(
                    stringResource(R.string.more_keep_alive),
                    stringResource(R.string.more_keep_alive_desc),
                    s.keepAliveInBackground,
                ) { v -> vault.updateSettings { it.copy(keepAliveInBackground = v) } }
                LabeledSwitch(
                    stringResource(R.string.more_keep_screen_on),
                    stringResource(R.string.more_keep_screen_on_desc),
                    s.keepScreenOn,
                ) { v -> vault.updateSettings { it.copy(keepScreenOn = v) } }
                BatteryRow()
                LabeledSwitch(stringResource(R.string.more_vibrate_bell), null, s.vibrateOnBell) { v -> vault.updateSettings { it.copy(vibrateOnBell = v) } }
                OutlinedTextField(
                    scrollback,
                    { v ->
                        scrollback = v.filter(Char::isDigit).take(6)
                        scrollback.toIntOrNull()?.let { n -> vault.updateSettings { it.copy(scrollbackLines = n.coerceIn(100, 100_000)) } }
                    },
                    label = { Text(stringResource(R.string.more_scrollback)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
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
    val vault = TermXApp.container.vault
    val data by vault.data.collectAsState()
    var deleting by remember { mutableStateOf<com.kroombox.termx.data.KnownHost?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.more_known_hosts)) },
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.more_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        if (data.knownHosts.isEmpty()) {
            Box(Modifier.padding(pad)) {
                EmptyState(Icons.Rounded.Shield, stringResource(R.string.more_kh_empty_title), stringResource(R.string.more_kh_empty_msg))
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
                                Text(stringResource(R.string.more_kh_added, timeAgo(k.addedAt)), style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
                            }
                            IconButton(onClick = { deleting = k }) { Icon(Icons.Rounded.Delete, stringResource(R.string.more_delete), tint = Palette.TextDim) }
                        }
                    }
                }
            }
        }
    }
    deleting?.let { k ->
        ConfirmDialog(
            title = stringResource(R.string.more_kh_forget_title),
            message = stringResource(R.string.more_kh_forget_msg, k.hostPort),
            confirmText = stringResource(R.string.more_kh_forget), danger = true,
            onConfirm = { vault.deleteKnownHost(k.hostPort, k.keyType) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun FontSection(s: AppSettings) {
    val context = LocalContext.current
    val vault = TermXApp.container.vault
    var message by remember { mutableStateOf<String?>(null) }
    /** 0 = berhasil, 1 = peringatan, 2 = gagal. */
    var messageKind by remember { mutableStateOf(0) }
    var deleting by remember { mutableStateOf<CustomFont?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            message = try {
                val name = SftpController.displayName(context.contentResolver, uri)
                val r = TerminalFonts.import(context, uri, name)
                vault.updateSettings { it.copy(customFonts = it.customFonts + r.font, fontId = r.font.id) }
                messageKind = if (r.warning != null) 1 else 0
                r.warning ?: S.get(R.string.more_font_added, r.font.name)
            } catch (e: Exception) {
                messageKind = 2
                S.get(R.string.more_font_import_failed, e.message ?: "")
            }
        }
    }
    AppCard(contentPadding = PaddingValues(8.dp)) {
        TerminalFonts.options(s).forEach { opt ->
            val loaded = remember(opt.id) { TerminalFonts.load(context, s, opt.id) }
            val family = remember(loaded) { FontFamily(loaded.regular) }
            val selected = opt.id == s.fontId
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Palette.SurfaceHighest else Color.Transparent)
                    .clickable { vault.updateSettings { it.copy(fontId = opt.id) } }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(opt.name, style = MaterialTheme.typography.titleSmall)
                        if (opt.ligatures && opt.custom == null) {
                            Spacer(Modifier.width(8.dp))
                            Tag(stringResource(R.string.more_tag_ligatures), Palette.Blue)
                        }
                        if (opt.custom != null) {
                            Spacer(Modifier.width(8.dp))
                            Tag(stringResource(R.string.more_tag_imported), Palette.TextDim)
                        }
                    }
                    Text(
                        "fun main() => 0O 1lI != >= -> {}",
                        fontFamily = family,
                        fontSize = 14.sp,
                        style = TextStyle(fontFeatureSettings = if (s.ligatures) null else "'liga' 0, 'calt' 0"),
                        color = if (selected) Palette.Accent else Palette.Text,
                        maxLines = 1,
                    )
                }
                if (opt.custom != null) {
                    IconButton(onClick = { deleting = opt.custom }) { Icon(Icons.Rounded.Delete, stringResource(R.string.more_delete_font), tint = Palette.TextDim) }
                } else if (selected) {
                    Icon(Icons.Rounded.Check, null, tint = Palette.Accent)
                }
            }
        }
        LabeledSwitch(
            stringResource(R.string.more_ligatures),
            stringResource(R.string.more_ligatures_desc),
            s.ligatures,
        ) { v -> vault.updateSettings { it.copy(ligatures = v) } }
        TextButton(onClick = {
            importer.launch(arrayOf("font/ttf", "font/otf", "font/*", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream", "*/*"))
        }) {
            Icon(Icons.Rounded.FontDownload, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.more_import_font))
        }
        message?.let {
            Text(
                it, style = MaterialTheme.typography.bodySmall,
                color = when (messageKind) { 2 -> Palette.Danger; 1 -> Palette.Warn; else -> Palette.Accent },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        Text(
            stringResource(R.string.more_font_license),
            style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
    deleting?.let { f ->
        ConfirmDialog(
            title = stringResource(R.string.more_delete_font_title),
            message = stringResource(R.string.more_delete_font_msg, f.name),
            confirmText = stringResource(R.string.more_delete), danger = true,
            onConfirm = {
                TerminalFonts.delete(context, f)
                vault.updateSettings { st ->
                    st.copy(
                        customFonts = st.customFonts.filterNot { it.id == f.id },
                        fontId = if (st.fontId == f.id) TerminalFonts.DEFAULT_ID else st.fontId,
                    )
                }
            },
            onDismiss = { deleting = null },
        )
    }
}

/** Status dan tombol pengecualian optimasi baterai. */
@Composable
private fun BatteryRow() {
    val context = LocalContext.current
    var ignoring by remember { mutableStateOf(BatteryOptimization.isIgnoring(context)) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) ignoring = BatteryOptimization.isIgnoring(context)
        }
        lifecycle.lifecycle.addObserver(obs)
        onDispose { lifecycle.lifecycle.removeObserver(obs) }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.more_battery), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(if (ignoring) R.string.more_battery_on else R.string.more_battery_off),
                style = MaterialTheme.typography.bodySmall,
                color = if (ignoring) Palette.Accent else Palette.Warn,
            )
        }
        if (!ignoring) TextButton(onClick = { BatteryOptimization.request(context) }) { Text(stringResource(R.string.more_allow)) }
    }
}
