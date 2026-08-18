using System;
using System.IO;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Threading;
using QRCoder;
using RemoteDesktopServer.Core;
using RemoteDesktopServer.Network;

namespace RemoteDesktopServer
{
    public partial class MainWindow : Window
    {
        private const int MaxLogLines = 500;
        private const double CompactLayoutWidth = 850;

        private RemoteWebSocketServer? _server;
        private UdpDiscoveryServer? _discoveryServer;
        private DispatcherTimer? _copyFeedbackTimer;
        private ServerUiState _uiState = ServerUiState.Stopped;
        private int _activePort = 9090;
        private bool _isRunning;
        private bool _isClosing;
        private bool _isCompactLayout;

        private enum ServerUiState
        {
            Stopped,
            Starting,
            Running,
            Error
        }

        public MainWindow()
        {
            InitializeComponent();
            Loaded += MainWindow_Loaded;
            Closing += MainWindow_Closing;
            ApplyServerState(ServerUiState.Stopped);
        }

        private void MainWindow_Loaded(object sender, RoutedEventArgs e)
        {
            var settings = AppSettings.Load();
            TxtPort.Text = settings.Port.ToString();
            SliderFps.Value = settings.Fps;
            SliderQuality.Value = settings.Quality;
            if (TxtFpsVal != null) TxtFpsVal.Text = $"{settings.Fps} FPS";
            if (TxtQualityVal != null) TxtQualityVal.Text = $"{settings.Quality}%";

            string localIp = RemoteWebSocketServer.GetLocalIPAddress();
            TxtIpAddress.Text = localIp;
            UpdateQrCode();
            ApplyCompactLayout();
            StartServer();
        }

        private void MainWindow_Closing(object? sender, System.ComponentModel.CancelEventArgs e)
        {
            _isClosing = true;
            SaveCurrentSettings();
            StopServer();
        }

        private void ApplyServerState(ServerUiState state, string? message = null, int? port = null)
        {
            _uiState = state;

            switch (state)
            {
                case ServerUiState.Starting:
                    BtnToggleServer.Style = (Style)FindResource("PrimaryButtonStyle");
                    BtnToggleServer.Content = "Starting...";
                    BtnToggleServer.IsEnabled = false;
                    StatusBadge.Background = GetBrush("BrushSurfaceRaised");
                    StatusBadge.BorderBrush = GetBrush("BrushWarning");
                    StatusIndicatorDot.Fill = GetBrush("BrushWarning");
                    TxtStatus.Text = "STARTING";
                    TxtStatus.Foreground = GetBrush("BrushWarning");
                    TxtFooterStatus.Text = "Starting server services...";
                    TxtPort.IsEnabled = false;
                    break;

                case ServerUiState.Running:
                    BtnToggleServer.Style = (Style)FindResource("DangerButtonStyle");
                    BtnToggleServer.Content = "Stop Server";
                    BtnToggleServer.IsEnabled = true;
                    StatusBadge.Background = GetBrush("BrushSurfaceRaised");
                    StatusBadge.BorderBrush = GetBrush("BrushSuccess");
                    StatusIndicatorDot.Fill = GetBrush("BrushSuccess");
                    TxtStatus.Text = "RUNNING";
                    TxtStatus.Foreground = GetBrush("BrushSuccess");
                    TxtFooterStatus.Text = $"Server active on ws://{TxtIpAddress.Text}:{port ?? _activePort}";
                    TxtPort.IsEnabled = false;
                    break;

                case ServerUiState.Error:
                    BtnToggleServer.Style = (Style)FindResource("PrimaryButtonStyle");
                    BtnToggleServer.Content = "Start Server";
                    BtnToggleServer.IsEnabled = true;
                    StatusBadge.Background = GetBrush("BrushSurfaceRaised");
                    StatusBadge.BorderBrush = GetBrush("BrushError");
                    StatusIndicatorDot.Fill = GetBrush("BrushError");
                    TxtStatus.Text = "ERROR";
                    TxtStatus.Foreground = GetBrush("BrushError");
                    TxtFooterStatus.Text = message ?? "Server could not be started";
                    TxtPort.IsEnabled = true;
                    break;

                default:
                    BtnToggleServer.Style = (Style)FindResource("PrimaryButtonStyle");
                    BtnToggleServer.Content = "Start Server";
                    BtnToggleServer.IsEnabled = true;
                    StatusBadge.Background = GetBrush("BrushSurfaceRaised");
                    StatusBadge.BorderBrush = GetBrush("BrushError");
                    StatusIndicatorDot.Fill = GetBrush("BrushError");
                    TxtStatus.Text = "STOPPED";
                    TxtStatus.Foreground = GetBrush("BrushTextSecondary");
                    TxtFooterStatus.Text = message ?? "Server stopped";
                    TxtClientCount.Text = "0 Devices";
                    TxtPort.IsEnabled = true;
                    break;
            }
        }

        private Brush GetBrush(string key)
        {
            return (Brush)FindResource(key);
        }

        private void ApplyCompactLayout()
        {
            if (MainContentGrid == null || ActualWidth <= 0)
            {
                return;
            }

            bool shouldUseCompactLayout = ActualWidth < CompactLayoutWidth;
            if (_isCompactLayout == shouldUseCompactLayout && MainContentGrid.RowDefinitions.Count > 0)
            {
                return;
            }

            _isCompactLayout = shouldUseCompactLayout;

            if (_isCompactLayout)
            {
                MainContentGrid.ColumnDefinitions[0].Width = new GridLength(1, GridUnitType.Star);
                MainContentGrid.ColumnDefinitions[1].Width = new GridLength(0);
                MainContentGrid.RowDefinitions[0].Height = new GridLength(1.1, GridUnitType.Star);
                MainContentGrid.RowDefinitions[1].Height = new GridLength(1, GridUnitType.Star);
                Grid.SetRow(ConnectionPanel, 0);
                Grid.SetColumn(ConnectionPanel, 0);
                Grid.SetRow(ActivityPanel, 1);
                Grid.SetColumn(ActivityPanel, 0);
                ConnectionPanel.Margin = new Thickness(0, 0, 0, 12);
                ActivityPanel.Margin = new Thickness(0);
            }
            else
            {
                MainContentGrid.ColumnDefinitions[0].Width = new GridLength(2, GridUnitType.Star);
                MainContentGrid.ColumnDefinitions[1].Width = new GridLength(3, GridUnitType.Star);
                MainContentGrid.RowDefinitions[0].Height = new GridLength(1, GridUnitType.Star);
                MainContentGrid.RowDefinitions[1].Height = new GridLength(0);
                Grid.SetRow(ConnectionPanel, 0);
                Grid.SetColumn(ConnectionPanel, 0);
                Grid.SetRow(ActivityPanel, 0);
                Grid.SetColumn(ActivityPanel, 1);
                ConnectionPanel.Margin = new Thickness(0, 0, 9, 0);
                ActivityPanel.Margin = new Thickness(9, 0, 0, 0);
            }
        }

        private void MainWindow_SizeChanged(object sender, SizeChangedEventArgs e)
        {
            ApplyCompactLayout();
        }

        private bool TryGetValidPort(out int port)
        {
            port = 0;
            string rawPort = TxtPort.Text.Trim();

            if (!int.TryParse(rawPort, out int parsedPort))
            {
                SetPortValidation("Enter a whole number for the port.", true);
                return false;
            }

            if (parsedPort < 1 || parsedPort > 65535)
            {
                SetPortValidation("Port must be between 1 and 65535.", true);
                return false;
            }

            if (_isRunning && parsedPort != _activePort)
            {
                SetPortValidation("Stop the server before changing its port.", true);
                return false;
            }

            port = parsedPort;
            SetPortValidation(string.Empty, false);
            return true;
        }

        private void SetPortValidation(string message, bool hasError)
        {
            TxtPortValidation.Text = message;
            TxtPortValidation.Visibility = hasError ? Visibility.Visible : Visibility.Collapsed;
            TxtPort.BorderBrush = hasError ? GetBrush("BrushError") : GetBrush("BrushBorderStrong");
        }

        private void UpdateQrCode()
        {
            if (!TryGetValidPort(out int port))
            {
                ImgQrCode.Source = null;
                return;
            }

            try
            {
                string ip = TxtIpAddress.Text.Trim();
                var qrPayloadObj = new
                {
                    server = Environment.MachineName,
                    ip,
                    port,
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
                if (ImgQrCodeEnlarged != null)
                {
                    ImgQrCodeEnlarged.Source = bitmap;
                }
                if (TxtModalIpAddress != null)
                {
                    TxtModalIpAddress.Text = $"{ip}:{port}";
                }
            }
            catch (Exception ex)
            {
                ImgQrCode.Source = null;
                if (ImgQrCodeEnlarged != null)
                {
                    ImgQrCodeEnlarged.Source = null;
                }
                AppendLog($"[QR Code Error] {ex.Message}");
            }
        }

        private void BtnToggleServer_Click(object sender, RoutedEventArgs e)
        {
            if (_uiState == ServerUiState.Starting)
            {
                return;
            }

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
            if (_isRunning || _uiState == ServerUiState.Starting)
            {
                return;
            }

            if (!TryGetValidPort(out int port))
            {
                return;
            }

            ApplyServerState(ServerUiState.Starting);

            try
            {
                _server = new RemoteWebSocketServer(port)
                {
                    DefaultFps = (int)SliderFps.Value,
                    DefaultQuality = (int)SliderQuality.Value
                };

                _server.OnLog += msg => DispatchUi(() => AppendLog(msg));
                _server.OnClientCountChanged += count => DispatchUi(() =>
                {
                    TxtClientCount.Text = $"{count} Device{(count == 1 ? "" : "s")}";
                });
                _server.OnMetricsUpdated += (fps, latencyMs, encodeMs) => DispatchUi(() =>
                {
                    TxtFooterStatus.Text = $"Server active on ws://{TxtIpAddress.Text}:{port} • {fps} FPS • Latency: {latencyMs} ms";
                });

                _server.Start();

                _discoveryServer = new UdpDiscoveryServer(9091, port);
                _discoveryServer.OnLog += msg => DispatchUi(() => AppendLog(msg));
                _discoveryServer.Start();

                _activePort = port;
                _isRunning = true;
                ApplyServerState(ServerUiState.Running, port: port);
                TxtCaptureEngine.Text = _server.CaptureMethod;
                UpdateQrCode();
                AppendLog($"[Server] Started on ws://{TxtIpAddress.Text}:{port}");
            }
            catch (Exception ex)
            {
                try
                {
                    _server?.Stop();
                    _discoveryServer?.Stop();
                }
                catch
                {
                }

                _server = null;
                _discoveryServer = null;
                _isRunning = false;
                ApplyServerState(ServerUiState.Error, ex.Message);
                AppendLog($"[Server Error] {ex.Message}");
            }
        }

        private void StopServer()
        {
            try
            {
                _server?.Stop();
                _discoveryServer?.Stop();
                _server = null;
                _discoveryServer = null;
                _isRunning = false;
                ApplyServerState(ServerUiState.Stopped);

                if (!_isClosing)
                {
                    AppendLog("[Server] Stopped");
                }
            }
            catch (Exception ex)
            {
                _server = null;
                _discoveryServer = null;
                _isRunning = false;
                ApplyServerState(ServerUiState.Error, ex.Message);
                AppendLog($"[Stop Error] {ex.Message}");
            }
        }

        private void AppendLog(string message)
        {
            if (_isClosing || TxtLogs == null)
            {
                return;
            }

            string time = DateTime.Now.ToString("HH:mm:ss");
            TxtLogs.AppendText($"[{time}] {message}\n");

            string[] lines = TxtLogs.Text.Split('\n', StringSplitOptions.RemoveEmptyEntries);
            if (lines.Length > MaxLogLines)
            {
                TxtLogs.Text = string.Join('\n', lines[^MaxLogLines..]) + '\n';
            }

            TxtLogs.ScrollToEnd();
        }

        private void BtnClearLogs_Click(object sender, RoutedEventArgs e)
        {
            TxtLogs.Clear();
        }

        private void BtnCopyIp_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                Clipboard.SetText(TxtIpAddress.Text);
                BtnCopyIp.Content = "Copied";
                TxtCopyFeedback.Text = "Copied to clipboard";
                StartCopyFeedbackTimer();
                AppendLog($"[Clipboard] Copied IP address: {TxtIpAddress.Text}");
            }
            catch (Exception ex)
            {
                TxtCopyFeedback.Text = "Copy failed";
                TxtCopyFeedback.Foreground = GetBrush("BrushError");
                AppendLog($"[Clipboard Error] {ex.Message}");
            }
        }

        private void StartCopyFeedbackTimer()
        {
            if (_copyFeedbackTimer == null)
            {
                _copyFeedbackTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(1.5) };
                _copyFeedbackTimer.Tick += (_, _) =>
                {
                    _copyFeedbackTimer.Stop();
                    BtnCopyIp.Content = "Copy";
                    TxtCopyFeedback.Text = string.Empty;
                    TxtCopyFeedback.Foreground = GetBrush("BrushSuccess");
                };
            }

            _copyFeedbackTimer.Stop();
            _copyFeedbackTimer.Start();
        }

        private void TxtPort_LostFocus(object sender, RoutedEventArgs e)
        {
            if (TryGetValidPort(out _))
            {
                UpdateQrCode();
                SaveCurrentSettings();
            }
        }

        private void TxtPort_TextChanged(object sender, TextChangedEventArgs e)
        {
            if (!IsLoaded)
            {
                return;
            }

            if (TryGetValidPort(out _))
            {
                UpdateQrCode();
            }
        }

        private void SliderFps_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
        {
            if (TxtFpsVal == null)
            {
                return;
            }

            TxtFpsVal.Text = $"{(int)SliderFps.Value} FPS";
            if (_server != null)
            {
                _server.DefaultFps = (int)SliderFps.Value;
            }
            if (IsLoaded)
            {
                SaveCurrentSettings();
            }
        }

        private void SliderQuality_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
        {
            if (TxtQualityVal == null)
            {
                return;
            }

            TxtQualityVal.Text = $"{(int)SliderQuality.Value}%";
            if (_server != null)
            {
                _server.DefaultQuality = (int)SliderQuality.Value;
            }
            if (IsLoaded)
            {
                SaveCurrentSettings();
            }
        }

        private void SaveCurrentSettings()
        {
            try
            {
                var settings = new AppSettings
                {
                    Port = TryGetValidPort(out int p) ? p : 9090,
                    Fps = (int)SliderFps.Value,
                    Quality = (int)SliderQuality.Value
                };
                settings.Save();
            }
            catch
            {
            }
        }

        private void DispatchUi(Action action)
        {
            if (_isClosing || Dispatcher.HasShutdownStarted || Dispatcher.HasShutdownFinished)
            {
                return;
            }

            try
            {
                if (Dispatcher.CheckAccess())
                {
                    action();
                }
                else
                {
                    Dispatcher.BeginInvoke(action);
                }
            }
            catch (InvalidOperationException)
            {
            }
        }

        private void BorderQrThumbnail_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
        {
            OpenQrModal();
        }

        private void BtnCloseQrModal_Click(object sender, RoutedEventArgs e)
        {
            CloseQrModal();
        }

        private void QrModalOverlay_MouseDown(object sender, MouseButtonEventArgs e)
        {
            CloseQrModal();
        }

        private void QrModalCard_MouseDown(object sender, MouseButtonEventArgs e)
        {
            e.Handled = true;
        }

        private void Window_KeyDown(object sender, KeyEventArgs e)
        {
            if (e.Key == Key.Escape && QrModalOverlay != null && QrModalOverlay.Visibility == Visibility.Visible)
            {
                CloseQrModal();
                e.Handled = true;
            }
        }

        private void OpenQrModal()
        {
            if (QrModalOverlay != null)
            {
                QrModalOverlay.Visibility = Visibility.Visible;
            }
        }

        private void CloseQrModal()
        {
            if (QrModalOverlay != null)
            {
                QrModalOverlay.Visibility = Visibility.Collapsed;
            }
        }
    }
}
