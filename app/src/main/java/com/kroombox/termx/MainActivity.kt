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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (savedInstanceState == null) TermXApp.container.startAutoForwards()
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

    override fun onStop() {
        super.onStop()
        // Aplikasi ke latar belakang (atau akan ditutup): simpan riwayat layar semua tab sekarang.
        TermXApp.container.sessions.saveAllHistory()
    }

    fun setKeepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
