package com.kroombox.termx.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.kroombox.termx.BuildConfig
import com.kroombox.termx.MainActivity
import com.kroombox.termx.R
import com.kroombox.termx.i18n.S
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Rilis TermX di GitHub. */
data class Release(val version: String, val tag: String, val notes: String) {
    val apkName get() = "TermX-$tag.apk"
    val apkUrl get() = "https://github.com/${Updater.REPO}/releases/download/$tag/$apkName"
    val shaUrl get() = "$apkUrl.sha256"
    val pageUrl get() = "https://github.com/${Updater.REPO}/releases/tag/$tag"
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class Ready(val release: Release, val file: File) : UpdateState
    data class Failed(val message: String, val release: Release? = null) : UpdateState
}

/**
 * Pembaruan di dalam aplikasi dari GitHub Releases. Versi terbaru dibaca dari redirect halaman
 * `releases/latest` (bukan REST API, yang dibatasi 60 permintaan/jam per IP), APK diunduh dan dicek
 * SHA-256 serta tanda tangannya, lalu installer Android dibuka. Data aplikasi tetap ada karena APK
 * baru ditandatangani dengan kunci yang sama.
 */
class Updater(private val app: Context) {
    private val prefs = app.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    /** Unduhan tetap berjalan walau layar yang memulainya ditutup. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Rilis yang dialognya sedang/perlu ditampilkan. */
    val prompt = MutableStateFlow<Release?>(null)

    val currentVersion: String get() = BuildConfig.VERSION_NAME.substringBefore('-')

    /** Cek berkala selama proses hidup: saat aplikasi dibuka, lalu setiap [INTERVAL_MS]. */
    fun startAutoCheck(enabled: () -> Boolean) {
        if (BuildConfig.DEBUG) return
        scope.launch {
            while (true) {
                val last = prefs.getLong(KEY_LAST_CHECK, 0)
                if (enabled() && System.currentTimeMillis() - last >= INTERVAL_MS) checkNow(manual = false)
                delay(CHECK_TICK_MS)
            }
        }
    }

    /** Cek rilis terbaru. Manual: dialog selalu muncul bila ada versi baru, walau pernah ditunda. */
    suspend fun checkNow(manual: Boolean) = mutex.withLock {
        if (_state.value is UpdateState.Downloading) return@withLock
        _state.value = UpdateState.Checking
        val result = withContext(Dispatchers.IO) { runCatching { fetchLatest() } }
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        val release = result.getOrElse {
            _state.value = if (manual) UpdateState.Failed(S.get(R.string.update_check_failed, it.message ?: "?")) else UpdateState.Idle
            return@withLock
        }
        if (release == null || !Versions.isNewer(release.version, currentVersion)) {
            _state.value = if (manual) UpdateState.UpToDate else UpdateState.Idle
            return@withLock
        }
        _state.value = UpdateState.Available(release)
        if (manual || prefs.getString(KEY_DISMISSED, null) != release.version) prompt.value = release
        if (!manual && prefs.getString(KEY_NOTIFIED, null) != release.version) {
            notifyAvailable(release)
            prefs.edit().putString(KEY_NOTIFIED, release.version).apply()
        }
    }

    /** "Nanti": versi ini tidak dimunculkan otomatis lagi (tetap bisa lewat Periksa update). */
    fun dismiss(release: Release) {
        prefs.edit().putString(KEY_DISMISSED, release.version).apply()
        prompt.value = null
    }

    /** Unduh, verifikasi, lalu buka installer. */
    fun downloadAndInstall(release: Release) {
        val s = _state.value
        if (s is UpdateState.Downloading) return
        if (s is UpdateState.Ready && s.release == release && s.file.exists()) {
            install(s.file)
            return
        }
        scope.launch {
            _state.value = UpdateState.Downloading(release, 0f)
            val file = withContext(Dispatchers.IO) {
                runCatching { download(release) { p -> _state.value = UpdateState.Downloading(release, p) } }
            }.getOrElse {
                _state.value = UpdateState.Failed(S.get(R.string.update_download_failed, it.message ?: "?"), release)
                return@launch
            }
            _state.value = UpdateState.Ready(release, file)
            install(file)
        }
    }

    /** Buka installer sistem. Bila izin "pasang aplikasi tak dikenal" belum ada, buka pengaturannya dulu. */
    fun install(file: File) {
        if (Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()) {
            app.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            android.widget.Toast.makeText(app, S.get(R.string.update_install_permission), android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", file)
        app.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, APK_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    // ------------------------------------------------------------------ Jaringan

    private fun fetchLatest(): Release? {
        val conn = open("https://github.com/$REPO/releases/latest")
        conn.instanceFollowRedirects = false
        val location = try {
            conn.responseCode
            conn.getHeaderField("Location")
        } finally {
            conn.disconnect()
        }
        val tag = location?.substringAfter("/releases/tag/", "")?.takeIf { it.isNotBlank() } ?: return null
        val version = tag.removePrefix("v")
        val notes = runCatching {
            Changelog.section(readText("https://raw.githubusercontent.com/$REPO/$tag/CHANGELOG.md"), version)
        }.getOrDefault("")
        return Release(version, tag, notes)
    }

    private fun download(release: Release, onProgress: (Float) -> Unit): File {
        val expected = readText(release.shaUrl).trim().substringBefore(' ').lowercase()
        require(expected.length == 64) { S.get(R.string.update_no_checksum) }
        val dir = File(app.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, release.apkName)
        val digest = MessageDigest.getInstance("SHA-256")
        val conn = open(release.apkUrl)
        try {
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            val total = conn.contentLengthLong
            var done = 0L
            var lastReport = 0L
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        if (total > 0 && done - lastReport > 256 * 1024) {
                            lastReport = done
                            onProgress(done.toFloat() / total)
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != expected) {
            file.delete()
            error(S.get(R.string.update_checksum_mismatch))
        }
        if (!sameSigner(file)) {
            file.delete()
            error(S.get(R.string.update_signature_mismatch))
        }
        onProgress(1f)
        return file
    }

    /** APK baru harus ditandatangani kunci yang sama dengan aplikasi terpasang. */
    @Suppress("DEPRECATION")
    private fun sameSigner(apk: File): Boolean {
        if (Build.VERSION.SDK_INT < 28) return true
        val pm = app.packageManager
        val archive = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES) ?: return false
        if (archive.packageName != app.packageName) return false
        val mine = pm.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val theirs = archive.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet() ?: return false
        val ours = mine.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet() ?: return false
        return theirs == ours
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        setRequestProperty("User-Agent", "TermX/${BuildConfig.VERSION_NAME}")
    }

    private fun readText(url: String): String {
        val conn = open(url)
        try {
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            conn.disconnect()
        }
    }

    // ------------------------------------------------------------------ Notifikasi

    fun ensureChannel() {
        app.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, S.get(R.string.update_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = S.get(R.string.update_channel_desc)
            },
        )
    }

    private fun notifyAvailable(release: Release) {
        val open = PendingIntent.getActivity(
            app, 7,
            Intent(app, MainActivity::class.java).setAction(MainActivity.ACTION_SHOW_UPDATE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_terminal)
            .setContentTitle(S.get(R.string.update_available_title, release.version))
            .setContentText(S.get(R.string.update_notif_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            app.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)
        } catch (_: SecurityException) {
            // Izin notifikasi ditolak: dialog di aplikasi tetap muncul.
        }
    }

    /** Pemeriksaan manual dari layar About. */
    fun checkInBackground() {
        scope.launch { checkNow(manual = true) }
    }

    /** Dari notifikasi: tampilkan dialog rilis (cek ulang bila proses sempat dimatikan). */
    fun showPrompt() {
        when (val s = state.value) {
            is UpdateState.Available -> prompt.value = s.release
            is UpdateState.Downloading -> prompt.value = s.release
            is UpdateState.Ready -> prompt.value = s.release
            else -> checkInBackground()
        }
    }

    companion object {
        const val REPO = "andiahmadnurmadani/TermX"
        private const val CHANNEL_ID = "updates"
        private const val NOTIF_ID = 4242
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val INTERVAL_MS = 6 * 60 * 60 * 1000L
        private const val CHECK_TICK_MS = 30 * 60 * 1000L
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_DISMISSED = "dismissed_version"
        private const val KEY_NOTIFIED = "notified_version"
    }
}

/** Perbandingan versi `x.y.z` (bagian non-angka diabaikan). */
object Versions {
    fun isNewer(remote: String, current: String): Boolean {
        val a = parts(remote)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(v: String) = v.substringBefore('-').split('.').map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
}

/** Ambil bagian `## [versi]` dari CHANGELOG.md sebagai catatan rilis. */
object Changelog {
    fun section(text: String, version: String): String {
        val out = StringBuilder()
        var found = false
        for (line in text.lineSequence()) {
            if (line.startsWith("## ")) {
                if (found) break
                if (line.contains("[$version]")) { found = true; continue }
            }
            if (found) out.appendLine(line)
        }
        return out.toString().trim()
    }
}
