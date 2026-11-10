# VPE GPT backend (Render)

Node.js 22+, native HTTP and fetch, no dependencies. This is an **API proxy**, not part of the APK. The APK sends HPE personality and verified vehicle-state context; this service calls OpenAI Responses API.

### Render
- New Web Service from this GitHub repository on `main`, Node runtime.
- Build command: `echo backend-ready`
- Start command: `node server/index.mjs`
- Environment variables: `OPENAI_API_KEY` (secret), `VPE_ACCESS_TOKEN` (a long random secret), optionally `OPENAI_MODEL` (default `gpt-4.1-mini`).
- Health endpoint: `GET /health` (returns readiness/configured booleans, not secrets).
- Chat endpoint: `POST /api/chat` with `Authorization: Bearer <VPE_ACCESS_TOKEN>`.
- Open VPE app -> **GPT 연결 설정** -> paste Render HTTPS URL + VPE_ACCESS_TOKEN (not OPENAI_API_KEY).

Keep OPENAI_API_KEY entirely off the APK and GitHub. The token is manually entered in the app and should not be committed to the repository. The backend rejects requests without it, limits requests to 12/min per IP, and caps the prompt size/output.

### Request
```json
{"message":"오늘 기분이 어때?","context":{"demo":true,"mbti":"ENFP","traits":{},"affect":{},"vehicle":{},"memory":{}},"history":[]}
```
Response: `{"reply":"...","model":"..."}`. Errors are JSON with `error` code.

OpenAI charges usage separately from ChatGPT membership. Set spending limits in the API platform.
