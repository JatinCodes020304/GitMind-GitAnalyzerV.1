# Changes and verification

## Deployment and cold-start fixes

- Added `app/vercel.json` so deep links (`/dashboard/:username`, `/compare/...`) no longer return 404 on Vercel when opened directly or refreshed.
- Raised the frontend API timeout from 30 s to 70 s. A sleeping free-tier backend can take about a minute to wake, and the old limit failed before it finished.
- Added a loading notice (`ColdStartNotice`) on the dashboard and compare pages that explains the delay after 6 seconds.
- Tuned the backend JVM flags for small instances (`TieredStopAtLevel=1`, `UseSerialGC`, `Xss512k`) for faster startup and lower memory use. Disabled `spring.jpa.open-in-view`.
- Fixed the root `.env.example` (removed the `GROK_MODEL` typo, added `CORS_ALLOWED_ORIGINS`).
- Rewrote `README.md` and cleaned `AI-SETUP.md`. The README now documents features, API, configuration, deployment and keep-alive monitoring with `/actuator/health`.

Not verified here: these edits were made without network access, so `npm run build`, `npm run lint` and `mvn test` were not re-run. Run them once locally before pushing.

Update: expanded AI developer profiles and Gemini/Groq support are described in [AI-SETUP.md](AI-SETUP.md). New reports have their own versioned cache entries. The suite now includes provider fallback, configuration errors, JSON validation, and invented-reference rejection tests.

- Connected an on-demand AI summary panel to the JSON API, with loading, retry, and degraded states. Shared username validation returns JSON 400 errors. Gemini is optional, uses connection/read timeouts and a configurable URL, and receives aggregates plus 12 original repositories with descriptions limited to 200 characters. Summaries are validated, stored by normalized username, refreshed after 24 hours, and read through the previously unused memory cache. Invalid/degraded output is never cached. The default Gemini model is based on [Google's model documentation](https://ai.google.dev/gemini-api/docs/models/gemini-2.5-flash).
- Normalized GitHub and contribution cache keys. Rate limits ignore forwarded headers, enforce concurrent admission atomically, skip CORS preflights, and run scheduled stale-key cleanup. Permission-related GitHub 403 responses remain 403; quota exhaustion becomes 429.
- Used GitHub contributionLevel values. Language charts explicitly count original repositories rather than code volume. Stars, forks, issues, and average sizes exclude forked repositories; original/fork/total inventory counts remain visible.
- Removed fictitious hero statistics, real-time claims, and the avatar status dot. Added contribution loading and error states, the Search navbar, and Footer Compare link. Removed navigation during render and duplicate Enter submission. Frontend errors use backend JSON messages.
- Used root-relative assets and same-origin API requests. Added Nginx API proxy and SPA fallback, frontend Docker build, PostgreSQL service with persistent storage, and startup health dependencies. Removed obsolete Compose version and custom health controller in favor of Actuator.
- Removed Vite starter pages/styles, scaffolding notes, Java-side npm lockfile, unused UI components and associated direct dependencies, inspection plugin, committed frontend environment file, redundant router package, unused model/DTO fields, identity mappings, and dead exception handlers. Added environment examples, ignore files, and an accurate README. Both external clients now use RestTemplate; WebFlux was removed.

Verification: 13 backend regression tests pass; frontend TypeScript/Vite build and ESLint pass; `docker compose config --quiet` passes. Tests cover AI cache expiry, output validation, bounded prompts, case-insensitive caching, JSON validation responses, concurrency and forwarded-header bypass, fork totals, GitHub heatmap levels, and forbidden/rate-limit distinction.

Runtime limits: Docker's engine was not running, so container startup was not tested. Live GitHub/Gemini requests were not tested with credentials. The frontend build reports a bundle-size warning. Compatible npm security updates reduced audit findings from 16 to 7; remaining findings affect the Tailwind 3 build toolchain and require a separate major-version migration. No authentication or distributed rate limiting was added.

