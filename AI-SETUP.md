# AI developer profiles

The old summary was a short paragraph. The new analysis provides a likely developer focus with confidence and repository evidence, project categories, strengths suggested by the public work, representative projects, and optional next steps. It does not inspect source code, private repositories, README files, or employment history. Confidence describes how much the supplied evidence supports the interpretation, not developer ability.

In the same PowerShell window you use for the backend, stop it with Ctrl+C, then set your keys and restart:

```powershell
$env:GEMINI_API_KEY="YOUR_GEMINI_KEY"
$env:GROQ_API_KEY="YOUR_GROQ_KEY"
$env:AI_PROVIDER="auto"
mvn spring-boot:run
```

Keep your existing database variables in this window. Either provider key is sufficient. `auto` tries Gemini first and falls back to Groq when Gemini is unavailable or returns invalid structured output. Use `AI_PROVIDER=gemini` or `AI_PROVIDER=groq` to restrict requests to one provider. `GROQ_MODEL` and `GEMINI_API_URL` can override the default models. Keys belong in the backend environment, never `VITE_*` or frontend files. A root `.env` is loaded by Docker Compose, not automatically by Maven.

Refresh the dashboard and click **Analyze developer**. Only this click sends the bounded public metadata sample to the configured provider; auto fallback may send it to both. Generated fields are validated before caching, and referenced repositories must exist in the supplied sample. Provider, sample size, generation time, and analysis limits appear in the report. Reports expire after 24 hours; the new version uses separate cache entries from the old paragraph summaries.

Missing keys, rejected keys, exhausted provider quota, network timeouts, and invalid output now have distinct messages. A configured key also needs appropriate API access/credits; consumer chat subscriptions do not configure this app.

The Groq integration uses `openai/gpt-oss-20b`, JSON object mode, and `max_completion_tokens`, following [Groq's API reference](https://console.groq.com/docs/api-reference). Gemini defaults to `gemini-3.5-flash-lite`, without the legacy thinking-budget field. Google currently limits 2.5 model access for new projects; see [Gemini model availability](https://ai.google.dev/gemini-api/docs/deprecations/). Provider HTTP requests are covered with mocked responses; live model calls require your keys and have not been verified.


