package com.kroombox.termx.agent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.kroombox.termx.MainActivity
import com.kroombox.termx.R
import com.kroombox.termx.i18n.S
import com.kroombox.termx.ssh.SessionManager
import com.kroombox.termx.ssh.SshConnection
import com.kroombox.termx.ssh.SshException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import net.schmizz.sshj.connection.channel.direct.Session
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

internal fun shq(s: String): String = "'" + s.replace("'", "'\\''") + "'"

/**
 * Remote AI agent di server lewat agentd (lihat assets/agentd.py). TermX tidak pernah memegang
 * login / API key agent: semuanya tetap di server, dipakai oleh CLI resmi masing-masing agent.
 */
class AgentManager(private val context: Context, private val sessions: SessionManager) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val installed = ConcurrentHashMap<String, Boolean>()
    private val prefs = context.getSharedPreferences("agents", Context.MODE_PRIVATE)

    private val script: ByteArray by lazy { context.assets.open("agentd.py").use { it.readBytes() } }
    private val scriptVersion: Int by lazy {
        Regex("""(?m)^VERSION = (\d+)""").find(String(script))?.groupValues?.get(1)?.toInt() ?: 0
    }

    private val _chats = MutableStateFlow<Map<String, AgentChat>>(emptyMap())
    val chats: StateFlow<Map<String, AgentChat>> = _chats.asStateFlow()

    /** Chat yang sedang tampil di layar (notifikasi tidak perlu untuk chat ini). */
    @Volatile var visibleChat: String? = null

    val activeCount: Int get() = _chats.value.size

    // ------------------------------------------------------------ perintah agentd

    private fun conn(hostId: String): SshConnection = sessions.connectionFor(hostId)

    private fun ensureInstalled(c: SshConnection, hostId: String) {
        if (installed[hostId] == true) return
        val v = c.exec("$PY $REMOTE version 2>/dev/null; command -v python3 >/dev/null 2>&1 || echo NOPY", timeoutSec = 15)
        if (v.stdout.contains("NOPY")) throw SshException(S.get(R.string.agent_no_python), fatal = true)
        if (v.stdout.trim().toIntOrNull() != scriptVersion) {
            val r = c.exec(
                "mkdir -p ~/.termx && chmod 700 ~/.termx && cat > ~/.termx/agentd.py.tmp && " +
                    "chmod 700 ~/.termx/agentd.py.tmp && mv ~/.termx/agentd.py.tmp $REMOTE && echo OK",
                timeoutSec = 30, stdin = String(script),
            )
            if (!r.stdout.contains("OK")) throw SshException(S.get(R.string.agent_install_failed, r.stderr.take(200)))
        }
        installed[hostId] = true
    }

    private fun run(hostId: String, args: String, timeoutSec: Long = 30): JsonObject {
        val c = conn(hostId)
        ensureInstalled(c, hostId)
        val r = c.exec("$PY $REMOTE $args", timeoutSec = timeoutSec)
        val line = r.stdout.lineSequence().lastOrNull { it.trim().startsWith("{") }
            ?: throw SshException(r.stderr.lineSequence().lastOrNull { it.isNotBlank() }?.take(300) ?: S.get(R.string.agent_bad_reply))
        return json.parseToJsonElement(line).jsonObject
    }

    suspend fun detect(hostId: String): List<AgentInfo> = withContext(Dispatchers.IO) {
        val o = run(hostId, "detect", timeoutSec = 60)
        o.arr("agents")?.mapNotNull { e ->
            (e as? JsonObject)?.let {
                AgentInfo(
                    it.str("id"), it.str("name"), it.str("bin"), it.str("version"), it.str("kind"),
                    it.arr("models")?.mapNotNull { m -> (m as? JsonPrimitive)?.content } ?: emptyList(),
                    it.arr("modes")?.mapNotNull { m -> (m as? JsonPrimitive)?.content } ?: emptyList(),
                    it.str("login"), it.str("login_cmd").ifEmpty { it.str("bin") }, it.str("resume_cmd").ifEmpty { it.str("bin") },
                )
            }
        }?.also { agentCache[hostId] = it } ?: emptyList()
    }

    suspend fun list(hostId: String): List<ChatMeta> = withContext(Dispatchers.IO) {
        (run(hostId, "list").arr("chats")?.mapNotNull { e -> (e as? JsonObject)?.toMeta() } ?: emptyList())
            .also { l -> l.forEach { metaCache[it.id] = it } }
    }

    /** Hasil detect/list terakhir (agar layar chat bisa langsung tampil tanpa menunggu server). */
    private val agentCache = ConcurrentHashMap<String, List<AgentInfo>>()
    private val metaCache = ConcurrentHashMap<String, ChatMeta>()
    fun cachedAgents(hostId: String): List<AgentInfo> = agentCache[hostId] ?: emptyList()
    fun cachedMeta(chatId: String): ChatMeta? = metaCache[chatId]

    /** Kuota provider terakhir yang dilaporkan agent di host ini (untuk kartu usage). */
    fun savedLimits(hostId: String): Pair<List<LimitWindow>, Long> {
        val raw = prefs.getString("limits_$hostId", null) ?: return emptyList<LimitWindow>() to 0L
        return runCatching {
            val o = json.parseToJsonElement(raw).jsonObject
            val r = ChatReducer()
            r.apply(o)
            r.snapshot().limits to o.long("at")
        }.getOrDefault(emptyList<LimitWindow>() to 0L)
    }

    /** Diisi dari notifikasi; UI membuka chat ini lalu mengosongkannya. */
    val pendingOpen = MutableStateFlow<Pair<String, String>?>(null)

    /** Sesi agent yang berjalan di server di luar TermX (tmux / herdr / terminal lain). */
    suspend fun running(hostId: String): RunningInfo = withContext(Dispatchers.IO) {
        val o = run(hostId, "running", timeoutSec = 20)
        RunningInfo(
            o.arr("running")?.mapNotNull { e ->
                (e as? JsonObject)?.let {
                    val t = it.obj("tmux")
                    val h = it.obj("herdr")
                    RunningAgent(
                        it.str("agent"), it.long("pid"), it.str("cwd"), it.long("started"), it.str("where"), it.str("tty"),
                        it.str("session"), it.str("title"),
                        t?.str("session") ?: "", t?.str("window") ?: "", t?.str("pane") ?: "", t?.bool("attached") == true,
                        h?.str("pane") ?: "", h?.str("status") ?: "",
                        it.str("status"), it.str("chat"),
                    )
                }
            } ?: emptyList(),
            o.str("herdr_bin"),
        )
    }

    /** Buka sesi yang berjalan sebagai chat (riwayat dari transkrip agent); mengembalikan id chat. */
    suspend fun mirror(hostId: String, r: RunningAgent): String = withContext(Dispatchers.IO) {
        val id = run(hostId, "mirror ${r.pid} ${shq(r.herdrPane)}", timeoutSec = 40).str("id")
        runCatching { list(hostId) }
        id
    }

    /** Pilih window & pane tmux tujuan sebelum attach (agar langsung tampil pane agent-nya). */
    suspend fun selectTmuxPane(hostId: String, r: RunningAgent) = withContext(Dispatchers.IO) {
        val target = shq("=${r.tmuxSession}:${r.tmuxWindow}.${r.tmuxPane}")
        runCatching {
            conn(hostId).exec("tmux select-window -t $target 2>/dev/null; tmux select-pane -t $target 2>/dev/null; true", timeoutSec = 10)
        }
    }

    suspend fun newChat(hostId: String, agent: String, cwd: String, model: String, mode: String): String =
        withContext(Dispatchers.IO) {
            rememberFolder(hostId, cwd)
            run(hostId, "new ${shq(agent)} ${shq(cwd)} ${shq(model)} ${shq(mode)}").str("id")
        }

    suspend fun delete(hostId: String, chatId: String) = withContext(Dispatchers.IO) {
        close(chatId)
        run(hostId, "rm ${shq(chatId)}")
    }

    suspend fun rename(hostId: String, chatId: String, title: String) = withContext(Dispatchers.IO) {
        run(hostId, "rename ${shq(chatId)} ${shq(title)}")
    }

    /** Cek cepat folder ada di server (untuk validasi form chat baru). */
    suspend fun folderExists(hostId: String, path: String): Boolean = withContext(Dispatchers.IO) {
        val p = if (path.startsWith("~")) "\$HOME" + shq(path.removePrefix("~")) else shq(path)
        conn(hostId).exec("test -d $p && echo YES", timeoutSec = 10).stdout.contains("YES")
    }

    private fun JsonObject.toMeta() = ChatMeta(
        str("id"), str("agent"), str("cwd"), str("title"), str("model"), str("mode"), str("session"),
        long("created"), long("updated"), str("status"), dbl("cost"), long("tokens_in"), long("tokens_out"),
        bool("alive") == true,
        obj("mirror")?.let { m ->
            MirrorInfo(m.str("where"), m.str("place"), m.str("tmux"), m.str("herdr_pane"), m.str("herdr_bin"))
        },
    )

    // ------------------------------------------------------------ folder terakhir

    fun recentFolders(hostId: String): List<String> =
        prefs.getString("dirs_$hostId", null)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()

    private fun rememberFolder(hostId: String, cwd: String) {
        val list = (listOf(cwd) + recentFolders(hostId).filter { it != cwd }).take(8)
        prefs.edit().putString("dirs_$hostId", list.joinToString("\n")).apply()
    }

    fun lastAgent(hostId: String): String? = prefs.getString("agent_$hostId", null)
    fun lastModel(agent: String): String = prefs.getString("model_$agent", "") ?: ""
    fun rememberChoice(hostId: String, agent: String, model: String) {
        prefs.edit().putString("agent_$hostId", agent).putString("model_$agent", model).apply()
    }

    // ------------------------------------------------------------ chat live

    fun open(hostId: String, chatId: String): AgentChat {
        _chats.value[chatId]?.let { return it }
        val chat = AgentChat(hostId, chatId)
        _chats.update { it + (chatId to chat) }
        sessions.acquire(hostId, "agent:$chatId")
        chat.start()
        sessions.refreshService()
        return chat
    }

    fun close(chatId: String) {
        val chat = _chats.value[chatId] ?: return
        _chats.update { it - chatId }
        chat.stop()
        sessions.release(chat.hostId, "agent:$chatId")
        sessions.refreshService()
    }

    /** Layar chat ditutup: tetap tersambung hanya bila agent masih bekerja / menunggu izin. */
    fun leave(chatId: String) {
        if (visibleChat == chatId) visibleChat = null
        val s = _chats.value[chatId]?.state?.value ?: return
        if (!s.working && s.pendingPermissions == 0) close(chatId)
    }

    inner class AgentChat(val hostId: String, val chatId: String) {
        private val reducer = ChatReducer()
        private val _state = MutableStateFlow(ChatState())
        val state: StateFlow<ChatState> = _state.asStateFlow()
        private val dirty = Channel<Unit>(Channel.CONFLATED)
        private var job: Job? = null
        @Volatile private var out: OutputStream? = null
        @Volatile private var session: Session? = null
        private val queue = ArrayList<String>()
        private var lastNotified = -1

        internal fun start() {
            job = scope.launch {
                launch { while (isActive) { dirty.receive(); publish(); delay(33) } }
                loop()
            }
        }

        private fun publish() {
            val s = synchronized(reducer) { reducer.snapshot() }
            _state.value = s
        }

        private fun mark() { dirty.trySend(Unit) }

        private suspend fun loop() {
            var backoff = 1000L
            var first = true
            while (scope.isActive && _chats.value.containsKey(chatId)) {
                synchronized(reducer) { reducer.setLink(if (first) Link.CONNECTING else Link.RECONNECTING) }
                mark()
                try {
                    val c = conn(hostId)
                    ensureInstalled(c, hostId)
                    val client = c.client ?: throw SshException(S.get(R.string.ssh_not_connected))
                    val s = client.startSession()
                    session = s
                    val from = synchronized(reducer) { reducer.lastSeq + 1 }
                    val cmd = s.exec("$PY $REMOTE attach ${shq(chatId)} $from")
                    val o = cmd.outputStream
                    out = o
                    synchronized(queue) {
                        for (line in queue) o.write((line + "\n").toByteArray())
                        queue.clear()
                    }
                    o.flush()
                    synchronized(reducer) { reducer.setLink(Link.LIVE) }
                    mark()
                    first = false
                    backoff = 1000L
                    val reader = cmd.inputStream.bufferedReader(Charsets.UTF_8)
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isBlank()) continue
                        val ev = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: continue
                        val changed = synchronized(reducer) { reducer.apply(ev) }
                        if (changed) {
                            mark()
                            maybeNotify(ev)
                        }
                        when (ev.str("t")) {
                            "limits" -> prefs.edit().putString("limits_$hostId", ev.toString()).apply()
                            // Selesai di latar belakang: tidak perlu tetap tersambung.
                            "done" -> if (visibleChat != chatId && System.currentTimeMillis() - ev.long("at") < 60_000) {
                                scope.launch { delay(1500); if (visibleChat != chatId) close(chatId) }
                            }
                        }
                        if (ev.str("t") == "gone") return
                    }
                } catch (e: Exception) {
                    if (!_chats.value.containsKey(chatId)) return
                    val fatal = (e as? SshException)?.fatal == true
                    synchronized(reducer) { reducer.setLink(if (fatal) Link.FAILED else Link.RECONNECTING, e.message) }
                    mark()
                    if (fatal) return
                } finally {
                    out = null
                    runCatching { session?.close() }
                    session = null
                }
                if (!_chats.value.containsKey(chatId)) return
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(8000)
            }
        }

        /** Coba sambung lagi sekarang (mis. setelah gagal). */
        fun retry() {
            if (job?.isActive != true) start() else runCatching { session?.close() }
        }

        internal fun stop() {
            job?.cancel()
            runCatching { session?.close() }
        }

        private fun write(o: JsonObject) {
            val line = o.toString()
            scope.launch {
                val stream = out
                val ok = stream != null && runCatching {
                    synchronized(stream) { stream.write((line + "\n").toByteArray()); stream.flush() }
                }.isSuccess
                if (!ok) synchronized(queue) { queue.add(line) }
            }
        }

        fun send(text: String) {
            synchronized(reducer) { reducer.optimistic(text) }
            mark()
            write(buildJsonObject { put("c", "send"); put("text", text) })
        }
        fun answer(rid: String, allow: Boolean, always: Boolean = false) =
            write(buildJsonObject { put("c", "perm"); put("rid", rid); put("allow", allow); put("always", always) })
        fun setModel(model: String) = write(buildJsonObject { put("c", "model"); put("model", model) })
        fun setMode(mode: String) = write(buildJsonObject { put("c", "mode"); put("mode", mode) })
        fun interrupt() = write(buildJsonObject { put("c", "stop") })

        private fun maybeNotify(ev: JsonObject) {
            if (visibleChat == chatId) return
            val t = ev.str("t")
            val seq = (ev["n"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return
            if (seq <= lastNotified) return
            val text = when (t) {
                "perm" -> S.get(R.string.agent_notif_permission, ev.str("tool"))
                "done" -> if (ev.bool("ok") == false) S.get(R.string.agent_notif_failed) else S.get(R.string.agent_notif_done)
                else -> return
            }
            // Event lama yang diputar ulang saat sambung pertama tidak perlu notifikasi.
            if (System.currentTimeMillis() - ev.long("at") > 60_000) return
            lastNotified = seq
            notify(chatId, text)
        }
    }

    // ------------------------------------------------------------ notifikasi

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, S.get(R.string.agent_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = S.get(R.string.agent_channel_desc)
            })
        }
    }

    private fun notify(chatId: String, text: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)
        val chat = _chats.value[chatId] ?: return
        val intent = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_AGENT)
            .putExtra(MainActivity.EXTRA_HOST, chat.hostId)
            .putExtra(MainActivity.EXTRA_CHAT, chatId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, chatId.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_terminal)
            .setContentTitle(S.get(R.string.agent_notif_title))
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { nm.notify(chatId.hashCode(), n) }
    }

    fun clearNotification(chatId: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(chatId.hashCode())
    }

    companion object {
        const val REMOTE = "~/.termx/agentd.py"
        const val PY = "python3"
        private const val CHANNEL = "agents"
    }
}
