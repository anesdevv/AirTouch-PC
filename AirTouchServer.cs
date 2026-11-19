using System;
using System.Collections.Generic;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;

namespace AirTouchPC
{
    public enum TelemetryInputType
    {
        None,
        MouseMove,
        MouseClick,
        MouseScroll,
        Keyboard,
        Text,
        Laser
    }

    public class NetworkAdapterInfo
    {
        public string Ip { get; set; }
        public string InterfaceName { get; set; }
        public string TypeName { get; set; }
        public bool IsRecommended { get; set; }
        public int Score { get; set; }

        public override string ToString()
        {
            return string.Format("{0} ({1})", Ip, TypeName);
        }
    }

    public class AirTouchServer
    {
        public const int UdpPort = 54545;
        public const int TcpPort = 54546;

        private static readonly Dictionary<string, ushort> VkMap = new Dictionary<string, ushort>(StringComparer.OrdinalIgnoreCase);

        private volatile bool _isRunning = false;
        private UdpClient _udpListener;
        private TcpListener _tcpListener;
        private Thread _udpThread;
        private Thread _tcpThread;
        private readonly List<TcpClient> _activeClients = new List<TcpClient>();
        private readonly object _clientsLock = new object();

        private string _currentClientEndpoint = null;
        private DateTime? _connectedSince = null;
        private long _packetCounter = 0;

        // Events for UI integration
        public event Action<string, string> OnLog;
        public event Action<string> OnClientConnected;
        public event Action<string> OnClientDisconnected;
        public event Action<string> OnInputActivity;
        public event Action<TelemetryInputType, string> OnTelemetry;

        public bool HasConnectedClient
        {
            get
            {
                lock (_clientsLock)
                {
                    return _activeClients.Count > 0;
                }
            }
        }

        public string CurrentClientEndpoint
        {
            get { return _currentClientEndpoint; }
        }

        public DateTime? ConnectedSince
        {
            get { return _connectedSince; }
        }

        public long TotalPackets
        {
            get { return _packetCounter; }
        }

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

        public bool IsRunning
        {
            get { return _isRunning; }
        }

        public void Start()
        {
            if (_isRunning) return;
            _isRunning = true;

            string hostname = Environment.MachineName;

            _udpThread = new Thread(delegate() { UdpWorker(hostname); })
            {
                IsBackground = true,
                Name = "AirTouchUdpServer"
            };
            _udpThread.Start();

            _tcpThread = new Thread(delegate() { TcpWorker(hostname); })
            {
                IsBackground = true,
                Name = "AirTouchTcpServer"
            };
            _tcpThread.Start();

            Log("AirTouch PC Server listening on TCP " + TcpPort + ", UDP " + UdpPort, "INFO");
        }

        public void Stop()
        {
            _isRunning = false;

            try
            {
                if (_udpListener != null)
                {
                    _udpListener.Close();
                    _udpListener = null;
                }
            }
            catch { }

            try
            {
                if (_tcpListener != null)
                {
                    _tcpListener.Stop();
                    _tcpListener = null;
                }
            }
            catch { }

            DisconnectAllClients();

            Log("AirTouch PC Server stopped", "INFO");
        }

        public void DisconnectAllClients()
        {
            lock (_clientsLock)
            {
                foreach (TcpClient c in _activeClients)
                {
                    try { c.Close(); } catch { }
                }
                _activeClients.Clear();
                _currentClientEndpoint = null;
                _connectedSince = null;
            }
        }

        private void Log(string msg, string level = "INFO")
        {
            Action<string, string> handler = OnLog;
            if (handler != null)
            {
                try { handler(msg, level); } catch { }
            }
        }

        private void NotifyActivity(string act)
        {
            Action<string> handler = OnInputActivity;
            if (handler != null)
            {
                try { handler(act); } catch { }
            }
        }

        private void NotifyTelemetry(TelemetryInputType type, string details)
        {
            Interlocked.Increment(ref _packetCounter);
            Action<TelemetryInputType, string> handler = OnTelemetry;
            if (handler != null)
            {
                try { handler(type, details); } catch { }
            }
        }

        private void UdpWorker(string hostname)
        {
            try
            {
                _udpListener = new UdpClient(UdpPort);
            }
            catch (Exception ex)
            {
                Log("UDP port " + UdpPort + " error: " + ex.Message, "ERROR");
                return;
            }

            IPEndPoint remote = new IPEndPoint(IPAddress.Any, 0);
            while (_isRunning)
            {
                try
                {
                    byte[] data = _udpListener.Receive(ref remote);
                    if (data == null || data.Length == 0) continue;

                    string msg = Encoding.UTF8.GetString(data).Trim();
                    if (msg == "AIRTOUCH_DISCOVER")
                    {
                        string json = "{\"service\":\"airtouch-pc\",\"name\":\"" + MiniJson.EscapeJson(hostname) + "\",\"tcp\":" + TcpPort + ",\"udp\":" + UdpPort + "}";
                        byte[] reply = Encoding.UTF8.GetBytes(json);
                        _udpListener.Send(reply, reply.Length, remote);
                        Log("Discovery probe from " + remote.Address, "INFO");
                    }
                    else if (msg.StartsWith("M,"))
                    {
                        string[] p = msg.Split(',');
                        int dx, dy;
                        if (p.Length == 3 && int.TryParse(p[1], out dx) && int.TryParse(p[2], out dy))
                        {
                            MoveMouse(dx, dy);
                            NotifyTelemetry(TelemetryInputType.MouseMove, dx + ", " + dy);
                        }
                    }
                    else if (msg.StartsWith("W,"))
                    {
                        string[] p = msg.Split(',');
                        int vx, vy;
                        if (p.Length == 3 && int.TryParse(p[1], out vx) && int.TryParse(p[2], out vy))
                        {
                            ScrollMouse(vx, vy);
                            NotifyTelemetry(TelemetryInputType.MouseScroll, vx + ", " + vy);
                        }
                    }
                }
                catch (SocketException)
                {
                    if (!_isRunning) break;
                }
                catch (Exception ex)
                {
                    if (!_isRunning) break;
                    Log("UDP packet exception: " + ex.Message, "WARN");
                }
            }
        }

        private void TcpWorker(string hostname)
        {
            try
            {
                _tcpListener = new TcpListener(IPAddress.Any, TcpPort);
                _tcpListener.Start();
            }
            catch (Exception ex)
            {
                Log("TCP server error on port " + TcpPort + ": " + ex.Message, "ERROR");
                return;
            }

            while (_isRunning)
            {
                try
                {
                    TcpClient client = _tcpListener.AcceptTcpClient();
                    lock (_clientsLock)
                    {
                        _activeClients.Add(client);
                        try
                        {
                            _currentClientEndpoint = client.Client.RemoteEndPoint.ToString();
                        }
                        catch
                        {
                            _currentClientEndpoint = "Phone";
                        }
                        _connectedSince = DateTime.Now;
                    }

                    Thread t = new Thread(delegate() { HandleClient(client, hostname); })
                    {
                        IsBackground = true
                    };
                    t.Start();
                }
                catch (SocketException)
                {
                    if (!_isRunning) break;
                }
                catch (Exception ex)
                {
                    if (!_isRunning) break;
                    Log("TCP accept error: " + ex.Message, "ERROR");
                }
            }
        }

        private void HandleClient(TcpClient client, string hostname)
        {
            string remoteEp = "Unknown";
            try
            {
                remoteEp = client.Client.RemoteEndPoint.ToString();
            }
            catch { }

            Log("Connected to phone: " + remoteEp, "SUCCESS");
            Action<string> connectHandler = OnClientConnected;
            if (connectHandler != null)
            {
                try { connectHandler(remoteEp); } catch { }
            }

            client.NoDelay = true;
            NetworkStream stream = null;
            byte[] buf = new byte[4096];
            StringBuilder sb = new StringBuilder();

            try
            {
                stream = client.GetStream();
                while (_isRunning)
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
                            byte[] resp = Encoding.UTF8.GetBytes("{\"t\":\"welcome\",\"name\":\"" + MiniJson.EscapeJson(hostname) + "\",\"os\":\"Windows\"}\n");
                            stream.Write(resp, 0, resp.Length);
                            stream.Flush();
                            Log("Handshake completed with " + remoteEp, "SUCCESS");
                        }
                        else if (t == "click")
                        {
                            string btn = GetStr(pkt, "btn", "left");
                            string act = GetStr(pkt, "act", "tap");
                            ClickMouse(btn, act);
                            NotifyTelemetry(TelemetryInputType.MouseClick, btn + " " + act);
                        }
                        else if (t == "text")
                        {
                            string s = GetStr(pkt, "s", "");
                            SendUnicodeText(s);
                            NotifyTelemetry(TelemetryInputType.Text, s.Length + " chars");
                        }
                        else if (t == "key")
                        {
                            string k = GetStr(pkt, "k", "");
                            List<string> mods = GetStrList(pkt, "mods");
                            string act = GetStr(pkt, "act", "tap");
                            SendKey(k, mods, act);
                            NotifyTelemetry(TelemetryInputType.Keyboard, k);
                        }
                        else if (t == "laser")
                        {
                            object activeObj;
                            bool active = pkt.TryGetValue("active", out activeObj) && activeObj is bool && (bool)activeObj;
                            SetLaserPointer(active);
                            NotifyTelemetry(TelemetryInputType.Laser, active ? "on" : "off");
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                if (_isRunning)
                {
                    Log("Connection error: " + ex.Message, "WARN");
                }
            }
            finally
            {
                lock (_clientsLock)
                {
                    _activeClients.Remove(client);
                    if (_activeClients.Count == 0)
                    {
                        _currentClientEndpoint = null;
                        _connectedSince = null;
                    }
                }
                try { client.Close(); } catch { }

                Log("Disconnected: " + remoteEp, "INFO");
                Action<string> disconnectHandler = OnClientDisconnected;
                if (disconnectHandler != null)
                {
                    try { disconnectHandler(remoteEp); } catch { }
                }
            }
        }

        private static string GetStr(Dictionary<string, object> d, string key, string fallback)
        {
            object v;
            if (d.TryGetValue(key, out v) && v != null) return v.ToString();
            return fallback;
        }

        private static long GetLong(Dictionary<string, object> d, string key, long fallback)
        {
            object v;
            if (d.TryGetValue(key, out v) && v != null)
            {
                long res;
                if (long.TryParse(v.ToString(), out res)) return res;
            }
            return fallback;
        }

        private static List<string> GetStrList(Dictionary<string, object> d, string key)
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

        public static List<NetworkAdapterInfo> GetRankedNetworkAdapters()
        {
            List<NetworkAdapterInfo> list = new List<NetworkAdapterInfo>();
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

                    string typeName = "Other";
                    if (ni.NetworkInterfaceType == NetworkInterfaceType.Wireless80211) typeName = "Wi-Fi";
                    else if (ni.NetworkInterfaceType == NetworkInterfaceType.Ethernet) typeName = "Ethernet";

                    IPInterfaceProperties props = ni.GetIPProperties();
                    foreach (UnicastIPAddressInformation ua in props.UnicastAddresses)
                    {
                        if (ua.Address.AddressFamily != AddressFamily.InterNetwork) continue;
                        string ip = ua.Address.ToString();
                        if (ip.StartsWith("127.") || ip.StartsWith("169.254.")) continue;
                        if (!seen.Add(ip)) continue;

                        int score = isVirtual ? 60 : 0;
                        if (ni.NetworkInterfaceType == NetworkInterfaceType.Wireless80211) score += 0;
                        else if (ni.NetworkInterfaceType == NetworkInterfaceType.Ethernet) score += 5;
                        else score += 20;

                        if (ip.StartsWith("192.168.")) score += 0;
                        else if (ip.StartsWith("10.")) score += 2;
                        else score += 4;

                        NetworkAdapterInfo info = new NetworkAdapterInfo
                        {
                            Ip = ip,
                            InterfaceName = ni.Name,
                            TypeName = typeName,
                            Score = score,
                            IsRecommended = false
                        };
                        list.Add(info);
                    }
                }
            }
            catch { }

            list.Sort(delegate(NetworkAdapterInfo a, NetworkAdapterInfo b)
            {
                int cmp = a.Score.CompareTo(b.Score);
                return cmp != 0 ? cmp : string.Compare(a.Ip, b.Ip, StringComparison.Ordinal);
            });

            if (list.Count > 0)
            {
                list[0].IsRecommended = true;
            }

            return list;
        }

        // --- Input Simulation via SendInput ---

        private static NativeMethods.INPUT MouseInput(int dx, int dy, uint data, uint flags)
        {
            NativeMethods.INPUT inp = new NativeMethods.INPUT();
            inp.type = NativeMethods.INPUT_MOUSE;
            inp.U.mi.dx = dx;
            inp.U.mi.dy = dy;
            inp.U.mi.mouseData = data;
            inp.U.mi.dwFlags = flags;
            return inp;
        }

        private static NativeMethods.INPUT KeyInput(ushort vk, ushort scan, uint flags)
        {
            ushort resolvedScan = scan;
            if (resolvedScan == 0 && vk != 0)
            {
                resolvedScan = (ushort)NativeMethods.MapVirtualKey(vk, 0);
            }
            NativeMethods.INPUT inp = new NativeMethods.INPUT();
            inp.type = NativeMethods.INPUT_KEYBOARD;
            inp.U.ki.wVk = vk;
            inp.U.ki.wScan = resolvedScan;
            inp.U.ki.dwFlags = flags;
            return inp;
        }

        private static void DispatchInputs(List<NativeMethods.INPUT> list)
        {
            if (list.Count == 0) return;
            NativeMethods.INPUT[] arr = list.ToArray();
            NativeMethods.SendInput((uint)arr.Length, arr, Marshal.SizeOf(typeof(NativeMethods.INPUT)));
        }

        public static void MoveMouse(int dx, int dy)
        {
            List<NativeMethods.INPUT> list = new List<NativeMethods.INPUT>(1);
            list.Add(MouseInput(dx, dy, 0, NativeMethods.MOUSEEVENTF_MOVE));
            DispatchInputs(list);
        }

        public static void ScrollMouse(int vx, int vy)
        {
            List<NativeMethods.INPUT> list = new List<NativeMethods.INPUT>(2);
            if (vy != 0) list.Add(MouseInput(0, 0, unchecked((uint)vy), NativeMethods.MOUSEEVENTF_WHEEL));
            if (vx != 0) list.Add(MouseInput(0, 0, unchecked((uint)vx), NativeMethods.MOUSEEVENTF_HWHEEL));
            DispatchInputs(list);
        }

        public static void ClickMouse(string btn, string act)
        {
            uint downFlag = NativeMethods.MOUSEEVENTF_LEFTDOWN;
            uint upFlag = NativeMethods.MOUSEEVENTF_LEFTUP;
            if (btn == "right") { downFlag = NativeMethods.MOUSEEVENTF_RIGHTDOWN; upFlag = NativeMethods.MOUSEEVENTF_RIGHTUP; }
            else if (btn == "middle") { downFlag = NativeMethods.MOUSEEVENTF_MIDDLEDOWN; upFlag = NativeMethods.MOUSEEVENTF_MIDDLEUP; }

            List<NativeMethods.INPUT> list = new List<NativeMethods.INPUT>(4);
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

        private static ushort ResolveVk(string name)
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

        public static void SendKey(string keyName, List<string> mods, string act)
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

            List<NativeMethods.INPUT> seq = new List<NativeMethods.INPUT>();
            if (act == "down")
            {
                foreach (ushort mv in modVks) seq.Add(KeyInput(mv, 0, 0));
                if (targetVk != 0) seq.Add(KeyInput(targetVk, 0, 0));
            }
            else if (act == "up")
            {
                if (targetVk != 0) seq.Add(KeyInput(targetVk, 0, NativeMethods.KEYEVENTF_KEYUP));
                for (int i = modVks.Count - 1; i >= 0; i--) seq.Add(KeyInput(modVks[i], 0, NativeMethods.KEYEVENTF_KEYUP));
            }
            else
            {
                foreach (ushort mv in modVks) seq.Add(KeyInput(mv, 0, 0));
                if (targetVk != 0)
                {
                    seq.Add(KeyInput(targetVk, 0, 0));
                    seq.Add(KeyInput(targetVk, 0, NativeMethods.KEYEVENTF_KEYUP));
                }
                for (int i = modVks.Count - 1; i >= 0; i--)
                {
                    seq.Add(KeyInput(modVks[i], 0, NativeMethods.KEYEVENTF_KEYUP));
                }
            }
            DispatchInputs(seq);
        }

        public static void SendUnicodeText(string text)
        {
            if (string.IsNullOrEmpty(text)) return;
            List<NativeMethods.INPUT> seq = new List<NativeMethods.INPUT>(text.Length * 2);
            foreach (char ch in text)
            {
                if (ch == '\n')
                {
                    seq.Add(KeyInput(0x0D, 0, 0));
                    seq.Add(KeyInput(0x0D, 0, NativeMethods.KEYEVENTF_KEYUP));
                }
                else
                {
                    ushort code = (ushort)ch;
                    seq.Add(KeyInput(0, code, NativeMethods.KEYEVENTF_UNICODE));
                    seq.Add(KeyInput(0, code, NativeMethods.KEYEVENTF_UNICODE | NativeMethods.KEYEVENTF_KEYUP));
                }
            }
            DispatchInputs(seq);
        }

        public static void SetLaserPointer(bool active)
        {
            const ushort VK_LCONTROL = 0xA2;
            if (active)
            {
                List<NativeMethods.INPUT> ctrlSeq = new List<NativeMethods.INPUT>(1);
                ctrlSeq.Add(KeyInput(VK_LCONTROL, 0, 0));
                DispatchInputs(ctrlSeq);
                Thread.Sleep(18);

                List<NativeMethods.INPUT> mouseSeq = new List<NativeMethods.INPUT>(3);
                mouseSeq.Add(MouseInput(0, 0, 0, NativeMethods.MOUSEEVENTF_LEFTDOWN));
                mouseSeq.Add(MouseInput(1, 0, 0, NativeMethods.MOUSEEVENTF_MOVE));
                mouseSeq.Add(MouseInput(-1, 0, 0, NativeMethods.MOUSEEVENTF_MOVE));
                DispatchInputs(mouseSeq);
            }
            else
            {
                List<NativeMethods.INPUT> upSeq = new List<NativeMethods.INPUT>(1);
                upSeq.Add(MouseInput(0, 0, 0, NativeMethods.MOUSEEVENTF_LEFTUP));
                DispatchInputs(upSeq);
                Thread.Sleep(12);

                List<NativeMethods.INPUT> ctrlUpSeq = new List<NativeMethods.INPUT>(1);
                ctrlUpSeq.Add(KeyInput(VK_LCONTROL, 0, NativeMethods.KEYEVENTF_KEYUP));
                DispatchInputs(ctrlUpSeq);
            }
        }
    }
}
