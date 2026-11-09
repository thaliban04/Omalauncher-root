# Omalauncher-Root (DroidSpaces & System Edition)

[![Release](https://img.shields.io/badge/Release-v1.0.0--stable-blue.svg)](releases/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%2010%2B%20%7C%20arm64-green.svg)]()
[![Magisk / KernelSU](https://img.shields.io/badge/Root-Magisk%20%7C%20KernelSU%20%7C%20APatch-orange.svg)]()

> **Versi Bahasa Indonesia** | [English Version](#english-documentation)

**Omalauncher-Root** adalah fork khusus performa tinggi dari [Omalauncher](https://omarchy.org), yang dioptimalkan untuk perangkat Android ber-root (Magisk / KernelSU / APatch) dengan integrasi kontainer native **DroidSpaces**, akselerasi grafis Wayland, dukungan penuh input periferal (Bluetooth Mouse & Keyboard), serta modul sistem priv-app tanpa lag dan anti-kill.

---

## 🚀 Fitur Unggulan & Optimasi

1. **Magisk / KernelSU System Priv-App Module**
   - Diinstal langsung ke `/system/priv-app/OmaLauncher/` dengan hak akses `privapp-permissions-dev.omalauncher.xml`.
   - Mendapatkan prioritas memori OOM / LMK `-1000` (tidak akan pernah di-kill oleh Android LowMemoryKiller).
   - Animasi kembali ke Home Screen instan tanpa reload / redraw.

2. **Backend Native DroidSpaces (Chroot / Linux Container)**
   - Menggantikan PRoot standar dengan container kernel native di `/mnt/Droidspaces/launcher/`.
   - Menghilangkan *overhead* ptrace: kompilasi kode, eksekusi biner, dan aplikasi GUI berjalan pada performa native (bare-metal speed).

3. **Immersive Fullscreen Wayland Display**
   - Status bar dan navigation bar Android otomatis disembunyikan secara bersih (*transient swipe*) saat membuka aplikasi Linux Wayland (seperti Foot, Artcraft, btop, cmake-gui).
   - Layar penuh tanpa gangguan status bar bawaan ROM.

4. **Dukungan Native Bluetooth Mouse & Keyboard Fisik**
   - **Mouse Wheel Scroll**: Mendukung penuh *scroll wheel* vertikal & horizontal (`AXIS_VSCROLL`, `AXIS_HSCROLL`), klik kanan, dan *hover pointer*.
   - **Floating Keyboard Button Collapsible**: Tombol virtual keyboard di pojok kanan bawah dapat di-hide/minimize (`⌨` / `‹`) sehingga tidak mengganggu saat menggunakan keyboard fisik Bluetooth.

5. **Penyempurnaan App Drawer & Ikon Linux**
   - **No Auto-Keyboard**: Keyboard virtual tidak lagi otomatis muncul saat membuka App Drawer; keyboard hanya aktif jika kolom pencarian diklik/fokus.
   - **High-Definition Linux Icons**: Deteksi multi-direktori cerdas untuk ikon aplikasi Linux (PNG 512px hingga 32px & SVG) dengan tampilan squircle modern.

6. **Fix Kompatibilitas Biner Linux**
   - Mengatasi isu `Operation not permitted` pada biner yang memiliki capability kernel (`btop`, dll).
   - Dukungan penuh Qt6 Wayland (`cmake-gui`, dll) dan pemutar musik terminal `cliamp` dengan ALSA.

---

## 📦 Download

| File | Keterangan | Download Link |
| --- | --- | --- |
| **OmaLauncher-Magisk-Module.zip** | Modul Magisk/KernelSU/APatch (System Priv-App, Anti-Kill) | [Download ZIP](releases/OmaLauncher-Magisk-Module.zip) |
| **OmaLauncher-v1.0.0-release.apk** | File APK Mandiri (Standalone Release, R8 ProGuard Minified) | [Download APK](releases/OmaLauncher-v1.0.0-release.apk) |

---

## 🛠️ Panduan Penginstalan

### Metode 1: Menggunakan Modul Magisk / KernelSU / APatch (Sangat Direkomendasikan)

Metode ini memberikan performa terbaik dan stabilitas maksimal karena aplikasi berjalan sebagai aplikasi sistem privileged.

1. Download [OmaLauncher-Magisk-Module.zip](releases/OmaLauncher-Magisk-Module.zip).
2. Buka aplikasi **Magisk**, **KernelSU**, atau **APatch Manager** di ponsel Anda.
3. Masuk ke tab **Modules** (Modul) -> klik **Install from storage** (Pasang dari penyimpanan).
4. Pilih file `OmaLauncher-Magisk-Module.zip`.
5. Tunggu hingga proses instalasi selesai, lalu **Reboot** (Mulai ulang) ponsel Anda.
6. Setelah reboot:
   - Buka **Settings (Pengaturan Android)** -> **Apps (Aplikasi)** -> **Default apps (Aplikasi default)** -> **Home app**.
   - Pilih **Oma Launcher** sebagai launcher utama Anda.

---

### Metode 2: Instalasi APK Standar

Jika Anda ingin mencoba launcher tanpa memasangnya ke partisi sistem:

1. Download [OmaLauncher-v1.0.0-release.apk](releases/OmaLauncher-v1.0.0-release.apk).
2. Buka file manager dan instal APK seperti biasa (izinkan pemasangan dari sumber tidak dikenal jika diminta).
3. Buka **Settings Android** -> jadikan **Oma Launcher** sebagai Default Home app.

---

## 🐧 Menyiapkan Rootfs Arch Linux & DroidSpaces

Untuk menjalankan lingkungan Linux penuh dengan performa native:

1. Pastikan folder DroidSpaces telah dibuat di penyimpanan root ponsel:
   ```bash
   su
   mkdir -p /mnt/Droidspaces/launcher
   ```
2. Ekstrak rootfs Arch Linux ARM (aarch64) ke dalam folder tersebut:
   ```bash
   tar -xpf /sdcard/Download/ArchLinuxARM-aarch64-latest.tar.gz -C /mnt/Droidspaces/launcher/
   ```
3. Konfigurasi socket Wayland dan permissions:
   ```bash
   sh /sdcard/Download/scripts/setup-wayland-droidspaces.sh
   ```
4. Buka Oma Launcher. Ketuk terminal atau buka aplikasi Linux dari App Drawer.

---

## 🎮 Pintasan Keyboard & Navigasi

| Tombol | Lokasi | Fungsi |
| --- | --- | --- |
| **Super + D** | Home Screen | Membuka App Drawer |
| **Super + T** | Home Screen | Membuka terminal Foot |
| **Super + Down** | Home Screen | Membuka panel swipe-down / notifikasi |
| **Super + 1..9** | Home Screen | Berpindah antar halaman workspace |
| **Super + W** | Jendela Linux | Menutup jendela aplikasi Linux |
| **Super + Esc** | Jendela Linux | Keluar ke Home tanpa menutup proses aplikasi |
| **Panah / Tab** | Home / Drawer | Navigasi fokus tile & aplikasi |
| **Enter** | Tile / Drawer | Membuka aplikasi yang disorot |
| **Esc** | Drawer / Panel | Menutup drawer atau panel yang sedang terbuka |

---

## 💡 Troubleshooting & Solusi

### 1. `Operation not permitted` saat menjalankan biner tertentu (contoh: `btop`)
Android kernel menolak capability bawaan paket Linux tertentu. Jalankan perintah ini di terminal:
```bash
su -c "setfattr -x security.capability /mnt/Droidspaces/launcher/usr/bin/btop"
```

### 2. Qt6 GUI Apps (`cmake-gui`) tidak muncul
Pastikan paket Qt6 Wayland terpasang di Arch Linux:
```bash
pacman -S --needed qt6-base qt6-wayland
```

### 3. Ikon Aplikasi Linux Tidak Muncul di App Drawer
Ikon aplikasi disimpan di `/usr/share/icons/hicolor/` atau `/root/.local/share/icons/hicolor/`. OmaLauncher-Root telah dilengkapi multi-directory icon resolver otomatis yang mendukung PNG 512x512 hingga 32x32 serta SVG.

---
---

<a name="english-documentation"></a>
# English Documentation

**Omalauncher-Root** is a high-performance fork of [Omalauncher](https://omarchy.org), tailored for rooted Android devices (Magisk, KernelSU, APatch) featuring native **DroidSpaces** Linux container integration, Wayland compositor optimizations, full peripheral input support (Bluetooth Mouse & Hardware Keyboard), and a zero-lag, anti-kill system privileged app module.

---

## ✨ Features & Enhancements

- **Magisk / KernelSU / APatch Module**: Installs as `/system/priv-app/OmaLauncher/` with `-1000` OOM priority, completely eliminating background app kills by the Android LowMemoryKiller.
- **DroidSpaces Chroot Container**: Runs Arch Linux ARM with native kernel namespaces directly on `/mnt/Droidspaces/launcher/` (zero ptrace overhead, bare-metal CPU & GPU performance).
- **Immersive Wayland Windows**: Android status bar and navigation bars automatically hide with transient swipe handling for Linux GUI apps.
- **Bluetooth Mouse & Keyboard Native Input**:
  - Smooth vertical & horizontal scrolling (`AXIS_VSCROLL`, `AXIS_HSCROLL`).
  - Pointer hover tracking and native right-click support.
  - Floating soft keyboard button can be collapsed/hidden (`⌨` / `‹`) for a clean physical keyboard experience.
- **Improved App Drawer**:
  - Soft keyboard no longer forces auto-popup upon opening the drawer.
  - Multi-resolution (512px to 32px PNG & SVG) Linux icon resolver with elegant squircle badges.
- **Pre-configured GUI & CLI Tools**:
  - Capability-stripped `btop` for Android kernel compatibility.
  - Qt6 Wayland integration for `cmake-gui` and native desktop apps.
  - Terminal audio player `cliamp` with ALSA support.

---

## 📥 Releases & Downloads

- **[Magisk Module ZIP](releases/OmaLauncher-Magisk-Module.zip)**: System priv-app module for Magisk / KernelSU / APatch.
- **[Standalone APK](releases/OmaLauncher-v1.0.0-release.apk)**: Standard release APK (R8/ProGuard minified).

---

## 🔨 Building from Source

To build the APK and tests locally:

```bash
# Clone the repository
git clone https://github.com/thaliban04/Omalauncher-root.git
cd Omalauncher-root

# Fetch proot binaries (required once)
./scripts/fetch-proot.sh

# Run tests
./gradlew :owl:test :app:testDebugUnitTest

# Assemble Release APK
./gradlew :app:assembleRelease
```

---

## 📄 License

Omalauncher-Root is released under the **MIT License**. See [LICENSE](LICENSE) for details.
All third-party tools, themes, and agent trademarks belong to their respective owners (see [THIRD_PARTY.md](THIRD_PARTY.md)).
