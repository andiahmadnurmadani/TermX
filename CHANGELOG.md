# Changelog

All notable changes to TermX are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and versions follow [Semantic Versioning](https://semver.org/). Releases are built from `vX.Y.Z` tags.

## [0.10.12] - 2026-11-04
### Added
- AI agents: when a host has no Python 3, the agent screen now offers **Install Python**. Windows installs Python 3.12 for the current user only (winget, falling back to the official python.org installer, no admin needed); Linux uses the server's package manager via sudo (apt, dnf, yum, zypper, apk, pacman); macOS uses Homebrew when present.
- Windows: Python that is installed but missing from the SSH session's PATH (e.g. under `%LOCALAPPDATA%\Programs\Python`) is now found automatically; the Microsoft Store `python` alias is ignored.

### Fixed
- Windows: a Python path containing spaces is run through PowerShell so it works from both cmd and PowerShell shells.

## [0.10.11] - 2026-11-04

### Added
- AI agents on Windows hosts. TermX detects Windows (PowerShell or cmd as the SSH shell), finds Python (`python3`, `python` or `py -3`), installs its helper over SFTP, and runs every command in a form that works in any Windows shell.
- On Windows, chat workers talk over a local loopback port protected by a random token, because Python has no Unix sockets there. Messages from the app are sent as separate commands.

### Fixed
- Installing the agent helper no longer fails with "The token '&&' is not a valid statement separator" on Windows hosts.
- Checking whether a chat worker is running no longer stops it on Windows.

### Known limitations
- On Windows, sessions started outside TermX are not listed under running agents yet, and tmux, herdr and terminal mirroring aren't available.

## [0.10.10] - 2026-11-04

### Added
- Agents started in a plain terminal (outside tmux or herdr) can now receive messages from TermX. Claude Code and Codex run as usual in that terminal through a small relay, and anything you send from the phone is typed into that terminal.
- The first time you message a plain-terminal session, TermX adds a short `claude`/`codex` shell function to `~/.bashrc` or `~/.zshrc`, between `# >>> termx >>>` markers. Delete the block to turn it off. A session that was already running needs one restart in a new terminal, for example `claude --resume <id>`, and the chat follows it.

## [0.10.9] - 2026-11-04

### Changed
- Messages sent from a mirrored chat always go to the agent's own terminal. TermX no longer starts a separate session when the terminal agent has exited.
- If the agent has exited but its tmux or herdr pane is still open, TermX reopens the same session there (for example `claude --resume <id>`) and types the message into it.
- If the original pane is closed, the same session is reopened in a new tmux session you can attach to, so the conversation stays in a real terminal.
- If you reopen the session yourself in another tmux or herdr pane, the chat follows it automatically.
- Sessions in a plain terminal (outside tmux or herdr) now show a clear message instead of forking a hidden session.

### Fixed
- Changing the mode in a mirrored chat no longer fails with an internal error.

## [0.10.8] - 2026-11-04

### Fixed
- Followed terminal sessions no longer turn into several chats. Reopening a running Claude session (after `/compact`, `/clear`, or a `claude --resume` restart) now reuses its chat instead of creating a new one. Duplicate chats made by earlier versions are hidden the next time the session is opened.

## [0.10.7] - 2026-11-04

### Added
- Hermes setup: custom endpoints, the same flow as `hermes model`. Enter a base URL, an optional key, the API mode, a display name and a context length. The app detects the endpoint's models and suggests a working URL (e.g. adding `/v1`). Saved endpoints show up in the provider list.
- Hermes setup: sign in to OAuth providers (Anthropic, Nous, OpenAI Codex, xAI, Qwen, MiniMax) from the app. TermX runs `hermes auth add --type oauth` on the server and shows the sign-in link and code with open/copy buttons. A pasted code is sent over stdin.
- Hermes model list: shows all models, not just the first 300. It merges Hermes' catalog with the provider's live `/models` list when a key is available, and shows the model count.

### Fixed
- Hermes setup: scrolling a list now scrolls the list instead of dragging the sheet, and there's a close button.
- Host cards: agent and Tailscale logos peek above the top-right edge instead of being hidden behind the card.
- Running agents are now detected on every host with a live connection, and the last result is kept for a while.

## [0.10.6] - 2026-11-04

### Added
- Hermes setup wizard: pick a provider, paste the API key, and choose a model from the app. The setup uses Hermes' own config API on the server; the key travels over stdin and is never sent back.
- Context usage is now a ring icon. Tap it for token details and a one-tap "Compact conversation".
- Claude sessions followed from tmux/herdr can switch models (sent as `/model`).
- Host cards show the logos of agents running on connected hosts. Tailscale hosts get the Tailscale logo, and the redundant OS label is gone.
- New loading animations: a radar scan with real agent logos while detecting agents, and a flowing bar in place of plain progress bars.
- Always-visible arrow keys in the terminal key bar. They fire on touch, with haptic feedback and auto-repeat while held.
- Real Google Antigravity logo.

### Changed
- Reopening a conversation shows it instantly at the latest message, with no loading screen.
- Autocomplete suggestions float over the terminal, away from the cursor line, instead of taking a fixed empty strip.
- Conversations started from TermX appear under "Running on server".

### Fixed
- Deleted conversations no longer come back. Delete removes the row immediately, waits for the worker to stop (force-killing it if needed), and reports an error if the folder can't be removed.
- Changing the mode in a mirrored session no longer types it as a model.

## [0.10.5] - 2026-11-04

### Changed
- Opening a chat now lands on the newest message right away. Only the last 20 prompts load at first. A
  "Load earlier prompts" button at the top brings in older history when you want it.
- New loading screen while a conversation opens: the agent's logo inside a spinning ring in its brand color, over a
  shimmering preview of the conversation. It stays until the history has arrived, so the list no longer fills in
  while you watch.
- Chat look: the agent's name and logo above each answer, icons for model, tokens, cost and duration under a turn,
  icons on notes and errors, a compact "Live" chip for tmux/herdr sessions, and a terminal button in the top bar.
- Terminal locations such as "pts/0" are no longer shown on sessions and chats.

### Fixed
- "Failed to install the agent helper (chmod: cannot access agentd.py.tmp)" when opening the AI agents screen
  right after an update. Several requests installed the helper at the same time and replaced each other's
  temporary file. Only one install runs now, and each uses its own temporary file.
- Typing lag in the terminal. Keystrokes are sent before command tracking runs. The suggestion bar now keeps a
  fixed height, so the terminal no longer resizes (and the shell redraws) as suggestions appear and disappear.

## [0.10.4] - 2026-11-04

### Added
- A "jump to latest message" button in agent chats. It appears once you scroll up and takes you straight back to
  the newest message.
- A sound when every prompt finishes. If you're watching the chat, the phone plays the notification tone. Otherwise
  you get a notification with sound that names the chat. Approval requests still notify as before.
- Antigravity CLI (`agy`) in the agent list. It runs in a terminal (tmux) and is shown in TermX like other
  terminal agents.
- Markdown preview for `.md` files in the file editor. It opens in preview for existing files, and you can switch
  to editing with one tap.

### Changed
- Opening a running session now loads its whole conversation from the agent's transcript, not just the last part.
  Long tool output in older turns is shortened so the history arrives quickly. Sessions opened before this version
  are imported again in full the next time you open them.
- The AI agents screen loads faster. Agent versions are cached on the server per binary, so detection drops from
  seconds to about 0.1 s. Accounts and usage appear immediately from the last result and refresh in the background.
- The agent list now covers Claude Code, opencode, Hermes Agent and Antigravity.
- Better Markdown rendering: real table grids with column alignment, task-list checkboxes, nested bullets, quotes
  with formatting, headings with dividers, escaped characters, `<br>`, images shown as links, and YAML front
  matter shown as a code block.

## [0.10.3] - 2026-11-04

### Added
- "Show in a terminal too" when starting a chat. The agent runs in its own tmux session on the server, so every
  message you send from TermX also appears in that terminal, and you can attach from a computer with `tmux attach`.
  On by default for Claude Code and Codex. Deleting the chat closes that tmux session.
- Usage and accounts on the AI agents screen, per provider: how Claude Code is connected (plan via Claude.ai,
  API key, or a cloud provider) with the 5-hour and weekly limits left, Codex sign-in and limits from its latest
  session, opencode totals, and what TermX chats cost. Each card says where its numbers come from. Remaining API
  credit is not available from Claude Code, and the card says so instead of guessing.

### Changed
- Redesigned agents screen: a row of installed agents that lights up while one is working, running sessions as
  cards with a clear status, and agent logos in the chat header.
- Opening a conversation jumps to the latest message and keeps following new ones while you are at the bottom.
- Approval prompts in tmux/herdr sessions pick the right choice by reading the options on screen (for example
  Claude Code's "trust this folder" dialog, where the default is "No").

### Fixed
- Deleting a chat sometimes left its folder behind on the server.

## [0.10.2] - 2026-11-04

### Changed
- Agents running on the server now open in the chat view instead of a terminal. Tap a session under "Running on
  the server" to see its conversation (read from the agent's own transcript for Claude Code and Codex) and follow
  it live. Messages you send are typed into the tmux or herdr session, so the terminal and the phone stay in sync.
  Approval prompts show up as a card with Allow / Always / Deny. Long-press a session to open it in a terminal.
- Every running session can be opened, including ones in other terminals. Those are shown read-only, and sending a
  message continues the conversation in TermX.
- The server helper is updated to version 3.

### Fixed
- Sessions under "Running on the server" that could not be tapped.

## [0.10.1] - 2026-11-04

### Added
- Agents now show their real logos (Claude Code, Codex, opencode, Hermes, Gemini CLI, Qwen Code, goose). Agents
  without an official mark (pi, oh-my-pi, aider, crush) keep a monogram.
- "Running on the server" lists coding agents that are already running on the host, also when they were started
  outside TermX in tmux or herdr. Tap a tmux entry to attach to that exact pane, or a herdr entry to open it with
  `herdr agent attach` (long-press to take it over from other screens). Agents in other terminals are shown for
  information with their folder and start time.

### Changed
- The server helper (`~/.termx/agentd.py`) is updated to version 2 and reinstalled automatically.

## [0.10.0] - 2026-11-04

### Added
- AI agents. Open a host's menu and pick AI agents to chat with the coding agents installed on that server:
  Claude Code, Codex, opencode, Hermes, pi and oh-my-pi get a full chat view; other agents (Gemini CLI, Qwen,
  aider, goose, crush) open in a terminal tab. The agent runs on the server with its own login or API key;
  TermX only sends your messages and never sees credentials.
- The chat view streams replies with Markdown, code blocks and tables, shows tool calls (commands, file reads,
  edits as diffs, to-do lists) and asks for approval with Deny / Always / Allow. Plans can be approved before
  the agent starts.
- Switch model and permission mode from the composer, use the agent's `/` commands, and see context use,
  tokens, cost per turn and, for Claude, the 5-hour and weekly plan limits.
- Conversations keep running on the server when you leave the app or lose signal. TermX reconnects and picks up
  where it stopped, and sends a notification when an agent needs approval or finishes.
- "Log in in terminal" and "Continue in terminal" open the agent in its own tmux window in the project folder.
- Tailscale hint: hosts on a Tailscale address (100.64.0.0/10 or `*.ts.net`) get a Tailscale tag, and if the
  Tailscale VPN is off on the phone TermX says so before connecting, with a button to open Tailscale.

### Requirements
- AI agents need `python3` on the server. TermX installs a small helper at `~/.termx/agentd.py`.

## [0.9.0] - 2026-11-03

### Added
- In-app updates. TermX checks GitHub Releases every few hours and shows a notification when a new version is out.
  The update dialog shows the release notes, downloads the APK, checks its SHA-256 checksum and signing key, and
  opens the system installer. You can also check by hand under More > About, and turn off the automatic check there.

### Fixed
- Typing no longer lags for seconds now and then. Saving the terminal history (every 5 seconds) used to hold the
  terminal lock while it built the whole scrollback as text, which froze drawing and new output. Lines are now copied
  quickly and the text is built outside the lock.
- Keystrokes are sent right away: the SSH socket now uses `TCP_NODELAY` (like OpenSSH), so small packets are not
  held back waiting for the previous one to be acknowledged, which was noticeable on mobile networks.
- Pressing Enter no longer writes the command history to disk on the UI thread.
- The suggestion bar is its own composable, so a keystroke only redraws that bar instead of the whole terminal screen.

### Changed
- Host cards use a neutral border instead of the group color, and host avatars and tags no longer have colored
  outlines or colored text. The group color stays on the group heading.

## [0.8.3] - 2026-10-12

### Fixed
- The bottom navigation bar looks like before again: tab labels stay on one line, and the language is applied as a
  locale-only override, so screen size, density, font scale, and night mode keep following the system.

## [0.8.2] - 2026-10-12

### Security
- `su` password autofill now requires the exact `Password:` prompt that `su` prints (after you ran `su` yourself);
  other password prompts are never filled automatically.

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
