# SDK initial character — Android 1.5.3 / character.v2.1

## Corrected behavior

The first valid SDK vehicle characteristics/settings create all 16 character dimensions immediately. A total odometer of 0 km is a valid input. Completed trips are required for learning long-term usage patterns, not for creating a character. The same server profile supplies the report, text-chat instructions and Realtime speech plan.

- Initial factors: vehicle experience (odometer/model year), company (passenger occupancy), structure (cruise/HVAC automatic setting), driving style (known mode enum), and warmth preference (manually selected target temperature).
- These are explicit creative mappings for a fictional car character, not psychological inferences about the driver. Unknown inputs remain unknown; VIN/vehicle identifiers never generate temperament.
- Initial settings update immediately while the character is in `sdk_initial`. Mature usage and explicit dialogue evidence change it to `formed`, after which daily adaptation remains bounded at 0.015 per dimension.
- Current SOC, cabin temperature and warnings affect present needs/reactions without rewriting long-term personality. When target temperature is missing, 22°C is an internal comfort reference, not a claimed SDK setting.
- Every field currently represented by the Android adapter is transported, including model year, HVAC power, charging switch, washer warning, fluid states, trip raw values, range/capacity raw values and cruise target raw value. Raw units are preserved rather than guessed.
- Cruise `ENABLED` / `DISABLED` now match the SDK's reported enums in both initial generation and trip-history aggregation.
- SDK callbacks coalesce for 500 ms, then drain queued batches instead of leaving trailing fields behind a 15-second gate. Text/Realtime startup also ingest pending observations before planning the turn.
- Neutral reports no longer invent the first three positive keywords. Signed keywords reflect the strongest actual dimensions. Reports show the initial evidence, all 16 dimensions and the current algorithm version. Borderline MBTI summaries are marked provisional after initial evidence exists.
- Existing v2.0 neutral profiles form from their stored SDK observations on migration. Mature learned profiles and shared memories are retained.

## Verification

`node --test server/character/bootstrap.test.mjs` covers zero-km formation, same-car setting changes reaching chat instructions, transient sensor isolation, 300 duplicate polls, missing data, v2.0 migration, input role coverage/raw data transport and mature adaptation limits.

The Postgres integration test covers pending SDK data ingested during the first chat, report/plan alignment, persistence and idempotent replay. Existing character, speech generation and usage-diversity tests remain in place. GitHub Actions runs Android unit tests/APK assembly and the backend tests against Postgres.

## Scope

This repairs the adapter and observations already implemented in this repository. The historical catalog contains 108 SDK query APIs; it is a wider API inventory, not a claim that all 108 have been added or respond on the emulator. Unsupported SDK properties are not substituted with fabricated values.
