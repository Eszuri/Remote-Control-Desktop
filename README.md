# 🖥️📱 PC Remote Control Desktop (Full-Stack System)

Sistem *Remote Desktop Control* berperforma tinggi dan berlatensi ultra-rendah (*ultra-low latency*) yang menghubungkan **Smartphone Android (Client)** dengan **PC Windows (Server)** secara nirkabel melalui jaringan Wi-Fi lokal (LAN) maupun internet jarak jauh (WAN / VPN) secara instan tanpa PIN.

---

## 🌟 Ikhtisar Sistem

Proyek ini merupakan solusi *full-stack* independen tanpa ketergantungan pihak ketiga cloud (*self-hosted / zero-cloud dependency*):

```
+------------------------------------------------------------------------------------+
|                                ARSITEKTUR SISTEM                                   |
|                                                                                    |
|  [ SMARTPHONE ANDROID ]                                 [ PC WINDOWS DESKTOP ]     |
|  +---------------------+                               +-------------------------+ |
|  | RemoteDesktopClient |                               |   RemoteDesktopServer   | |
|  | (Jetpack Compose)   |                               |      (WPF / .NET 10)    | |
|  |                     |        UDP Broadcast (9091)   |                         | |
|  |  [LanDiscovery]     | ============================> | [UdpDiscoveryServer]    | |
|  |  [CameraX QR Scan]  | <---------------------------- | [QRCoder Instant Pair]  | |
|  |                     |                               |                         | |
|  |                     |        WebSocket (TCP 9090)   |                         | |
|  |  [OkHttp WS]        | ----------------------------> | [Fleck WebSocketServer] | |
|  |                     |        JSON Input Commands    |            |            | |
|  |  [Touch / Keyboard] | ----------------------------> | [Win32 SendInput API]   | |
|  |                     |                               |            |            | |
|  |  [RGB_565 Canvas]   | <============================ | [DXGI / GDI Capture]    | |
|  |  (Immersive Sticky) |   Binary Screen Stream (JPEG) | (Vortice Direct3D11)    | |
|  +---------------------+                               +-------------------------+ |
+------------------------------------------------------------------------------------+
```

---

## 🚀 Fitur Unggulan

| Kategori | Fitur Server (Windows) | Fitur Klien (Android) |
| :--- | :--- | :--- |
| **Video Streaming** | DirectX DXGI GPU Duplication (Vortice) + GDI BitBlt fallback | Hardware Accelerated Canvas Drawing + Fast `RGB_565` Bitmap Decoder |
| **Resolusi & FPS** | Dinamis 10–60 FPS & Kualitas JPEG 20–95% | Menampilkan indikator FPS real-time & status koneksi |
| **Kendali Mouse** | P/Invoke Win32 `SendInput` (Absolute & Delta Relative) | Dual Mode: **Trackpad Mode** (Touchpad laptop) & **Direct Touch Mode** |
| **Kendali Keyboard** | Full Unicode Text Injection (`KEYEVENTF_UNICODE`) | Pop-up Virtual Keyboard & IME Text Forwarding (mendukung emoji & simbol) |
| **Pintasan Cepat** | Eksekusi otomatis shortcut Windows | Toolbar Pintasan: `Win+D`, `Alt+Tab`, `Ctrl+C`, `Ctrl+V`, `Ctrl+Z`, `Ctrl+A`, dll. |
| **Konektivitas** | Fleck WebSocket Server (TCP 9090) & UDP Discovery (9091) | Pemindaian LAN otomatis & QR Code Camera Scanner (CameraX + ML Kit) |
| **Kemudahan Akses** | Koneksi instan tanpa PIN (*Zero PIN configuration*) | Otomatis terhubung setelah scan QR atau klik daftar server LAN |

---

## 📁 Struktur Repositori

```
Remote Control Desktop/
├── README.md                   # Dokumentasi Master Sistem (File Ini)
├── .gitignore                  # Gitignore Global (Visual Studio + Android/Gradle)
│
├── RemoteDesktopServer/        # [SERVER] Aplikasi Windows WPF (.NET 10.0)
│   ├── README.md               # Dokumentasi Teknis Server
│   ├── .gitignore              # Gitignore Khusus C# / WPF / .NET
│   ├── RemoteDesktopServer.csproj
│   ├── App.xaml / App.xaml.cs
│   ├── MainWindow.xaml / .cs   # Modern Dark UI Dashboard, Sliders, & Logging
│   ├── Core/
│   │   ├── IScreenCapture.cs   # Interface Capture
│   │   ├── DxgiScreenCapture.cs# Engine DirectX 11 Desktop Duplication
│   │   ├── GdiScreenCapture.cs # Fallback Engine GDI BitBlt
│   │   ├── ScreenCaptureManager.cs # Manajer Switch Engine Otomatis
│   │   ├── InputSimulator.cs   # P/Invoke Win32 SendInput (Mouse, Keyboard, Unicode)
│   │   └── NativeMethods.cs    # Win32 Native Structs & APIs
│   └── Network/
│       ├── Protocol.cs         # Serialisasi Pesan JSON Client & Server
│       ├── RemoteWebSocketServer.cs # WebSocket Server & Binary Streaming Loop
│       └── UdpDiscoveryServer.cs    # UDP Broadcast Auto-Discovery Responder
│
└── RemoteDesktopClient/        # [CLIENT] Aplikasi Android Native (Kotlin)
    ├── README.md               # Dokumentasi Teknis Klien
    ├── .gitignore              # Gitignore Khusus Android / Kotlin / Gradle
    ├── build.gradle.kts
    ├── settings.gradle.kts
    ├── app/
    │   ├── build.gradle.kts
    │   └── src/main/
    │       ├── AndroidManifest.xml
    │       └── java/com/remotedesktop/client/
    │           ├── MainActivity.kt         # Entry point & Immersive Mode Handler
    │           ├── data/Protocol.kt        # Data Transfer Objects & States
    │           ├── network/
    │           │   ├── WebSocketManager.kt # OkHttp WebSocket & Binary JPEG Receiver
    │           │   └── LanDiscoveryManager.kt # UDP Broadcast Scanner (9091)
    │           ├── viewmodel/RemoteViewModel.kt # State Management & Haptic Feedback
    │           └── ui/
    │               ├── theme/Theme.kt      # Material 3 Dark Theme
    │               ├── components/QrScannerDialog.kt # CameraX + ML Kit QR Reader
    │               └── screens/
    │                   ├── ConnectionScreen.kt # Form Koneksi, Scan LAN, & QR
    │                   └── RemoteScreen.kt    # Interactive Screen Canvas & Toolbar
```

---

## 🛠️ Panduan Mulai Cepat (Quick Start)

### Langkah 1: Jalankan Server di PC Windows
1. Pastikan PC terhubung ke jaringan Wi-Fi atau kabel LAN.
2. Buka terminal PowerShell di folder server:
   ```powershell
   cd "RemoteDesktopServer"
   dotnet run
   ```
3. Atau jalankan file executable hasil publish (`RemoteDesktopServer.exe`).
4. Server akan otomatis aktif dan menampilkan IP lokal dan QR Code.

> 💡 **Rekomendasi:** Jalankan server sebagai **Administrator** (*Run as Administrator*) agar server memiliki izin mengendalikan aplikasi ber-hak akses tinggi (seperti Task Manager, dialog UAC, dan game fullscreen).

---

### Langkah 2: Jalankan Klien di Smartphone Android
1. Pastikan HP Android terhubung ke jaringan Wi-Fi yang **sama** dengan PC.
2. Buka proyek `RemoteDesktopClient` di **Android Studio**, lalu jalankan di HP Android Anda (atau pasang file APK `app-debug.apk`).
3. Pilih salah satu cara koneksi instan:
   - **Opsi A (Scan QR):** Tekan tombol **Scan QR** di HP, lalu arahkan kamera ke QR Code di layar PC.
   - **Opsi B (Scan LAN):** Tekan **Scan LAN**, tunggu daftar PC muncul, lalu tekan **Connect**.
   - **Opsi C (Manual):** Masukkan IP PC dan Port (`9090`), lalu tekan **Connect Now**.
4. Layar PC Windows akan langsung tampil di smartphone Android dalam mode layar penuh *immersive*!

---

## 🌐 Panduan Koneksi Jarak Jauh (WAN / Luar Rumah)

Jika Anda ingin mengontrol PC dari luar rumah melalui jaringan seluler (4G/5G) atau Wi-Fi publik:

### Menggunakan VPN Mesh (Tailscale / ZeroTier) — *Direkomendasikan (Paling Aman)*
1. Pasang **Tailscale** di PC Windows dan HP Android Anda.
2. Login dengan akun yang sama pada kedua perangkat.
3. Masukkan IP Tailscale PC Windows Anda (misal: `100.x.y.z`) ke dalam aplikasi Android pada kolom IP Address.
4. Tekan **Connect Now** — koneksi akan terenkripsi secara *end-to-end* tanpa perlu *port forwarding*.

### Menggunakan Tunneling (Ngrok)
1. Di PC Windows, jalankan ngrok untuk mengekspos port 9090:
   ```bash
   ngrok tcp 9090
   ```
2. Salin host dan port yang diberikan oleh ngrok (misal: `0.tcp.ngrok.io:12345`).
3. Masukkan host dan port tersebut ke aplikasi Android.

---

## 📡 Spesifikasi Protokol Data

### 1. UDP Auto-Discovery (Port 9091)
- **Request (Android -> Broadcast `255.255.255.255:9091`):**
  ```text
  DISCOVER_REMOTE_SERVER
  ```
- **Response (Server -> Android):**
  ```json
  {
    "type": "REMOTE_SERVER_INFO",
    "serverName": "DESKTOP-WIN11",
    "port": 9090
  }
  ```

### 2. Binary Frame Stream Header (Server -> Klien)
| Offset | Tipe Data | Keterangan |
| :--- | :--- | :--- |
| `0` | `byte` | Magic Byte `0x53` ('S') untuk menandai Screen Frame |
| `1 .. 4` | `UInt32` (BE) | Frame Index urutan gambar |
| `5 .. 6` | `UInt16` (BE) | Lebar frame layar (Width) |
| `7 .. 8` | `UInt16` (BE) | Tinggi frame layar (Height) |
| `9 .. End` | `byte[]` | Raw Binary Payload Gambar JPEG |

### 3. JSON Input Commands (Klien -> Server)
- **Gerakan Mouse Delta (Trackpad):**
  ```json
  { "type": "mouse_move_delta", "dx": 15, "dy": -10 }
  ```
- **Klik Mouse:**
  ```json
  { "type": "mouse_click", "button": "left", "action": "click" }
  ```
- **Scroll Mouse Wheel:**
  ```json
  { "type": "mouse_scroll", "dy": 120, "dx": 0 }
  ```
- **Ketik Teks Langsung (Unicode):**
  ```json
  { "type": "text_input", "text": "Hello World! 🚀" }
  ```
- **Pintasan Windows:**
  ```json
  { "type": "shortcut", "name": "win_d" }
  ```

---

## 🔧 Troubleshooting & Tips Performa

1. **Layar Hitam atau Tidak Tampil:**
   - Server memiliki fallback otomatis dari DirectX DXGI ke GDI BitBlt. Jika layar tetap tidak tampil, pastikan resolusi monitor PC Anda tidak melebihi batas kartu grafis.
2. **Koneksi Ditolak / Timeout:**
   - Periksa apakah firewall Windows memblokir port `9090` (TCP) dan `9091` (UDP).
   - Pastikan opsi *AP Isolation* pada router Wi-Fi Anda dalam keadaan nonaktif.
3. **Mengoptimalkan Latensi (Zero-Lag):**
   - Gunakan pita frekuensi **Wi-Fi 5 GHz** untuk transmisi nirkabel yang stabil.
   - Di antarmuka Server, atur slider kualitas JPEG ke **60–75%** untuk keseimbangan optimal antara ketajaman teks dan bandwidth.
   - Atur FPS ke **30 atau 60 FPS** sesuai performa perangkat smartphone Anda.

---

## 📄 Lisensi

Proyek ini dilisensikan di bawah [MIT License](LICENSE). Bebas digunakan dan dimodifikasi untuk keperluan pribadi maupun pengembangan lebih lanjut.
