# VPE Human Personality Engine (HPE) v4 / app v1.2.0 · source-code weight audit

> Vehicle-persona engineering document; implements psychological concepts as **design analogies**, not as scientifically measured personalities. No medical/psychometric diagnosis is made from vehicle signals.

## Mission and boundaries

VPE is a **vehicle personality engine** rather than a replacement navigation assistant. It turns *verified vehicle observations* into a persistent fictional MBTI character, a seven-component trait vector, episodic memory, immediate needs, and an appropriate expressive style. It avoids inventing sensor data and controlling vehicle hardware. The app now includes short, event-triggered proactive output. In-vehicle safety review is required before deployment.

The app intentionally omits perceived age from the user-facing identity. Vehicle voice blueprints now control Android TTS pitch and cadence when a suitable Korean TTS engine is available on the device. Voice availability and PC audio routing require Pleos runtime testing.

## Six layers, interfaces and model provenance

| Stage | Implementation | Inputs | Outputs |
| --- | --- | --- | --- |
| 1. Vehicle perception | `VehicleReadings`, `HpeSignals`, `VehicleDataMapper` | Verified SDK callbacks and optional enriched context | Typed values; null for missing/denied/unsupported |
| 2. Personality Core | `HumanPersonalityEngine.makeCore()` | Vehicle key + available long-term evidence | MBTI four continuous axes E/N/T/J, seven traits, evidence coverage |
| 3. Narrative Memory | `learn()`, `HpeStore` | New, meaningful driving events | Accumulated *jointly observed* km, event count, brief milestones |
| 4. Cognitive Appraisal | `appraise()` | Energy, climate target/actual, charge switch, safety | Need, discomfort, perceived coping, uncertainty |
| 5. Affective Dynamics | `affect()` | Appraisal | 8 needs/affect dimensions, valence [-1,1], arousal [0,1] |
| 6. Personality Expression | `express()`, `MainActivity.reply()` | Personality, affect, context | Text, display label, vocal-style scalar, motion intensity and proactively generated event text |

**Psychological inspiration, not ground-truth coefficients:**

- Big Five: model traits as continuous dimensions rather than one of 16 disjoint character scripts.
- McAdams' three levels of personality: stable dispositions, adaptive characteristics and narrative self/memory.
- Mischel–Shoda CAPS: an if/then behavioral signature (same need, distinct personality-conditioned reaction).
- Scherer/Lazarus cognitive appraisal: needs, goal relevance, and coping capacity contribute separately to emotion.
- Russell circumplex: valence/arousal guide *presentation only*; they do not replace concrete needs.

All VPE weights are explicit **engineering priors**, versionable and testable. The MBTI label is a fictional vehicle-character presentation, **not** a validated human personality measurement.

## Identity and weights

Seed each axis deterministically: `0.5 + 0.20 × (SHA256(vehicleKey, axis) - 0.5)`, SHA256 first two bytes normalized to [0,1]. This is a stable **fictional style prior**, not measured personality evidence.

If supported and observed, add:

- E: `+0.28 × (interactionRatio−0.5)`
- N: `+0.36 × (explorationRatio−0.5)`
- J: `+0.32 × (regularityRatio−0.5)`
- T: currently seed-based, because verified vehicle telemetry is **not** a legitimate measure of human analytical preference.

Clamp each axis to [0,1]. Convert to MBTI letters at 0.5; maintain the underlying continuous values. Seven secondary traits use combinations of these axes and verified accumulated mileage; see the source of `makeCore`. Expose a coverage score rather than implying unsupported personal or historical evidence.

Core learning uses explicit, auditable engineering coefficients:
- **35% candidate / 65% saved core** when a new verified long-term input first arrives.
- **12% candidate** after a >=5 percentage-point change in a long-term driving/use-pattern ratio.
- **3% candidate** after an accumulated >=100km odometer milestone.
- **0% candidate** for identical sensor polling and transient battery/climate readings.
- Long-term coverage counts available odometer, VIN-derived estimated age, exploration, regularity and interaction inputs, divided by 5. It is a coverage fraction, **not** a statistical confidence interval.

These are transparent, deterministic **engineering priors**, not weights validated on a real-world driver personality dataset. Calibrate them later with consent-based real driving sessions and ratings of perceived vehicle-character differences.

**Joint experience:** at the first verified reading, jointly observed distance starts at 0 km even when the odometer already reads 199,177 km. The difference between later verified odometer readings is tracked separately.

### Current 7-trait weight matrix (clamped to [0,1])

| Long-term trait | Formula |
| --- | --- |
| Activity | 0.23 + 0.54 E + 0.10 normalized mileage |
| Curiosity | 0.18 + 0.66 N + 0.10 exploration |
| Warmth | 0.25 + 0.52 (1 − T) + 0.10 interaction |
| Confidence | 0.32 + 0.32 weighted age/mileage + 0.20 E |
| Caution | 0.22 + 0.55 J + 0.12 T + model-age adjustment |
| Playfulness | 0.18 + 0.55 (1 − J) + 0.18 E |
| Expressiveness | 0.20 + 0.60 E + 0.10 N |

Only E/N/J have optional long-run measured input ratios. T/F remains a seed-based fictional style. Missing ratios are neutral 0.5 **only in calculations**, not exported as real SDK readings. Odometer saturation is 250,000km and model-age saturation is 25 years. The actual production app still has no observed exploration, regularity or interaction ratios.

## Cognitive appraisal

For EV battery SOC in percent:

`hunger = clamp((35 − SOC) / 35, 0, 1)`.

For current cabin temperature `T_current` **and** HVAC target `T_target` in Celsius:

`hot = clamp((T_current − T_target − 1) / 7, 0, 1)`
`cold = clamp((T_target − T_current − 1) / 7, 0, 1)`.

Do not infer cabin temperature from target temperature. If either reading is absent, thermal need is *unknown*, not measured as zero; the UI shows an unavailable indicator.

A supported charge-switch ON state lowers energy-related uncertainty without falsifying SOC. It indicates the charge switch's state, **not confirmed energy transfer**; a full charging-state getter is a future extension.

Safety and critical-energy messages may override the ordinary speech cooldown. `LiveProactiveEngine` emits one greeting per connected vehicle, plus deduplicated battery/temperature/charging-switch/washer-warning events without user chat input. `HpeExpression.canInitiate` still reports whether parked evidence exists; it does not gate the proactive engine. Real speed/road-limit and gear-P remain unverified in this SDK adapter, and speeding alerts have not been implemented.

Emotion vector: hunger, heat, cold, fatigue, excitement, tension, relief, comfort. Fatigue and excitement intentionally remain neutral until trustworthy trip-time and novelty-event sources are connected; they must not be presented as measured.

## SDK integration matrix

| Signal | Getter / listener | Status |
| --- | --- | --- |
| Odometer | typed Odometer getter + listener | Implemented; real Pleos AVD receipt previously verified |
| Battery SOC | typed EvBattery getter + listener | Implemented; real Pleos AVD receipt previously verified |
| VIN / CRN | typed CarInfo getter; hash-only | Implemented; real Pleos AVD receipt previously verified |
| Fuel | typed Fuel getter + listener | Implemented; may return unsupported on EV |
| Driver actual cabin temperature | `HVAC.getHvacTemperatureCurrent` + listener | Implemented in code; vehicle-level read to be verified |
| Driver target temperature | `HVAC.getHvacTemperature` + listener | Implemented in code; vehicle-level read to be verified |
| HVAC power | `HVAC.getHvacPowerState` + listener | Implemented in code; vehicle-level read to be verified |
| EV charge switch | `EvBattery.getEvChargeSwitchState` | Getter implemented; eight-second fallback polling |
| Gear / speed / safety / door | No verified read adapter yet | Unknown; cannot trigger fictional safety assertions |
| Location/route, maintenance, real shared trip history | Requires further source and consent | Unknown; not inferred from odometer or hardcoded |

Permissions are read-only `CAR_DRIVE`, `CAR_ENERGY`, `CAR_INFO`, `CAR_CLIMATE`. Real device access still depends on the Vehicle Service, capability and granted permissions. Data sources must be evaluated independently and errors displayed in diagnostics.

## Acceptance tests

Run `gradle testDebugUnitTest assembleDebug` or check GitHub Actions:

- Identical vehicle state, vehicle key, HPE version -> identical computed identity and output.
- Repeated polls do not create memory events.
- 1 km updates over >=100 km produce one meaningful event.
- Existing 199,177 km odometer starts with 0 km of jointly observed driving.
- Battery 100% -> 15% changes hunger and tension, not immediate MBTI.
- Charging switch ON changes coping and relief, not the measured battery level.
- A target temperature alone does not fabricate "hot"; current+target does.
- Safety warnings and critical SOC can preempt ordinary speech throttling.
- Same source data produces repeatable expression without an LLM.
- App/build tests do not prove newly added HVAC getters are available on the user's AVD; use the diagnostic SDK callback logs.

## Explicit next-phase boundaries

Real geographic novelty requires FusedLocation/NaviHelper integration and appropriate permissions. Maintenance history requires an authorized maintenance data source. Android TTS and the LLM are independent adapters; the HPE algorithm stays deterministic. Actual Pleos microphone/speaker integration requires emulator testing. Additional Console properties do **not** imply typed SDK access, and unsupported sources must not be silently simulated.

## Current v1.2.0 code extensions (supersedes v1.1.2 coefficient notes above)

- HPE version **4** adds **modeVarietyRatio**, **cruiseUseRatio**, and genuine VPE chat **interactionRatio** as optional, persistent, per-vehicle observations. Car mode and cruise mode are polled no more often than every 8 seconds when their SDK values are fresh. Four samples are required for the first two ratios; at least three user messages for the latter.
- N/S axis candidate: previous base + `0.36*(geographicExploration-.5)` if verified + **`0.42*(observedModeVariety-.5)`** if the mode variety ratio has qualified. Mode variety is **not labeled as GPS exploration**.
- J/P candidate: previous base + `0.32*(routeRegularity-.5)` if independently observed + **`0.36*(cruiseUseRatio-.5)`** after sufficient SDK readings.
- E/I candidate remains base + `0.28*(actualInteractionRatio-.5)` after >=3 VPE user messages, with ratio `clamp(actualMessages/12)`. T/F is still seed only.
- Previous mileage experience factor `clamp(km/250000)` **is replaced** with `clamp(ln(1+km/50000)/ln(1+1000000/50000))`. Vehicles over 250,000 km therefore do not receive identical experience saturation.
- Evidence coverage divides available inputs **by 7**: odometer, model age, geographic exploration, route regularity, interaction, observed mode variety, observed cruise use. It counts availability, not the validation of a personality theory.
- First evidence adaptation 35%, meaningful pattern >=5p adaptation 12%, 100-km event adaptation 3%; repeated unchanged input 0%. The existing HPE per-vehicle store is backward-readable.
- A periodic live tick re-evaluates deferred proactive events after 8s even when the SDK state remains unchanged. This fixes the missed climate event after a greeting.
- GPT production responses are instructed by the HPE core; the opt-in **🧪 GPT 성격 차이 검증** comparison uses the exact same real vehicle facts and question, changes only HPE personality traits/axes for a test-only opposite personality, and never overwrites identity/history. Observe the actual resulting text; no statistical psychometric validity is claimed.
