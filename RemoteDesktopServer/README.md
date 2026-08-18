# PC Remote Desktop Server (Windows C# .NET 10 WPF)

Aplikasi server native Windows berperforma tinggi (*ultra-low latency*) untuk mengizinkan kendali remote PC penuh dari smartphone Android secara nirkabel melalui jaringan lokal (Wi-Fi) ataupun jarak jauh (WAN/VPN).

---

## 🚀 Arsitektur & Fitur Utama

```
+-------------------------------------------------------------------+
|                     RemoteDesktopServer (WPF)                     |
|                                                                   |
|  [ DirectX DXGI Capture / GDI Fallback ] -> [ JPEG Encoder ]      |
|                                                     |             |
|                                             (Binary Stream)       |
|                                                     v             |
|  [ Win32 SendInput ] <--- (JSON Commands) --- [ Fleck WebSocket ] |
|                                                     ^             |
|  [ QRCoder Generator ]       [ UDP Discovery Server (9091) ]      |
+-------------------------------------------------------------------+
```

### 1. Dual Screen Capture Engine (Ultra-Low Latency)
- **DirectX DXGI Desktop Duplication API (Primary):**
  - Menggunakan library `Vortice.Direct3D11` dan `Vortice.DXGI`.
  - Mengambil frame langsung dari VRAM GPU (hardware-accelerated) dengan latensi sub-frame.
  - Menggambar hardware system cursor secara otomatis ke dalam frame (`NativeMethods.DrawSystemCursor`).
- **Win32 GDI BitBlt (Automatic Fallback):**
  - Berpindah secara otomatis jika DXGI kehilangan akses context (misalnya saat game *exclusive fullscreen*, resolusi berubah, atau dialog UAC tertentu).
  - Memastikan *stream* tidak pernah freeze/crash.

### 2. Native Input Simulation (P/Invoke Win32 `SendInput`)
- **Absolute & Relative Mouse:**
  - Emulasi trackpad presisi tinggi menggunakan koordinat Delta (`dx`, `dy`).
  - Dukungan koordinat absolut normalisasi `0..65535` untuk mode *Direct Touch*.
- **Mouse Actions & Scroll:**
  - Left Click, Right Click, Middle Click, Double Click, Mouse Down/Up (Drag & Drop).
  - Smooth vertical scroll wheel (`MOUSEEVENTF_WHEEL`) dan horizontal wheel.
- **Full Unicode & Keyboard Typing:**
  - Pengetikan teks kalimat, simbol spesial, dan emoji menggunakan `KEYEVENTF_UNICODE` langsung ke aplikasi yang sedang aktif.
  - Virtual key code events (Down, Up, Press).
- **Windows Shortcuts Built-in:**
  - `Win+D` (Show Desktop), `Alt+Tab` (App Switcher), `Ctrl+C`, `Ctrl+V`, `Ctrl+Z`, `Ctrl+A`, `Esc`, `Enter`, `Backspace`, `Tab`, `Space`, dan Tombol Panah Navigasi.

### 3. Jaringan & Keamanan
- **Fleck WebSocket Server:**
  - Port default: `9090` (`ws://0.0.0.0:9090`).
  - Binary frame stream teroptimasi: Header paket binary (`0x53` 'S' + Frame Index + Width + Height + JPEG Data).
- **UDP LAN Auto-Discovery:**
  - Port discovery: `9091`.
  - Menjawab broadcast UDP dari aplikasi Android untuk koneksi instan satu ketukan tanpa repot mengetik IP address.
- **PIN Authentication & QR Code Pairing:**
  - Menghasilkan QR Code dinamis berisi metadata koneksi (IP, Port, PIN).
  - Verifikasi PIN Code 6 digit sebelum klien diizinkan mengirim perintah input kontrol.

### 4. Modern WPF Dashboard UI
- Tampilan Dark Theme modern.
- Real-time logging terminal dengan stempel waktu (*timestamp*).
- Indikator status koneksi & penghitung klien aktif secara *live*.
- Kontrol slider real-time untuk FPS (10 - 60 FPS) dan JPEG Quality (20% - 95%).

---

## 🛠️ Prasyarat Sistem

| Komponen | Spesifikasi Minimum | Rekomendasi |
| :--- | :--- | :--- |
| **Sistem Operasi** | Windows 10 (Build 1809+) 64-bit | Windows 11 64-bit |
| **.NET SDK / Runtime** | .NET 10.0 SDK atau .NET 8.0 Windows Desktop Runtime | .NET 10.0 SDK |
| **Kartu Grafis (GPU)** | GPU dengan dukungan DirectX 11.0 Feature Level | Intel HD/Iris, NVIDIA GeForce, atau AMD Radeon |
| **Jaringan** | Wi-Fi 2.4 GHz lokal | Wi-Fi 5 GHz / Ethernet LAN (Gigabit) |

---

## 📦 Struktur File Server

```
RemoteDesktopServer/
├── App.xaml                    # Definisi aplikasi WPF
├── App.xaml.cs                 # Code-behind aplikasi WPF
├── AssemblyInfo.cs             # Metadata assembly
├── MainWindow.xaml             # Antarmuka Dashboard (Dark Theme, QR, Sliders, Logs)
├── MainWindow.xaml.cs          # Logika UI, integrasi WebSocket, Discovery, & QR
├── RemoteDesktopServer.csproj  # Project file (.NET 10, Dependencies)
├── .gitignore                  # Filter version control file build/VS
├── Core/
│   ├── IScreenCapture.cs       # Kontrak interface capture layar
│   ├── DxgiScreenCapture.cs    # Engine DirectX 11 Desktop Duplication (Vortice)
│   ├── GdiScreenCapture.cs     # Fallback engine Win32 GDI BitBlt
│   ├── ScreenCaptureManager.cs # Manajer capture & auto-switching engine
│   ├── InputSimulator.cs       # P/Invoke Win32 SendInput (Mouse, Keyboard, Unicode)
│   └── NativeMethods.cs        # Definisi struktur Win32 C++ native & konstanta API
└── Network/
    ├── Protocol.cs             # Model DTO pesan Client/Server (JSON serialization)
    ├── RemoteWebSocketServer.cs# WebSocket Server, session manager, & streaming loop
    └── UdpDiscoveryServer.cs   # UDP broadcast listener & responder auto-discovery
```

---

## 🚀 Cara Menjalankan & Membangun (Build)

### 1. Menjalankan Langsung via .NET CLI
Buka PowerShell atau Command Prompt di folder ini:
```powershell
cd "d:\Codingan\Project-Full-Stack\Remote Control Desktop\RemoteDesktopServer"
dotnet run
```

### 2. Build Release Executable (.exe Mandiri / Self-Contained)
Untuk membuat file executable tunggal yang dapat dijalankan di PC Windows mana pun tanpa perlu menginstal .NET SDK terlebih dahulu:
```powershell
dotnet publish -c Release -r win-x64 --self-contained true /p:PublishSingleFile=true
```
File `.exe` akan tersedia di:
`bin\Release\net10.0-windows\win-x64\publish\RemoteDesktopServer.exe`

---

## ⚙️ Panduan Penggunaan & Konfigurasi

1. **Jalankan Aplikasi:**
   - Luncurkan `RemoteDesktopServer.exe`.
   - Disarankan **Run as Administrator** agar server memiliki izin mengontrol jendela elevated (seperti Task Manager, dialog UAC, dan game fullscreen).
2. **Periksa Informasi Jaringan:**
   - Server secara otomatis mendeteksi alamat IPv4 lokal Anda (misal: `192.168.1.100`).
   - Port default: `9090` (WebSocket) dan `9091` (UDP Discovery).
3. **Atur PIN Keamanan:**
   - Isi kotak **PIN Code** (misal: `123456`) untuk mencegah akses tanpa izin.
   - Kosongkan PIN jika ingin mengizinkan koneksi otomatis tanpa autentikasi.
4. **Hubungkan Smartphone Android:**
   - **Metode 1 (Scan QR):** Buka aplikasi Android, pilih **Scan QR**, lalu arahkan kamera HP ke QR code di layar PC.
   - **Metode 2 (Scan LAN):** Tekan **Scan LAN** pada aplikasi Android untuk mendeteksi server secara otomatis.
   - **Metode 3 (Manual):** Ketikkan IP Address, Port, dan PIN secara manual.
5. **Penyesuaian Kualitas Real-time:**
   - **FPS Slider:** Sesuaikan target frame per second (10 - 60 FPS).
   - **Quality Slider:** Sesuaikan tingkat kompresi JPEG (20% - 95%) sesuai bandwidth jaringan Wi-Fi Anda.

---

## 🛡️ Pengaturan Windows Firewall

Jika smartphone Android gagal menemukan atau terhubung ke server:
1. Buka **Windows Defender Firewall with Advanced Security**.
2. Tambahkan **Inbound Rule** baru:
   - **Port TCP:** `9090` (Izinkan koneksi)
   - **Port UDP:** `9091` (Izinkan koneksi)
3. Atau jalankan perintah PowerShell (Run as Administrator):
```powershell
New-NetFirewallRule -DisplayName "PC Remote Desktop Server (TCP 9090)" -Direction Inbound -LocalPort 9090 -Protocol TCP -Action Allow
New-NetFirewallRule -DisplayName "PC Remote Desktop Server (UDP 9091)" -Direction Inbound -LocalPort 9091 -Protocol UDP -Action Allow
```

---

## 📡 Spesifikasi Protokol Komunikasi

### Binary Frame Packet Structure (Stream Layar)
Setiap frame video dikirim sebagai WebSocket Binary Message:
```
[ Byte 0 ]     : 0x53 ('S' = Screen Frame Magic Byte)
[ Byte 1..4 ]  : Frame Index (UInt32 Big-Endian)
[ Byte 5..6 ]  : Lebar Layar (UInt16 Big-Endian)
[ Byte 7..8 ]  : Tinggi Layar (UInt16 Big-Endian)
[ Byte 9..End ]: Raw JPEG Image Buffer
```

### JSON Control Payload (Klien ke Server)
```json
{ "type": "mouse_move_delta", "dx": 12, "dy": -8 }
{ "type": "mouse_click", "button": "left", "action": "click" }
{ "type": "mouse_scroll", "dy": 120, "dx": 0 }
{ "type": "text_input", "text": "Halo Dunia! 🚀" }
{ "type": "shortcut", "name": "win_d" }
```
