# PC Remote Desktop Server (Windows C# .NET 10 WPF v1.2.0)

[![Version](https://img.shields.io/badge/Version-1.2.0-blue.svg)](.)
[![Framework](https://img.shields.io/badge/.NET-10.0%20Windows-512BD4.svg)](.)
[![Graphics](https://img.shields.io/badge/Graphics-DirectX%2011%20DXGI-green.svg)](.)

Aplikasi server native Windows berperforma tinggi (*ultra-low latency*) untuk mengizinkan kendali remote PC penuh dari smartphone Android secara nirkabel melalui jaringan lokal (Wi-Fi) ataupun jarak jauh (WAN/VPN) tanpa memerlukan PIN atau konfigurasi rumit.

---

## 🚀 Fitur Unggulan Server v1.2.0

### 1. Dual Screen Capture Engine (DirectX DXGI GPU + GDI Fallback)
- **DirectX DXGI Desktop Duplication API (Utama):**
  - Menggunakan library `Vortice.Direct3D11` dan `Vortice.DXGI`.
  - Mengambil frame langsung dari VRAM GPU (*Hardware Accelerated*) dengan latensi sub-frame (< 10ms).
  - Menggambar hardware system cursor secara otomatis ke dalam frame (`NativeMethods.DrawSystemCursor`).
- **GDI BitBlt (Fallback Otomatis):**
  - Berpindah secara otomatis jika DXGI context terlepas (misalnya saat game *exclusive fullscreen*, resolusi berubah, atau dialog UAC).
  - Melakukan auto-recovery kembali ke DirectX setiap 150 frame.
- **Zero-Client Idle Sleep Mode:**
  - Saat 0 klien aktif, loop capture otomatis *sleep* (0% penggunaan GPU/CPU).
  - Seketika aktif kembali saat klien terhubung.

### 2. Auto-Startup & Background System Tray
- **Windows Auto-Startup Registry:** Opsi switch on/off di UI untuk mendaftarkan server ke startup Windows (`HKCU\Software\Microsoft\Windows\CurrentVersion\Run`) dengan flag `--minimized`.
- **System Tray Native (Baki Sistem):**
  - Berjalan di latar belakang tanpa memunculkan jendela saat komputer pertama kali dinyalakan.
  - **Klik Kiri pada Tray Icon:** Membuka dan menampilkan kembali jendela server ke layar depan.
  - **Klik Kanan pada Tray Icon:** Menampilkan menu konteks (*Open Server Window*, *Toggle Server*, *Exit Server*).
  - **Tooltip Informasi:** Menampilkan status koneksi & alamat IP secara real-time.
- **Minimize to Tray on Close (X):** Menekan tombol silang (X) otomatis menyembunyikan jendela ke system tray (*ShowInTaskbar = false*) tanpa memutuskan koneksi remote.

### 3. Native Input Simulation (P/Invoke Win32 `SendInput`)
- Emulasi trackpad presisi tinggi menggunakan koordinat Delta (`dx`, `dy`).
- Pengetikan teks kalimat, simbol spesial, dan emoji menggunakan `KEYEVENTF_UNICODE` langsung ke aplikasi PC yang sedang aktif.
- Pintasan Windows bawaan: `Win+D`, `Alt+Tab`, `Ctrl+C`, `Ctrl+V`, `Ctrl+Z`, `Ctrl+A`, dll.

### 4. Pairing Instan Tanpa PIN
- **QRCoder Dynamic Generator:** Menghasilkan QR Code dinamis berisi IP & Port lokal.
- **Centered Modal Zoom:** Klik pada thumbnail QR untuk memperbesar tampilan QR Code di tengah layar dengan efek backdrop gelap.

---

## 📦 Struktur File Server

```
RemoteDesktopServer/
├── App.xaml                    # Definisi styling & resource aplikasi WPF
├── App.xaml.cs                 # Code-behind aplikasi WPF
├── MainWindow.xaml             # Dashboard UI (Modern Dark Theme, Sliders, QR, Logs)
├── MainWindow.xaml.cs          # Logika UI, integrasi WebSocket, Discovery, & Tray
├── RemoteDesktopServer.csproj  # Project file (.NET 10, Dependencies, Icon Assets)
├── Assets/
│   ├── app.ico                 # Multi-Resolution Icon (16, 32, 48, 64, 128, 256)
│   └── app.png                 # High-Resolution App Icon (512x512)
├── Core/
│   ├── AppSettings.cs          # Pengelola persistensi konfigurasi lokal
│   ├── StartupManager.cs       # Pengelola registri startup Windows
│   ├── TrayIconManager.cs      # Native Win32 Shell_NotifyIcon & Context Menu
│   ├── IScreenCapture.cs       # Kontrak interface capture layar
│   ├── DxgiScreenCapture.cs    # Engine DirectX 11 Desktop Duplication (Vortice)
│   ├── GdiScreenCapture.cs     # Fallback engine Win32 GDI BitBlt
│   ├── ScreenCaptureManager.cs # Manajer capture & auto-switching engine
│   ├── InputSimulator.cs       # P/Invoke Win32 SendInput (Mouse, Keyboard, Unicode)
│   └── NativeMethods.cs        # Definisi struktur Win32 native & API
└── Network/
    ├── Protocol.cs             # Model DTO pesan Client/Server (JSON serialization)
    ├── RemoteWebSocketServer.cs# WebSocket Server, session manager, & streaming loop
    └── UdpDiscoveryServer.cs   # UDP broadcast listener & responder auto-discovery (9091)
```

---

## 🚀 Cara Menjalankan & Membangun (Build)

### 1. Menjalankan Langsung via .NET CLI
```powershell
cd "RemoteDesktopServer"
dotnet run
```

### 2. Build Release Executable (.exe Mandiri / Self-Contained)
```powershell
dotnet publish -c Release -r win-x64 --self-contained true /p:PublishSingleFile=true
```
File executable mandiri akan tersedia di:
`bin\Release\net10.0-windows\win-x64\publish\RemoteDesktopServer.exe`
