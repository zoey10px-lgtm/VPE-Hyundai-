# VPE Pleos SDK input matrix · v1.2.0 (2026-11-12)

The following are **typed getters found in the actual Vehicle 2.3.2 AAR**; only rows marked connected are called by VPE, with individual failure logs. Successful compilation only proves API compatibility; each new getter still requires a Virtual Console reception test.

| Input | SDK method | VPE consumer | AVD evidence |
| --- | --- | --- | --- |
| VIN / CRN | CarInfo.getVINInfo / getCRNInfo | Vehicle-key seed | Earlier VIN_OK / CRN_OK |
| Odometer | Odometer.getCurrentDistanceDriven + listener | Long-term experience | Earlier ODO_OK |
| Battery SOC | EvBattery.getEvBatteryStateOfCharge + listener | Transient needs, proactive alerts | Earlier EV_OK |
| Fuel % | Fuel.getFuelPercent + listener | Transient energy | Pending EV AVD |
| HVAC actual/target/power | HVAC.getHvacTemperatureCurrent / getHvacTemperature / getHvacPowerState + listeners | Transient climate and chat | Not yet proven on target AVD |
| Charge switch | EvBattery.getEvChargeSwitchState | Temporary relief, chat | Not yet proven |
| Trip cumulative time/distance | Odometer.getTripCumulativeDrivingTime / Distance | Diagnostics/chat only; units unknown | Not yet proven |
| Driving mode | DrivingMode.getCurrentDrivingModeState | Diagnostics/chat, no long-term scoring | Not yet proven |
| Washer, oil, brake fluid | Safety typed getters | Warning or diagnostics | Not yet proven |
| **Range remaining** | Odometer.getRangeRemainingDistance | Diagnostics and GPT context, raw units | **New, unverified** |
| **Battery capacity** | EvBattery.getEvBatteryCapacity | Diagnostics and GPT context, raw units | **New, unverified** |
| **Cruise state** | DrivingMode.getCruiseControlState | Diagnostics and GPT context | **New, unverified** |
| **Cruise target speed** | DrivingMode.getCruiseControlTargetSpeed | Diagnostics and GPT context, **not actual speed** | **New, unverified** |
| **HVAC fan speed** | HVAC.getHvacFanSpeed(driverSeat) | Diagnostics and GPT context | **New, unverified** |
| Driver seat occupied | Seat.getCurrentSeatOccupancy(driverSeat) | **Not connected**; requires approved CAR_SEATS permission | Pending permission |
| Ambient light brightness | Light.getAmbientLightBrightness(driverSeat) | **Not connected**; requires approved CAR_LIGHTS permission | Pending permission |

## Why not feed all values into the HPE long-term core?

Vehicle *state* and user *preference* are different. Current battery SOC, open doors, cruise target, and cabin fan are **situational**. Long-term traits need repeatable trip/session summaries, a stable vehicle key, signal availability, and unit validation. The HPE's current verified long-term inputs are odometer and VIN-derived estimated model age. Exploration, regularity and social ratios are still absent; engine neutral values must never masquerade as real measurements.

The five new getters are safe read-only requests behind per-domain failure reporting, retried by the app's eight-second fallback refresh if supported. Record the SDK `*_OK`, `*_ERROR`, or `EMPTY` diagnostics after changing each corresponding Virtual Console control. No direct actual-speed or road-limit getter has been integrated, so speeding detection is not claimed.

## Tuning and debugging

- `app/src/main/java/com/vpe/hyundai/PleosVehicleClient.kt`: SDK getters and listeners
- `app/src/main/java/com/vpe/hyundai/VehicleDataMapper.kt`: typed fields / null-safe merges
- `app/src/main/java/com/vpe/hyundai/MainActivity.kt`: automatic refresh, HPE context, diagnostics, proactive speech
- `app/src/main/java/com/vpe/hyundai/HumanPersonalityEngine.kt`: long-term weights and transient affect
- `docs/HPE-ARCHITECTURE.md`: exact weighting, trust boundaries and test method

## Energy source reliability correction (v1.1.4)

For an EV, battery SOC takes priority over fuel percent when both SDK getters return data. Previously, `fuelPercent ?: evBatteryLevel` could mask a real Virtual Console battery change. The app now uses `evBatteryLevel ?: fuelPercent` with range validation, adds `energySource` to GPT context, and shows the chosen source in Diagnostics. Charging switch ON indicates the switch state only, not confirmed energy delivery.

## v1.2.0 cumulative HPE observations

The app now persists a per-vehicle aggregate of **actual** driving mode and cruise-state observations. A sample is counted only during an 8-second live polling tick and only if a recent typed SDK response exists. After four meaningful samples, mode diversity `C((1 - highestModeCount / totalModeSamples) * 1.5)` influences the N/S axis; cruise usage `engagedSamples / recognizedSamples` influences the J/P axis. Only explicit cruise ON/ACTIVE/ENGAGED/RUNNING/OFF/STANDBY/etc. strings are interpreted; unknown responses do not count. Three actual user-to-VPE messages enable `C(interactionCount/12)` on the E/I axis. These are **engineered fictional character mappings**, not validated measurements of the user's personal MBTI. Real location exploration remains disconnected.

The current mode/fan/range/cruise values also appear under the home screen in a separate **Pleos 데이터 · 페르소나 영향** list, with explicit HPE/GPT-only labels. Energy, climate and charging remain temporary affect inputs and do not immediately change the long-term MBTI. Missing values stay unavailable.

Proactive events suppressed by the 8-second cooldown are retried on the next live ticker, including if the last SDK measurements are unchanged. The account-based GPT first turn uses POST `/api/proactive`. The `/api/persona-compare` endpoint is solely an opt-in test comparing two persona contexts with identical sensor facts; it stores no conversation.
