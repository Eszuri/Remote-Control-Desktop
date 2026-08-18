using System;
using System.Collections.Concurrent;
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
        public bool IsAuthenticated { get; set; }
        public int TargetQuality { get; set; } = 70;
        public double ScaleFactor { get; set; } = 1.0;
        public int TargetFps { get; set; } = 30;
    }

    public class RemoteWebSocketServer : IDisposable
    {
        private WebSocketServer? _server;
        private readonly ConcurrentDictionary<Guid, ClientSession> _clients = new();
        private ScreenCaptureManager? _captureManager;
        private CancellationTokenSource? _streamingCts;
        private Task? _streamingTask;

        public int Port { get; private set; }
        public string PinCode { get; set; } = "";
        public int DefaultFps { get; set; } = 30;
        public int DefaultQuality { get; set; } = 70;
        public double DefaultScale { get; set; } = 1.0;

        public event Action<string>? OnLog;
        public event Action<int>? OnClientCountChanged;

        public int ConnectedClientCount => _clients.Count;

        public RemoteWebSocketServer(int port, string pinCode)
        {
            Port = port;
            PinCode = pinCode;
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
                            IsAuthenticated = string.IsNullOrEmpty(PinCode),
                            TargetFps = DefaultFps,
                            TargetQuality = DefaultQuality,
                            ScaleFactor = DefaultScale
                        };

                        _clients[socket.ConnectionInfo.Id] = session;
                        OnLog?.Invoke($"[Client Connected] {socket.ConnectionInfo.ClientIpAddress}:{socket.ConnectionInfo.ClientPort}");
                        OnClientCountChanged?.Invoke(_clients.Count);

                        // If no PIN required, send auth success immediately
                        if (session.IsAuthenticated)
                        {
                            SendAuthResult(socket, true, "Authenticated automatically (No PIN required)");
                        }
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
                        if (string.IsNullOrEmpty(PinCode) || msg.Pin == PinCode)
                        {
                            session.IsAuthenticated = true;
                            SendAuthResult(session.Connection, true, "Authentication successful");
                            OnLog?.Invoke($"[Client Auth] {session.Connection.ConnectionInfo.ClientIpAddress} authorized.");
                        }
                        else
                        {
                            session.IsAuthenticated = false;
                            SendAuthResult(session.Connection, false, "Invalid PIN code");
                            OnLog?.Invoke($"[Client Auth] {session.Connection.ConnectionInfo.ClientIpAddress} sent invalid PIN.");
                        }
                        break;

                    case "ping":
                        var pong = new ServerResponse
                        {
                            Type = "pong",
                            Timestamp = msg.Timestamp ?? DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
                            Success = true
                        };
                        session.Connection.Send(JsonSerializer.Serialize(pong));
                        break;

                    default:
                        // For control commands, client must be authenticated
                        if (!session.IsAuthenticated) return;
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

            while (!ct.IsCancellationRequested)
            {
                try
                {
                    int authenticatedClients = 0;
                    foreach (var s in _clients.Values)
                    {
                        if (s.IsAuthenticated && s.Connection.IsAvailable)
                            authenticatedClients++;
                    }

                    if (authenticatedClients > 0 && _captureManager != null)
                    {
                        int quality = DefaultQuality;
                        double scale = DefaultScale;

                        if (_captureManager.CaptureFrame(quality, scale, out var frame))
                        {
                            frameIndex++;

                            // Packet Structure:
                            // [0] = 0x53 ('S')
                            // [1..4] = Frame Index (UInt32)
                            // [5..6] = Width (UInt16)
                            // [7..8] = Height (UInt16)
                            // [9..end] = JPEG Data
                            byte[] packet = new byte[9 + frame.Data.Length];
                            packet[0] = 0x53; // 'S' for Screen Frame

                            // Big-Endian or Little-Endian packing
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
                                if (client.IsAuthenticated && client.Connection.IsAvailable)
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

                    int delayMs = 1000 / Math.Max(1, DefaultFps);
                    await Task.Delay(delayMs, ct);
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
                        await Task.Delay(500, ct);
                    }
                }
            }
        }

        public void Stop()
        {
            _streamingCts?.Cancel();
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
