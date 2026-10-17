# GitMind AI

Public GitHub profile analysis with optional Gemini summaries. This application has no authentication and uses per-instance rate limiting.
## Live Link 
https://gitmindgitapp.vercel.app/
## Run locally

Requires Java 21, Maven, Node 22, and PostgreSQL. Start PostgreSQL with a `gitmind` database, then run `mvn spring-boot:run` in `backend` and `npm ci && npm run dev` in `app`. The frontend opens on port 3000 and proxies `/api` to port 8080. Database credentials can be set with `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`.

Alternatively, copy `.env.example` to `.env` and run `docker compose up --build`. Open http://localhost:3000. Compose includes PostgreSQL, the backend, and an Nginx frontend with deep-route fallback.

`GITHUB_TOKEN` enables contribution calendars and raises GitHub REST quotas. `GEMINI_API_KEY` or `GROQ_API_KEY` enables the dashboard's Analyze developer button. Keys are optional; unavailable features show an explicit message. Never put these keys in frontend environment variables. See [AI setup and features](AI-SETUP.md) for provider selection and the expanded developer profile.

GitHub results are cached for 10 minutes, with outage fallback up to 6 hours. Usernames are normalized case-insensitively. AI summaries expire after 24 hours; Gemini receives aggregate metrics and up to 12 original repositories, with bounded descriptions. AI output may be inaccurate. Repository fetches are capped at 1,000; language charts count primary languages of original repositories, not code volume. Aggregate stars, forks, issues, and sizes exclude forked repositories.

Rate limiting uses the direct peer address and ignores forwarded headers. Configure a trusted reverse proxy strategy before deployments requiring distinct client IPs behind a proxy. There is no distributed rate limiter or authentication.

## Verification

Run `npm run build` and `npm run lint` in `app`; run `mvn test` in `backend`. Health is exposed at `/actuator/health`. Docker and live provider calls require their respective runtime and credentials.

