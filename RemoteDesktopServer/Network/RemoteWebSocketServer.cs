using System;
using System.Collections.Concurrent;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using Fleck;
using RemoteDesktopServer.Core;

namespace RemoteDesktopServer.Network
{
    public class ClientSession
    {
        public IWebSocketConnection Connection { get; set; } = null!;
        public bool IsAuthenticated { get; set; } = true;
        public int TargetQuality { get; set; } = 70;
        public double ScaleFactor { get; set; } = 1.0;
        public int TargetFps { get; set; } = 60;
        public long LastReportedLatency { get; set; } = 1;
    }

    public class RemoteWebSocketServer : IDisposable
    {
        private WebSocketServer? _server;
        private readonly ConcurrentDictionary<Guid, ClientSession> _clients = new();
        private ScreenCaptureManager? _captureManager;
        private CancellationTokenSource? _streamingCts;
        private Task? _streamingTask;
        private Task? _metricsTask;

        private int _framesDeliveredCounter = 0;
        private int _lastMeasuredFps = 0;
        private double _lastEncodeDurationMs = 0;

        public int Port { get; private set; }
        public int DefaultFps { get; set; } = 60;
        public int DefaultQuality { get; set; } = 70;
        public double DefaultScale { get; set; } = 1.0;

        public event Action<string>? OnLog;
        public event Action<int>? OnClientCountChanged;
        public event Action<int, long, double>? OnMetricsUpdated; // fps, latencyMs, encodeMs

        public int ConnectedClientCount => _clients.Count;
        public string CaptureMethod => _captureManager?.ActiveCaptureMethod ?? "DirectX DXGI (GPU)";

        public RemoteWebSocketServer(int port)
        {
            Port = port;
        }

        public void Start()
        {
            try
            {
                _captureManager = new ScreenCaptureManager();
                OnLog?.Invoke($"[Screen Capture] Initialized using {_captureManager.ActiveCaptureMethod} ({_captureManager.ScreenWidth}x{_captureManager.ScreenHeight})");

                FleckLog.LogAction = (level, message, ex) => { };

                _server = new WebSocketServer($"ws://0.0.0.0:{Port}");
                _server.Start(socket =>
                {
                    socket.OnOpen = () =>
                    {
                        var session = new ClientSession
                        {
                            Connection = socket,
                            IsAuthenticated = true,
                            TargetFps = DefaultFps,
                            TargetQuality = DefaultQuality,
                            ScaleFactor = DefaultScale
                        };

                        _clients[socket.ConnectionInfo.Id] = session;
                        OnLog?.Invoke($"[Client Connected] {socket.ConnectionInfo.ClientIpAddress}:{socket.ConnectionInfo.ClientPort}");
                        OnClientCountChanged?.Invoke(_clients.Count);

                        SendAuthResult(socket, true, "Connected successfully");
                    };

                    socket.OnClose = () =>
                    {
                        _clients.TryRemove(socket.ConnectionInfo.Id, out _);
                        OnLog?.Invoke($"[Client Disconnected] {socket.ConnectionInfo.ClientIpAddress}:{socket.ConnectionInfo.ClientPort}");
                        OnClientCountChanged?.Invoke(_clients.Count);
                    };

                    socket.OnMessage = message =>
                    {
                        if (_clients.TryGetValue(socket.ConnectionInfo.Id, out var session))
                        {
                            HandleClientMessage(session, message);
                        }
                    };

                    socket.OnError = ex =>
                    {
                        OnLog?.Invoke($"[Client Error] {socket.ConnectionInfo.ClientIpAddress}: {ex.Message}");
                    };
                });

                OnLog?.Invoke($"[WebSocket Server] Running at ws://0.0.0.0:{Port}/");

                _streamingCts = new CancellationTokenSource();
                _streamingTask = Task.Run(() => StreamScreenLoop(_streamingCts.Token));
                _metricsTask = Task.Run(() => MetricLoggerLoop(_streamingCts.Token));
            }
            catch (Exception ex)
            {
                OnLog?.Invoke($"[Server Start Error] {ex.Message}");
            }
        }

        private void HandleClientMessage(ClientSession session, string json)
        {
            try
            {
                var msg = JsonSerializer.Deserialize<ClientMessage>(json);
                if (msg == null) return;

                switch (msg.Type?.ToLowerInvariant())
                {
                    case "auth":
                        session.IsAuthenticated = true;
                        SendAuthResult(session.Connection, true, "Connected successfully");
                        break;

                    case "ping":
                        long clientSentTime = msg.Timestamp ?? DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
                        if (msg.ClientLatency.HasValue && msg.ClientLatency.Value > 0)
                        {
                            session.LastReportedLatency = msg.ClientLatency.Value;
                        }

                        var pong = new ServerResponse
                        {
                            Type = "pong",
                            Timestamp = clientSentTime,
                            Latency = session.LastReportedLatency,
                            Fps = _lastMeasuredFps,
                            Success = true
                        };
                        session.Connection.Send(JsonSerializer.Serialize(pong));
                        break;

                    default:
                        ProcessControlCommand(session, msg);
                        break;
                }
            }
            catch (Exception ex)
            {
                OnLog?.Invoke($"[Message Handler Error] {ex.Message}");
            }
        }

        private void ProcessControlCommand(ClientSession session, ClientMessage msg)
        {
            switch (msg.Type?.ToLowerInvariant())
            {
                case "mouse_move":
                    if (msg.X.HasValue && msg.Y.HasValue)
                    {
                        InputSimulator.MoveMouseAbsolute(msg.X.Value, msg.Y.Value);
                    }
                    break;

                case "mouse_move_delta":
                    if (msg.Dx.HasValue && msg.Dy.HasValue)
                    {
                        InputSimulator.MoveMouseDelta(msg.Dx.Value, msg.Dy.Value);
                    }
                    break;

                case "mouse_click":
                    InputSimulator.MouseButton(msg.Button ?? "left", msg.Action ?? "click");
                    break;

                case "mouse_scroll":
                    InputSimulator.MouseScroll(msg.Dy ?? 0, msg.Dx ?? 0);
                    break;

                case "key_event":
                    if (msg.Code.HasValue)
                    {
                        InputSimulator.KeyEvent(msg.Code.Value, msg.Action ?? "press");
                    }
                    break;

                case "text_input":
                    if (!string.IsNullOrEmpty(msg.Text))
                    {
                        InputSimulator.SendUnicodeText(msg.Text);
                    }
                    break;

                case "shortcut":
                    if (!string.IsNullOrEmpty(msg.Name))
                    {
                        InputSimulator.ExecuteShortcut(msg.Name);
                    }
                    break;

                case "quality_change":
                    if (msg.Quality.HasValue) session.TargetQuality = Math.Clamp(msg.Quality.Value, 10, 100);
                    if (msg.Scale.HasValue) session.ScaleFactor = Math.Clamp(msg.Scale.Value, 0.25, 1.0);
                    if (msg.Fps.HasValue) session.TargetFps = Math.Clamp(msg.Fps.Value, 5, 60);
                    break;
            }
        }

        private void SendAuthResult(IWebSocketConnection socket, bool success, string message)
        {
            var res = new ServerResponse
            {
                Type = "auth_result",
                Success = success,
                Message = message,
                ServerName = Environment.MachineName,
                ScreenWidth = _captureManager?.ScreenWidth ?? 1920,
                ScreenHeight = _captureManager?.ScreenHeight ?? 1080,
                Timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
            };
            socket.Send(JsonSerializer.Serialize(res));
        }

        private async Task StreamScreenLoop(CancellationToken ct)
        {
            uint frameIndex = 0;
            var stopwatch = new Stopwatch();
            var encodeStopwatch = new Stopwatch();

            while (!ct.IsCancellationRequested)
            {
                try
                {
                    stopwatch.Restart();

                    int activeClients = 0;
                    foreach (var s in _clients.Values)
                    {
                        if (s.Connection.IsAvailable)
                            activeClients++;
                    }

                    if (activeClients > 0 && _captureManager != null)
                    {
                        int quality = DefaultQuality;
                        double scale = DefaultScale;

                        encodeStopwatch.Restart();
                        bool captured = _captureManager.CaptureFrame(quality, scale, out var frame);
                        encodeStopwatch.Stop();
                        _lastEncodeDurationMs = encodeStopwatch.Elapsed.TotalMilliseconds;

                        if (captured)
                        {
                            frameIndex++;
                            Interlocked.Increment(ref _framesDeliveredCounter);

                            // Packet Structure:
                            // [0] = 0x53 ('S')
                            // [1..4] = Frame Index (UInt32)
                            // [5..6] = Width (UInt16)
                            // [7..8] = Height (UInt16)
                            // [9..end] = JPEG Data
                            byte[] packet = new byte[9 + frame.Data.Length];
                            packet[0] = 0x53; // 'S' for Screen Frame

                            packet[1] = (byte)(frameIndex >> 24);
                            packet[2] = (byte)(frameIndex >> 16);
                            packet[3] = (byte)(frameIndex >> 8);
                            packet[4] = (byte)(frameIndex);

                            packet[5] = (byte)(frame.Width >> 8);
                            packet[6] = (byte)(frame.Width);
                            packet[7] = (byte)(frame.Height >> 8);
                            packet[8] = (byte)(frame.Height);

                            Buffer.BlockCopy(frame.Data, 0, packet, 9, frame.Data.Length);

                            foreach (var pair in _clients)
                            {
                                var client = pair.Value;
                                if (client.Connection.IsAvailable)
                                {
                                    try
                                    {
                                        _ = client.Connection.Send(packet);
                                    }
                                    catch
                                    {
                                    }
                                }
                            }
                        }
                    }

                    long elapsedMs = stopwatch.ElapsedMilliseconds;
                    int targetFps = Math.Clamp(DefaultFps, 10, 60);
                    int targetIntervalMs = 1000 / targetFps;

                    int remainingSleep = targetIntervalMs - (int)elapsedMs;
                    if (remainingSleep > 1)
                    {
                        await Task.Delay(remainingSleep, ct);
                    }
                    else
                    {
                        await Task.Yield();
                    }
                }
                catch (OperationCanceledException)
                {
                    break;
                }
                catch (Exception ex)
                {
                    if (!ct.IsCancellationRequested)
                    {
                        OnLog?.Invoke($"[Streaming Loop Error] {ex.Message}");
                        await Task.Delay(200, ct);
                    }
                }
            }
        }

        private async Task MetricLoggerLoop(CancellationToken ct)
        {
            while (!ct.IsCancellationRequested)
            {
                try
                {
                    await Task.Delay(1000, ct);

                    if (_clients.Count > 0)
                    {
                        int currentFps = Interlocked.Exchange(ref _framesDeliveredCounter, 0);
                        _lastMeasuredFps = currentFps;
                        double avgEncodeMs = _lastEncodeDurationMs;
                        long avgLatency = GetAverageClientLatency();

                        OnLog?.Invoke($"[Latency Monitor] Latency: {avgLatency} ms | FPS: {currentFps} | Encode: {avgEncodeMs:F1} ms | Clients: {_clients.Count}");
                        OnMetricsUpdated?.Invoke(currentFps, avgLatency, avgEncodeMs);
                    }
                }
                catch (OperationCanceledException)
                {
                    break;
                }
                catch
                {
                }
            }
        }

        private long GetAverageClientLatency()
        {
            long total = 0;
            int count = 0;
            foreach (var client in _clients.Values)
            {
                if (client.Connection.IsAvailable)
                {
                    total += Math.Max(1, client.LastReportedLatency);
                    count++;
                }
            }
            return count > 0 ? (total / count) : 1;
        }

        public void Stop()
        {
            _streamingCts?.Cancel();

            foreach (var client in _clients.Values)
            {
                try
                {
                    if (client.Connection.IsAvailable)
                    {
                        var stopResponse = new ServerResponse
                        {
                            Type = "server_stopped",
                            Success = false,
                            Message = "Server remote telah dimatikan dari sisi PC host.",
                            Timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
                        };
                        client.Connection.Send(JsonSerializer.Serialize(stopResponse));
                        client.Connection.Close();
                    }
                }
                catch
                {
                }
            }

            _server?.Dispose();
            _server = null;
            _captureManager?.Dispose();
            _captureManager = null;
            _clients.Clear();
            OnClientCountChanged?.Invoke(0);
        }

        public void Dispose()
        {
            Stop();
        }

        public static string GetLocalIPAddress()
        {
            try
            {
                using var socket = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, 0);
                socket.Connect("8.8.8.8", 65530);
                if (socket.LocalEndPoint is IPEndPoint endPoint)
                {
                    return endPoint.Address.ToString();
                }
            }
            catch
            {
            }

            var host = Dns.GetHostEntry(Dns.GetHostName());
            foreach (var ip in host.AddressList)
            {
                if (ip.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(ip))
                {
                    return ip.ToString();
                }
            }
            return "127.0.0.1";
        }
    }
}
