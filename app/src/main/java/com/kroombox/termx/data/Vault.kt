package com.kroombox.termx.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Penyimpanan lokal semua data aplikasi dalam satu file JSON (atomic write).
 * Rahasia di dalamnya sudah terenkripsi oleh [SecretBox].
 */
class Vault(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "vault.json"))
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    private val _data = MutableStateFlow(load())
    val data: StateFlow<VaultData> = _data.asStateFlow()

    val current: VaultData get() = _data.value

    private fun load(): VaultData = try {
        if (file.baseFile.exists()) json.decodeFromString<VaultData>(String(file.readFully(), Charsets.UTF_8))
        else VaultData()
    } catch (e: Exception) {
        // Jangan sampai data hilang: simpan salinan file yang gagal dibaca sebelum ditimpa.
        try {
            val bad = File(file.baseFile.parentFile, "vault.json.bad-${System.currentTimeMillis()}")
            file.baseFile.copyTo(bad, overwrite = false)
        } catch (_: Exception) {
        }
        VaultData()
    }

    fun update(transform: (VaultData) -> VaultData) {
        _data.update(transform)
        persist()
    }

    private fun persist() {
        scope.launch {
            writeLock.withLock {
                val snapshot = _data.value
                val bytes = json.encodeToString(snapshot).toByteArray(Charsets.UTF_8)
                val out = file.startWrite()
                try {
                    out.write(bytes)
                    file.finishWrite(out)
                } catch (e: Exception) {
                    file.failWrite(out)
                }
            }
        }
    }

    // ---------------- Host
    fun host(id: String?): Host? = id?.let { i -> current.hosts.firstOrNull { it.id == i } }

    fun saveHost(host: Host) = update { d ->
        val exists = d.hosts.any { it.id == host.id }
        d.copy(hosts = if (exists) d.hosts.map { if (it.id == host.id) host else it } else d.hosts + host)
    }

    fun deleteHost(id: String) = update { d ->
        d.copy(
            hosts = d.hosts.filterNot { it.id == id }.map { if (it.jumpHostId == id) it.copy(jumpHostId = null) else it },
            forwards = d.forwards.filterNot { it.hostId == id },
        )
    }

    fun touchHost(id: String) = update { d ->
        d.copy(hosts = d.hosts.map { if (it.id == id) it.copy(lastConnectedAt = System.currentTimeMillis()) else it })
    }

    // ---------------- Group
    fun saveGroup(g: HostGroup) = update { d ->
        val exists = d.groups.any { it.id == g.id }
        d.copy(groups = if (exists) d.groups.map { if (it.id == g.id) g else it } else d.groups + g)
    }

    fun deleteGroup(id: String) = update { d ->
        d.copy(
            groups = d.groups.filterNot { it.id == id },
            hosts = d.hosts.map { if (it.groupId == id) it.copy(groupId = null) else it },
        )
    }

    // ---------------- Key
    fun key(id: String?): SshKey? = id?.let { i -> current.keys.firstOrNull { it.id == i } }

    fun saveKey(k: SshKey) = update { d ->
        val exists = d.keys.any { it.id == k.id }
        d.copy(keys = if (exists) d.keys.map { if (it.id == k.id) k else it } else d.keys + k)
    }

    fun deleteKey(id: String) = update { d ->
        d.copy(
            keys = d.keys.filterNot { it.id == id },
            hosts = d.hosts.map { if (it.keyId == id) it.copy(keyId = null, authType = AuthType.PASSWORD) else it },
        )
    }

    // ---------------- Snippet
    fun saveSnippet(s: Snippet) = update { d ->
        val exists = d.snippets.any { it.id == s.id }
        d.copy(snippets = if (exists) d.snippets.map { if (it.id == s.id) s else it } else d.snippets + s)
    }

    fun deleteSnippet(id: String) = update { d -> d.copy(snippets = d.snippets.filterNot { it.id == id }) }

    // ---------------- Known hosts
    fun knownHost(hostPort: String, keyType: String): KnownHost? =
        current.knownHosts.firstOrNull { it.hostPort == hostPort && it.keyType == keyType }

    fun knownHostsFor(hostPort: String): List<KnownHost> = current.knownHosts.filter { it.hostPort == hostPort }

    fun saveKnownHost(k: KnownHost) = update { d ->
        d.copy(knownHosts = d.knownHosts.filterNot { it.hostPort == k.hostPort && it.keyType == k.keyType } + k)
    }

    fun deleteKnownHost(hostPort: String, keyType: String) = update { d ->
        d.copy(knownHosts = d.knownHosts.filterNot { it.hostPort == hostPort && it.keyType == keyType })
    }

    // ---------------- Forward
    fun saveForward(f: ForwardRule) = update { d ->
        val exists = d.forwards.any { it.id == f.id }
        d.copy(forwards = if (exists) d.forwards.map { if (it.id == f.id) f else it } else d.forwards + f)
    }

    fun deleteForward(id: String) = update { d -> d.copy(forwards = d.forwards.filterNot { it.id == id }) }

    // ---------------- Settings
    fun updateSettings(transform: (AppSettings) -> AppSettings) = update { d -> d.copy(settings = transform(d.settings)) }

    /** Ekspor konfigurasi tanpa rahasia (untuk backup/migrasi). */
    fun exportWithoutSecrets(): String {
        val d = current
        val clean = d.copy(
            hosts = d.hosts.map { it.copy(passwordEnc = null, sudoPasswordEnc = null) },
            keys = emptyList(),
            openSessions = emptyList(),
        )
        return Json { prettyPrint = true; encodeDefaults = true }.encodeToString(clean)
    }

    fun importConfig(text: String): Int {
        val incoming = json.decodeFromString<VaultData>(text)
        var added = 0
        update { d ->
            val hostIds = d.hosts.map { it.id }.toSet()
            val newHosts = incoming.hosts.filter { it.id !in hostIds }.map { h ->
                if (h.keyId != null && d.keys.none { it.id == h.keyId }) h.copy(keyId = null, authType = AuthType.PASSWORD) else h
            }
            added = newHosts.size
            val groupIds = d.groups.map { it.id }.toSet()
            val snippetIds = d.snippets.map { it.id }.toSet()
            val fwdIds = d.forwards.map { it.id }.toSet()
            d.copy(
                hosts = d.hosts + newHosts,
                groups = d.groups + incoming.groups.filter { it.id !in groupIds },
                snippets = d.snippets + incoming.snippets.filter { it.id !in snippetIds },
                forwards = d.forwards + incoming.forwards.filter { it.id !in fwdIds },
            )
        }
        return added
    }
}
