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
import os
import shutil
import signal
import socket
import subprocess
import sys
import threading
import time
import uuid

VERSION = 2
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
    {"id": "codex", "name": "Codex", "bins": ["codex"],
     "models": [], "modes": ["workspace-write", "read-only"], "login": "codex login", "login_cmd": "{bin} login", "resume_cmd": "{bin} resume {session}"},
    {"id": "opencode", "name": "opencode", "bins": ["opencode"],
     "models": [], "modes": [], "login": "opencode auth login", "login_cmd": "{bin} auth login", "resume_cmd": "{bin} --session {session}"},
    {"id": "hermes", "name": "Hermes Agent", "bins": ["hermes"],
     "models": [], "modes": [], "login": "hermes setup", "login_cmd": "{bin} setup", "resume_cmd": "{bin}"},
    {"id": "pi", "name": "pi", "bins": ["pi"],
     "models": [], "modes": [], "login": "pi  →  /login", "login_cmd": "{bin}", "resume_cmd": "{bin} --session {session}"},
    {"id": "omp", "name": "oh-my-pi", "bins": ["omp"],
     "models": [], "modes": [], "login": "omp  →  /login", "login_cmd": "{bin}", "resume_cmd": "{bin} --session {session}"},
    {"id": "gemini", "name": "Gemini CLI", "bins": ["gemini"], "models": [], "modes": [], "login": "gemini"},
    {"id": "qwen", "name": "Qwen Code", "bins": ["qwen"], "models": [], "modes": [], "login": "qwen"},
    {"id": "aider", "name": "Aider", "bins": ["aider"], "models": [], "modes": [], "login": "aider"},
    {"id": "goose", "name": "Goose", "bins": ["goose"], "models": [], "modes": [], "login": "goose configure"},
    {"id": "crush", "name": "Crush", "bins": ["crush"], "models": [], "modes": [], "login": "crush"},
]


def cmd_detect(_args):
    found = []
    for a in CATALOG:
        p = first_bin(*a["bins"])
        if not p:
            continue
        kind = "chat" if a["id"] in ADAPTERS else "terminal"
        found.append({
            "id": a["id"], "name": a["name"], "bin": p, "version": version_of(p), "kind": kind,
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
            if m:
                m["alive"] = worker_alive(cid)
                out.append(m)
    out.sort(key=lambda m: m.get("updated", 0), reverse=True)
    print(jdump({"chats": out}))


def cmd_new(args):
    # new <agent> <cwd> [model] [mode]
    agent, cwd = args[0], os.path.expanduser(args[1] if len(args) > 1 else "~")
    if agent not in ADAPTERS:
        raise SystemExit("unsupported agent: " + agent)
    if not os.path.isdir(cwd):
        raise SystemExit("folder not found: " + cwd)
    cid = time.strftime("%Y%m%d-%H%M%S-") + uuid.uuid4().hex[:6]
    os.makedirs(chat_dir(cid), mode=0o700)
    meta = {
        "id": cid, "agent": agent, "cwd": os.path.abspath(cwd), "title": "",
        "model": args[2] if len(args) > 2 and args[2] else "", "mode": args[3] if len(args) > 3 and args[3] else "",
        "session": "", "created": now_ms(), "updated": now_ms(), "status": "idle",
        "cost": 0.0, "tokens_in": 0, "tokens_out": 0,
    }
    write_meta(cid, meta)
    spawn_worker(cid)
    print(jdump({"id": cid}))


def cmd_rm(args):
    cid = args[0]
    stop_worker(cid)
    shutil.rmtree(chat_dir(cid), ignore_errors=True)
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


def stop_worker(cid):
    try:
        with open(os.path.join(chat_dir(cid), "worker.pid")) as f:
            os.killpg(int(f.read().strip()), signal.SIGTERM)
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
    cid, start = args[0], int(args[1]) if len(args) > 1 else 0
    d = chat_dir(cid)
    if not os.path.isdir(d):
        print(jdump({"t": "gone"}), flush=True)
        return
    spawn_worker(cid)
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
        skipped = 0
        buf = ""
        last_data = time.time()
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
        self.adapter = ADAPTERS[self.meta["agent"]](self)

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
        if a.startswith("--resume=") or a.startswith("--session="):
            return re.sub(r"\.jsonl$", "", os.path.basename(a.split("=", 1)[1]))
    return ""


def cmd_running(_args):
    procs = _procs()
    panes = _tmux_panes()
    herdr_bin, herdr_list = _herdr_agents()
    me = os.getpid()
    found = []
    for pid, p in procs.items():
        agent = _agent_of(p["argv"])
        if not agent:
            continue
        where, info, seen, cur, dup = "other", {}, set(), p["ppid"], False
        while cur > 1 and cur not in seen:
            seen.add(cur)
            q = procs.get(cur)
            if q is None:
                break
            if cur == me or any("agentd.py" in a for a in q["argv"][:3]):
                where = "termx"   # chat milik TermX sendiri: sudah tampil di daftar percakapan
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
        if dup or where == "termx":
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
        found.append({"agent": agent, "pid": pid, "cwd": cwd, "started": p["start"], "where": where,
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
    out.sort(key=lambda f: f.get("started") or 0, reverse=True)
    print(jdump({"running": out, "herdr_bin": herdr_bin or "", "tmux": bool(shutil.which("tmux"))}))


COMMANDS = {
    "detect": cmd_detect, "list": cmd_list, "new": cmd_new, "attach": cmd_attach, "rm": cmd_rm,
    "stop": cmd_stop, "rename": cmd_rename, "running": cmd_running, "_worker": cmd_worker,
}


def main():
    os.makedirs(ROOT, mode=0o700, exist_ok=True)
    if len(sys.argv) < 2 or sys.argv[1] not in COMMANDS:
        if len(sys.argv) > 1 and sys.argv[1] == "version":
            print(VERSION)
            return
        raise SystemExit("usage: agentd.py detect|list|new|attach|rm|stop|rename|running|version")
    COMMANDS[sys.argv[1]](sys.argv[2:])


if __name__ == "__main__":
    main()
