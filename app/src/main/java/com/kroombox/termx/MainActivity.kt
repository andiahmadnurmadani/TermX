package com.kroombox.termx

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.kroombox.termx.ui.AppRoot
import com.kroombox.termx.ui.theme.TermXTheme

class MainActivity : ComponentActivity() {

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(newBase)
        // Bahasa pilihan pengguna (default English). Hanya locale yang ditimpa; ukuran layar, kepadatan,
        // skala huruf, dan mode malam tetap mengikuti sistem (menyalin seluruh konfigurasi membekukannya
        // dan mengubah tampilan, mis. bar navigasi).
        val loc = com.kroombox.termx.i18n.Lang.locale(TermXApp.container.vault.current.settings.language)
        applyOverrideConfiguration(
            android.content.res.Configuration().apply {
                setLocale(loc)
                setLocales(android.os.LocaleList(loc))
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (savedInstanceState == null) TermXApp.container.startAutoForwards()
        handleIntent(intent)
        setContent {
            TermXTheme {
                AppRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Pastikan service berjalan bila ada sesi (start dari latar belakang bisa ditolak sebelumnya).
        TermXApp.container.sessions.refreshService()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent?.action == ACTION_CONFIRM_DISCONNECT) {
            TermXApp.container.sessions.disconnectRequested.value = true
        }
    }

    override fun onStop() {
        super.onStop()
        // Aplikasi ke latar belakang (atau akan ditutup): simpan riwayat layar semua tab sekarang.
        TermXApp.container.sessions.saveAllHistory()
    }

    companion object {
        const val ACTION_CONFIRM_DISCONNECT = "com.kroombox.termx.CONFIRM_DISCONNECT"
    }

    fun setKeepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
