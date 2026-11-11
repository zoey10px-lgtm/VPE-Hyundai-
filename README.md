# VPE Hyundai · Vehicle Persona Engine

**Latest: v1.0.0 — accounts, cloud-ready multi-vehicle profiles and GPT personality**

New in v1.0.0:
- Login/register UI for individual VPE accounts (passwords hashed on server, session tokens encrypted on Android).
- Full vehicle HPE report and snapshot ready for PostgreSQL persistence; saved vehicle list and manual display names.
- Chat history keyed by **account + vehicle** both locally and in PostgreSQL, preventing conversation mixing.
- GPT expression rules use HPE-computed MBTI, all 7 long-term traits, current mood and the vehicle's name. The server never invents a new HPE identity.
- Separate vehicle personality report screen with a 7-axis radar chart.
- **Backend database activation pending:** Render workspace already has an active free PostgreSQL database for another project. Only one free DB can be active. See [server database setup](server/README.md) before using registration/cloud sync. Old APK local/demo flows remain available.

### Architecture

`Pleos Vehicle SDK → HPE traits/memory → VPE Android → Render Node API → PostgreSQL (account/vehicle/chat) + OpenAI Responses API`

The existing app's HPE engine and Pleos SDK bridge are preserved. New PostgreSQL tables are created automatically once a dedicated `DATABASE_URL` is configured and Render installs the `pg` dependency.

---

**Historical release: v0.6.0 — Human Personality Engine 1.0**

Six-stage **psychology-inspired vehicle persona engine**: actual Pleos signals → features → deterministic MBTI and 7 long-term traits → observed experience/memory → current needs and cognitive appraisal → expression. MBTI is a fictional character label, not a validated human psychometric inference.

**[Read the full HPE architecture, formulas, inputs, SDK status and acceptance tests](docs/HPE-ARCHITECTURE.md).**

**[SDK 실측/미수신 데이터 연결 표 및 성격 알고리즘 수정 위치](docs/SDK-DATA-MATRIX.md)** (2026-11-11 갱신).

### New in v0.6.0

- **MBTI** (continuous E/N/T/J axes) replaces the displayed voice age group; feminine/masculine voice blueprint identity remains.
- Seven long-term traits and eight emotional/need dimensions, with missing signals distinguished from real observations.
- Separately persisted and slowly adapting HPE identity, event-gated narrative memory, no repeated sensor-poll learning.
- Personality-conditioned expression and chat instead of a static mood response. No unsolicited driving speech.
- New real Pleos getter/listener adapters for driver **actual cabin temperature**, **target HVAC temperature**, **HVAC power**, and getter for **EV charge switch**. Vehicle-specific callback success and permissions must be confirmed in diagnostics.
- Deterministic Kotlin unit tests for MBTI, experience, battery, charging and temperature behavior; CI builds an Android APK.

### Verified vs pending

Previously verified on the official Pleos AVD: Odometer, EV SOC, hashed VIN identity. Newly added HVAC and charging switch getters are compile-tested and still require on-device verification. Location/route history, maintenance, gear and warnings are planned optional adapters, and unavailable values remain explicitly unknown. This app is not an autonomous driving assistant. Voice synthesis/STT/LLM are not part of this release.

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

## Real Pleos data reception — acceptance test

**Successful APK compilation and ordinary Android emulator UI tests are not proof of Vehicle Service reception.** For the VPE SDK integration to pass, the app must receive at least one actual numeric `ODO_OK: km=<number>` callback on the **official Pleos Connect v2.1.0 API 34 Automotive emulator**, not from the fixed UI/demo fixtures. `FUEL_OK: percent=<number>`, `EV_OK: percent=<number>`, and `VIN_OK`/`CRN_OK` provide independent evidence for energy and vehicle identity. A capability/listener-registration message alone is **not** a data receipt.

### Windows / Android Studio verification

1. Install the official Pleos Connect system image from `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml` under **SDK Manager → SDK Update Sites**.
2. Create an **Automotive** AVD with **Pleos Connect v2.1.0 APIs, API 34**, **Cold boot**, **4 GB RAM**. The official Pleos emulator documentation requires a discrete graphics card; standard GitHub Actions runners may fail to boot it.
3. Boot that AVD and wait until the vehicle UI is fully loaded. Use Android Studio terminal / PowerShell from this repository's root:

   ```powershell
   gradle assembleDebug
   powershell -ExecutionPolicy Bypass -File tools/verify-pleos.ps1 -ApkPath app/build/outputs/apk/debug/app-debug.apk -WatchSeconds 60
   ```

   The verifier installs the real APK, confirms Android Automotive, opens VPE, taps **Pleos 차량 연결하기**, collects `VPE_PLEOS` logs under `pleos-evidence/`, and asserts a **numeric odometer callback**. It never fabricates SDK responses.

4. To test *changing* values in Vehicle Service, forward `adb forward tcp:8088 tcp:8088`, visit `http://127.0.0.1:8088/` on the host browser and use **Motion → Driving Distance** or **Energy** controls while the `-WatchSeconds` observation runs. The logs must include `ODO_UPDATE: km=` or `FUEL_UPDATE: percent=` / `EV_SOC_UPDATE: percent=`. These are the actual registered Pleos listeners.
5. Inspect `pleos-evidence/sdk-callbacks.txt`. `ODO_ERROR` or `PERMISSION_DENIED` is a real failure to investigate, **not** a successful integration. Do not use the app's demo button for acceptance.

### SDK integration implemented

- Typed `Vehicle.getOdometer().getCurrentDistanceDriven(...)` and `registerDistanceDriven(...)`.
- Typed `getFuelPercent(...)` and `registerFuelPercent(...)`.
- Typed `getEvBatteryStateOfCharge(...)` and `registerEvBatteryStateOfCharge(...)`.
- VIN read with CRN fallback (SHA-256-derived identifier, original not logged or saved).
- Successful numeric SDK readings feed `VehicleDataMapper` and `IdentityEngine`. Sensor updates persist after initial connection, with long-term memory changing only at the engine's thresholds.
- Domain failures are independent; a missing CarInfo permission no longer suppresses driving/energy callbacks.
- `VehicleEnvironmentInspector` separates a regular phone from the Pleos Automotive host; it is **not** itself proof of Vehicle Service connectivity.

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
