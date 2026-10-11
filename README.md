# Konsol — klien SSH Android

> Nama "Konsol" masih sementara. Untuk menggantinya, ubah `app_name` di
> `app/src/main/res/values/strings.xml` dan `applicationId` di `app/build.gradle.kts`.

Klien SSH Android bergaya modern (terinspirasi alur kerja Termius) dengan Kotlin + Jetpack Compose.
Fitur andalannya adalah **sesi persisten**: shell dijalankan di dalam `tmux` di server. Program yang
Anda jalankan (Claude Code, build panjang, `tail -f`, dsb.) tetap hidup di server walau HP terputus,
layar mati, atau aplikasi ditutup. Saat tersambung lagi, aplikasi otomatis masuk ke sesi yang sama.

## Fitur

| Area | Isi |
|---|---|
| **Host** | Daftar host dengan grup, tag, warna, pencarian; duplikat; jump host (bastion); keep-alive; perintah awal |
| **Terminal** | Emulator xterm-256color + truecolor buatan sendiri (alt screen, scroll region, mouse SGR, bracketed paste, OSC title/clipboard), multi-tab, pinch-zoom, seleksi & salin, bar tombol tambahan (CTRL/ALT sticky, panah, F1–F12, dapat diatur), 5 tema |
| **Sesi persisten** | Attach otomatis ke `tmux new -A`; mouse tmux untuk menggulir riwayat dengan geser; auto-reconnect dengan backoff + reaksi saat jaringan berganti; daftar/attach/akhiri sesi tmux di server; perintah awal hanya dijalankan saat sesi baru dibuat |
| **Latar belakang** | Foreground service + wake/wifi lock menjaga koneksi saat aplikasi di belakang; notifikasi dengan tombol "Putuskan semua" |
| **Keychain** | Buat kunci Ed25519 / RSA, impor OpenSSH / PEM / PuTTY (dengan passphrase), salin/bagikan public key, **pasang ke `authorized_keys` server dengan satu tap** |
| **Keamanan** | Password & kunci privat dienkripsi AES-256-GCM dengan Android Keystore; verifikasi host key (TOFU, SHA256) dengan peringatan bila berubah; daftar known hosts |
| **SFTP** | Jelajah, unggah/unduh (Storage Access Framework) dengan progres, buat folder, ganti nama, hapus rekursif, lihat/edit file teks kecil, file tersembunyi |
| **Port forwarding** | Lokal (-L), remote (-R), dinamis/SOCKS5 (-D); mulai otomatis |
| **Snippet** | Simpan perintah; jalankan dari terminal (⚡) atau ke tab aktif mana pun |
| **Monitor** | CPU, RAM, swap, load, disk, jaringan, proses teratas, jumlah container Docker — read-only, refresh 3 detik |
| **Cadangan** | Ekspor/impor konfigurasi (tanpa rahasia) |

## Membangun APK

### Opsi A — GitHub Actions (tanpa install apa pun)
1. Push folder ini ke repo GitHub (privat).
2. Workflow `.github/workflows/android.yml` otomatis menjalankan unit test dan `assembleDebug`.
3. Unduh APK dari tab **Actions → run terbaru → Artifacts → `konsol-debug-apk`**.

### Opsi B — Android Studio
1. Buka folder proyek di Android Studio (Ladybug atau lebih baru), tunggu Gradle sync.
2. **Build → Build App Bundle(s)/APK(s) → Build APK(s)**, atau `./gradlew assembleDebug`.
3. APK ada di `app/build/outputs/apk/debug/`.

Kebutuhan: JDK 17, Android SDK 34. Versi: AGP 8.5.2, Kotlin 2.0.21, Gradle 8.9, Compose BOM 2024.09.00,
sshj 0.39.0, BouncyCastle 1.78.1. minSdk 26 (Android 8.0).

## Cara kerja sesi persisten

Saat membuka terminal ke host dengan "Jalankan di tmux" aktif, aplikasi menjalankan (via `sh -c`):

```sh
tmux has-session -t NAMA || tmux new-session -d -s NAMA [&& kirim perintah awal]
exec tmux attach-session -t NAMA \; set-option -q mouse on
```

Bila `tmux` tidak ada di server, aplikasi memberi tahu lalu jatuh kembali ke shell biasa
(pasang dengan `sudo apt install tmux`). Contoh pemakaian untuk Claude Code: buat host dengan
nama sesi tmux `claude` dan perintah awal `cd /srv/app && claude`. Tutup aplikasi kapan saja;
Claude Code tetap bekerja di server, dan saat Anda membuka host itu lagi, layarnya muncul kembali.

Menutup tab **tidak** menghentikan sesi tmux. Gunakan menu ⋮ → "Tutup & akhiri sesi tmux",
atau tab **Sesi → Sesi tmux di server** untuk mengakhirinya.

## Struktur kode

```
app/src/main/java/com/kroombox/konsol/
├── KonsolApp.kt, MainActivity.kt   # setup BouncyCastle, container, activity
├── data/       # model (kotlinx.serialization), Vault (JSON atomik), SecretBox (Keystore), KeyManager, format kunci OpenSSH
├── ssh/        # SshConnection (sshj), SessionManager, TerminalSession (reconnect), Tmux, PortForwards (+SOCKS5), ServerStats, Prompts
├── terminal/   # TerminalEmulator (VT/xterm), TerminalView (Canvas + IME), KeyEncoder, TerminalTheme
├── service/    # SessionService (foreground service)
└── ui/         # Compose: AppNav, theme, components, screens/*
```

Unit test JVM (`app/src/test`) mencakup emulator terminal, encoder tombol, format kunci, perintah tmux, dan parser statistik.

## Catatan

- Port di bawah 1024 tidak bisa dibuka di HP tanpa root (port forwarding lokal/SOCKS).
- Opsi "Jaga koneksi saat layar mati" memakai wake lock — menambah konsumsi baterai, matikan di Pengaturan bila tidak perlu.
- Belum ada: sinkronisasi cloud antar perangkat, Mosh, agent forwarding, biometrik app lock — kandidat versi berikutnya.
