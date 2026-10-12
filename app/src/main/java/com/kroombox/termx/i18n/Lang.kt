package com.kroombox.termx.i18n

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.StringRes
import java.util.Locale

/**
 * Bahasa aplikasi. Default English; Bahasa Indonesia bisa dipilih di Settings, atau mengikuti sistem.
 *
 * UI Compose memakai `stringResource(...)` dari context Activity yang sudah dibungkus [wrap]. Kode di
 * luar UI (service, koneksi SSH, pesan error) memakai [S.get], yang membaca context ber-locale yang sama.
 */
object Lang {
    const val ENGLISH = "en"
    const val INDONESIAN = "id"
    const val SYSTEM = "system"

    /** Locale sistem saat proses dimulai (sebelum aplikasi mengubah apa pun). */
    private val systemLocale: Locale = Locale.getDefault()

    @Volatile
    private var appContext: Context? = null

    fun locale(setting: String): Locale = when (setting) {
        INDONESIAN -> Locale("in", "ID")
        SYSTEM -> if (systemLocale.language == "in" || systemLocale.language == "id") Locale("in", "ID") else Locale.ENGLISH
        else -> Locale.ENGLISH
    }

    fun wrap(base: Context, setting: String): Context {
        val loc = locale(setting)
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(loc)
        cfg.setLocales(LocaleList(loc))
        return base.createConfigurationContext(cfg)
    }

    /** Dipanggil saat start dan saat bahasa diganti. */
    fun apply(app: Context, setting: String) {
        appContext = wrap(app.applicationContext, setting)
    }

    fun context(): Context = appContext ?: error("Lang is not initialized")
}

/** String terjemahan untuk kode di luar Compose. */
object S {
    fun get(@StringRes id: Int, vararg args: Any): String =
        if (args.isEmpty()) Lang.context().getString(id) else Lang.context().getString(id, *args)
}
