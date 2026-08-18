using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Runtime.InteropServices;
using Vortice.Direct3D;
using Vortice.Direct3D11;
using Vortice.DXGI;
using Drawing2D = System.Drawing.Drawing2D;

namespace RemoteDesktopServer.Core
{
    public class DxgiScreenCapture : IScreenCapture
    {
        private ID3D11Device? _device;
        private ID3D11DeviceContext? _context;
        private IDXGIOutputDuplication? _deskDupl;
        private ID3D11Texture2D? _stagingTexture;
        private int _width;
        private int _height;
        private bool _isInitialized = false;

        private static ImageCodecInfo? _jpegEncoder;
        private static readonly EncoderParameters _encoderParams = new EncoderParameters(1);

        static DxgiScreenCapture()
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

        public int ScreenWidth => _width;
        public int ScreenHeight => _height;

        public bool Initialize()
        {
            try
            {
                Dispose();

                var driverTypes = new[]
                {
                    DriverType.Hardware,
                    DriverType.Warp
                };

                FeatureLevel[] featureLevels = new[]
                {
                    FeatureLevel.Level_11_1,
                    FeatureLevel.Level_11_0,
                    FeatureLevel.Level_10_1,
                    FeatureLevel.Level_10_0
                };

                var creationFlags = DeviceCreationFlags.BgraSupport;

                foreach (var driverType in driverTypes)
                {
                    if (D3D11.D3D11CreateDevice(IntPtr.Zero, driverType, creationFlags, featureLevels, out _device, out _context).Success)
                    {
                        break;
                    }
                }

                if (_device == null || _context == null)
                    return false;

                using var dxgiDevice = _device.QueryInterface<IDXGIDevice>();
                using var adapter = dxgiDevice.GetAdapter();
                
                if (adapter.EnumOutputs(0, out var output).Failure)
                {
                    return false;
                }

                using var output1 = output.QueryInterface<IDXGIOutput1>();

                var desc = output.Description;
                _width = desc.DesktopCoordinates.Right - desc.DesktopCoordinates.Left;
                _height = desc.DesktopCoordinates.Bottom - desc.DesktopCoordinates.Top;

                _deskDupl = output1.DuplicateOutput(_device);

                var textureDesc = new Texture2DDescription
                {
                    CPUAccessFlags = CpuAccessFlags.Read,
                    BindFlags = BindFlags.None,
                    Format = Format.B8G8R8A8_UNorm,
                    Width = (uint)_width,
                    Height = (uint)_height,
                    MiscFlags = ResourceOptionFlags.None,
                    MipLevels = 1,
                    ArraySize = 1,
                    SampleDescription = { Count = 1, Quality = 0 },
                    Usage = ResourceUsage.Staging
                };

                _stagingTexture = _device.CreateTexture2D(textureDesc);
                _isInitialized = true;
                return true;
            }
            catch
            {
                _isInitialized = false;
                return false;
            }
        }

        public bool CaptureFrame(int quality, double scale, out CapturedFrame frame)
        {
            frame = default;
            if (!_isInitialized || _deskDupl == null || _device == null || _context == null || _stagingTexture == null)
            {
                if (!Initialize())
                    return false;
            }

            try
            {
                var result = _deskDupl!.AcquireNextFrame(100, out var frameInfo, out var desktopResource);
                if (result.Failure)
                {
                    if (result.Code == Vortice.DXGI.ResultCode.AccessLost.Code || result.Code == Vortice.DXGI.ResultCode.AccessDenied.Code)
                    {
                        Initialize();
                    }
                    return false;
                }

                using (desktopResource)
                {
                    using var desktopTexture = desktopResource.QueryInterface<ID3D11Texture2D>();
                    _context!.CopyResource(_stagingTexture!, desktopTexture);
                }

                _deskDupl.ReleaseFrame();

                if (_stagingTexture == null) return false;
                var map = _context.Map(_stagingTexture, 0, MapMode.Read, Vortice.Direct3D11.MapFlags.None);
                try
                {
                    scale = Math.Clamp(scale, 0.25, 1.0);
                    int targetWidth = (int)(_width * scale);
                    int targetHeight = (int)(_height * scale);

                    using var bmp = new Bitmap(_width, _height, (int)map.RowPitch, PixelFormat.Format32bppArgb, map.DataPointer);
                    using (var gCursor = Graphics.FromImage(bmp))
                    {
                        NativeMethods.DrawSystemCursor(gCursor);
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
                            gScaled.InterpolationMode = Drawing2D.InterpolationMode.Bilinear;
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
                finally
                {
                    _context.Unmap(_stagingTexture, 0);
                }
            }
            catch
            {
                return false;
            }
        }

        public void Dispose()
        {
            _stagingTexture?.Dispose();
            _stagingTexture = null;
            _deskDupl?.Dispose();
            _deskDupl = null;
            _context?.Dispose();
            _context = null;
            _device?.Dispose();
            _device = null;
            _isInitialized = false;
        }
    }
}
