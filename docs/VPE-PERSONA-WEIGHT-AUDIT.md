# VPE Persona Weight & Trust Audit — 2026-11-12 (app v1.1.4)

Source of truth: `HumanPersonalityEngine.kt`, `PersonaEngine.kt`, `IdentityEngine.kt`, `LiveProactiveEngine.kt`, `MainActivity.kt`, `VehicleDataMapper.kt`, `server/index.mjs`.
This report transcribes **implemented code**, not proposed algorithms. All coefficients below are **engineering priors**, not coefficients fitted or psychometrically validated using real vehicle/driver data.

## Feature status (1 / 2 / 3)

1. SDK: getter/listener adapters for odometer, VIN/CRN, EV SOC, fuel, HVAC temperature/target/power, charging switch, trip time/distance, driving mode and safety context; five new v1.1.3 getters: remaining range, battery capacity, cruise state, cruise target speed, HVAC fan speed. The five new getter values flow to diagnostics and GPT context, **not long-term HPE**. Automotive AVD must confirm each newly added getter in a runtime reception test.
2. Live: registered callbacks for odometer, fuel, EV SOC and three HVAC signals; fallback 8s polling for the rest after first successful vehicle connection. The Activity merges partial callbacks, runs HPE.observe on changes and updates current-screen text. On app restart the persisted persona is loaded but actual SDK refresh requires reconnect.
3. Proactive: one vehicle greeting or one eligible event when first connected, then SOC, thermal, charging-switch and washer-warning prompts with hysteresis/cooldown; chat messages and Android TTS playback. These prompts use deterministic templates conditioned on HPE axes/traits; **no spontaneous GPT-generated continuation**. There is no verified actual vehicle-speed/road-speed-limit input and no speeding warning.

## HPE stable identity (v3)

Notation:
- `C(x)=min(1,max(0,x))`. `C11(x)=min(1,max(-1,x))`.
- `D=C(odometerKm/250000)` when available; `A=C(modelAgeYears/25)` when an estimated age is derivable from VIN.
- `X` = explorationRatio, `R` = regularityRatio, `I` = interactionRatio, all valid only if actually supplied in [0,1]. **All three are null in the current production MainActivity HPE adapter.**
- `S=0.65D+0.35A` if both supplied, else D or A, else null.
- `H_k` = first two bytes of SHA256("vpe-hpe1|" + vehicleKey + "|" + axisName), interpreted as uint16 / 65535. `B_k=0.5+0.20(H_k-0.5)`, hence B in [0.4,0.6].
- Missing long-term scalar inputs contribute no adjustment to axis scores. In each derived trait only, missing D/A/X/I/S uses the neutral value 0.5.

Four continuous MBTI-style axes:
- E = C(B_E + 0.28(I - 0.5)) if I exists, else B_E.
- N = C(B_N + 0.36(X - 0.5)) if X exists, else B_N.
- T = C(B_T) always (fictional seeded style only).
- J = C(B_J + 0.32(R - 0.5)) if R exists, else B_J.
- Label = E vs I, N vs S, T vs F, J vs P at threshold 0.5.

Seven derived traits, each clamped to [0,1]:
- activity = C(0.23 + 0.54 E + 0.10 D)
- curiosity = C(0.18 + 0.66 N + 0.10 X)
- warmth = C(0.25 + 0.52(1-T) + 0.10 I)
- confidence = C(0.32 + 0.32 S + 0.20 E)
- caution = C(0.22 + 0.55 J + 0.12 T + 0.08(A-0.5))
- playfulness = C(0.18 + 0.55(1-J) + 0.18 E)
- expressiveness = C(0.20 + 0.60 E + 0.10 N)

Coverage = count of available sources among odometer/X/R/I/modelAge divided by 5. This is **data coverage**, not a confidence interval.

Adaptation for each previous score P and fresh candidate P*:
`P_next=C((1-alpha)P + alpha P*)`.
- First creation: alpha=1.
- First newly available verified long-term input: alpha=0.35.
- Significant >=0.05 pattern delta for X/R/I: alpha=0.12.
- >=100km additional accumulated odometer milestone: alpha=0.03.
- Unchanged poll, battery SOC or ambient temperature only: alpha=0.
Previously observed odometer starts joint distance memory at 0km on first observation.

Because X/R/I are not wired and T is always seeded, **today's four MBTI letters mostly encode a deterministic vehicle-key seed**, not empirically observed driving characteristics. The current odometer and inferred age influence only selected secondary traits.

## HPE situational appraisal & affect

For valid SOC percentage:
- hunger = C((35-SOC)/35); if SOC missing, internal default 0.
For both actual cabin temperature Ta and desired target Tt:
- hot = C((Ta-Tt-1)/7)
- cold = C((Tt-Ta-1)/7)
If either temperature missing, hot/cold internal default to 0; the adapter's source list still distinguishes the absence.
Let `q=1` if EV **charge switch** is on (not proof of energy transfer), else 0; `w=1` if a verified safety-warning flag is true, else 0:
- uncertainty = C(hunger*(1-0.60q)+0.65w)
- relief = 0.65 if q=1 and hunger>0.05, else 0
- tension = C(0.75*uncertainty+0.25*w)
- comfort = C(1-max(hot,cold)) if both temperatures were measured, else 0
- valence = C11(0.35*comfort+0.45*relief-0.30*hunger-0.50*tension-0.22*(hot+cold))
- arousal = C(0.18+0.48*tension+0.16*max(hot,cold)+0.25*excitement)
- fatigue=0 and excitement=0 pending verified trip/session signals.

HPE expression:
- Priority: warning > hunger>=0.25 > hot>=0.35 > cold>=0.35 > charge-switch plus relief > calm.
- analytical = (T>=0.56 AND playfulness<0.70).
- Motion intensity = C(0.15+0.60*arousal+0.12*expressiveness).
- HPE voiceSpeed = C(0.75+0.30*expressiveness-0.14*tension).
- Expression warmth = stable warmth trait.
- canInitiate = (parked==true), but **this field does not gate LiveProactiveEngine**.

## LiveProactiveEngine thresholds

- Priority order: washer warning > SOC<=10% > SOC<=20% > heat>=0.35 > cold>=0.35 > charge switch on.
- Clear/rearm battery conditions at SOC>=26%; heat and cold at <=0.15; charge and washer when off.
- Normal cooldown 8,000 ms; washer and SOC<=10% bypass cooldown.
- Vehicle key change resets spoken flags; greeting once per connected vehicle subject to not already having spoken an event.
- Templates choose between analytical (T>=0.56), playful (playfulness>=0.56), and neutral. They are not GPT-generated proactive conversations.

## Legacy PersonaEngine and voice blueprint

This is **separate from the seven-trait HPE**. It still provides the initial VpeIdentity baseline, memory and TTS voice blueprint.

Legacy normalized variables:
- a = C(ageYears/20), d = C(odometerKm/250000), c = C(maintenanceScore/100),
  x = C(explorationRatio), t = C(longTripRatio), f = C(fuelPercent/100).
- When the first real vehicle identity is created, MainActivity supplies neutral defaults for missing fields: `ageYears=10` if VIN model year unavailable, `fuelPercent=50` if energy unavailable, `maintenanceScore=50`, `explorationRatio=0.5`, `longTripRatio=0.5`. **These are defaults, not observed historical driving/maintenance evidence.**

Legacy core formulas:
- energy = C(0.45-0.12a+0.22t+0.13c+0.12x)
- curiosity = C(0.25+0.48x+0.17t-0.10a)
- warmth = C(0.30+0.50c+0.10d)
- confidence = C(0.30+0.28d+0.25c+0.12t)
- expressiveness = C(0.20+0.38energy+0.28curiosity+0.14warmth)
- hunger = C((0.25-f)/0.25)
- concern = C(0.65(1-c)+0.35hunger)
- legacy speechRate = clamp(0.85+0.30energy-0.07hunger, 0.75, 1.2)
- legacy pitch = clamp(0.92+0.16expressiveness-0.05concern, 0.8, 1.15)
- legacy memory EMA on meaningful events: next = 0.95 previous + 0.05 candidate.
- legacy live expression blend function = 0.25 memory + 0.75 currently calculated; that function is **not** used in current MainActivity HPE report.

IdentityEngine voice:
- genderExpression = C(0.47+0.32(expressiveness-0.5)-0.28(confidence-0.5)+0.16(warmth-0.5))
- perceivedAge = round(20+15a+12d), bounded [20,50]
- pitchBlueprint = clamp(0.85+0.30genderExpression+0.10energy, 0.7,1.3)
- resonance = C(0.40+0.35confidence+0.20d)
- softness = warmth
- speechRateBlueprint = clamp(0.85+0.30energy, 0.75,1.2)
- Android TTS actual rate = clamp(0.65speechRateBlueprint+0.35 HPEvoiceSpeed, 0.75,1.2)
- Android TTS actual pitch = clamp(pitchBlueprint, 0.8,1.25)
- Resonance, softness, perceivedAge and genderExpression are metadata/blueprint fields; standard TTS play requests only set rate and pitch.

## GPT text representation

The Render backend receives HPE axes, seven traits, current affect and verified SDK context. For each trait v:
- tendency = high if v >= 0.5, else low.
- intensity = round(abs(v-0.5)*200) on [0,100].
It sends these alongside style instructions to the model. **These are instructions, not a proof that the model's generated wording exactly matches the numerical profile**.

## Reliability assessment

- GitHub Android compile/unit tests/standard Android UI smoke success is a build-level check, **not** a real Pleos SDK reception test.
- New data fields (range, battery capacity, cruise state/target, fan) are currently diagnostics/GPT context only. Their raw units must be validated before numerical scoring.
- The code now prefers valid **EV SOC over fuel percent** when both are present, and exposes `energySource` as EV_BATTERY_SOC/FUEL_PERCENT. Charge-switch-on is not confirmed charging.
- Actual speed, limit, gear, turn, braking, steering, navigation novelty, regularly recurring trips and conversational interaction ratios are not in current HPE telemetry.
- Reproducible calculations + unit tests = **auditable engineering**. Genuine algorithm validity requires a separate measured dataset, bounded/normalized feature definitions, ablation/sensitivity analysis and blinded human ratings of between-vehicle and within-vehicle persona differences.

## Acceptance test on Pleos AVD

1. Install the latest APK; connect the vehicle once. Verify VIN and odometer responses and see source coverage.
2. Console: EV SOC 80% -> 15%. Confirm EV_SOC_UPDATE/EV_OK; the display updates without regenerating identity, the core stays constant, hunger increases, and a one-time proactive text is emitted.
3. If fuel percent also reports a value, HPE `energySource` must still be EV_BATTERY_SOC. Check that SOC=15% is used.
4. Toggle charge switch and HVAC target/actual. Confirm distinct `*_OK`/listener values before interpreting mood; unavailable current cabin temperature cannot prove thermal discomfort.
5. Do not claim speeding warnings: cruise target speed is a setpoint, not actual instantaneous vehicle speed.
6. Test text-first proactive behavior even when the Android Korean TTS engine is unavailable; Pleos PC speakers require a separate audio-route check.
