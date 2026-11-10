# VPE Hyundai 1.0 — Render / PostgreSQL / GPT

This is the backend for the **Vehicle Persona Engine** Android app. The HPE algorithm
remains on the device; GPT expresses each *already-computed* vehicle identity.

## Flow

1. User registers or logs in with an ID and password.
2. Android stores a 30-day session token encrypted with Android Keystore.
3. A vehicle is identified by the app's **SHA-256-derived** `vehicle:<hash>` identifier
   (raw VIN is never uploaded).
4. `POST /api/vehicles` saves the vehicle name and the complete last HPE context:
   MBTI + four axes, seven long-term traits, emotional state, observed vehicle
   snapshot, experience memory, and voice blueprint.
5. `POST /api/chat` reads *only* messages belonging to that account+vehicle,
   sends those messages and the current HPE identity to OpenAI, and saves the pair
   of user/assistant messages in PostgreSQL.
6. `GET /api/messages?vehicleKey=...` and `GET /api/vehicles` restore only
   authenticated owner's data.

The GPT cannot change the canonical HPE identity. Stable long-term temperament
determines speech style; current car conditions influence temporary mood.
The server converts numerical traits to explicit language-style instructions.

## Database

The backend automatically creates the four PostgreSQL tables and indexes when
`DATABASE_URL` is configured:

- `vpe_accounts` — username, scrypt-salted password hash
- `vpe_sessions` — hashed, expiring login session token
- `vpe_vehicles` — account-owned vehicle key, display name, full HPE JSON
- `vpe_messages` — account-owned messages associated with one vehicle

Foreign keys use `ON DELETE CASCADE`. SQL uses parameters for user inputs.
All profile/message reads are scoped to the authenticated `user_id` and
`vehicle_id`. The app's local chat cache is also keyed by user+vehicle.

**Database is not automatically provisioned by source code.** On Render create
a PostgreSQL database in the same region as the web service and copy its
**internal** connection string to the web service environment variable
`DATABASE_URL`. Do not paste the connection string into GitHub or APK.

Render's Free PostgreSQL tier permits **one active instance per workspace** and
a free instance expires 30 days after creation. Use a durable paid tier for
long-lived accounts and conversations, or an external persistent PostgreSQL
provider with a reachable connection URL.

## Render deployment settings

- Repository: `zoey10px-lgtm/VPE-Hyundai-`
- Service: `vpe-hyundai`, Singapore, Node
- **Build Command: `npm install --prefix server --omit=dev`**
- **Start Command: `node server/index.mjs`**
- `DATABASE_URL` = PostgreSQL internal connection string (required for login)
- `OPENAI_API_KEY` = your OpenAI API Secret Key
- `OPENAI_MODEL` = `gpt-4.1-mini` (optional)

The legacy `VPE_ACCESS_TOKEN` is supported only until `DATABASE_URL` is set,
so older APKs can use GPT while the database is being prepared. After database
activation, all chats require **individual account sessions**.

`GET /health` returns configured booleans but never any secrets.

## API endpoints

- `POST /api/auth/register`: `{"username":"...","password":"..."}`
- `POST /api/auth/login`: same input; both return `{"username":"...","token":"..."}`
- `POST /api/auth/logout`: Bearer session token, empty JSON body
- `GET /api/me`: Bearer session token
- `GET /api/vehicles`: Bearer session token
- `POST /api/vehicles`: Bearer, `{"vehicleKey":"vehicle:...","vehicleName":"...","context":{...}}`
- `GET /api/messages?vehicleKey=...`: Bearer session token
- `POST /api/chat`: Bearer session token, `{"vehicleKey":"vehicle:...","vehicleName":"...","context":{...},"message":"..."}`

Registration accepts an English/numeric/underscore username of 4–24 chars
and a password of 8–128 chars. Usernames are case-insensitive, normalized to
lowercase. Passwords are hashed with a random salt and scrypt. Sessions expire
after 30 days; passwords and raw tokens are not logged or committed.

## Validation and next steps

- `.github/workflows/backend-smoke.yml`: Node syntax and health/auth smoke.
- `.github/workflows/build-apk.yml`: Kotlin HPE unit tests and Android APK build.
- `.github/workflows/ui-smoke.yml`: demo/report/chat screen checks.
- **Real cloud account writes cannot be verified without provisioning a DB.**
- A production release needs account recovery, a privacy policy, data export/deletion,
  durable backups and a reviewed security model.
