using System;
using System.Runtime.InteropServices;

namespace RemoteDesktopServer.Core
{
    public class InputSimulator
    {
        public static void MoveMouseAbsolute(double normX, double normY)
        {
            // Normalize to 0 - 65535
            normX = Math.Clamp(normX, 0.0, 1.0);
            normY = Math.Clamp(normY, 0.0, 1.0);

            int absX = (int)(normX * 65535);
            int absY = (int)(normY * 65535);

            var input = new NativeMethods.INPUT
            {
                type = NativeMethods.INPUT_MOUSE,
                u = new NativeMethods.InputUnion
                {
                    mi = new NativeMethods.MOUSEINPUT
                    {
                        dx = absX,
                        dy = absY,
                        dwFlags = NativeMethods.MOUSEEVENTF_MOVE | NativeMethods.MOUSEEVENTF_ABSOLUTE | NativeMethods.MOUSEEVENTF_VIRTUALDESK,
                        time = 0,
                        dwExtraInfo = IntPtr.Zero
                    }
                }
            };

            NativeMethods.SendInput(1, new[] { input }, Marshal.SizeOf(typeof(NativeMethods.INPUT)));
        }

        public static void MoveMouseDelta(int dx, int dy)
        {
            var input = new NativeMethods.INPUT
            {
                type = NativeMethods.INPUT_MOUSE,
                u = new NativeMethods.InputUnion
                {
                    mi = new NativeMethods.MOUSEINPUT
                    {
                        dx = dx,
                        dy = dy,
                        dwFlags = NativeMethods.MOUSEEVENTF_MOVE,
                        time = 0,
                        dwExtraInfo = IntPtr.Zero
                    }
                }
            };

            NativeMethods.SendInput(1, new[] { input }, Marshal.SizeOf(typeof(NativeMethods.INPUT)));
        }

        public static void MouseButton(string button, string action)
        {
            uint downFlag = 0;
            uint upFlag = 0;

            switch (button?.ToLowerInvariant())
            {
                case "right":
                    downFlag = NativeMethods.MOUSEEVENTF_RIGHTDOWN;
                    upFlag = NativeMethods.MOUSEEVENTF_RIGHTUP;
                    break;
                case "middle":
                    downFlag = NativeMethods.MOUSEEVENTF_MIDDLEDOWN;
                    upFlag = NativeMethods.MOUSEEVENTF_MIDDLEUP;
                    break;
                case "left":
                default:
                    downFlag = NativeMethods.MOUSEEVENTF_LEFTDOWN;
                    upFlag = NativeMethods.MOUSEEVENTF_LEFTUP;
                    break;
            }

            if (action == "down")
            {
                SendMouseFlag(downFlag);
            }
            else if (action == "up")
            {
                SendMouseFlag(upFlag);
            }
            else if (action == "dblclick")
            {
                SendMouseFlag(downFlag);
                SendMouseFlag(upFlag);
                System.Threading.Thread.Sleep(50);
                SendMouseFlag(downFlag);
                SendMouseFlag(upFlag);
            }
            else // "click"
            {
                SendMouseFlag(downFlag);
                SendMouseFlag(upFlag);
            }
        }

        public static void MouseScroll(int deltaY, int deltaX = 0)
        {
            if (deltaY != 0)
            {
                var input = new NativeMethods.INPUT
                {
                    type = NativeMethods.INPUT_MOUSE,
                    u = new NativeMethods.InputUnion
                    {
                        mi = new NativeMethods.MOUSEINPUT
                        {
                            mouseData = (uint)deltaY,
                            dwFlags = NativeMethods.MOUSEEVENTF_WHEEL,
                            time = 0,
                            dwExtraInfo = IntPtr.Zero
                        }
                    }
                };
                NativeMethods.SendInput(1, new[] { input }, Marshal.SizeOf(typeof(NativeMethods.INPUT)));
            }

            if (deltaX != 0)
            {
                var input = new NativeMethods.INPUT
                {
                    type = NativeMethods.INPUT_MOUSE,
                    u = new NativeMethods.InputUnion
                    {
                        mi = new NativeMethods.MOUSEINPUT
                        {
                            mouseData = (uint)deltaX,
                            dwFlags = NativeMethods.MOUSEEVENTF_HWHEEL,
                            time = 0,
                            dwExtraInfo = IntPtr.Zero
                        }
                    }
                };
                NativeMethods.SendInput(1, new[] { input }, Marshal.SizeOf(typeof(NativeMethods.INPUT)));
            }
        }

        private static void SendMouseFlag(uint flag)
        {
            var input = new NativeMethods.INPUT
            {
                type = NativeMethods.INPUT_MOUSE,
                u = new NativeMethods.InputUnion
                {
                    mi = new NativeMethods.MOUSEINPUT
                    {
                        dwFlags = flag,
                        time = 0,
                        dwExtraInfo = IntPtr.Zero
                    }
                }
            };
            NativeMethods.SendInput(1, new[] { input }, Marshal.SizeOf(typeof(NativeMethods.INPUT)));
        }

        public static void KeyEvent(ushort vkCode, string action)
        {
            uint flags = 0;
            if (action == "up")
            {
                flags |= NativeMethods.KEYEVENTF_KEYUP;
            }

            var input = new NativeMethods.INPUT
            {
                type = NativeMethods.INPUT_KEYBOARD,
                u = new NativeMethods.InputUnion
                {
                    ki = new NativeMethods.KEYBDINPUT
                    {
                        wVk = vkCode,
                        wScan = 0,
                        dwFlags = flags,
                        time = 0,
                        dwExtraInfo = IntPtr.Zero
                    }
                }
            };

            if (action == "press")
            {
                var inputDown = input;
                inputDown.u.ki.dwFlags = 0;

                var inputUp = input;
                inputUp.u.ki.dwFlags = NativeMethods.KEYEVENTF_KEYUP;

                NativeMethods.SendInput(2, new[] { inputDown, inputUp }, Marshal.SizeOf(typeof(NativeMethods.INPUT)));
            }
            else
            {
                NativeMethods.SendInput(1, new[] { input }, Marshal.SizeOf(typeof(NativeMethods.INPUT)));
            }
        }

        public static void SendUnicodeText(string text)
        {
            if (string.IsNullOrEmpty(text)) return;

            var inputs = new NativeMethods.INPUT[text.Length * 2];
            for (int i = 0; i < text.Length; i++)
            {
                char c = text[i];
                // Down
                inputs[i * 2] = new NativeMethods.INPUT
                {
                    type = NativeMethods.INPUT_KEYBOARD,
                    u = new NativeMethods.InputUnion
                    {
                        ki = new NativeMethods.KEYBDINPUT
                        {
                            wVk = 0,
                            wScan = (ushort)c,
                            dwFlags = NativeMethods.KEYEVENTF_UNICODE,
                            time = 0,
                            dwExtraInfo = IntPtr.Zero
                        }
                    }
                };

                // Up
                inputs[i * 2 + 1] = new NativeMethods.INPUT
                {
                    type = NativeMethods.INPUT_KEYBOARD,
                    u = new NativeMethods.InputUnion
                    {
                        ki = new NativeMethods.KEYBDINPUT
                        {
                            wVk = 0,
                            wScan = (ushort)c,
                            dwFlags = NativeMethods.KEYEVENTF_UNICODE | NativeMethods.KEYEVENTF_KEYUP,
                            time = 0,
                            dwExtraInfo = IntPtr.Zero
                        }
                    }
                };
            }

            NativeMethods.SendInput((uint)inputs.Length, inputs, Marshal.SizeOf(typeof(NativeMethods.INPUT)));
        }

        public static void ExecuteShortcut(string name)
        {
            switch (name?.ToLowerInvariant())
            {
                case "win_d":
                    // Win + D
                    KeyEvent(0x5B, "down"); // VK_LWIN
                    KeyEvent(0x44, "press"); // 'D'
                    KeyEvent(0x5B, "up");
                    break;
                case "alt_tab":
                    KeyEvent(0x12, "down"); // VK_MENU (Alt)
                    KeyEvent(0x09, "press"); // VK_TAB
                    KeyEvent(0x12, "up");
                    break;
                case "ctrl_c":
                    KeyEvent(0x11, "down"); // VK_CONTROL
                    KeyEvent(0x43, "press"); // 'C'
                    KeyEvent(0x11, "up");
                    break;
                case "ctrl_v":
                    KeyEvent(0x11, "down"); // VK_CONTROL
                    KeyEvent(0x56, "press"); // 'V'
                    KeyEvent(0x11, "up");
                    break;
                case "ctrl_z":
                    KeyEvent(0x11, "down"); // VK_CONTROL
                    KeyEvent(0x5A, "press"); // 'Z'
                    KeyEvent(0x11, "up");
                    break;
                case "ctrl_a":
                    KeyEvent(0x11, "down"); // VK_CONTROL
                    KeyEvent(0x41, "press"); // 'A'
                    KeyEvent(0x11, "up");
                    break;
                case "enter":
                    KeyEvent(0x0D, "press"); // VK_RETURN
                    break;
                case "backspace":
                    KeyEvent(0x08, "press"); // VK_BACK
                    break;
                case "escape":
                case "esc":
                    KeyEvent(0x1B, "press"); // VK_ESCAPE
                    break;
                case "tab":
                    KeyEvent(0x09, "press"); // VK_TAB
                    break;
                case "space":
                    KeyEvent(0x20, "press"); // VK_SPACE
                    break;
                case "arrow_up":
                    KeyEvent(0x26, "press");
                    break;
                case "arrow_down":
                    KeyEvent(0x28, "press");
                    break;
                case "arrow_left":
                    KeyEvent(0x25, "press");
                    break;
                case "arrow_right":
                    KeyEvent(0x27, "press");
                    break;
            }
        }
    }
}
