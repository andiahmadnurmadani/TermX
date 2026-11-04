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

    private val installLocks = ConcurrentHashMap<String, Any>()
    private val remotes = ConcurrentHashMap<String, RemoteHost>()
    private val posixHost = RemoteHost(false, PY, REMOTE)

    /** Cara memanggil agentd di host ini (diketahui setelah pemasangan pertama). */
    private fun rh(hostId: String): RemoteHost = remotes[hostId] ?: posixHost

    // detect/list/running dipanggil bersamaan: hanya satu yang memasang, sisanya menunggu hasilnya.
    private fun ensureInstalled(c: SshConnection, hostId: String) {
        if (installed[hostId] == true) return
        synchronized(installLocks.getOrPut(hostId) { Any() }) {
            if (installed[hostId] == true) return
            installNow(c, hostId)
        }
    }

    private fun installNow(c: SshConnection, hostId: String) {
        val host = probe(c)
        remotes[hostId] = host
        if (host.windows) installWindows(c, host) else installPosix(c)
        installed[hostId] = true
    }

    /** Linux/macOS → sh seperti biasa; Windows → python yang ada (python3 / python / py -3) dan path absolut. */
    private fun probe(c: SshConnection): RemoteHost {
        for (py in listOf("python3", "python", "py -3")) {
            val r = runCatching { c.exec("$py -c \"${RemoteCmd.PROBE}\"", timeoutSec = 15) }.getOrNull() ?: continue
            val (plat, home) = RemoteCmd.parseProbe(r.stdout) ?: continue
            return if (plat == "win32") RemoteHost(true, py, home.trimEnd('\\', '/') + "\\.termx\\agentd.py") else posixHost
        }
        throw SshException(S.get(R.string.agent_no_python), fatal = true)
    }

    private fun installPosix(c: SshConnection) {
        val v = c.exec("$PY $REMOTE version 2>/dev/null; command -v python3 >/dev/null 2>&1 || echo NOPY", timeoutSec = 15)
        if (v.stdout.contains("NOPY")) throw SshException(S.get(R.string.agent_no_python), fatal = true)
        if (v.stdout.trim().toIntOrNull() != scriptVersion) {
            val r = c.exec(
                // Nama sementara unik per proses ($$) agar dua pemasangan tidak saling menimpa.
                "mkdir -p ~/.termx && chmod 700 ~/.termx && t=~/.termx/agentd.py.tmp.$$ && cat > \"\$t\" && " +
                    "chmod 700 \"\$t\" && mv -f \"\$t\" $REMOTE && echo OK",
                timeoutSec = 30, stdin = String(script),
            )
            if (!r.stdout.contains("OK")) throw SshException(S.get(R.string.agent_install_failed, r.stderr.take(200)))
        }
    }

    private fun installWindows(c: SshConnection, host: RemoteHost) {
        val v = c.exec("${host.py} \"${host.script}\" version", timeoutSec = 15)
        if (v.stdout.trim().toIntOrNull() == scriptVersion) return
        // SFTP dulu (tidak bergantung pada cara PowerShell/cmd meneruskan stdin), lalu ganti berkasnya lewat python.
        val viaSftp = runCatching {
            val sftp = c.sftp()
            runCatching { sftp.mkdirs(".termx") }
            val rf = sftp.open(".termx/agentd.py.tmp", java.util.EnumSet.of(
                net.schmizz.sshj.sftp.OpenMode.WRITE, net.schmizz.sshj.sftp.OpenMode.CREAT, net.schmizz.sshj.sftp.OpenMode.TRUNC))
            try {
                rf.RemoteFileOutputStream(0, 16).use { it.write(script) }
            } finally {
                rf.close()
            }
            c.exec("${host.py} -c \"${RemoteCmd.WIN_REPLACE}\"", timeoutSec = 20).stdout.contains("OK")
        }.getOrDefault(false)
        if (viaSftp) return
        val r = c.exec("${host.py} -c \"${RemoteCmd.WIN_STDIN_INSTALL}\"", timeoutSec = 30, stdin = String(script))
        if (!r.stdout.contains("OK")) throw SshException(S.get(R.string.agent_install_failed, r.stderr.take(200)))
    }

    private fun run(hostId: String, args: String, timeoutSec: Long = 30, stdin: String? = null): JsonObject {
        val c = conn(hostId)
        ensureInstalled(c, hostId)
        val h = rh(hostId)
        val r = when {
            h.windows -> c.exec(h.line(args, stdin), timeoutSec = timeoutSec)  // stdin ikut di dalam argumen
            stdin != null -> c.exec(h.line(args), timeoutSec = timeoutSec, stdin = stdin)
            else -> c.exec(h.line(args), timeoutSec = timeoutSec)
        }
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
    /** Agent yang terakhir terlihat berjalan per host (untuk ikon di kartu host). */
    private val _runningByHost = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val runningByHost: StateFlow<Map<String, List<String>>> = _runningByHost.asStateFlow()

    /** Agent yang terakhir terlihat berjalan di host ini, bila dicek kurang dari 15 menit lalu. */
    fun lastRunning(hostId: String): List<String> {
        val raw = prefs.getString("run_$hostId", null) ?: return emptyList()
        val at = raw.substringBefore('|').toLongOrNull() ?: return emptyList()
        if (System.currentTimeMillis() - at > 15 * 60_000) return emptyList()
        return raw.substringAfter('|').split(',').filter { it.isNotBlank() }
    }

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
        ).also { ri ->
            val ids = ri.agents.map { a -> a.agent }.distinct()
            _runningByHost.update { it + (hostId to ids) }
            prefs.edit().putString("run_$hostId", "${System.currentTimeMillis()}|${ids.joinToString(",")}").apply()
        }
    }

    /** Buka sesi yang berjalan sebagai chat (riwayat dari transkrip agent); mengembalikan id chat. */
    suspend fun mirror(hostId: String, r: RunningAgent): String = withContext(Dispatchers.IO) {
        val id = run(hostId, "mirror ${r.pid} ${shq(r.herdrPane)}", timeoutSec = 90).str("id")
        runCatching { list(hostId) }
        id
    }

    /** Pilih window & pane tmux tujuan sebelum attach (agar langsung tampil pane agent-nya). */
    suspend fun selectTmuxPane(hostId: String, r: RunningAgent) = withContext(Dispatchers.IO) {
        val target = shq("=${r.tmuxSession}:${r.tmuxWindow}.${r.tmuxPane}")
        if (rh(hostId).windows) return@withContext
        runCatching {
            conn(hostId).exec("tmux select-window -t $target 2>/dev/null; tmux select-pane -t $target 2>/dev/null; true", timeoutSec = 10)
        }
    }

    suspend fun newChat(hostId: String, agent: String, cwd: String, model: String, mode: String, inTerminal: Boolean = false): String =
        withContext(Dispatchers.IO) {
            rememberFolder(hostId, cwd)
            prefs.edit().putBoolean("term_$agent", inTerminal).apply()
            val id = run(hostId, "new ${shq(agent)} ${shq(cwd)} ${shq(model)} ${shq(mode)}" + if (inTerminal) " tmux" else "", timeoutSec = 40).str("id")
            if (inTerminal) runCatching { list(hostId) }
            id
        }

    /** Pilihan terakhir "tampilkan juga di terminal" untuk agent ini (bawaan: aktif untuk Claude & Codex). */
    fun lastInTerminal(agent: String): Boolean = prefs.getBoolean("term_$agent", agent == "claude")

    /** Akun & kuota tiap provider, dari perintah status resmi agent dan kejadian yang dilaporkannya. */
    suspend fun usage(hostId: String, cached: Boolean = false): List<ProviderUsage> = withContext(Dispatchers.IO) {
        val o = run(hostId, if (cached) "usage cached" else "usage", timeoutSec = if (cached) 20 else 45)
        o.arr("providers")?.mapNotNull { e ->
            (e as? JsonObject)?.let { p ->
                val a = p.obj("auth")
                ProviderUsage(
                    p.str("agent"), p.str("name"), p.bool("reported") == true,
                    a?.bool("logged_in"), a?.str("method") ?: "", a?.str("plan") ?: "", a?.str("provider") ?: "",
                    a?.str("account") ?: "", a?.str("org") ?: "", a?.str("expires") ?: "",
                    p.obj("limits")?.map { (k, v) ->
                        val w = v as? JsonObject
                        LimitWindow(k, (w?.get("used") as? kotlinx.serialization.json.JsonPrimitive)?.let { it.content.toDoubleOrNull() },
                            w?.long("reset")?.takeIf { it > 0 }, w?.str("status"))
                    } ?: emptyList(),
                    p.long("limits_at"),
                    p.arr("stats")?.mapNotNull { s -> (s as? kotlinx.serialization.json.JsonArray)?.takeIf { it.size >= 2 }?.let {
                        (it[0] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty() to (it[1] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                    } } ?: emptyList(),
                    p.dbl("cost"), p.long("tokens_in"), p.long("tokens_out"), p.long("chats").toInt(),
                )
            }
        } ?: emptyList()
    }

    suspend fun delete(hostId: String, chatId: String) = withContext(Dispatchers.IO) {
        close(chatId)
        synchronized(reducerCache) { reducerCache.remove(chatId) }
        run(hostId, "rm ${shq(chatId)}")
        metaCache.remove(chatId)
        clearNotification(chatId)
    }

    // ------------------------------------------------------------ setup Hermes

    /** Provider & model Hermes saat ini, plus daftar provider (kunci API hanya dilaporkan ada/tidak). */
    suspend fun hermesStatus(hostId: String): HermesStatus = withContext(Dispatchers.IO) {
        val o = run(hostId, "hermes-setup status", timeoutSec = 40)
        val keys = o.obj("keys")
        val oauth = o.obj("oauth")
        HermesStatus(
            o.str("provider"), o.str("model"), o.str("base_url"), o.str("active_oauth"),
            o.arr("providers")?.mapNotNull { e ->
                (e as? JsonObject)?.let {
                    val id = it.str("id")
                    HermesProvider(id, it.str("name"), it.str("env"), it.str("auth"), keys?.bool(id) == true, oauth?.bool(id) == true)
                }
            } ?: emptyList(),
            apiMode = o.str("api_mode"),
            custom = o.arr("custom")?.mapNotNull { e ->
                (e as? JsonObject)?.let { HermesCustom(it.str("name"), it.str("base_url"), it.str("model"), it.str("api_mode"), it.bool("has_key") == true) }
            } ?: emptyList(),
        )
    }

    /** Semua model provider: katalog Hermes + daftar langsung /models bila kunci ada (kunci baru lewat stdin). */
    suspend fun hermesModels(hostId: String, provider: String, key: String = ""): Pair<List<String>, Boolean> = withContext(Dispatchers.IO) {
        val body = buildJsonObject { if (key.isNotBlank()) put("key", key) }
        val o = run(hostId, "hermes-setup models ${shq(provider)}", timeoutSec = 90, stdin = body.toString())
        (o.arr("models")?.mapNotNull { (it as? JsonPrimitive)?.content }?.distinct() ?: emptyList()) to (o.bool("live") == true)
    }

    suspend fun hermesProbe(hostId: String, baseUrl: String, key: String): HermesProbe = withContext(Dispatchers.IO) {
        val body = buildJsonObject { put("base_url", baseUrl); if (key.isNotBlank()) put("key", key) }
        val o = run(hostId, "hermes-setup probe", timeoutSec = 60, stdin = body.toString())
        HermesProbe(
            o.bool("ok") == true, o.arr("models")?.mapNotNull { (it as? JsonPrimitive)?.content }?.distinct() ?: emptyList(),
            o.str("probed_url"), o.str("resolved_base_url"), o.str("suggested_base_url"), o.bool("used_fallback") == true, o.str("default_name"),
        )
    }

    /** Simpan pilihan; kunci API dikirim lewat stdin (tidak tampil di daftar proses server). */
    suspend fun hermesApply(hostId: String, provider: String, model: String, key: String) = withContext(Dispatchers.IO) {
        val body = buildJsonObject { put("provider", provider); put("model", model); if (key.isNotBlank()) put("key", key) }
        run(hostId, "hermes-setup apply", timeoutSec = 40, stdin = body.toString())
    }

    /** Simpan endpoint custom seperti alur custom `hermes model`. */
    suspend fun hermesApplyCustom(
        hostId: String, baseUrl: String, key: String, apiMode: String, model: String, name: String, contextLength: Int?,
    ) = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("provider", "custom"); put("base_url", baseUrl); put("model", model); put("api_mode", apiMode); put("name", name)
            if (key.isNotBlank()) put("key", key)
            if (contextLength != null) put("context_length", contextLength)
        }
        run(hostId, "hermes-setup apply", timeoutSec = 40, stdin = body.toString())
    }

    suspend fun hermesLoginStart(hostId: String, provider: String) = withContext(Dispatchers.IO) {
        run(hostId, "hermes-setup login ${shq(provider)}", timeoutSec = 30)
    }

    suspend fun hermesLoginStatus(hostId: String): HermesLogin = withContext(Dispatchers.IO) {
        val o = run(hostId, "hermes-setup login-status", timeoutSec = 20)
        fun list(k: String) = o.arr(k)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
        HermesLogin(o.bool("running") == true, list("lines"), list("urls"), list("codes"))
    }

    /** Jawaban untuk proses login (mis. kode otorisasi) lewat stdin, bukan argumen. */
    suspend fun hermesLoginInput(hostId: String, text: String) = withContext(Dispatchers.IO) {
        run(hostId, "hermes-setup login-input", timeoutSec = 20, stdin = text)
    }

    suspend fun hermesLoginCancel(hostId: String) = withContext(Dispatchers.IO) {
        run(hostId, "hermes-setup login-cancel", timeoutSec = 20)
    }

    suspend fun rename(hostId: String, chatId: String, title: String) = withContext(Dispatchers.IO) {
        run(hostId, "rename ${shq(chatId)} ${shq(title)}")
    }

    /** Cek cepat folder ada di server (untuk validasi form chat baru). */
    suspend fun folderExists(hostId: String, path: String): Boolean = withContext(Dispatchers.IO) {
        if (rh(hostId).windows) return@withContext run(hostId, "isdir ${shq(path)}", timeoutSec = 15).bool("ok") == true
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

    /** Isi chat yang baru ditutup: dibuka lagi langsung tampil tanpa memuat ulang dari awal. */
    private val reducerCache = object : LinkedHashMap<String, ChatReducer>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ChatReducer>?) = size > 8
    }

    fun close(chatId: String) {
        val chat = _chats.value[chatId] ?: return
        _chats.update { it - chatId }
        chat.stop()
        chat.stash()
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
        private val reducer = synchronized(reducerCache) { reducerCache.remove(chatId) } ?: ChatReducer()
        private val _state = MutableStateFlow(synchronized(reducer) { reducer.snapshot() })
        val state: StateFlow<ChatState> = _state.asStateFlow()
        private val dirty = Channel<Unit>(Channel.CONFLATED)
        private var job: Job? = null
        @Volatile private var out: OutputStream? = null
        @Volatile private var session: Session? = null
        private val queue = ArrayList<String>()
        private val postLock = Any()
        private var lastNotified = -1
        /** Berapa prompt terakhir yang dimuat saat membuka chat (0 = semua). */
        @Volatile private var tailTurns = INITIAL_TURNS
        @Volatile private var resetPending = false
        private val _loadingOlder = MutableStateFlow(false)
        val loadingOlder: StateFlow<Boolean> = _loadingOlder.asStateFlow()

        /** Muat riwayat yang lebih lama: sambung ulang dengan batas lebih besar. */
        fun loadOlder() {
            if (_loadingOlder.value) return
            tailTurns = if (tailTurns == 0 || tailTurns >= 160) 0 else tailTurns * 4
            _loadingOlder.value = true
            resetPending = true
            runCatching { session?.close() }
            if (job?.isActive != true) start()
        }

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
                    if (resetPending) {
                        synchronized(reducer) { reducer.reset() }
                        resetPending = false
                    }
                    val from = synchronized(reducer) { reducer.lastSeq + 1 }
                    val h = rh(hostId)
                    val cmd = s.exec(h.line("attach ${shq(chatId)} $from" + (if (from == 0 && tailTurns > 0) " $tailTurns" else "") +
                        if (h.windows) " --no-stdin" else ""))
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
                    if (_loadingOlder.value) scope.launch { delay(600); _loadingOlder.value = false }
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
                if (!resetPending) delay(backoff)
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

        /** Simpan isi yang sudah lengkap agar pembukaan berikutnya seketika (lanjut dari urutan terakhir). */
        internal fun stash() {
            val keep = synchronized(reducer) { reducer.snapshot().synced && !resetPending }
            if (keep) synchronized(reducerCache) { reducerCache[chatId] = reducer }
        }

        private fun write(o: JsonObject) {
            val line = o.toString()
            if (rh(hostId).windows) {
                // Windows: stdin lewat PowerShell/cmd tidak bisa diandalkan, kirim tiap perintah sendiri-sendiri.
                scope.launch {
                    synchronized(postLock) {
                        runCatching { run(hostId, "post ${shq(chatId)} ${shq(line)}", timeoutSec = 30) }.onFailure { e ->
                            synchronized(reducer) { reducer.apply(buildJsonObject { put("t", "error"); put("msg", e.message ?: "send failed") }) }
                            mark()
                        }
                    }
                }
                return
            }
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
            val t = ev.str("t")
            if (t != "perm" && t != "done") return
            val seq = (ev["n"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return
            if (seq <= lastNotified) return
            // Event lama yang diputar ulang saat sambung pertama tidak perlu notifikasi.
            if (System.currentTimeMillis() - ev.long("at") > 60_000) return
            lastNotified = seq
            val watching = visibleChat == chatId && appInForeground()
            if (t == "perm") {
                if (!watching) notify(chatId, S.get(R.string.agent_notif_permission, ev.str("tool")))
                return
            }
            // Tiap giliran selesai: bunyi; bila chat tidak sedang dilihat juga notifikasi.
            val text = if (ev.bool("ok") == false) S.get(R.string.agent_notif_failed) else S.get(R.string.agent_notif_done)
            if (watching) playDoneSound() else notify(chatId, text)
        }
    }

    private fun appInForeground(): Boolean = runCatching {
        val info = android.app.ActivityManager.RunningAppProcessInfo()
        android.app.ActivityManager.getMyMemoryState(info)
        info.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }.getOrDefault(true)

    private fun playDoneSound() {
        runCatching {
            val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION) ?: return
            android.media.RingtoneManager.getRingtone(context, uri)?.apply {
                audioAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            }?.play()
        }
    }

    // ------------------------------------------------------------ notifikasi

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            runCatching { nm.deleteNotificationChannel(OLD_CHANNEL) } // saluran lama tanpa suara
            nm.createNotificationChannel(NotificationChannel(CHANNEL, S.get(R.string.agent_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = S.get(R.string.agent_channel_desc)
                setSound(
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
                enableVibration(true)
            }
            )
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
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSubText(metaCache[chatId]?.title?.takeIf { it.isNotBlank() })
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
        private const val CHANNEL = "agents_sound"
        const val INITIAL_TURNS = 20
        private const val OLD_CHANNEL = "agents"
    }
}
