# AI developer profiles

The **Analyze developer** button generates a structured profile from a profile's public metadata: a likely developer focus (with confidence and repository evidence), project categories, strengths suggested by the public work, representative projects and optional next steps.

It does not inspect source code, private repositories, README files or employment history. Confidence describes how much the supplied evidence supports the interpretation, not developer ability.

## Enable it

Set at least one provider key in the **backend** environment. Keys belong in the backend only, never in `VITE_*` variables or frontend files.

| Variable | Purpose |
| --- | --- |
| `GEMINI_API_KEY` | Use Gemini |
| `GROQ_API_KEY` | Use Groq |
| `AI_PROVIDER` | `auto` (default), `gemini` or `groq` |
| `GROQ_MODEL` | Override the default Groq model |
| `GEMINI_API_URL` | Override the default Gemini endpoint/model |

`auto` tries Gemini first and falls back to Groq when Gemini is unavailable or returns invalid structured output. Use `AI_PROVIDER=gemini` or `AI_PROVIDER=groq` to restrict requests to one provider.

**Local run (PowerShell):**

```powershell
$env:GEMINI_API_KEY="YOUR_GEMINI_KEY"
$env:GROQ_API_KEY="YOUR_GROQ_KEY"
$env:AI_PROVIDER="auto"
cd backend
mvn spring-boot:run
```

Keep your database variables set in the same window. Either provider key is enough.

**Local run (bash):**

```bash
export GEMINI_API_KEY="YOUR_GEMINI_KEY"
export GROQ_API_KEY="YOUR_GROQ_KEY"
export AI_PROVIDER="auto"
cd backend && mvn spring-boot:run
```

**Docker Compose:** put the keys in the root `.env` file (Compose loads it; Maven does not).

**Hosted (Render):** add the keys as environment variables on the backend service and redeploy.

## How it behaves

- Only clicking **Analyze developer** sends the bounded public metadata sample to a provider. With `auto`, a fallback may send it to both.
- Generated fields are validated before caching, and every referenced repository must exist in the supplied sample.
- The provider, sample size, generation time and analysis limits appear in the report.
- Reports expire after 24 hours. Invalid or degraded output is never cached.
- Missing keys, rejected keys, exhausted quota, network timeouts and invalid output each produce a distinct message.

## Notes

- The Groq integration uses `openai/gpt-oss-20b` in JSON object mode with `max_completion_tokens`, following [Groq's API reference](https://console.groq.com/docs/api-reference).
- Gemini defaults to `gemini-3.5-flash-lite`. Google limits some model versions for new projects; see [Gemini model availability](https://ai.google.dev/gemini-api/docs/deprecations/).
- A configured key also needs appropriate API access or credits. Consumer chat subscriptions do not configure this app.
- Provider HTTP requests are covered by tests with mocked responses. Live model calls depend on your keys and were not verified in the test suite.
