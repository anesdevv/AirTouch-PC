package com.example.companion

object WindowsBatchLauncher {
    const val FILENAME = "AirTouchPC.bat"

    val SOURCE_CODE: String = """
@echo off
chcp 65001 >nul
title AirTouch PC
set "SELF=%~f0"
powershell -NoProfile -ExecutionPolicy Bypass -Command "@@m='###AIRTOUCH_'+'PS###';@@c=[IO.File]::ReadAllText(@@env:SELF,[Text.Encoding]::UTF8);Invoke-Expression @@c.Substring(@@c.IndexOf(@@m)+@@m.Length)"
pause
exit /b
###AIRTOUCH_PS###
[Console]::OutputEncoding = [Text.Encoding]::UTF8
@@ErrorActionPreference = 'Continue'

# Ensure Windows Firewall allows AirTouch PC ports (auto-prompts UAC once if needed)
try {
    netsh advfirewall firewall show rule name="AirTouch PC TCP" > @@null 2>&1
    @@tcpMissing = (@@LASTEXITCODE -ne 0)
    netsh advfirewall firewall show rule name="AirTouch PC UDP" > @@null 2>&1
    @@udpMissing = (@@LASTEXITCODE -ne 0)
    if (@@tcpMissing -or @@udpMissing) {
        Write-Host "[*] Allowing AirTouch PC in Windows Firewall (click Yes if prompted)..." -ForegroundColor Cyan
        @@fwCmd = '/c netsh advfirewall firewall add rule name="AirTouch PC TCP" dir=in action=allow protocol=TCP localport=54546 profile=any & netsh advfirewall firewall add rule name="AirTouch PC UDP" dir=in action=allow protocol=UDP localport=54545 profile=any'
        try {
            Start-Process cmd.exe -ArgumentList @@fwCmd -Verb RunAs -Wait -WindowStyle Hidden
        } catch {
            Write-Host "[!] Firewall prompt declined. If phone times out, re-run and click Yes." -ForegroundColor Yellow
        }
    }
} catch {}
@@ErrorActionPreference = 'Stop'

@@csharp = @'
using System;
using System.Collections.Generic;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;

public class AirTouchServer
{
    private const int UdpPort = 54545;
    private const int TcpPort = 54546;

    private const uint INPUT_MOUSE = 0;
    private const uint INPUT_KEYBOARD = 1;
    private const uint MOUSEEVENTF_MOVE = 0x0001;
    private const uint MOUSEEVENTF_LEFTDOWN = 0x0002;
    private const uint MOUSEEVENTF_LEFTUP = 0x0004;
    private const uint MOUSEEVENTF_RIGHTDOWN = 0x0008;
    private const uint MOUSEEVENTF_RIGHTUP = 0x0010;
    private const uint MOUSEEVENTF_MIDDLEDOWN = 0x0020;
    private const uint MOUSEEVENTF_MIDDLEUP = 0x0040;
    private const uint MOUSEEVENTF_WHEEL = 0x0800;
    private const uint MOUSEEVENTF_HWHEEL = 0x01000;
    private const uint KEYEVENTF_KEYUP = 0x0002;
    private const uint KEYEVENTF_UNICODE = 0x0004;

    [StructLayout(LayoutKind.Sequential)]
    struct MOUSEINPUT
    {
        public int dx;
        public int dy;
        public uint mouseData;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    struct KEYBDINPUT
    {
        public ushort wVk;
        public ushort wScan;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    struct HARDWAREINPUT
    {
        public uint uMsg;
        public ushort wParamL;
        public ushort wParamH;
    }

    [StructLayout(LayoutKind.Explicit)]
    struct InputUnion
    {
        [FieldOffset(0)] public MOUSEINPUT mi;
        [FieldOffset(0)] public KEYBDINPUT ki;
        [FieldOffset(0)] public HARDWAREINPUT hi;
    }

    [StructLayout(LayoutKind.Sequential)]
    struct INPUT
    {
        public uint type;
        public InputUnion U;
    }

    [DllImport("user32.dll", SetLastError = true)]
    static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

    [DllImport("user32.dll")]
    static extern uint MapVirtualKey(uint uCode, uint uMapType);

    [DllImport("user32.dll")]
    static extern bool SetProcessDPIAware();

    static readonly Dictionary<string, ushort> VkMap = new Dictionary<string, ushort>(StringComparer.OrdinalIgnoreCase);

    static AirTouchServer()
    {
        VkMap["BACKSPACE"] = 0x08;
        VkMap["TAB"] = 0x09;
        VkMap["ENTER"] = 0x0D;
        VkMap["SHIFT"] = 0x10;
        VkMap["CTRL"] = 0x11;
        VkMap["ALT"] = 0x12;
        VkMap["CAPS"] = 0x14;
        VkMap["ESC"] = 0x1B;
        VkMap["SPACE"] = 0x20;
        VkMap["PAGE_UP"] = 0x21;
        VkMap["PAGE_DOWN"] = 0x22;
        VkMap["END"] = 0x23;
        VkMap["HOME"] = 0x24;
        VkMap["LEFT"] = 0x25;
        VkMap["UP"] = 0x26;
        VkMap["RIGHT"] = 0x27;
        VkMap["DOWN"] = 0x28;
        VkMap["PRINTSCREEN"] = 0x2C;
        VkMap["DELETE"] = 0x2E;
        VkMap["WIN"] = 0x5B;
        VkMap["VOL_MUTE"] = 0xAD;
        VkMap["VOL_DOWN"] = 0xAE;
        VkMap["VOL_UP"] = 0xAF;
        VkMap["NEXT_TRACK"] = 0xB0;
        VkMap["PREV_TRACK"] = 0xB1;
        VkMap["PLAY_PAUSE"] = 0xB3;
        for (int i = 1; i <= 12; i++)
        {
            VkMap["F" + i] = (ushort)(0x6F + i);
        }
    }

    static INPUT MouseInput(int dx, int dy, uint data, uint flags)
    {
        INPUT inp = new INPUT();
        inp.type = INPUT_MOUSE;
        inp.U.mi.dx = dx;
        inp.U.mi.dy = dy;
        inp.U.mi.mouseData = data;
        inp.U.mi.dwFlags = flags;
        return inp;
    }

    static INPUT KeyInput(ushort vk, ushort scan, uint flags)
    {
        ushort resolvedScan = scan;
        if (resolvedScan == 0 && vk != 0)
        {
            resolvedScan = (ushort)MapVirtualKey(vk, 0);
        }
        INPUT inp = new INPUT();
        inp.type = INPUT_KEYBOARD;
        inp.U.ki.wVk = vk;
        inp.U.ki.wScan = resolvedScan;
        inp.U.ki.dwFlags = flags;
        return inp;
    }

    static void SetLaserPointer(bool active)
    {
        const ushort VK_LCONTROL = 0xA2;
        if (active)
        {
            List<INPUT> ctrlSeq = new List<INPUT>(1);
            ctrlSeq.Add(KeyInput(VK_LCONTROL, 0, 0));
            DispatchInputs(ctrlSeq);
            Thread.Sleep(18);

            List<INPUT> mouseSeq = new List<INPUT>(3);
            mouseSeq.Add(MouseInput(0, 0, 0, MOUSEEVENTF_LEFTDOWN));
            mouseSeq.Add(MouseInput(1, 0, 0, MOUSEEVENTF_MOVE));
            mouseSeq.Add(MouseInput(-1, 0, 0, MOUSEEVENTF_MOVE));
            DispatchInputs(mouseSeq);
        }
        else
        {
            List<INPUT> upSeq = new List<INPUT>(1);
            upSeq.Add(MouseInput(0, 0, 0, MOUSEEVENTF_LEFTUP));
            DispatchInputs(upSeq);
            Thread.Sleep(12);

            List<INPUT> ctrlUpSeq = new List<INPUT>(1);
            ctrlUpSeq.Add(KeyInput(VK_LCONTROL, 0, KEYEVENTF_KEYUP));
            DispatchInputs(ctrlUpSeq);
        }
    }

    static void DispatchInputs(List<INPUT> list)
    {
        if (list.Count == 0) return;
        INPUT[] arr = list.ToArray();
        SendInput((uint)arr.Length, arr, Marshal.SizeOf(typeof(INPUT)));
    }

    static void MoveMouse(int dx, int dy)
    {
        List<INPUT> list = new List<INPUT>(1);
        list.Add(MouseInput(dx, dy, 0, MOUSEEVENTF_MOVE));
        DispatchInputs(list);
    }

    static void ScrollMouse(int vx, int vy)
    {
        List<INPUT> list = new List<INPUT>(2);
        if (vy != 0) list.Add(MouseInput(0, 0, unchecked((uint)vy), MOUSEEVENTF_WHEEL));
        if (vx != 0) list.Add(MouseInput(0, 0, unchecked((uint)vx), MOUSEEVENTF_HWHEEL));
        DispatchInputs(list);
    }

    static void ClickMouse(string btn, string act)
    {
        uint downFlag = MOUSEEVENTF_LEFTDOWN;
        uint upFlag = MOUSEEVENTF_LEFTUP;
        if (btn == "right") { downFlag = MOUSEEVENTF_RIGHTDOWN; upFlag = MOUSEEVENTF_RIGHTUP; }
        else if (btn == "middle") { downFlag = MOUSEEVENTF_MIDDLEDOWN; upFlag = MOUSEEVENTF_MIDDLEUP; }

        List<INPUT> list = new List<INPUT>(4);
        if (act == "down")
        {
            list.Add(MouseInput(0, 0, 0, downFlag));
        }
        else if (act == "up")
        {
            list.Add(MouseInput(0, 0, 0, upFlag));
        }
        else if (act == "double")
        {
            list.Add(MouseInput(0, 0, 0, downFlag));
            list.Add(MouseInput(0, 0, 0, upFlag));
            list.Add(MouseInput(0, 0, 0, downFlag));
            list.Add(MouseInput(0, 0, 0, upFlag));
        }
        else
        {
            list.Add(MouseInput(0, 0, 0, downFlag));
            list.Add(MouseInput(0, 0, 0, upFlag));
        }
        DispatchInputs(list);
    }

    static ushort ResolveVk(string name)
    {
        if (string.IsNullOrEmpty(name)) return 0;
        ushort vk;
        if (VkMap.TryGetValue(name, out vk)) return vk;
        string up = name.ToUpperInvariant();
        if (up.Length == 1)
        {
            char c = up[0];
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) return (ushort)c;
        }
        return 0;
    }

    static void SendKey(string keyName, List<string> mods, string act)
    {
        List<ushort> modVks = new List<ushort>();
        if (mods != null)
        {
            foreach (string m in mods)
            {
                ushort mv;
                if (VkMap.TryGetValue(m, out mv)) modVks.Add(mv);
            }
        }
        ushort targetVk = ResolveVk(keyName);
        if (targetVk == 0 && modVks.Count == 0) return;

        List<INPUT> seq = new List<INPUT>();
        if (act == "down")
        {
            foreach (ushort mv in modVks) seq.Add(KeyInput(mv, 0, 0));
            if (targetVk != 0) seq.Add(KeyInput(targetVk, 0, 0));
        }
        else if (act == "up")
        {
            if (targetVk != 0) seq.Add(KeyInput(targetVk, 0, KEYEVENTF_KEYUP));
            for (int i = modVks.Count - 1; i >= 0; i--) seq.Add(KeyInput(modVks[i], 0, KEYEVENTF_KEYUP));
        }
        else
        {
            foreach (ushort mv in modVks) seq.Add(KeyInput(mv, 0, 0));
            if (targetVk != 0)
            {
                seq.Add(KeyInput(targetVk, 0, 0));
                seq.Add(KeyInput(targetVk, 0, KEYEVENTF_KEYUP));
            }
            for (int i = modVks.Count - 1; i >= 0; i--)
            {
                seq.Add(KeyInput(modVks[i], 0, KEYEVENTF_KEYUP));
            }
        }
        DispatchInputs(seq);
    }

    static void SendUnicodeText(string text)
    {
        if (string.IsNullOrEmpty(text)) return;
        List<INPUT> seq = new List<INPUT>(text.Length * 2);
        foreach (char ch in text)
        {
            if (ch == '\n')
            {
                seq.Add(KeyInput(0x0D, 0, 0));
                seq.Add(KeyInput(0x0D, 0, KEYEVENTF_KEYUP));
            }
            else
            {
                ushort code = (ushort)ch;
                seq.Add(KeyInput(0, code, KEYEVENTF_UNICODE));
                seq.Add(KeyInput(0, code, KEYEVENTF_UNICODE | KEYEVENTF_KEYUP));
            }
        }
        DispatchInputs(seq);
    }

    static void UdpLoop(string hostname)
    {
        UdpClient udp = null;
        try
        {
            udp = new UdpClient(UdpPort);
        }
        catch (Exception ex)
        {
            Console.WriteLine("[!] UDP port " + UdpPort + " unavailable: " + ex.Message);
            return;
        }
        IPEndPoint remote = new IPEndPoint(IPAddress.Any, 0);
        while (true)
        {
            try
            {
                byte[] data = udp.Receive(ref remote);
                string msg = Encoding.UTF8.GetString(data).Trim();
                if (msg == "AIRTOUCH_DISCOVER")
                {
                    Console.WriteLine("[i] Discovery probe from " + remote.Address);
                    string json = "{\"service\":\"airtouch-pc\",\"name\":\"" + EscapeJson(hostname) + "\",\"tcp\":" + TcpPort + ",\"udp\":" + UdpPort + "}";
                    byte[] reply = Encoding.UTF8.GetBytes(json);
                    udp.Send(reply, reply.Length, remote);
                }
                else if (msg.StartsWith("M,"))
                {
                    string[] p = msg.Split(',');
                    int dx, dy;
                    if (p.Length == 3 && int.TryParse(p[1], out dx) && int.TryParse(p[2], out dy))
                    {
                        MoveMouse(dx, dy);
                    }
                }
                else if (msg.StartsWith("W,"))
                {
                    string[] p = msg.Split(',');
                    int vx, vy;
                    if (p.Length == 3 && int.TryParse(p[1], out vx) && int.TryParse(p[2], out vy))
                    {
                        ScrollMouse(vx, vy);
                    }
                }
            }
            catch { }
        }
    }

    static void HandleTcpClient(TcpClient client, string hostname)
    {
        EndPoint ep = client.Client.RemoteEndPoint;
        Console.WriteLine("[+] Connected: " + ep);
        client.NoDelay = true;
        NetworkStream stream = client.GetStream();
        byte[] buf = new byte[4096];
        StringBuilder sb = new StringBuilder();
        try
        {
            while (true)
            {
                int read = stream.Read(buf, 0, buf.Length);
                if (read <= 0) break;
                sb.Append(Encoding.UTF8.GetString(buf, 0, read));
                while (true)
                {
                    string current = sb.ToString();
                    int nl = current.IndexOf('\n');
                    if (nl < 0) break;
                    string line = current.Substring(0, nl).Trim();
                    sb.Remove(0, nl + 1);
                    if (line.Length == 0) continue;

                    Dictionary<string, object> pkt = MiniJson.ParseObject(line);
                    if (pkt == null) continue;
                    string t = GetStr(pkt, "t", "");
                    if (t == "ping")
                    {
                        long ts = GetLong(pkt, "ts", 0);
                        byte[] resp = Encoding.UTF8.GetBytes("{\"t\":\"pong\",\"ts\":" + ts + "}\n");
                        stream.Write(resp, 0, resp.Length);
                        stream.Flush();
                    }
                    else if (t == "hello")
                    {
                        byte[] resp = Encoding.UTF8.GetBytes("{\"t\":\"welcome\",\"name\":\"" + EscapeJson(hostname) + "\",\"os\":\"Windows\"}\n");
                        stream.Write(resp, 0, resp.Length);
                        stream.Flush();
                    }
                    else if (t == "click")
                    {
                        ClickMouse(GetStr(pkt, "btn", "left"), GetStr(pkt, "act", "tap"));
                    }
                    else if (t == "text")
                    {
                        SendUnicodeText(GetStr(pkt, "s", ""));
                    }
                    else if (t == "key")
                    {
                        SendKey(GetStr(pkt, "k", ""), GetStrList(pkt, "mods"), GetStr(pkt, "act", "tap"));
                    }
                    else if (t == "laser")
                    {
                        object activeObj;
                        bool active = pkt.TryGetValue("active", out activeObj) && activeObj is bool && (bool)activeObj;
                        SetLaserPointer(active);
                    }
                }
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine("[!] Client error: " + ex.Message);
        }
        finally
        {
            client.Close();
            Console.WriteLine("[-] Disconnected: " + ep);
        }
    }

    static string GetStr(Dictionary<string, object> d, string key, string fallback)
    {
        object v;
        if (d.TryGetValue(key, out v) && v != null) return v.ToString();
        return fallback;
    }

    static long GetLong(Dictionary<string, object> d, string key, long fallback)
    {
        object v;
        if (d.TryGetValue(key, out v) && v != null)
        {
            long res;
            if (long.TryParse(v.ToString(), out res)) return res;
        }
        return fallback;
    }

    static List<string> GetStrList(Dictionary<string, object> d, string key)
    {
        List<string> res = new List<string>();
        object v;
        if (d.TryGetValue(key, out v) && v is List<object>)
        {
            foreach (object item in (List<object>)v)
            {
                if (item != null) res.Add(item.ToString());
            }
        }
        return res;
    }

    static string EscapeJson(string s)
    {
        if (s == null) return "";
        return s.Replace("\\", "\\\\").Replace("\"", "\\\"");
    }

    static List<string> GetLocalIps()
    {
        List<KeyValuePair<int, string>> ranked = new List<KeyValuePair<int, string>>();
        HashSet<string> seen = new HashSet<string>();

        try
        {
            foreach (NetworkInterface ni in NetworkInterface.GetAllNetworkInterfaces())
            {
                if (ni.OperationalStatus != OperationalStatus.Up) continue;
                if (ni.NetworkInterfaceType == NetworkInterfaceType.Loopback ||
                    ni.NetworkInterfaceType == NetworkInterfaceType.Tunnel) continue;

                string desc = (ni.Description + " " + ni.Name).ToLowerInvariant();
                bool isVirtual = desc.Contains("virtual") || desc.Contains("vmware") ||
                                 desc.Contains("vbox") || desc.Contains("hyper-v") ||
                                 desc.Contains("wsl") || desc.Contains("tap-") || desc.Contains("tun");

                IPInterfaceProperties props = ni.GetIPProperties();
                foreach (UnicastIPAddressInformation ua in props.UnicastAddresses)
                {
                    if (ua.Address.AddressFamily != AddressFamily.InterNetwork) continue;
                    string ip = ua.Address.ToString();
                    if (ip.StartsWith("127.") || ip.StartsWith("169.254.")) continue;
                    if (!seen.Add(ip)) continue;

                    int score = isVirtual ? 50 : 0;
                    if (ni.NetworkInterfaceType == NetworkInterfaceType.Wireless80211) score += 0;
                    else if (ni.NetworkInterfaceType == NetworkInterfaceType.Ethernet) score += 5;
                    else score += 20;

                    if (ip.StartsWith("192.168.")) score += 0;
                    else if (ip.StartsWith("10.")) score += 2;
                    else score += 4;

                    ranked.Add(new KeyValuePair<int, string>(score, ip));
                }
            }
        }
        catch { }

        ranked.Sort(delegate(KeyValuePair<int, string> a, KeyValuePair<int, string> b)
        {
            int cmp = a.Key.CompareTo(b.Key);
            return cmp != 0 ? cmp : string.Compare(a.Value, b.Value, StringComparison.Ordinal);
        });

        List<string> ips = new List<string>();
        foreach (KeyValuePair<int, string> kv in ranked) ips.Add(kv.Value);
        return ips;
    }

    static int GfMul(int a, int b)
    {
        int res = 0;
        while (b != 0)
        {
            if ((b & 1) != 0) res ^= a;
            a <<= 1;
            if ((a & 0x100) != 0) a ^= 0x11D;
            b >>= 1;
        }
        return res;
    }

    static int[] RsEncode(int[] data, int ecLen)
    {
        int[] gen = new int[] { 1 };
        int root = 1;
        for (int step = 0; step < ecLen; step++)
        {
            int[] next = new int[gen.Length + 1];
            for (int i = 0; i < gen.Length; i++)
            {
                next[i] ^= gen[i];
                next[i + 1] ^= GfMul(gen[i], root);
            }
            gen = next;
            root = GfMul(root, 2);
        }
        int[] msg = new int[data.Length + ecLen];
        Array.Copy(data, msg, data.Length);
        for (int i = 0; i < data.Length; i++)
        {
            int coef = msg[i];
            if (coef != 0)
            {
                for (int j = 1; j < gen.Length; j++) msg[i + j] ^= GfMul(gen[j], coef);
            }
        }
        int[] ec = new int[ecLen];
        Array.Copy(msg, data.Length, ec, 0, ecLen);
        return ec;
    }

    static int[,] MakeQrMatrix(string payload)
    {
        byte[] raw = Encoding.UTF8.GetBytes(payload);
        int[,] specs = new int[,] {
            { 1, 19, 7, -1 },
            { 2, 34, 10, 18 },
            { 3, 55, 15, 22 },
            { 4, 80, 20, 26 }
        };
        int chosen = -1;
        for (int i = 0; i < 4; i++)
        {
            if (raw.Length <= specs[i, 1] - 2) { chosen = i; break; }
        }
        if (chosen < 0) return null;

        int ver = specs[chosen, 0];
        int dataCw = specs[chosen, 1];
        int ecCw = specs[chosen, 2];
        int alignPos = specs[chosen, 3];
        int size = 17 + 4 * ver;

        List<int> bits = new List<int>();
        bits.Add(0); bits.Add(1); bits.Add(0); bits.Add(0);
        for (int i = 7; i >= 0; i--) bits.Add((raw.Length >> i) & 1);
        foreach (byte b in raw)
        {
            for (int i = 7; i >= 0; i--) bits.Add((b >> i) & 1);
        }
        int term = Math.Min(4, dataCw * 8 - bits.Count);
        for (int i = 0; i < term; i++) bits.Add(0);
        while (bits.Count % 8 != 0) bits.Add(0);

        List<int> cw = new List<int>();
        for (int i = 0; i < bits.Count; i += 8)
        {
            int val = 0;
            for (int j = 0; j < 8; j++) val = (val << 1) | bits[i + j];
            cw.Add(val);
        }
        int[] pads = new int[] { 0xEC, 0x11 };
        int pIdx = 0;
        while (cw.Count < dataCw) { cw.Add(pads[pIdx % 2]); pIdx++; }

        int[] ec = RsEncode(cw.ToArray(), ecCw);
        List<int> stream = new List<int>();
        foreach (int b in cw) for (int i = 7; i >= 0; i--) stream.Add((b >> i) & 1);
        foreach (int b in ec) for (int i = 7; i >= 0; i--) stream.Add((b >> i) & 1);

        int[,] grid = new int[size, size];
        bool[,] res = new bool[size, size];

        Action<int, int, int> setFn = delegate(int r, int c, int v)
        {
            if (r >= 0 && r < size && c >= 0 && c < size) { grid[r, c] = v; res[r, c] = true; }
        };
        Action<int, int> addFinder = delegate(int r0, int c0)
        {
            for (int r = -1; r <= 7; r++)
            {
                for (int c = -1; c <= 7; c++)
                {
                    bool inOut = r >= 0 && r <= 6 && c >= 0 && c <= 6;
                    bool onBorder = r == 0 || r == 6 || c == 0 || c == 6;
                    bool inCore = r >= 2 && r <= 4 && c >= 2 && c <= 4;
                    setFn(r0 + r, c0 + c, (inOut && (onBorder || inCore)) ? 1 : 0);
                }
            }
        };

        addFinder(0, 0);
        addFinder(0, size - 7);
        addFinder(size - 7, 0);

        if (alignPos >= 0)
        {
            for (int r = -2; r <= 2; r++)
            {
                for (int c = -2; c <= 2; c++)
                {
                    int v = (Math.Abs(r) == 2 || Math.Abs(c) == 2 || (r == 0 && c == 0)) ? 1 : 0;
                    setFn(alignPos + r, alignPos + c, v);
                }
            }
        }
        for (int i = 8; i < size - 8; i++)
        {
            int v = (i % 2 == 0) ? 1 : 0;
            setFn(6, i, v);
            setFn(i, 6, v);
        }
        setFn(size - 8, 8, 1);
        for (int i = 0; i < 9; i++) { if (i != 6) { res[8, i] = true; res[i, 8] = true; } }
        for (int i = 1; i < 8; i++) res[size - i, 8] = true;
        for (int i = 1; i < 9; i++) res[8, size - i] = true;

        int bitIdx = 0;
        bool up = true;
        int col = size - 1;
        while (col > 0)
        {
            if (col == 6) col--;
            for (int step = 0; step < size; step++)
            {
                int r = up ? (size - 1 - step) : step;
                int[] cols = new int[] { col, col - 1 };
                foreach (int c in cols)
                {
                    if (!res[r, c])
                    {
                        grid[r, c] = (bitIdx < stream.Count) ? stream[bitIdx] : 0;
                        bitIdx++;
                    }
                }
            }
            up = !up;
            col -= 2;
        }

        int[,] bestMat = null;
        int bestPen = int.MaxValue;
        for (int mid = 0; mid < 8; mid++)
        {
            int[,] cand = new int[size, size];
            for (int r = 0; r < size; r++)
            {
                for (int c = 0; c < size; c++)
                {
                    bool flip = false;
                    if (!res[r, c])
                    {
                        if (mid == 0) flip = (r + c) % 2 == 0;
                        else if (mid == 1) flip = r % 2 == 0;
                        else if (mid == 2) flip = c % 3 == 0;
                        else if (mid == 3) flip = (r + c) % 3 == 0;
                        else if (mid == 4) flip = (r / 2 + c / 3) % 2 == 0;
                        else if (mid == 5) flip = (r * c) % 2 + (r * c) % 3 == 0;
                        else if (mid == 6) flip = ((r * c) % 2 + (r * c) % 3) % 2 == 0;
                        else flip = ((r + c) % 2 + (r * c) % 3) % 2 == 0;
                    }
                    cand[r, c] = grid[r, c] ^ (flip ? 1 : 0);
                }
            }

            int d = (1 << 3) | mid;
            int fv = d << 10;
            while (BitLen(fv) >= 11) fv ^= 0x537 << (BitLen(fv) - 11);
            int fmt = ((d << 10) | fv) ^ 0x5412;
            int[] tlCols = new int[] { 0, 1, 2, 3, 4, 5, 7, 8 };
            for (int ic = 0; ic < tlCols.Length; ic++) cand[8, tlCols[ic]] = (fmt >> (14 - ic)) & 1;
            int[] tlRows = new int[] { 7, 5, 4, 3, 2, 1, 0 };
            for (int ir = 0; ir < tlRows.Length; ir++) cand[tlRows[ir], 8] = (fmt >> (6 - ir)) & 1;
            for (int i = 0; i < 7; i++) cand[size - 1 - i, 8] = (fmt >> (14 - i)) & 1;
            for (int i = 0; i < 8; i++) cand[8, size - 8 + i] = (fmt >> (7 - i)) & 1;

            int dark = 0;
            for (int r = 0; r < size; r++) for (int c = 0; c < size; c++) dark += cand[r, c];
            int pen = Math.Abs((dark * 100 / (size * size)) - 50) * 2;
            for (int r = 0; r < size; r++)
            {
                int run = 1;
                for (int c = 1; c < size; c++)
                {
                    if (cand[r, c] == cand[r, c - 1]) { run++; if (run == 5) pen += 3; else if (run > 5) pen += 1; }
                    else run = 1;
                }
            }
            for (int c = 0; c < size; c++)
            {
                int run = 1;
                for (int r = 1; r < size; r++)
                {
                    if (cand[r, c] == cand[r - 1, c]) { run++; if (run == 5) pen += 3; else if (run > 5) pen += 1; }
                    else run = 1;
                }
            }
            if (pen < bestPen) { bestPen = pen; bestMat = cand; }
        }
        return bestMat;
    }

    static int BitLen(int v)
    {
        int len = 0;
        while (v > 0) { len++; v >>= 1; }
        return len;
    }

    static void PrintPairingQr(List<string> ips, string hostname)
    {
        if (ips.Count == 0) return;
        string primary = ips[0];
        string shortHost = hostname.Length > 14 ? hostname.Substring(0, 14) : hostname;
        string payload = "airtouch://" + primary + ":" + TcpPort + "?u=" + UdpPort + "&n=" + Uri.EscapeDataString(shortHost);
        if (ips.Count > 1)
        {
            string alts = ips[1];
            if (ips.Count > 2) alts += "," + ips[2];
            string cand = payload + "&a=" + alts;
            if (Encoding.UTF8.GetByteCount(cand) <= 76) payload = cand;
        }
        int[,] mat = MakeQrMatrix(payload);
        if (mat == null) return;
        int dim = mat.GetLength(0);
        int quiet = 2;
        int n = dim + quiet * 2;
        char[] blocks = new char[] { ' ', '\u2584', '\u2580', '\u2588' };

        Console.WriteLine("  Scan QR Code in AirTouch PC app to pair:");
        for (int r = 0; r < n; r += 2)
        {
            StringBuilder line = new StringBuilder("  ");
            for (int c = 0; c < n; c++)
            {
                int rr = r - quiet;
                int cc = c - quiet;
                int top = (rr >= 0 && rr < dim && cc >= 0 && cc < dim) ? (mat[rr, cc] == 0 ? 1 : 0) : 1;
                int bot = 1;
                if (r + 1 < n)
                {
                    int rr2 = r + 1 - quiet;
                    bot = (rr2 >= 0 && rr2 < dim && cc >= 0 && cc < dim) ? (mat[rr2, cc] == 0 ? 1 : 0) : 1;
                }
                line.Append(blocks[(top << 1) | bot]);
            }
            Console.WriteLine(line.ToString());
        }
    }

    public static void Run()
    {
        try { SetProcessDPIAware(); } catch { }
        string hostname = Environment.MachineName;
        List<string> ips = GetLocalIps();

        Thread udpThread = new Thread(delegate() { UdpLoop(hostname); });
        udpThread.IsBackground = true;
        udpThread.Start();

        TcpListener srv = new TcpListener(IPAddress.Any, TcpPort);
        try
        {
            srv.Start();
        }
        catch (Exception ex)
        {
            Console.WriteLine("[!] Could not start TCP server on port " + TcpPort + ": " + ex.Message);
            Console.WriteLine("    Close any other running AirTouch PC window and try again.");
            return;
        }

        Console.WriteLine("================================================");
        Console.WriteLine("  AirTouch PC Companion (Zero-Install Windows)");
        Console.WriteLine("  PC Name : " + hostname);
        Console.WriteLine("  LAN IPs : " + (ips.Count > 0 ? string.Join(", ", ips.ToArray()) : "None found"));
        Console.WriteLine("  Ports   : UDP " + UdpPort + " | TCP " + TcpPort);
        Console.WriteLine("================================================");
        PrintPairingQr(ips, hostname);
        Console.WriteLine("  Waiting for AirTouch Android app...");

        while (true)
        {
            TcpClient client = srv.AcceptTcpClient();
            Thread t = new Thread(delegate() { HandleTcpClient(client, hostname); });
            t.IsBackground = true;
            t.Start();
        }
    }
}

static class MiniJson
{
    public static Dictionary<string, object> ParseObject(string json)
    {
        int i = 0;
        object val = ParseValue(json, ref i);
        return val as Dictionary<string, object>;
    }

    static void SkipWs(string s, ref int i)
    {
        while (i < s.Length && char.IsWhiteSpace(s[i])) i++;
    }

    static object ParseValue(string s, ref int i)
    {
        SkipWs(s, ref i);
        if (i >= s.Length) return null;
        char c = s[i];
        if (c == '{') return ParseObj(s, ref i);
        if (c == '[') return ParseArr(s, ref i);
        if (c == '"') return ParseStr(s, ref i);
        if (c == 't' && i + 4 <= s.Length && s.Substring(i, 4) == "true") { i += 4; return true; }
        if (c == 'f' && i + 5 <= s.Length && s.Substring(i, 5) == "false") { i += 5; return false; }
        if (c == 'n' && i + 4 <= s.Length && s.Substring(i, 4) == "null") { i += 4; return null; }
        int start = i;
        while (i < s.Length && (char.IsDigit(s[i]) || s[i] == '-' || s[i] == '.' || s[i] == 'e' || s[i] == 'E')) i++;
        if (i > start)
        {
            long lv;
            string num = s.Substring(start, i - start);
            if (long.TryParse(num, out lv)) return lv;
            return num;
        }
        return null;
    }

    static Dictionary<string, object> ParseObj(string s, ref int i)
    {
        Dictionary<string, object> dict = new Dictionary<string, object>();
        i++;
        while (i < s.Length)
        {
            SkipWs(s, ref i);
            if (i < s.Length && s[i] == '}') { i++; return dict; }
            string key = ParseStr(s, ref i);
            SkipWs(s, ref i);
            if (i < s.Length && s[i] == ':') i++;
            object val = ParseValue(s, ref i);
            if (key != null) dict[key] = val;
            SkipWs(s, ref i);
            if (i < s.Length && s[i] == ',') i++;
        }
        return dict;
    }

    static List<object> ParseArr(string s, ref int i)
    {
        List<object> list = new List<object>();
        i++;
        while (i < s.Length)
        {
            SkipWs(s, ref i);
            if (i < s.Length && s[i] == ']') { i++; return list; }
            list.Add(ParseValue(s, ref i));
            SkipWs(s, ref i);
            if (i < s.Length && s[i] == ',') i++;
        }
        return list;
    }

    static string ParseStr(string s, ref int i)
    {
        SkipWs(s, ref i);
        if (i >= s.Length || s[i] != '"') return null;
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < s.Length)
        {
            char c = s[i++];
            if (c == '"') return sb.ToString();
            if (c == '\\' && i < s.Length)
            {
                char esc = s[i++];
                if (esc == '"' || esc == '\\' || esc == '/') sb.Append(esc);
                else if (esc == 'b') sb.Append('\b');
                else if (esc == 'f') sb.Append('\f');
                else if (esc == 'n') sb.Append('\n');
                else if (esc == 'r') sb.Append('\r');
                else if (esc == 't') sb.Append('\t');
                else if (esc == 'u' && i + 4 <= s.Length)
                {
                    int code;
                    if (int.TryParse(s.Substring(i, 4), System.Globalization.NumberStyles.HexNumber, null, out code))
                    {
                        sb.Append((char)code);
                        i += 4;
                    }
                }
            }
            else
            {
                sb.Append(c);
            }
        }
        return sb.ToString();
    }
}
'@

Add-Type -TypeDefinition @@csharp -Language CSharp
[AirTouchServer]::Run()
""".trimIndent().replace("@@", "$")
}
