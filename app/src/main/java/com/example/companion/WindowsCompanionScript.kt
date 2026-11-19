package com.example.companion

object WindowsCompanionScript {
    const val FILENAME = "airtouch_win.py"

    val SOURCE_CODE: String = """
#!/usr/bin/env python3
'''
AirTouch PC - Zero-Dependency Windows Companion Server
Requires: Python 3.8+ on Windows (uses built-in user32.dll via ctypes).
Run: python airtouch_win.py
'''
import ctypes
from ctypes import wintypes
import json
import socket
import subprocess
import sys
import threading
import time

UDP_PORT = 54545
TCP_PORT = 54546

if sys.platform != "win32":
    print("Note: Designed for Windows (user32.dll). Running in preview/test mode.")

user32 = ctypes.windll.user32 if sys.platform == "win32" else None
if user32 is not None:
    try:
        user32.SetProcessDPIAware()
    except Exception:
        pass

INPUT_MOUSE = 0
INPUT_KEYBOARD = 1

MOUSEEVENTF_MOVE = 0x0001
MOUSEEVENTF_LEFTDOWN = 0x0002
MOUSEEVENTF_LEFTUP = 0x0004
MOUSEEVENTF_RIGHTDOWN = 0x0008
MOUSEEVENTF_RIGHTUP = 0x0010
MOUSEEVENTF_MIDDLEDOWN = 0x0020
MOUSEEVENTF_MIDDLEUP = 0x0040
MOUSEEVENTF_WHEEL = 0x0800
MOUSEEVENTF_HWHEEL = 0x01000

KEYEVENTF_KEYUP = 0x0002
KEYEVENTF_UNICODE = 0x0004

ULONG_PTR = ctypes.c_uint64 if ctypes.sizeof(ctypes.c_void_p) == 8 else ctypes.c_uint32


class MOUSEINPUT(ctypes.Structure):
    _fields_ = [
        ("dx", wintypes.LONG),
        ("dy", wintypes.LONG),
        ("mouseData", wintypes.DWORD),
        ("dwFlags", wintypes.DWORD),
        ("time", wintypes.DWORD),
        ("dwExtraInfo", ULONG_PTR),
    ]


class KEYBDINPUT(ctypes.Structure):
    _fields_ = [
        ("wVk", wintypes.WORD),
        ("wScan", wintypes.WORD),
        ("dwFlags", wintypes.DWORD),
        ("time", wintypes.DWORD),
        ("dwExtraInfo", ULONG_PTR),
    ]


class HARDWAREINPUT(ctypes.Structure):
    _fields_ = [
        ("uMsg", wintypes.DWORD),
        ("wParamL", wintypes.WORD),
        ("wParamH", wintypes.WORD),
    ]


class _INPUT_UNION(ctypes.Union):
    _fields_ = [("mi", MOUSEINPUT), ("ki", KEYBDINPUT), ("hi", HARDWAREINPUT)]


class INPUT(ctypes.Structure):
    _anonymous_ = ("u",)
    _fields_ = [("type", wintypes.DWORD), ("u", _INPUT_UNION)]


VK_MAP = {
    "BACKSPACE": 0x08,
    "TAB": 0x09,
    "ENTER": 0x0D,
    "SHIFT": 0x10,
    "CTRL": 0x11,
    "ALT": 0x12,
    "CAPS": 0x14,
    "ESC": 0x1B,
    "SPACE": 0x20,
    "PAGE_UP": 0x21,
    "PAGE_DOWN": 0x22,
    "END": 0x23,
    "HOME": 0x24,
    "LEFT": 0x25,
    "UP": 0x26,
    "RIGHT": 0x27,
    "DOWN": 0x28,
    "PRINTSCREEN": 0x2C,
    "DELETE": 0x2E,
    "WIN": 0x5B,
    "VOL_MUTE": 0xAD,
    "VOL_DOWN": 0xAE,
    "VOL_UP": 0xAF,
    "NEXT_TRACK": 0xB0,
    "PREV_TRACK": 0xB1,
    "PLAY_PAUSE": 0xB3,
}
for _i in range(1, 13):
    VK_MAP[f"F{_i}"] = 0x6F + _i


def _send_inputs(*inputs):
    if user32 is None or not inputs:
        return
    arr = (INPUT * len(inputs))(*inputs)
    user32.SendInput(len(inputs), arr, ctypes.sizeof(INPUT))


def move_mouse(dx: int, dy: int):
    _send_inputs(INPUT(type=INPUT_MOUSE, mi=MOUSEINPUT(dx, dy, 0, MOUSEEVENTF_MOVE, 0, 0)))


def scroll_mouse(vx: int, vy: int):
    events = []
    if vy != 0:
        events.append(INPUT(type=INPUT_MOUSE, mi=MOUSEINPUT(0, 0, vy & 0xFFFFFFFF, MOUSEEVENTF_WHEEL, 0, 0)))
    if vx != 0:
        events.append(INPUT(type=INPUT_MOUSE, mi=MOUSEINPUT(0, 0, vx & 0xFFFFFFFF, MOUSEEVENTF_HWHEEL, 0, 0)))
    _send_inputs(*events)


def click_mouse(btn: str, act: str):
    down_flag, up_flag = {
        "left": (MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP),
        "right": (MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP),
        "middle": (MOUSEEVENTF_MIDDLEDOWN, MOUSEEVENTF_MIDDLEUP),
    }.get(btn, (MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP))

    if act == "down":
        _send_inputs(INPUT(type=INPUT_MOUSE, mi=MOUSEINPUT(0, 0, 0, down_flag, 0, 0)))
    elif act == "up":
        _send_inputs(INPUT(type=INPUT_MOUSE, mi=MOUSEINPUT(0, 0, 0, up_flag, 0, 0)))
    elif act == "double":
        _send_inputs(
            INPUT(type=INPUT_MOUSE, mi=MOUSEINPUT(0, 0, 0, down_flag, 0, 0)),
            INPUT(type=INPUT_MOUSE, mi=MOUSEINPUT(0, 0, 0, up_flag, 0, 0)),
            INPUT(type=INPUT_MOUSE, mi=MOUSEINPUT(0, 0, 0, down_flag, 0, 0)),
            INPUT(type=INPUT_MOUSE, mi=MOUSEINPUT(0, 0, 0, up_flag, 0, 0)),
        )
    else:
        _send_inputs(
            INPUT(type=INPUT_MOUSE, mi=MOUSEINPUT(0, 0, 0, down_flag, 0, 0)),
            INPUT(type=INPUT_MOUSE, mi=MOUSEINPUT(0, 0, 0, up_flag, 0, 0)),
        )


def _vk_for(name: str):
    up = name.upper()
    if up in VK_MAP:
        return VK_MAP[up]
    if len(up) == 1 and ("A" <= up <= "Z" or "0" <= up <= "9"):
        return ord(up)
    return None


def _scan_for(vk: int) -> int:
    if user32 is None or vk == 0:
        return 0
    try:
        return int(user32.MapVirtualKeyW(vk, 0))
    except Exception:
        return 0


def set_laser_pointer(active: bool):
    vk_lcontrol = 0xA2
    scan = _scan_for(vk_lcontrol) or 0x1D
    if active:
        _send_inputs(INPUT(type=INPUT_KEYBOARD, ki=KEYBDINPUT(vk_lcontrol, scan, 0, 0, 0)))
        time.sleep(0.018)
        _send_inputs(
            INPUT(type=INPUT_MOUSE, mi=MOUSEINPUT(0, 0, 0, MOUSEEVENTF_LEFTDOWN, 0, 0)),
            INPUT(type=INPUT_MOUSE, mi=MOUSEINPUT(1, 0, 0, MOUSEEVENTF_MOVE, 0, 0)),
            INPUT(type=INPUT_MOUSE, mi=MOUSEINPUT(-1, 0, 0, MOUSEEVENTF_MOVE, 0, 0)),
        )
    else:
        _send_inputs(INPUT(type=INPUT_MOUSE, mi=MOUSEINPUT(0, 0, 0, MOUSEEVENTF_LEFTUP, 0, 0)))
        time.sleep(0.012)
        _send_inputs(INPUT(type=INPUT_KEYBOARD, ki=KEYBDINPUT(vk_lcontrol, scan, KEYEVENTF_KEYUP, 0, 0)))


def send_key(key_name: str, mods=None, act: str = "tap"):
    mods = mods or []
    mod_vks = [VK_MAP[m.upper()] for m in mods if m.upper() in VK_MAP]
    target_vk = _vk_for(key_name)
    if target_vk is None and not mod_vks:
        return
    if act == "down":
        seq = [INPUT(type=INPUT_KEYBOARD, ki=KEYBDINPUT(vk, _scan_for(vk), 0, 0, 0)) for vk in mod_vks]
        if target_vk is not None:
            seq.append(INPUT(type=INPUT_KEYBOARD, ki=KEYBDINPUT(target_vk, _scan_for(target_vk), 0, 0, 0)))
    elif act == "up":
        seq = []
        if target_vk is not None:
            seq.append(INPUT(type=INPUT_KEYBOARD, ki=KEYBDINPUT(target_vk, _scan_for(target_vk), KEYEVENTF_KEYUP, 0, 0)))
        for vk in reversed(mod_vks):
            seq.append(INPUT(type=INPUT_KEYBOARD, ki=KEYBDINPUT(vk, _scan_for(vk), KEYEVENTF_KEYUP, 0, 0)))
    else:
        seq = [INPUT(type=INPUT_KEYBOARD, ki=KEYBDINPUT(vk, _scan_for(vk), 0, 0, 0)) for vk in mod_vks]
        if target_vk is not None:
            sc = _scan_for(target_vk)
            seq.append(INPUT(type=INPUT_KEYBOARD, ki=KEYBDINPUT(target_vk, sc, 0, 0, 0)))
            seq.append(INPUT(type=INPUT_KEYBOARD, ki=KEYBDINPUT(target_vk, sc, KEYEVENTF_KEYUP, 0, 0)))
        for vk in reversed(mod_vks):
            seq.append(INPUT(type=INPUT_KEYBOARD, ki=KEYBDINPUT(vk, _scan_for(vk), KEYEVENTF_KEYUP, 0, 0)))
    _send_inputs(*seq)


def send_unicode_text(text: str):
    seq = []
    for ch in text:
        if ch == "\n":
            vk = VK_MAP["ENTER"]
            seq.append(INPUT(type=INPUT_KEYBOARD, ki=KEYBDINPUT(vk, 0, 0, 0, 0)))
            seq.append(INPUT(type=INPUT_KEYBOARD, ki=KEYBDINPUT(vk, 0, KEYEVENTF_KEYUP, 0, 0)))
        else:
            code = ord(ch)
            if code <= 0xFFFF:
                seq.append(INPUT(type=INPUT_KEYBOARD, ki=KEYBDINPUT(0, code, KEYEVENTF_UNICODE, 0, 0)))
                seq.append(INPUT(type=INPUT_KEYBOARD, ki=KEYBDINPUT(0, code, KEYEVENTF_UNICODE | KEYEVENTF_KEYUP, 0, 0)))
    if seq:
        _send_inputs(*seq)


def udp_loop():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind(("0.0.0.0", UDP_PORT))
    hostname = socket.gethostname()
    while True:
        data, addr = sock.recvfrom(1024)
        msg = data.decode("utf-8", errors="ignore").strip()
        if msg == "AIRTOUCH_DISCOVER":
            print(f"[i] Discovery probe from {addr[0]}:{addr[1]}")
            reply = json.dumps({
                "service": "airtouch-pc",
                "name": hostname,
                "tcp": TCP_PORT,
                "udp": UDP_PORT,
            }).encode("utf-8")
            sock.sendto(reply, addr)
        elif msg.startswith("M,"):
            parts = msg.split(",")
            if len(parts) == 3:
                move_mouse(int(parts[1]), int(parts[2]))
        elif msg.startswith("W,"):
            parts = msg.split(",")
            if len(parts) == 3:
                scroll_mouse(int(parts[1]), int(parts[2]))


def handle_tcp_client(conn, addr):
    print(f"[+] Connected: {addr[0]}:{addr[1]}")
    conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    buf = b""
    hostname = socket.gethostname()
    try:
        while True:
            chunk = conn.recv(4096)
            if not chunk:
                break
            buf += chunk
            while b"\n" in buf:
                line, buf = buf.split(b"\n", 1)
                if not line:
                    continue
                try:
                    pkt = json.loads(line.decode("utf-8"))
                except Exception:
                    continue
                t = pkt.get("t")
                if t == "ping":
                    resp = json.dumps({"t": "pong", "ts": pkt.get("ts", 0)}) + "\n"
                    conn.sendall(resp.encode("utf-8"))
                elif t == "hello":
                    resp = json.dumps({"t": "welcome", "name": hostname, "os": "Windows"}) + "\n"
                    conn.sendall(resp.encode("utf-8"))
                elif t == "click":
                    click_mouse(pkt.get("btn", "left"), pkt.get("act", "tap"))
                elif t == "text":
                    send_unicode_text(pkt.get("s", ""))
                elif t == "key":
                    send_key(pkt.get("k", ""), pkt.get("mods", []), pkt.get("act", "tap"))
                elif t == "laser":
                    set_laser_pointer(bool(pkt.get("active", False)))
    except Exception as exc:
        print(f"[!] Client error ({addr[0]}): {exc}")
    finally:
        conn.close()
        print(f"[-] Disconnected: {addr[0]}:{addr[1]}")


def _gf_mul(a: int, b: int) -> int:
    res = 0
    while b:
        if b & 1:
            res ^= a
        a <<= 1
        if a & 0x100:
            a ^= 0x11D
        b >>= 1
    return res


def _rs_encode(data, ec_len: int):
    gen = [1]
    root = 1
    for _ in range(ec_len):
        next_gen = [0] * (len(gen) + 1)
        for i, g in enumerate(gen):
            next_gen[i] ^= g
            next_gen[i + 1] ^= _gf_mul(g, root)
        gen = next_gen
        root = _gf_mul(root, 2)
    msg = list(data) + [0] * ec_len
    for i in range(len(data)):
        coef = msg[i]
        if coef != 0:
            for j in range(1, len(gen)):
                msg[i + j] ^= _gf_mul(gen[j], coef)
    return msg[len(data):]


def _make_qr_matrix(payload: str):
    raw = payload.encode("utf-8")
    specs = (
        (1, 19, 7, None),
        (2, 34, 10, 18),
        (3, 55, 15, 22),
        (4, 80, 20, 26),
    )
    chosen = next((s for s in specs if len(raw) <= s[1] - 2), None)
    if chosen is None:
        return None
    ver, data_cw, ec_cw, align_pos = chosen
    size = 17 + 4 * ver

    bits = [0, 1, 0, 0] + [(len(raw) >> (7 - i)) & 1 for i in range(8)]
    for b in raw:
        bits.extend((b >> (7 - i)) & 1 for i in range(8))
    bits.extend([0] * min(4, data_cw * 8 - len(bits)))
    while len(bits) % 8 != 0:
        bits.append(0)
    cw = [int("".join(str(x) for x in bits[i:i + 8]), 2) for i in range(0, len(bits), 8)]
    pad_bytes = (0xEC, 0x11)
    idx = 0
    while len(cw) < data_cw:
        cw.append(pad_bytes[idx % 2])
        idx += 1
    all_cw = cw + _rs_encode(cw, ec_cw)
    stream = []
    for b in all_cw:
        stream.extend((b >> (7 - i)) & 1 for i in range(8))

    grid = [[0] * size for _ in range(size)]
    reserved = [[False] * size for _ in range(size)]

    def set_fn(r, c, val):
        if 0 <= r < size and 0 <= c < size:
            grid[r][c] = val
            reserved[r][c] = True

    def add_finder(r0, c0):
        for r in range(-1, 8):
            for c in range(-1, 8):
                rr, cc = r0 + r, c0 + c
                if 0 <= rr < size and 0 <= cc < size:
                    in_outer = 0 <= r <= 6 and 0 <= c <= 6
                    on_border = r in (0, 6) or c in (0, 6)
                    in_core = 2 <= r <= 4 and 2 <= c <= 4
                    set_fn(rr, cc, 1 if (in_outer and (on_border or in_core)) else 0)

    add_finder(0, 0)
    add_finder(0, size - 7)
    add_finder(size - 7, 0)

    if align_pos is not None:
        for r in range(-2, 3):
            for c in range(-2, 3):
                val = 1 if (abs(r) == 2 or abs(c) == 2 or (r == 0 and c == 0)) else 0
                set_fn(align_pos + r, align_pos + c, val)

    for i in range(8, size - 8):
        val = 1 if i % 2 == 0 else 0
        set_fn(6, i, val)
        set_fn(i, 6, val)

    set_fn(size - 8, 8, 1)
    for i in range(9):
        if i != 6:
            reserved[8][i] = True
            reserved[i][8] = True
    for i in range(1, 8):
        reserved[size - i][8] = True
    for i in range(1, 9):
        reserved[8][size - i] = True

    bit_idx = 0
    up = True
    col = size - 1
    while col > 0:
        if col == 6:
            col -= 1
        rows = range(size - 1, -1, -1) if up else range(size)
        for r in rows:
            for c in (col, col - 1):
                if not reserved[r][c]:
                    grid[r][c] = stream[bit_idx] if bit_idx < len(stream) else 0
                    bit_idx += 1
        up = not up
        col -= 2

    mask_fns = (
        lambda r, c: (r + c) % 2 == 0,
        lambda r, c: r % 2 == 0,
        lambda r, c: c % 3 == 0,
        lambda r, c: (r + c) % 3 == 0,
        lambda r, c: (r // 2 + c // 3) % 2 == 0,
        lambda r, c: (r * c) % 2 + (r * c) % 3 == 0,
        lambda r, c: ((r * c) % 2 + (r * c) % 3) % 2 == 0,
        lambda r, c: ((r + c) % 2 + (r * c) % 3) % 2 == 0,
    )

    def apply_format(m, mask_id):
        d = (1 << 3) | mask_id
        v = d << 10
        while v.bit_length() >= 11:
            v ^= 0x537 << (v.bit_length() - 11)
        fmt = ((d << 10) | v) ^ 0x5412
        fb = [(fmt >> i) & 1 for i in range(15)]
        tl_cols = (0, 1, 2, 3, 4, 5, 7, 8)
        for idx_c, cc in enumerate(tl_cols):
            m[8][cc] = fb[14 - idx_c]
        tl_rows = (7, 5, 4, 3, 2, 1, 0)
        for idx_r, rr in enumerate(tl_rows):
            m[rr][8] = fb[6 - idx_r]
        for i in range(7):
            m[size - 1 - i][8] = fb[14 - i]
        for i in range(8):
            m[8][size - 8 + i] = fb[7 - i]

    best_mat = None
    best_pen = 10**9
    for mid, fn in enumerate(mask_fns):
        cand = [
            [grid[r][c] ^ (1 if (not reserved[r][c] and fn(r, c)) else 0) for c in range(size)]
            for r in range(size)
        ]
        apply_format(cand, mid)
        dark = sum(sum(row) for row in cand)
        pen = abs((dark * 100 // (size * size)) - 50) * 2
        for r in range(size):
            run = 1
            for c in range(1, size):
                if cand[r][c] == cand[r][c - 1]:
                    run += 1
                    if run == 5:
                        pen += 3
                    elif run > 5:
                        pen += 1
                else:
                    run = 1
        for c in range(size):
            run = 1
            for r in range(1, size):
                if cand[r][c] == cand[r - 1][c]:
                    run += 1
                    if run == 5:
                        pen += 3
                    elif run > 5:
                        pen += 1
                else:
                    run = 1
        if pen < best_pen:
            best_pen = pen
            best_mat = cand
    return best_mat


def print_pairing_qr(ips, hostname: str):
    primary = ips[0]
    alts = [ip for ip in ips[1:3] if ip != primary]
    payload = f"airtouch://{primary}:{TCP_PORT}?u={UDP_PORT}&n={hostname[:14]}"
    if alts:
        cand = payload + "&a=" + ",".join(alts)
        if len(cand.encode("utf-8")) <= 76:
            payload = cand
    mat = _make_qr_matrix(payload)
    if mat is None:
        return
    quiet = 2
    n = len(mat) + quiet * 2

    def is_white(r, c):
        rr, cc = r - quiet, c - quiet
        if 0 <= rr < len(mat) and 0 <= cc < len(mat):
            return 1 if mat[rr][cc] == 0 else 0
        return 1

    blocks = (" ", "▄", "▀", "█")
    print("  Scan QR Code in AirTouch PC app to pair:")
    for r in range(0, n, 2):
        line = []
        for c in range(n):
            top = is_white(r, c)
            bot = is_white(r + 1, c) if r + 1 < n else 1
            line.append(blocks[(top << 1) | bot])
        print("  " + "".join(line))


def _ip_rank(ip: str, default_ip: str) -> tuple:
    # Prefer standard LAN 192.168.* (avoiding common VirtualBox 192.168.56.*), then 10.*, then 172.16-31.*
    is_default = 0 if ip == default_ip else 1
    if ip.startswith("192.168.") and not ip.startswith("192.168.56."):
        return (0, is_default, ip)
    if ip.startswith("10."):
        return (1, is_default, ip)
    if ip.startswith("172."):
        try:
            second = int(ip.split(".")[1])
            if 16 <= second <= 31:
                return (2, is_default, ip)
        except Exception:
            pass
    return (3, is_default, ip)


def get_local_ips():
    found = set()
    default_ip = ""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        default_ip = s.getsockname()[0]
        found.add(default_ip)
        s.close()
    except Exception:
        pass
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            ip = info[4][0]
            if ip and not ip.startswith("127.") and not ip.startswith("169.254."):
                found.add(ip)
    except Exception:
        pass
    if not found:
        try:
            ip = socket.gethostbyname(socket.gethostname())
            if ip and not ip.startswith("127."):
                found.add(ip)
        except Exception:
            pass
    return sorted(found, key=lambda x: _ip_rank(x, default_ip))


def ensure_windows_firewall():
    if sys.platform != "win32":
        return
    try:
        chk = subprocess.run(
            ["netsh", "advfirewall", "firewall", "show", "rule", "name=AirTouch PC TCP"],
            capture_output=True,
            text=True,
            timeout=3,
        )
        if chk.returncode == 0:
            return
        r1 = subprocess.run(
            [
                "netsh", "advfirewall", "firewall", "add", "rule",
                "name=AirTouch PC TCP", "dir=in", "action=allow",
                "protocol=TCP", f"localport={TCP_PORT}", "profile=any",
            ],
            capture_output=True,
            text=True,
            timeout=3,
        )
        subprocess.run(
            [
                "netsh", "advfirewall", "firewall", "add", "rule",
                "name=AirTouch PC UDP", "dir=in", "action=allow",
                "protocol=UDP", f"localport={UDP_PORT}", "profile=any",
            ],
            capture_output=True,
            text=True,
            timeout=3,
        )
        if r1.returncode == 0:
            print("[+] Windows Firewall rules added automatically.")
            return

        # Trigger Windows UAC prompt once to add both firewall rules cleanly
        print("[*] Opening Windows Firewall permission prompt (click Yes to allow Wi-Fi connection)...")
        cmd_args = (
            f'/c netsh advfirewall firewall add rule name="AirTouch PC TCP" dir=in action=allow protocol=TCP localport={TCP_PORT} profile=any'
            f' & netsh advfirewall firewall add rule name="AirTouch PC UDP" dir=in action=allow protocol=UDP localport={UDP_PORT} profile=any'
        )
        ret = ctypes.windll.shell32.ShellExecuteW(None, "runas", "cmd.exe", cmd_args, None, 0)
        if ret > 32:
            import time
            time.sleep(1.5)
            rechk = subprocess.run(
                ["netsh", "advfirewall", "firewall", "show", "rule", "name=AirTouch PC TCP"],
                capture_output=True,
                text=True,
                timeout=3,
            )
            if rechk.returncode == 0:
                print("[+] Windows Firewall rules added!")
                return

        print("[!] Windows Firewall rule not active for ports 54545/54546.")
        print("    If phone times out, run these two commands in Administrator PowerShell or CMD:")
        print(f'    netsh advfirewall firewall add rule name="AirTouch PC TCP" dir=in action=allow protocol=TCP localport={TCP_PORT} profile=any')
        print(f'    netsh advfirewall firewall add rule name="AirTouch PC UDP" dir=in action=allow protocol=UDP localport={UDP_PORT} profile=any')
    except Exception:
        pass


def main():
    threading.Thread(target=udp_loop, daemon=True).start()
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("0.0.0.0", TCP_PORT))
    srv.listen(4)
    hostname = socket.gethostname()
    ips = get_local_ips()
    print("=" * 48)
    print("  AirTouch PC Companion (Windows)")
    print(f"  PC Name : {hostname}")
    print(f"  LAN IPs : {', '.join(ips) if ips else 'None found'}")
    print(f"  Ports   : UDP {UDP_PORT} | TCP {TCP_PORT}")
    print("=" * 48)
    ensure_windows_firewall()
    if ips:
        try:
            print_pairing_qr(ips, hostname)
        except Exception:
            pass
    print("  Waiting for AirTouch Android app...")
    while True:
        conn, addr = srv.accept()
        threading.Thread(target=handle_tcp_client, args=(conn, addr), daemon=True).start()


if __name__ == "__main__":
    main()
""".trimIndent()
}
