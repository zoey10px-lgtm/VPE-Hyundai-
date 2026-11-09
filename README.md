# VPE Hyundai · Vehicle Persona Engine

**Latest:** v0.4.1 — Pleos host environment and permission diagnostics.

A Kotlin Android prototype that turns **verified vehicle readings** into a persistent personality and a voice-design blueprint. Visual design is a bright, mobile-first UI with a continuously animated custom orb/waveform.

## Version 0.4 · What works

- **Home:** animated 27-bar waveform and gradient orb, real-vehicle connection, connection progress, profile and data cards.
- **Conversation:** separate tab with chat bubbles, local message history, prompts, and rule-based replies.
- **Diagnostics:** separate tab with permission/query errors, actual SDK data reception status, retry, and clipboard copy.
- **Demo:** clearly labeled **시연용 데모 체험** button on ordinary Android smartphones; no manual sliders; demo data never saved as real vehicle readings.
- **Identity:** deterministic weighted traits, stable first-time voice *blueprint*, locally saved identity and event-gated long-term learning. Audio/STT/LLM have not been implemented.

## Pleos Vehicle SDK v2.3.2

### 0.4.1: Why a phone may show permission denied

The SDK library can be compiled into a standard Android APK, but **Pleos Vehicle Service runs in Pleos Connect on Android Automotive OS**, not on normal phones. The APK declares the SDK's read-only `CAR_DRIVE`, `CAR_ENERGY`, and `CAR_INFO` permissions. These are not Android runtime popup permissions. On a normal smartphone the system usually does not provide the Pleos permission definitions or Vehicle Service, so the SDK cannot supply real vehicle readings.

At app launch `VehicleEnvironmentInspector` now checks `FEATURE_AUTOMOTIVE`, the app manifest declarations, whether the host OS defines each permission, and whether each permission is actually granted. On phones the app shows an actionable Pleos connection guide and a **clearly labeled demo button**, without reporting a fake successful vehicle connection. The SDK client itself also checks the host before making any SDK requests. On Automotive, passing this preflight is a prerequisite, **not proof that Vehicle Service is connected**; actual SDK callbacks remain the proof.

To verify real vehicle readings, use the official [Pleos Connect Emulator installation guide](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator) and start the emulator with **Cold boot**. Confirm Vehicle Service is up, install the APK, and use the **진단** tab to inspect permission state and per-API callbacks.



This project resolves the official SDK AAR from Pleos Maven and uses **actual, compile-verified typed methods** rather than guessed method names or reflection.

| Domain | API | SDK result | Usage |
| --- | --- | --- | --- |
| Odometer | checkCurrentDistanceDrivenCapabilities → getCurrentDistanceDriven | Float? | Odometer, kilometers |
| Fuel | checkFuelPercentCapabilities → getFuelPercent | Int? | Fuel percentage |
| EvBattery | getEvBatteryLevel | Float? | Battery level if percentage representation is unambiguous |
| CarInfo | getVINInfo | String? | SHA-256-derived local car key, raw VIN not stored |

The manifest declares `pleos.car.permission.CAR_DRIVE`, `CAR_ENERGY`, and `CAR_INFO`. Every read has an asynchronous success/failure callback and separate permission-error reporting. Missing readings remain unavailable in the UI.

**Supported device context:** Vehicle Service runs in Pleos Connect on Android Automotive OS. A standard Android phone does not provide that service/privileged data. Actual vehicle-read success must be tested in a Pleos Connect emulator or authorized vehicle environment; Android APK compilation alone is not a proof of car data reception.

The SDK's public CarInfo data in this version exposes VIN and CRN, not a verified model year getter. Maintenance, exploration, and trip history are also not automatically available from the four APIs above. Neutral defaults are used for those personality weight inputs and described in UI. EV battery Float percent range needs validation with actual Pleos emulator responses; unambiguous values are used, raw uncertain values remain diagnostic only.

## Quick start

1. Open **Actions → Build VPE Android APK** and download the successful run's **VPE-Hyundai-debug-apk** artifact.
2. Unzip the artifact to get **app-debug.apk** and install it on Android 9+.
3. On a standard phone, use **시연용 데모 체험** to explore the new screens, animation, voice *profile*, and text conversation.
4. In the **Pleos Connect emulator** with Vehicle Service and relevant access permissions, choose **Pleos 차량 연결하기**; review the **진단** tab for real data and error details.
5. On a desktop, Android Studio with JDK 17 / Android SDK 35 can run the Gradle project.

## Validation

GitHub Actions workflow **Build VPE Android APK** runs:
- `gradle testDebugUnitTest assembleDebug`: nine automated regression tests including male/female voice profiles, deterministic outputs, learning, fuel/mood separation, and preservation of missing data.
- `javap` signatures against the resolved SDK `Vehicle-2.3.2.aar`.

Separate workflow **VPE Android UI Smoke** launches an Android 14 emulator and checks:
- app startup and visible home,
- two screenshots that show **changed animated waveform frames**,
- demo identity generation,
- independent chat navigation/message interaction,
- separate diagnostic screen,
- no observed fatal AndroidRuntime crash.

The UI smoke test additionally verifies the non-Automotive environment notice and the phone demo entry path. These are **Android UI and build checks**, not a substitute for Pleos Vehicle Service runtime integration testing.

## Code map

- `MainActivity.kt`: home, conversation, diagnostics; separated demo/real states
- `VpeWaveView.kt`: genuine Android Canvas / ValueAnimator animation (ambient; **not** microphone-driven)
- `PleosVehicleClient.kt`: typed SDK calls with capability checks, per-domain error handling
- `VehicleDataMapper.kt`: validates and maps only observed data
- `PersonaEngine.kt`: deterministic personality and mood
- `IdentityEngine.kt`: voice profile, long-term EMA learning, engine versioning
- `IdentityStore.kt`: local personality and voice profile persistence
- `PersonaEngineTest.kt`: JVM regression suite

## Design references

The navigation hierarchy, separate conversation and minimal diagnostics follow common modern mobile-app patterns and Android Material navigation principles. Rounded surfaces, wide touch controls, colorful gradient orb, and motion take inspiration from modern AI voice-assistant and automotive companion UX. The implementation is original Android code.

## Known next integrations

- Validate real Odometer / Fuel / EV / VIN values in a **Pleos Connect emulator**, including optional/missing-data and permission errors.
- Add trip and maintenance source adapters to replace neutral personality inputs.
- Connect an LLM for free-form responses and a voice engine for real audio/STT. The current waveform is a *visual animation only*, not synchronized to microphone or synthesized speech.
