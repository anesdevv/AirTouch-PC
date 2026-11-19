using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.Net;
using System.Reflection;
using System.Text;
using System.Windows.Forms;

namespace AirTouchPC
{
    public class MainForm : Form
    {
        private AirTouchServer _server;
        private List<NetworkAdapterInfo> _adapters;
        private NetworkAdapterInfo _selectedAdapter;

        // UI Surfaces
        private Panel _pnlHeader;
        private PictureBox _picIcon;
        private Label _lblAppTitle;
        private Label _lblHeaderStatus;

        private Panel _cardSession;
        // Standby Controls
        private Panel _pnlStandbyView;
        private Label _lblPairTitle;
        private Label _lblPairDesc;
        private PictureBox _picQr;
        private Label _lblQrToast;
        private Label _lblAdapterTitle;
        private ComboBox _cboIp;
        private Label _lblNetworkMeta;
        private Button _btnCopyLink;

        // Active Session Controls
        private Panel _pnlConnectedView;
        private Label _lblConnectedHeader;
        private Label _lblConnectedDevice;
        private Label _lblSessionDuration;
        private Label _lblPacketCount;
        private Button _btnDisconnect;

        // Telemetry Bar
        private Panel _pnlTelemetry;
        private Label _lblTelemetryHeader;
        private Label _telTouch;
        private Label _telClick;
        private Label _telScroll;
        private Label _telKey;
        private Label _telLaser;

        // Footer Bar
        private Panel _pnlFooter;
        private Label _lblFirewall;
        private Button _btnFirewallFix;
        private CheckBox _chkMinimizeOnClose;
        private Button _btnDiagnostics;

        // Diagnostics Drawer
        private Panel _pnlDiagnostics;
        private RichTextBox _rtbDiag;
        private Button _btnCloseDiag;

        // System Tray
        private NotifyIcon _notifyIcon;
        private ContextMenuStrip _trayMenu;
        private ToolStripMenuItem _menuItemStatus;

        private Icon _appIcon;
        private Image _appLogo;
        private Label _lblClientEp;
        private bool _isExiting = false;
        private bool _hasShownTrayBalloon = false;

        // Telemetry decay timers
        private int _decayTouch = 0;
        private int _decayClick = 0;
        private int _decayScroll = 0;
        private int _decayKey = 0;
        private int _decayLaser = 0;
        private Timer _telemetryDecayTimer;
        private Timer _uptimeTimer;

        // Dark Palette
        private static readonly Color BgApp = Color.FromArgb(13, 17, 23);         // #0D1117
        private static readonly Color BgCard = Color.FromArgb(22, 27, 34);        // #161B22
        private static readonly Color BorderCard = Color.FromArgb(48, 54, 61);    // #30363D
        private static readonly Color TextPrimary = Color.FromArgb(240, 246, 252); // #F0F6FC
        private static readonly Color TextMuted = Color.FromArgb(139, 148, 158);   // #8B949E
        private static readonly Color TextSubtle = Color.FromArgb(110, 118, 129);  // #6E7681
        private static readonly Color AccentBlue = Color.FromArgb(37, 99, 235);    // #2563EB
        private static readonly Color AccentBlueHover = Color.FromArgb(29, 78, 216);// #1D4ED8
        private static readonly Color SuccessGreen = Color.FromArgb(16, 185, 129); // #10B981
        private static readonly Color WarningAmber = Color.FromArgb(245, 158, 11); // #F59E0B

        public MainForm()
        {
            LoadAppIcon();
            BuildInterface();
            InitializeServer();
            CheckFirewallStatusAsync();
        }

        private void LoadAppIcon()
        {
            try
            {
                using (Stream stream = Assembly.GetExecutingAssembly().GetManifestResourceStream("AirTouchPC.AirTouchPC.ico"))
                {
                    if (stream != null)
                    {
                        _appIcon = new Icon(stream);
                    }
                }
            }
            catch { }

            if (_appIcon == null)
            {
                try
                {
                    if (File.Exists("AirTouchPC.ico"))
                    {
                        _appIcon = new Icon("AirTouchPC.ico");
                    }
                    else
                    {
                        _appIcon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);
                    }
                }
                catch { }
            }

            try
            {
                using (Stream stream = Assembly.GetExecutingAssembly().GetManifestResourceStream("AirTouchPC.icon_256.png"))
                {
                    if (stream != null)
                    {
                        _appLogo = Image.FromStream(stream);
                    }
                }
            }
            catch { }

            if (_appLogo == null)
            {
                try
                {
                    if (File.Exists("icon_256.png"))
                    {
                        _appLogo = Image.FromFile("icon_256.png");
                    }
                    else if (File.Exists("app_assets\\icon_256.png"))
                    {
                        _appLogo = Image.FromFile("app_assets\\icon_256.png");
                    }
                }
                catch { }
            }
        }

        private void BuildInterface()
        {
            FontFamily baseFamily = SystemFonts.MessageBoxFont.FontFamily;

            this.SuspendLayout();

            this.Text = "AirTouch PC Companion";
            this.ClientSize = new Size(460, 590);
            this.FormBorderStyle = FormBorderStyle.FixedSingle;
            this.MaximizeBox = false;
            this.StartPosition = FormStartPosition.CenterScreen;
            this.BackColor = BgApp;
            this.ForeColor = TextPrimary;
            this.AutoScaleMode = AutoScaleMode.None;
            this.DoubleBuffered = true;

            if (_appIcon != null)
            {
                this.Icon = _appIcon;
            }

            // 1. Header (Height: 56)
            _pnlHeader = new Panel
            {
                Location = new Point(0, 0),
                Size = new Size(460, 56),
                Dock = DockStyle.Top,
                BackColor = BgApp
            };
            _pnlHeader.Paint += delegate(object s, PaintEventArgs e)
            {
                using (Pen borderPen = new Pen(BorderCard, 1))
                {
                    e.Graphics.DrawLine(borderPen, 0, _pnlHeader.Height - 1, _pnlHeader.Width, _pnlHeader.Height - 1);
                }
            };

            _picIcon = new PictureBox
            {
                Location = new Point(18, 12),
                Size = new Size(32, 32),
                BackColor = Color.Transparent
            };
            _picIcon.Paint += delegate(object s, PaintEventArgs e)
            {
                if (_appLogo != null)
                {
                    e.Graphics.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.HighQualityBicubic;
                    e.Graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.HighQuality;
                    e.Graphics.PixelOffsetMode = System.Drawing.Drawing2D.PixelOffsetMode.HighQuality;
                    e.Graphics.DrawImage(_appLogo, new Rectangle(0, 0, _picIcon.Width, _picIcon.Height));
                }
                else if (_appIcon != null)
                {
                    e.Graphics.DrawIcon(_appIcon, new Rectangle(0, 0, _picIcon.Width, _picIcon.Height));
                }
            };

            _lblAppTitle = new Label
            {
                Location = new Point(58, 17),
                Size = new Size(180, 22),
                Text = "AirTouch PC",
                Font = new Font(baseFamily, 12.5f, FontStyle.Bold),
                ForeColor = TextPrimary
            };

            _lblHeaderStatus = new Label
            {
                Location = new Point(312, 16),
                Size = new Size(130, 24),
                TextAlign = ContentAlignment.MiddleRight,
                Text = "● Ready",
                Font = new Font(baseFamily, 9f, FontStyle.Regular),
                ForeColor = TextMuted
            };

            _pnlHeader.Controls.Add(_picIcon);
            _pnlHeader.Controls.Add(_lblAppTitle);
            _pnlHeader.Controls.Add(_lblHeaderStatus);

            // 2. Central Card (Height: 336)
            _cardSession = new Panel
            {
                Location = new Point(18, 70),
                Size = new Size(424, 342),
                BackColor = BgCard
            };
            _cardSession.Paint += delegate(object s, PaintEventArgs e)
            {
                using (Pen pen = new Pen(BorderCard, 1))
                {
                    e.Graphics.DrawRectangle(pen, 0, 0, _cardSession.Width - 1, _cardSession.Height - 1);
                }
            };

            // Standby View Panel
            _pnlStandbyView = new Panel
            {
                Location = new Point(0, 0),
                Size = new Size(424, 342),
                BackColor = BgCard,
                Visible = true
            };

            _lblPairTitle = new Label
            {
                Location = new Point(20, 16),
                Size = new Size(384, 20),
                Text = "Pair with phone",
                Font = new Font(baseFamily, 11f, FontStyle.Bold),
                ForeColor = TextPrimary
            };

            _lblPairDesc = new Label
            {
                Location = new Point(20, 38),
                Size = new Size(384, 18),
                Text = "Scan with AirTouch or tap the code to copy link",
                Font = new Font(baseFamily, 8.5f, FontStyle.Regular),
                ForeColor = TextMuted
            };

            _picQr = new PictureBox
            {
                Location = new Point(20, 68),
                Size = new Size(174, 174),
                SizeMode = PictureBoxSizeMode.CenterImage,
                Cursor = Cursors.Hand,
                BackColor = Color.White
            };
            _picQr.Click += delegate { CopyPairingLink(); };

            _lblQrToast = new Label
            {
                Location = new Point(20, 246),
                Size = new Size(174, 20),
                TextAlign = ContentAlignment.MiddleCenter,
                Text = "Tap QR to copy link",
                Font = new Font(baseFamily, 8f, FontStyle.Regular),
                ForeColor = TextSubtle
            };

            _lblAdapterTitle = new Label
            {
                Location = new Point(210, 68),
                Size = new Size(196, 18),
                Text = "Network Interface",
                Font = new Font(baseFamily, 8.5f, FontStyle.Bold),
                ForeColor = TextMuted
            };

            _cboIp = new ComboBox
            {
                Location = new Point(210, 88),
                Size = new Size(196, 26),
                DropDownStyle = ComboBoxStyle.DropDownList,
                BackColor = Color.FromArgb(33, 38, 45),
                ForeColor = TextPrimary,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Consolas", 8.5f, FontStyle.Regular)
            };
            _cboIp.SelectedIndexChanged += delegate
            {
                if (_cboIp.SelectedItem is NetworkAdapterInfo)
                {
                    _selectedAdapter = (NetworkAdapterInfo)_cboIp.SelectedItem;
                    UpdateQrCode();
                    UpdateNetworkMeta();
                }
            };

            _lblNetworkMeta = new Label
            {
                Location = new Point(210, 122),
                Size = new Size(196, 68),
                Font = new Font("Consolas", 8.5f, FontStyle.Regular),
                ForeColor = TextMuted,
                Text = "Host: " + Environment.MachineName + "\n" +
                       "Port TCP: 54546\n" +
                       "Port UDP: 54545"
            };

            _btnCopyLink = new Button
            {
                Location = new Point(210, 204),
                Size = new Size(196, 38),
                Text = "Copy Pairing Link",
                FlatStyle = FlatStyle.Flat,
                BackColor = AccentBlue,
                ForeColor = Color.White,
                Cursor = Cursors.Hand,
                Font = new Font(baseFamily, 9f, FontStyle.Bold)
            };
            _btnCopyLink.FlatAppearance.BorderSize = 0;
            _btnCopyLink.MouseEnter += delegate { _btnCopyLink.BackColor = AccentBlueHover; };
            _btnCopyLink.MouseLeave += delegate { _btnCopyLink.BackColor = AccentBlue; };
            _btnCopyLink.Click += delegate { CopyPairingLink(); };

            // Standby controls added to standby panel
            _pnlStandbyView.Controls.Add(_lblPairTitle);
            _pnlStandbyView.Controls.Add(_lblPairDesc);
            _pnlStandbyView.Controls.Add(_picQr);
            _pnlStandbyView.Controls.Add(_lblQrToast);
            _pnlStandbyView.Controls.Add(_lblAdapterTitle);
            _pnlStandbyView.Controls.Add(_cboIp);
            _pnlStandbyView.Controls.Add(_lblNetworkMeta);
            _pnlStandbyView.Controls.Add(_btnCopyLink);
            _cardSession.Controls.Add(_pnlStandbyView);

            // Active Connected View (Overlay Panel inside cardSession, hidden initially)
            _pnlConnectedView = new Panel
            {
                Location = new Point(0, 0),
                Size = new Size(424, 342),
                BackColor = BgCard,
                Visible = false
            };

            _lblConnectedHeader = new Label
            {
                Location = new Point(24, 20),
                Size = new Size(376, 26),
                Text = "Phone Connected",
                Font = new Font(baseFamily, 13.5f, FontStyle.Bold),
                ForeColor = SuccessGreen
            };

            _lblConnectedDevice = new Label
            {
                Location = new Point(24, 46),
                Size = new Size(376, 18),
                Text = "Active touch & input session",
                Font = new Font(baseFamily, 8.5f, FontStyle.Regular),
                ForeColor = TextMuted
            };

            // Two stat cards (Session Duration & Events Processed)
            Panel pnlStat1 = new Panel
            {
                Location = new Point(24, 78),
                Size = new Size(178, 76),
                BackColor = Color.FromArgb(17, 21, 28)
            };
            pnlStat1.Paint += delegate(object s, PaintEventArgs e)
            {
                using (Pen p = new Pen(BorderCard, 1))
                {
                    e.Graphics.DrawRectangle(p, 0, 0, pnlStat1.Width - 1, pnlStat1.Height - 1);
                }
            };
            Label lblStat1Title = new Label
            {
                Location = new Point(14, 12),
                Size = new Size(150, 16),
                Text = "SESSION TIME",
                Font = new Font(baseFamily, 7.5f, FontStyle.Bold),
                ForeColor = TextSubtle
            };
            _lblSessionDuration = new Label
            {
                Location = new Point(14, 32),
                Size = new Size(150, 32),
                Text = "00:00",
                Font = new Font("Consolas", 15f, FontStyle.Bold),
                ForeColor = TextPrimary
            };
            pnlStat1.Controls.Add(lblStat1Title);
            pnlStat1.Controls.Add(_lblSessionDuration);

            Panel pnlStat2 = new Panel
            {
                Location = new Point(218, 78),
                Size = new Size(178, 76),
                BackColor = Color.FromArgb(17, 21, 28)
            };
            pnlStat2.Paint += delegate(object s, PaintEventArgs e)
            {
                using (Pen p = new Pen(BorderCard, 1))
                {
                    e.Graphics.DrawRectangle(p, 0, 0, pnlStat2.Width - 1, pnlStat2.Height - 1);
                }
            };
            Label lblStat2Title = new Label
            {
                Location = new Point(14, 12),
                Size = new Size(150, 16),
                Text = "INPUTS RECEIVED",
                Font = new Font(baseFamily, 7.5f, FontStyle.Bold),
                ForeColor = TextSubtle
            };
            _lblPacketCount = new Label
            {
                Location = new Point(14, 32),
                Size = new Size(150, 32),
                Text = "0",
                Font = new Font("Consolas", 15f, FontStyle.Bold),
                ForeColor = TextPrimary
            };
            pnlStat2.Controls.Add(lblStat2Title);
            pnlStat2.Controls.Add(_lblPacketCount);

            Label lblClientInfo = new Label
            {
                Location = new Point(24, 174),
                Size = new Size(376, 18),
                Text = "REMOTE ENDPOINT",
                Font = new Font(baseFamily, 7.5f, FontStyle.Bold),
                ForeColor = TextSubtle
            };

            _lblClientEp = new Label
            {
                Location = new Point(24, 194),
                Size = new Size(376, 24),
                Text = "192.168.1.x:54546",
                Font = new Font("Consolas", 9.5f, FontStyle.Regular),
                ForeColor = TextPrimary
            };

            _btnDisconnect = new Button
            {
                Location = new Point(24, 242),
                Size = new Size(140, 36),
                Text = "Disconnect",
                FlatStyle = FlatStyle.Flat,
                BackColor = Color.FromArgb(33, 38, 45),
                ForeColor = Color.FromArgb(248, 113, 113),
                Cursor = Cursors.Hand,
                Font = new Font(baseFamily, 9f, FontStyle.Bold)
            };
            _btnDisconnect.FlatAppearance.BorderColor = BorderCard;
            _btnDisconnect.MouseEnter += delegate { _btnDisconnect.BackColor = Color.FromArgb(50, 26, 30); };
            _btnDisconnect.MouseLeave += delegate { _btnDisconnect.BackColor = Color.FromArgb(33, 38, 45); };
            _btnDisconnect.Click += delegate
            {
                if (_server != null)
                {
                    _server.DisconnectAllClients();
                }
            };

            _pnlConnectedView.Controls.Add(_lblConnectedHeader);
            _pnlConnectedView.Controls.Add(_lblConnectedDevice);
            _pnlConnectedView.Controls.Add(pnlStat1);
            _pnlConnectedView.Controls.Add(pnlStat2);
            _pnlConnectedView.Controls.Add(lblClientInfo);
            _pnlConnectedView.Controls.Add(_lblClientEp);
            _pnlConnectedView.Controls.Add(_btnDisconnect);
            _cardSession.Controls.Add(_pnlConnectedView);

            // 3. Telemetry Bar (Height: 52)
            _pnlTelemetry = new Panel
            {
                Location = new Point(18, 424),
                Size = new Size(424, 52),
                BackColor = BgCard
            };
            _pnlTelemetry.Paint += delegate(object s, PaintEventArgs e)
            {
                using (Pen pen = new Pen(BorderCard, 1))
                {
                    e.Graphics.DrawRectangle(pen, 0, 0, _pnlTelemetry.Width - 1, _pnlTelemetry.Height - 1);
                }
            };

            _lblTelemetryHeader = new Label
            {
                Location = new Point(14, 16),
                Size = new Size(68, 20),
                Text = "Signals",
                Font = new Font(baseFamily, 8f, FontStyle.Bold),
                ForeColor = TextSubtle
            };

            _telTouch = CreateTelemetryPill("Touch", 86);
            _telClick = CreateTelemetryPill("Click", 152);
            _telScroll = CreateTelemetryPill("Scroll", 218);
            _telKey = CreateTelemetryPill("Keys", 284);
            _telLaser = CreateTelemetryPill("Laser", 350);

            _pnlTelemetry.Controls.Add(_lblTelemetryHeader);
            _pnlTelemetry.Controls.Add(_telTouch);
            _pnlTelemetry.Controls.Add(_telClick);
            _pnlTelemetry.Controls.Add(_telScroll);
            _pnlTelemetry.Controls.Add(_telKey);
            _pnlTelemetry.Controls.Add(_telLaser);

            // 4. Footer Bar (Height: 46)
            _pnlFooter = new Panel
            {
                Location = new Point(0, 544),
                Size = new Size(460, 46),
                Dock = DockStyle.Bottom,
                BackColor = BgApp
            };

            _lblFirewall = new Label
            {
                Location = new Point(18, 13),
                Size = new Size(130, 20),
                Text = "Firewall: checking...",
                Font = new Font(baseFamily, 8f, FontStyle.Regular),
                ForeColor = TextSubtle
            };

            _btnFirewallFix = new Button
            {
                Location = new Point(148, 9),
                Size = new Size(72, 26),
                Text = "Allow",
                FlatStyle = FlatStyle.Flat,
                BackColor = Color.FromArgb(33, 38, 45),
                ForeColor = WarningAmber,
                Visible = false,
                Cursor = Cursors.Hand,
                Font = new Font(baseFamily, 8f, FontStyle.Bold)
            };
            _btnFirewallFix.FlatAppearance.BorderColor = Color.FromArgb(90, 60, 15);
            _btnFirewallFix.Click += delegate { ConfigureFirewall(); };

            _chkMinimizeOnClose = new CheckBox
            {
                Location = new Point(230, 11),
                Size = new Size(130, 24),
                Text = "Tray on close",
                Checked = true,
                Font = new Font(baseFamily, 8f, FontStyle.Regular),
                ForeColor = TextMuted
            };

            _btnDiagnostics = new Button
            {
                Location = new Point(366, 9),
                Size = new Size(76, 26),
                Text = "Logs",
                FlatStyle = FlatStyle.Flat,
                BackColor = Color.Transparent,
                ForeColor = TextMuted,
                Cursor = Cursors.Hand,
                Font = new Font(baseFamily, 8f, FontStyle.Regular)
            };
            _btnDiagnostics.FlatAppearance.BorderSize = 0;
            _btnDiagnostics.Click += delegate { ToggleDiagnostics(); };

            _pnlFooter.Controls.Add(_lblFirewall);
            _pnlFooter.Controls.Add(_btnFirewallFix);
            _pnlFooter.Controls.Add(_chkMinimizeOnClose);
            _pnlFooter.Controls.Add(_btnDiagnostics);

            // 5. Diagnostics Drawer (collapsible overlay at bottom of card)
            _pnlDiagnostics = new Panel
            {
                Location = new Point(18, 200),
                Size = new Size(424, 276),
                BackColor = Color.FromArgb(10, 14, 20),
                Visible = false
            };
            _pnlDiagnostics.Paint += delegate(object s, PaintEventArgs e)
            {
                using (Pen pen = new Pen(BorderCard, 1))
                {
                    e.Graphics.DrawRectangle(pen, 0, 0, _pnlDiagnostics.Width - 1, _pnlDiagnostics.Height - 1);
                }
            };

            Label lblDiagTitle = new Label
            {
                Location = new Point(14, 10),
                Size = new Size(200, 20),
                Text = "Diagnostic Log",
                Font = new Font(baseFamily, 8.5f, FontStyle.Bold),
                ForeColor = TextMuted
            };

            _btnCloseDiag = new Button
            {
                Location = new Point(350, 6),
                Size = new Size(60, 24),
                Text = "Close",
                FlatStyle = FlatStyle.Flat,
                BackColor = Color.Transparent,
                ForeColor = TextMuted,
                Cursor = Cursors.Hand,
                Font = new Font(baseFamily, 8f, FontStyle.Regular)
            };
            _btnCloseDiag.FlatAppearance.BorderSize = 0;
            _btnCloseDiag.Click += delegate { ToggleDiagnostics(); };

            _rtbDiag = new RichTextBox
            {
                Location = new Point(14, 34),
                Size = new Size(396, 230),
                BackColor = Color.FromArgb(10, 14, 20),
                ForeColor = TextMuted,
                BorderStyle = BorderStyle.None,
                Font = new Font("Consolas", 8f, FontStyle.Regular),
                ReadOnly = true
            };

            _pnlDiagnostics.Controls.Add(lblDiagTitle);
            _pnlDiagnostics.Controls.Add(_btnCloseDiag);
            _pnlDiagnostics.Controls.Add(_rtbDiag);

            // Add all surfaces to Form
            this.Controls.Add(_pnlDiagnostics);
            this.Controls.Add(_pnlFooter);
            this.Controls.Add(_pnlTelemetry);
            this.Controls.Add(_cardSession);
            this.Controls.Add(_pnlHeader);

            // 6. System Tray & Context Menu
            _trayMenu = new ContextMenuStrip();
            ToolStripMenuItem itemOpen = new ToolStripMenuItem("Open AirTouch PC", null, delegate { RestoreFromTray(); })
            {
                Font = new Font(baseFamily, 9f, FontStyle.Bold)
            };
            _menuItemStatus = new ToolStripMenuItem("Status: Standby", null) { Enabled = false };
            ToolStripMenuItem itemCopyLink = new ToolStripMenuItem("Copy Pairing Link", null, delegate { CopyPairingLink(); });
            ToolStripMenuItem itemFirewall = new ToolStripMenuItem("Firewall Permissions...", null, delegate { ConfigureFirewall(); });
            ToolStripSeparator sep = new ToolStripSeparator();
            ToolStripMenuItem itemExit = new ToolStripMenuItem("Exit AirTouch PC", null, delegate { ExitApplication(); });

            _trayMenu.Items.Add(itemOpen);
            _trayMenu.Items.Add(_menuItemStatus);
            _trayMenu.Items.Add(itemCopyLink);
            _trayMenu.Items.Add(itemFirewall);
            _trayMenu.Items.Add(sep);
            _trayMenu.Items.Add(itemExit);

            _notifyIcon = new NotifyIcon
            {
                Icon = _appIcon,
                Text = "AirTouch PC - Ready",
                Visible = (_appIcon != null),
                ContextMenuStrip = _trayMenu
            };
            _notifyIcon.DoubleClick += delegate { RestoreFromTray(); };

            this.FormClosing += MainForm_FormClosing;

            // 7. Setup Timers
            _telemetryDecayTimer = new Timer { Interval = 40 };
            _telemetryDecayTimer.Tick += TelemetryDecayTimer_Tick;
            _telemetryDecayTimer.Start();

            _uptimeTimer = new Timer { Interval = 1000 };
            _uptimeTimer.Tick += UptimeTimer_Tick;
            _uptimeTimer.Start();

            this.ResumeLayout(false);
        }

        private Label CreateTelemetryPill(string label, int x)
        {
            FontFamily baseFamily = SystemFonts.MessageBoxFont.FontFamily;
            Label lbl = new Label
            {
                Location = new Point(x, 14),
                Size = new Size(58, 24),
                Text = label,
                TextAlign = ContentAlignment.MiddleCenter,
                Font = new Font(baseFamily, 7.5f, FontStyle.Bold),
                BackColor = Color.FromArgb(20, 24, 30),
                ForeColor = TextSubtle,
                BorderStyle = BorderStyle.FixedSingle
            };
            return lbl;
        }

        private void InitializeServer()
        {
            _adapters = AirTouchServer.GetRankedNetworkAdapters();
            _cboIp.Items.Clear();

            foreach (NetworkAdapterInfo ad in _adapters)
            {
                _cboIp.Items.Add(ad);
            }

            if (_adapters.Count > 0)
            {
                _cboIp.SelectedIndex = 0;
                _selectedAdapter = _adapters[0];
            }

            UpdateQrCode();
            UpdateNetworkMeta();

            _server = new AirTouchServer();
            _server.OnLog += Server_OnLog;
            _server.OnClientConnected += Server_OnClientConnected;
            _server.OnClientDisconnected += Server_OnClientDisconnected;
            _server.OnTelemetry += Server_OnTelemetry;
            _server.Start();
        }

        private string BuildPairingPayload()
        {
            string primaryIp = _selectedAdapter != null ? _selectedAdapter.Ip : "127.0.0.1";
            string hostname = Environment.MachineName;
            string shortHost = hostname.Length > 14 ? hostname.Substring(0, 14) : hostname;

            string payload = "airtouch://" + primaryIp + ":" + AirTouchServer.TcpPort +
                             "?u=" + AirTouchServer.UdpPort + "&n=" + Uri.EscapeDataString(shortHost);

            if (_adapters != null && _adapters.Count > 1)
            {
                List<string> alts = new List<string>();
                for (int i = 0; i < _adapters.Count; i++)
                {
                    if (_adapters[i].Ip != primaryIp && alts.Count < 2)
                    {
                        alts.Add(_adapters[i].Ip);
                    }
                }
                if (alts.Count > 0)
                {
                    string cand = payload + "&a=" + string.Join(",", alts.ToArray());
                    if (Encoding.UTF8.GetByteCount(cand) <= 76)
                    {
                        payload = cand;
                    }
                }
            }

            return payload;
        }

        private void UpdateQrCode()
        {
            string payload = BuildPairingPayload();
            Bitmap qr = QrCodeGenerator.GenerateQrBitmap(payload, 174, 3);
            if (qr != null)
            {
                Image old = _picQr.Image;
                _picQr.Image = qr;
                if (old != null) old.Dispose();
            }
        }

        private void UpdateNetworkMeta()
        {
            string adapterType = _selectedAdapter != null ? _selectedAdapter.TypeName : "LAN";
            string ip = _selectedAdapter != null ? _selectedAdapter.Ip : "0.0.0.0";
            _lblNetworkMeta.Text = string.Format("Host: {0}\nIP: {1} ({2})\nTCP 54546 • UDP 54545",
                Environment.MachineName, ip, adapterType);
        }

        private void CopyPairingLink()
        {
            string payload = BuildPairingPayload();
            try
            {
                Clipboard.SetText(payload);
                _lblQrToast.Text = "✓ Link copied";
                _lblQrToast.ForeColor = SuccessGreen;

                string origBtnText = _btnCopyLink.Text;
                _btnCopyLink.Text = "Copied to clipboard";
                _btnCopyLink.BackColor = SuccessGreen;

                Timer t = new Timer { Interval = 1600 };
                t.Tick += delegate
                {
                    _lblQrToast.Text = "Tap QR to copy link";
                    _lblQrToast.ForeColor = TextSubtle;
                    _btnCopyLink.Text = origBtnText;
                    _btnCopyLink.BackColor = AccentBlue;
                    t.Stop();
                    t.Dispose();
                };
                t.Start();
                AppendDiagLog("Copied pairing link: " + payload, "INFO");
            }
            catch (Exception ex)
            {
                MessageBox.Show("Could not copy link: " + ex.Message, "AirTouch PC", MessageBoxButtons.OK, MessageBoxIcon.Warning);
            }
        }

        private void CheckFirewallStatusAsync()
        {
            System.Threading.ThreadPool.QueueUserWorkItem(delegate
            {
                bool allowed = false;
                try
                {
                    ProcessStartInfo psi = new ProcessStartInfo("netsh", "advfirewall firewall show rule name=\"AirTouch PC TCP\"")
                    {
                        CreateNoWindow = true,
                        UseShellExecute = false,
                        RedirectStandardOutput = true,
                        RedirectStandardError = true
                    };
                    using (Process p = Process.Start(psi))
                    {
                        p.WaitForExit(3000);
                        allowed = (p.ExitCode == 0);
                    }
                }
                catch { }

                if (this.IsHandleCreated)
                {
                    this.BeginInvoke((MethodInvoker)delegate
                    {
                        if (allowed)
                        {
                            _lblFirewall.Text = "● Firewall ready";
                            _lblFirewall.ForeColor = SuccessGreen;
                            _btnFirewallFix.Visible = false;
                        }
                        else
                        {
                            _lblFirewall.Text = "● Inbound restricted";
                            _lblFirewall.ForeColor = WarningAmber;
                            _btnFirewallFix.Visible = true;
                        }
                    });
                }
            });
        }

        private void ConfigureFirewall()
        {
            string exePath = Application.ExecutablePath;
            string fwCmd = string.Format(
                "/c netsh advfirewall firewall add rule name=\"AirTouch PC TCP\" dir=in action=allow protocol=TCP localport={0} profile=any & " +
                "netsh advfirewall firewall add rule name=\"AirTouch PC UDP\" dir=in action=allow protocol=UDP localport={1} profile=any & " +
                "netsh advfirewall firewall add rule name=\"AirTouch PC App\" dir=in action=allow program=\"{2}\" enable=yes profile=any",
                AirTouchServer.TcpPort, AirTouchServer.UdpPort, exePath
            );

            try
            {
                ProcessStartInfo psi = new ProcessStartInfo("cmd.exe", fwCmd)
                {
                    Verb = "runas",
                    WindowStyle = ProcessWindowStyle.Hidden,
                    UseShellExecute = true
                };
                using (Process p = Process.Start(psi))
                {
                    p.WaitForExit();
                    AppendDiagLog("Firewall rules configured successfully.", "SUCCESS");
                    _lblFirewall.Text = "● Firewall ready";
                    _lblFirewall.ForeColor = SuccessGreen;
                    _btnFirewallFix.Visible = false;
                }
            }
            catch (Exception ex)
            {
                AppendDiagLog("Firewall configuration declined: " + ex.Message, "WARN");
            }
        }

        private void Server_OnLog(string msg, string level)
        {
            if (this.IsHandleCreated)
            {
                this.BeginInvoke((MethodInvoker)delegate
                {
                    AppendDiagLog(msg, level);
                });
            }
        }

        public void SetConnectedForTest(bool connected, string ep = "192.168.1.45:51234")
        {
            if (connected)
            {
                if (_pnlStandbyView != null) _pnlStandbyView.Visible = false;
                _pnlConnectedView.Visible = true;
                _pnlConnectedView.BringToFront();
                _lblHeaderStatus.Text = "● Connected";
                _lblHeaderStatus.ForeColor = SuccessGreen;
                if (_lblClientEp != null) _lblClientEp.Text = ep;
                if (_lblSessionDuration != null) _lblSessionDuration.Text = "04:12";
                if (_lblPacketCount != null) _lblPacketCount.Text = "1,842";
            }
            else
            {
                _pnlConnectedView.Visible = false;
                if (_pnlStandbyView != null)
                {
                    _pnlStandbyView.Visible = true;
                    _pnlStandbyView.BringToFront();
                }
                _lblHeaderStatus.Text = "● Ready";
                _lblHeaderStatus.ForeColor = TextMuted;
            }
        }

        private void Server_OnClientConnected(string remoteEp)
        {
            if (this.IsHandleCreated)
            {
                this.BeginInvoke((MethodInvoker)delegate
                {
                    if (_pnlStandbyView != null) _pnlStandbyView.Visible = false;
                    _pnlConnectedView.Visible = true;
                    _pnlConnectedView.BringToFront();

                    _lblHeaderStatus.Text = "● Connected";
                    _lblHeaderStatus.ForeColor = SuccessGreen;
                    if (_lblClientEp != null) _lblClientEp.Text = remoteEp;
                    _menuItemStatus.Text = "Status: Connected (" + remoteEp + ")";
                    _notifyIcon.Text = "AirTouch PC - Connected";

                    _notifyIcon.ShowBalloonTip(
                        2000,
                        "AirTouch Connected",
                        "Phone connected from " + remoteEp,
                        ToolTipIcon.Info
                    );
                });
            }
        }

        private void Server_OnClientDisconnected(string remoteEp)
        {
            if (this.IsHandleCreated)
            {
                this.BeginInvoke((MethodInvoker)delegate
                {
                    _pnlConnectedView.Visible = false;
                    if (_pnlStandbyView != null)
                    {
                        _pnlStandbyView.Visible = true;
                        _pnlStandbyView.BringToFront();
                    }

                    _lblHeaderStatus.Text = "● Ready";
                    _lblHeaderStatus.ForeColor = TextMuted;
                    _menuItemStatus.Text = "Status: Standby";
                    _notifyIcon.Text = "AirTouch PC - Ready";
                });
            }
        }

        private void Server_OnTelemetry(TelemetryInputType type, string details)
        {
            if (type == TelemetryInputType.MouseMove) _decayTouch = 8;
            else if (type == TelemetryInputType.MouseClick) _decayClick = 12;
            else if (type == TelemetryInputType.MouseScroll) _decayScroll = 10;
            else if (type == TelemetryInputType.Keyboard || type == TelemetryInputType.Text) _decayKey = 10;
            else if (type == TelemetryInputType.Laser) _decayLaser = 12;
        }

        private void TelemetryDecayTimer_Tick(object sender, EventArgs e)
        {
            UpdatePillColor(_telTouch, ref _decayTouch, Color.FromArgb(56, 189, 248));
            UpdatePillColor(_telClick, ref _decayClick, Color.FromArgb(52, 211, 153));
            UpdatePillColor(_telScroll, ref _decayScroll, Color.FromArgb(167, 139, 250));
            UpdatePillColor(_telKey, ref _decayKey, Color.FromArgb(251, 191, 36));
            UpdatePillColor(_telLaser, ref _decayLaser, Color.FromArgb(248, 113, 113));
        }

        private void UpdatePillColor(Label pill, ref int decay, Color activeColor)
        {
            if (decay > 0)
            {
                decay--;
                pill.BackColor = Color.FromArgb(30, 41, 59);
                pill.ForeColor = activeColor;
            }
            else
            {
                if (pill.ForeColor != TextSubtle)
                {
                    pill.BackColor = Color.FromArgb(20, 24, 30);
                    pill.ForeColor = TextSubtle;
                }
            }
        }

        private void UptimeTimer_Tick(object sender, EventArgs e)
        {
            if (_server != null && _server.HasConnectedClient && _server.ConnectedSince.HasValue)
            {
                TimeSpan elapsed = DateTime.Now - _server.ConnectedSince.Value;
                _lblSessionDuration.Text = string.Format("Session: {0:D2}:{1:D2}:{2:D2}",
                    (int)elapsed.TotalHours, elapsed.Minutes, elapsed.Seconds);
                _lblPacketCount.Text = "Events Received: " + _server.TotalPackets.ToString("N0");
            }
        }

        private void ToggleDiagnostics()
        {
            _pnlDiagnostics.Visible = !_pnlDiagnostics.Visible;
            if (_pnlDiagnostics.Visible)
            {
                _pnlDiagnostics.BringToFront();
                _btnDiagnostics.ForeColor = TextPrimary;
            }
            else
            {
                _btnDiagnostics.ForeColor = TextMuted;
            }
        }

        private void AppendDiagLog(string msg, string level)
        {
            if (_rtbDiag == null || _rtbDiag.IsDisposed) return;

            string time = DateTime.Now.ToString("HH:mm:ss");
            Color color = TextMuted;
            if (level == "SUCCESS") color = SuccessGreen;
            else if (level == "WARN") color = WarningAmber;
            else if (level == "ERROR") color = Color.FromArgb(248, 113, 113);

            _rtbDiag.SelectionStart = _rtbDiag.TextLength;
            _rtbDiag.SelectionLength = 0;
            _rtbDiag.SelectionColor = TextSubtle;
            _rtbDiag.AppendText("[" + time + "] ");

            _rtbDiag.SelectionColor = color;
            _rtbDiag.AppendText(msg + "\r\n");
            _rtbDiag.ScrollToCaret();

            if (_rtbDiag.Lines.Length > 200)
            {
                _rtbDiag.Select(0, _rtbDiag.GetFirstCharIndexFromLine(50));
                _rtbDiag.SelectedText = "";
            }
        }

        private void MinimizeToTray()
        {
            this.Hide();
            if (!_hasShownTrayBalloon)
            {
                _hasShownTrayBalloon = true;
                _notifyIcon.ShowBalloonTip(
                    2000,
                    "AirTouch PC is Running",
                    "Active in tray. Double-click to open, or right-click to exit.",
                    ToolTipIcon.Info
                );
            }
        }

        private void RestoreFromTray()
        {
            this.Show();
            if (this.WindowState == FormWindowState.Minimized)
            {
                this.WindowState = FormWindowState.Normal;
            }
            this.BringToFront();
            this.Activate();
        }

        private void MainForm_FormClosing(object sender, FormClosingEventArgs e)
        {
            if (!_isExiting && _chkMinimizeOnClose.Checked && e.CloseReason == CloseReason.UserClosing)
            {
                e.Cancel = true;
                MinimizeToTray();
            }
            else
            {
                CleanUp();
            }
        }

        private void ExitApplication()
        {
            _isExiting = true;
            CleanUp();
            Application.Exit();
        }

        private void CleanUp()
        {
            if (_telemetryDecayTimer != null)
            {
                _telemetryDecayTimer.Stop();
                _telemetryDecayTimer.Dispose();
                _telemetryDecayTimer = null;
            }
            if (_uptimeTimer != null)
            {
                _uptimeTimer.Stop();
                _uptimeTimer.Dispose();
                _uptimeTimer = null;
            }
            if (_notifyIcon != null)
            {
                _notifyIcon.Visible = false;
                _notifyIcon.Dispose();
                _notifyIcon = null;
            }
            if (_server != null)
            {
                _server.Stop();
                _server = null;
            }
        }
    }
}
