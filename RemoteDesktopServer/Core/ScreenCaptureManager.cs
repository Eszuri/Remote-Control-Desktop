using System;

namespace RemoteDesktopServer.Core
{
    public class ScreenCaptureManager : IDisposable
    {
        private IScreenCapture _capture;
        private bool _usingDxgi = true;
        private int _consecutiveFailures = 0;
        private int _gdiFrameCounter = 0;

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
            // If currently on GDI fallback, try to recover DXGI GPU capture after every 150 frames (~3-5 seconds)
            if (!_usingDxgi)
            {
                _gdiFrameCounter++;
                if (_gdiFrameCounter > 150)
                {
                    _gdiFrameCounter = 0;
                    var dxgi = new DxgiScreenCapture();
                    if (dxgi.Initialize() && dxgi.CaptureFrame(quality, scale, out frame))
                    {
                        _capture.Dispose();
                        _capture = dxgi;
                        _usingDxgi = true;
                        _consecutiveFailures = 0;
                        return true;
                    }
                    else
                    {
                        dxgi.Dispose();
                    }
                }
            }

            bool success = _capture.CaptureFrame(quality, scale, out frame);
            if (!success)
            {
                _consecutiveFailures++;
                // Only switch to GDI fallback if DXGI hard-crashes repeatedly (more than 50 consecutive hard failures)
                if (_consecutiveFailures > 50 && _usingDxgi)
                {
                    _capture.Dispose();
                    _capture = new GdiScreenCapture();
                    _capture.Initialize();
                    _usingDxgi = false;
                    _consecutiveFailures = 0;
                    _gdiFrameCounter = 0;
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
