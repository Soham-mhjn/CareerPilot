# CareerPilot

CareerPilot is a personal job-search workspace. The React client is connected to the Spring Boot API using an HttpOnly session cookie and CSRF tokens. Application records, resume text, analysis history, practice answers, and calendar items persist in the configured database; uploaded resume originals are stored in private per-account directories.

## Implemented

- Account registration, login/logout, profile updates, account deletion, BCrypt password hashing, expiring server-side sessions, CSRF protection, and user-scoped resource access.
- Application create/list/status/delete, text search, status filters, table and drag-and-drop Kanban views, dashboard counts, source/status analytics, and account-scoped CSV export.
- PDF, DOCX, and TXT resume upload (10 MB limit), text extraction, private download, editable text, and separate tailored versions. Original files are retained until the last version referencing them is removed.
- Configurable AI provider for resume/job evidence analysis, tailored interview practice, and opt-in aggregate career insights: Groq (OpenAI-compatible API), AgentRouter, direct OpenAI API, deterministic demo mode, or local Ollama. Results identify their provider; they are not ATS scores or hiring predictions.
- Date-filtered analytics (7/30/90 days or all time), application-creation timeline, interview progression, resolved-outcome rates, and source distribution. Cohorts use record creation dates because status history is not yet stored.
- Persisted interview and follow-up items, task completion, editing of future calendar events, and deterministic interview-practice questions with answer/self-assessment saving.
- Secure password reset links delivered through configurable SMTP, with hashed, expiring, single-use tokens.
- Append-only application stage history with per-application timelines, stage movement analytics, and average days to first interview.
- Responsive UI and empty states.

## Current limitations

- OpenAI API use requires separate API billing; a ChatGPT plan does not include API usage. OpenAI keys stay on the backend. When configured for a remote provider, selected resume text and job descriptions are sent to that provider. Demo mode is local. The application can also connect to local Ollama without API charges, but requires installing Ollama and downloading a model. AI question sets are practice aids, not predictions.
- Resume storage is local filesystem storage. For production, use a private persistent disk or implement a managed object-storage adapter before deployment. The database and file storage must be backed up together.
- Analytics dates mean application-record creation date, not actual submission date. Stage transitions are now timestamped, but response-time and stage dwell-time reports still need explicit definitions and additional tests.
- The internal calendar now has a month grid and selected-day event details. It does not send reminders or notifications and has no timezone preference UI.
- No browser extension, job-board scraping, automated email, password reset, rate limiter, or end-to-end browser suite is included.
- Production still needs managed object storage, login/register rate limiting backed by shared storage, password reset delivery, deployment automation, and a security/performance review.
- UI/API and backend integration tests cover core account-scoped flows, but are not an exhaustive security or production-readiness audit.

## Prerequisites

- Node.js 20+ and npm
- Java 21+ (tested here with Java 22) and Maven 3.9+
- Docker Desktop only if you want PostgreSQL through Compose

## Backend structure

Backend Java code is grouped by responsibility under `backend/src/main/java/com/careerpilot`:

```text
com.careerpilot
├── CareerPilotApplication.java
├── config/       Security and application configuration
├── controller/   REST endpoints and API exception handling
├── entity/       JPA entities
├── repository/   Spring Data repositories
└── service/      AI and interview-question business logic
```

## Run locally

The default API uses H2 file storage, so PostgreSQL and Docker are optional for development.

Terminal 1, API:

```powershell
cd backend
mvn spring-boot:run
```

To use free-tier Groq inference, create an API key at [Groq Console](https://console.groq.com/keys), set `GROQ_API_KEY` in the project-root `.env`, and run `backend\\run-with-groq.ps1` on Windows. The checked-in defaults use `https://api.groq.com/openai/v1` and `openai/gpt-oss-20b`; check Groq's current [model list](https://console.groq.com/docs/models) and your account's [rate limits](https://console.groq.com/docs/rate-limits). Free access is subject to model availability and account limits. Spring Boot loads the root `.env` whether launched from the repository root or `backend`. Keep `.env` private; the frontend never receives provider keys. The backend reports a missing Groq key instead of silently presenting demo output.

To use AgentRouter, set `CAREERPILOT_AI_PROVIDER=agentrouter`, put the gateway key in `CODEX_GATEWAY_API_KEY` (or `AGENTROUTER_API_KEY`), and select a supported model and base URL. Its gateway may reject direct custom app clients.

Terminal 2, frontend:

```powershell
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. The frontend proxies `/api` to http://localhost:8080. Register a new account in the sign-in screen. The local API creates the `./backend/data` database and `./backend/data/resumes` document directory on first start. Resume uploads are limited to 10 MB at both the HTTP layer and validation layer.

Health: http://localhost:8080/actuator/health  
API reference: http://localhost:8080/swagger-ui.html

## PostgreSQL

```powershell
docker compose up -d postgres
```

Set these environment variables before starting the API:

```powershell
$env:DATABASE_URL='jdbc:postgresql://localhost:5432/careerpilot'
$env:DATABASE_USERNAME='careerpilot'
$env:DATABASE_PASSWORD='careerpilot_local'
$env:CORS_ALLOWED_ORIGINS='http://localhost:5173'
```

Flyway runs versioned migrations at startup; Hibernate validates the schema. The Compose password is for local development only. Do not use it in a deployed environment.

## Security and configuration

- Browser authentication is server-managed and stored in an HttpOnly session cookie. State-changing requests use a session CSRF token.
- Configure `COOKIE_SECURE=true` behind HTTPS and set `CORS_ALLOWED_ORIGINS` to the exact trusted frontend origin(s).
- AI settings are `CAREERPILOT_AI_PROVIDER` (`groq`, `agentrouter`, `openai`, `demo`, or `ollama`), `GROQ_API_KEY`, `CODEX_GATEWAY_API_KEY` (or `AGENTROUTER_API_KEY`), `OPENAI_API_KEY`, `OPENAI_MODEL`, and `CAREERPILOT_AI_BASE_URL`. Legacy `CAREERPILOT_AI_API_KEY` and `CAREERPILOT_AI_MODEL` aliases are supported. Groq uses `https://api.groq.com/openai/v1` and a Groq model ID. AgentRouter uses `https://agentrouter.org/v1`; use an exact model ID enabled for your key. The backend sends OpenAI-compatible Chat Completions requests, which is separate from Codex CLI's `wire_api = "responses"` setting. Direct OpenAI uses `https://api.openai.com/v1`; Ollama can use `http://localhost:11434/v1` with a locally pulled model and no API key. Keep credentials in the backend environment only. The AI Coach has an opt-in button for insights from aggregate application metrics.
- To use free local inference on Windows, install [Ollama](https://ollama.com/download/windows), then run `ollama pull llama3.2:3b`. In the backend PowerShell terminal set `$env:CAREERPILOT_AI_PROVIDER='ollama'`, `$env:CAREERPILOT_AI_MODEL='llama3.2:3b'`, and `$env:CAREERPILOT_AI_BASE_URL='http://localhost:11434/v1'` before `mvn spring-boot:run`. The installer and model download are separate from the app and require local disk/RAM.
- To use AgentRouter, set the provider, key from the account's token console, base URL (`https://agentrouter.org/v1`), and model in the root `.env`, then start `backend\run-with-agentrouter.ps1`. The launcher now reads the configured base URL instead of overriding it. AgentRouter's other [OpenAI-compatible setup guide](https://co.agentrouter.org/portal/guide) documents the same Chat Completions protocol on its `co` hostname. To use direct OpenAI instead, set `CAREERPILOT_AI_PROVIDER=openai`, set `OPENAI_API_KEY`, and use `https://api.openai.com/v1`.
- On Windows, `backend\run-with-openai.ps1` starts the API with the OpenAI provider enabled and reads the key from the root `.env` automatically. Do not launch a second backend while one is already using the local H2 database.
- `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `CORS_ALLOWED_ORIGINS`, `COOKIE_SECURE`, `PORT`, and `careerpilot.storage.path` are environment-configurable. `.env.example` contains placeholders only.
- Password reset requires SMTP settings in the repository-root `.env`: set `MAIL_ENABLED=true`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, and `CAREERPILOT_FRONTEND_URL`. For Gmail, use an app password. Restart the backend after configuring these values. Reset links expire after 30 minutes and can only be used once.
- Resume uploads are private and scoped to the authenticated account. Only PDF, DOCX, and TXT extensions and matching basic signatures are accepted; uploaded files are never executed.
- Do not expose database or AI credentials in frontend variables.

## Test and build

```powershell
cd frontend
npm test
npm run build
cd ..\backend
mvn test
mvn package
```

The backend integration tests use an isolated in-memory H2 database. The frontend tests cover signed-out and authenticated API-backed app states.

## Deployment

Use managed PostgreSQL, HTTPS, exact CORS origins, external secret management, and persistent private object storage. Configure storage so each user's file paths remain isolated. Apply Flyway migrations during deployment. This repository does not yet contain a cloud object-storage adapter, production container image, or complete deployment automation; do not deploy resume storage to ephemeral local disk.
