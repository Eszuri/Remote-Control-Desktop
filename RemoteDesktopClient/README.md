# PC Remote Client (Android Kotlin Native)

Aplikasi Android native berperforma tinggi yang dibangun menggunakan **Jetpack Compose**, **Material Design 3**, dan **CameraX** untuk mengendalikan PC Windows secara penuh dari genggaman (Mouse, Keyboard, Layar Fullscreen Immersive, dan Windows Shortcuts) secara instan tanpa PIN.

---

## 📱 Arsitektur & Fitur Utama

```
+-------------------------------------------------------------------------+
|                       RemoteDesktopClient (Android)                     |
|                                                                         |
|  [ UI: Jetpack Compose ] <-> [ RemoteViewModel (StateFlow/SharedFlow) ] |
|            |                                    |                       |
|   +--------+--------+                  +--------+--------+              |
|   |                 |                  |                 |              |
| [CameraX/ML Kit] [Canvas Screen]   [OkHttp WebSocket] [UDP Discovery]   |
| (QR Scan Pair)   (RGB_565 Stream)   (ws://IP:9090)     (Port 9091)      |
+-------------------------------------------------------------------------+
```

### 1. Tampilan Layar Real-time (Immersive Fullscreen)
- **Zero-Latency Canvas Stream:**
  - Rendering canvas hardware-accelerated tanpa delay pipeline UI standar.
  - Optimasi memori dan kecepatan menggunakan decoder `Bitmap.Config.RGB_565` (mengurangi beban RAM hingga 50% dibandingkan ARGB_8888).
- **Immersive Sticky Window:**
  - Menyembunyikan status bar dan navigation bar sistem secara penuh menggunakan `WindowInsetsControllerCompat`.
  - Mencegah layar mati otomatis (*Keep Screen On*).

### 2. Dual Touch Mode (Trackpad vs Direct Touch)
- **Trackpad Mode (Default):** Layar HP berfungsi seperti touchpad laptop:
  - **1 Jari Geser:** Menggerakkan kursor mouse PC dengan sensitivitas halus.
  - **1 Tap:** Klik Kiri (Left Click).
  - **2 Tap Cepat:** Dobel Klik (Double Click).
  - **Long Press:** Klik Kanan (Right Click).
  - **Haptic Feedback:** Getaran mikro haptik responsif pada setiap interaksi sentuhan.
- **Direct Touch Mode:**
  - Layar HP menjadi cermin touchscreen monitor PC: sentuhan pada koordinat layar HP langsung mengklik titik yang persis sama di layar PC.

### 3. Kontrol Input & Pintasan Keyboard Windows
- **Toolbar Pintasan Windows (Shortcut Bar):**
  - Akses cepat 1-ketukan untuk: `Win+D`, `Alt+Tab`, `Ctrl+C`, `Ctrl+V`, `Ctrl+Z`, `Ctrl+A`, `Esc`, `Enter`, `Bksp`, `Tab`, `Space`, dan tombol navigasi arah (`▲`, `▼`, `◀`, `▶`).
- **Virtual Action Bar:**
  - Tombol fisik virtual *Left Click* & *Right Click* di bagian bawah.
  - Tombol pintas *Mouse Wheel Scroll Up* & *Scroll Down*.
- **Virtual Keyboard & Unicode Input:**
  - Dialog pop-up keyboard untuk mengetik kalimat panjang, angka, simbol spesial, dan emoji, langsung terkirim dan diketikkan secara native ke aplikasi PC yang aktif.

### 4. Koneksi Instan (Zero Configuration)
- **QR Code Camera Scanner (CameraX + ML Kit):**
  - Cukup arahkan kamera smartphone ke QR Code pada aplikasi server Windows untuk pairing instan satu detik.
- **LAN Auto-Discovery (UDP Socket):**
  - Tombol **Scan LAN** memancarkan broadcast UDP di port `9091`.
  - Menampilkan daftar server Windows di jaringan Wi-Fi lokal dan terkoneksi hanya dengan satu ketukan tombol **Connect**.
- **Koneksi Jarak Jauh (WAN / VPN):**
  - Mendukung IP Publik router atau alamat IP VPN seperti Tailscale / ZeroTier / Ngrok untuk kendali jarak jauh antar kota/negara.

---

## 🛠️ Prasyarat & Spesifikasi Teknis

| Parameter | Kebutuhan |
| :--- | :--- |
| **Minimum SDK** | Android 8.0 Oreo (API Level 26) |
| **Target SDK / Compile SDK** | Android 14 (API Level 34) |
| **Bahasa Pemrograman** | Kotlin 1.9.24 |
| **UI Toolkit** | Jetpack Compose (BOM 2024.06.00) + Material 3 |
| **Arsitektur** | Android Jetpack MVVM (ViewModel, Coroutines, Flow) |
| **Network Client** | OkHttp 4.12.0 (WebSocket) & Java DatagramSocket |
| **Computer Vision** | CameraX 1.3.4 + Google ML Kit Barcode Scanning 17.3.0 |
| **Gradle** | Gradle 8.7 & Android Gradle Plugin 8.4.1 |

---

## 📂 Struktur File Client

```
RemoteDesktopClient/
├── build.gradle.kts                    # Root build script
├── settings.gradle.kts                 # Project & repository settings
├── gradle.properties                   # JVM & AndroidX properties
├── gradlew / gradlew.bat               # Gradle Wrapper script
├── .gitignore                          # Filter file build Android/Gradle
├── app/
│   ├── build.gradle.kts                # Modul app (Dependencies, SDK configs)
│   └── src/main/
│       ├── AndroidManifest.xml         # Izin sistem (Kamera, Internet, Getar, Wi-Fi)
│       ├── res/                        # Icon aplikasi, tema, dan string resources
│       └── java/com/remotedesktop/client/
│           ├── MainActivity.kt         # Entry point, full-screen immersive controller
│           ├── data/
│           │   └── Protocol.kt         # Data Transfer Objects, TouchMode, & ConnectionState
│           ├── network/
│           │   ├── WebSocketManager.kt # OkHttp WebSocket, receiver frame JPEG, & sender JSON
│           │   └── LanDiscoveryManager.kt # UDP Broadcast scanner (Port 9091)
│           ├── viewmodel/
│           │   └── RemoteViewModel.kt  # State holder, FPS counter, haptic feedback, actions
│           └── ui/
│               ├── theme/
│               │   └── Theme.kt        # Skema warna Dark Theme & tipografi Material 3
│               ├── components/
│               │   └── QrScannerDialog.kt # Scanner QR Code berbasis CameraX & ML Kit
│               └── screens/
│                   ├── ConnectionScreen.kt # Form koneksi, scan QR, & daftar server LAN
│                   └── RemoteScreen.kt    # Layar canvas interaktif, toolbar, & shortcut
```

---

## 🔨 Cara Build & Install ke Smartphone

### Opsi A: Menggunakan Android Studio (Direkomendasikan)
1. Buka **Android Studio** (Hedgehog / Iguana / Jellyfish atau lebih baru).
2. Pilih **Open**, lalu arahkan ke folder:
   `d:\Codingan\Project-Full-Stack\Remote Control Desktop\RemoteDesktopClient`
3. Tunggu hingga proses **Gradle Sync** selesai secara otomatis.
4. Aktifkan **USB Debugging** pada smartphone Android Anda (Settings > Developer Options > USB Debugging).
5. Hubungkan smartphone ke komputer menggunakan kabel USB atau via Wi-Fi Pairing.
6. Klik tombol **Run 'app'** (`Shift + F10`) di toolbar Android Studio.

### Opsi B: Build APK via Terminal Gradle Wrapper
Buka terminal PowerShell di folder client:
```powershell
cd "d:\Codingan\Project-Full-Stack\Remote Control Desktop\RemoteDesktopClient"
./gradlew assembleDebug
```
File APK yang dihasilkan akan berada di:
`app\build\outputs\apk\debug\app-debug.apk`

Pasang APK ke smartphone melalui ADB:
```powershell
adb install app\build\outputs\apk\debug\app-debug.apk
```

---

## 📋 Izin Sistem (Permissions Breakdown)

Aplikasi memerlukan izin berikut yang dideklarasikan pada `AndroidManifest.xml`:
- `android.permission.INTERNET`: Untuk membuka koneksi WebSocket ke PC server.
- `android.permission.ACCESS_NETWORK_STATE` & `ACCESS_WIFI_STATE`: Untuk mendeteksi status Wi-Fi lokal.
- `android.permission.CHANGE_WIFI_MULTICAST_STATE`: Untuk mengirim dan menerima paket UDP broadcast Auto-Discovery.
- `android.permission.CAMERA`: Untuk memindai QR Code pairing pada layar PC.
- `android.permission.VIBRATE`: Untuk memberikan respon haptik saat melakukan klik atau menekan tombol pintasan.

---

## 🎮 Panduan Gesture Layar Sentuh

| Aksi Gesture | Mode Trackpad | Mode Direct Touch |
| :--- | :--- | :--- |
| **Geser 1 Jari** | Menggerakkan kursor mouse PC | Menggerakkan kursor ke posisi jari |
| **Tap 1 Jari** | Klik Kiri (Left Click) | Klik Kiri pada titik yang disentuh |
| **Tap Ganda (2 Tap)** | Dobel Klik (Buka file / folder) | Dobel Klik pada titik yang disentuh |
| **Long Press (Tahan)** | Klik Kanan (Context Menu) | Klik Kanan pada titik yang disentuh |
| **Tombol Panah Atas/Bawah** | Scroll halaman ke atas / bawah | Scroll halaman ke atas / bawah |
| **Tombol Keyboard** | Mengetik teks jarak jauh | Mengetik teks jarak jauh |
