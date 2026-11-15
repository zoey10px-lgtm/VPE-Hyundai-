# Multivariate persona scoring — character.v3.0

## Compatibility

Existing Android APK 1.5.3, routes, authentication, observation batches, report fields, 16 traits, 7-trait chart, text chat and Realtime packet schemas are retained. No APK rebuild is required for this server-side calculation change. Report, chat and voice use the same `effectiveTraits` function.

## Actual SDK audit

The official Vehicle 2.3.2 AAR was downloaded and its class-file method signatures independently extracted on 2026-11-15. SHA-256: `e11fdfe498cad3ecc0d5a2a6992b0c4a4d135195f1455018127bc069fd5ef937`. Its 20 domain interfaces contain 108 unique query methods / 109 including overloads. All 108 entries in `Hyundai_Persona_Input_Catalog.md` were found; no uncatalogued query method was found. A binary API existing is separate from adapter implementation and emulator delivery.

The current Android adapter remains unchanged. This change does not claim to query all 108 APIs, nor to receive media volume or acceleration from Vehicle SDK 2.3.2.

## Inputs and normalization

| Feature | Input | Normalization / semantics | Current acquisition |
|---|---|---|---|
| Occupancy | passengerCount or passenger | count 0–4 → 0–100; Boolean fallback denotes presence, never an invented count | Existing front-seat Boolean; optional validated count |
| Pace | averageSpeedKmh | 0–120 km/h → 0–100, bounded to 100 | Derived only after at least 3 valid odometer intervals covering 30 seconds; reset/teleport rejected |
| Acceleration | harshAccelPer100Km | 0–12 events/100 km → 0–100 | Optional externally validated signal / isolated preset |
| Deceleration | harshBrakePer100Km | 0–12 events/100 km → 0–100 | Optional externally validated signal / isolated preset |
| Time context | hourLocal + weekend | smooth day/night cycle and weekday/weekend mixture → 0–100 | Actual KST clock |
| Climate | cabin/target temperature | comfort delta → 0–100 | Existing cabin SDK reading; 22°C internal reference when target missing |
| Media | mediaVolumePercent | 0–100 | Optional external integration / isolated preset; no invented live media getter |
| Cruise | cruiseControlState | explicit enabled/disabled enums → 100/0 | Existing SDK getter |
| Mode | drivingMode | known mode category → 0–100 | Existing getter, missing when unsupported |
| Energy | energyPercent | measured 0–100 | Existing SOC / fuel fallback |

Missing/invalid values stay null. A fixed full-row denominator prevents one available signal from being magnified to 100% influence. At least four independent feature groups are required before contextual character scoring becomes active; acceleration and braking count as one group. Every input has an absolute coefficient <= 0.20 and every trait has an absolute weight budget <= 1. Each of 16 traits is a continuous weighted sum. MBTI axes project those continuous traits; border regions can remain X.

## Core versus context

Long-term learned traits remain in `profile.traits` and retain the daily 0.015 adaptation bound. Current sensor weighting is stored separately in `bootstrap.contextScore`. `effectiveTraits = clamp(core + 0.70 × (contextTraits − 0.5))` controls the report and actual conversation. Seat occupancy and current media/climate cannot rewrite the long-term core. Migrations retain memories, relationships and learned residuals while removing the previous direct initial sensor mapping.

Current contextual values expire after 30 seconds. Noisy low-rate odometer samples are not converted to harsh acceleration/deceleration. Preset tests never insert data into production vehicle histories.

## Run the two isolated presets

```sh
node server/character/scoring.mjs
```

- `lively_weekend_drive`: solo, dynamic, daytime/weekend, high volume → raw context ENFP, E_score 73.625.
- `quiet_night_cruise`: three passengers, steady, night, low volume → raw context ISTJ, E_score 23.1392.

Raw context scores and effective profile scores are separately exposed. Test assertions verify occupied/quiet remains I and solo/dynamic remains E, missing-data behavior, fixed influence budgets, repeatability, core isolation, report/chat parity, speed derivation and persistence compatibility.

## Vehicle 2.3.2 cruise enum correction (character.v3.0.1)

The actual AAR enum contains OTHER, ENABLED, ACTIVATED, USER_OVERRIDE, SUSPENDED and FORCED_DEACTIVATION_WARNING. ENABLED is available/standby; ACTIVATED denotes engagement. The original scorer mistakenly omitted ACTIVATED and treated ENABLED as engagement. This is corrected; override/suspended map to inactive, and unknown/warning states remain unclassified.

The user's actual Virtual Console card offers Not ready, Ready, Longitudinal, Lateral and Full autonomous. DISABLED is not a button on that card. Ready and Longitudinal must be checked against received SDK values, rather than presenting SDK enum names as console menu labels. Live confirmation still requires the reported raw cruise value and normalized contribution to change.

Raw SDK evidence now refreshes independently of normalized-score hashes, so equal normalized values cannot leave old source values/timestamps displayed. The v3.0-to-v3.0.1 migration retains learned traits, memories and relationships.
