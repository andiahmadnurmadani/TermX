package com.kroombox.termx.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kroombox.termx.R
import com.kroombox.termx.TermXApp
import com.kroombox.termx.agent.HermesProvider
import com.kroombox.termx.agent.HermesStatus
import com.kroombox.termx.ui.components.FlowBar
import com.kroombox.termx.ui.components.OrbitLogo
import com.kroombox.termx.ui.components.RadarLoader
import com.kroombox.termx.ui.components.WaveDots
import com.kroombox.termx.ui.theme.MonoSmall
import com.kroombox.termx.ui.theme.Palette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.sp
import com.kroombox.termx.agent.HermesCustom
import com.kroombox.termx.agent.HermesLogin
import com.kroombox.termx.agent.HermesProbe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

private val HermesGold = Color(0xFFF2C14E)

/** Kartu di beranda agent: ajakan setup bila Hermes belum diatur, ringkasan bila sudah. */
@Composable
internal fun HermesCard(status: HermesStatus?, onOpen: () -> Unit) {
    val ready = status?.configured == true
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(18.dp),
        color = if (ready) Palette.Surface else HermesGold.copy(alpha = 0.08f),
        border = if (ready) null else BorderStroke(1.dp, HermesGold.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            AgentGlyph("hermes", 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (ready) {
                    Text(
                        stringResource(R.string.hermes_card_ready, status!!.model.ifBlank { status.activeOauth }),
                        style = MaterialTheme.typography.bodyLarge, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Hub, null, tint = Palette.TextDim, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            status.providers.firstOrNull { it.id == status.provider }?.name ?: status.provider.ifBlank { status.activeOauth },
                            style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, maxLines = 1,
                        )
                    }
                } else {
                    Text(stringResource(R.string.hermes_card_title), style = MaterialTheme.typography.titleSmall, color = Palette.Text)
                    Text(stringResource(R.string.hermes_card_sub), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                }
            }
            if (status == null) WaveDots(HermesGold, 5.dp)
            else if (ready) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Edit, null, tint = Palette.TextDim, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.hermes_card_change), style = MaterialTheme.typography.labelLarge, color = Palette.TextDim)
            } else Icon(Icons.Rounded.ChevronRight, null, tint = HermesGold)
        }
    }
}

private enum class Step { PROVIDER, KEY, CUSTOM, LOGIN, MODEL, SAVING, DONE }

private val API_MODES = listOf("", "chat_completions", "codex_responses", "anthropic_messages")

/** Isian endpoint custom; sama dengan pertanyaan di alur custom `hermes model`. */
private data class CustomForm(
    val url: String = "", val key: String = "", val mode: String = "", val name: String = "", val ctx: String = "",
    val hasKey: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HermesSetupSheet(hostId: String, onDismiss: () -> Unit, onSaved: () -> Unit, onTerminal: () -> Unit) {
    val mgr = TermXApp.container.agents
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var status by remember { mutableStateOf<HermesStatus?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableStateOf(Step.PROVIDER) }
    var provider by remember { mutableStateOf<HermesProvider?>(null) }
    var custom by remember { mutableStateOf<CustomForm?>(null) }
    var probe by remember { mutableStateOf<HermesProbe?>(null) }
    var probing by remember { mutableStateOf(false) }
    var probeError by remember { mutableStateOf<String?>(null) }
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var models by remember { mutableStateOf<List<String>?>(null) }
    var live by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var oauthAsk by remember { mutableStateOf<HermesProvider?>(null) }

    LaunchedEffect(Unit) {
        try {
            val s = mgr.hermesStatus(hostId)
            status = s
            if (s.model.isNotBlank()) model = s.model
        } catch (e: CancellationException) { throw e } catch (e: Exception) { loadError = e.message ?: e.toString() }
    }
    LaunchedEffect(provider?.id, step) {
        val p = provider ?: return@LaunchedEffect
        if (step == Step.MODEL && models == null && custom == null) {
            val r = runCatching { mgr.hermesModels(hostId, p.id, key.trim()) }.getOrDefault(emptyList<String>() to false)
            models = r.first; live = r.second
        }
    }

    fun customUrl(): String {
        val c = custom ?: return ""
        val pr = probe
        return if (pr != null && pr.usedFallback && pr.resolvedBaseUrl.isNotBlank()) pr.resolvedBaseUrl else c.url.trim().trimEnd('/')
    }

    /** Cek /models endpoint custom; bila [thenNext], lanjut ke pilihan model. */
    fun runProbe(thenNext: Boolean) {
        val c = custom ?: return
        probing = true; probeError = null
        scope.launch {
            runCatching { mgr.hermesProbe(hostId, c.url.trim(), c.key.trim()) }
                .onSuccess { pr ->
                    probe = pr; models = pr.models; live = pr.ok
                    if (c.name.isBlank() && pr.defaultName.isNotBlank()) custom = custom?.copy(name = pr.defaultName)
                    if (thenNext) step = Step.MODEL
                }
                .onFailure { e -> probeError = e.message ?: e.toString() }
            probing = false
        }
    }

    fun pickProvider(p: HermesProvider, s: HermesStatus) {
        custom = null; probe = null
        if (provider?.id != p.id) { models = null; if (p.id != s.provider) model = "" }
        provider = p; key = ""
        step = when {
            p.usesKey -> if (p.env.isBlank()) Step.MODEL else Step.KEY
            p.canLogin -> if (p.loggedIn) Step.MODEL else Step.LOGIN
            else -> { oauthAsk = p; Step.PROVIDER }
        }
    }

    fun pickCustom(c: HermesCustom?, s: HermesStatus) {
        provider = HermesProvider("custom", c?.name?.ifBlank { null } ?: "Custom", "", "custom", c?.hasKey == true)
        custom = if (c == null) CustomForm() else CustomForm(c.baseUrl, "", c.apiMode, c.name, "", c.hasKey)
        probe = null; models = null; key = ""
        model = c?.model ?: if (s.provider == "custom") s.model else ""
        step = Step.CUSTOM
    }

    fun save() {
        val p = provider ?: return
        step = Step.SAVING
        saveError = null
        scope.launch {
            val c = custom
            runCatching {
                if (c != null) mgr.hermesApplyCustom(
                    hostId, customUrl(), c.key.trim(), c.mode, model.trim(),
                    c.name.trim().ifBlank { probe?.defaultName ?: "" },
                    c.ctx.trim().lowercase().replace(",", "").replace("k", "000").toIntOrNull()?.takeIf { it > 0 },
                ) else mgr.hermesApply(hostId, p.id, model.trim(), key.trim())
            }
                .onSuccess { key = ""; custom = custom?.copy(key = ""); step = Step.DONE; onSaved() }
                .onFailure { e -> saveError = e.message ?: e.toString(); step = Step.MODEL }
        }
    }

    // Sisa gulir/fling dari daftar provider/model ditahan di sini agar tidak menarik sheet:
    // geser di daftar menggulir daftarnya, bukan sheet-nya.
    val keepScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = available
            override suspend fun onPostFling(consumed: Velocity, available: Velocity) = available
            override suspend fun onPreFling(available: Velocity) = Velocity.Zero
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Palette.Bg, dragHandle = null) {
        Column(Modifier.nestedScroll(keepScroll).navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 16.dp)) {
            // kepala: logo + judul + tombol tutup
            Row(verticalAlignment = Alignment.CenterVertically) {
                AgentGlyph("hermes", 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.hermes_title), style = MaterialTheme.typography.titleLarge, color = Palette.Text)
                    status?.takeIf { it.configured }?.let {
                        Text(
                            "${stringResource(R.string.hermes_current)}: ${it.model.ifBlank { it.activeOauth }}",
                            style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.hermes_close), tint = Palette.TextDim)
                }
            }
            if (step != Step.DONE) {
                Spacer(Modifier.height(16.dp))
                Stepper(
                    when (step) { Step.PROVIDER -> 0; Step.KEY, Step.CUSTOM, Step.LOGIN -> 1; else -> 2 },
                    listOf(
                        Icons.Rounded.Hub to stringResource(R.string.hermes_step_provider),
                        Icons.Rounded.Key to stringResource(R.string.hermes_step_access),
                        Icons.Rounded.AutoAwesome to stringResource(R.string.hermes_step_model),
                    ),
                )
            }
            Spacer(Modifier.height(16.dp))

            val s = status
            when {
                loadError != null -> ErrorBlock(loadError!!)
                s == null -> RadarLoader(
                    stringResource(R.string.hermes_title), stringResource(R.string.agent_detect_sub, "Hermes"),
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    logos = listOf(OrbitLogo(R.drawable.ic_agent_hermes, HermesGold)), center = Icons.Rounded.Hub, color = HermesGold, size = 150.dp,
                )
                else -> AnimatedContent(
                    step,
                    transitionSpec = {
                        val fwd = targetState.ordinal > initialState.ordinal
                        (slideInHorizontally(tween(260)) { if (fwd) it / 3 else -it / 3 } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(220)) { if (fwd) -it / 3 else it / 3 } + fadeOut(tween(160)))
                    },
                    label = "step",
                ) { st ->
                    when (st) {
                        Step.PROVIDER -> ProviderStep(s, onPick = { pickProvider(it, s) }, onCustom = { pickCustom(it, s) })
                        Step.KEY -> KeyStep(
                            provider!!, key, { key = it }, onBack = { step = Step.PROVIDER }, onNext = { models = null; step = Step.MODEL },
                            onSignIn = if (provider!!.canLogin) ({ step = Step.LOGIN }) else null,
                        )
                        Step.CUSTOM -> CustomStep(
                            custom ?: CustomForm(), { nf -> if (nf.url != custom?.url || nf.key != custom?.key) probe = null; custom = nf }, probe, probing, probeError,
                            onProbe = { runProbe(false) }, onBack = { step = Step.PROVIDER },
                            onNext = { if (probe == null) runProbe(true) else step = Step.MODEL },
                        )
                        Step.LOGIN -> LoginStep(
                            hostId, provider!!,
                            onBack = { step = if (provider!!.usesKey) Step.KEY else Step.PROVIDER },
                            onTerminal = { onDismiss(); onTerminal() },
                            onSignedIn = { ns ->
                                status = ns
                                provider = ns.providers.firstOrNull { it.id == provider!!.id } ?: provider
                                models = null; key = ""; step = Step.MODEL
                            },
                        )
                        Step.MODEL, Step.SAVING -> ModelStep(
                            if (custom != null) (custom!!.name.ifBlank { probe?.defaultName ?: "" }.ifBlank { customUrl() }) else provider!!.name,
                            models, live, model, { model = it }, saving = st == Step.SAVING, error = saveError,
                            onBack = {
                                val p = provider!!
                                step = when {
                                    custom != null -> Step.CUSTOM
                                    p.usesKey && p.env.isNotBlank() -> Step.KEY
                                    else -> Step.PROVIDER
                                }
                            },
                            onSave = { save() },
                        )
                        Step.DONE -> DoneStep(model, if (custom != null) custom!!.name.ifBlank { customUrl() } else provider?.name ?: "", onDismiss)
                    }
                }
            }
        }
    }

    oauthAsk?.let { p ->
        AlertDialog(
            onDismissRequest = { oauthAsk = null },
            icon = { Icon(Icons.AutoMirrored.Rounded.Login, null, tint = HermesGold) },
            title = { Text(stringResource(R.string.hermes_login_title)) },
            text = { Text(stringResource(R.string.hermes_login_msg, p.name)) },
            confirmButton = {
                Button(onClick = { oauthAsk = null; onDismiss(); onTerminal() }) {
                    Icon(Icons.Rounded.Terminal, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.hermes_open_terminal))
                }
            },
            dismissButton = { TextButton(onClick = { oauthAsk = null }) { Text(stringResource(R.string.nav_cancel)) } },
        )
    }
}

/** Tiga langkah dengan ikon; yang sudah lewat jadi tanda centang, garis penghubung ikut terisi. */
@Composable
private fun Stepper(current: Int, steps: List<Pair<ImageVector, String>>) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { i, (icon, label) ->
            val done = i < current
            val on = i == current
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape)
                        .background(if (done) Palette.Accent else if (on) Palette.Accent.copy(alpha = 0.16f) else Palette.SurfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (done) Icons.Rounded.Check else icon, null,
                        tint = if (done) Palette.Bg else if (on) Palette.Accent else Palette.TextDim, modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = if (on) Palette.Text else Palette.TextDim)
            }
            if (i < steps.lastIndex) {
                Box(Modifier.weight(1f).padding(horizontal = 6.dp).padding(bottom = 18.dp).height(2.dp).clip(CircleShape).background(Palette.SurfaceHigh)) {
                    val next by animateFloatAsState(if (i < current) 1f else 0f, tween(400), label = "l$i")
                    Box(Modifier.fillMaxWidth(next).height(2.dp).background(Palette.Accent))
                }
            }
        }
    }
}

@Composable
private fun ProviderStep(s: HermesStatus, onPick: (HermesProvider) -> Unit, onCustom: (HermesCustom?) -> Unit) {
    var q by remember { mutableStateOf("") }
    val list = remember(s, q) {
        val all = s.providers.sortedWith(
            compareByDescending<HermesProvider> { it.id == s.provider }.thenByDescending { it.hasKey || it.loggedIn },
        )
        if (q.isBlank()) all else all.filter { it.name.contains(q, true) || it.id.contains(q, true) }
    }
    val customs = remember(s, q) { s.custom.filter { q.isBlank() || it.name.contains(q, true) || it.baseUrl.contains(q, true) } }
    val showNew = q.isBlank() || "custom".contains(q, true) || stringResource(R.string.hermes_custom).contains(q, true)
    Column {
        Text(stringResource(R.string.hermes_pick_provider), style = MaterialTheme.typography.titleMedium, color = Palette.Text)
        Spacer(Modifier.height(10.dp))
        SearchField(q, { q = it }, stringResource(R.string.hermes_search_provider))
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.heightIn(max = 420.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
            if (showNew) item(key = "custom:new") {
                ProviderRow(Icons.Rounded.Link, HermesGold, stringResource(R.string.hermes_custom), stringResource(R.string.hermes_custom_sub), false, {
                    if (s.provider == "custom" && s.custom.none { it.baseUrl.trimEnd('/') == s.baseUrl.trimEnd('/') })
                        Badge(stringResource(R.string.hermes_current), Palette.Accent, Icons.Rounded.CheckCircle)
                }) { onCustom(null) }
            }
            items(customs, key = { "custom:" + it.baseUrl }) { c ->
                val cur = s.provider == "custom" && s.baseUrl.trimEnd('/') == c.baseUrl.trimEnd('/')
                ProviderRow(Icons.Rounded.Dns, Palette.Blue, c.name.ifBlank { c.baseUrl }, c.baseUrl, true, {
                    if (cur) Badge(stringResource(R.string.hermes_current), Palette.Accent, Icons.Rounded.CheckCircle)
                    else Badge(stringResource(R.string.hermes_saved_custom), Palette.Blue, Icons.Rounded.Dns)
                }) { onCustom(c) }
            }
            items(list, key = { it.id }) { p ->
                val sub = when {
                    p.usesKey -> p.env.ifBlank { p.id }
                    p.canLogin -> p.id
                    else -> stringResource(R.string.hermes_needs_login)
                }
                ProviderRow(
                    if (p.usesKey) Icons.Rounded.Key else Icons.AutoMirrored.Rounded.Login,
                    if (p.hasKey || p.loggedIn) Palette.Accent else Palette.TextDim, p.name, sub, p.usesKey || p.canLogin,
                    {
                        when {
                            p.id == s.provider -> Badge(stringResource(R.string.hermes_current), Palette.Accent, Icons.Rounded.CheckCircle)
                            p.loggedIn -> Badge(stringResource(R.string.hermes_logged_in), Palette.Blue, Icons.Rounded.Lock)
                            p.hasKey -> Badge(stringResource(R.string.hermes_key_saved), Palette.Blue, Icons.Rounded.Lock)
                        }
                    },
                ) { onPick(p) }
            }
        }
    }
}

@Composable
private fun ProviderRow(
    icon: ImageVector, tint: Color, title: String, sub: String, mono: Boolean,
    badge: @Composable () -> Unit, onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Palette.SurfaceHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                sub, style = if (mono) MonoSmall else MaterialTheme.typography.bodySmall, color = Palette.TextDim,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        badge()
        Icon(Icons.Rounded.ChevronRight, null, tint = Palette.TextDim, modifier = Modifier.padding(start = 4.dp).size(20.dp))
    }
}

@Composable
private fun KeyStep(
    p: HermesProvider, key: String, onKey: (String) -> Unit, onBack: () -> Unit, onNext: () -> Unit, onSignIn: (() -> Unit)?,
) {
    var show by remember { mutableStateOf(false) }
    Column {
        Text(stringResource(R.string.hermes_key_title, p.name), style = MaterialTheme.typography.titleMedium, color = Palette.Text)
        Spacer(Modifier.height(12.dp))
        SecretField(key, onKey, stringResource(R.string.hermes_key_label), show) { show = !show }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Lock, null, tint = Palette.TextDim, modifier = Modifier.padding(top = 2.dp).size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.hermes_key_hint, p.env), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
        }
        if (p.hasKey || p.loggedIn) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = Palette.Accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(if (p.hasKey) R.string.hermes_key_keep else R.string.hermes_logged_in),
                    style = MaterialTheme.typography.bodySmall, color = Palette.Accent,
                )
            }
        }
        if (onSignIn != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onSignIn, modifier = Modifier.fillMaxWidth().height(46.dp), border = BorderStroke(1.dp, HermesGold.copy(alpha = 0.5f))) {
                Icon(Icons.AutoMirrored.Rounded.Login, null, tint = HermesGold, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.hermes_or_sign_in), color = HermesGold)
            }
        }
        Spacer(Modifier.height(20.dp))
        NavRow(onBack, onNext, enabled = key.isNotBlank() || p.hasKey || p.loggedIn, nextLabel = stringResource(R.string.hermes_next))
    }
}

@Composable
private fun SecretField(value: String, onValue: (String) -> Unit, label: String, show: Boolean, onToggle: () -> Unit) {
    OutlinedTextField(
        value, onValue, singleLine = true, modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Rounded.Key, null) },
        trailingIcon = { IconButton(onClick = onToggle) { Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null) } },
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        shape = RoundedCornerShape(14.dp),
    )
}

private fun looksLocalWithoutV1(url: String): Boolean {
    val u = url.trim().trimEnd('/').lowercase()
    return u.isNotEmpty() && !u.endsWith("/v1") &&
        listOf("localhost", "127.0.0.1", "0.0.0.0", ":11434", ":8080", ":5000").any { it in u }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomStep(
    f: CustomForm, onForm: (CustomForm) -> Unit, probe: HermesProbe?, probing: Boolean, probeError: String?,
    onProbe: () -> Unit, onBack: () -> Unit, onNext: () -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    val validUrl = f.url.trim().let { it.startsWith("http://") || it.startsWith("https://") }
    Column {
    Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.hermes_custom_title), style = MaterialTheme.typography.titleMedium, color = Palette.Text)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            f.url, { onForm(f.copy(url = it.trim())) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.hermes_custom_url)) },
            placeholder = { Text("https://api.example.com/v1") },
            leadingIcon = { Icon(Icons.Rounded.Link, null) },
            textStyle = MonoSmall.copy(color = Palette.Text),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
            shape = RoundedCornerShape(14.dp),
        )
        if (looksLocalWithoutV1(f.url)) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.hermes_local_v1), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, modifier = Modifier.weight(1f))
                TextButton(onClick = { onForm(f.copy(url = f.url.trim().trimEnd('/') + "/v1")) }) { Text(stringResource(R.string.hermes_add_v1)) }
            }
        }
        Spacer(Modifier.height(10.dp))
        SecretField(f.key, { onForm(f.copy(key = it)) }, stringResource(R.string.hermes_custom_key), show) { show = !show }
        if (f.hasKey && f.key.isBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.hermes_key_keep), style = MaterialTheme.typography.bodySmall, color = Palette.Accent)
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.hermes_custom_mode), style = MaterialTheme.typography.labelLarge, color = Palette.TextDim)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            API_MODES.forEach { m ->
                val label = when (m) {
                    "chat_completions" -> R.string.hermes_mode_chat
                    "codex_responses" -> R.string.hermes_mode_responses
                    "anthropic_messages" -> R.string.hermes_mode_anthropic
                    else -> R.string.hermes_mode_auto
                }
                FilterChip(
                    selected = f.mode == m, onClick = { onForm(f.copy(mode = m)) }, label = { Text(stringResource(label)) },
                    leadingIcon = if (f.mode == m) ({ Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }) else null,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            f.name, { onForm(f.copy(name = it)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.hermes_custom_name)) },
            placeholder = { Text(probe?.defaultName ?: "") },
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            f.ctx, { v -> onForm(f.copy(ctx = v.filter { it.isDigit() || it == 'k' || it == 'K' || it == ',' })) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.hermes_custom_ctx)) },
            placeholder = { Text(stringResource(R.string.hermes_custom_ctx_hint)) },
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.height(12.dp))
        // hasil cek endpoint, kalimatnya sama dengan yang ditampilkan `hermes model`
        when {
            probing -> Row(verticalAlignment = Alignment.CenterVertically) {
                WaveDots(HermesGold, 5.dp); Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.hermes_model_loading, f.url), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            probeError != null -> ErrorBlock(probeError)
            probe != null -> {
                val (icon, color, msg) = when {
                    probe.usedFallback && probe.resolvedBaseUrl.isNotBlank() ->
                        Triple(Icons.Rounded.Info, Palette.Blue, stringResource(R.string.hermes_probe_fallback, probe.resolvedBaseUrl))
                    probe.ok -> Triple(Icons.Rounded.CheckCircle, Palette.Accent, stringResource(R.string.hermes_probe_ok, probe.probedUrl, probe.models.size))
                    else -> Triple(Icons.Rounded.ErrorOutline, HermesGold, stringResource(R.string.hermes_probe_fail, probe.probedUrl))
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.10f)).padding(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = Palette.Text)
                }
                if (!probe.ok && probe.suggestedBaseUrl.isNotBlank() && probe.suggestedBaseUrl != f.url.trim()) {
                    TextButton(onClick = { onForm(f.copy(url = probe.suggestedBaseUrl)) }) {
                        Text(stringResource(R.string.hermes_probe_try, probe.suggestedBaseUrl), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onProbe, enabled = validUrl && !probing, modifier = Modifier.fillMaxWidth().height(46.dp)) {
            Icon(Icons.Rounded.Search, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.hermes_detect_models))
        }
        Spacer(Modifier.height(14.dp))
        NavRow(onBack, onNext, enabled = validUrl && !probing, nextLabel = stringResource(R.string.hermes_next))
    }
}

/** Baris keluaran yang layak ditampilkan: tanpa garis kotak dan tanpa URL (URL jadi tombol). */
private fun loginLines(l: HermesLogin): List<String> =
    l.lines.map { it.trim().trim('│', '|').trim() }
        .filter { line -> line.isNotBlank() && line.any { it.isLetterOrDigit() } && !line.startsWith("http") }
        .takeLast(8)

@Composable
private fun LoginStep(
    hostId: String, p: HermesProvider, onBack: () -> Unit, onTerminal: () -> Unit, onSignedIn: (HermesStatus) -> Unit,
) {
    val mgr = TermXApp.container.agents
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val clip = LocalClipboardManager.current
    var attempt by remember { mutableStateOf(0) }
    var screen by remember { mutableStateOf<HermesLogin?>(null) }
    var failed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var answer by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf("") }
    val running = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    LaunchedEffect(attempt) {
        screen = null; failed = false; error = null
        try {
            mgr.hermesLoginStart(hostId, p.id)
            running.set(true)
            while (true) {
                delay(1500)
                val l = mgr.hermesLoginStatus(hostId)
                screen = l
                if (!l.running) break
            }
            running.set(false)
            // proses selesai: cek ulang status Hermes, bukan kode keluar (yang tidak selalu mencerminkan hasil)
            val ns = mgr.hermesStatus(hostId)
            if (ns.providers.firstOrNull { it.id == p.id }?.loggedIn == true) {
                runCatching { mgr.hermesLoginCancel(hostId) }
                onSignedIn(ns)
            } else failed = true
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            error = e.message ?: e.toString()
        }
    }
    // keluar dari langkah ini saat login masih berjalan: hentikan proses di server
    DisposableEffect(Unit) {
        onDispose {
            if (running.get()) CoroutineScope(Dispatchers.IO).launch { runCatching { mgr.hermesLoginCancel(hostId) } }
        }
    }

    Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.hermes_login_step_title, p.name), style = MaterialTheme.typography.titleMedium, color = Palette.Text)
        Spacer(Modifier.height(12.dp))
        val l = screen
        when {
            error != null -> ErrorBlock(error!!)
            l == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                WaveDots(HermesGold, 5.dp); Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.hermes_login_starting), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
            }
            else -> {
                l.urls.firstOrNull()?.let { url ->
                    Button(
                        onClick = { runCatching { uri.openUri(url) } }, modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = HermesGold, contentColor = Palette.Bg),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.hermes_login_open), fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(url, style = MonoSmall, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = { clip.setText(AnnotatedString(url)); copied = url }) {
                            Icon(if (copied == url) Icons.Rounded.Check else Icons.Rounded.ContentCopy, stringResource(R.string.hermes_copied),
                                tint = if (copied == url) Palette.Accent else Palette.TextDim, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                l.codes.forEach { code ->
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Surface)
                            .clickable { clip.setText(AnnotatedString(code)); copied = code }.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.hermes_login_code), style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
                            Text(code, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = MonoSmall.fontFamily, letterSpacing = 3.sp),
                                color = Palette.Text, fontWeight = FontWeight.SemiBold)
                        }
                        Icon(if (copied == code) Icons.Rounded.Check else Icons.Rounded.ContentCopy, null,
                            tint = if (copied == code) Palette.Accent else Palette.TextDim)
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (l.running) Row(verticalAlignment = Alignment.CenterVertically) {
                    WaveDots(HermesGold, 5.dp); Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.hermes_login_wait), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                }
                if (failed) ErrorBlock(stringResource(R.string.hermes_login_failed))
                val shown = loginLines(l)
                if (shown.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.hermes_login_output), style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
                    Spacer(Modifier.height(4.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Surface).padding(10.dp)) {
                        shown.forEach { Text(it, style = MonoSmall, color = Palette.TextDim) }
                    }
                }
                if (l.running) {
                    Spacer(Modifier.height(10.dp))
                    var show by remember { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            SecretField(answer, { answer = it }, stringResource(R.string.hermes_login_paste), show) { show = !show }
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val t = answer.trim(); answer = ""
                                scope.launch { runCatching { mgr.hermesLoginInput(hostId, t) }.onFailure { e -> error = e.message } }
                            },
                            enabled = answer.isNotBlank(), modifier = Modifier.height(54.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.Bg),
                        ) { Icon(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.hermes_login_send)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (failed || error != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.height(48.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.hermes_back))
                }
                Button(
                    onClick = { attempt++ }, modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.Bg),
                ) { Text(stringResource(R.string.hermes_login_retry), fontWeight = FontWeight.SemiBold) }
            }
        } else OutlinedButton(onClick = onBack, modifier = Modifier.height(48.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.hermes_back))
        }
        TextButton(onClick = onTerminal, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(Icons.Rounded.Terminal, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.hermes_login_terminal))
        }
    }
}

@Composable
private fun ModelStep(
    source: String, models: List<String>?, live: Boolean, model: String, onModel: (String) -> Unit,
    saving: Boolean, error: String?, onBack: () -> Unit, onSave: () -> Unit,
) {
    var q by remember { mutableStateOf("") }
    val list = remember(models, q) { models.orEmpty().filter { q.isBlank() || it.contains(q, true) } }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.hermes_model_title), style = MaterialTheme.typography.titleMedium, color = Palette.Text, modifier = Modifier.weight(1f))
            if (!models.isNullOrEmpty()) {
                if (live) { Badge(stringResource(R.string.hermes_model_live), Palette.Accent, Icons.Rounded.Bolt); Spacer(Modifier.width(6.dp)) }
                Text(stringResource(R.string.hermes_model_count, models.size), style = MaterialTheme.typography.labelMedium, color = Palette.TextDim)
            }
        }
        Spacer(Modifier.height(10.dp))
        when {
            models == null -> Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                WaveDots(HermesGold)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.hermes_model_loading, source), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
            }
            models.isEmpty() -> Text(stringResource(R.string.hermes_model_none), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
            else -> {
                SearchField(q, { q = it }, stringResource(R.string.hermes_model_search))
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(list, key = { it }) { m ->
                        val on = m == model
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (on) Palette.Accent.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable { onModel(m) }.padding(horizontal = 10.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (on) Icons.Rounded.CheckCircle else Icons.Rounded.AutoAwesome, null,
                                tint = if (on) Palette.Accent else Palette.TextDim, modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(m, style = MonoSmall, color = if (on) Palette.Text else Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            model, onModel, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.hermes_model_custom)) },
            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
            textStyle = MonoSmall.copy(color = Palette.Text),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            shape = RoundedCornerShape(14.dp),
        )
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            ErrorBlock(stringResource(R.string.hermes_failed, error))
        }
        Spacer(Modifier.height(18.dp))
        if (saving) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.hermes_saving), style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                Spacer(Modifier.height(10.dp))
                FlowBar(Modifier.fillMaxWidth(0.6f), HermesGold)
            }
        } else NavRow(onBack, onSave, enabled = model.isNotBlank(), nextLabel = stringResource(R.string.hermes_save), finish = true)
    }
}

@Composable
private fun DoneStep(model: String, provider: String, onDone: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val pop by animateFloatAsState(if (shown) 1f else 0.4f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow), label = "pop")
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(84.dp).graphicsLayer { scaleX = pop; scaleY = pop; alpha = pop.coerceIn(0f, 1f) }
                .clip(CircleShape).background(Palette.Accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_agent_hermes), null, tint = HermesGold, modifier = Modifier.size(40.dp))
            Box(Modifier.align(Alignment.BottomEnd).size(28.dp).clip(CircleShape).background(Palette.Accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, tint = Palette.Bg, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.hermes_done_title), style = MaterialTheme.typography.titleLarge, color = Palette.Text)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.hermes_done_msg, model, provider), style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextDim, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.Bg)) {
            Text(stringResource(R.string.hermes_done), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun NavRow(onBack: () -> Unit, onNext: () -> Unit, enabled: Boolean, nextLabel: String, finish: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onBack, modifier = Modifier.height(48.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.hermes_back))
        }
        Button(
            onClick = onNext, enabled = enabled, modifier = Modifier.weight(1f).height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.Bg),
        ) {
            Text(nextLabel, fontWeight = FontWeight.SemiBold); Spacer(Modifier.width(6.dp))
            Icon(if (finish) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SearchField(q: String, onQ: (String) -> Unit, hint: String) {
    OutlinedTextField(
        q, onQ, singleLine = true, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(hint) }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
private fun Badge(text: String, color: Color, icon: ImageVector) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(3.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

@Composable
private fun ErrorBlock(msg: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Danger.copy(alpha = 0.10f)).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = Palette.Danger, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(msg, style = MaterialTheme.typography.bodySmall, color = Palette.Text)
    }
}
