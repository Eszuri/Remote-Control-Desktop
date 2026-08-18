using System;
using System.IO;
using System.Text.Json;

namespace RemoteDesktopServer.Core
{
    public class AppSettings
    {
        public int Port { get; set; } = 9090;
        public int Fps { get; set; } = 30;
        public int Quality { get; set; } = 70;

        private static string SettingsFilePath =>
            Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "RemoteDesktopServer",
                "settings.json"
            );

        public static AppSettings Load()
        {
            try
            {
                string path = SettingsFilePath;
                if (File.Exists(path))
                {
                    string json = File.ReadAllText(path);
                    var settings = JsonSerializer.Deserialize<AppSettings>(json);
                    if (settings != null)
                    {
                        settings.Port = Math.Clamp(settings.Port, 1, 65535);
                        settings.Fps = Math.Clamp(settings.Fps, 10, 60);
                        settings.Quality = Math.Clamp(settings.Quality, 20, 95);
                        return settings;
                    }
                }
            }
            catch
            {
            }
            return new AppSettings();
        }

        public void Save()
        {
            try
            {
                string path = SettingsFilePath;
                string? dir = Path.GetDirectoryName(path);
                if (!string.IsNullOrEmpty(dir) && !Directory.Exists(dir))
                {
                    Directory.CreateDirectory(dir);
                }

                string json = JsonSerializer.Serialize(this, new JsonSerializerOptions { WriteIndented = true });
                File.WriteAllText(path, json);
            }
            catch
            {
            }
        }
    }
}
