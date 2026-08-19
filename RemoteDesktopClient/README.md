# PC Remote Desktop Client (Android Kotlin Native v1.2.0)

[![Version](https://img.shields.io/badge/Version-1.2.0-blue.svg)](.)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84.svg)](.)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg)](.)

Aplikasi Android native berperforma tinggi yang dibangun menggunakan **Jetpack Compose**, **Material Design 3**, dan **CameraX** untuk mengendalikan PC Windows secara penuh dari genggaman (Mouse, Keyboard, Layar Fullscreen Immersive, dan Windows Shortcuts) secara instan tanpa PIN.

---

## 📱 Fitur Unggulan Client v1.2.0

### 1. Auto-Rotate & Immersive Fullscreen
- **Auto-Rotate Landscape:** Begitu berhasil terhubung ke server PC, layar otomatis berputar ke mode landscape sensor (*bolak-balik*). Saat koneksi terputus, orientasi otomatis dikembalikan ke normal.
- **Hardware-Accelerated Canvas:** Rendering frame JPEG langsung pada canvas hardware dengan decoder `Bitmap.Config.RGB_565` untuk latensi terendah dan penghematan memori RAM hingga 50%.

### 2. Picture-in-Picture (PiP) Layar Mengambang
- Tombol **Layar Mengambang (`⧉`)** di toolbar kanan atas untuk seketika beralih ke mode jendela mengambang di atas aplikasi lain.
- Saat keluar dari aplikasi atau menekan tombol Home, aplikasi tidak memunculkan floating window yang mengganggu, melainkan tetap berjalan di background via Foreground Service.

### 3. Koneksi Background 24/7 (Foreground Service)
- Layanan **Foreground Service (`RemoteConnectionService`)** menjaga koneksi WebSocket tetap hidup 24/7 dengan `PARTIAL_WAKE_LOCK` dan `WIFI_MODE_FULL_HIGH_PERF`.
- Menampilkan notifikasi status aktif pada status bar Android (`ic_notification`).

### 4. Input Ketik Langsung & Sensitivitas Kursor
- **Direct Typing:** Keyboard HP mengetik langsung ke aplikasi PC yang aktif secara *real-time* tanpa form perantara.
- **Sensitivitas Kursor Fleksibel:** Pengaturan slider sensitivitas di menu koneksi dan tombol *quick-cycle* preset di toolbar remote (0.75x hingga 3.0x).

### 5. Deteksi Terputus & Watchdog Anti-Stuck
- Deteksi instan saat server berhenti atau koneksi terputus.
- Timer watchdog 3.5 detik mencegah aplikasi *freeze* atau *stuck* di layar remote.

### 6. Pusat Izin Aplikasi Lipat (*Collapsible Permissions*)
- Kartu izin aplikasi dapat dilipat/dibuka dengan tombol panah (*chevron*), menampilkan badge status kesiapan (*Ready / Action Needed*).

---

## 📂 Struktur File Client

```
RemoteDesktopClient/
├── build.gradle.kts                    # Root build script
├── settings.gradle.kts                 # Project & repository settings
└── app/
    ├── build.gradle.kts                # Config modul app (v1.2.0, Compose, CameraX)
    └── src/main/
        ├── AndroidManifest.xml         # Izin sistem & deklarasi Foreground Service
        ├── res/
        │   ├── drawable/               # Vector icons (ic_launcher, ic_notification)
        │   └── mipmap-.../             # Launcher icon PNGs (MDPI s/d XXXHDPI)
        └── java/com/remotedesktop/client/
            ├── MainActivity.kt         # Entry point, PiP & Auto-Rotate
            ├── data/Protocol.kt        # Data Transfer Objects & States
            ├── service/
            │   └── RemoteConnectionService.kt # 24/7 Background Foreground Service
            ├── network/
            │   ├── WebSocketManager.kt # OkHttp WebSocket & Anti-Stuck Watchdog
            │   └── LanDiscoveryManager.kt # UDP Broadcast Scanner (9091)
            ├── viewmodel/RemoteViewModel.kt # State Holder & Settings Persistence
            └── ui/
                ├── theme/Theme.kt      # Material 3 Dark Theme
                ├── components/
                │   ├── PermissionCenter.kt # Collapsible Permissions Card
                │   └── QrScannerDialog.kt  # CameraX + ML Kit QR Reader
                └── screens/
                    ├── ConnectionScreen.kt # Form Koneksi, Scan LAN & QR
                    └── RemoteScreen.kt    # Canvas Stream, PiP, & Direct Typing
```

---

## 🔨 Cara Build & Install APK

Buka terminal di folder client:
```powershell
cd "RemoteDesktopClient"
./gradlew assembleDebug
```
File APK akan berada di:
`app\build\outputs\apk\debug\app-debug.apk`

Pasang ke smartphone via ADB:
```powershell
adb install app\build\outputs\apk\debug\app-debug.apk
```
