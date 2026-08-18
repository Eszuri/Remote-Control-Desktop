using System;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;

namespace RemoteDesktopServer.Network
{
    public class UdpDiscoveryServer : IDisposable
    {
        private UdpClient? _udpClient;
        private CancellationTokenSource? _cts;
        private readonly int _discoveryPort;
        private readonly int _serverPort;
        private readonly Func<string> _pinProvider;

        public event Action<string>? OnLog;

        public UdpDiscoveryServer(int discoveryPort, int serverPort, Func<string> pinProvider)
        {
            _discoveryPort = discoveryPort;
            _serverPort = serverPort;
            _pinProvider = pinProvider;
        }

        public void Start()
        {
            try
            {
                _cts = new CancellationTokenSource();
                _udpClient = new UdpClient();
                _udpClient.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
                _udpClient.Client.Bind(new IPEndPoint(IPAddress.Any, _discoveryPort));

                Task.Run(() => ListenLoop(_cts.Token));
                OnLog?.Invoke($"[UDP Discovery] Listening on port {_discoveryPort}");
            }
            catch (Exception ex)
            {
                OnLog?.Invoke($"[UDP Discovery Error] {ex.Message}");
            }
        }

        private async Task ListenLoop(CancellationToken ct)
        {
            while (!ct.IsCancellationRequested && _udpClient != null)
            {
                try
                {
                    var result = await _udpClient.ReceiveAsync(ct);
                    string message = Encoding.UTF8.GetString(result.Buffer);

                    if (message.Contains("DISCOVER_REMOTE_SERVER"))
                    {
                        var responseObj = new
                        {
                            type = "REMOTE_SERVER_INFO",
                            serverName = Environment.MachineName,
                            port = _serverPort,
                            hasPin = !string.IsNullOrEmpty(_pinProvider())
                        };

                        byte[] responseBytes = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(responseObj));
                        await _udpClient.SendAsync(responseBytes, responseBytes.Length, result.RemoteEndPoint);
                        OnLog?.Invoke($"[UDP Discovery] Responded to discovery request from {result.RemoteEndPoint.Address}");
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
                        OnLog?.Invoke($"[UDP Discovery Exception] {ex.Message}");
                        await Task.Delay(1000, ct);
                    }
                }
            }
        }

        public void Stop()
        {
            _cts?.Cancel();
            _udpClient?.Close();
            _udpClient?.Dispose();
            _udpClient = null;
        }

        public void Dispose()
        {
            Stop();
        }
    }
}
