# Character v3 implementation

## Running

The Android 1.5.0 client requires the matching backend revision (`/health` reports `apiRevision:3`). Existing Render `OPENAI_API_KEY` and `DATABASE_URL` are reused; no key is bundled in the APK. `OPENAI_MODEL` controls text and semantic extraction; `OPENAI_REALTIME_MODEL` remains the existing audio model setting. Database tables are added lazily without replacing existing accounts, vehicles or messages.

`npm ci --prefix server && npm test --prefix server` runs unit tests. Set `DATABASE_URL` and `TEST_DATABASE=1` to include actual PostgreSQL integration tests; CI provides PostgreSQL 16. Android CI runs `testDebugUnitTest assembleDebug`.

## Implemented path

Fresh typed Vehicle SDK patches → timestamped persistent Android observation queue → authenticated `/api/v3/observations` → idempotent events and trip aggregation → 16 continuous traits, context offsets and daily change cap → state/relationship/memory/action plan → canonical instructions shared by `/chat` and `/realtime/token` + `/turn/plan`.

The creative initial temperament is seeded per vehicle; it is explicitly not a measured human personality. New cars do not claim driving evidence. MBTI is a descriptive projection with neutral X boundaries and hysteresis, not a generation input. The seven-axis chart uses a compatibility projection of the server's sixteen traits.

Text requests are queued in order. Realtime automatic responses are disabled: transcription → turn plan → matching `session.updated` → `response.create`. Responses may contain several connected thoughts and a relevant question. User speech invalidates old plans and queued audio; unfinished playback is truncated and the unheard full transcript is not saved as spoken history. After six seconds without a plan, the last confirmed persona answers the original audio. This fallback is logged.

## Sources and constraints

- Vehicle 2.3.2 supplies odometer, SOC/fuel, climate, gear, front passenger occupancy, driving mode, cruise state and warnings. Nullable/expired data is excluded. Front passenger occupancy does not represent all seats.
- Location is an explicit foreground Android LocationManager fallback with runtime permission, provider availability, measurement-age and accuracy checks. The documented Pleos FusedLocation 1.1.2 Maven artifact returned HTTP 404 during implementation, so no unavailable dependency is declared. If Pleos exposes no Android location provider, F01/F02 stay unobserved.
- Derived features include departure regularity, distance variability, refill thresholds, manual temperature-setting stability, passenger/night/cruise ratios and route novelty when location exists. No raw trip-distance/time units are guessed.
- Long-term dialogue evidence requires explicit quoted preferences, minimum observations and confidence; a casual mood never directly rewrites traits. Memory retrieval currently uses grounded lexical relevance/recency/importance, not embeddings.
- Numeric coefficients and example utterances are engineering choices. Psychological validity and user-perceived character separation require evaluation; successful compilation is not a listening test.
- Existing legacy v2 endpoints remain for installed 1.4 clients. Legacy proactive event and comparison screens still use their existing policy; the new text/voice turn pipeline is authoritative for conversations.
- Actual Pleos microphone interruption, provider availability, voice timbre and paid OpenAI calls must be exercised on the deployed service/device. No API credentials are present in this development workspace.

## Character v2 / app 1.5.1

Replaces ID-seeded temperament with a neutral awaiting-evidence state. Qualified vehicle aggregates form six experience coordinates and nonlinear interactions; a continuous 6×16 projection affects all sixteen traits. The first formation uses observed evidence; subsequent updates retain the daily .015 cap. Existing v1 profiles migrate using stored aggregates. The mappings are explicit creative design coefficients for fictional vehicle characters, not psychological inferences about a driver.

Performance contracts specify distance, opinion, bluntness, expression, play and conversational expansion. Reference dialogue is selected by distance in six expression dimensions, not MBTI. All characters no longer share a friendly service role. Grounded local turn cues preserve affect/question intent when semantic extraction times out; they cannot add persistent preferences or memories.

Authenticated `/api/v3/persona-audit` replays three predefined synthetic SDK histories (24 trips each) through ingestion, aggregation, formation and the production compiler. Same vehicle ID, current sensors, relationship and messages; only usage history differs. The endpoint calls the existing GPT integration and returns actual generated replies. It never writes synthetic data into a real vehicle profile. Android's comparison dialog labels the data as synthetic. This is a controlled character-separation experiment, not evidence of a user's real driving history.
