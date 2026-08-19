# 🖥️📱 PC Remote Control Desktop (Full-Stack System v1.2.0)

[![Release](https://img.shields.io/badge/Release-v1.2.0-blue.svg)](https://github.com/)
[![Windows Server](https://img.shields.io/badge/Server-WPF%20.NET%2010-0078D7.svg)](RemoteDesktopServer/)
[![Android Client](https://img.shields.io/badge/Client-Kotlin%20Compose-3DDC84.svg)](RemoteDesktopClient/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

Sistem **Remote Desktop Control** berperforma tinggi dan berlatensi ultra-rendah (*ultra-low latency*) yang menghubungkan **Smartphone Android (Client)** dengan **PC Windows (Server)** secara nirkabel melalui jaringan Wi-Fi lokal (LAN) maupun internet jarak jauh (WAN / VPN) secara instan tanpa PIN.

---

## 🌟 Ikhtisar Arsitektur Sistem

Proyek ini merupakan solusi *full-stack* independen tanpa ketergantungan server cloud pihak ketiga (*self-hosted / zero-cloud dependency*):

```
+------------------------------------------------------------------------------------+
|                             ARSITEKTUR SISTEM v1.2.0                               |
|                                                                                    |
|  [ SMARTPHONE ANDROID (Client) ]                        [ PC WINDOWS (Server) ]    |
|  +-----------------------------+                       +-------------------------+ |
|  | RemoteDesktopClient v1.2.0  |                       | RemoteDesktopServer     | |
|  | (Jetpack Compose & Kotlin)  |                       | (WPF / .NET 10 Desktop) | |
|  |                             | UDP Broadcast (9091)  |                         | |
|  |  [LanDiscoveryManager]      | ====================> | [UdpDiscoveryServer]    | |
|  |  [CameraX QR Scanner]       | <-------------------- | [QRCoder Dynamic Gen]   | |
|  |                             |                       |                         | |
|  |  [Foreground Service]       |                       | [System Tray & Startup] | |
|  |  (24/7 WakeLock / WifiLock) |  WebSocket (TCP 9090) | (Run in Background)     | |
|  |  [OkHttp WebSocket]         | <-------------------> | [Fleck WebSocketServer] | |
|  |                             |                       |            |            | |
|  |  [Touch / Delta Sensitivity]|  JSON Input Messages  |            v            | |
|  |  [Direct Unicode Keyboard]  | --------------------> | [Win32 SendInput API]   | |
|  |  [Picture-in-Picture (PiP)] |                       |                         | |
|  |                             |  Binary Screen Stream | [DXGI GPU Duplication]  | |
|  |  [Hardware-Accelerated      | <==================== | (Vortice Direct3D11)    | |
|  |   RGB_565 Canvas Stream]    |   (0x53 Header+JPEG)  | [GDI BitBlt Fallback]   | |
|  +-----------------------------+                       +-------------------------+ |
+------------------------------------------------------------------------------------+
```

---

## 🚀 Fitur Unggulan Sistem

### 🖥️ Di Sisi Server (Windows PC)
1. **DirectX DXGI GPU Capture Engine:** Mengambil frame langsung dari VRAM kartu grafis dengan latensi sub-frame (< 10ms) dan konsumsi CPU minimal.
2. **Auto-Recovery GDI Fallback:** Jika DXGI context terlepas (misal saat game fullscreen atau perubahan resolusi), server otomatis beralih ke GDI BitBlt dan melakukan pemulihan otomatis kembali ke DirectX.
3. **Zero-Client Idle Sleep Mode:** Server otomatis menghentikan capture saat tidak ada klien yang terhubung (0 FPS / 0% CPU), dan seketika bangun saat klien terhubung.
4. **Auto-Startup on Booting:** Dilengkapi opsi switch on/off untuk mendaftarkan server ke startup Windows (`HKCU\...\Run`) agar otomatis berjalan saat PC dinyalakan.
5. **System Tray Integration:**
   - Server dapat berjalan murni di background tanpa memunculkan jendela saat booting.
   - Ikon baki sistem (*System Tray*) dengan tooltip IP & status real-time.
   - Klik kiri pada tray icon untuk memunculkan jendela server; klik kanan untuk menu cepat (*Open*, *Toggle Server*, *Exit*).
   - Menekan tombol close **(X)** tidak mematikan server, melainkan otomatis meminimalkan server ke system tray (*ShowInTaskbar = false*).
6. **Simulasi Input Native Win32:**
   - Emulasi trackpad presisi tinggi menggunakan koordinat delta.
   - Injeksi teks dan simbol Unicode langsung (`KEYEVENTF_UNICODE`).
   - Eksekusi instan pintasan Windows (`Win+D`, `Alt+Tab`, `Ctrl+C`, `Ctrl+V`, `Ctrl+Z`, `Ctrl+A`, dll.).
7. **Pairing Instan Tanpa PIN:** QR Code dinamis kontras tinggi dengan fitur klik untuk memperbesar (*centered modal zoom*).

### 📱 Di Sisi Client (Android Smartphone)
1. **Auto-Rotate Layar:** Otomatis berputar ke mode landscape sensor (*bolak-balik*) saat terhubung, dan kembali ke mode portrait saat terputus.
2. **Picture-in-Picture (PiP) Layar Mengambang:** Tekan tombol **`⧉`** di pojok kanan atas untuk seketika mengubah remote PC menjadi jendela mengambang di atas aplikasi Android lain.
3. **Koneksi Latar Belakang 24/7 (Foreground Service):** Dilengkapi *Ongoing Notification*, `PARTIAL_WAKE_LOCK`, dan `WIFI_MODE_FULL_HIGH_PERF` sehingga koneksi tidak pernah putus meskipun Anda keluar dari aplikasi atau layar smartphone mati.
4. **Ketik Langsung Real-time:** Mengetik langsung dari keyboard HP ke aplikasi PC tanpa perlu form input perantara. Dilengkapi tombol pemicu show/hide keyboard virtual di sebelah tombol setting.
5. **Pengaturan Sensitivitas Kursor:** Slider pengaturan sensitivitas kursor (0.5x hingga 4.0x) pada menu koneksi dan tombol *quick-cycle preset* (0.75x ➡️ 1.0x ➡️ 1.5x ➡️ 2.0x ➡️ 2.5x ➡️ 3.0x) pada toolbar remote.
6. **Deteksi Terputus & Watchdog Anti-Stuck:** Deteksi instan saat server berhenti atau jaringan putus (heartbeat timeout 3.5 detik), otomatis keluar dari mode remote ke layar utama dengan notifikasi peringatan.
7. **Pusat Izin Lipat (*Collapsible App Permissions*):** Kartu izin aplikasi yang dapat dibuka/tutup dengan tombol panah chevron, menjaga tampilan antarmuka tetap bersih.
8. **Persistensi Pengaturan:** Menyimpan port terakhir, FPS, kualitas JPEG, dan sensitivitas kursor untuk sesi berikutnya.

---

## 📁 Struktur Repositori

```
Remote Control Desktop/
├── README.md                   # Dokumentasi Utama Sistem (v1.2.0)
├── .gitignore                  # Gitignore Global
│
├── RemoteDesktopServer/        # [SERVER] Aplikasi Windows WPF (.NET 10.0)
│   ├── README.md               # Dokumentasi Teknis Server
│   ├── RemoteDesktopServer.csproj
│   ├── App.xaml / App.xaml.cs  # Desain Modern Dark Theme & Resource
│   ├── MainWindow.xaml / .cs   # Dashboard Kontrol, Tray Handler, & Startup Manager
│   ├── Assets/
│   │   ├── app.ico             # Ikon Multi-Resolution Windows (16x16 s/d 256x256)
│   │   └── app.png             # Ikon High-Res 512x512
│   ├── Core/
│   │   ├── AppSettings.cs      # Persistensi Konfigurasi Lokal (settings.json)
│   │   ├── StartupManager.cs   # Pengelola Windows Registry Run Key
│   │   ├── TrayIconManager.cs  # Native Win32 Shell_NotifyIcon & Context Menu
│   │   ├── IScreenCapture.cs   # Interface Screen Capture
│   │   ├── DxgiScreenCapture.cs# Engine DirectX 11 GPU Capture (Vortice)
│   │   ├── GdiScreenCapture.cs # Fallback Engine Win32 GDI BitBlt
│   │   ├── ScreenCaptureManager.cs # Switcher & Auto-Recovery Capture Engine
│   │   ├── InputSimulator.cs   # Win32 SendInput (Mouse, Keyboard, Unicode)
│   │   └── NativeMethods.cs    # Win32 P/Invoke Declarations & Structs
│   └── Network/
│       ├── Protocol.cs         # JSON Serialization Model
│       ├── RemoteWebSocketServer.cs # WebSocket Server & Binary Streaming Loop
│       └── UdpDiscoveryServer.cs    # UDP Auto-Discovery Responder (Port 9091)
│
└── RemoteDesktopClient/        # [CLIENT] Aplikasi Android Native (Kotlin)
    ├── README.md               # Dokumentasi Teknis Client
    ├── build.gradle.kts
    ├── settings.gradle.kts
    └── app/
        ├── build.gradle.kts    # Gradle Config (v1.2.0, Compose, CameraX)
        └── src/main/
            ├── AndroidManifest.xml # Permissions & Services Declaration
            ├── res/
            │   ├── drawable/   # Vector icons (ic_notification, ic_launcher)
            │   └── mipmap-.../ # Android Launcher Icons (MDPI s/d XXXHDPI)
            └── java/com/remotedesktop/client/
                ├── MainActivity.kt         # Immersive Handler, Auto-Rotate & PiP
                ├── data/Protocol.kt        # State Management & DTO Models
                ├── service/
                │   └── RemoteConnectionService.kt # 24/7 Foreground Service
                ├── network/
                │   ├── WebSocketManager.kt # OkHttp WebSocket & Anti-Stuck Watchdog
                │   └── LanDiscoveryManager.kt # UDP Broadcast Scanner (9091)
                ├── viewmodel/RemoteViewModel.kt # State Holder & Settings Persistence
                └── ui/
                    ├── theme/Theme.kt      # Material Design 3 Dark Theme
                    ├── components/
                    │   ├── PermissionCenter.kt # Collapsible Permissions Card
                    │   └── QrScannerDialog.kt  # CameraX + ML Kit QR Reader
                    └── screens/
                        ├── ConnectionScreen.kt # Form Koneksi, Scan LAN & QR
                        └── RemoteScreen.kt    # Canvas Stream, PiP, & Direct Typing
```

---

## 🛠️ Panduan Mulai Cepat (Quick Start)

### 1. Menjalankan Server di PC Windows
1. Buka folder `RemoteDesktopServer` di terminal:
   ```powershell
   cd "RemoteDesktopServer"
   dotnet run
   ```
2. Atau jalankan file executable `RemoteDesktopServer.exe`.
3. Server akan otomatis aktif dan menampilkan alamat IP lokal serta QR Code.
4. *(Opsional)* Aktifkan switch **"Launch on Startup"** agar server otomatis aktif di system tray setiap kali PC menyala.

### 2. Menjalankan Client di Smartphone Android
1. Buka proyek `RemoteDesktopClient` di **Android Studio** atau pasang file APK `app-debug.apk`.
2. Pastikan HP terhubung ke jaringan Wi-Fi yang sama dengan PC (atau menggunakan VPN).
3. Hubungkan dengan salah satu metode:
   - **Scan QR:** Tekan tombol **Scan QR** di kanan atas HP dan arahkan ke QR Code di layar PC.
   - **Scan LAN:** Tekan tombol **Scan LAN** untuk mendeteksi PC secara otomatis, lalu tekan **Connect**.
   - **Manual:** Masukkan IP PC dan Port `9090`, lalu tekan **Connect Now**.
4. Layar HP akan seketika berputar ke posisi landscape dan menampilkan desktop PC Anda secara *real-time*.

---

## 🌐 Panduan Penggunaan Jarak Jauh (Online / Luar Rumah)

Jika Anda berada di luar rumah dan ingin mengontrol PC melalui koneksi seluler (4G/5G) atau Wi-Fi lain:

### Rekomendasi: Menggunakan Mesh VPN (Tailscale) — *Paling Cepat & Aman*
1. Pasang aplikasi **Tailscale** di PC Windows dan HP Android Anda.
2. Login dengan akun yang sama pada kedua perangkat.
3. Masukkan alamat IP Tailscale PC Anda (misal: `100.x.y.z`) ke aplikasi Android pada kolom Server IP Address.
4. Tekan **Connect Now** — Anda terhubung secara aman dan terenkripsi dari mana saja tanpa perlu konfigurasi router (*Zero Port Forwarding*).

---

## 📄 Lisensi

Proyek ini dilisensikan di bawah lisensi [MIT License](LICENSE). Bebas digunakan dan dikembangkan untuk kebutuhan pribadi maupun komersial.
