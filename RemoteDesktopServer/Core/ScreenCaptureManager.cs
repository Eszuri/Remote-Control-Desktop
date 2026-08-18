using System;

namespace RemoteDesktopServer.Core
{
    public class ScreenCaptureManager : IDisposable
    {
        private IScreenCapture _capture;
        private bool _usingDxgi = true;
        private int _consecutiveFailures = 0;

        public int ScreenWidth => _capture?.ScreenWidth ?? 1920;
        public int ScreenHeight => _capture?.ScreenHeight ?? 1080;
        public string ActiveCaptureMethod => _usingDxgi ? "DirectX DXGI (GPU)" : "Win32 GDI";

        public ScreenCaptureManager()
        {
            _capture = new DxgiScreenCapture();
            if (!_capture.Initialize())
            {
                _capture.Dispose();
                _capture = new GdiScreenCapture();
                _capture.Initialize();
                _usingDxgi = false;
            }
        }

        public bool CaptureFrame(int quality, double scale, out CapturedFrame frame)
        {
            bool success = _capture.CaptureFrame(quality, scale, out frame);
            if (!success)
            {
                _consecutiveFailures++;
                // Only switch if DXGI has persistently failed more than 30 consecutive times
                if (_consecutiveFailures > 30 && _usingDxgi)
                {
                    _capture.Dispose();
                    _capture = new GdiScreenCapture();
                    _capture.Initialize();
                    _usingDxgi = false;
                    _consecutiveFailures = 0;
                    success = _capture.CaptureFrame(quality, scale, out frame);
                }
            }
            else
            {
                _consecutiveFailures = 0;
            }
            return success;
        }

        public void Dispose()
        {
            _capture?.Dispose();
        }
    }
}
