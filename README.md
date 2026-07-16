<p align="left">
  <img src="icons/logo-bg.svg" alt="Spectre Logo" width="120" />
</p>

# Spectre

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Min SDK](https://img.shields.io/badge/minSdk-24%20%28Android%207.0%29-informational)](https://developer.android.com/about/versions/nougat)
[![License](https://img.shields.io/badge/license-GPLv3-blue)](LICENSE)
[![Release](https://img.shields.io/github/v/release/matepazy/spectre-app?include_prereleases&label=release)](https://github.com/matepazy/spectre-app/releases)

**Spectre** is an educational Android app that shows you exactly what your device reveals about itself to any app, without asking.

It collects over **60 distinct device signals** across hardware, software, network, and sensor layers, then computes a real-time fingerprint score so you can see how identifiable your device actually is.

---

## What it does

Every app you install can silently read a surprising amount of data from your device — before asking for a single permission. Spectre makes that invisible surface visible.

Open it, and within seconds you'll see:
- Which signals are **freely available** to any app, which require **user consent**, and which can be extracted via **side-channels**
- Real-time updates as sensors, battery state, and network conditions change

> Everything is computed **on-device**. No data is sent anywhere.

---

## Signal categories

Spectre organizes its signals into three tiers based on how an app would realistically obtain them:

| Tier | Label | What it means |
|---|---|---|
| 🟢 | **Passive** | Readable instantly by any app — no prompt, no permission, no warning |
| 🟡 | **Needs Permission** | Gated behind standard Android runtime permissions |
| 🔴 | **Advanced** | Side-channel queries, package installation footprints, and configuration inference |

### Signals monitored

| Domain | Examples |
|---|---|
| **System & Build** | OS version, SDK level, build fingerprint, bootloader string, kernel version, uptime |
| **Hardware & CPU** | Processor name, ABI list, core count, thermal state, display resolution & refresh rate, HDR & Widevine DRM level |
| **Memory & Storage** | Heap size, total/available RAM, low-memory state, internal storage, SD card presence, encrypted storage |
| **Battery & Power** | Charge level, health, voltage, temperature, technology, charging state, power saver mode |
| **Network & Comms** | Wi-Fi SSID & BSSID, signal strength, IPv4/IPv6, network interfaces, VPN state, Bluetooth scan |
| **Telephony & SIM** | Carrier name, SIM state, network type (5G/4G/3G/2G), MCC/MNC, roaming state |
| **Sensors** | Live readings from accelerometer, gyroscope, magnetometer, barometer, light, proximity |
| **Security** | Developer options, ADB state, root binary check, SELinux mode, safe mode, biometric hardware |
| **Installed Apps** | Side-channel social graph mapping via package presence (no `QUERY_ALL_PACKAGES` needed) |

---

## Getting the app

Spectre is **sideload-only** — it is not on the Play Store.

1. Go to [**Releases**](https://github.com/matepazy/spectre-app/releases)
2. Download the latest `.apk`
3. Enable *Install from unknown sources* for your browser or file manager
4. Install and open

The app checks GitHub Releases on launch and will notify you when a new version is available, with an in-app download and install flow.

---

## Technical overview

> This section is for developers and researchers interested in how Spectre is built.

### Architecture

```
┌──────────────────────────────────────────────────────────┐
│                        UI Layer                          │
│           Jetpack Compose  ·  Material Design 3          │
│   SpectreScreens.kt  —  dark theme, animated charts,    │
│   expandable signal cards, search/filter, export         │
└───────────────────────┬──────────────────────────────────┘
                        │ StateFlow / collectAsStateWithLifecycle
┌───────────────────────▼──────────────────────────────────┐
│                    ViewModel Layer                        │
│                   SpectreViewModel.kt                    │
│   Orchestrates scan lifecycle · debounces permission     │
│   requests · drives 2-second live polling coroutine      │
└──────────┬────────────────────────────┬──────────────────┘
           │                            │
┌──────────▼───────────┐   ┌────────────▼─────────────────┐
│   Signal Providers   │   │      Inference Engine        │
│ DeviceSignalProviders│   │  AppInferenceEngine.kt       │
│ .kt  (181 KB)        │   │                              │
│                      │   │  · Tracking risk score       │
│  Reads from:         │   │  · Uniqueness index          │
│  · Android APIs      │   │  · Active vulnerability      │
│  · System files      │   │    count (threatScore >= 7)  │
│  · Telephony mgr     │   │  · SHA-256 device signature  │
│  · Sensor mgr        │   │  · Fingerprint narrative     │
│  · PackageManager    │   └──────────────────────────────┘
└──────────────────────┘
           │
┌──────────▼───────────────────────────────────────────────┐
│                   Permission Layer                        │
│                   PermissionCenter.kt                    │
│   SDK-version-aware permission mapping · safe fallbacks  │
│   for Bluetooth (API 31+), notifications (API 33+),      │
│   and media storage (API 33+)                            │
└──────────────────────────────────────────────────────────┘
```

### Key design decisions

**Signal model** — Every signal is a `FingerprintSignal` with a `threatScore` (0–10), `SignalCategory` tier, optional `detailedData` sub-items, and a `sensitiveRawValue` that is SHA-256 hashed before being included in the device signature. This means the signature can be reproduced without exposing raw identifiers.

**Inference** — `AppInferenceEngine` computes scores locally using weighted signal counts: passive signals × 1.0, permission signals × 2.5, advanced signals × 2.0. The tracking risk score is the ratio of exposed threat points to total possible threat points, scaled to 0–100%.

**Permission handling** — `PermissionCenter` maps each permission to its safe runtime equivalent per SDK level. No API 31+ permission string is ever passed to `checkSelfPermission` on API 30 and below; instead it transparently falls back to the legacy coarse-location or Bluetooth path.

**Updates** — `VersionUpdater` polls `api.github.com/repos/matepazy/spectre-app/releases`, parses semantic versions, and downloads the APK asset directly into the app's cache directory. Installation is triggered via `FileProvider` + `ACTION_VIEW`.

### Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material Design 3 |
| State | `StateFlow` + structured coroutines |
| Blur effects | [Haze](https://github.com/chrisbanes/haze) |
| Networking | OkHttp + Retrofit |
| JSON | Moshi + KSP codegen |
| Local DB | Room |
| Biometrics | AndroidX Biometric |
| Tests | JUnit · Robolectric · Roborazzi |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 37 |

### Building from source

**Prerequisites**
- Android Studio Ladybug or newer
- JDK 17
- Android SDK 37

**Build debug APK**
```bash
./gradlew assembleDebug
```

**Run unit tests**
```bash
./gradlew :app:testDebugUnitTest
```

---

## Permissions

Spectre will request some permissions at runtime to unlock additional signals. Every permission request is optional — refusing one simply marks that signal as `Permission Blocked` rather than crashing or nagging again.

Permissions that may be requested:

| Permission | Signal unlocked |
|---|---|
| `READ_CONTACTS` | Contact list exposure threshold |
| `READ_PHONE_STATE` | IMEI, subscriber ID, telephony state |
| `ACCESS_FINE_LOCATION` | Precise GPS coordinates |
| `BLUETOOTH_CONNECT` (API 31+) | Paired Bluetooth device list |
| `BLUETOOTH_SCAN` (API 31+) | Nearby BLE beacon scan |
| `READ_CALL_LOG` | Call log metadata |
| `READ_SMS` | SMS metadata |
| `ACTIVITY_RECOGNITION` | Step count / motion state |
| `CAMERA` | Camera hardware characteristics |
| `RECORD_AUDIO` | Microphone hardware presence |

---

## Inspiration

Spectre was directly inspired by **[Loupe](https://github.com/mysk-research/loupe)** by Mysk Research, which performs an equivalent analysis on iOS. Spectre translates that concept to Android's broader, more fragmented ecosystem.

---

## License

GNU General Public License v3.0 — see [LICENSE](LICENSE) for full text.
