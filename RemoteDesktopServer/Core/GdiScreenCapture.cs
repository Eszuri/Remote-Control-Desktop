using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.IO;

namespace RemoteDesktopServer.Core
{
    public class GdiScreenCapture : IScreenCapture
    {
        private static ImageCodecInfo? _jpegEncoder;
        private static readonly EncoderParameters _encoderParams = new EncoderParameters(1);

        static GdiScreenCapture()
        {
            foreach (var codec in ImageCodecInfo.GetImageEncoders())
            {
                if (codec.FormatID == ImageFormat.Jpeg.Guid)
                {
                    _jpegEncoder = codec;
                    break;
                }
            }
        }

        public int ScreenWidth { get; private set; }
        public int ScreenHeight { get; private set; }

        public GdiScreenCapture()
        {
            ScreenWidth = NativeMethods.GetSystemMetrics(NativeMethods.SM_CXSCREEN);
            ScreenHeight = NativeMethods.GetSystemMetrics(NativeMethods.SM_CYSCREEN);
            if (ScreenWidth <= 0) ScreenWidth = 1920;
            if (ScreenHeight <= 0) ScreenHeight = 1080;
        }

        public bool Initialize()
        {
            ScreenWidth = NativeMethods.GetSystemMetrics(NativeMethods.SM_CXSCREEN);
            ScreenHeight = NativeMethods.GetSystemMetrics(NativeMethods.SM_CYSCREEN);
            return true;
        }

        public bool CaptureFrame(int quality, double scale, out CapturedFrame frame)
        {
            frame = default;
            try
            {
                int srcWidth = NativeMethods.GetSystemMetrics(NativeMethods.SM_CXSCREEN);
                int srcHeight = NativeMethods.GetSystemMetrics(NativeMethods.SM_CYSCREEN);
                if (srcWidth <= 0 || srcHeight <= 0)
                {
                    srcWidth = ScreenWidth;
                    srcHeight = ScreenHeight;
                }

                ScreenWidth = srcWidth;
                ScreenHeight = srcHeight;

                scale = Math.Clamp(scale, 0.25, 1.0);
                int targetWidth = (int)(srcWidth * scale);
                int targetHeight = (int)(srcHeight * scale);

                using var bmp = new Bitmap(srcWidth, srcHeight, PixelFormat.Format32bppArgb);
                using (var g = Graphics.FromImage(bmp))
                {
                    g.CopyFromScreen(0, 0, 0, 0, new Size(srcWidth, srcHeight), CopyPixelOperation.SourceCopy);
                    NativeMethods.DrawSystemCursor(g);
                }

                using var ms = new MemoryStream();
                _encoderParams.Param[0] = new EncoderParameter(Encoder.Quality, (long)Math.Clamp(quality, 10, 100));

                if (Math.Abs(scale - 1.0) < 0.01)
                {
                    if (_jpegEncoder != null)
                    {
                        bmp.Save(ms, _jpegEncoder, _encoderParams);
                    }
                    else
                    {
                        bmp.Save(ms, ImageFormat.Jpeg);
                    }
                }
                else
                {
                    using var scaledBmp = new Bitmap(targetWidth, targetHeight, PixelFormat.Format32bppRgb);
                    using (var gScaled = Graphics.FromImage(scaledBmp))
                    {
                        gScaled.InterpolationMode = InterpolationMode.Bilinear;
                        gScaled.DrawImage(bmp, 0, 0, targetWidth, targetHeight);
                    }

                    if (_jpegEncoder != null)
                    {
                        scaledBmp.Save(ms, _jpegEncoder, _encoderParams);
                    }
                    else
                    {
                        scaledBmp.Save(ms, ImageFormat.Jpeg);
                    }
                }

                frame = new CapturedFrame
                {
                    Data = ms.ToArray(),
                    Width = targetWidth,
                    Height = targetHeight,
                    Timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
                };

                return true;
            }
            catch
            {
                return false;
            }
        }

        public void Dispose()
        {
        }
    }
}
