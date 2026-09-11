# Folder Share

> **Zero-touch local file sharing and seekable media streaming across PC and Android.**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![GitHub Releases](https://img.shields.io/github/v/release/hammadbawara/FolderShare?logo=github&label=Release)](https://github.com/hammadbawara/FolderShare/releases)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Linux%20%7C%20Windows-lightgrey)]()

---

**Folder Share** turns any selected folder on your computer or phone into an instant local network share. No cloud uploads, no USB cables, and no account setup required.

Browse directories, download files at full Wi-Fi speeds, or stream large video and audio files instantly with smooth seeking.

---

## ✨ Features

- **⚡ Zero Setup**: Automatic local peer discovery via mDNS — start sharing in one click.
- **🎬 Seekable Media Streaming**: Watch high-definition videos or listen to music without downloading the full file first (HTTP 206 Partial Content support).
- **🌐 WebDAV & Browser Access**: Connect seamlessly from any web browser or native OS file manager (Windows Explorer, macOS Finder, Linux Files).
- **🔒 100% Private**: Your data never leaves your local Wi-Fi or LAN. No analytics, tracking, or cloud relays.
- **📱 Multiplatform**: Modern, unified Compose Multiplatform UI across Android and Desktop.

---

## 🚀 How It Works

```text
[ Device A (PC / Android) ]  ──( Wi-Fi / LAN )──>  [ Device B (Phone / PC / TV) ]
      Select Folder                                      Stream or Download
```

1. **Pick a Folder:** Open the app on your host device and select the folder you want to share.
2. **Discover or Connect:** Other devices on the same Wi-Fi detect the share automatically, or you can open the provided local URL in any browser.
3. **Browse & Enjoy:** Download documents or play videos directly with real-time seeking.

---

## 📥 Downloads

| Platform | Download Link | Notes |
| :--- | :--- | :--- |
| **Android** | [GitHub Releases](https://github.com/hammadbawara/FolderShare/releases) | Requires Android 8.0+ |
| **Linux** | [GitHub Releases (.tar.gz / deb)](https://github.com/hammadbawara/FolderShare/releases) | Standalone package |
| **Windows** | [GitHub Releases (.msi / .zip)](https://github.com/hammadbawara/FolderShare/releases) | Standalone installer & portable |

*Visit the [Official Website](https://hammadbawara.github.io/FolderShare/) for additional documentation.*

---

<details>
<summary>🛠️ <b>Building from Source & Development</b></summary>

### Prerequisites
- JDK 17 or higher
- Android SDK (for Android build)

### Build Commands

```bash
# Clone the repository
git clone https://github.com/hammadbawara/FolderShare.git
cd FolderShare

# Run Desktop app
./gradlew :desktopApp:run

# Run Desktop app with hot-reload
./gradlew :desktopApp:hotRun --auto

# Build Android debug APK
./gradlew :androidApp:assembleDebug

# Build Android release APK
./gradlew :androidApp:assembleRelease

# Run all unit tests
./gradlew test
```

</details>

<details>
<summary>🏗️ <b>Architecture & Tech Stack</b></summary>

Folder Share is built with Kotlin Multiplatform (KMP) following clean layering and SOLID principles:

- **UI:** Compose Multiplatform (Material 3) with passive UI observing `StateFlow`.
- **Architecture:** MVI / MVVM with single-source-of-truth immutable UI states.
- **Server & Networking:** Ktor (HTTP & WebDAV server engine with byte-range support).
- **Service Discovery:** Platform-native mDNS (NSD / jmDNS).
- **Database & Storage:** Room KMP (`AppDatabase`) and Okio file system abstractions.
- **Dependency Injection:** Koin.

</details>

<details>
<summary>📄 <b>License & Contributing</b></summary>

- **License:** Distributed under the [GNU General Public License v3.0](LICENSE).
- **Contributions:** Pull requests and bug reports are welcome! Please open an issue to discuss proposed changes first.

</details>