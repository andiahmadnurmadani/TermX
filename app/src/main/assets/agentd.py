#!/usr/bin/env python3
"""TermX agentd: jembatan kecil antara aplikasi TermX dan agent AI di server.

Dipasang otomatis oleh TermX ke ~/.termx/agentd.py. Hanya pustaka standar Python 3.8+.

Agent (Claude Code, Codex, ...) dijalankan oleh proses "worker" yang lepas dari sesi SSH, jadi
pekerjaan tetap jalan saat HP terputus. Semua kejadian ditulis ke events.jsonl dengan nomor urut;
aplikasi menyambung lewat `agentd.py attach <id> <dari>` dan hanya menerima yang belum dimilikinya.

Login / API key tiap agent tidak pernah disentuh: agent memakai konfigurasi miliknya sendiri di server.
"""
import json
import re
import shlex
import os
import shutil
import signal
import socket
import subprocess
import sys
import threading
import time
import uuid

VERSION = 10
ROOT = os.path.expanduser("~/.termx/agents")
IDLE_EXIT_SEC = 30 * 60          # worker berhenti bila tidak ada yang menyambung & tidak bekerja selama ini
FLUSH_SEC = 0.05                  # penggabungan potongan teks
MAX_OUT = 6000                    # batas output tool yang dikirim ke HP

# Folder bin tambahan yang sering dipakai pemasang agent (tidak selalu ada di PATH sesi non-interaktif).
EXTRA_PATHS = [
    "~/.local/bin", "~/.npm-global/bin", "~/.bun/bin", "~/.cargo/bin", "~/.opencode/bin",
    "~/.local/opt/agents/node/bin", "~/.local/opt/agents/npm/bin", "~/.local/opt/agents/uv-bin",
    "~/go/bin", "/usr/local/bin",
]


def env_path():
    parts = [os.path.expanduser(p) for p in EXTRA_PATHS]
    nvm = os.path.expanduser("~/.nvm/versions/node")
    if os.path.isdir(nvm):
        for v in sorted(os.listdir(nvm), reverse=True):
            parts.append(os.path.join(nvm, v, "bin"))
    return os.pathsep.join(parts + [os.environ.get("PATH", "")])


os.environ["PATH"] = env_path()


def now_ms():
    return int(time.time() * 1000)


def jdump(o):
    return json.dumps(o, ensure_ascii=False, separators=(",", ":"))


def clip(s, n=MAX_OUT):
    if s is None:
        return ""
    if not isinstance(s, str):
        s = jdump(s)
    return s if len(s) <= n else s[: n // 2] + "\n…\n" + s[-n // 2:]


CTX_CACHE = os.path.expanduser("~/.termx/context.json")


def _ctx_used(u):
    """Konteks yang terpakai = seluruh token input pesan terakhir (termasuk cache)."""
    if not isinstance(u, dict):
        return 0
    return (u.get("input_tokens") or 0) + (u.get("cache_creation_input_tokens") or 0) + (u.get("cache_read_input_tokens") or 0)


def _ctx_window(model):
    """Ukuran jendela konteks yang pernah dilaporkan Claude Code untuk model ini (0 = belum diketahui)."""
    try:
        with open(CTX_CACHE, encoding="utf-8") as f:
            return int((json.load(f) or {}).get(model or "", 0) or 0)
    except Exception:
        return 0


def _ctx_remember(model, window):
    if not model or not window:
        return
    try:
        try:
            with open(CTX_CACHE, encoding="utf-8") as f:
                d = json.load(f) or {}
        except Exception:
            d = {}
        if d.get(model) != window:
            d[model] = window
            tmp = CTX_CACHE + ".%d" % os.getpid()
            with open(tmp, "w", encoding="utf-8") as f:
                json.dump(d, f)
            os.replace(tmp, CTX_CACHE)
    except OSError:
        pass


def version_of(cmd):
    try:
        out = subprocess.run([cmd, "--version"], capture_output=True, text=True, timeout=15)
        line = (out.stdout or out.stderr).strip().splitlines()
        return line[0][:80] if line else ""
    except Exception:
        return ""


# ---------------------------------------------------------------- katalog agent

def first_bin(*names):
    for n in names:
        p = shutil.which(n)
        if p:
            return p
    return None


# kind "chat" = adapter terstruktur; kind "terminal" = dijalankan di terminal (tmux) apa adanya.
CATALOG = [
    {"id": "claude", "name": "Claude Code", "bins": ["claude"],
     "models": ["opus", "sonnet", "haiku", "fable"],
     "modes": ["manual", "acceptEdits", "plan", "auto"], "login": "claude  →  /login", "login_cmd": "{bin}", "resume_cmd": "{bin} --resume {session}"},
    {"id": "opencode", "name": "opencode", "bins": ["opencode"],
     "models": [], "modes": [], "login": "opencode auth login", "login_cmd": "{bin} auth login", "resume_cmd": "{bin} --session {session}"},
    {"id": "hermes", "name": "Hermes Agent", "bins": ["hermes"],
     "models": [], "modes": [], "login": "hermes setup", "login_cmd": "{bin} setup", "resume_cmd": "{bin}"},
    # Antigravity CLI (Google): binary "agy", dijalankan di terminal (tmux) lalu dicerminkan.
    {"id": "antigravity", "name": "Antigravity", "bins": ["agy"],
     "models": [], "modes": [], "login": "agy", "login_cmd": "{bin}", "resume_cmd": "{bin}"},
]


DETECT_CACHE = os.path.expanduser("~/.termx/versions.json")
USAGE_CACHE = os.path.expanduser("~/.termx/usage.json")


def _load_json(path):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return None


def _save_json(path, obj):
    try:
        tmp = path + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            f.write(jdump(obj))
        os.replace(tmp, path)
    except OSError:
        pass


def cmd_detect(_args):
    # "--version" tiap agent bisa makan detik: hasilnya disimpan per (path, mtime) binary.
    cache = _load_json(DETECT_CACHE) or {}
    bins = [(a, first_bin(*a["bins"])) for a in CATALOG]
    bins = [(a, p) for a, p in bins if p]
    vers, todo = {}, []
    for a, p in bins:
        try:
            key = "%s|%d" % (os.path.realpath(p), int(os.path.getmtime(os.path.realpath(p))))
        except OSError:
            key = p
        if key in cache:
            vers[p] = cache[key]
        else:
            todo.append((p, key))

    def ver(p, key):
        vers[p] = cache[key] = version_of(p)

    ths = [threading.Thread(target=ver, args=t, daemon=True) for t in todo]
    for t in ths:
        t.start()
    for t in ths:
        t.join(20)
    if todo:
        _save_json(DETECT_CACHE, cache)
    found = []
    for a, p in bins:
        kind = "chat" if a["id"] in ADAPTERS else "terminal"
        found.append({
            "id": a["id"], "name": a["name"], "bin": p, "version": vers.get(p, ""), "kind": kind,
            "models": a["models"], "modes": a["modes"], "login": a["login"],
            "login_cmd": a.get("login_cmd", "{bin}").replace("{bin}", p),
            "resume_cmd": a.get("resume_cmd", "{bin}").replace("{bin}", p),
        })
    print(jdump({"v": VERSION, "agents": found, "tmux": bool(shutil.which("tmux"))}))


# ---------------------------------------------------------------- penyimpanan chat

def chat_dir(cid):
    if not cid or "/" in cid or cid.startswith("."):
        raise SystemExit("invalid chat id")
    return os.path.join(ROOT, cid)


def read_meta(cid):
    try:
        with open(os.path.join(chat_dir(cid), "meta.json"), encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return None


def write_meta(cid, meta):
    p = os.path.join(chat_dir(cid), "meta.json")
    tmp = p + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False)
    os.replace(tmp, p)


def worker_alive(cid):
    try:
        with open(os.path.join(chat_dir(cid), "worker.pid")) as f:
            pid = int(f.read().strip())
        os.kill(pid, 0)
        return True
    except Exception:
        return False


def cmd_list(_args):
    out = []
    if os.path.isdir(ROOT):
        for cid in os.listdir(ROOT):
            m = read_meta(cid)
            if m and not m.get("superseded"):
                m["alive"] = worker_alive(cid)
                out.append(m)
    out.sort(key=lambda m: m.get("updated", 0), reverse=True)
    print(jdump({"chats": out}))


def cmd_new(args):
    # new <agent> <cwd> [model] [mode] [tmux]
    agent, cwd = args[0], os.path.expanduser(args[1] if len(args) > 1 else "~")
    in_tmux = len(args) > 4 and args[4] == "tmux"
    if agent not in ADAPTERS and not in_tmux:
        raise SystemExit("unsupported agent: " + agent)
    if not os.path.isdir(cwd):
        raise SystemExit("folder not found: " + cwd)
    mirror = _new_in_tmux(agent, os.path.abspath(cwd), args[2] if len(args) > 2 else "", args[3] if len(args) > 3 else "") if in_tmux else None
    cid = time.strftime("%Y%m%d-%H%M%S-") + uuid.uuid4().hex[:6]
    os.makedirs(chat_dir(cid), mode=0o700)
    meta = {
        "id": cid, "agent": agent, "cwd": os.path.abspath(cwd), "title": "",
        "model": args[2] if len(args) > 2 and args[2] else "", "mode": args[3] if len(args) > 3 and args[3] else "",
        "session": "", "created": now_ms(), "updated": now_ms(), "status": "idle",
        "cost": 0.0, "tokens_in": 0, "tokens_out": 0,
    }
    if mirror:
        meta["mirror"] = mirror
    write_meta(cid, meta)
    spawn_worker(cid)
    print(jdump({"id": cid}))


def cmd_rm(args):
    cid = args[0]
    m = read_meta(cid) or {}
    owned = (m.get("mirror") or {}).get("owned")
    stop_worker(cid)
    for _ in range(50):  # tunggu worker selesai menulis, agar folder benar-benar terhapus
        if not worker_alive(cid):
            break
        time.sleep(0.1)
    if worker_alive(cid):  # masih bandel: paksa berhenti
        stop_worker(cid, signal.SIGKILL)
        time.sleep(0.2)
    if owned and shutil.which("tmux"):  # sesi tmux yang dibuat TermX untuk chat ini
        subprocess.run(["tmux", "kill-session", "-t", "=" + owned], capture_output=True)
    for _ in range(5):
        shutil.rmtree(chat_dir(cid), ignore_errors=True)
        if not os.path.exists(chat_dir(cid)):
            break
        time.sleep(0.2)
    if os.path.exists(chat_dir(cid)):
        raise SystemExit("could not delete the conversation")
    print("{}")


def cmd_stop(args):
    stop_worker(args[0])
    print("{}")


def cmd_rename(args):
    m = read_meta(args[0])
    if m:
        m["title"] = " ".join(args[1:])[:120]
        write_meta(args[0], m)
    print("{}")


def stop_worker(cid, sig=signal.SIGTERM):
    try:
        with open(os.path.join(chat_dir(cid), "worker.pid")) as f:
            os.killpg(int(f.read().strip()), sig)
    except Exception:
        pass


def spawn_worker(cid):
    if worker_alive(cid):
        return
    log = open(os.path.join(chat_dir(cid), "worker.log"), "ab")
    subprocess.Popen(
        [sys.executable, os.path.abspath(__file__), "_worker", cid],
        stdin=subprocess.DEVNULL, stdout=log, stderr=log, start_new_session=True, close_fds=True,
    )
    for _ in range(100):  # tunggu soket siap (maks ~5 detik)
        if os.path.exists(os.path.join(chat_dir(cid), "in.sock")) and worker_alive(cid):
            return
        time.sleep(0.05)


# ---------------------------------------------------------------- attach (dipakai aplikasi)

def cmd_attach(args):
    """Kirim event mulai nomor <dari>, lalu ikuti terus. Baris JSON di stdin diteruskan ke worker."""
    # attach <id> <dari> [giliran]: bila <dari> 0 dan [giliran] > 0, hanya N prompt terakhir yang dikirim
    # (plus event status terakhir sebelumnya), didahului {"t":"older","turns":k} bila ada riwayat yang dilewati.
    cid, start = args[0], int(args[1]) if len(args) > 1 else 0
    tail = int(args[2]) if len(args) > 2 and args[2].isdigit() else 0
    d = chat_dir(cid)
    if not os.path.isdir(d):
        print(jdump({"t": "gone"}), flush=True)
        return
    spawn_worker(cid)
    prelude = []
    if start == 0 and tail > 0:
        start, prelude = _tail_start(os.path.join(d, "events.jsonl"), tail)
    stop = threading.Event()

    def pump_stdin():
        for line in sys.stdin:
            line = line.strip()
            if not line:
                continue
            if line == "ping":
                continue
            for attempt in range(2):
                try:
                    spawn_worker(cid)
                    s = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
                    s.connect(os.path.join(d, "in.sock"))
                    s.sendall((line + "\n").encode())
                    s.close()
                    break
                except Exception:
                    time.sleep(0.2)
        stop.set()

    threading.Thread(target=pump_stdin, daemon=True).start()
    path = os.path.join(d, "events.jsonl")
    while not os.path.exists(path) and not stop.is_set():
        time.sleep(0.05)
    with open(path, encoding="utf-8") as f:
        out = sys.stdout
        for line in prelude:
            out.write(line)
        skipped = 0
        buf = ""
        last_data = time.time()
        synced = False
        while not stop.is_set():
            chunk = f.readline()
            if chunk:
                if not chunk.endswith("\n"):  # baris belum selesai ditulis worker
                    buf += chunk
                    continue
                chunk, buf = buf + chunk, ""
                if skipped < start:  # lewati yang sudah dimiliki aplikasi
                    skipped += 1
                    continue
                out.write(chunk)
                last_data = time.time()
                continue  # kumpulkan yang tersedia, flush sekali
            try:
                if not synced and not buf:  # riwayat awal sudah terkirim semua
                    out.write('{"t":"synced"}\n')
                    synced = True
                out.flush()
            except BrokenPipeError:
                return
            idle = time.time() - last_data
            time.sleep(0.02 if idle < 3 else 0.15)
            if idle > 20:  # detak agar aplikasi tahu sambungan masih hidup
                try:
                    out.write('{"t":"hb"}\n')
                    out.flush()
                except BrokenPipeError:
                    return
                last_data = time.time()


STATE_EVENTS = ("init", "model", "mode", "models", "modes", "commands", "limits", "context", "mirror_end")


def _tail_start(path, turns):
    """Nomor baris awal untuk N prompt terakhir + baris status terakhir sebelum itu + penanda riwayat lama."""
    try:
        with open(path, "rb") as f:
            lines = f.readlines()
    except OSError:
        return 0, []
    users = [i for i, l in enumerate(lines) if b'"t":"user"' in l or b'"t": "user"' in l]
    if len(users) <= turns:
        return 0, []
    cut = users[-turns]
    last = {}
    for i in range(cut):
        l = lines[i]
        if len(l) > 20000:
            continue
        m = re.search(rb'"t":\s*"([a-z_]+)"', l)
        if m and m.group(1).decode() in STATE_EVENTS:
            last[m.group(1)] = i
    prelude = [lines[i].decode("utf-8", "replace") for i in sorted(last.values())]
    prelude.append(jdump({"t": "older", "turns": len(users) - turns}) + "\n")
    return cut, prelude


# ---------------------------------------------------------------- worker

class Worker:
    def __init__(self, cid):
        self.cid = cid
        self.dir = chat_dir(cid)
        self.meta = read_meta(cid)
        self.lock = threading.Lock()
        self.events = open(os.path.join(self.dir, "events.jsonl"), "a", encoding="utf-8")
        with open(os.path.join(self.dir, "events.jsonl"), encoding="utf-8") as f:
            self.seq = sum(1 for _ in f)
        self.busy = False
        self.last_activity = time.time()
        self.adapter = MirrorAdapter(self) if self.meta.get("mirror") else ADAPTERS[self.meta["agent"]](self)

    def emit(self, ev):
        with self.lock:
            ev["n"] = self.seq
            ev.setdefault("at", now_ms())
            self.seq += 1
            self.events.write(jdump(ev) + "\n")
            self.events.flush()
            self.last_activity = time.time()

    def save_meta(self, **kw):
        with self.lock:
            self.meta.update(kw)
            self.meta["updated"] = now_ms()
            write_meta(self.cid, self.meta)

    def set_busy(self, busy):
        self.busy = busy
        self.save_meta(status="working" if busy else "idle")
        self.emit({"t": "status", "s": "working" if busy else "idle"})

    def handle(self, cmd):
        c = cmd.get("c")
        if c == "send":
            text = (cmd.get("text") or "").strip()
            if not text:
                return
            if not self.meta.get("title"):
                self.save_meta(title=text.splitlines()[0][:80])
            self.emit({"t": "user", "text": text})
            self.set_busy(True)
            self.adapter.send(text)
        elif c == "perm":
            self.adapter.permission(cmd.get("rid"), bool(cmd.get("allow")), bool(cmd.get("always")))
        elif c == "model":
            self.adapter.set_model(cmd.get("model") or "")
        elif c == "mode":
            self.adapter.set_mode(cmd.get("mode") or "")
        elif c == "stop":
            self.adapter.interrupt()

    def serve(self):
        sp = os.path.join(self.dir, "in.sock")
        try:
            os.unlink(sp)
        except FileNotFoundError:
            pass
        srv = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        srv.bind(sp)
        os.chmod(sp, 0o600)
        srv.listen(8)
        srv.settimeout(1.0)
        with open(os.path.join(self.dir, "worker.pid"), "w") as f:
            f.write(str(os.getpid()))
        while True:
            try:
                conn, _ = srv.accept()
            except socket.timeout:
                if not self.busy and time.time() - self.last_activity > IDLE_EXIT_SEC:
                    break
                continue
            data = b""
            conn.settimeout(5)
            try:
                while not data.endswith(b"\n"):
                    part = conn.recv(65536)
                    if not part:
                        break
                    data += part
            except Exception:
                pass
            conn.close()
            for line in data.decode("utf-8", "replace").splitlines():
                try:
                    self.last_activity = time.time()
                    self.handle(json.loads(line))
                except Exception as e:  # jangan biarkan worker mati karena satu perintah
                    self.emit({"t": "error", "msg": "command failed: %s" % e})
        self.adapter.close()
        if self.meta.get("status") == "working":
            self.save_meta(status="idle")
        try:
            os.unlink(sp)
        except Exception:
            pass


def cmd_worker(args):
    w = Worker(args[0])
    signal.signal(signal.SIGTERM, lambda *_: (w.adapter.close(), os._exit(0)))
    w.serve()


# ---------------------------------------------------------------- adapter: Claude Code

def summarize_input(name, inp):
    """Ringkasan input tool untuk kartu di aplikasi (path, perintah, dsb)."""
    if not isinstance(inp, dict):
        return {"summary": clip(inp, 300)}
    out = {}
    for k in ("command", "file_path", "path", "pattern", "url", "query", "description", "prompt", "notebook_path"):
        if k in inp and isinstance(inp[k], str):
            out[k] = clip(inp[k], 1500)
    if name in ("Edit", "MultiEdit") or "old_string" in inp:
        out["old"] = clip(inp.get("old_string", ""), 3000)
        out["new"] = clip(inp.get("new_string", ""), 3000)
    if name == "Write" and "content" in inp:
        out["new"] = clip(inp.get("content", ""), 3000)
    if "plan" in inp and isinstance(inp["plan"], str):
        out["plan"] = clip(inp["plan"], 8000)
    if name == "TodoWrite" and "todos" in inp:
        out["todos"] = [{"t": t.get("content", ""), "s": t.get("status", "")} for t in inp["todos"]][:30]
    if not out:
        out["summary"] = clip(inp, 600)
    return out


def result_text(content):
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts = []
        for c in content:
            if isinstance(c, dict) and c.get("type") == "text":
                parts.append(c.get("text", ""))
            elif isinstance(c, dict) and c.get("type") == "image":
                parts.append("[image]")
        return "\n".join(parts)
    return jdump(content) if content is not None else ""


class ClaudeAdapter:
    def __init__(self, w):
        self.w = w
        self.proc = None
        self.wlock = threading.Lock()
        self.pending = {}      # request_id -> request (izin)
        self.blocks = {}       # (msg_id, index) -> {"kind":..., "id":...}
        self.buf = {}          # block id -> teks yang belum dikirim
        self.buf_lock = threading.Lock()
        self.req = 0
        threading.Thread(target=self._flusher, daemon=True).start()

    # -- proses
    def ensure(self):
        if self.proc and self.proc.poll() is None:
            return
        m = self.w.meta
        cmd = [first_bin("claude") or "claude", "-p", "--input-format", "stream-json",
               "--output-format", "stream-json", "--verbose", "--include-partial-messages",
               "--permission-prompt-tool", "stdio"]
        if m.get("mode"):
            cmd += ["--permission-mode", m["mode"]]
        if m.get("model"):
            cmd += ["--model", m["model"]]
        if m.get("session"):
            cmd += ["--resume", m["session"]]
        self.proc = subprocess.Popen(cmd, cwd=m["cwd"], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                     stderr=subprocess.PIPE, text=True, bufsize=1)
        threading.Thread(target=self._read, args=(self.proc,), daemon=True).start()
        threading.Thread(target=self._read_err, args=(self.proc,), daemon=True).start()
        self._write({"type": "control_request", "request_id": "init", "request": {"subtype": "initialize"}})

    def _write(self, o):
        with self.wlock:
            try:
                self.proc.stdin.write(jdump(o) + "\n")
                self.proc.stdin.flush()
            except Exception as e:
                self.w.emit({"t": "error", "msg": "agent did not accept input: %s" % e})

    def _control(self, request):
        self.req += 1
        self._write({"type": "control_request", "request_id": "t%d" % self.req, "request": request})

    # -- perintah dari aplikasi
    def send(self, text):
        self.ensure()
        self._write({"type": "user", "message": {"role": "user", "content": text}})

    def permission(self, rid, allow, always):
        req = self.pending.pop(rid, None)
        if req is None:
            return
        if allow:
            resp = {"behavior": "allow", "updatedInput": req.get("input", {})}
            if always and req.get("permission_suggestions"):
                resp["updatedPermissions"] = req["permission_suggestions"]
        else:
            resp = {"behavior": "deny", "message": "Ditolak oleh pengguna."}
        self._write({"type": "control_response",
                     "response": {"subtype": "success", "request_id": rid, "response": resp}})
        self.w.emit({"t": "perm_done", "rid": rid, "allow": allow})

    def set_model(self, model):
        self.w.save_meta(model=model)
        if self.proc and self.proc.poll() is None:
            self._control({"subtype": "set_model", "model": model or None})
        self.w.emit({"t": "model", "model": model})

    def set_mode(self, mode):
        self.w.save_meta(mode=mode)
        if self.proc and self.proc.poll() is None:
            self._control({"subtype": "set_permission_mode", "mode": mode})
        self.w.emit({"t": "mode", "mode": mode})

    def interrupt(self):
        if self.proc and self.proc.poll() is None:
            self._control({"subtype": "interrupt"})
        for rid in list(self.pending):
            self.permission(rid, False, False)

    def close(self):
        if self.proc and self.proc.poll() is None:
            try:
                self.proc.stdin.close()
                self.proc.wait(timeout=5)
            except Exception:
                self.proc.kill()

    # -- penggabungan teks
    def _push(self, bid, text):
        with self.buf_lock:
            self.buf[bid] = self.buf.get(bid, "") + text

    def _flush(self):
        with self.buf_lock:
            items, self.buf = self.buf, {}
        for bid, text in items.items():
            if text:
                self.w.emit({"t": "delta", "id": bid, "d": text})

    def _flusher(self):
        while True:
            time.sleep(FLUSH_SEC)
            self._flush()

    # -- membaca keluaran Claude Code
    def _read_err(self, proc):
        for line in proc.stderr:
            line = line.strip()
            if line:
                self.w.emit({"t": "stderr", "msg": clip(line, 800)})

    def _read(self, proc):
        for line in proc.stdout:
            try:
                o = json.loads(line)
            except Exception:
                continue
            try:
                self._on(o)
            except Exception as e:
                self.w.emit({"t": "error", "msg": "could not read event: %s" % e})
        self._flush()
        code = proc.wait()
        if self.w.busy:
            self.w.emit({"t": "error", "msg": "agent exited (code %s)" % code})
            self.w.set_busy(False)

    def _on(self, o):
        t = o.get("type")
        if t == "system":
            st = o.get("subtype")
            if st == "init":
                self.w.save_meta(session=o.get("session_id", ""))
                self.w.emit({"t": "init", "session": o.get("session_id"), "model": o.get("model"),
                             "mode": o.get("permissionMode"), "cwd": o.get("cwd")})
            elif st == "status" and o.get("status") == "compacting":
                self.w.emit({"t": "note", "msg": "Compacting conversation…"})
        elif t == "stream_event":
            ev = o.get("event") or {}
            et = ev.get("type")
            mid = o.get("api_message_id") or ""
            if et == "content_block_start":
                b = ev.get("content_block") or {}
                key = (mid, ev.get("index"))
                if b.get("type") == "text":
                    bid = "%s:%s" % key
                    self.blocks[key] = {"kind": "text", "id": bid}
                    self.w.emit({"t": "text_start", "id": bid})
                elif b.get("type") == "thinking":
                    self.blocks[key] = {"kind": "think"}
                    self.w.emit({"t": "thinking"})
                elif b.get("type") == "tool_use":
                    self.blocks[key] = {"kind": "tool"}
                    self.w.emit({"t": "tool_prep", "id": b.get("id"), "name": b.get("name")})
            elif et == "content_block_delta":
                d = ev.get("delta") or {}
                blk = self.blocks.get((mid, ev.get("index")))
                if blk and blk["kind"] == "text" and d.get("type") == "text_delta":
                    self._push(blk["id"], d.get("text", ""))
            elif et == "content_block_stop":
                blk = self.blocks.pop((mid, ev.get("index")), None)
                if blk and blk["kind"] == "text":
                    self._flush()
                    self.w.emit({"t": "text_end", "id": blk["id"]})
        elif t == "assistant":
            if o.get("parent_tool_use_id"):
                return  # pesan sub-agent: cukup tampil sebagai kartu Task
            used = _ctx_used((o.get("message") or {}).get("usage"))
            if used:
                self.ctx_used = used
            for b in (o.get("message") or {}).get("content") or []:
                if b.get("type") == "tool_use":
                    self.w.emit({"t": "tool", "id": b.get("id"), "name": b.get("name"),
                                 "input": summarize_input(b.get("name"), b.get("input"))})
        elif t == "user":
            if o.get("parent_tool_use_id"):
                return
            content = (o.get("message") or {}).get("content")
            if isinstance(content, list):
                for b in content:
                    if isinstance(b, dict) and b.get("type") == "tool_result":
                        self.w.emit({"t": "tool_result", "id": b.get("tool_use_id"),
                                     "ok": not b.get("is_error"), "out": clip(result_text(b.get("content")))})
        elif t == "control_request":
            req = o.get("request") or {}
            if req.get("subtype") == "can_use_tool":
                rid = o.get("request_id")
                self.pending[rid] = req
                self.w.emit({"t": "perm", "rid": rid, "tool": req.get("tool_name"),
                             "desc": req.get("description") or "",
                             "input": summarize_input(req.get("tool_name"), req.get("input")),
                             "always": bool(req.get("permission_suggestions"))})
            else:  # permintaan lain yang tidak kita tangani: jawab agar agent tidak menunggu
                self._write({"type": "control_response", "response": {
                    "subtype": "error", "request_id": o.get("request_id"), "error": "unsupported"}})
        elif t == "control_response":
            r = o.get("response") or {}
            if r.get("request_id") == "init" and r.get("subtype") == "success":
                cmds = (r.get("response") or {}).get("commands") or []
                self.w.emit({"t": "commands", "list": [
                    {"name": c.get("name"), "desc": clip(c.get("description", ""), 140), "hint": c.get("argumentHint", "")}
                    for c in cmds][:200]})
            elif r.get("subtype") == "error":
                self.w.emit({"t": "note", "msg": clip(r.get("error", ""), 300)})
        elif t == "rate_limit_event":
            info = o.get("rate_limit_info") or {}
            win = info.get("unifiedWindows") or {}
            self.w.emit({"t": "limits", "windows": {
                k: {"used": v.get("utilization"), "reset": v.get("resetsAt"), "status": v.get("status")}
                for k, v in win.items() if isinstance(v, dict)}})
        elif t == "result":
            self._flush()
            u = o.get("usage") or {}
            tin = (u.get("input_tokens") or 0) + (u.get("cache_creation_input_tokens") or 0) + (u.get("cache_read_input_tokens") or 0)
            tout = u.get("output_tokens") or 0
            cost = o.get("total_cost_usd") or 0.0
            m = self.w.meta
            self.w.save_meta(cost=round(m.get("cost", 0.0) + cost, 6), tokens_in=m.get("tokens_in", 0) + tin,
                             tokens_out=m.get("tokens_out", 0) + tout)
            mu = o.get("modelUsage") or {}
            models = sorted(mu, key=lambda k: (mu[k] or {}).get("outputTokens", 0), reverse=True)
            if models:
                window = (mu[models[0]] or {}).get("contextWindow") or 0
                _ctx_remember(models[0], window)
                if getattr(self, "ctx_used", 0):
                    self.w.emit({"t": "context", "used": self.ctx_used, "size": window})
            self.w.emit({"t": "done", "ok": not o.get("is_error"), "cost": cost, "in": tin, "out": tout,
                         "ms": o.get("duration_ms"), "turns": o.get("num_turns"), "model": models[0] if models else None,
                         "error": clip(o.get("result"), 600) if o.get("is_error") else None})
            for rid in list(self.pending):  # izin yang tak terjawab sudah tidak berlaku
                self.pending.pop(rid, None)
            self.w.set_busy(False)


# ---------------------------------------------------------------- dasar adapter lain

class StreamAdapter:
    """Dasar adapter: proses agent + penggabungan potongan teks + blok teks berurutan."""

    def __init__(self, w):
        self.w = w
        self.proc = None
        self.wlock = threading.Lock()
        self.buf = {}
        self.buf_lock = threading.Lock()
        self.text_id = None
        self.blk = 0
        self.pending = {}
        self.models_sent = False
        threading.Thread(target=self._flusher, daemon=True).start()

    # teks
    def _flusher(self):
        while True:
            time.sleep(FLUSH_SEC)
            self.flush()

    def flush(self):
        with self.buf_lock:
            items, self.buf = self.buf, {}
        for bid, text in items.items():
            if text:
                self.w.emit({"t": "delta", "id": bid, "d": text})

    def text(self, chunk):
        if not chunk:
            return
        if self.text_id is None:
            self.blk += 1
            self.text_id = "%s-%d-%d" % (self.w.meta["agent"], int(time.time()), self.blk)
            self.w.emit({"t": "text_start", "id": self.text_id})
        with self.buf_lock:
            self.buf[self.text_id] = self.buf.get(self.text_id, "") + chunk

    def end_text(self):
        if self.text_id is not None:
            self.flush()
            self.w.emit({"t": "text_end", "id": self.text_id})
            self.text_id = None

    def tool(self, tid, name, inp):
        self.end_text()
        self.w.emit({"t": "tool", "id": tid, "name": name, "input": summarize_input(name, inp)})

    def tool_done(self, tid, ok, out):
        self.w.emit({"t": "tool_result", "id": tid, "ok": bool(ok), "out": clip(out)})

    def finish(self, ok=True, cost=0.0, tin=0, tout=0, ms=None, model=None, error=None):
        self.end_text()
        m = self.w.meta
        self.w.save_meta(cost=round(m.get("cost", 0.0) + (cost or 0.0), 6), tokens_in=m.get("tokens_in", 0) + (tin or 0),
                         tokens_out=m.get("tokens_out", 0) + (tout or 0))
        self.w.emit({"t": "done", "ok": ok, "cost": cost or 0.0, "in": tin or 0, "out": tout or 0, "ms": ms,
                     "model": model, "error": clip(error, 600) if error else None})
        self.pending.clear()
        self.w.set_busy(False)

    def models(self, items, current=None):
        self.w.emit({"t": "models", "list": [{"id": i, "name": n} for i, n in items][:300], "current": current})

    # proses
    def spawn(self, cmd, cwd):
        self.proc = subprocess.Popen(cmd, cwd=cwd, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                     stderr=subprocess.PIPE, text=True, bufsize=1)
        threading.Thread(target=self._read, args=(self.proc,), daemon=True).start()
        threading.Thread(target=self._read_err, args=(self.proc,), daemon=True).start()

    def alive(self):
        return self.proc is not None and self.proc.poll() is None

    def write(self, o):
        with self.wlock:
            try:
                self.proc.stdin.write(jdump(o) + "\n")
                self.proc.stdin.flush()
            except Exception as e:
                self.w.emit({"t": "error", "msg": "agent did not accept input: %s" % e})

    def _read_err(self, proc):
        tail = []
        for line in proc.stderr:
            line = line.rstrip()
            if line:
                tail = (tail + [line])[-12:]
                self.stderr_line(line)
        self.err_tail = "\n".join(tail)

    def stderr_line(self, line):
        pass

    def _read(self, proc):
        for line in proc.stdout:
            line = line.rstrip("\r\n")
            if not line:
                continue
            try:
                o = json.loads(line)
            except Exception:
                continue
            try:
                self.on(o)
            except Exception as e:
                self.w.emit({"t": "error", "msg": "could not read event: %s" % e})
        code = proc.wait()
        time.sleep(0.2)
        self.exited(code)

    def exited(self, code):
        if self.w.busy:
            err = getattr(self, "err_tail", "") or ("agent exited (code %s)" % code)
            self.finish(ok=False, error=err)

    def close(self):
        if self.alive():
            try:
                self.proc.stdin.close()
                self.proc.wait(timeout=5)
            except Exception:
                try:
                    self.proc.kill()
                except Exception:
                    pass

    def set_mode(self, mode):
        self.w.save_meta(mode=mode)
        self.w.emit({"t": "mode", "mode": mode})

    def permission(self, rid, allow, always):
        pass


def content_text(c):
    """Teks dari blok konten ACP / pi (string, {type:text}, daftar)."""
    if c is None:
        return ""
    if isinstance(c, str):
        return c
    if isinstance(c, list):
        return "\n".join(content_text(x) for x in c)
    if isinstance(c, dict):
        if c.get("type") == "text":
            return c.get("text", "")
        if c.get("type") == "content":
            return content_text(c.get("content"))
        if c.get("type") == "diff":
            return "--- %s\n%s" % (c.get("path", ""), c.get("newText", "")[:4000])
        if "text" in c:
            return str(c.get("text"))
    return ""


# ---------------------------------------------------------------- adapter: Codex (codex exec --json)

class CodexAdapter(StreamAdapter):
    """Satu proses `codex exec --json` per giliran; percakapan dilanjutkan dengan `exec resume <id>`."""

    def send(self, text):
        m = self.w.meta
        cmd = [first_bin("codex") or "codex", "exec", "--json", "--skip-git-repo-check"]
        if m.get("session"):
            cmd += ["resume", m["session"]]
        if m.get("model"):
            cmd += ["-m", m["model"]]
        cmd += ["-c", 'sandbox_mode="%s"' % (m.get("mode") or "workspace-write"), "-"]
        self.started = time.time()
        self.usage = None
        self.spawn(cmd, m["cwd"])
        try:
            self.proc.stdin.write(text)
            self.proc.stdin.close()
        except Exception:
            pass

    def on(self, o):
        t = o.get("type")
        if t == "thread.started":
            self.w.save_meta(session=o.get("thread_id", ""))
            self.w.emit({"t": "init", "session": o.get("thread_id"), "model": self.w.meta.get("model") or ""})
        elif t in ("item.started", "item.updated", "item.completed"):
            it = o.get("item") or {}
            kind = it.get("type") or it.get("item_type")
            iid = it.get("id") or ""
            done = t == "item.completed"
            if kind == "agent_message" and done:
                self.text(it.get("text", ""))
                self.end_text()
            elif kind == "reasoning" and not done:
                self.w.emit({"t": "thinking"})
            elif kind == "command_execution":
                if t == "item.started":
                    self.tool(iid, "Bash", {"command": it.get("command", "")})
                elif done:
                    code = it.get("exit_code")
                    self.tool_done(iid, code in (0, None) and it.get("status") != "failed", it.get("aggregated_output", ""))
            elif kind == "file_change":
                ch = it.get("changes") or []
                if t == "item.started" or done:
                    paths = ", ".join("%s %s" % (c.get("kind", ""), c.get("path", "")) for c in ch)
                    self.tool(iid, "Edit", {"file_path": paths})
                if done:
                    self.tool_done(iid, it.get("status") != "failed", paths)
            elif kind == "web_search":
                if t == "item.started":
                    self.tool(iid, "WebSearch", {"query": it.get("query", "")})
                elif done:
                    self.tool_done(iid, True, "")
            elif kind == "mcp_tool_call":
                name = "%s.%s" % (it.get("server", ""), it.get("tool", ""))
                if t == "item.started":
                    self.tool(iid, name, it.get("arguments") or {})
                elif done:
                    self.tool_done(iid, it.get("status") != "failed", content_text(it.get("result")) or jdump(it.get("error") or ""))
            elif kind == "todo_list":
                todos = [{"content": x.get("text", ""), "status": "completed" if x.get("completed") else "pending"}
                         for x in it.get("items") or []]
                self.tool(iid or "todo", "TodoWrite", {"todos": todos})
                self.tool_done(iid or "todo", True, "")
            elif kind == "error" and done:
                self.w.emit({"t": "error", "msg": it.get("message", "")})
        elif t == "turn.completed":
            self.usage = o.get("usage") or {}
        elif t == "turn.failed":
            self.fail = (o.get("error") or {}).get("message", "gagal")
        elif t == "error":
            msg = o.get("message", "")
            # Percobaan ulang & peralihan transport hanya status sementara, bukan item chat.
            if "Reconnecting" in msg or "Falling back" in msg:
                self.w.emit({"t": "retry", "msg": clip(msg.split(" (")[0], 120)})
            else:
                self.w.emit({"t": "error", "msg": msg})

    def exited(self, code):
        u = self.usage or {}
        fail = getattr(self, "fail", None)
        self.fail = None
        ms = int((time.time() - getattr(self, "started", time.time())) * 1000)
        if self.w.busy:
            ok = code == 0 and not fail and self.usage is not None
            err = fail or (None if ok else (getattr(self, "err_tail", "") or "codex berhenti (kode %s)" % code))
            self.finish(ok=ok, tin=(u.get("input_tokens") or 0), tout=(u.get("output_tokens") or 0), ms=ms,
                        model=self.w.meta.get("model") or None, error=err)

    def set_model(self, model):
        self.w.save_meta(model=model)
        self.w.emit({"t": "model", "model": model})

    def interrupt(self):
        if self.alive():
            self.proc.send_signal(signal.SIGINT)


# ---------------------------------------------------------------- adapter: ACP (opencode, Hermes)

class AcpAdapter(StreamAdapter):
    """Agent Client Protocol (JSON-RPC lewat stdio). Dipakai opencode (`opencode acp`) dan Hermes (`hermes-acp`)."""

    COMMANDS = {"opencode": lambda b: [b, "acp"], "hermes": lambda b: [os.path.join(os.path.dirname(b), "hermes-acp")]}

    def __init__(self, w):
        super().__init__(w)
        self.rid = 0
        self.calls = {}
        self.sid = None
        self.ready = threading.Event()
        self.loading = False
        self.queue = []
        self.started = time.time()

    def call(self, method, params, cb=None):
        self.rid += 1
        self.calls[self.rid] = (method, cb)
        self.write({"jsonrpc": "2.0", "id": self.rid, "method": method, "params": params})

    def notify(self, method, params):
        self.write({"jsonrpc": "2.0", "method": method, "params": params})

    def ensure(self):
        if self.alive():
            return
        agent = self.w.meta["agent"]
        b = first_bin(*[a for a in CATALOG if a["id"] == agent][0]["bins"])
        self.sid = None
        self.ready.clear()
        self.spawn(self.COMMANDS[agent](b), self.w.meta["cwd"])
        self.call("initialize", {"protocolVersion": 1, "clientCapabilities": {
            "fs": {"readTextFile": False, "writeTextFile": False}, "terminal": False}}, self._initialized)

    def _initialized(self, res, err):
        if err:
            return self._fatal(err)
        caps = (res or {}).get("agentCapabilities") or {}
        cwd = self.w.meta["cwd"]
        if self.w.meta.get("session") and caps.get("loadSession"):
            self.loading = True  # riwayat yang diputar ulang agent sudah ada di events.jsonl
            self.call("session/load", {"sessionId": self.w.meta["session"], "cwd": cwd, "mcpServers": []}, self._session)
        else:
            self.call("session/new", {"cwd": cwd, "mcpServers": []}, self._session)

    def _session(self, res, err):
        self.loading = False
        if err:
            if self.w.meta.get("session"):  # sesi lama tidak bisa dimuat: mulai sesi baru
                self.w.save_meta(session="")
                self.w.emit({"t": "note", "msg": "Could not resume the previous session; started a new one."})
                return self.call("session/new", {"cwd": self.w.meta["cwd"], "mcpServers": []}, self._session)
            return self._fatal(err)
        res = res or {}
        self.sid = res.get("sessionId") or self.w.meta.get("session")
        self.w.save_meta(session=self.sid)
        self._apply_options(res)
        self.w.emit({"t": "init", "session": self.sid, "model": self.w.meta.get("model") or ""})
        if self.w.meta.get("model") and self.w.meta["model"] != getattr(self, "current_model", None):
            self._set_model_rpc(self.w.meta["model"])
        if self.w.meta.get("mode") and self.w.meta["mode"] != getattr(self, "current_mode", None):
            self.call("session/set_mode", {"sessionId": self.sid, "modeId": self.w.meta["mode"]})
        self.ready.set()
        for text in self.queue:
            self._prompt(text)
        self.queue = []

    def _apply_options(self, res):
        models = res.get("models") or {}
        items = [(m.get("modelId"), m.get("name") or m.get("modelId")) for m in models.get("availableModels") or []]
        current = models.get("currentModelId")
        for opt in res.get("configOptions") or []:
            if opt.get("category") == "model" or opt.get("id") == "model":
                items = [(o.get("value"), o.get("name") or o.get("value")) for o in opt.get("options") or []]
                current = opt.get("currentValue")
        if items:
            self.current_model = current
            self.models(items, current)
        modes = res.get("modes") or {}
        if modes.get("availableModes"):
            self.current_mode = modes.get("currentModeId")
            self.w.emit({"t": "modes", "list": [{"id": m.get("id"), "name": m.get("name") or m.get("id")}
                                                for m in modes["availableModes"]], "current": self.current_mode})

    def _fatal(self, err):
        msg = (err.get("data") or {}).get("details") if isinstance(err.get("data"), dict) else None
        msg = msg or err.get("message") or jdump(err)
        self.w.emit({"t": "error", "msg": msg})
        if self.w.busy:
            self.finish(ok=False, error=msg)
        self.close()

    def send(self, text):
        self.ensure()
        if self.ready.is_set():
            self._prompt(text)
        else:
            self.queue.append(text)

    def _prompt(self, text):
        self.started = time.time()
        self.call("session/prompt", {"sessionId": self.sid, "prompt": [{"type": "text", "text": text}]}, self._prompted)

    def _prompted(self, res, err):
        res = res or {}
        u = res.get("usage") or {}
        ms = int((time.time() - self.started) * 1000)
        if err:
            msg = (err.get("data") or {}).get("details") if isinstance(err.get("data"), dict) else None
            self.finish(ok=False, ms=ms, error=msg or err.get("message"))
        else:
            reason = res.get("stopReason")
            self.finish(ok=reason not in ("refusal",), ms=ms, model=getattr(self, "current_model", None),
                        tin=u.get("inputTokens") or u.get("input_tokens") or 0,
                        tout=u.get("outputTokens") or u.get("output_tokens") or 0,
                        error="Stopped." if reason == "cancelled" else None)

    def _set_model_rpc(self, model):
        def fallback(_res, err):
            if err:
                self.call("session/set_config_option", {"sessionId": self.sid, "configId": "model", "value": model})
        self.call("session/set_model", {"sessionId": self.sid, "modelId": model}, fallback)
        self.current_model = model

    def set_model(self, model):
        self.w.save_meta(model=model)
        if self.sid and self.alive():
            self._set_model_rpc(model)
        self.w.emit({"t": "model", "model": model})

    def set_mode(self, mode):
        super().set_mode(mode)
        if self.sid and self.alive():
            self.call("session/set_mode", {"sessionId": self.sid, "modeId": mode})

    def interrupt(self):
        if self.sid and self.alive():
            self.notify("session/cancel", {"sessionId": self.sid})
        for rid in list(self.pending):
            self.permission(rid, False, False)

    def permission(self, rid, allow, always):
        req = self.pending.pop(rid, None)
        if req is None:
            return
        opts = req.get("options") or []
        want = ("allow_always", "allow_once") if allow and always else ("allow_once", "allow_always") if allow else ("reject_once", "reject_always")
        pick = next((o for k in want for o in opts if o.get("kind") == k), None)
        outcome = {"outcome": "selected", "optionId": pick["optionId"]} if pick else {"outcome": "cancelled"}
        self.write({"jsonrpc": "2.0", "id": req["_id"], "result": {"outcome": outcome}})
        self.w.emit({"t": "perm_done", "rid": rid, "allow": allow})

    def on(self, o):
        if "id" in o and "method" not in o:  # balasan untuk panggilan kita
            method, cb = self.calls.pop(o["id"], (None, None))
            if cb:
                cb(o.get("result"), o.get("error"))
            elif o.get("error") and method not in ("session/set_mode", "session/set_config_option"):
                self.w.emit({"t": "note", "msg": "%s: %s" % (method, o["error"].get("message"))})
            return
        method = o.get("method")
        params = o.get("params") or {}
        if "id" in o:  # permintaan dari agent
            if method == "session/request_permission":
                rid = str(o["id"])
                tc = params.get("toolCall") or {}
                req = dict(params)
                req["_id"] = o["id"]
                self.pending[rid] = req
                self.end_text()
                name = tc.get("title") or tc.get("kind") or "tool"
                self.w.emit({"t": "perm", "rid": rid, "tool": name, "desc": tc.get("kind") or "",
                             "input": summarize_input(name, tc.get("rawInput") or {}),
                             "always": any(x.get("kind") == "allow_always" for x in params.get("options") or [])})
            else:
                self.write({"jsonrpc": "2.0", "id": o["id"], "error": {"code": -32601, "message": "tidak didukung"}})
            return
        if method != "session/update" or self.loading:
            return
        u = params.get("update") or {}
        k = u.get("sessionUpdate")
        if k == "agent_message_chunk":
            self.text(content_text(u.get("content")))
        elif k == "agent_thought_chunk":
            self.w.emit({"t": "thinking"})
        elif k == "tool_call":
            inp = u.get("rawInput") or {}
            if not inp and u.get("locations"):
                inp = {"path": ", ".join(l.get("path", "") for l in u["locations"])}
            self.tool(u.get("toolCallId"), u.get("title") or u.get("kind") or "tool", inp)
            if u.get("status") in ("completed", "failed"):
                self.tool_done(u.get("toolCallId"), u.get("status") == "completed", content_text(u.get("content")))
        elif k == "tool_call_update":
            st = u.get("status")
            if u.get("rawInput") or u.get("title"):
                self.w.emit({"t": "tool", "id": u.get("toolCallId"), "name": u.get("title") or "tool",
                             "input": summarize_input(u.get("title") or "", u.get("rawInput") or {})})
            if st in ("completed", "failed"):
                out = content_text(u.get("content")) or (jdump(u.get("rawOutput")) if u.get("rawOutput") else "")
                self.tool_done(u.get("toolCallId"), st == "completed", out)
        elif k == "plan":
            todos = [{"content": e.get("content", ""), "status": e.get("status", "")} for e in u.get("entries") or []]
            self.tool("plan", "TodoWrite", {"todos": todos})
            self.tool_done("plan", True, "")
        elif k == "available_commands_update":
            self.w.emit({"t": "commands", "list": [
                {"name": c.get("name"), "desc": clip(c.get("description", ""), 140), "hint": ((c.get("input") or {}).get("hint") or "")}
                for c in u.get("availableCommands") or []][:200]})
        elif k == "current_mode_update":
            self.current_mode = u.get("currentModeId")
            self.w.emit({"t": "mode", "mode": self.current_mode})
        elif k == "config_option_update":
            self._apply_options({"configOptions": u.get("configOptions") or []})
        elif k == "usage_update":
            self.w.emit({"t": "context", "used": u.get("used"), "size": u.get("size")})


# ---------------------------------------------------------------- adapter: pi / oh-my-pi (--mode rpc)

class PiAdapter(StreamAdapter):
    """RPC mode resmi pi (`pi --mode rpc`); oh-my-pi (`omp`) memakai protokol yang sama."""

    def __init__(self, w):
        super().__init__(w)
        self.turn = {"in": 0, "out": 0, "cost": 0.0, "model": None}
        self.started = time.time()

    def ensure(self):
        if self.alive():
            return
        m = self.w.meta
        b = first_bin("omp" if m["agent"] == "omp" else "pi")
        cmd = [b, "--mode", "rpc"]
        if m.get("session"):
            cmd += ["--session", m["session"]]
        if m.get("model"):
            cmd += ["--model", m["model"]]
        self.spawn(cmd, m["cwd"])
        self.write({"id": "state", "type": "get_state"})
        self.write({"id": "models", "type": "get_available_models"})
        self.write({"id": "commands", "type": "get_commands"})

    def send(self, text):
        self.ensure()
        self.started = time.time()
        self.turn = {"in": 0, "out": 0, "cost": 0.0, "model": None}
        self.write({"type": "prompt", "message": text})

    def on(self, o):
        t = o.get("type")
        if t == "response":
            cmd, data = o.get("command"), o.get("data") or {}
            if not o.get("success"):
                self.w.emit({"t": "error", "msg": "%s: %s" % (cmd, o.get("error") or "failed")})
                if cmd == "prompt" and self.w.busy:
                    self.finish(ok=False, error=o.get("error"))
            elif cmd == "get_state":
                sid = data.get("sessionId") or ""
                self.w.save_meta(session=sid)
                mod = data.get("model") or {}
                cur = "%s/%s" % (mod.get("provider"), mod.get("id")) if mod.get("provider") not in (None, "unknown") else ""
                self.w.emit({"t": "init", "session": sid, "model": cur})
            elif cmd == "get_available_models":
                self.models([("%s/%s" % (x.get("provider"), x.get("id")), x.get("name") or x.get("id"))
                             for x in data.get("models") or []])
            elif cmd == "get_commands":
                cmds = data.get("commands") or []
                self.w.emit({"t": "commands", "list": [{"name": c.get("name"), "desc": clip(c.get("description", ""), 140), "hint": ""}
                                                       for c in cmds if isinstance(c, dict)][:200]})
        elif t == "message_update":
            ev = o.get("assistantMessageEvent") or {}
            et = ev.get("type")
            if et == "text_delta":
                self.text(ev.get("delta", ""))
            elif et == "text_end":
                self.end_text()
            elif et == "thinking_start":
                self.w.emit({"t": "thinking"})
            elif et == "error":
                self.w.emit({"t": "error", "msg": ev.get("reason") or "error"})
        elif t == "message_end":
            msg = o.get("message") or {}
            if msg.get("role") == "assistant":
                u = msg.get("usage") or {}
                self.turn["in"] += (u.get("input") or 0) + (u.get("cacheRead") or 0) + (u.get("cacheWrite") or 0)
                self.turn["out"] += u.get("output") or 0
                self.turn["cost"] += (u.get("cost") or {}).get("total") or 0.0
                self.turn["model"] = msg.get("model") or self.turn["model"]
                if msg.get("stopReason") == "error" and msg.get("errorMessage"):
                    self.w.emit({"t": "error", "msg": msg.get("errorMessage")})
        elif t == "tool_execution_start":
            self.tool(o.get("toolCallId"), o.get("toolName") or "tool", o.get("args") or {})
        elif t == "tool_execution_end":
            self.tool_done(o.get("toolCallId"), not o.get("isError"), content_text((o.get("result") or {}).get("content")))
        elif t == "agent_end":
            self.finish(ok=True, cost=self.turn["cost"], tin=self.turn["in"], tout=self.turn["out"],
                        ms=int((time.time() - self.started) * 1000), model=self.turn["model"])
        elif t == "compaction_start":
            self.w.emit({"t": "note", "msg": "Compacting conversation…"})
        elif t == "auto_retry_start":
            self.w.emit({"t": "note", "msg": "Retrying… (%s)" % clip(o.get("errorMessage") or "", 200)})
        elif t == "extension_ui_request":
            rid = str(o.get("id"))
            if o.get("method") == "confirm":
                self.pending[rid] = o
                self.end_text()
                self.w.emit({"t": "perm", "rid": rid, "tool": o.get("title") or "Confirm", "desc": "",
                             "input": {"summary": clip(o.get("message") or "", 1500)}, "always": False})
            elif o.get("method") in ("select", "input", "editor"):
                self.write({"type": "extension_ui_response", "id": o.get("id"), "cancelled": True})
            elif o.get("method") == "notify":
                self.w.emit({"t": "note", "msg": clip(o.get("message") or "", 400)})
        elif t == "extension_error":
            self.w.emit({"t": "note", "msg": clip(o.get("error") or "", 400)})

    def stderr_line(self, line):
        pass

    def permission(self, rid, allow, always):
        req = self.pending.pop(rid, None)
        if req is not None:
            self.write({"type": "extension_ui_response", "id": req.get("id"), "confirmed": bool(allow)})
            self.w.emit({"t": "perm_done", "rid": rid, "allow": allow})

    def set_model(self, model):
        self.w.save_meta(model=model)
        if self.alive() and "/" in model:
            prov, mid = model.split("/", 1)
            self.write({"type": "set_model", "provider": prov, "modelId": mid})
        self.w.emit({"t": "model", "model": model})

    def interrupt(self):
        if self.alive():
            self.write({"type": "abort"})


ADAPTERS = {"claude": ClaudeAdapter, "codex": CodexAdapter, "opencode": AcpAdapter, "hermes": AcpAdapter,
            "pi": PiAdapter, "omp": PiAdapter}


# ------------------------------------------------------------------ sesi agent yang sedang berjalan
# Deteksi agent yang dijalankan di luar TermX (di tmux, herdr, atau terminal lain) milik user ini.

INTERPRETERS = ("node", "bun", "deno", "python", "python3")
SKIP_ARGS = {"mcp", "--version", "-v", "--help", "acp", "app-server", "proto", "serve", "server", "completion"}


def _read(path, binary=False):
    try:
        with open(path, "rb") as f:
            data = f.read()
        return data if binary else data.decode("utf-8", "replace")
    except OSError:
        return None


def _procs():
    """pid -> {ppid, argv, start(epoch detik), tty}; hanya proses milik user ini."""
    uid = os.getuid()
    btime = 0
    for line in (_read("/proc/stat") or "").splitlines():
        if line.startswith("btime "):
            btime = int(line.split()[1])
    hz = os.sysconf("SC_CLK_TCK") if hasattr(os, "sysconf") else 100
    out = {}
    for d in os.listdir("/proc"):
        if not d.isdigit():
            continue
        try:
            if os.stat("/proc/" + d).st_uid != uid:
                continue
        except OSError:
            continue
        stat = _read("/proc/%s/stat" % d)
        raw = _read("/proc/%s/cmdline" % d, binary=True)
        if not stat or raw is None:
            continue
        rest = stat[stat.rfind(")") + 2:].split()
        argv = [a.decode("utf-8", "replace") for a in raw.split(b"\0") if a]
        out[int(d)] = {"ppid": int(rest[1]), "argv": argv,
                       "start": btime + int(rest[19]) // hz if btime else 0}
    return out


def _agent_of(argv):
    if not argv:
        return None
    names = {b: a["id"] for a in CATALOG for b in a["bins"]}
    b0 = os.path.basename(argv[0])
    cand = None
    if b0 in names:
        cand, rest = names[b0], argv[1:]
    elif b0 in INTERPRETERS or b0.startswith("python3."):
        args = [a for a in argv[1:] if not a.startswith("-")]
        if args:
            b = re.sub(r"\.(c?js|mjs|py)$", "", os.path.basename(args[0]))
            if b in names:
                cand, rest = names[b], argv[argv.index(args[0]) + 1:]
    if cand is None:
        return None
    if any(a in SKIP_ARGS for a in rest[:2]):
        return None   # proses pembantu (server MCP/ACP, cek versi), bukan sesi interaktif
    return cand


def _tmux_panes():
    if not shutil.which("tmux"):
        return {}
    try:
        r = subprocess.run(["tmux", "list-panes", "-a", "-F",
                            "#{pane_pid}\t#{session_name}\t#{window_index}\t#{pane_index}\t#{session_attached}\t#{pane_title}\t#{window_name}"],
                           capture_output=True, text=True, timeout=3)
    except Exception:
        return {}
    panes = {}
    host = socket.gethostname().split(".")[0]
    for line in r.stdout.splitlines():
        p = line.split("\t")
        if len(p) >= 7 and p[0].isdigit():
            panes[int(p[0])] = {"session": p[1], "window": p[2], "pane": p[3],
                                "attached": p[4] not in ("", "0"),
                                "title": "" if p[5].split(".")[0] == host else p[5], "window_name": p[6]}
    return panes


def _herdr_agents():
    b = shutil.which("herdr")
    if not b:
        return None, []
    try:
        r = subprocess.run([b, "agent", "list"], capture_output=True, text=True, timeout=4)
        data = json.loads(r.stdout or "{}")
        return b, (data.get("result") or {}).get("agents") or []
    except Exception:
        return b, []


def _session_arg(argv):
    for i, a in enumerate(argv):
        if a in ("--resume", "-r", "--session-id", "--session") and i + 1 < len(argv):
            return re.sub(r"\.jsonl$", "", os.path.basename(argv[i + 1]))
        if a == "resume" and i + 1 < len(argv) and not argv[i + 1].startswith("-"):  # codex resume <id>
            return argv[i + 1]
        if a.startswith("--resume=") or a.startswith("--session="):
            return re.sub(r"\.jsonl$", "", os.path.basename(a.split("=", 1)[1]))
    return ""


def _claude_session(pid):
    """Sesi interaktif Claude Code menulis ~/.claude/sessions/<pid>.json (id sesi, status, nama)."""
    try:
        with open(os.path.expanduser("~/.claude/sessions/%d.json" % pid), encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return {}


def _claude_transcript(sid):
    if not sid or "/" in sid:
        return ""
    import glob
    hits = glob.glob(os.path.expanduser("~/.claude/projects/*/%s.jsonl" % sid))
    return max(hits, key=os.path.getmtime) if hits else ""


def _codex_transcript(sid, cwd, since):
    """Rollout Codex: menurut id sesi, atau yang terbaru untuk folder ini sejak proses mulai."""
    import glob
    files = glob.glob(os.path.expanduser("~/.codex/sessions/*/*/*/rollout-*.jsonl"))
    if sid:
        hit = [f for f in files if f.endswith(sid + ".jsonl")]
        if hit:
            return hit[0]
    best = ""
    for f in sorted(files, key=os.path.getmtime, reverse=True)[:40]:
        if since and os.path.getmtime(f) < since - 5:
            break
        try:
            with open(f, encoding="utf-8") as fh:
                first = json.loads(fh.readline() or "{}")
        except Exception:
            continue
        if (first.get("payload") or {}).get("cwd") == cwd:
            best = f
            break
    return best


def transcript_of(agent, pid, session, cwd, started):
    if agent == "claude":
        info = _claude_session(pid) if pid else {}
        sid = info.get("sessionId") or session
        return sid, _claude_transcript(sid)
    if agent == "codex":
        f = _codex_transcript(session, cwd, started)
        sid = session or (re.search(r"([0-9a-f-]{36})\.jsonl$", f).group(1) if f and re.search(r"([0-9a-f-]{36})\.jsonl$", f) else "")
        return sid, f
    return session, ""


def cmd_running(_args):
    out, herdr_bin = _running()
    mirrors, owned, live = {}, {}, set()
    if os.path.isdir(ROOT):
        for cid in os.listdir(ROOT):
            m = read_meta(cid) or {}
            if m.get("superseded"):
                continue
            live.add(cid)
            mi = m.get("mirror") or {}
            if mi:
                mirrors[(mi.get("pid") or 0, mi.get("herdr_pane") or "")] = cid
                if mi.get("owned"):
                    owned[mi["owned"]] = cid
    keep = []
    for f in out:
        own = f.pop("owner", "")
        if (f.get("tmux") or {}).get("session", "") == HERMES_AUTH_SESS:
            continue  # login OAuth Hermes dari wizard, bukan agent yang berjalan
        if f["where"] == "termx":
            if own not in live:
                continue  # chat sudah dihapus
            f["chat"] = own
        else:
            f["chat"] = (mirrors.get((f["pid"], (f.get("herdr") or {}).get("pane", "")))
                         or owned.get((f.get("tmux") or {}).get("session", "")) or "")
        keep.append(f)
    out = keep
    print(jdump({"running": out, "herdr_bin": herdr_bin or "", "tmux": bool(shutil.which("tmux"))}))


def _running():
    procs = _procs()
    panes = _tmux_panes()
    herdr_bin, herdr_list = _herdr_agents()
    me = os.getpid()
    found = []
    for pid, p in procs.items():
        agent = _agent_of(p["argv"])
        if not agent:
            continue
        where, info, seen, cur, dup, owner = "other", {}, set(), p["ppid"], False, ""
        while cur > 1 and cur not in seen:
            seen.add(cur)
            q = procs.get(cur)
            if q is None:
                break
            if cur == me or any("agentd.py" in a for a in q["argv"][:3]):
                where = "termx"   # dijalankan TermX: tampil bila milik worker chat, selain itu (mis. cek kuota) diabaikan
                argv = q["argv"]
                if "_worker" in argv and argv.index("_worker") + 1 < len(argv):
                    owner = argv[argv.index("_worker") + 1]
                break
            if _agent_of(q["argv"]) == agent:
                dup = True        # proses anak dari agent yang sama (mis. pembungkus node)
                break
            if where == "other":
                if cur in panes:
                    where, info = "tmux", panes[cur]
                elif q["argv"] and os.path.basename(q["argv"][0]) == "herdr":
                    where = "herdr"
            cur = q["ppid"]
        if dup or (where == "termx" and not owner):
            continue
        if where == "other" and pid in panes:
            where, info = "tmux", panes[pid]
        try:
            cwd = os.readlink("/proc/%d/cwd" % pid)
        except OSError:
            cwd = ""
        try:
            tty = os.readlink("/proc/%d/fd/0" % pid)
            tty = tty if tty.startswith("/dev/pts") or tty.startswith("/dev/tty") else ""
        except OSError:
            tty = ""
        found.append({"agent": agent, "pid": pid, "cwd": cwd, "started": p["start"], "where": where, "owner": owner,
                      "tty": tty.replace("/dev/", ""), "session": _session_arg(p["argv"]), **(
                          {"tmux": info, "title": info.get("title", "")} if where == "tmux" else {})})

    # herdr melaporkan agent-nya sendiri (status, judul, id pane) — lebih akurat dari pindaian proses.
    out = [f for f in found if f["where"] != "herdr"]
    hproc = [f for f in found if f["where"] == "herdr"]
    for h in herdr_list:
        aid = h.get("agent") or ""
        match = next((f for f in hproc if f["agent"] == aid and f["cwd"] == (h.get("foreground_cwd") or h.get("cwd"))), None)
        if match:
            hproc.remove(match)
        sess = h.get("agent_session") or {}
        out.append({"agent": aid, "pid": match["pid"] if match else 0, "cwd": h.get("foreground_cwd") or h.get("cwd") or "",
                    "started": match["started"] if match else 0, "where": "herdr", "tty": match["tty"] if match else "",
                    "session": sess.get("value") or "", "title": h.get("terminal_title_stripped") or "",
                    "herdr": {"pane": h.get("pane_id") or "", "workspace": h.get("workspace_id") or "",
                              "status": h.get("agent_status") or "", "focused": bool(h.get("focused"))}})
    out.extend(hproc)
    for f in out:  # Claude Code: id sesi, nama & status dari berkas sesinya
        if f["agent"] == "claude" and f["pid"]:
            info = _claude_session(f["pid"])
            f["session"] = info.get("sessionId") or f["session"]
            if not f.get("title"):
                f["title"] = info.get("name") or ""
            if info.get("status") and not (f.get("herdr") or {}).get("status"):
                f["status"] = "working" if info["status"] == "busy" else info["status"]
    out.sort(key=lambda f: f.get("started") or 0, reverse=True)
    return out, herdr_bin


# ------------------------------------------------------------------ chat langsung untuk sesi di tmux / herdr
# Sesi agent yang berjalan di luar TermX ditampilkan sebagai chat: riwayat dibaca dari transkrip agent
# (Claude Code, Codex), pesan diketik ke pane tmux/herdr-nya. Prosesnya tidak pernah dihentikan TermX.

IMPORT_BYTES = 1024 * 1024 * 1024  # riwayat awal: seluruh transkrip (batas aman untuk file raksasa)
IMPORT_EVENTS = 40000
BLOCKED_RE = re.compile(r"Do you want to|Would you like to|Allow (this|once|always)|\(y/n\)|\[y/N\]|❯ ?1\. Yes|Yes, proceed|"
                        r"Press enter to confirm|Enter to confirm|trust this folder", re.I)
MENU_MARK = ("❯", "›", "▶", "→")


def _menu(screen):
    """Opsi menu pilihan di layar TUI: (daftar teks opsi, indeks kursor) atau (None, -1)."""
    rows = screen.splitlines()[-30:]
    cur = next((i for i in range(len(rows) - 1, -1, -1) if rows[i].strip()[:1] in MENU_MARK), -1)
    if cur < 0:
        return None, -1

    def is_opt(s):
        s = s.strip()
        return bool(s) and not s.endswith("?") and not s.endswith(":") and not re.search(r"(Enter|Esc) to |^[─━-]{3,}", s) and len(s) < 120

    lo = cur
    while lo - 1 >= 0 and is_opt(rows[lo - 1]) and (rows[lo - 1].startswith("  ") or re.match(r"\s*\d+[.)]", rows[lo - 1])):
        lo -= 1
    hi = cur
    while hi + 1 < len(rows) and is_opt(rows[hi + 1]) and (rows[hi + 1].startswith("  ") or re.match(r"\s*\d+[.)]", rows[hi + 1])):
        hi += 1
    opts = [re.sub(r"^[\s❯›▶→>]*(\d+[.)]\s*)?", "", r).strip() for r in rows[lo:hi + 1]]
    return opts, cur - lo


def _menu_keys(screen, allow, always):
    """Tombol untuk memilih Yes / Always / No pada menu persetujuan; None bila menu tak terbaca."""
    if not allow:
        return ["esc"]
    opts, cur = _menu(screen)
    if not opts:
        return None
    yes = [i for i, o in enumerate(opts) if re.match(r"(yes|allow|approve|proceed|trust|continue|ok)\b", o, re.I)]
    if not yes:
        return None
    pick = yes[0]
    if always:
        more = [i for i in yes if re.search(r"don.t ask|always|for this session|all ", opts[i], re.I)]
        pick = more[0] if more else yes[0]
    else:
        plain = [i for i in yes if not re.search(r"don.t ask|always|for this session", opts[i], re.I)]
        pick = plain[0] if plain else yes[0]
    d = pick - cur
    return (["down"] * d if d > 0 else ["up"] * -d) + ["enter"]


def _plain_user(text):
    """Teks pengguna dari transkrip; None untuk pesan sistem/otomatis."""
    s = (text or "").strip()
    if not s:
        return None
    m = re.match(r"<command-name>(.*?)</command-name>", s, re.S)
    if m:
        args = re.search(r"<command-args>(.*?)</command-args>", s, re.S)
        return (m.group(1).strip() + " " + (args.group(1).strip() if args else "")).strip()
    if s.startswith("<") or s.startswith("Caveat:") or s.startswith("# AGENTS.md"):
        return None
    return s


class TranscriptConverter:
    """Mengubah baris transkrip agent menjadi event chat TermX (format yang sama dengan adapter biasa)."""

    def __init__(self, agent):
        self.agent = agent
        self.n = 0

    def convert(self, line):
        try:
            o = json.loads(line)
        except Exception:
            return []
        try:
            return self._claude(o) if self.agent == "claude" else self._codex(o) if self.agent == "codex" else []
        except Exception:
            return []

    def _text(self, bid, text):
        return [{"t": "text_start", "id": bid}, {"t": "delta", "id": bid, "d": text}, {"t": "text_end", "id": bid}]

    def _claude(self, o):
        t = o.get("type")
        if o.get("isSidechain"):
            return []
        if t == "system":
            st = o.get("subtype")
            if st == "turn_duration":
                out = [{"t": "done", "ok": True, "cost": 0, "in": 0, "out": 0, "ms": o.get("durationMs"),
                        "model": getattr(self, "model", None)}]
                if getattr(self, "ctx", 0):
                    out.insert(0, {"t": "context", "used": self.ctx, "size": _ctx_window(getattr(self, "model", ""))})
                return out
            if st == "compact_boundary":
                return [{"t": "note", "msg": "Conversation compacted"}]
            return []
        msg = o.get("message") or {}
        content = msg.get("content")
        if t == "user":
            if o.get("isMeta") or o.get("isCompactSummary"):
                return []
            if isinstance(content, str):
                if content.startswith("[Request interrupted"):
                    return [{"t": "note", "msg": "Interrupted"}]
                u = _plain_user(content)
                return [{"t": "user", "text": u}] if u else []
            out, texts = [], []
            for b in content or []:
                if not isinstance(b, dict):
                    continue
                if b.get("type") == "tool_result":
                    out.append({"t": "tool_result", "id": b.get("tool_use_id"), "ok": not b.get("is_error"),
                                "out": clip(result_text(b.get("content")))})
                elif b.get("type") == "text":
                    if b.get("text", "").startswith("[Request interrupted"):
                        out.append({"t": "note", "msg": "Interrupted"})
                    else:
                        texts.append(b.get("text", ""))
                elif b.get("type") == "image":
                    texts.append("[image]")
            u = _plain_user("\n".join(texts))
            return ([{"t": "user", "text": u}] if u else []) + out
        if t == "assistant":
            out = []
            used = _ctx_used(msg.get("usage"))
            if used:
                self.ctx = used
            mdl = msg.get("model")
            if mdl and mdl != "<synthetic>" and mdl != getattr(self, "model", None):
                self.model = mdl
                out.append({"t": "model", "model": mdl})
            for i, b in enumerate(content or []):
                if not isinstance(b, dict):
                    continue
                if b.get("type") == "text" and b.get("text", "").strip():
                    out += self._text("%s:%d" % (o.get("uuid") or self.n, i), b["text"])
                elif b.get("type") == "tool_use":
                    out.append({"t": "tool", "id": b.get("id"), "name": b.get("name"),
                                "input": summarize_input(b.get("name"), b.get("input"))})
            self.n += 1
            return out
        return []

    def _codex(self, o):
        t, p = o.get("type"), o.get("payload") or {}
        pt = p.get("type")
        if t == "event_msg":
            if pt == "task_started":
                return [{"t": "status", "s": "working"}]
            if pt == "task_complete":
                return [{"t": "done", "ok": not p.get("error"), "cost": 0, "in": 0, "out": 0}]
            return []
        if t != "response_item":
            return []
        self.n += 1
        if pt == "message":
            parts = [c.get("text", "") for c in p.get("content") or [] if isinstance(c, dict) and c.get("type") in ("input_text", "output_text", "text")]
            if p.get("role") == "user":
                u = _plain_user("\n".join(parts))
                return [{"t": "user", "text": u}] if u else []
            if p.get("role") == "assistant" and "".join(parts).strip():
                return self._text(p.get("id") or "cx%d" % self.n, "\n".join(parts))
            return []
        if pt in ("function_call", "custom_tool_call", "local_shell_call"):
            raw = p.get("arguments") if pt == "function_call" else p.get("input") or p.get("action")
            try:
                inp = json.loads(raw) if isinstance(raw, str) and raw.strip().startswith("{") else raw
            except Exception:
                inp = raw
            if isinstance(inp, dict):
                cmd = inp.get("cmd") or inp.get("command")
                if isinstance(cmd, list):
                    inp = dict(inp, command=" ".join(cmd[2:] if cmd[:2] in (["bash", "-lc"], ["sh", "-c"]) else cmd))
                elif isinstance(cmd, str):
                    inp = dict(inp, command=cmd)
            return [{"t": "tool", "id": p.get("call_id") or p.get("id"), "name": p.get("name") or "shell",
                     "input": summarize_input(p.get("name") or "", inp)}]
        if pt in ("function_call_output", "custom_tool_call_output"):
            out = p.get("output")
            if isinstance(out, dict):
                out = out.get("content") or out.get("output") or jdump(out)
            if isinstance(out, str) and out.startswith("{"):
                try:
                    j = json.loads(out)
                    out = j.get("output", out) if isinstance(j, dict) else out
                except Exception:
                    pass
            return [{"t": "tool_result", "id": p.get("call_id"), "ok": True, "out": clip(result_text(out))}]
        return []


def _proc_is(pid, agent):
    if not pid:
        return False
    raw = _read("/proc/%d/cmdline" % pid, binary=True)
    return bool(raw) and _agent_of([a.decode("utf-8", "replace") for a in raw.split(b"\0") if a]) == agent


def _screen(mi, lines=60):
    try:
        if mi.get("where") == "herdr" and mi.get("herdr_pane"):
            r = subprocess.run([mi.get("herdr_bin") or "herdr", "agent", "read", mi["herdr_pane"], "--source", "recent",
                                "--lines", str(lines)], capture_output=True, text=True, timeout=5)
        elif mi.get("where") == "tmux" and mi.get("tmux"):
            r = subprocess.run(["tmux", "capture-pane", "-p", "-J", "-t", mi["tmux"], "-S", "-%d" % lines],
                               capture_output=True, text=True, timeout=5)
        else:
            return ""
    except Exception:
        return ""
    rows = [x.rstrip() for x in (r.stdout or "").splitlines()]
    while rows and not rows[-1]:
        rows.pop()
    return "\n".join(rows[-lines:])


def _herdr_status(mi):
    try:
        r = subprocess.run([mi.get("herdr_bin") or "herdr", "agent", "get", mi["herdr_pane"]],
                           capture_output=True, text=True, timeout=4)
        d = json.loads(r.stdout or "{}").get("result") or {}
        a = d.get("agent") if isinstance(d.get("agent"), dict) else d
        return a.get("agent_status") or "", True
    except Exception:
        return "", False


IMPORT_CLIP = 1500                # riwayat lama: output/isi tool dipersingkat agar cepat sampai ke HP


def _slim(e):
    t = e.get("t")
    if t == "tool_result" and isinstance(e.get("out"), str):
        e["out"] = clip(e["out"], IMPORT_CLIP)
    elif t == "tool" and isinstance(e.get("input"), dict):
        e["input"] = {k: clip(v, IMPORT_CLIP) if isinstance(v, str) else v for k, v in e["input"].items()}
    return e


def _import_history(agent, path):
    """Seluruh riwayat transkrip (dibaca per baris) + offset byte setelah baris lengkap terakhir.
    Untuk transkrip raksasa hanya IMPORT_BYTES terakhir yang dibaca, dan paling banyak IMPORT_EVENTS event."""
    if not path or not os.path.exists(path):
        return [], 0
    import collections
    size = os.path.getsize(path)
    start = max(0, size - IMPORT_BYTES)
    conv = TranscriptConverter(agent)
    groups, count, pos = collections.deque(), 0, start
    with open(path, "rb") as f:
        f.seek(start)
        if start > 0:
            pos += len(f.readline())   # baris pertama terpotong
        for raw in f:
            if not raw.endswith(b"\n"):
                break                  # baris terakhir belum lengkap: dibaca live nanti
            pos += len(raw)
            if not raw.strip():
                continue
            g = [_slim(e) for e in conv.convert(raw.decode("utf-8", "replace")) if e.get("t") != "status"]
            if not g:
                continue
            groups.append(g)
            count += len(g)
            while count > IMPORT_EVENTS and len(groups) > 1:
                count -= len(groups.popleft())
    events = [e for g in groups for e in g]
    while events and events[0].get("t") in ("tool_result", "done"):  # buang potongan giliran yang terpotong
        events.pop(0)
    return events, pos


def _place(f):
    if f["where"] == "herdr":
        return "herdr " + (f.get("herdr") or {}).get("pane", "")
    if f["where"] == "tmux":
        t = f.get("tmux") or {}
        return "tmux %s:%s.%s" % (t.get("session"), t.get("window"), t.get("pane"))
    return "terminal " + (f.get("tty") or "")


def cmd_mirror(args):
    # mirror <pid> [herdr_pane]
    pid = int(args[0] or 0)
    pane = args[1] if len(args) > 1 else ""
    found, herdr_bin = _running()
    f = next((x for x in found if (pid and x["pid"] == pid) or (not pid and pane and (x.get("herdr") or {}).get("pane") == pane)), None)
    if f is None:
        raise SystemExit("this session is no longer running")
    sid, tr = transcript_of(f["agent"], f["pid"], f.get("session") or "", f["cwd"], f.get("started") or 0)
    key = (f["pid"], (f.get("herdr") or {}).get("pane", ""))
    # Satu proses / satu sesi = satu chat. Dulu chat hanya dipakai ulang bila id sesinya sama persis,
    # sehingga setelah /compact, /clear, atau claude dibuka ulang dengan --resume, membuka sesi ini lagi
    # membuat chat baru sementara yang lama tetap tampil (chat ganda).
    cands = []
    if os.path.isdir(ROOT):
        for cid in os.listdir(ROOT):
            m = read_meta(cid) or {}
            mi = m.get("mirror") or {}
            if not mi or m.get("superseded") or m.get("agent") != f["agent"]:
                continue
            same_proc = (mi.get("pid") or 0, mi.get("herdr_pane") or "") == key
            same_sess = bool(sid) and m.get("session", "") == sid
            if same_proc or same_sess:
                cands.append((bool(mi.get("full") or mi.get("owned")), same_proc and same_sess, m.get("updated") or 0, cid, m))
    cands.sort(reverse=True)
    old = [c[3] for c in cands]
    t = f.get("tmux") or {}
    def _stop_wait(c):
        stop_worker(c)
        for _ in range(30):
            if not worker_alive(c):
                break
            time.sleep(0.1)

    if cands and cands[0][0]:
        keep, m = cands[0][3], cands[0][4]
        mi = dict(m.get("mirror") or {})
        if (mi.get("pid") or 0, mi.get("herdr_pane") or "") != key:
            # proses baru untuk sesi yang sama (mis. claude --resume): ikuti proses barunya
            _stop_wait(keep)
            m = read_meta(keep) or m
            mi = dict(m.get("mirror") or {})
            mi.update(where=f["where"], pid=f["pid"], herdr_pane=(f.get("herdr") or {}).get("pane", ""),
                      tmux="=%s:%s.%s" % (t["session"], t["window"], t["pane"]) if t else "", place=_place(f))
            if herdr_bin:
                mi["herdr_bin"] = herdr_bin
            if tr and tr != mi.get("transcript") and m.get("session", "") != sid:
                mi.update(transcript=tr, offset=0)
                m["session"] = sid
            m["mirror"] = mi
            write_meta(keep, m)
        for o in old[1:]:  # duplikat dari versi sebelumnya: sembunyikan, arahkan ke chat yang dipakai
            _stop_wait(o)
            om = read_meta(o)
            if om:
                om["superseded"] = keep
                write_meta(o, om)
        spawn_worker(keep)
        print(jdump({"id": keep}))
        return
    mi = {"where": f["where"], "pid": f["pid"], "herdr_pane": (f.get("herdr") or {}).get("pane", ""),
          "herdr_bin": herdr_bin or "", "tmux": "=%s:%s.%s" % (t["session"], t["window"], t["pane"]) if t else "",
          "place": _place(f), "transcript": tr, "offset": 0, "full": True}
    events, mi["offset"] = _import_history(f["agent"], tr)
    cid = time.strftime("%Y%m%d-%H%M%S-") + uuid.uuid4().hex[:6]
    os.makedirs(chat_dir(cid), mode=0o700)
    first_user = next((e["text"] for e in events if e["t"] == "user"), "")
    meta = {"id": cid, "agent": f["agent"], "cwd": f["cwd"], "title": (f.get("title") or first_user.splitlines()[0] if first_user else f.get("title") or "")[:80],
            "model": "", "mode": "", "session": sid, "created": now_ms(), "updated": now_ms(), "status": "idle",
            "cost": 0.0, "tokens_in": 0, "tokens_out": 0, "mirror": mi}
    write_meta(cid, meta)
    with open(os.path.join(chat_dir(cid), "events.jsonl"), "w", encoding="utf-8") as fh:
        n = 0
        for e in events:
            e["n"], e["at"] = n, now_ms()
            fh.write(jdump(e) + "\n")
            n += 1
    for o in old:
        stop_worker(o)
        for _ in range(30):
            if not worker_alive(o):
                break
            time.sleep(0.1)
        m = read_meta(o)
        if m:
            m["superseded"] = cid
            write_meta(o, m)
    spawn_worker(cid)
    print(jdump({"id": cid}))


class MirrorAdapter:
    """Mengikuti sesi agent di tmux/herdr: transkrip → event chat, pesan → diketik ke pane-nya."""

    POLL = 0.6

    def __init__(self, w):
        self.w = w
        self.mi = dict(w.meta.get("mirror") or {})
        self.agent = w.meta["agent"]
        self.conv = TranscriptConverter(self.agent)
        self.sent = []
        self.stopped = threading.Event()
        self.perm = None           # rid kartu izin yang sedang terbuka
        self.perm_n = 0
        self.screen = ""
        self.ext = ""              # status terakhir dari luar (working/idle/blocked)
        self.grace = 0.0           # setelah mengirim: jangan langsung dianggap idle
        self.gone = False
        self.saved_at = 0.0
        self.partial = b""
        self.pending = []          # pesan yang menunggu agent dibuka lagi di terminalnya
        self.reviving = False
        self.ready_at = 0.0
        self.lock = threading.Lock()
        threading.Thread(target=self._loop, daemon=True).start()

    # -- target
    def _alive(self):
        if self.mi.get("pid"):
            return _proc_is(self.mi["pid"], self.agent)
        if self.mi.get("where") == "herdr" and self.mi.get("herdr_pane"):
            return _herdr_status(self.mi)[1]
        return False

    def _interactive(self):
        return self.mi.get("where") in ("tmux", "herdr") and not self.gone

    def _keys(self, *keys):
        """Tombol dengan nama netral: enter, down, esc, atau satu karakter."""
        if self.mi.get("where") == "herdr":
            subprocess.run([self.mi.get("herdr_bin") or "herdr", "agent", "send-keys", self.mi["herdr_pane"], *keys],
                           capture_output=True, timeout=5)
        else:
            names = {"enter": "Enter", "down": "Down", "up": "Up", "esc": "Escape"}
            for k in keys:
                subprocess.run(["tmux", "send-keys", "-t", self.mi["tmux"], names.get(k, k)], capture_output=True, timeout=5)
                time.sleep(0.08)

    def _type(self, text):
        if self.mi.get("where") == "herdr":
            r = subprocess.run([self.mi.get("herdr_bin") or "herdr", "agent", "prompt", self.mi["herdr_pane"], text],
                               capture_output=True, text=True, timeout=15)
            if r.returncode != 0:
                raise RuntimeError((r.stderr or r.stdout or "herdr prompt failed").strip()[:300])
        else:
            subprocess.run(["tmux", "load-buffer", "-b", "termx", "-"], input=text.encode(), capture_output=True, timeout=5, check=True)
            subprocess.run(["tmux", "paste-buffer", "-d", "-p", "-b", "termx", "-t", self.mi["tmux"]], capture_output=True, timeout=5, check=True)
            time.sleep(0.25)
            subprocess.run(["tmux", "send-keys", "-t", self.mi["tmux"], "Enter"], capture_output=True, timeout=5, check=True)

    # -- perintah dari aplikasi
    def send(self, text):
        # Pesan selalu masuk ke terminal aslinya; TermX tidak pernah membuat sesi terpisah.
        if self._interactive() and self._alive():
            return self._deliver(text)
        with self.lock:
            self.pending.append(text)
            start = not self.reviving
            self.reviving = True
        self.w.set_busy(True)
        if start:
            threading.Thread(target=self._revive, daemon=True).start()

    def _deliver(self, text):
        if self.mi.get("owned"):  # agent baru dijalankan: beri waktu tampilan terminalnya siap
            wait = 6 - (time.time() - (self.w.meta.get("created", 0) / 1000.0))
            if wait > 0:
                time.sleep(min(wait, 6))
        if self.ready_at > time.time():
            time.sleep(min(self.ready_at - time.time(), 10))
        self.sent.append(text)
        self.grace = time.time() + 8
        try:
            self._type(text)
        except Exception as e:
            self.sent.remove(text)
            self.w.emit({"t": "error", "msg": "could not send to %s: %s" % (self.mi.get("place"), e)})
            self.w.set_busy(False)

    def _revive(self):
        err = ""
        try:
            ok = self._retarget() or self._reopen()
        except Exception as e:
            ok, err = False, str(e)
        with self.lock:
            texts, self.pending = self.pending, []
            self.reviving = False
        if not ok:
            self.w.emit({"t": "error", "msg": err or "The agent isn't running in its terminal any more. Start it there again."})
            return self.w.set_busy(False)
        for t in texts:
            self._deliver(t)

    def _follow(self, f, herdr_bin=""):
        """Ikuti proses agent f (pane yang sama atau sesi yang sama dibuka lagi)."""
        where, tgt = _pane_target(f)
        self.mi.update(where=where, pid=f.get("pid") or 0, herdr_pane=tgt if where == "herdr" else "",
                       tmux=tgt if where == "tmux" else "", place=_place(f))
        if herdr_bin:
            self.mi["herdr_bin"] = herdr_bin
        if self.mi.get("owned") and not tgt.startswith("=%s:" % self.mi["owned"]):
            self.mi.pop("owned", None)
        sid = f.get("session") or ""
        if self.agent == "codex" and sid and sid != self.w.meta.get("session"):
            self.w.save_meta(session=sid)
            self.mi.update(transcript="", offset=0)
        self.gone = False
        self.w.save_meta(mirror=self.mi)

    def _retarget(self):
        """Agent sudah berjalan lagi (sesi yang sama, atau di pane yang sama): ikuti, jangan buka yang baru."""
        found, herdr_bin = _running()
        sess = self.w.meta.get("session") or ""
        mine = ("herdr", self.mi.get("herdr_pane")) if self.mi.get("where") == "herdr" else ("tmux", self.mi.get("tmux"))
        cands = [f for f in found if f["agent"] == self.agent and f["where"] in ("tmux", "herdr")]
        f = next((f for f in cands if sess and f.get("session") == sess), None) \
            or next((f for f in cands if mine[1] and _pane_target(f) == mine), None)
        if f is None:
            return False
        self._follow(f, herdr_bin)
        return True

    def _wait_ready(self, pid):
        """Tunggu tampilan agent siap menerima ketikan setelah dibuka lagi."""
        if self.agent == "claude" and pid:
            for _ in range(40):
                if _claude_session(pid).get("status") == "idle":
                    break
                time.sleep(0.5)
            self.ready_at = time.time() + 1.5
        else:
            self.ready_at = time.time() + 4

    def _reopen(self):
        """Buka lagi sesi yang sama (resume) di terminal aslinya; bila pane-nya sudah tidak ada, di tmux baru."""
        args = _resume_args(self.agent, self.w.meta.get("session") or "")
        if args is None:
            raise RuntimeError("The agent in the terminal has exited and this session can't be reopened from TermX. Start it again in its terminal.")
        binp = _agent_bin(self.agent)
        if not binp:
            raise RuntimeError("agent not found: " + self.agent)
        if self.mi.get("where") == "other" and self.mi.get("pid") and _proc_is(self.mi["pid"], self.agent):
            raise RuntimeError("This session runs in a plain terminal, so TermX can't type into it. Run it inside tmux or herdr.")
        cwd = self.w.meta.get("cwd") or os.path.expanduser("~")
        where = self.mi.get("where")
        if where == "herdr" and self.mi.get("herdr_pane") and self._reopen_herdr(args):
            return True
        if where == "tmux" and self.mi.get("tmux") and self._reopen_tmux(binp, args, cwd):
            return True
        # Terminal aslinya sudah ditutup: lanjutkan sesi yang sama di tmux baru (tetap terlihat di terminal).
        mi = _new_in_tmux(self.agent, cwd, "", "", args)
        for k in ("where", "pid", "herdr_pane", "tmux", "place", "owned"):
            self.mi[k] = mi[k]
        self.gone = False
        self.w.save_meta(mirror=self.mi)
        self.w.emit({"t": "note", "msg": "The original terminal is closed, so the session was reopened in %s "
                                         "(attach with: tmux attach -t %s)." % (mi["place"], mi["owned"])})
        self._wait_ready(mi["pid"])
        return True

    def _reopen_tmux(self, binp, args, cwd):
        tgt = self.mi["tmux"]
        r = subprocess.run(["tmux", "display-message", "-p", "-t", tgt, "#{pane_dead}\t#{pane_current_command}"],
                           capture_output=True, text=True, timeout=5)
        dead, _, cur = (r.stdout or "").strip().partition("\t")
        if r.returncode != 0 or dead == "1" or cur not in SHELLS:
            return False  # pane sudah tidak ada / sedang menjalankan program lain: jangan mengetik ke sana
        line = "cd %s && %s" % (shlex.quote(cwd), _tmux_cmd_for(self.agent, binp, "", "", args))
        subprocess.run(["tmux", "send-keys", "-t", tgt, "C-u"], capture_output=True, timeout=5, check=True)
        subprocess.run(["tmux", "send-keys", "-t", tgt, "-l", line], capture_output=True, timeout=5, check=True)
        subprocess.run(["tmux", "send-keys", "-t", tgt, "Enter"], capture_output=True, timeout=5, check=True)
        for _ in range(50):  # tunggu agent muncul di pane itu (maks ~25 detik)
            time.sleep(0.5)
            found, herdr_bin = _running()
            f = next((x for x in found if x["agent"] == self.agent and _pane_target(x) == ("tmux", tgt)), None)
            if f:
                self._follow(f, herdr_bin)
                self.w.emit({"t": "note", "msg": "Reopened the session in %s." % self.mi.get("place")})
                self._wait_ready(f["pid"])
                return True
        raise RuntimeError("Typed the resume command into %s, but the agent didn't start." % self.mi.get("place"))

    def _reopen_herdr(self, args):
        hb = self.mi.get("herdr_bin") or shutil.which("herdr") or "herdr"
        kind = {"antigravity": "agy"}.get(self.agent, self.agent)
        r = subprocess.run([hb, "agent", "start", self.agent, "--kind", kind, "--pane", self.mi["herdr_pane"],
                            "--timeout", "40000", "--", *args], capture_output=True, text=True, timeout=60)
        if r.returncode != 0:
            return False  # pane tidak di prompt shell / sudah ditutup
        found, herdr_bin = _running()
        f = next((x for x in found if x["agent"] == self.agent and _pane_target(x) == ("herdr", self.mi["herdr_pane"])), None)
        if f is None:
            return False
        self._follow(f, herdr_bin or hb)
        self.w.emit({"t": "note", "msg": "Reopened the session in %s." % self.mi.get("place")})
        self._wait_ready(f.get("pid"))
        return True

    def permission(self, rid, allow, always):
        if rid != self.perm:
            return
        keys = _menu_keys(_screen(self.mi, 40), allow, always)
        if keys is None:  # menu tidak terbaca: jangan menebak pilihan
            self.w.emit({"t": "note", "msg": "Couldn't read the choices on screen. Open the session in a terminal to answer."})
            return
        try:
            self._keys(*keys)
        except Exception as e:
            self.w.emit({"t": "error", "msg": "could not answer: %s" % e})
            return
        self.perm = None
        self.w.emit({"t": "perm_done", "rid": rid, "allow": allow})

    def set_model(self, model):
        # Claude Code menerima "/model <nama>" langsung di sesinya.
        if self.agent == "claude" and model and re.match(r"^[\w.\[\]-]+$", model) and self._interactive() and self._alive():
            try:
                self._type("/model " + model)
                self.w.emit({"t": "model", "model": model})
            except Exception as e:
                self.w.emit({"t": "error", "msg": "could not change the model: %s" % e})
            return
        self.w.emit({"t": "note", "msg": "Change the model in the agent's own session (e.g. /model)."})

    def set_mode(self, mode):
        self.w.emit({"t": "note", "msg": "Change the mode in the agent's own session."})

    def interrupt(self):
        if self._interactive():
            try:
                self._keys("esc")
            except Exception:
                pass

    def close(self):
        self.stopped.set()
        self._save(force=True)

    # -- mengikuti
    def _save(self, force=False):
        if force or time.time() - self.saved_at > 4:
            self.saved_at = time.time()
            if not self.stopped.is_set() or force:
                if self.w.meta.get("mirror") is not None:
                    self.w.save_meta(mirror=self.mi)

    def _emit_events(self, events):
        for e in events:
            t = e.get("t")
            if t == "user":
                if e["text"] in self.sent:  # pesan dari aplikasi sudah tampil
                    self.sent.remove(e["text"])
                    continue
                self.w.emit(e)
                if not self.w.busy:
                    self.w.set_busy(True)
            elif t == "status":
                if not self.w.busy:
                    self.w.set_busy(True)
            elif t == "done":
                self._close_perm(True)
                self.w.emit(e)
                if self.w.busy:
                    self.w.set_busy(False)
            else:
                self.w.emit(e)

    def _close_perm(self, allow):
        if self.perm:
            self.w.emit({"t": "perm_done", "rid": self.perm, "allow": allow})
            self.perm = None

    def _follow_transcript(self):
        if self.agent == "claude" and self.mi.get("pid"):
            sid = _claude_session(self.mi["pid"]).get("sessionId")
            if sid and sid != self.w.meta.get("session"):  # /clear atau /resume di terminal: sesi baru
                tr = _claude_transcript(sid)
                if tr:
                    had = bool(self.w.meta.get("session"))
                    self.w.save_meta(session=sid)
                    self.mi.update(transcript=tr, offset=0)
                    self.partial = b""
                    if had:
                        self.w.emit({"t": "note", "msg": "A new conversation started in the terminal."})
        if self.agent == "codex" and not self.mi.get("transcript"):
            sid, tr = transcript_of("codex", self.mi.get("pid"), self.w.meta.get("session"), self.w.meta["cwd"], 0)
            if tr:
                self.w.save_meta(session=sid)
                self.mi.update(transcript=tr, offset=0)
        path = self.mi.get("transcript")
        if not path:
            return False
        try:
            size = os.path.getsize(path)
        except OSError:
            return True
        off = self.mi.get("offset") or 0
        if size < off:
            off = 0
        if size == off:
            return True
        with open(path, "rb") as f:
            f.seek(off)
            data = f.read(min(size - off, 4 * 1024 * 1024))
        self.mi["offset"] = off + len(data)
        data = self.partial + data
        cut = data.rfind(b"\n") + 1
        self.partial = data[cut:]
        for line in data[:cut].decode("utf-8", "replace").splitlines():
            if line.strip():
                self._emit_events(self.conv.convert(line))
        self._save()
        return True

    def _status(self):
        """working / idle / blocked dari herdr atau berkas sesi Claude; '' bila tak diketahui."""
        if self.mi.get("where") == "herdr" and self.mi.get("herdr_pane"):
            s, ok = _herdr_status(self.mi)
            return s
        if self.agent == "claude" and self.mi.get("pid"):
            s = _claude_session(self.mi["pid"]).get("status") or ""
            return "working" if s == "busy" else s
        return ""

    def _loop(self):
        tick = 0
        has_transcript = True
        while not self.stopped.is_set():
            time.sleep(self.POLL)
            tick += 1
            try:
                has_transcript = self._follow_transcript()
                if tick % 2:
                    continue
                if not self._alive():
                    if self.reviving:
                        continue
                    if not self.gone:
                        self.gone = True
                        self._close_perm(False)
                        self.w.emit({"t": "note", "msg": "The agent in the terminal has exited. Send a message to reopen this session there."})
                        if self.w.busy:
                            self.w.set_busy(False)
                    elif tick % 16 == 0:  # dibuka lagi sendiri di terminal (mis. claude --resume): ikuti
                        with self.lock:
                            busy = self.reviving
                        if not busy and self._retarget():
                            self.w.emit({"t": "note", "msg": "Following the session again in %s." % self.mi.get("place")})
                    continue
                st = self._status()
                screen = ""
                if self._interactive() and (not has_transcript or st in ("working", "blocked", "") or self.perm):
                    screen = _screen(self.mi, 40)
                blocked = st == "blocked" or (self._interactive() and st != "idle" and bool(BLOCKED_RE.search("\n".join(screen.splitlines()[-14:]))))
                if not has_transcript and self.agent not in ("claude", "codex") and screen and screen != self.screen:
                    self.screen = screen
                    self.screen_at = time.time()
                    self.w.emit({"t": "screen", "text": clip(screen, 5000)})
                if blocked and not self.perm:
                    self.perm_n += 1
                    self.perm = "s%d-%d" % (int(time.time()), self.perm_n)
                    lines = [x.strip() for x in (screen or _screen(self.mi, 40)).splitlines()
                             if x.strip() and not re.match(r"^[\s─━═│┃╭╮╰╯┌┐└┘-]+$", x)][-14:]
                    self.w.emit({"t": "perm", "rid": self.perm, "tool": "Approval in %s" % self.mi.get("place"),
                                 "desc": "", "input": {"summary": "\n".join(lines)}, "always": self.agent in ("claude", "codex")})
                    if not self.w.busy:
                        self.w.set_busy(True)
                elif not blocked and self.perm:
                    self._close_perm(True)
                if self.perm:
                    pass  # menunggu jawaban: tetap "working"
                elif st == "working" and not self.w.busy:
                    self.w.set_busy(True)
                elif st in ("idle", "done") and self.w.busy and time.time() > self.grace and not has_transcript:
                    self.w.set_busy(False)
                elif not st and not has_transcript and self.w.busy and time.time() > self.grace \
                        and time.time() - getattr(self, "screen_at", 0) > 4:
                    self.w.set_busy(False)  # tanpa sumber status: layar diam = selesai
                elif st in ("idle", "done") and self.w.busy and time.time() > self.grace + 20:
                    self.w.set_busy(False)  # transkrip tidak mencatat akhir giliran (mis. dibatalkan)
                self.ext = st
            except Exception as e:
                self.w.emit({"t": "stderr", "msg": "mirror: %s" % e})


# ------------------------------------------------------------------ chat yang juga tampil di terminal (tmux)

def _tmux_cmd_for(agent, binp, model, mode, extra=()):
    cmd = [binp]
    if agent == "claude":
        if model:
            cmd += ["--model", model]
        if mode and mode != "manual":
            cmd += ["--permission-mode", "default" if mode == "manual" else mode]
    elif agent == "codex" and model:
        cmd += ["-m", model]
    cmd += list(extra)
    return " ".join(shlex.quote(c) for c in cmd)


# Membuka lagi sesi yang sama di terminal (bukan sesi baru): argumen resume per agent.
RESUME_ARGS = {"claude": ["--resume", "{s}"], "codex": ["resume", "{s}"], "opencode": ["--session", "{s}"]}
SHELLS = ("bash", "zsh", "sh", "fish", "dash", "ksh", "tcsh", "csh")


def _resume_args(agent, session):
    tpl = RESUME_ARGS.get(agent)
    if not tpl or not session or not re.match(r"^[\w.-]+$", session):
        return None
    return [a.replace("{s}", session) for a in tpl]


def _agent_bin(agent):
    entry = next((a for a in CATALOG if a["id"] == agent), None)
    return first_bin(*(entry["bins"] if entry else [agent]))


def _pane_target(f):
    """(where, target) pane tempat agent berjalan: tmux '=sesi:jendela.pane' atau id pane herdr."""
    if f.get("where") == "tmux" and f.get("tmux"):
        t = f["tmux"]
        return "tmux", "=%s:%s.%s" % (t["session"], t["window"], t["pane"])
    if f.get("where") == "herdr":
        return "herdr", (f.get("herdr") or {}).get("pane", "")
    return "", ""


def _new_in_tmux(agent, cwd, model, mode, extra=()):
    """Jalankan agent interaktif di sesi tmux baru, lalu ikuti sebagai chat (lihat MirrorAdapter)."""
    if not shutil.which("tmux"):
        raise SystemExit("tmux is not installed on this server")
    binp = _agent_bin(agent)
    if not binp:
        raise SystemExit("agent not found: " + agent)
    name = "termx-" + uuid.uuid4().hex[:6]
    env_path()
    subprocess.run(["tmux", "new-session", "-d", "-s", name, "-x", "160", "-y", "48", "-c", cwd,
                    _tmux_cmd_for(agent, binp, model, mode, extra)], check=True, capture_output=True, timeout=10)
    pid = 0
    for _ in range(60):  # tunggu proses agent muncul di pane (maks ~6 detik)
        time.sleep(0.1)
        procs = _procs()
        pane_pid = next((pp for pp, info in _tmux_panes().items() if info["session"] == name), 0)
        if not pane_pid:
            continue
        if _agent_of(procs.get(pane_pid, {}).get("argv")) == agent:
            pid = pane_pid
            break
        kids = [p for p, q in procs.items() if q["ppid"] == pane_pid and _agent_of(q["argv"]) == agent]
        if kids:
            pid = kids[0]
            break
    if not pid:
        subprocess.run(["tmux", "kill-session", "-t", "=" + name], capture_output=True)
        raise SystemExit("the agent did not start in tmux")
    mi = {"where": "tmux", "pid": pid, "herdr_pane": "", "herdr_bin": "", "tmux": "=%s:0.0" % name,
          "place": "tmux " + name, "transcript": "", "offset": 0, "owned": name}
    return mi


# ------------------------------------------------------------------ pemakaian & akun per provider
# Hanya dari sumber resmi milik agent: perintah status CLI, kejadian rate limit yang dilaporkan agent,
# dan biaya yang dicatat TermX. Kredensial tidak pernah dibaca.

def _run_text(cmd, timeout=15):
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout, stdin=subprocess.DEVNULL)
        return (r.stdout or "") + (r.stderr or "")
    except Exception:
        return ""


def _tail_lines(path, nbytes=256 * 1024):
    try:
        size = os.path.getsize(path)
        with open(path, "rb") as f:
            f.seek(max(0, size - nbytes))
            data = f.read()
        return data.decode("utf-8", "replace").splitlines()[1 if size > nbytes else 0:]
    except OSError:
        return []


def _claude_usage(binp):
    u = {"limits": None, "limits_at": 0}
    out = _run_text([binp, "auth", "status"])
    try:
        j = json.loads(out[out.index("{"):out.rindex("}") + 1])
    except Exception:
        j = {}
    u["auth"] = {"logged_in": bool(j.get("loggedIn")), "method": j.get("authMethod") or "",
                 "plan": j.get("subscriptionType") or "", "provider": j.get("apiProvider") or "",
                 "account": j.get("email") or "", "org": j.get("orgName") or "", "expires": j.get("loginExpiresAt") or ""}
    # kuota terbaru yang dilaporkan Claude Code ke chat TermX mana pun
    best = None
    if os.path.isdir(ROOT):
        for cid in os.listdir(ROOT):
            p = os.path.join(ROOT, cid, "events.jsonl")
            if not os.path.exists(p):
                continue
            for line in reversed(_tail_lines(p, 128 * 1024)):
                if '"t":"limits"' in line or '"t": "limits"' in line:
                    try:
                        e = json.loads(line)
                    except Exception:
                        continue
                    if best is None or e.get("at", 0) > best.get("at", 0):
                        best = e
                    break
    if best:
        u["limits"], u["limits_at"] = best.get("windows"), best.get("at", 0)
    return u


def _codex_usage(binp):
    out = _run_text([binp, "login", "status"]).strip().splitlines()
    u = {"auth": {"logged_in": bool(out) and not out[0].lower().startswith("not logged"), "method": out[0] if out else ""},
         "limits": None, "limits_at": 0}
    import glob
    files = sorted(glob.glob(os.path.expanduser("~/.codex/sessions/*/*/*/rollout-*.jsonl")), key=os.path.getmtime, reverse=True)
    for f in files[:10]:
        for line in reversed(_tail_lines(f)):
            if '"rate_limits"' not in line:
                continue
            try:
                o = json.loads(line)
            except Exception:
                continue
            rl = (o.get("payload") or {}).get("rate_limits") or {}
            ts = o.get("timestamp") or ""
            try:
                import datetime
                at = int(datetime.datetime.fromisoformat(ts.replace("Z", "+00:00")).timestamp() * 1000)
            except Exception:
                at = int(os.path.getmtime(f) * 1000)
            wins = {}
            for k in ("primary", "secondary"):
                w = rl.get(k)
                if not isinstance(w, dict):
                    continue
                mins = w.get("window_minutes") or 0
                key = ("five_hour" if mins == 300 else "seven_day" if mins == 10080 else "%dm" % mins) if mins else k
                reset = w.get("resets_at") or (at // 1000 + int(w["resets_in_seconds"]) if w.get("resets_in_seconds") else None)
                up = w.get("used_percent")
                wins[key] = {"used": up / 100.0 if isinstance(up, (int, float)) else None, "reset": reset}  # pecahan 0..1
            if wins:
                u["limits"], u["limits_at"] = wins, at
                return u
    return u


def _opencode_usage(binp):
    stats = []
    for line in _run_text([binp, "stats"], timeout=15).splitlines():
        m = re.match(r"^\s*│\s*(.+?)\s{2,}(\S.*?)\s*│\s*$", line)
        if m and m.group(1).strip() and not m.group(1).strip().isupper():
            stats.append([m.group(1).strip(), m.group(2).strip()])
    return {"auth": {}, "limits": None, "limits_at": 0, "stats": stats[:12]}


USAGE_READERS = {"claude": _claude_usage, "codex": _codex_usage, "opencode": _opencode_usage}


def cmd_usage(args):
    # "usage cached": hasil terakhir seketika (bisa basi; HP lalu meminta yang segar di latar).
    if args and args[0] == "cached":
        c = _load_json(USAGE_CACHE)
        if c:
            c["cached"] = True
            print(jdump(c))
            return
    totals = {}
    if os.path.isdir(ROOT):
        for cid in os.listdir(ROOT):
            m = read_meta(cid)
            if not m:
                continue
            t = totals.setdefault(m.get("agent"), {"cost": 0.0, "tokens_in": 0, "tokens_out": 0, "chats": 0})
            t["cost"] += m.get("cost") or 0.0
            t["tokens_in"] += m.get("tokens_in") or 0
            t["tokens_out"] += m.get("tokens_out") or 0
            t["chats"] += 1
    results, threads = {}, []
    agents = [(a, first_bin(*a["bins"])) for a in CATALOG]
    agents = [(a, p) for a, p in agents if p]

    def work(a, p):
        try:
            results[a["id"]] = USAGE_READERS[a["id"]](p) if a["id"] in USAGE_READERS else {}
        except Exception as e:
            results[a["id"]] = {"error": str(e)[:200]}

    for a, p in agents:
        th = threading.Thread(target=work, args=(a, p), daemon=True)
        th.start()
        threads.append(th)
    for th in threads:
        th.join(20)
    out = []
    for a, _p in agents:
        r = dict(results.get(a["id"]) or {})
        r.update({"agent": a["id"], "name": a["name"], "reported": a["id"] in USAGE_READERS,
                  **{k: round(v, 6) if isinstance(v, float) else v for k, v in (totals.get(a["id"]) or {}).items()}})
        out.append(r)
    res = {"providers": out, "at": now_ms()}
    _save_json(USAGE_CACHE, res)
    print(jdump(res))


# ---------------------------------------------------------------- setup Hermes dari aplikasi
# Memakai API Hermes sendiri (hermes_cli.config / auth / models) di Python milik Hermes, sama seperti
# alur `hermes model`: kunci API ke ~/.hermes/.env, provider & model ke config.yaml. Kunci API hanya
# lewat stdin, tidak pernah di argumen proses, dan tidak pernah dikirim balik ke aplikasi.
HERMES_HELPER = r"""
import json, sys
op = sys.argv[1]
from hermes_cli import auth as A
from hermes_cli import config as C
OAUTH = ["anthropic", "nous", "openai-codex", "xai-oauth", "qwen-oauth", "minimax-oauth"]
def pooled(pid):
    try:
        from agent.credential_pool import load_pool
        return bool(load_pool(pid).entries())
    except Exception:
        return False
def provs():
    out = [{"id": "openrouter", "name": "OpenRouter", "env": "OPENROUTER_API_KEY", "base_url": "", "auth": "api_key"}]
    seen = set()
    for pid, pc in A.PROVIDER_REGISTRY.items():
        envs = list(getattr(pc, "api_key_env_vars", ()) or ())
        sig = (envs[0] if envs else pid, getattr(pc, "inference_base_url", "") or "")
        if sig in seen:  # alias provider yang sama (mis. fw = fireworks)
            continue
        seen.add(sig)
        out.append({"id": pid, "name": getattr(pc, "name", pid) or pid, "env": envs[0] if envs else "",
                    "base_url": getattr(pc, "inference_base_url", "") or "", "auth": getattr(pc, "auth_type", "") or ""})
    return out
if op == "status":
    cfg = C.load_config()
    m = cfg.get("model")
    m = m if isinstance(m, dict) else ({"default": m} if m else {})
    ps = provs()
    keys = {}
    for p in ps:
        if p["env"]:
            keys[p["id"]] = bool(C.get_env_value(p["env"]))
    try:
        active = (A._load_auth_store() or {}).get("active_provider") or ""
    except Exception:
        active = ""
    oauth = {}
    for pid in OAUTH:
        try:
            oauth[pid] = bool((A.get_auth_status(pid) or {}).get("logged_in"))
        except Exception:
            oauth[pid] = False
    custom = []
    for c in (cfg.get("custom_providers") or []):
        if isinstance(c, dict) and c.get("base_url"):
            custom.append({"name": str(c.get("name") or ""), "base_url": str(c.get("base_url") or ""),
                           "model": str(c.get("model") or ""), "api_mode": str(c.get("api_mode") or ""),
                           "has_key": bool(c.get("api_key"))})
    print(json.dumps({"provider": str(m.get("provider") or ""), "model": str(m.get("default") or m.get("model") or ""),
                      "base_url": str(m.get("base_url") or ""), "api_mode": str(m.get("api_mode") or ""),
                      "active_oauth": active, "providers": ps, "keys": keys, "oauth": oauth, "custom": custom}))
elif op == "models":
    # katalog Hermes, lalu digabung dengan daftar langsung dari /models endpoint provider bila ada kunci
    from hermes_cli.models import provider_model_ids, probe_api_models
    pid = sys.argv[2]
    req = json.loads(sys.stdin.read() or "{}")
    try:
        ids = [str(i) for i in (provider_model_ids(pid) or [])]
    except Exception:
        ids = []
    live = False
    info = next((p for p in provs() if p["id"] == pid), None)
    if info and info["auth"] == "api_key":
        key = str(req.get("key") or "").strip() or (C.get_env_value(info["env"]) if info["env"] else "") or ""
        url = info["base_url"]
        if pid == "openrouter":
            from hermes_constants import OPENROUTER_BASE_URL
            url = OPENROUTER_BASE_URL
        if key and url.startswith("http"):
            try:
                got = (probe_api_models(key, url) or {}).get("models")
            except Exception:
                got = None
            if got:
                live = True
                seen = set(ids)
                ids += [str(i) for i in got if str(i) not in seen]
    print(json.dumps({"models": ids[:3000], "live": live}))
elif op == "probe":
    req = json.loads(sys.stdin.read() or "{}")
    url, key = str(req.get("base_url") or "").strip(), str(req.get("key") or "").strip()
    if not url.startswith(("http://", "https://")):
        raise SystemExit("base URL must start with http:// or https://")
    from hermes_cli.models import probe_api_models
    from hermes_cli.main import _auto_provider_name
    pr = probe_api_models(key, url) or {}
    models = pr.get("models")
    print(json.dumps({"ok": models is not None, "models": [str(i) for i in (models or [])][:3000],
                      "probed_url": str(pr.get("probed_url") or ""), "resolved_base_url": str(pr.get("resolved_base_url") or ""),
                      "suggested_base_url": str(pr.get("suggested_base_url") or ""), "used_fallback": bool(pr.get("used_fallback")),
                      "default_name": _auto_provider_name(pr.get("resolved_base_url") if pr.get("used_fallback") and pr.get("resolved_base_url") else url)}))
elif op == "apply":
    req = json.loads(sys.stdin.read() or "{}")
    pid, model = str(req.get("provider") or ""), str(req.get("model") or "").strip()
    key = str(req.get("key") or "").strip()
    if pid == "custom":
        # sama dengan _model_flow_custom milik `hermes model`
        from hermes_cli.main import _auto_provider_name, _save_custom_provider
        url = str(req.get("base_url") or "").strip().rstrip("/")
        if not url.startswith(("http://", "https://")):
            raise SystemExit("base URL must start with http:// or https://")
        if not model:
            raise SystemExit("model is required")
        api_mode = str(req.get("api_mode") or "").strip()
        if api_mode not in ("", "chat_completions", "codex_responses", "anthropic_messages"):
            raise SystemExit("api mode not valid")
        name = str(req.get("name") or "").strip() or _auto_provider_name(url)
        ctx = req.get("context_length")
        ctx = int(ctx) if isinstance(ctx, int) and ctx > 0 else None
        A._save_model_choice(model)
        cfg = C.load_config()
        m = cfg.get("model")
        if not isinstance(m, dict):
            m = {"default": m} if m else {}
            cfg["model"] = m
        m["provider"] = "custom"
        m["base_url"] = url
        if key:
            m["api_key"] = key
        else:
            m.pop("api_key", None)
        if api_mode:
            m["api_mode"] = api_mode
        else:
            m.pop("api_mode", None)
        C.save_config(cfg)
        A.deactivate_provider()
        _save_custom_provider(url, key, model, context_length=ctx, name=name, api_mode=api_mode or None)
        try:
            from hermes_cli.model_setup_flows import _prune_replaced_custom_model_config_credentials
            _prune_replaced_custom_model_config_credentials(url, provider_name=name)
        except Exception:
            pass
        print(json.dumps({"ok": True, "provider": "custom", "model": model}))
        sys.exit(0)
    info = next((p for p in provs() if p["id"] == pid), None)
    if not info or not model:
        raise SystemExit("provider or model not valid")
    if info["auth"] != "api_key":
        # provider OAuth: sama dengan alur `hermes model` setelah login (mis. _model_flow_openai_codex)
        if pid not in OAUTH or not (A.get_auth_status(pid) or {}).get("logged_in"):
            raise SystemExit("log in to this provider first")
        A._save_model_choice(model)
        A._update_config_for_provider(pid, info["base_url"], model)
        print(json.dumps({"ok": True, "provider": pid, "model": model}))
        sys.exit(0)
    if key:
        if not info["env"]:
            raise SystemExit("this provider has no API key setting")
        C.save_env_value(info["env"], key)
    elif info["env"] and not C.get_env_value(info["env"]) and not pooled(pid):
        raise SystemExit("API key is required")
    A._save_model_choice(model)
    cfg = C.load_config()
    m = cfg.get("model")
    if not isinstance(m, dict):
        m = {"default": m} if m else {}
        cfg["model"] = m
    m["provider"] = pid
    if pid == "openrouter":
        from hermes_constants import OPENROUTER_BASE_URL
        m["base_url"] = OPENROUTER_BASE_URL
        m["api_mode"] = "chat_completions"
    else:
        m["base_url"] = info["base_url"]
    C.clear_model_endpoint_credentials(m, clear_api_mode=False)
    C.save_config(cfg)
    A.deactivate_provider()
    print(json.dumps({"ok": True, "provider": pid, "model": model}))
"""


def _hermes_python():
    b = shutil.which("hermes")
    if not b:
        raise SystemExit("hermes is not installed on this server")
    try:
        with open(os.path.realpath(b), "rb") as f:
            first = f.readline().decode("utf-8", "replace").strip()
    except OSError:
        first = ""
    py = first[2:].strip().split()[0] if first.startswith("#!") else ""
    if not py or not os.path.isfile(py) or "python" not in os.path.basename(py):
        raise SystemExit("could not find the Python used by hermes")
    return py


HERMES_AUTH_SESS = "termx-hermes-auth"
HERMES_OAUTH = ("anthropic", "nous", "openai-codex", "xai-oauth", "qwen-oauth", "minimax-oauth")
_ANSI = re.compile(r"\x1b\[[0-9;?]*[A-Za-z]")


def _hermes_login(op, args):
    # Login OAuth berjalan di sesi tmux terpisah (`hermes auth add <p> --type oauth --no-browser`):
    # aplikasi membaca layar untuk URL/kode dan mengirim balasan (mis. kode otorisasi) lewat stdin.
    if not shutil.which("tmux"):
        raise SystemExit("tmux is required for login on this server")
    tgt = "=" + HERMES_AUTH_SESS
    pane = tgt + ":"  # target pane perlu pemisah jendela agar "=" (nama persis) diterima
    if op == "login":
        pid = args[1] if len(args) > 1 else ""
        if pid not in HERMES_OAUTH:
            raise SystemExit("this provider has no OAuth login")
        b = shutil.which("hermes")
        if not b:
            raise SystemExit("hermes is not installed on this server")
        subprocess.run(["tmux", "kill-session", "-t", tgt], capture_output=True)
        sh = (shlex.quote(b) + " auth add " + shlex.quote(pid) + " --type oauth --no-browser"
              + '; echo; echo "__TERMX_DONE_$?__"; sleep 900')
        r = subprocess.run(["tmux", "new-session", "-d", "-s", HERMES_AUTH_SESS, "-x", "220", "-y", "60", sh],
                           capture_output=True, text=True, cwd=os.path.expanduser("~"))
        if r.returncode != 0:
            raise SystemExit((r.stderr.strip() or "could not start login")[:300])
        print(jdump({"ok": True}))
    elif op == "login-status":
        r = subprocess.run(["tmux", "capture-pane", "-p", "-J", "-S", "-300", "-t", pane],
                           capture_output=True, text=True)
        if r.returncode != 0:
            print(jdump({"running": False, "done": True, "exit": -1, "lines": [], "urls": [], "codes": []}))
            return
        text = _ANSI.sub("", r.stdout)
        lines = [l.rstrip() for l in text.splitlines()]
        while lines and not lines[-1].strip():
            lines.pop()
        m = re.search(r"__TERMX_DONE_(\d+)__", text)
        body = "\n".join(l for l in lines if "__TERMX_DONE_" not in l)
        urls = list(dict.fromkeys(u.rstrip(".,)>]│") for u in re.findall(r"https?://[^\s│]+", body)))
        codes = list(dict.fromkeys(re.findall(r"\b[A-Z0-9]{4,5}-[A-Z0-9]{4,5}\b", body)))
        shown = [l for l in lines if "__TERMX_DONE_" not in l][-40:]
        print(jdump({"running": m is None, "done": m is not None, "exit": int(m.group(1)) if m else None,
                     "lines": shown, "urls": urls, "codes": codes}))
    elif op == "login-input":
        data = sys.stdin.read().strip()
        if not data:
            raise SystemExit("nothing to send")
        subprocess.run(["tmux", "load-buffer", "-b", "termxauth", "-"], input=data, text=True, capture_output=True)
        subprocess.run(["tmux", "paste-buffer", "-d", "-b", "termxauth", "-t", pane], capture_output=True)
        subprocess.run(["tmux", "send-keys", "-t", pane, "Enter"], capture_output=True)
        print(jdump({"ok": True}))
    else:  # login-cancel
        subprocess.run(["tmux", "kill-session", "-t", tgt], capture_output=True)
        print(jdump({"ok": True}))


def cmd_hermes_setup(args):
    # hermes-setup status | models <provider> | probe | apply (JSON lewat stdin)
    #              | login <provider> | login-status | login-input (stdin) | login-cancel
    op = args[0] if args else "status"
    if op in ("login", "login-status", "login-input", "login-cancel"):
        _hermes_login(op, args)
        return
    if op not in ("status", "models", "probe", "apply"):
        raise SystemExit("usage: hermes-setup status|models <provider>|probe|apply|login <provider>|login-status|login-input|login-cancel")
    argv = [_hermes_python(), "-c", HERMES_HELPER, op] + (args[1:2] if op == "models" else [])
    data = sys.stdin.read() if op in ("apply", "probe", "models") else ""
    r = subprocess.run(argv, input=data, capture_output=True, text=True,
                       timeout=60 if op in ("models", "probe") else 30, cwd=os.path.expanduser("~"))
    line = next((l for l in reversed(r.stdout.splitlines()) if l.startswith("{")), "")
    if r.returncode != 0 or not line:
        err = (r.stderr.strip().splitlines() or ["hermes setup failed"])[-1]
        raise SystemExit(err[:300])
    print(line)


COMMANDS = {
    "detect": cmd_detect, "list": cmd_list, "new": cmd_new, "attach": cmd_attach, "rm": cmd_rm,
    "stop": cmd_stop, "rename": cmd_rename, "running": cmd_running, "mirror": cmd_mirror, "usage": cmd_usage, "_worker": cmd_worker,
    "hermes-setup": cmd_hermes_setup,
}


def main():
    os.makedirs(ROOT, mode=0o700, exist_ok=True)
    if len(sys.argv) < 2 or sys.argv[1] not in COMMANDS:
        if len(sys.argv) > 1 and sys.argv[1] == "version":
            print(VERSION)
            return
        raise SystemExit("usage: agentd.py detect|list|new|attach|rm|stop|rename|running|mirror|usage|version")
    COMMANDS[sys.argv[1]](sys.argv[2:])


if __name__ == "__main__":
    main()
