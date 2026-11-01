# GitMind AI

Analyze any public GitHub profile in seconds: repositories, languages, activity, a contribution heatmap, side-by-side comparison of two developers, and an optional AI-written developer profile (Gemini or Groq).

**Live demo:** https://gitmindgitapp.vercel.app/

> The backend runs on a free hosting tier and sleeps when idle. The very first request after a long pause can take up to a minute while it wakes up; the app shows a notice while this happens. After that it responds normally.

## Features

- **Profile analytics:** bio, followers, following, account age and public repository counts.
- **Repository insights:** stars, forks, issues and sizes for original repositories (forks are listed separately and excluded from totals).
- **Languages:** primary-language breakdown by repository count (not code volume).
- **Activity:** recent public events.
- **Contribution heatmap:** GitHub contribution calendar (needs a `GITHUB_TOKEN` on the backend).
- **Compare:** put two profiles next to each other.
- **AI developer profile (optional):** likely focus, project types, strengths, notable projects and next steps, validated against the real repositories before it is shown. See [AI-SETUP.md](AI-SETUP.md).
- **Reliability:** in-memory caching with stale fallback, a circuit breaker and retries around GitHub, and a rolling-window rate limiter.
- **Deep links:** every dashboard has a shareable URL, e.g. `/dashboard/torvalds` or `/compare/torvalds/gaearon`.

## Tech stack

| Layer | Technology |
| --- | --- |
| Frontend | React 19, TypeScript, Vite, Tailwind CSS, React Router 7, TanStack Query, Recharts |
| Backend | Java 21, Spring Boot 3.3, Spring Data JPA, Caffeine cache, Resilience4j |
| Database | PostgreSQL (stores generated AI reports) |
| AI | Google Gemini and/or Groq (optional, backend only) |
| Deployment | Docker, Render (backend), Vercel (frontend), Nginx for the Docker Compose setup |

## How it works

```
Browser (React, Vercel)
   |  GET /api/github/{username}
   |  GET /api/github/{username}/contributions
   |  GET /api/ai/summary/{username}
   v
Spring Boot API (Render)
   |-- Rate limiter (per client IP, 30 requests / 60 s)
   |-- Cache (fresh 10 min, stale fallback up to 6 h)
   |-- GitHub REST + GraphQL (circuit breaker + retry)
   |-- Gemini / Groq (only when "Analyze developer" is clicked)
   `-- PostgreSQL (AI reports, 24 h expiry)
```

## API

| Method | Path | Description |
| --- | --- | --- |
| GET | `/api/github/{username}` | Profile, repositories, language stats, recent events |
| GET | `/api/github/{username}/contributions` | Contribution calendar (requires `GITHUB_TOKEN`) |
| GET | `/api/ai/summary/{username}` | AI developer profile (requires an AI key) |
| GET | `/actuator/health` | Health check (use this for uptime monitors) |

Usernames are validated and normalized case-insensitively. Errors are returned as JSON with a readable `message`.

## Run locally

Requirements: Java 21, Maven, Node 22 and PostgreSQL.

**Option 1: run each part**

1. Create a PostgreSQL database named `gitmind`.
2. Backend:
   ```bash
   cd backend
   mvn spring-boot:run
   ```
3. Frontend (new terminal):
   ```bash
   cd app
   npm ci
   npm run dev
   ```
4. Open http://localhost:3000. The dev server proxies `/api` to port 8080.

Database settings can be overridden with `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`.

**Option 2: Docker Compose**

```bash
cp .env.example .env
docker compose up --build
```

Open http://localhost:3000. Compose starts PostgreSQL, the backend and an Nginx frontend that proxies `/api` and handles deep-link routing.

## Configuration

Backend environment variables (never put these in frontend variables):

| Variable | Required | Purpose |
| --- | --- | --- |
| `GITHUB_TOKEN` | No | Enables the contribution heatmap and raises the GitHub quota from 60 to 5,000 requests/hour |
| `GEMINI_API_KEY` | No | Enables AI profiles via Gemini |
| `GROQ_API_KEY` | No | Enables AI profiles via Groq |
| `AI_PROVIDER` | No | `auto` (default), `gemini` or `groq` |
| `GROQ_MODEL` | No | Override the default Groq model |
| `CORS_ALLOWED_ORIGINS` | For split hosting | Comma-separated frontend origins, e.g. `https://gitmindgitapp.vercel.app` |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | Yes | PostgreSQL connection |

Frontend (build-time) variable:

| Variable | Purpose |
| --- | --- |
| `VITE_API_URL` | Base URL of the backend, e.g. `https://gitmind-gitanalyzerv-1.onrender.com`. Leave empty when the frontend and backend share one origin (Docker Compose, dev proxy) |

Keys are optional. Features whose key is missing show an explicit message instead of failing.

## Deployment

**Backend on Render**

1. New Web Service, runtime **Docker**, root directory `backend`.
2. Add the environment variables above, especially the database settings and `CORS_ALLOWED_ORIGINS`.
3. Health check path: `/actuator/health`.

**Frontend on Vercel**

1. Import the repository and set the root directory to `app`.
2. Set `VITE_API_URL` to your Render backend URL, then redeploy (Vite reads it at build time).
3. `app/vercel.json` rewrites every path to `index.html`, so deep links such as `/dashboard/torvalds` work when opened directly or refreshed.

**Keeping a free backend awake (optional)**

Free Render services sleep after about 15 minutes without traffic. Point an uptime monitor (for example UptimeRobot, HTTP monitor, 5 minute interval) at the backend, not the frontend:

```
https://<your-backend>.onrender.com/actuator/health
```

Do not monitor `/api/github/...`; that would spend GitHub quota on every ping.

## Tests and checks

```bash
cd backend && mvn test          # backend tests
cd app && npm run lint          # ESLint
cd app && npm run build         # TypeScript check + production build
```

The backend suite covers AI cache expiry and output validation, provider fallback, bounded prompts, case-insensitive caching, JSON validation errors, rate limiting (including forwarded-header bypass), fork-aware totals and GitHub error handling.

## Project structure

```
.
├── app/                 React frontend (Vite)
│   ├── src/pages        Landing, Search, Dashboard, Compare
│   ├── src/components   Charts, cards, heatmap, AI summary, error and loading states
│   ├── src/api          Axios client
│   └── vercel.json      SPA fallback for deep links
├── backend/             Spring Boot API
│   └── src/main/java/com/gitmind
│       ├── controller   REST endpoints
│       ├── service      GitHub analysis, contributions, AI analysis
│       ├── client       GitHub REST/GraphQL clients
│       ├── ratelimit    Rolling-window limiter and filter
│       ├── config       Cache, CORS, properties
│       └── exception    JSON error handling
├── docker-compose.yml   PostgreSQL + backend + Nginx frontend
├── AI-SETUP.md          AI provider setup and report details
└── CHANGES.md           Change log and verification notes
```

## Limitations

- Public GitHub data only. There is no authentication.
- Rate limiting is per backend instance and uses the direct peer address; it ignores forwarded headers. Behind a reverse proxy you need a trusted-proxy strategy for distinct client IPs. There is no distributed limiter.
- Repository fetches are capped at 1,000. Language charts count primary languages of original repositories, not lines of code.
- AI reports use public metadata only (not source code, README files or private repositories) and can be inaccurate. Confidence describes how well the evidence supports the interpretation, not developer ability.
- Free hosting means occasional cold starts on the first request.

## License

Add a license of your choice before sharing the code publicly.
