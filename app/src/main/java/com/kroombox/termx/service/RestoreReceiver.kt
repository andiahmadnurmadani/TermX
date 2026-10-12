package com.kroombox.termx.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kroombox.termx.TermXApp

/**
 * Setelah HP dinyalakan ulang atau aplikasi diperbarui, sesi yang masih aktif (belum diputus
 * pengguna) dibuka lagi. Memulai proses saja sudah cukup: TermXApp.onCreate memulihkan tab dan
 * menyalakan foreground service beserta notifikasinya.
 */
class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val mgr = TermXApp.container.sessions
                mgr.restoreSessions()
                mgr.refreshService()
            }
        }
    }
}
