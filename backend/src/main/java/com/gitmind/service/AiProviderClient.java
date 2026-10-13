package com.gitmind.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import java.time.Duration;
import java.util.*;

/** Provider keys stay on the server. Auto tries Gemini, then Groq, at most once each. */
@Service
@lombok.extern.slf4j.Slf4j
public class AiProviderClient {
    private final RestTemplate rest;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String geminiUrl, geminiKey, groqUrl, groqKey, groqModel, provider;

    @org.springframework.beans.factory.annotation.Autowired
    public AiProviderClient(@Value("${gemini.api.url}") String geminiUrl,
            @Value("${gemini.api.key:}") String geminiKey,
            @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}") String groqUrl,
            @Value("${groq.api.key:}") String groqKey,
            @Value("${groq.api.model:openai/gpt-oss-20b}") String groqModel,
            @Value("${ai.provider:auto}") String provider) {
        this(geminiUrl, geminiKey, groqUrl, groqKey, groqModel, provider,
                new RestTemplateBuilder().setConnectTimeout(Duration.ofSeconds(2)).setReadTimeout(Duration.ofSeconds(10)).build());
    }

    AiProviderClient(String geminiUrl, String geminiKey, String groqUrl, String groqKey,
            String groqModel, String provider, RestTemplate rest) {
        this.geminiUrl = geminiUrl; this.geminiKey = geminiKey;
        this.groqUrl = groqUrl; this.groqKey = groqKey; this.groqModel = groqModel;
        this.provider = provider.toLowerCase(Locale.ROOT); this.rest = rest;
    }

    public String cacheVariant() { return "insights-v2-" + provider + "-" + Integer.toHexString(Objects.hash(groqModel, geminiUrl)); }

    public String generate(String prompt) {
        List<String> candidates = switch (provider) {
            case "gemini" -> List.of("gemini");
            case "groq" -> List.of("groq");
            case "auto" -> List.of("gemini", "groq");
            default -> throw new IllegalStateException("Set AI_PROVIDER to auto, gemini, or groq.");
        };
        String keyName = provider.equals("gemini") ? "GEMINI_API_KEY" : provider.equals("groq") ? "GROQ_API_KEY" : "GEMINI_API_KEY or GROQ_API_KEY";
        String failure = "AI is not configured. Add " + keyName + " to the backend environment, then restart it.";
        List<String> failures = new ArrayList<>();
        for (String candidate : candidates) {
            if ((candidate.equals("gemini") ? geminiKey : groqKey).isBlank()) continue;
            try {
                String raw = candidate.equals("gemini") ? callGemini(prompt) : callGroq(prompt);
                ObjectNode result = AiInsights.validate(mapper.readTree(raw));
                result.put("provider", candidate);
                return mapper.writeValueAsString(result);
            } catch (HttpStatusCodeException ex) {
                int status = ex.getStatusCode().value();
                String reason = switch (status) {
                    case 401, 403 -> "API key or account access was rejected";
                    case 404 -> "model or endpoint is unavailable; check " + (candidate.equals("gemini") ? "GEMINI_API_URL" : "GROQ_MODEL or GROQ_API_URL");
                    case 400, 422 -> "request was rejected; check API key validity and model compatibility";
                    case 402 -> "account credits or billing are required";
                    case 429 -> "quota or rate limit was reached; check account credits or try later";
                    default -> status >= 500 ? "provider service error; try later" : "request could not be completed";
                };
                failures.add(candidate + " (HTTP " + status + "): " + reason);
                log.warn("AI provider {} returned HTTP {}", candidate, status);
            } catch (ResourceAccessException ex) {
                failures.add(candidate + ": timed out or could not be reached");
                log.warn("AI provider {} network timeout or connection failure", candidate);
            } catch (Exception ex) {
                failures.add(candidate + ": returned incomplete or invalid analysis");
                log.warn("AI provider {} returned invalid structured output", candidate);
            }
        }
        // Never expose provider response bodies, URLs, or credentials to the browser/logs.
        throw new IllegalStateException(failures.isEmpty() ? failure : "The AI provider request failed. " + String.join("; ", failures) + ".");
    }

    private String callGemini(String prompt) {
        var body = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("responseMimeType", "application/json", "maxOutputTokens", 2400));
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON); headers.set("x-goog-api-key", geminiKey);
        JsonNode response = rest.postForObject(geminiUrl, new HttpEntity<>(body, headers), JsonNode.class);
        if (response == null) throw new IllegalStateException("Empty response");
        var parts = response.path("candidates").path(0).path("content").path("parts");
        StringBuilder text = new StringBuilder();
        parts.forEach(part -> { if (!part.path("thought").asBoolean()) text.append(part.path("text").asText()); });
        return text.toString();
    }

    private String callGroq(String prompt) {
        var body = new HashMap<String, Object>();
        body.put("model", groqModel);
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        body.put("response_format", Map.of("type", "json_object"));
        body.put("max_completion_tokens", 4000);
        body.put("stream", false);
        if (groqModel.startsWith("openai/gpt-oss-")) body.put("reasoning_effort", "low");
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON); headers.setBearerAuth(groqKey);
        JsonNode response = rest.postForObject(groqUrl, new HttpEntity<>(body, headers), JsonNode.class);
        if (response == null) throw new IllegalStateException("Empty response");
        return response.path("choices").path(0).path("message").path("content").asText();
    }
}



