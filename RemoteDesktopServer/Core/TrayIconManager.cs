using System;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Interop;

namespace RemoteDesktopServer.Core
{
    public class TrayIconManager : IDisposable
    {
        private const int WM_USER = 0x0400;
        private const int WM_TRAYICON = WM_USER + 101;
        private const int WM_LBUTTONUP = 0x0202;
        private const int WM_LBUTTONDBLCLK = 0x0203;
        private const int WM_RBUTTONUP = 0x0205;

        private const int NIM_ADD = 0x00000000;
        private const int NIM_MODIFY = 0x00000001;
        private const int NIM_DELETE = 0x00000002;

        private const int NIF_MESSAGE = 0x00000001;
        private const int NIF_ICON = 0x00000002;
        private const int NIF_TIP = 0x00000004;

        private const uint IDI_APPLICATION = 32512;

        // Context Menu Commands
        private const uint MF_STRING = 0x00000000;
        private const uint MF_SEPARATOR = 0x00000800;
        private const uint TPM_RIGHTBUTTON = 0x0002;
        private const uint TPM_RETURNCMD = 0x0100;

        private const int CMD_OPEN = 1001;
        private const int CMD_TOGGLE_SERVER = 1002;
        private const int CMD_EXIT = 1003;

        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Auto)]
        private struct NOTIFYICONDATA
        {
            public int cbSize;
            public IntPtr hWnd;
            public int uID;
            public int uFlags;
            public int uCallbackMessage;
            public IntPtr hIcon;
            [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)]
            public string szTip;
            public int dwState;
            public int dwStateMask;
            [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 256)]
            public string szInfo;
            public int uTimeoutOrVersion;
            [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 64)]
            public string szInfoTitle;
            public int dwInfoFlags;
            public Guid guidItem;
            public IntPtr hBalloonIcon;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct POINT
        {
            public int x;
            public int y;
        }

        [DllImport("shell32.dll", CharSet = CharSet.Auto)]
        private static extern bool Shell_NotifyIcon(int dwMessage, ref NOTIFYICONDATA lpData);

        [DllImport("user32.dll")]
        private static extern IntPtr LoadIcon(IntPtr hInstance, IntPtr lpIconName);

        [DllImport("user32.dll")]
        private static extern IntPtr CreatePopupMenu();

        [DllImport("user32.dll", CharSet = CharSet.Auto)]
        private static extern bool AppendMenu(IntPtr hMenu, uint uFlags, uint uIDNewItem, string lpNewItem);

        [DllImport("user32.dll")]
        private static extern bool DestroyMenu(IntPtr hMenu);

        [DllImport("user32.dll")]
        private static extern bool GetCursorPos(out POINT lpPoint);

        [DllImport("user32.dll")]
        private static extern bool SetForegroundWindow(IntPtr hWnd);

        [DllImport("user32.dll")]
        private static extern int TrackPopupMenu(IntPtr hMenu, uint uFlags, int x, int y, int nReserved, IntPtr hWnd, IntPtr prcRect);

        [DllImport("user32.dll", CharSet = CharSet.Auto)]
        private static extern IntPtr LoadImage(IntPtr hinst, string lpszName, uint uType, int cxDesired, int cyDesired, uint fuLoad);

        private const uint IMAGE_ICON = 1;
        private const uint LR_LOADFROMFILE = 0x00000010;
        private const uint LR_DEFAULTSIZE = 0x00000040;

        private readonly Window _window;
        private IntPtr _hWnd;
        private HwndSource? _hwndSource;
        private bool _isCreated;
        private IntPtr _hIcon;

        public event Action? OnOpenRequested;
        public event Action? OnToggleServerRequested;
        public event Action? OnExitRequested;

        public TrayIconManager(Window window)
        {
            _window = window;
        }

        public void Initialize()
        {
            var wih = new WindowInteropHelper(_window);
            _hWnd = wih.EnsureHandle();

            _hwndSource = HwndSource.FromHwnd(_hWnd);
            _hwndSource?.AddHook(WndProc);

            try
            {
                string baseDir = AppDomain.CurrentDomain.BaseDirectory;
                string icoPath = System.IO.Path.Combine(baseDir, "Assets", "app.ico");
                if (System.IO.File.Exists(icoPath))
                {
                    _hIcon = LoadImage(IntPtr.Zero, icoPath, IMAGE_ICON, 16, 16, LR_LOADFROMFILE);
                }
            }
            catch
            {
            }

            if (_hIcon == IntPtr.Zero)
            {
                _hIcon = LoadIcon(IntPtr.Zero, new IntPtr(IDI_APPLICATION));
            }

            var nid = new NOTIFYICONDATA
            {
                cbSize = Marshal.SizeOf<NOTIFYICONDATA>(),
                hWnd = _hWnd,
                uID = 1,
                uFlags = NIF_MESSAGE | NIF_ICON | NIF_TIP,
                uCallbackMessage = WM_TRAYICON,
                hIcon = _hIcon,
                szTip = "PC Remote Control Server"
            };

            _isCreated = Shell_NotifyIcon(NIM_ADD, ref nid);
        }

        public void UpdateTooltip(string tooltip)
        {
            if (!_isCreated || _hWnd == IntPtr.Zero) return;

            var nid = new NOTIFYICONDATA
            {
                cbSize = Marshal.SizeOf<NOTIFYICONDATA>(),
                hWnd = _hWnd,
                uID = 1,
                uFlags = NIF_TIP,
                szTip = tooltip.Length > 127 ? tooltip.Substring(0, 127) : tooltip
            };

            Shell_NotifyIcon(NIM_MODIFY, ref nid);
        }

        private IntPtr WndProc(IntPtr hwnd, int msg, IntPtr wParam, IntPtr lParam, ref bool handled)
        {
            if (msg == WM_TRAYICON)
            {
                int mouseEvent = lParam.ToInt32();
                if (mouseEvent == WM_LBUTTONUP || mouseEvent == WM_LBUTTONDBLCLK)
                {
                    OnOpenRequested?.Invoke();
                    handled = true;
                }
                else if (mouseEvent == WM_RBUTTONUP)
                {
                    ShowContextMenu();
                    handled = true;
                }
            }

            return IntPtr.Zero;
        }

        private void ShowContextMenu()
        {
            if (_hWnd == IntPtr.Zero) return;

            IntPtr hMenu = CreatePopupMenu();
            if (hMenu == IntPtr.Zero) return;

            try
            {
                AppendMenu(hMenu, MF_STRING, CMD_OPEN, "Open Server Window");
                AppendMenu(hMenu, MF_STRING, CMD_TOGGLE_SERVER, "Toggle Server (Start/Stop)");
                AppendMenu(hMenu, MF_SEPARATOR, 0, string.Empty);
                AppendMenu(hMenu, MF_STRING, CMD_EXIT, "Exit Server");

                GetCursorPos(out POINT pt);
                SetForegroundWindow(_hWnd);

                int cmd = TrackPopupMenu(hMenu, TPM_RETURNCMD | TPM_RIGHTBUTTON, pt.x, pt.y, 0, _hWnd, IntPtr.Zero);

                switch (cmd)
                {
                    case CMD_OPEN:
                        OnOpenRequested?.Invoke();
                        break;
                    case CMD_TOGGLE_SERVER:
                        OnToggleServerRequested?.Invoke();
                        break;
                    case CMD_EXIT:
                        OnExitRequested?.Invoke();
                        break;
                }
            }
            finally
            {
                DestroyMenu(hMenu);
            }
        }

        public void Dispose()
        {
            if (_isCreated && _hWnd != IntPtr.Zero)
            {
                var nid = new NOTIFYICONDATA
                {
                    cbSize = Marshal.SizeOf<NOTIFYICONDATA>(),
                    hWnd = _hWnd,
                    uID = 1
                };
                Shell_NotifyIcon(NIM_DELETE, ref nid);
                _isCreated = false;
            }

            _hwndSource?.RemoveHook(WndProc);
            _hwndSource = null;
        }
    }
}
