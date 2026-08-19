using System;
using System.Diagnostics;
using Microsoft.Win32;

namespace RemoteDesktopServer.Core
{
    public static class StartupManager
    {
        private const string AppName = "RemoteControlDesktopServer";
        private const string RunRegistryKey = @"Software\Microsoft\Windows\CurrentVersion\Run";

        public static bool IsStartupEnabled()
        {
            try
            {
                using var key = Registry.CurrentUser.OpenSubKey(RunRegistryKey, false);
                var val = key?.GetValue(AppName) as string;
                return !string.IsNullOrWhiteSpace(val);
            }
            catch
            {
                return false;
            }
        }

        public static bool SetStartup(bool enable)
        {
            try
            {
                using var key = Registry.CurrentUser.OpenSubKey(RunRegistryKey, true);
                if (key == null) return false;

                if (enable)
                {
                    string? exePath = Environment.ProcessPath;
                    if (string.IsNullOrEmpty(exePath))
                    {
                        exePath = Process.GetCurrentProcess().MainModule?.FileName;
                    }

                    if (!string.IsNullOrEmpty(exePath))
                    {
                        key.SetValue(AppName, $"\"{exePath}\"");
                        return true;
                    }
                    return false;
                }
                else
                {
                    key.DeleteValue(AppName, false);
                    return true;
                }
            }
            catch
            {
                return false;
            }
        }
    }
}
