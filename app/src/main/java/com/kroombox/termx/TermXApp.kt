package com.kroombox.termx

import android.app.Application
import com.kroombox.termx.data.Vault
import com.kroombox.termx.service.SessionService
import com.kroombox.termx.ssh.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.schmizz.sshj.common.SecurityUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

/** Wadah dependensi sederhana untuk seluruh aplikasi. */
class AppContainer(app: Application) {
    val vault = Vault(app)
    val sessions = SessionManager(app, vault)

    /** Mulai port forward yang ditandai "mulai otomatis". */
    fun startAutoForwards() {
        val rules = vault.current.forwards.filter { it.autoStart }
        if (rules.isEmpty()) return
        sessions.scope.launch(Dispatchers.IO) {
            rules.forEach { sessions.forwards.start(it) }
        }
    }
}

class TermXApp : Application() {
    override fun onCreate() {
        super.onCreate()
        setupCrypto()
        SessionService.ensureChannel(this)
        container = AppContainer(this)
        // Tab yang masih aktif saat proses terakhir dimatikan Android dibuka lagi (attach ke tmux
        // yang sama). Sesi hanya berakhir bila pengguna sendiri menutupnya.
        container.sessions.restoreSessions()
    }

    private fun setupCrypto() {
        // Android membawa BouncyCastle versi terpangkas dengan nama "BC". Ganti dengan versi lengkap
        // (ditaruh di urutan terakhir agar Android Keystore tetap dipakai untuk enkripsi lokal).
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.addProvider(BouncyCastleProvider())
        SecurityUtils.setRegisterBouncyCastle(false)
        SecurityUtils.setSecurityProvider(BouncyCastleProvider.PROVIDER_NAME)
    }

    companion object {
        lateinit var container: AppContainer
            private set
    }
}
