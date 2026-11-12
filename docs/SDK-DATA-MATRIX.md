# VPE Pleos SDK input matrix · v1.1.3 (2026-11-12)

The following are **typed getters found in the actual Vehicle 2.3.2 AAR** and called by VPE, with individual failure logs. Successful compilation only proves API compatibility; each new getter still requires a Virtual Console reception test.

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
| **Driver seat occupied** | Seat.getCurrentSeatOccupancy(driverSeat) | Diagnostics and GPT context | **New, unverified** |
| **Ambient light brightness** | Light.getAmbientLightBrightness(driverSeat) | Diagnostics and GPT context | **New, unverified** |

## Why not feed all values into the HPE long-term core?

Vehicle *state* and user *preference* are different. Current battery SOC, open doors, cruise target, and cabin fan are **situational**. Long-term traits need repeatable trip/session summaries, a stable vehicle key, signal availability, and unit validation. The HPE's current verified long-term inputs are odometer and VIN-derived estimated model age. Exploration, regularity and social ratios are still absent; engine neutral values must never masquerade as real measurements.

The seven new getters are safe read-only requests behind per-domain failure reporting, retried by the app's eight-second fallback refresh if supported. Record the SDK `*_OK`, `*_ERROR`, or `EMPTY` diagnostics after changing each corresponding Virtual Console control. No direct actual-speed or road-limit getter has been integrated, so speeding detection is not claimed.

## Tuning and debugging

- `app/src/main/java/com/vpe/hyundai/PleosVehicleClient.kt`: SDK getters and listeners
- `app/src/main/java/com/vpe/hyundai/VehicleDataMapper.kt`: typed fields / null-safe merges
- `app/src/main/java/com/vpe/hyundai/MainActivity.kt`: automatic refresh, HPE context, diagnostics, proactive speech
- `app/src/main/java/com/vpe/hyundai/HumanPersonalityEngine.kt`: long-term weights and transient affect
- `docs/HPE-ARCHITECTURE.md`: exact weighting, trust boundaries and test method
