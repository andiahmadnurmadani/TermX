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

private enum class Step { PROVIDER, KEY, MODEL, SAVING, DONE }

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
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var models by remember { mutableStateOf<List<String>?>(null) }
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
        if (step == Step.MODEL && models == null) {
            models = runCatching { mgr.hermesModels(hostId, p.id) }.getOrDefault(emptyList())
        }
    }

    fun save() {
        val p = provider ?: return
        step = Step.SAVING
        saveError = null
        scope.launch {
            runCatching { mgr.hermesApply(hostId, p.id, model.trim(), key.trim()) }
                .onSuccess { key = ""; step = Step.DONE; onSaved() }
                .onFailure { e -> saveError = e.message ?: e.toString(); step = Step.MODEL }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Palette.Bg, dragHandle = null) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 16.dp)) {
            // kepala: logo + judul + langkah
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
            }
            if (step != Step.DONE) {
                Spacer(Modifier.height(16.dp))
                Stepper(
                    when (step) { Step.PROVIDER -> 0; Step.KEY -> 1; else -> 2 },
                    listOf(
                        Icons.Rounded.Hub to stringResource(R.string.hermes_step_provider),
                        Icons.Rounded.Key to stringResource(R.string.hermes_step_key),
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
                        Step.PROVIDER -> ProviderStep(s) { p ->
                            if (!p.usesKey) oauthAsk = p
                            else {
                                if (provider?.id != p.id) { models = null; if (p.id != s.provider) model = "" }
                                provider = p; key = ""
                                step = if (p.env.isBlank()) Step.MODEL else Step.KEY
                            }
                        }
                        Step.KEY -> KeyStep(provider!!, key, { key = it }, onBack = { step = Step.PROVIDER }, onNext = { step = Step.MODEL })
                        Step.MODEL, Step.SAVING -> ModelStep(
                            provider!!, models, model, { model = it }, saving = st == Step.SAVING, error = saveError,
                            onBack = { step = if (provider!!.env.isBlank()) Step.PROVIDER else Step.KEY }, onSave = { save() },
                        )
                        Step.DONE -> DoneStep(model, provider?.name ?: "", onDismiss)
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
private fun ProviderStep(s: HermesStatus, onPick: (HermesProvider) -> Unit) {
    var q by remember { mutableStateOf("") }
    val list = remember(s, q) {
        val all = s.providers.sortedWith(compareByDescending<HermesProvider> { it.id == s.provider }.thenByDescending { it.hasKey })
        if (q.isBlank()) all else all.filter { it.name.contains(q, true) || it.id.contains(q, true) }
    }
    Column {
        Text(stringResource(R.string.hermes_pick_provider), style = MaterialTheme.typography.titleMedium, color = Palette.Text)
        Spacer(Modifier.height(10.dp))
        SearchField(q, { q = it }, stringResource(R.string.hermes_search_provider))
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.heightIn(max = 420.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
            items(list, key = { it.id }) { p ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onPick(p) }.padding(horizontal = 6.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Palette.SurfaceHigh), contentAlignment = Alignment.Center) {
                        Icon(
                            if (p.usesKey) Icons.Rounded.Key else Icons.AutoMirrored.Rounded.Login, null,
                            tint = if (p.hasKey) Palette.Accent else Palette.TextDim, modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.bodyLarge, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (p.usesKey) p.env.ifBlank { p.id } else stringResource(R.string.hermes_needs_login),
                            style = if (p.usesKey) MonoSmall else MaterialTheme.typography.bodySmall, color = Palette.TextDim, maxLines = 1,
                        )
                    }
                    if (p.id == s.provider) Badge(stringResource(R.string.hermes_current), Palette.Accent, Icons.Rounded.CheckCircle)
                    else if (p.hasKey) Badge(stringResource(R.string.hermes_key_saved), Palette.Blue, Icons.Rounded.Lock)
                    Icon(Icons.Rounded.ChevronRight, null, tint = Palette.TextDim, modifier = Modifier.padding(start = 4.dp).size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun KeyStep(p: HermesProvider, key: String, onKey: (String) -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    var show by remember { mutableStateOf(false) }
    Column {
        Text(stringResource(R.string.hermes_key_title, p.name), style = MaterialTheme.typography.titleMedium, color = Palette.Text)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            key, onKey, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.hermes_key_label)) },
            leadingIcon = { Icon(Icons.Rounded.Key, null) },
            trailingIcon = {
                IconButton(onClick = { show = !show }) { Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null) }
            },
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Lock, null, tint = Palette.TextDim, modifier = Modifier.padding(top = 2.dp).size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.hermes_key_hint, p.env), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
        }
        if (p.hasKey) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = Palette.Accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.hermes_key_keep), style = MaterialTheme.typography.bodySmall, color = Palette.Accent)
            }
        }
        Spacer(Modifier.height(20.dp))
        NavRow(onBack, onNext, enabled = key.isNotBlank() || p.hasKey, nextLabel = stringResource(R.string.hermes_next))
    }
}

@Composable
private fun ModelStep(
    p: HermesProvider, models: List<String>?, model: String, onModel: (String) -> Unit,
    saving: Boolean, error: String?, onBack: () -> Unit, onSave: () -> Unit,
) {
    var q by remember { mutableStateOf("") }
    val list = remember(models, q) { models.orEmpty().filter { q.isBlank() || it.contains(q, true) } }
    Column {
        Text(stringResource(R.string.hermes_model_title), style = MaterialTheme.typography.titleMedium, color = Palette.Text)
        Spacer(Modifier.height(10.dp))
        when {
            models == null -> Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                WaveDots(HermesGold)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.hermes_model_loading, p.name), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
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
