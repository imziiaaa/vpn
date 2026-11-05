# VeilGate Android VPN Client

A stealth, frictionless Native Android VPN client built with **Kotlin** and **Jetpack Compose**, integrated directly with the **VeilGate Control Plane**.

---

## Key Features

1. **Zero-Auth / Frictionless User Experience**:
   - No login, password, or registration required.
   - Automatically generates and securely persists an anonymous, hardware-based `deviceId` (UUID) in local storage.

2. **Strict Dual-Limit Session Enforcement**:
   - **1-Hour Time Limit**: Enforces an active countdown timer (3,600 seconds). Automatically tears down the tunnel and notifies the user when the hour expires.
   - **1-GB Data Quota**: Continuously monitors byte counters (`rx_bytes + tx_bytes`). Automatically terminates the connection once 1,073,741,824 bytes (1,024 MB) have been transferred.
   - **Whichever comes first** triggers automatic disconnection with informative banner warnings.

3. **Dynamic Server Provisioning**:
   - Real-time server picker fetching active AmneziaWG VPN nodes from `GET /api/app/servers`.
   - On connection, sends device ID, selected node, and platform metadata to `POST /api/app/connect`.
   - VeilGate Control Plane dynamically allocates an ephemeral peer and generates obfuscated AmneziaWG configuration (`Jc`, `Jmin`, `Jmax`, `S1`, `S2`, `H1-H4`).

4. **Stealth & Cyberpunk UI**:
   - Built entirely in Jetpack Compose with Material 3.
   - Live dual progress meters tracking remaining session time and quota consumed.
   - Glowing pulsing connection ring.
   - Ongoing Foreground Notification with live countdown timer and data counter, plus a quick "Disconnect" action.

---

## Configuration & Security

To keep your deployment credentials safe, never hardcode private API keys or live endpoints in version control.

Add your environment settings to `local.properties` (which is excluded from Git):

```properties
sdk.dir=/path/to/your/android/sdk
VEILGATE_BASE_URL=https://your-control-plane-domain.com/
VEILGATE_APP_KEY=your_private_app_api_key
```

When building, Gradle reads these properties and injects them into `BuildConfig` at compile-time. If not provided, fallback defaults are used, allowing users to enter custom endpoints via the in-app Settings dialog.

---

## Project Structure

```
Vpn/
├── app/
│   ├── build.gradle.kts
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/veilgate/vpn/
│   │   │   ├── VeilGateApp.kt           # Application init & notification channels
│   │   │   ├── data/
│   │   │   │   ├── model/Models.kt      # Data models & API contracts
│   │   │   │   ├── model/VpnState.kt    # Connection status & limit reasons
│   │   │   │   ├── network/VeilGateApi.kt # Retrofit API endpoints
│   │   │   │   ├── network/NetworkClient.kt # OkHttp client with X-App-Key header
│   │   │   │   └── repository/
│   │   │   │       ├── DeviceManager.kt # Anonymous device ID generator
│   │   │   │       └── VpnRepository.kt # Data coordinator
│   │   │   ├── service/
│   │   │   │   ├── SessionLimits.kt     # 3600s & 1GB formatting and constants
│   │   │   │   ├── AmneziaConfigBuilder.kt # UAPI obfuscation parameters converter
│   │   │   │   └── VeilVpnService.kt    # Android VpnService & dual limit engine
│   │   │   └── ui/
│   │   │       ├── MainActivity.kt      # Activity & VpnService permission launcher
│   │   │       ├── theme/               # Dark cyberpunk theme & typography
│   │   │       ├── viewmodel/MainViewModel.kt # State management
│   │   │       └── screens/
│   │   │           ├── MainScreen.kt    # Dashboard with power button & meters
│   │   │           ├── ServerPickerSheet.kt # Server selector
│   │   │           └── SettingsDialog.kt # Base URL and App Key settings
│   │   └── res/
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/
```

---

## How to Build & Run

1. Open this project root in **Android Studio**.
2. Sync the project with Gradle files.
3. Select an Android emulator or physical device running Android 8.0+ (API 26 to 35).
4. Click **Run** (`Control + R` on Mac or `Shift + F10`).
