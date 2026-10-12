# Changelog

Semua perubahan penting TermX dicatat di sini. Format mengikuti [Keep a Changelog](https://keepachangelog.com/id-ID/1.1.0/),
versi mengikuti [Semantic Versioning](https://semver.org/lang/id/). Rilis dibuat dari tag `vX.Y.Z`.

## [0.3.3] - 2026-10-12

### Diubah
- Keyboard terminal tidak lagi memakai mode password. Default **Standar** (seperti Termux): keyboard HP tampil normal
  dan setiap huruf langsung terkirim.
- Pilihan jenis keyboard di Pengaturan: Standar, Teks dengan saran (autocorrect), atau Tanpa saran (mode lama).

### Diperbaiki
- Jeda saat mengetik: teks yang sedang disusun keyboard kini langsung dikirim (perubahan dari saran/autocorrect
  dikirim sebagai selisih), bukan menunggu kata selesai.

## [0.3.2] - 2026-10-12

### Diubah
- Logo baru: jendela terminal `>_` dengan rak server, bergradien sian-biru (ikon launcher, ikon bertema, notifikasi, halaman Tentang).
- Terminal yang dibuka baru dari host selalu kosong. Riwayat layar hanya ditampilkan saat membuka lagi sesi dari tab
  **Sesi** (Baru ditutup / sesi tmux) atau saat tab dipulihkan otomatis.
- Setiap tab terminal biasa punya riwayat sendiri, sehingga membuka terminal baru tidak menimpa riwayat sesi lama.
- "Putuskan semua" memasukkan tab ke daftar Baru ditutup agar riwayatnya tetap bisa dibuka.

## [0.3.1] - 2026-10-12

### Diubah
- Ikon aplikasi: prompt `>_` diperkecil agar proporsional di launcher.
- Tombol **Putuskan semua…** di notifikasi kini meminta konfirmasi di aplikasi, supaya sesi tidak terhapus karena salah ketuk.

### Diperbaiki
- Sesi tetap berjalan di latar belakang setelah aplikasi ditutup dari daftar aplikasi terbaru: service tidak ikut
  berhenti, notifikasi tetap tampil, dan bila HP tetap mematikan prosesnya, service dijadwalkan hidup lagi dan
  semua tab dipulihkan.
- Sesi aktif dipulihkan otomatis setelah HP dinyalakan ulang atau aplikasi diperbarui.

## [0.3.0] - 2026-10-12

### Ditambahkan
- Banner ASCII **TermX** di atas terminal saat tersambung, berisi user@host, OS, mode (biasa/tmux), dan waktu.
- Tab **Sesi** berupa daftar host yang bisa dibuka-tutup: tab terbuka, sesi tmux di server (dimuat otomatis),
  dan tab yang baru ditutup per host — tidak perlu membuat sesi secara manual.
- Snippet dijalankan dari dalam terminal: tombol ⚡ di bar atas, di bar tombol bawah, dan item **Snippet** di menu.
- Tombol **Deteksi ulang OS** di editor host.
- Riwayat layar terminal disimpan per sesi dan dimuat lagi saat tab dibuka kembali, setelah aplikasi ditutup,
  atau setelah proses dimatikan Android (warna ikut tersimpan).
- Daftar **Baru ditutup** di tab Sesi untuk membuka lagi tab yang ditutup beserta riwayatnya.
- Riwayat tmux di server diperbesar menjadi 50.000 baris untuk sesi baru.
- Rilis resmi bertanda tangan dari tag Git, dengan CHANGELOG dan checksum SHA-256.

### Diubah
- Default konek menjadi **terminal biasa**; tmux tetap tersedia lewat menu host ("Terminal tmux"), tab Sesi,
  dan menu terminal ("Tab baru di tmux"). Host lama dimigrasikan sekali ke default baru.
- Tab Snippet hanya untuk mengelola daftar (eksekusi dari terminal).
- OS server dideteksi sekali saat koneksi pertama lalu disimpan; tidak dideteksi ulang setiap membuka aplikasi.
- Logo baru bergaya terminal: prompt `>_` bergradien sian-hijau.
- Pemilih ikon host dan snippet bisa dilipat (Logo OS / Ikon), dan di editor host baru muncul saat menekan "Ganti ikon".
- Logo dan teks "TermX" di dalam aplikasi hanya ditampilkan di halaman Tentang.

## [0.2.0] - 2026-10-12

### Ditambahkan
- Nama aplikasi TermX (sebelumnya Konsol) dengan logo baru.
- Ikon host otomatis sesuai OS server (31 logo), bisa diganti logo OS, ikon bawaan, atau emoji.
- Snippet dengan ikon pilihan dan opsi sudo (password diketik otomatis saat prompt sudo muncul).
- Monitor dengan mode sudo, petunjuk bila Docker butuh sudo, dan daftar container.
- Sesi tidak putus sampai pengguna memutuskannya: sambung ulang tanpa batas, pemulihan tab, notifikasi tetap.

## [0.1.0] - 2026-10-12

### Ditambahkan
- Rilis awal: terminal SSH, tmux persisten, SFTP, port forwarding, keychain, snippet, monitor, font kustom.
