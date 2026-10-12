# TermX — SSH client for Android

<p align="center"><img src="docs/logo.png" width="128" alt="TermX"></p>

<p align="center">
  <a href="../../releases/latest">Download the latest APK</a> ·
  <a href="CHANGELOG.md">Changelog</a> ·
  <a href="LICENSE">MIT License</a> ·
  <a href="README.id.md">Bahasa Indonesia</a>
</p>

A modern SSH client for Android (inspired by the Termius workflow), written in Kotlin + Jetpack Compose.
Tap a host to open a **terminal**; choose **tmux terminal** (host menu, Sessions, or the terminal's **+**) for a
**persistent session**: the shell runs inside `tmux` on the server, so whatever you start (Claude Code, long builds,
`tail -f`, …) keeps running when the phone disconnects, the screen turns off, or the app is closed. When you
reconnect, TermX attaches to the same session.

**Sessions never end on their own.** Until you close a session, TermX keeps reconnecting (including across
Wi-Fi ↔ mobile data switches; healthy connections, e.g. over Tailscale, are kept untouched), restores sessions
after Android kills the app, and shows a persistent notification until you disconnect.

The app is available in **English** (default) and **Bahasa Indonesia** — switch in **More → Settings → Language**.

## Features

| Area | Details |
|---|---|
| **Hosts** | Groups (with custom colors), tags, search, duplicate, jump host (bastion), keep-alive, startup command |
| **Host icons** | OS logo detected automatically on first connect (Ubuntu, Debian, Arch, Fedora, Rocky, Alpine, Raspberry Pi, Proxmox, macOS, Windows, …); can be replaced by another OS logo, a built-in icon, or an emoji |
| **Terminal** | Own xterm-256color + truecolor emulator (alt screen, scroll regions, SGR mouse, bracketed paste, OSC title/clipboard), Termius-style controls (back, session pill with close, **+** new session, keyboard toggle), extra keys (`esc` `tab` `shift+tab` `ctrl` `alt`, arrows, F1–F12, customizable), pinch-zoom, selection, 5 themes, custom fonts (.ttf/.otf, ligatures) |
| **Sessions** | Session list with live terminal previews; switch, close, or open new sessions from inside the terminal; SFTP sessions too; screen history saved per session; ASCII banner on connect |
| **Persistent sessions** | `tmux` attach (50,000-line history, mouse scrolling), unlimited auto-reconnect, list/attach/kill tmux sessions on the server |
| **Background** | Sticky foreground service + wake/Wi-Fi lock, non-dismissable notification with active sessions, restore after process death / reboot / update, battery-optimization exemption prompt |
| **Autocomplete** | Command suggestions from the host's history, snippets, and common commands; commands that are not echoed (e.g. passwords) are never stored |
| **Sudo** | Per-host sudo password (or the login password); after **you** run `sudo`, the `[sudo] password for <user>` prompt is filled automatically; other prompts only show a fill button |
| **Snippets** | Manage commands with custom icons and a sudo option; run them from the terminal (⚡) |
| **SFTP & editor** | Browse, upload/download, rename, delete; full-screen **text editor** with line numbers, undo/redo, find & replace, auto-indent, symbol bar; open/save root-owned files with sudo |
| **Keychain** | Generate Ed25519 / RSA keys, import OpenSSH / PEM / PuTTY, install to `authorized_keys` in one tap, and see which servers each key is installed on (check / remove) |
| **Security** | Passwords and private keys encrypted with AES-256-GCM via Android Keystore; host key verification (TOFU, SHA-256) with change warnings |
| **Port forwarding** | Local (-L), remote (-R), dynamic/SOCKS5 (-D); auto-start |
| **Monitor** | CPU, RAM, swap, load, disks, network, top processes, Docker containers; **sudo** mode for servers that require root |
| **Backup** | Export/import configuration (without secrets) |

## Install

Download `TermX-vX.Y.Z.apk` from [Releases](../../releases/latest) and install it on Android 8.0+ (allow installs from
unknown sources). Newer releases install over older ones without losing data. Verify the download with the
`.sha256` file attached to each release.

## Versions & releases

- Versions follow [SemVer](https://semver.org/) and are recorded in [`CHANGELOG.md`](CHANGELOG.md).
- **Release**: add a CHANGELOG entry, bump `termx.versionName` / `termx.versionCode` in `gradle.properties`, then tag:
  ```sh
  git tag v0.8.0 && git push origin v0.8.0
  ```
  `release.yml` builds a signed release APK (key stored in the `TERMX_KEYSTORE_B64` / `TERMX_KEYSTORE_PASSWORD`
  repository secrets) and publishes GitHub Release `TermX vX.Y.Z` with the APK, a checksum, and notes from the
  CHANGELOG. `versionCode = X*10000 + Y*100 + Z`.
- **Development builds**: every push to `main` updates the `latest-debug` pre-release (separate app id
  `com.kroombox.termx.debug`).

## Building

Requirements: JDK 17, Android SDK 34. Versions: AGP 8.5.2, Kotlin 2.0.21, Gradle 8.9, Compose BOM 2024.09.00,
sshj 0.39.0, Bouncy Castle 1.78.1. minSdk 26 (Android 8.0).

```sh
./gradlew testDebugUnitTest assembleDebug   # APK in app/build/outputs/apk/debug/
```

Or open the project in Android Studio (Ladybug or newer) and use **Build → Build APK(s)**.

## How persistent sessions work

When you open a tmux terminal, TermX runs (via `sh -c`):

```sh
tmux has-session -t NAME || { tmux start-server \; set-option -gq history-limit 50000; tmux new-session -d -s NAME; }
exec tmux attach-session -t NAME \; set-option -q mouse on
```

If `tmux` is not installed, TermX says so and falls back to a regular shell (`sudo apt install tmux`).
Closing a tab does **not** stop the tmux session; use the menu → "Close & end tmux session" to end it.

## Project layout

```
app/src/main/java/com/kroombox/termx/
├── TermXApp.kt, MainActivity.kt   # BouncyCastle setup, container, activity
├── data/       # models (kotlinx.serialization), Vault (atomic JSON), SecretBox (Keystore), KeyManager, OpenSSH key format
├── i18n/       # Lang (English / Bahasa Indonesia), S (strings outside Compose)
├── os/         # OsCatalog: OS logos (Simple Icons, CC0) + OS detection
├── ssh/        # SshConnection, SessionManager, TerminalSession, Sudo, Autocomplete, Tmux, PortForwards, ServerStats
├── terminal/   # TerminalEmulator (VT/xterm), TerminalView (Canvas + IME), KeyEncoder, themes, fonts, banner
├── service/    # SessionService (sticky foreground service), battery optimization, restore receiver
└── ui/         # Compose: navigation, theme, components, screens
```

Translations live in `app/src/main/res/values/strings.xml` (English) and `values-in/strings.xml` (Indonesian).

## License

TermX is released under the [MIT License](LICENSE). Third-party components (sshj, Bouncy Castle, AndroidX, fonts,
OS logos) use their own licenses — see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
Security issues: see [SECURITY.md](SECURITY.md).

## Contributing

Issues and pull requests are welcome. Run `./gradlew testDebugUnitTest assembleDebug` before opening a PR.
CI builds for pull requests from forks are signed with the default debug key (repository secrets are not
available to forks). When adding UI text, add it to both `values/strings.xml` and `values-in/strings.xml`.

## Notes

- Ports below 1024 cannot be bound on a phone without root (local/SOCKS forwarding).
- "Keep connection in background" uses a wake lock and costs some battery; turn it off in Settings if not needed.
- Some brands (Xiaomi, Oppo, Vivo, Huawei) have extra battery managers: also allow "Autostart"/"No restrictions" for TermX.
- OS logos come from [Simple Icons](https://simpleicons.org) (CC0); trademarks belong to their owners.
- Not yet: cloud sync between devices, Mosh, agent forwarding, biometric app lock.
