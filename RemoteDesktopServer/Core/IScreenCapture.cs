using System;

namespace RemoteDesktopServer.Core
{
    public struct CapturedFrame
    {
        public byte[] Data;
        public int Width;
        public int Height;
        public long Timestamp;
    }

    public interface IScreenCapture : IDisposable
    {
        int ScreenWidth { get; }
        int ScreenHeight { get; }
        bool Initialize();
        bool CaptureFrame(int quality, double scale, out CapturedFrame frame);
    }
}
