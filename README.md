# TermX — klien SSH Android

<p align="center"><img src="docs/logo.png" width="128" alt="TermX"></p>

<p align="center">
  <a href="../../releases/latest">Unduh APK terbaru</a> ·
  <a href="CHANGELOG.md">Changelog</a> ·
  <a href="LICENSE">Lisensi MIT</a>
</p>

Klien SSH Android bergaya modern (terinspirasi alur kerja Termius) dengan Kotlin + Jetpack Compose.
Ketuk host untuk membuka **terminal biasa**; pilih **Terminal tmux** (menu host, tab Sesi, atau menu
terminal) untuk **sesi persisten**: shell dijalankan di dalam `tmux` di server. Program yang
Anda jalankan (Claude Code, build panjang, `tail -f`, dsb.) tetap hidup di server walau HP terputus,
layar mati, atau aplikasi ditutup. Saat tersambung lagi, aplikasi otomatis masuk ke sesi yang sama.

**Sesi tidak pernah putus sendiri.** Selama tab belum ditutup, TermX terus menyambung ulang (tanpa
batas, termasuk saat jaringan berganti Wi-Fi ↔ data), memulihkan tab setelah Android mematikan
proses aplikasi, dan menampilkan notifikasi tetap di atas sampai Anda menekan **Putuskan semua** atau
menutup tab.

## Fitur

| Area | Isi |
|---|---|
| **Host** | Daftar host dengan grup, tag, warna, pencarian; duplikat; jump host (bastion); keep-alive; perintah awal |
| **Ikon host** | Logo OS otomatis (Ubuntu, Debian, Arch, Fedora, Rocky, Alpine, Raspberry Pi, Proxmox, macOS, Windows, dll.) dideteksi saat konek; bisa diganti logo OS lain, ikon bawaan, atau emoji |
| **Sudo** | Password sudo per host (atau pakai password login); snippet "jalankan dengan sudo" mengetik password otomatis saat prompt sudo muncul; menu terminal "Ketik password sudo" |
| **Font** | JetBrains Mono (default), Fira Code, Cascadia Code, Hack, monospace sistem, plus **impor font sendiri (.ttf/.otf, mis. Nerd Font)**; pratinjau di Pengaturan; ligatur bisa dinyalakan; ganti cepat dari menu terminal |
| **Terminal** | Emulator xterm-256color + truecolor buatan sendiri (alt screen, scroll region, mouse SGR, bracketed paste, OSC title/clipboard), multi-tab, pinch-zoom, seleksi & salin, bar tombol tambahan (CTRL/ALT sticky, panah, F1–F12, dapat diatur), 5 tema |
| **Sesi persisten** | Attach otomatis ke `tmux new -A`; mouse tmux untuk menggulir riwayat dengan geser; auto-reconnect dengan backoff + reaksi saat jaringan berganti; daftar/attach/akhiri sesi tmux di server; perintah awal hanya dijalankan saat sesi baru dibuat |
| **Latar belakang** | Foreground service (sticky) + wake/wifi lock; notifikasi tetap (muncul lagi bila diusap) berisi daftar sesi, lama aktif, tombol "Putuskan semua"; tab dipulihkan setelah proses dimatikan; permintaan pengecualian optimasi baterai |
| **Keychain** | Buat kunci Ed25519 / RSA, impor OpenSSH / PEM / PuTTY (dengan passphrase), salin/bagikan public key, **pasang ke `authorized_keys` server dengan satu tap** |
| **Keamanan** | Password & kunci privat dienkripsi AES-256-GCM dengan Android Keystore; verifikasi host key (TOFU, SHA256) dengan peringatan bila berubah; daftar known hosts |
| **SFTP** | Jelajah, unggah/unduh (Storage Access Framework) dengan progres, buat folder, ganti nama, hapus rekursif, lihat/edit file teks kecil, file tersembunyi |
| **Port forwarding** | Lokal (-L), remote (-R), dinamis/SOCKS5 (-D); mulai otomatis |
| **Snippet** | Kelola daftar perintah (ikon pilihan sendiri, opsi sudo) di tab Snippet; jalankan dari dalam terminal lewat ⚡ |
| **Monitor** | CPU, RAM, swap, load, disk, jaringan, proses teratas, daftar container Docker; mode **sudo** untuk server yang butuh root (mis. Docker) — read-only, refresh 3 detik |
| **Cadangan** | Ekspor/impor konfigurasi (tanpa rahasia) |

## Versi & rilis

- Versi mengikuti [SemVer](https://semver.org/lang/id/) dan dicatat di [`CHANGELOG.md`](CHANGELOG.md).
- **Rilis resmi**: tambahkan entri di CHANGELOG, naikkan `termx.versionName`/`termx.versionCode` di
  `gradle.properties`, lalu buat tag:
  ```sh
  git tag v0.3.0 && git push origin v0.3.0
  ```
  Workflow `release.yml` membangun APK rilis bertanda tangan (kunci di secret GitHub
  `TERMX_KEYSTORE_B64` / `TERMX_KEYSTORE_PASSWORD`) dan membuat GitHub Release `TermX vX.Y.Z`
  berisi `TermX-vX.Y.Z.apk`, checksum, dan catatan dari CHANGELOG. `versionCode = X*10000 + Y*100 + Z`.
- **Build pengembangan**: setiap push ke `main` memperbarui pre-release `latest-debug` (aplikasi terpisah,
  id `com.kroombox.termx.debug`).

## Membangun APK

### Opsi A — GitHub Actions (tanpa install apa pun)
1. Push folder ini ke repo GitHub (privat).
2. Workflow `.github/workflows/android.yml` otomatis menjalankan unit test dan `assembleDebug`.
3. Unduh APK dari **Releases → `latest-debug` → `termx-debug.apk`** (atau Actions → Artifacts → `termx-debug-apk`).
   Build debug CI ditandatangani kunci debug yang disimpan sebagai secret repo, jadi versi baru bisa dipasang menimpa yang lama.

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
app/src/main/java/com/kroombox/termx/
├── TermXApp.kt, MainActivity.kt   # setup BouncyCastle, container, activity
├── data/       # model (kotlinx.serialization), Vault (JSON atomik), SecretBox (Keystore), KeyManager, format kunci OpenSSH
├── os/         # OsCatalog: logo OS (Simple Icons, CC0) + deteksi OS dari /etc/os-release
├── ssh/        # SshConnection (sshj, sudo), SessionManager, TerminalSession (reconnect tanpa batas, sudo autofill), Sudo, Tmux, PortForwards (+SOCKS5), ServerStats, Prompts
├── terminal/   # TerminalEmulator (VT/xterm), TerminalView (Canvas + IME), KeyEncoder, TerminalTheme
├── service/    # SessionService (foreground service sticky + notifikasi tetap), BatteryOptimization
└── ui/         # Compose: AppNav, theme, components, screens/*
```

Unit test JVM (`app/src/test`) mencakup emulator terminal, encoder tombol, format kunci, perintah tmux, parser statistik/Docker, deteksi OS, dan sudo.

## Lisensi

TermX dirilis di bawah [lisensi MIT](LICENSE). Komponen pihak ketiga (sshj, Bouncy Castle, AndroidX, font,
logo OS) memakai lisensinya masing-masing — lihat [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
Laporan celah keamanan: lihat [SECURITY.md](SECURITY.md).

## Kontribusi

Issue dan pull request dipersilakan. Jalankan `./gradlew testDebugUnitTest assembleDebug` sebelum mengirim PR.
Build CI untuk PR dari fork ditandatangani kunci debug bawaan (secret repo tidak tersedia untuk fork).

## Catatan

- Port di bawah 1024 tidak bisa dibuka di HP tanpa root (port forwarding lokal/SOCKS).
- Opsi "Jaga koneksi di latar belakang" memakai wake lock — menambah konsumsi baterai, matikan di Pengaturan bila tidak perlu.
- Beberapa merek HP (Xiaomi, Oppo, Vivo, Huawei) punya manajer baterai tambahan: izinkan juga "Autostart"/"Tanpa batasan" untuk TermX di pengaturan HP.
- Logo OS berasal dari [Simple Icons](https://simpleicons.org) (CC0); merek milik pemiliknya masing-masing.
- Belum ada: sinkronisasi cloud antar perangkat, Mosh, agent forwarding, biometrik app lock — kandidat versi berikutnya.
