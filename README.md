# VPE Hyundai · Vehicle Persona Engine

## 최신 버전 · v1.5.7

차량 데이터를 캐릭터 성향으로 변환하고, 차량 이름과 성격을 반영해 대화하는 Android 앱입니다.

- 성향 카드 선택 → 실제 계산에 사용된 입력값과 항목별 기여도(%p)를 표시합니다. 서버가 제공한 계산 결과를 사용하며, 미수신 항목은 수신값으로 표시하지 않습니다.
- MBTI 4개 축, 7가지 성향, 16개 세부 성향의 분석 근거를 확인할 수 있습니다.
- 차량 프로필에서 이름을 설정합니다. 미설정 시 **내 차**를 사용하고, 리포트 제목과 답변 대기 문구에도 적용합니다.
- 실시간 갱신 시 스크롤 위치를 유지하고, 드래그 중 화면 교체를 지연합니다.
- 성격 산출 방식과 서버 API는 그대로 사용합니다. 이번 배포는 Android UI 변경입니다.

## 담당자용 개발·시연 환경

| 항목 | 프로젝트 설정 / 재현 기준 |
| --- | --- |
| 개발 호스트 | Windows + Android Studio, JDK 17 |
| Android 앱 | Kotlin 2.0.21 / Android Gradle Plugin 8.7.3 |
| Gradle | 8.9 (GitHub Actions 빌드 기준) |
| 앱 SDK | compile / target 35, min 28 (Android 9) |
| 차량 SDK | `ai.pleos.playground:Vehicle:2.3.2` |
| 차량 시연 시스템 이미지 | **Pleos Connect v2.1.0 APIs / Android 14 / API 34 / x86_64 / Automotive** |
| AVD 생성 기준 | `automotive_1024p_landscape`, RAM 4096 MB, 가로 방향, Cold Boot |
| 재현용 화면 기준 | **1920 × 1080 px / 160 dpi** — 프로젝트에서 지정하는 시연 설정. 아래 명령으로 적용 |
| Android Emulator 실행 파일 버전 | 설치된 버전은 `capture-demo-environment.ps1`이 `emulator -version`으로 기록 |
| 사용자 PC의 실제 해상도·밀도 | 스크린샷 크기로 추정하지 않음. 같은 스크립트가 `wm size`, `wm density`의 물리값·override 값을 기록 |
| CI UI 검증 환경 | 일반 Android 14 / API 34 / Google APIs / x86_64 / Pixel 6. 차량 SDK 시연용 AVD와 별도 |
| 백엔드 | Node.js 20 이상 / Render / PostgreSQL. 앱 기본 주소 `https://vpe-hyundai.onrender.com` |
| 앱 식별자 | `com.vpe.hyundai` / `com.vpe.hyundai/.MainActivity` |

### Pleos AVD 준비와 화면 재현

1. Android Studio → SDK Manager → SDK Update Sites에 공식 이미지 저장소를 추가합니다: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`.
2. **Pleos Connect v2.1.0 APIs / API 34 / x86_64** 시스템 이미지를 설치합니다. Automotive AVD를 만들고 가로 화면·RAM 4 GB를 선택합니다. 저장소 자동화에서 사용하는 장치 프로필은 `automotive_1024p_landscape`이며, AVD 이름은 자유롭게 정할 수 있습니다.
3. **Cold Boot**로 시작한 뒤 차량 화면이 완전히 열릴 때까지 기다립니다. 일반 Google APIs 이미지에는 Pleos Vehicle Service가 없습니다.
4. 프로젝트 루트 PowerShell에서 아래 명령을 실행하면 시연 화면 규격을 적용하고, 실제 Emulator 버전·AVD 이름·OS·ABI·화면 크기·밀도·앱 버전을 파일로 기록합니다.

```powershell
powershell -ExecutionPolicy Bypass -File tools/capture-demo-environment.ps1 -ApplyDemoDisplay
```

결과 파일: `pleos-evidence/environment.txt`. 기존 환경만 기록하려면 `-ApplyDemoDisplay`를 빼고 실행합니다. 화면 override를 되돌리려면 다음을 실행합니다.

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb -e shell wm size reset
& $adb -e shell wm density reset
```

### APK 설치와 담당자 시연 순서

1. **Actions → Build VPE Android APK**의 최신 성공 실행에서 **VPE-Hyundai-debug-apk**를 다운로드하고 압축을 풉니다.
2. Pleos 에뮬레이터 하나를 켠 상태에서 설치·실행합니다. `-r`은 기존 로그인과 차량 이름을 유지합니다.

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb -e install -r .\app-debug.apk
& $adb -e shell am start -W -n com.vpe.hyundai/.MainActivity
```

3. 앱에서 회원가입/로그인 → **Pleos 차량 연결하기** → 리포트 진입을 진행합니다. **진단** 탭에서 실제 SDK 수신 상태를 확인할 수 있습니다.
4. **차량 프로필 → 이름 변경**에서 이름을 저장합니다. 상단 제목에 이름이 반영되는지 확인합니다.
5. 리포트에서 **호기심** 등 성향 카드를 선택합니다. 수신된 입력값, 점수를 높이거나 낮추는 기여도, 기본 성향을 확인합니다. 미수신 항목은 근거 목록에서 제외됩니다.
6. 대화 탭에서 메시지를 보내고 **‘이름이/가 답변을 생각하고 있어요…’** 문구와 응답을 확인합니다. 이름 미설정 시 **‘내 차가 답변을 생각하고 있어요…’**로 표시됩니다.
7. SDK 값 변경 콘솔: `adb -e forward tcp:8088 tcp:8088` 실행 후 `http://127.0.0.1:8088/`에 접속합니다. 에너지·온도·동승자·크루즈 등 지원되는 항목을 변경한 뒤 앱에서 리포트와 분석 근거를 확인합니다.

일반 Android 기기에서는 홈의 시연 모드를 사용할 수 있습니다. 이는 고정 시연 데이터이며 실제 SDK 수신 시연과 구분됩니다.

### 빌드 및 검증

```text
gradle --no-daemon testDebugUnitTest assembleDebug assembleDebugAndroidTest
```

`Build VPE Android APK`는 JVM 회귀 테스트와 APK 빌드를 실행합니다. `VPE Android UI Smoke`는 에뮬레이터에서 스크롤 유지, 드래그 중 갱신, 탭 위치 유지, 성향 카드의 실제 기여도 표시, 이름 저장·기본 이름·답변 대기 문구를 검사하고 화면을 보관합니다. UI 테스트의 계산 데이터는 production scoring/report 코드로 생성한 테스트 전용 fixture이며 실제 차량 이력에 저장하지 않습니다.

## 이전 버전 기록

아래 항목은 각 버전 출시 시점의 기능·진행 상태를 보존한 기록입니다. 현재 설치와 시연은 위 v1.5.7 절차를 기준으로 합니다.


**Historical: v1.2.0 — observed vehicle usage pattern learning + reliable proactive event retries + GPT style contrast tests**

- Five additional typed, read-only Pleos getters for range, battery capacity, cruise state/target, and HVAC fan speed are logged and exposed as GPT context, pending real AVD reception; Seat/Light are excluded until their CAR_SEATS/CAR_LIGHTS permissions are approved; see [data matrix](docs/SDK-DATA-MATRIX.md).
- Real SDK readings merge automatically; event-based speech covers low SOC, thermal discomfort, charging switch, and washer warning, with debounce and priority.
- Car-specific TTS rate and pitch are applied if an Android Korean TTS engine is available. Pleos AVD audio output has not been verified on the user's host.
- HPE v3 reduces unobserved VIN-seed influence and explicitly weights newly available long-run evidence, persistent vehicle state and deduplication. See [architecture audit](docs/HPE-ARCHITECTURE.md).
- Existing accounts, vehicles and v2 HPE storage can be read by the new version. The production SDK adapter still lacks verified speed/road-limit and long-run driving pattern ratios.

**Historical: v1.0.0 — accounts, cloud-ready multi-vehicle profiles and GPT personality**

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

## VPE v1.2.0 — changes validated through tests

- **Observed usage evidence:** time-separated Pleos driving-mode samples now form a measured *mode diversity* ratio (after >=4 samples); cruise-state samples form a measured engagement ratio (after >=4 recognized samples). Conversation counts from the real VPE chat form an interaction ratio (after >=3 user messages). These aggregates are vehicle-keyed and persisted in Android SharedPreferences; missing/unrecognized values stay null. No real route novelty, actual speed, steering, driver psychological traits or verified charging current is assumed.
- **HPE core:** observed mode variety affects continuous N/S at engineering coefficient 0.42; cruise use affects J/P at 0.36; real user conversation count affects E/I with preexisting 0.28 coefficient. T/F remains a deterministic fictional style seed. The 7-way evidence-coverage fraction updates only for actual observations. A long-term MBTI letter changes only if the continuous axis crosses 0.5 after stable evidence and smoothing.
- **High odometer fix:** replaces previous 250,000-km saturation with a bounded log experience scale up to 1,000,000km. Old records persist; new measurements adapt according to the HPE learning rule.
- **Proactive reliability:** 8s polling tick re-evaluates suppressed vehicle prompts even if sensor values remain stable. Previously, a greeting followed closely by a temperature change could suppress that event permanently.
- **True proactive GPT:** event-specific authenticated API endpoint generates a first-person greeting or grounded short prompt without inventing a user turn. If OpenAI fails, the built-in event text remains.
- **Testable GPT identity fidelity:** chat button **🧪 GPT 성격 차이 검증** performs 2 explicit model calls, keeping the same real vehicle state and question while reversing 7 HPE traits/4 axes for a labeled *test-only counterfactual*. No history or HPE state is overwritten. These two generated outputs let users judge whether actual word choice, response energy and empathy differ. It does not itself establish empirical personality-model validity.
- **Visible input contribution:** home screen shows real values, immediate mood scores, cumulative mode/cruise/interaction stats, each field's current role, plus individual continuous MBTI axes in the report. Diagnostic view still contains raw SDK reports.
- Android standard UI smoke and JVM unit tests cannot prove real Pleos Vehicle Service input or actual GPT response. Recheck the official Automotive emulator and the latest deployed Render service.
