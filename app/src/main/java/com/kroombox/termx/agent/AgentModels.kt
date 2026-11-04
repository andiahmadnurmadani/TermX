package com.kroombox.termx.agent

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** Agent yang terpasang di server (hasil `agentd.py detect`). */
data class AgentInfo(
    val id: String,
    val name: String,
    val bin: String,
    val version: String,
    /** "chat" = tampilan chat terstruktur; "terminal" = dibuka di terminal (tmux). */
    val kind: String,
    val models: List<String>,
    val modes: List<String>,
    val login: String,
    /** Perintah lengkap untuk login di terminal server. */
    val loginCmd: String = bin,
    /** Template untuk melanjutkan sesi di terminal; `{session}` diganti id sesi. */
    val resumeCmd: String = bin,
) {
    val isChat: Boolean get() = kind == "chat"
}

/** Ringkasan satu percakapan (meta.json di server). */
data class ChatMeta(
    val id: String,
    val agent: String,
    val cwd: String,
    val title: String,
    val model: String,
    val mode: String,
    val session: String,
    val created: Long,
    val updated: Long,
    val status: String,
    val cost: Double,
    val tokensIn: Long,
    val tokensOut: Long,
    val alive: Boolean,
)

/** Agent yang sedang berjalan di server di luar TermX (tmux, herdr, atau terminal lain). */
data class RunningAgent(
    val agent: String,
    val pid: Long,
    val cwd: String,
    val startedSec: Long,
    /** "tmux", "herdr", atau "other". */
    val where: String,
    val tty: String,
    val session: String,
    val title: String,
    val tmuxSession: String = "",
    val tmuxWindow: String = "",
    val tmuxPane: String = "",
    val tmuxAttached: Boolean = false,
    val herdrPane: String = "",
    val herdrStatus: String = "",
)

data class RunningInfo(val agents: List<RunningAgent>, val herdrBin: String)

/** Jendela kuota resmi dari provider (mis. Claude: five_hour, seven_day). */
data class LimitWindow(val key: String, val used: Double?, val resetEpochSec: Long?, val status: String?)

data class SlashCommand(val name: String, val desc: String, val hint: String)

data class Choice(val id: String, val name: String)

sealed interface ChatItem {
    val key: String
}

data class UserItem(override val key: String, val text: String, val at: Long) : ChatItem
data class TextItem(override val key: String, val text: String, val streaming: Boolean) : ChatItem

data class ToolItem(
    override val key: String,
    val name: String,
    val input: Map<String, String>,
    val todos: List<Pair<String, String>>,
    val output: String?,
    val ok: Boolean?,
) : ChatItem {
    val running: Boolean get() = ok == null
}

data class PermItem(
    override val key: String,
    val tool: String,
    val desc: String,
    val input: Map<String, String>,
    val canAlways: Boolean,
    /** null = menunggu jawaban. */
    val allowed: Boolean?,
) : ChatItem

data class NoteItem(override val key: String, val text: String, val error: Boolean) : ChatItem

data class TurnItem(
    override val key: String,
    val cost: Double,
    val tokensIn: Long,
    val tokensOut: Long,
    val ms: Long,
    val model: String?,
    val ok: Boolean,
    val error: String?,
) : ChatItem

enum class Link { CONNECTING, LIVE, RECONNECTING, FAILED }

data class ChatState(
    val items: List<ChatItem> = emptyList(),
    val working: Boolean = false,
    val thinking: Boolean = false,
    val model: String = "",
    val mode: String = "",
    val session: String = "",
    val commands: List<SlashCommand> = emptyList(),
    val limits: List<LimitWindow> = emptyList(),
    val cost: Double = 0.0,
    val tokensIn: Long = 0,
    val tokensOut: Long = 0,
    val link: Link = Link.CONNECTING,
    val linkError: String? = null,
    val gone: Boolean = false,
    val pendingPermissions: Int = 0,
    /** Model yang dilaporkan agent (opencode, pi). Kosong = pakai daftar bawaan katalog. */
    val models: List<Choice> = emptyList(),
    val modes: List<Choice> = emptyList(),
    val contextUsed: Long = 0,
    val contextSize: Long = 0,
    /** Status sementara (mis. "Reconnecting… 2/5"), hilang saat ada kemajuan. */
    val retry: String? = null,
)

// ------------------------------------------------------------------ JSON kecil

internal fun JsonObject.str(k: String): String = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content ?: ""
internal fun JsonObject.long(k: String): Long = (this[k] as? JsonPrimitive)?.longOrNull ?: (this[k] as? JsonPrimitive)?.doubleOrNull?.toLong() ?: 0L
internal fun JsonObject.dbl(k: String): Double = (this[k] as? JsonPrimitive)?.doubleOrNull ?: 0.0
internal fun JsonObject.bool(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull
internal fun JsonObject.obj(k: String): JsonObject? = this[k] as? JsonObject
internal fun JsonObject.arr(k: String): JsonArray? = this[k] as? JsonArray

internal fun JsonElement.asStringMap(): Map<String, String> = (this as? JsonObject)?.mapNotNull { (k, v) ->
    (v as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.let { k to it }
}?.toMap() ?: emptyMap()

/**
 * Menyusun event dari agentd menjadi daftar item chat. Dipanggil dari satu thread pembaca saja;
 * [snapshot] menghasilkan salinan tak berubah untuk UI.
 */
class ChatReducer {
    private val order = ArrayList<String>()
    private val items = HashMap<String, ChatItem>()
    private val texts = HashMap<String, StringBuilder>()
    var lastSeq = -1
        private set
    private var state = ChatState()
    private var noteSeq = 0

    private fun put(item: ChatItem) {
        if (!items.containsKey(item.key)) order.add(item.key)
        items[item.key] = item
    }

    fun reset() {
        order.clear(); items.clear(); texts.clear(); lastSeq = -1; state = ChatState(link = state.link)
    }

    fun setLink(link: Link, error: String? = null) {
        state = state.copy(link = link, linkError = error)
    }

    /** @return true bila tampilan berubah. */
    fun apply(o: JsonObject): Boolean {
        val t = o.str("t")
        if (t == "hb") return false
        val n = (o["n"] as? JsonPrimitive)?.longOrNull?.toInt()
        if (n != null) {
            if (n <= lastSeq) return false // sudah dimiliki (sambung ulang)
            lastSeq = n
        }
        if (t != "retry" && t != "stderr" && state.retry != null) state = state.copy(retry = null)
        when (t) {
            "retry" -> state = state.copy(retry = o.str("msg"))
            "models" -> state = state.copy(
                models = o.arr("list")?.mapNotNull { e -> (e as? JsonObject)?.let { Choice(it.str("id"), it.str("name")) } } ?: emptyList(),
                model = o.str("current").ifEmpty { state.model },
            )
            "modes" -> state = state.copy(
                modes = o.arr("list")?.mapNotNull { e -> (e as? JsonObject)?.let { Choice(it.str("id"), it.str("name")) } } ?: emptyList(),
                mode = o.str("current").ifEmpty { state.mode },
            )
            "context" -> state = state.copy(contextUsed = o.long("used"), contextSize = o.long("size"))
            "gone" -> state = state.copy(gone = true)
            "user" -> {
                order.firstOrNull { it.startsWith("local") && (items[it] as? UserItem)?.text == o.str("text") }?.let {
                    order.remove(it); items.remove(it)
                }
                put(UserItem("u$n", o.str("text"), o.long("at")))
            }
            "status" -> {
                val working = o.str("s") == "working"
                state = state.copy(working = working, thinking = if (!working) false else state.thinking)
                if (!working) closeStreaming()
            }
            "thinking" -> state = state.copy(thinking = true)
            "init" -> state = state.copy(
                session = o.str("session"),
                model = o.str("model").ifEmpty { state.model },
                mode = o.str("mode").ifEmpty { state.mode },
            )
            "model" -> state = state.copy(model = o.str("model"))
            "mode" -> state = state.copy(mode = o.str("mode"))
            "commands" -> state = state.copy(commands = o.arr("list")?.mapNotNull { e ->
                (e as? JsonObject)?.let { SlashCommand(it.str("name"), it.str("desc"), it.str("hint")) }
            } ?: emptyList())
            "text_start" -> {
                val id = o.str("id")
                texts[id] = StringBuilder()
                put(TextItem(id, "", true))
                state = state.copy(thinking = false)
            }
            "delta" -> {
                val id = o.str("id")
                val sb = texts.getOrPut(id) { StringBuilder() }
                sb.append(o.str("d"))
                put(TextItem(id, sb.toString(), true))
            }
            "text_end" -> {
                val id = o.str("id")
                val text = texts.remove(id)?.toString() ?: (items[id] as? TextItem)?.text ?: ""
                if (text.isBlank()) { items.remove(id); order.remove(id) } else put(TextItem(id, text, false))
            }
            "tool_prep" -> {
                val id = o.str("id")
                if (!items.containsKey(id)) put(ToolItem(id, o.str("name"), emptyMap(), emptyList(), null, null))
                state = state.copy(thinking = false)
            }
            "tool" -> {
                val id = o.str("id")
                val input = o["input"] as? JsonObject
                val todos = input?.arr("todos")?.mapNotNull { e -> (e as? JsonObject)?.let { it.str("t") to it.str("s") } } ?: emptyList()
                val prev = items[id] as? ToolItem
                put(ToolItem(id, o.str("name"), input?.asStringMap() ?: emptyMap(), todos, prev?.output, prev?.ok))
            }
            "tool_result" -> {
                val id = o.str("id")
                val prev = items[id] as? ToolItem ?: ToolItem(id, "?", emptyMap(), emptyList(), null, null)
                put(prev.copy(output = o.str("out"), ok = o.bool("ok") ?: true))
            }
            "perm" -> put(PermItem("p" + o.str("rid"), o.str("tool"), o.str("desc"), o["input"]?.asStringMap() ?: emptyMap(),
                o.bool("always") == true, null))
            "perm_done" -> (items["p" + o.str("rid")] as? PermItem)?.let { put(it.copy(allowed = o.bool("allow") == true)) }
            "limits" -> state = state.copy(limits = o.obj("windows")?.map { (k, v) ->
                val w = v as? JsonObject
                LimitWindow(k, (w?.get("used") as? JsonPrimitive)?.doubleOrNull, w?.long("reset")?.takeIf { it > 0 }, w?.str("status"))
            } ?: emptyList())
            "done" -> {
                closeStreaming()
                // Izin yang tak terjawab tidak berlaku lagi setelah giliran selesai.
                items.values.filterIsInstance<PermItem>().filter { it.allowed == null }.forEach { put(it.copy(allowed = false)) }
                items.values.filterIsInstance<ToolItem>().filter { it.running }.forEach { put(it.copy(ok = false, output = it.output ?: "")) }
                val cost = o.dbl("cost")
                put(TurnItem("d$n", cost, o.long("in"), o.long("out"), o.long("ms"), o.str("model").ifEmpty { null },
                    o.bool("ok") != false, o.str("error").ifEmpty { null }))
                state = state.copy(cost = state.cost + cost, tokensIn = state.tokensIn + o.long("in"), tokensOut = state.tokensOut + o.long("out"))
            }
            "note" -> put(NoteItem("n$n", o.str("msg"), false))
            "error" -> put(NoteItem("e$n", o.str("msg"), true))
            "stderr" -> {
                // stderr agent biasanya hanya peringatan; tampilkan bila jelas error.
                val msg = o.str("msg")
                if (msg.contains("error", ignoreCase = true)) put(NoteItem("s$n", msg, true))
            }
            else -> return false
        }
        return true
    }

    private fun closeStreaming() {
        for ((id, sb) in texts) put(TextItem(id, sb.toString(), false))
        texts.clear()
    }

    fun snapshot(): ChatState {
        val list = order.mapNotNull { items[it] }
        return state.copy(items = list, pendingPermissions = list.count { it is PermItem && it.allowed == null })
    }

    /** Pesan pengguna tampil seketika; diganti event "user" dari server saat tiba. */
    fun optimistic(text: String) {
        put(UserItem("local${noteSeq++}", text, System.currentTimeMillis()))
        state = state.copy(working = true)
    }
}
