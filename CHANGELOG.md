# Changelog

All notable changes to TermX are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and versions follow [Semantic Versioning](https://semver.org/). Releases are built from `vX.Y.Z` tags.

## [0.8.1] - 2026-10-12

### Added
- **"SSH password is also the root/sudo password"** toggle right under the password field (on by default). The
  password typed at login is remembered in memory (never stored unless you choose to save it) and used for sudo, so
  you don't type it twice. It also fills the `Password:` prompt right after **you** run `su` / `su -`.

## [0.8.0] - 2026-10-12

### Added
- **English and Bahasa Indonesia.** English is the default; switch in **Settings → Language** (English, Bahasa
  Indonesia, or system default). Notifications, connection messages, and errors follow the selected language.

### Changed
- Documentation in English (README, CHANGELOG, SECURITY), with an Indonesian README (`README.id.md`).
- Default snippets have English names.

## [0.7.1] - 2026-10-12

### Fixed
- The "SFTP" title no longer wraps letter by letter: the SFTP top bar keeps Refresh and New file; Parent folder,
  New folder, hidden files, and Close SFTP session moved to the ⋮ menu.

### Project
- Repository made public under the [MIT License](LICENSE), with [SECURITY.md](SECURITY.md) and
  [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
- The debug signing key is no longer stored in the repository (moved to a CI secret and rotated). Uninstall old
  debug builds once before installing a new debug build; APKs from **Releases** are not affected.

## [0.7.0] - 2026-10-12

### Added
- **SFTP sessions** (Termius-style): opening SFTP creates a session shown in **Sessions** (with a folder preview),
  in the terminal's session panel, and in the notification. It stays open (and is restored if Android kills the app)
  until closed with ✕. The **New session** panel has an SFTP button per host.

### Changed
- The connect banner shows the host name, OS, and `SSH · user@host:port` (or the tmux session).

## [0.6.1] - 2026-10-12

### Changed
- Termius-style terminal layout: no top bar; at the bottom **‹** back, the active session pill (host icon, name,
  **✕** close, tap for the session list), **+** new session, and a **keyboard** toggle.
- Key row: snippet, sudo password, menu (⋯), and file (SFTP) icons, followed by `esc` `tab` `shift+tab` `ctrl` `alt`
  and arrows. New `STAB` (shift+tab) key token.

### Fixed
- **Connections are no longer dropped when the network changes.** Previously every network change (including VPNs
  such as Tailscale, whose address stays the same) dropped all connections. Each connection is now probed with an SSH
  keepalive; only those that do not answer within 8 seconds are reconnected.

## [0.6.0] - 2026-10-12

### Added
- Full-screen **text editor** for files on the server: line numbers, terminal font, toggleable wrap, **undo/redo**,
  **find & replace**, **auto-indent**, a symbol bar above the keyboard, font size, unsaved-changes marker and exit
  confirmation. Root-owned files can be **opened/saved with sudo** when permission is denied.
- The editor opens from SFTP (tap a text file, **View / edit**, or **New file**) and from the terminal menu (**Edit file…**).

### Fixed
- SFTP stays in the last folder when returning from another screen.

## [0.5.0] - 2026-10-12

### Changed
- **Sessions** shows only connected sessions, each with a live **terminal preview**.
- Terminal: **+** opens a new session to any host without leaving, **✕** closes the current session.

### Added
- **Keychain** shows the servers each key is installed on (recorded on install, check, or successful key login),
  hosts using the key, and **Check** / **Remove** actions.

## [0.4.1] - 2026-10-12

### Security
- Sudo password autofill now only runs right after **you** run `sudo` (Enter on a line containing `sudo` within the
  last 15 seconds, or a sudo snippet). Text that merely looks like a sudo prompt no longer triggers typing the password.

## [0.4.0] - 2026-10-12

### Added
- Termius-style session management, **command autocomplete**, **sudo password autofill**, editable **group colors**.

### Changed
- Logo returns to the `>_` prompt in the app's theme colors.

## [0.3.3] - 2026-10-12

### Changed
- The terminal keyboard no longer uses the password keyboard type; default **Standard** mode sends every key
  immediately. Keyboard type selectable in Settings.

## [0.3.2] - 2026-10-12

### Changed
- New terminals opened from a host start empty; screen history is shown when reopening a session from Sessions.

## [0.3.1] - 2026-10-12

### Changed
- Smaller `>_` launcher icon; **Disconnect all…** in the notification asks for confirmation.

### Fixed
- Sessions keep running after the app is swiped away; restored after reboot or update.

## [0.3.0] - 2026-10-12

### Added
- ASCII banner on connect, per-host Sessions view, snippets run from the terminal, persistent screen history,
  signed releases from Git tags.

### Changed
- Plain terminal is the default; tmux is optional. OS is detected once on first connect.

## [0.2.0] - 2026-10-12

### Added
- App renamed to TermX with a new logo; OS-based host icons; snippets with icons and sudo; monitor sudo mode;
  sessions that stay connected until the user disconnects.

## [0.1.0] - 2026-10-12

### Added
- Initial release: SSH terminal, persistent tmux, SFTP, port forwarding, keychain, snippets, monitor, custom fonts.
