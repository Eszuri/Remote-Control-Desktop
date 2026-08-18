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
        public string ActiveCaptureMethod => _usingDxgi ? "DirectX DXGI" : "Win32 GDI";

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
                if (_consecutiveFailures > 10 && _usingDxgi)
                {
                    // Switch to GDI fallback
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
