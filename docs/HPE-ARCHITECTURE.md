# VPE Human Personality Engine (HPE) 1.0

> Vehicle-persona engineering document; implements psychological concepts as **design analogies**, not as scientifically measured personalities. No medical/psychometric diagnosis is made from vehicle signals.

## Mission and boundaries

VPE is a **vehicle personality engine** rather than a replacement navigation assistant. It turns *verified vehicle observations* into a persistent fictional MBTI character, a seven-component trait vector, episodic memory, immediate needs, and an appropriate expressive style. It avoids unsolicited speech while driving, controlling vehicle hardware, and inventing sensor data.

The app intentionally omits perceived age from the user-facing identity. Voice is a **blueprint**, not synthesized audio. Its feminine/masculine presentation is retained.

## Six layers, interfaces and model provenance

| Stage | Implementation | Inputs | Outputs |
| --- | --- | --- | --- |
| 1. Vehicle perception | `VehicleReadings`, `HpeSignals`, `VehicleDataMapper` | Verified SDK callbacks and optional enriched context | Typed values; null for missing/denied/unsupported |
| 2. Personality Core | `HumanPersonalityEngine.makeCore()` | Vehicle key + available long-term evidence | MBTI four continuous axes E/N/T/J, seven traits, evidence coverage |
| 3. Narrative Memory | `learn()`, `HpeStore` | New, meaningful driving events | Accumulated *jointly observed* km, event count, brief milestones |
| 4. Cognitive Appraisal | `appraise()` | Energy, climate target/actual, charge switch, safety | Need, discomfort, perceived coping, uncertainty |
| 5. Affective Dynamics | `affect()` | Appraisal | 8 needs/affect dimensions, valence [-1,1], arousal [0,1] |
| 6. Personality Expression | `express()`, `MainActivity.reply()` | Personality, affect, context | Text, display label, vocal-style scalar, motion intensity, no proactive speech when moving |

**Psychological inspiration, not ground-truth coefficients:**

- Big Five: model traits as continuous dimensions rather than one of 16 disjoint character scripts.
- McAdams' three levels of personality: stable dispositions, adaptive characteristics and narrative self/memory.
- Mischel–Shoda CAPS: an if/then behavioral signature (same need, distinct personality-conditioned reaction).
- Scherer/Lazarus cognitive appraisal: needs, goal relevance, and coping capacity contribute separately to emotion.
- Russell circumplex: valence/arousal guide *presentation only*; they do not replace concrete needs.

All VPE weights are explicit **engineering priors**, versionable and testable. The MBTI label is a fictional vehicle-character presentation, **not** a validated human personality measurement.

## Identity and weights

Seed each axis deterministically: `0.5 + 0.28 × (SHA256(vehicleKey, axis) - 0.5)` where SHA256's first two bytes are normalized to [0,1].

If supported and observed, add:

- E: `+0.28 × (interactionRatio−0.5)`
- N: `+0.36 × (explorationRatio−0.5)`
- J: `+0.32 × (regularityRatio−0.5)`
- T: currently seed-based, because verified vehicle telemetry is **not** a legitimate measure of human analytical preference.

Clamp each axis to [0,1]. Convert to MBTI letters at 0.5; maintain the underlying continuous values. Seven secondary traits use combinations of these axes and verified accumulated mileage; see the source of `makeCore`. Expose a coverage score rather than implying unsupported personal or historical evidence.

After **meaningful** observed driving >=100km or a >=5 percentage-point change in a verified long-run pattern, `adaptCore` uses 97% previous + 3% new candidate. Repeated identical API readings do not count as experience. Do not use fuel fluctuations to update core traits.

**Joint experience:** at the first verified reading, jointly observed distance starts at 0 km even when the odometer already reads 199,177 km. The difference between later verified odometer readings is tracked separately.

## Cognitive appraisal

For EV battery SOC in percent:

`hunger = clamp((35 − SOC) / 35, 0, 1)`.

For current cabin temperature `T_current` **and** HVAC target `T_target` in Celsius:

`hot = clamp((T_current − T_target − 1) / 7, 0, 1)`
`cold = clamp((T_target − T_current − 1) / 7, 0, 1)`.

Do not infer cabin temperature from target temperature. If either reading is absent, thermal need is *unknown*, not measured as zero; the UI shows an unavailable indicator.

A supported charge-switch ON state lowers energy-related uncertainty without falsifying SOC. It indicates the charge switch's state, **not confirmed energy transfer**; a full charging-state getter is a future extension.

Safety warnings have higher expressive priority than hunger or discomfort. The current model never autonomously speaks: `canInitiate` is true only when explicit parked evidence is provided. Actual gear-P is **not yet** available through the current typed Pleos SDK integration, so it remains null, and proactive speech remains off.

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
| EV charge switch | `EvBattery.getEvChargeSwitchState` | Getter implemented; periodic manual refresh |
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
- Safety warnings outrank hunger and in-motion proactive speech stays suppressed.
- Same source data produces repeatable expression without an LLM.
- App/build tests do not prove newly added HVAC getters are available on the user's AVD; use the diagnostic SDK callback logs.

## Explicit next-phase boundaries

Real geographic novelty requires FusedLocation/NaviHelper integration and appropriate permissions. Maintenance history requires an authorized maintenance data source. Voice/TTS synthesis and an LLM are separate adapters; the HPE algorithm stays independently testable and deterministic. Additional Console properties do **not** imply typed SDK access, and unsupported sources must not be silently simulated.
