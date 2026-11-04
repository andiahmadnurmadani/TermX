package com.kroombox.termx.net

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import com.kroombox.termx.data.Host
import kotlinx.coroutines.flow.MutableStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Info Tailscale saja: TermX tidak memakai token/akun Tailscale. Bila host beralamat tailnet
 * (100.64.0.0/10, fd7a:115c:a1e0::/48, atau *.ts.net) dan VPN belum aktif di HP, pengguna diberi tahu
 * sebelum menyambung.
 */
object Tailscale {
    const val PACKAGE = "com.tailscale.ipn"

    fun isTailnetAddress(hostname: String): Boolean {
        val h = hostname.trim().trimEnd('.').lowercase()
        if (h.endsWith(".ts.net")) return true
        if (h.startsWith("fd7a:115c:a1e0")) return true
        val p = h.split('.')
        if (p.size != 4) return false
        val a = p[0].toIntOrNull() ?: return false
        val b = p[1].toIntOrNull() ?: return false
        if (p.drop(2).any { it.toIntOrNull() !in 0..255 }) return false
        return a == 100 && b in 64..127
    }

    /** Host (atau jump host-nya) lewat tailnet. */
    fun needsTailnet(host: Host, lookup: (String) -> Host?): Boolean {
        var h: Host? = host
        var depth = 0
        while (h != null && depth < 4) {
            if (isTailnetAddress(h.hostname)) return true
            h = h.jumpHostId?.takeIf { it != h!!.id }?.let(lookup)
            depth++
        }
        return false
    }

    /** VPN aktif dan HP punya alamat tailnet (100.64/10). */
    fun isUp(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        @Suppress("DEPRECATION")
        val vpn = cm.allNetworks.any { n -> cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
        if (!vpn) return false
        return runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().any { ni ->
                ni.isUp && ni.inetAddresses.toList().any { a ->
                    a is Inet4Address && a.address[0].toInt() and 0xff == 100 && (a.address[1].toInt() and 0xff) in 64..127
                }
            }
        }.getOrDefault(true) // tak bisa dicek: anggap VPN yang aktif adalah Tailscale
    }

    fun isInstalled(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(PACKAGE, 0); true }.getOrDefault(false)

    fun open(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(PACKAGE)
        val intent = launch ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE"))
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Aksi yang menunggu keputusan pengguna (dialog di AppRoot). */
    data class Pending(val hostLabel: String, val proceed: () -> Unit)

    val pending = MutableStateFlow<Pending?>(null)

    /** Jalankan [action] langsung, atau tampilkan info dulu bila Tailscale dibutuhkan tapi belum aktif. */
    fun guard(context: Context, host: Host?, lookup: (String) -> Host?, action: () -> Unit) {
        if (host == null || !needsTailnet(host, lookup) || isUp(context)) action()
        else pending.value = Pending(host.label, action)
    }
}
