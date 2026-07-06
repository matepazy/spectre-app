# 👻 Spectre

**Spectre** is a diagnostic and forensic tool for Android designed to showcase and analyze the vast landscape of device characteristics, identifiers, build parameters, sensor telemetry, and hardware fingerprints that apps can read.

Inspired by the acclaimed iOS application **[Loupe](https://github.com/mysk-research/loupe)**, Spectre exposes how device fingerprints are constructed and evaluates potential privacy leakage across different Android versions.

---

## 💡 Origin & Inspiration
* **iOS Inspiration**: This application is heavily inspired by **Loupe** by Mysk Research. While Loupe demonstrates the richness of device signals available on iOS, **Spectre** translates this concept to the diverse, fragmented ecosystem of Android, highlighting build parameters, system services, and hardware characteristics.
* **AI Assistance**: Parts of this application were designed and implemented with the assistance of **Gemini 3.5 Flash**.

---

## 🔍 How the App Works

Spectre extracts hundreds of distinct device characteristics, organizing them into logical, high-visibility categories. It continuously polls or registers listeners to track dynamic signals, providing real-time telemetry.

### 1. Architectural Overview
The app follows modern Android development practices with a clean **MVVM (Model-View-ViewModel)** architecture:
* **UI Layer**: Built entirely in **Jetpack Compose** using Material Design 3. Features a dark-themed cybernetic user interface (`SpectreScreens.kt`) with animated charts, responsive search filtering, and categorized signal expandable list items.
* **State Management**: Orchestrated via a unified state repository and `SpectreViewModel.kt` utilizing Kotlin `StateFlow` and structured coroutines for non-blocking asynchronous hardware checks.
* **Signal Providers**: Modular collection engines (`DeviceSignalProviders.kt`) interacting with the Android System Services, Hardware Managers, Telephony Registry, and low-level File System.
* **Inference & Decision Engine**: A lightweight rule-based local heuristics engine (`InferenceAndPermissions.kt`) that analyzes the combination of gathered signals to score the device's overall fingerprint uniqueness, security posture, and custom anomalies.

---

## 🛠️ Deep Dive: The Signal Categories

The application probes various layers of the operating system:

| Category | Icon | Primary Signals Monitored |
|---|---|---|
| **System & Build** | `⚙️` | OS Version, SDK API Level, Build Fingerprint, Bootloader, Kernel Version, System Uptime, Local Time/Timezone. |
| **Hardware & CPU** | `🖥️` | Processor Name, ABI Support, CPU Cores, Thermal Throttling State, Display Resolution, Refresh Rate, HDR support, DRM Capabilities (Widevine levels). |
| **Storage & Memory**| `💾` | Heap Size, Total/Available RAM, Low Memory State, Internal Storage Capacity, External SD Card Presence, Encrypted Storage support. |
| **Battery & Power** | `🔋` | Charging Status, Health, Voltage, Battery Temperature, Technology (Li-Ion, Polymer), Battery Level, Power Saver Mode. |
| **Network & Comms** | `🌐` | Wi-Fi SSID, BSSID, Signal Strength, IPv4/IPv6 Addresses, Network Interfaces, VPN active status, Bluetooth Scan/State. |
| **Telephony & SIM** | `📱` | Mobile Carrier, SIM State, Network Type (5G/4G/3G), Mobile Country Code (MCC), Mobile Network Code (MNC), Roaming status. |
| **Environment & Sensors** | `🌡️` | Real-time values from Accelerometer, Gyroscope, Magnetometer, Barometer, Light Sensor, Proximity Sensor. |
| **Security & Privacy** | `🛡️` | Developer Options status, USB Debugging (ADB) active state, Root (SU) Binary checks, SELinux enforcing state, Safe Mode, Biometric Hardware availability. |

---

## 🛡️ Robust Permission Gate & Compatibility Handling

Requesting sensitive system identifiers on modern Android versions (Android 11, 12, 13, 14, 15, and 16) is highly restricted and subject to runtime permissions. 

Spectre features a **highly robust, crash-free permission handling engine** to gracefully extract information:
1. **Dynamic Permission Mapping**: If a particular signal requires permission, it is listed as `NEEDS_PERMISSION` and provides a safe prompt to request it.
2. **SDK Version Guarding**: Permissions such as `POST_NOTIFICATIONS` (SDK 33+), `BLUETOOTH_CONNECT`/`BLUETOOTH_SCAN` (SDK 31+), and media permissions are dynamically adapted depending on the host device's actual SDK level. Attempting to request API 31+ permissions on older devices will gracefully map to backward-compatible fallback checks (e.g. `ACCESS_COARSE_LOCATION` or Bluetooth legacy API) rather than crashing the process.
3. **Graceful Failbacks**: If a permission request is rejected or is unavailable on the hardware, the app avoids runtime exceptions by logging a safe fallback value (e.g. `"PERMISSION_DENIED"` or `"NOT_AVAILABLE"`) and allows the scanning flow to continue uninterrupted.

---

## 📤 Features
* **Live Signal Polling**: Signals are polled dynamically every 2 seconds, ensuring the battery temperature, RAM usage, and sensor states reflect immediate reality.
* **Local Identity Scoring**: Computes a "Fingerprint Security Score" and "Uniqueness Score" indicating how distinct or identifiable your device configuration is.
* **Search & Filter**: Find specific parameters instantly using an integrated high-performance text-based search bar.
* **Export Diagnostics**: Export the entire scanned payload as a formatted JSON report or copy it directly to your clipboard for security audit purposes.

---

## 🏗️ Development & Compilation

To compile and run the application locally:

### Prerequisites
* Android Studio (Ladybug or newer)
* Android SDK 36 (or compatible)
* JDK 17

### Commands
Build the debug APK:
```bash
gradle assembleDebug
```

Run unit tests and local Robolectric verification:
```bash
gradle :app:testDebugUnitTest
```
