# Architecture

CareerPilot is a modular monolith. A React + TypeScript + Vite client calls a Spring Boot REST API. The API owns authentication, authorization, validation, domain rules, persistence, document parsing, and explainable scoring. PostgreSQL is the production relational target; H2 is the local default. Flyway owns schema evolution and Hibernate runs in validation mode.

## Security and ownership

Browser authentication uses server-managed sessions. Passwords are BCrypt encoded. Every private-resource query includes an owner check in the API; opaque/guessed IDs alone do not grant access. CSRF protects state-changing requests. CORS origins are configurable. Uploaded files use generated UUID storage names in per-account directories and are parsed as untrusted input by Apache Tika.

## Modules

- `AuthController` and `PrivacyController`: register/login/logout/current profile/update/delete.
- `CareerController`: owned applications, dashboard counts, analytics, CSV, and persisted analyses.
- `ResumeController`: upload, parsing, download, text editing, versions, and deletion.
- `WorkspaceController`: interviews, deadlines, and follow-up tasks.
- `InterviewController` and `InterviewQuestionService`: deterministic demonstration question generation and saved practice responses.

`CareerAiService` has deterministic demo behavior plus configurable OpenAI-compatible providers (including Groq) for resume/job evidence reviews, tailored interview practice, and opt-in aggregate career insights. Provider API keys are read only by the backend (`GROQ_API_KEY`, `OPENAI_API_KEY`, AgentRouter key aliases, or the legacy `CAREERPILOT_AI_API_KEY`) and are never returned to the client. AI requests send only the feature inputs: selected resume/job text for analysis, role/job description for practice prompts, and aggregate counts for career insights. Results are evidence hints and suggestions, not ATS scores or hiring probabilities. Demo mode is deterministic; if Groq is selected without its key, requests fail clearly instead of silently returning demo output.

## Storage and production

H2 file storage and `./data/resumes` are development defaults. PostgreSQL runs through Compose or a managed service. Production needs managed PostgreSQL, TLS, secure cookies, environment-managed credentials, and private persistent object storage; implement and verify an object-storage adapter before hosting user resumes on an ephemeral platform.
