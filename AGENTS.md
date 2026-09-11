# AGENTS.md — Folder Share Development Guide

## Overview & Architecture
**Folder Share** is a zero-touch local network folder sharing app for **Android** and **Linux (Desktop)** built with **Kotlin Multiplatform (KMP)** and **Compose Multiplatform**. It exposes selected local directories via HTTP/WebDAV with support for seekable media streaming (HTTP 206 Partial Content).

### Project Layout
- `:shared` — Core KMP logic (`commonMain`, `androidMain`, `jvmMain`, `commonTest`, `jvmTest`).
- `:androidApp` — Android target launcher and configuration.
- `:desktopApp` — Desktop JVM / Linux target launcher.

---

## Architectural Patterns & SOLID Principles

### 1. Clean Layering & State Management
- **UI (Compose Multiplatform):** Passive UI observing `StateFlow` from ViewModels.
- **ViewModel (MVI/MVVM):** Holds single-source-of-truth immutable `UiState` data classes.
- **Domain & Repository Layer:** Encapsulates business logic (`RemoteFileRepository`, `VirtualFileSystem`, `DeviceDiscoveryEngine`).
- **Data & Platform Layer:** Ktor WebDAV server/client, Room KMP database (`AppDatabase`), Okio file system, platform mDNS.

### 2. Dependency Injection (Koin)
- Inject dependencies via interface abstractions, registered in `di/Koin.kt` (`coreModule` or `viewModelModule`).
- ViewModels must depend on abstractions (e.g., `RemoteFileRepository`, `ServiceBrowser`), never concrete platform implementations.

---

## Technical Constraints & Protocols

1. **Seekable Streaming (HTTP 206):** Server routes must handle HTTP 206 Byte-Range headers to enable fast media scrubbing without buffering full files.
2. **WebDAV Conformance:** Keep directory listings and HTTP methods (`PROPFIND`, `GET`, `PUT`, `DELETE`, `MKCOL`) spec-compliant for standard WebDAV client compatibility.
3. **Non-Blocking Concurrency:** All network, database, and file IO operations must use Kotlin Coroutines on appropriate dispatchers (`Dispatchers.IO`) without blocking the main/UI thread.

---

## Verification & Build Commands

- `./gradlew test` — Run all unit tests across common & target source sets.
- `./gradlew desktopApp:run` — Launch Linux/Desktop application.
- `./gradlew androidApp:assembleDebug` — Build Android debug APK.