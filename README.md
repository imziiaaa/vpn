# Raven VPN for Android

Raven VPN is a Kotlin and Jetpack Compose app that uses Android `VpnService` and the bundled AmneziaWG userspace engine.

## Backend contract status

This workspace needs `ANDROID_HANDOFF.md` before it can connect to the deployed mobile API. The available control-plane snapshot at commit `16e8a38` uses a shared App API Key for `/api/app/servers`, `/api/app/connect`, `/api/app/session`, and `/api/app/disconnect`. Its disconnect request identifies a device and node. The requested mobile flow needs enrollment, refresh, authenticated listing, idempotent session creation, and disconnect by session ID.

The Android client has interfaces for those operations, a Keystore credential vault bound to the control plane's HTTPS origin, serialized token rotation, and a journal for server-issued session IDs. The production HTTP adapter is pending the versioned routes, payloads, auth and error rules, and lease recovery semantics. Until those are supplied, accepting the disclosure opens the live picker with an actionable service-unavailable error. The previous App API Key flow has been removed.

The disclosure has preliminary text for traffic routing, DNS, and session limits. Restore an in-app privacy link using the verified public policy URL and reconcile the text with the deployed operator's data inventory, including collection, sharing, logging, and retention, before distributing the app.

## Android connection lifecycle

The home screen opens the disclosure before server access. Accepting it records version 2 and loads the picker. Choosing a node and tapping Protect then starts Android's VPN permission flow. On upgrades the app migrates the previous node preference and retires the saved endpoint and key.

The service starts from the visible, user-initiated Protect flow after Android VPN authorization. On Android 14 and higher the manifest and runtime specify `systemExempted`, the type Android provides for authorized VPN apps. AOSP checks the VPN activation app op for this type. This foreground service establishes and maintains the encrypted tunnel when Raven VPN is in the background. Its persistent notification shows the connection state and offers a Disconnect action when notifications are permitted. The service is non-exported and requires `android.permission.BIND_VPN_SERVICE`. [Android foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types) · [AOSP type policy](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/ForegroundServiceTypePolicy.java)

The client validates numeric tunnel addresses, endpoints, keys, and routes before setting up the TUN. The legacy snapshot supplies an IPv4 lease address and advertises both default routes. Raven VPN enables only families with a provisioned interface address. Android blocks unconfigured families. A bounded UDP DNS probe to a provisioned resolver, bound to the VPN network, and a recent AmneziaWG handshake gate the Connected state. Confirm the mobile contract's address families and test leak behavior on staging.

The client keeps a canceled provisioning request alive long enough to release a lease from its late response. It retries failed releases for known session IDs and attempts orphan recovery on a later launch. Recovery from a process death or lost response before a session ID was received requires the backend's idempotency and lease-expiry contract.

## Build and signing

Use JDK 21, Android SDK 36 with Build Tools 36.0.0, AGP 8.10.1, and the Gradle 8.11.1 wrapper.

Copy `local.properties.example` to an ignored `local.properties` and set `sdk.dir`, or set `ANDROID_HOME`. Provide all four `RAVEN_UPLOAD_*` settings through the ignored file or CI secrets to sign with the upload key registered for `com.veilgate.vpn`. Gradle produces unsigned release artifacts when all four settings are absent. The release build uses R8 and preserves the AmneziaWG JNI entry points.

```sh
./gradlew assembleRelease bundleRelease lintRelease testReleaseUnitTest
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab
keytool -printcert -jarfile app/build/outputs/bundle/release/app-release.aab
bundletool validate --bundle=app/build/outputs/bundle/release/app-release.aab
bundletool dump manifest --bundle=app/build/outputs/bundle/release/app-release.aab --module=base
bundletool dump config --bundle=app/build/outputs/bundle/release/app-release.aab
```

Compare the AAB signing certificate SHA-256 to the **upload** certificate in Play Console. Confirm the merged manifest reports target SDK 36, `BIND_VPN_SERVICE`, `exported=false`, and `foregroundServiceType=systemExempted`. Check all bundled ELF `LOAD` segments and use bundletool to generate an APK set from the AAB. Extract the APK and run `zipalign -c -P 16 -v 4` on it. Bundle config should report `PAGE_ALIGNMENT_16K`. [Android 16 KB verification](https://developer.android.com/guide/practices/page-sizes)

## Play Console handoff

- Confirm the app ID and next unused version code. Register or confirm the upload certificate under Play App Signing and sign the AAB using the registered upload key.
- Submit the VpnService and foreground service declarations in App content. Describe the user-started tunnel in the store listing and attach a demo of the disclosure, connection, and notification's Disconnect action. [VpnService policy](https://support.google.com/googleplay/android-developer/answer/12564964) · [Foreground service declarations](https://support.google.com/googleplay/android-developer/answer/13392821)
- Publish the verified privacy policy in the app and Play Console. Complete Data safety using the actual app, control plane, VPN node, DNS, and vendor data flows. [App content](https://support.google.com/googleplay/android-developer/answer/9859455) · [Data safety form](https://support.google.com/googleplay/android-developer/answer/10787469)
- Verify the signed minified release on fresh Android 15 and 16 installations, including a 16 KB runtime and real staging tunnels, then test the signed upload in an internal Play track.

### Suggested reviewer instructions after mobile access is live

Open Raven VPN. Tap **Review VPN disclosure** and read the notice. Tap **Agree and load nodes**, wait for the list, and choose an available node. Tap **Protect** and approve Android's VPN connection request. Allow notifications if prompted so the Disconnect action is visible. Wait until Raven VPN shows **Connected**, then use **Disconnect** in the ongoing notification. To check the opt-out path, start with a fresh app install, open the disclosure, and tap **Decline** or press Back. You can open the disclosure again from the home screen. Access uses the same enrollment flow for all users.

## Changed files

- Onboarding and picker in `MainActivity.kt`, `MainViewModel.kt`, `VpnState.kt`, `MainScreen.kt`, `ServerPickerSheet.kt`, `VpnDisclosureDialog.kt`, and `strings.xml`. Removed the legacy `SettingsDialog.kt` key entry.
- Mobile client interfaces, storage, and cleanup in `Models.kt`, `VeilGateApi.kt`, `NetworkClient.kt`, `CredentialVault.kt`, `DeviceManager.kt`, and `VpnRepository.kt`.
- Tunnel and notification lifecycle in `VeilVpnService.kt`, `AmneziaConfigBuilder.kt`, `NativeTunnel.kt`, `TunnelHealth.kt`, and `VeilGateApp.kt`.
- Android configuration and release inputs in `AndroidManifest.xml`, `data_extraction_rules.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `gradle-wrapper.properties`, `local.properties.example`, and `.gitignore`.
- Regression tests in `AmneziaConfigBuilderTest.kt` and `NetworkClientTest.kt`.

## Verification on 2026-11-22

| Device | Page size | Observed result |
| --- | --- | --- |
| Android 14 emulator · API 34 | 4,096 bytes | Isolated minified smoke build. Decline and Back returned home without requesting Android VPN permission. Accept opened the picker, which reported that mobile access was unavailable. Refresh and relaunch retained consent and the usable picker. The packaged ARM64 AmneziaWG library loaded and returned `v3.1.20260814` through JNI. |
| Android 15 · API 35 | Pending | The emulator image download was stopped at the owner's request. |
| Android 16 · API 36 | Pending, including a 16 KB device | The emulator image download was stopped at the owner's request. |

Seven JUnit tests passed for endpoint, address and key validation, AmneziaWG parameter preservation, HTTPS origin enforcement, and redirect handling. The missing mobile contract and staging access blocked a successful server fetch, node selection from live results, OS permission denial during a real connection flow, connect, immediate cancel, background provisioning, notification disconnect, revocation, and IPv4/IPv6 leak testing. Those cases still need the signed, minified staging build on fresh installs.

The configured AGP 8.10.1 and Gradle 8.11.1 build used the offline cache after separate dependency retrieval. The configured lint analyzer's four JARs timed out during download, so the successful artifact run excluded the three lint-vital tasks. A separate `lintRelease` run on the same production sources in an isolated AGP 8.7.2 compatibility checkout passed with 0 errors and 26 warnings. AGP 8.7.2 is below Android's supported toolchain minimum for API 36.

| Command or check | Result |
| --- | --- |
| `assembleRelease bundleRelease --offline -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease` | Passed on AGP 8.10.1 with target SDK 36 and R8 enabled. |
| `lintRelease` | Blocked on AGP 8.10.1 by unavailable lint, lint-api, IntelliJ core and Kotlin compiler 31.10.1 JARs. The isolated cached-toolchain lint run passed. |
| `testReleaseUnitTest` | Passed on AGP 8.10.1 with all seven tests. JUnit was supplied from the offline cache. |
| `jarsigner -verify -verbose -certs` | Reported an unsigned AAB. The registered Play upload key was unavailable. |
| `bundletool validate` and `bundletool dump manifest` | Passed. The bundle reports target SDK 36, `BIND_VPN_SERVICE`, `exported=false`, and `systemExempted` (`0x00000400`). |
| `bundletool dump config`, `build-apks` and `zipalign -c -P 16 -v 4` | Passed. The AAB requests `PAGE_ALIGNMENT_16K`, and the generated universal APK passed alignment verification. Its signing key was used for local QA. |
| ELF and minified DEX inspection | All 16 bundled libraries, including the four AndroidX graphics binaries, passed 16 KB load-segment checks in the AAB and APK. The minified DEX retained `org.amnezia.awg.GoBackend` and its six native methods. |

The unsigned AAB at `app/build/outputs/bundle/release/app-release.aab` has SHA-256 `051be14c2a79b9f2f3796fb9df557d316c4aa2ffabe338e1ccc68d5096525333`.
