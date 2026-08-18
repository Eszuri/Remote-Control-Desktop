using System;
using System.IO;
using System.Text.Json;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using QRCoder;
using RemoteDesktopServer.Network;

namespace RemoteDesktopServer
{
    public partial class MainWindow : Window
    {
        private RemoteWebSocketServer? _server;
        private UdpDiscoveryServer? _discoveryServer;
        private bool _isRunning = false;

        public MainWindow()
        {
            InitializeComponent();
            Loaded += MainWindow_Loaded;
            Closing += MainWindow_Closing;
        }

        private void MainWindow_Loaded(object sender, RoutedEventArgs e)
        {
            string localIp = RemoteWebSocketServer.GetLocalIPAddress();
            TxtIpAddress.Text = localIp;
            UpdateQrCode();

            // Auto start server on launch
            StartServer();
        }

        private void MainWindow_Closing(object? sender, System.ComponentModel.CancelEventArgs e)
        {
            StopServer();
        }

        private void UpdateQrCode()
        {
            try
            {
                string ip = TxtIpAddress.Text.Trim();
                string port = TxtPort.Text.Trim();
                string pin = TxtPin.Text.Trim();

                var qrPayloadObj = new
                {
                    server = Environment.MachineName,
                    ip = ip,
                    port = int.TryParse(port, out int p) ? p : 9090,
                    pin = pin,
                    wsUrl = $"ws://{ip}:{port}"
                };

                string payload = JsonSerializer.Serialize(qrPayloadObj);

                using var qrGenerator = new QRCodeGenerator();
                using var qrCodeData = qrGenerator.CreateQrCode(payload, QRCodeGenerator.ECCLevel.M);
                using var qrCode = new PngByteQRCode(qrCodeData);
                byte[] qrBytes = qrCode.GetGraphic(10);

                using var ms = new MemoryStream(qrBytes);
                var bitmap = new BitmapImage();
                bitmap.BeginInit();
                bitmap.CacheOption = BitmapCacheOption.OnLoad;
                bitmap.StreamSource = ms;
                bitmap.EndInit();
                bitmap.Freeze();

                ImgQrCode.Source = bitmap;
            }
            catch (Exception ex)
            {
                AppendLog($"[QR Code Error] {ex.Message}");
            }
        }

        private void BtnToggleServer_Click(object sender, RoutedEventArgs e)
        {
            if (_isRunning)
            {
                StopServer();
            }
            else
            {
                StartServer();
            }
        }

        private void StartServer()
        {
            try
            {
                int port = int.TryParse(TxtPort.Text.Trim(), out int p) ? p : 9090;
                string pin = TxtPin.Text.Trim();

                _server = new RemoteWebSocketServer(port, pin)
                {
                    DefaultFps = (int)SliderFps.Value,
                    DefaultQuality = (int)SliderQuality.Value
                };

                _server.OnLog += msg => Dispatcher.Invoke(() => AppendLog(msg));
                _server.OnClientCountChanged += count => Dispatcher.Invoke(() =>
                {
                    TxtClientCount.Text = $"{count} Device{(count == 1 ? "" : "s")}";
                });

                _server.Start();

                // Start UDP Auto Discovery
                _discoveryServer = new UdpDiscoveryServer(9091, port, () => TxtPin.Text.Trim());
                _discoveryServer.OnLog += msg => Dispatcher.Invoke(() => AppendLog(msg));
                _discoveryServer.Start();

                _isRunning = true;
                BtnToggleServer.Content = "Stop Server";
                BtnToggleServer.Background = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#EF4444"));
                StatusBadge.Background = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#10B981"));
                TxtStatus.Text = "RUNNING";
                TxtFooterStatus.Text = $"Server active on ws://{TxtIpAddress.Text}:{port}";

                UpdateQrCode();
                AppendLog($"[Server] Started on ws://{TxtIpAddress.Text}:{port}");
            }
            catch (Exception ex)
            {
                AppendLog($"[Server Error] {ex.Message}");
            }
        }

        private void StopServer()
        {
            try
            {
                _server?.Stop();
                _server = null;

                _discoveryServer?.Stop();
                _discoveryServer = null;

                _isRunning = false;
                BtnToggleServer.Content = "Start Server";
                BtnToggleServer.Background = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#3B82F6"));
                StatusBadge.Background = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#EF4444"));
                TxtStatus.Text = "STOPPED";
                TxtFooterStatus.Text = "Server stopped";
                TxtClientCount.Text = "0 Devices";

                AppendLog("[Server] Stopped");
            }
            catch (Exception ex)
            {
                AppendLog($"[Stop Error] {ex.Message}");
            }
        }

        private void AppendLog(string message)
        {
            string time = DateTime.Now.ToString("HH:mm:ss");
            TxtLogs.AppendText($"[{time}] {message}\n");
            TxtLogs.ScrollToEnd();
        }

        private void BtnClearLogs_Click(object sender, RoutedEventArgs e)
        {
            TxtLogs.Clear();
        }

        private void SliderFps_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
        {
            if (TxtFpsVal != null)
            {
                TxtFpsVal.Text = $"{(int)SliderFps.Value} FPS";
                if (_server != null)
                {
                    _server.DefaultFps = (int)SliderFps.Value;
                }
            }
        }

        private void SliderQuality_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
        {
            if (TxtQualityVal != null)
            {
                TxtQualityVal.Text = $"{(int)SliderQuality.Value}%";
                if (_server != null)
                {
                    _server.DefaultQuality = (int)SliderQuality.Value;
                }
            }
        }
    }
}