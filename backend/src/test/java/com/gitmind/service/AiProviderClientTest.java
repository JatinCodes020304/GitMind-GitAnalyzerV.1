package com.gitmind.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AiProviderClientTest {
    private static final String GEMINI = "https://gemini.test/generate";
    private static final String GROQ = "https://groq.test/chat/completions";
    private final RestTemplate rest = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private AiProviderClient client(String provider, String geminiKey, String groqKey) {
        return new AiProviderClient(GEMINI, geminiKey, GROQ, groqKey, "test-model", provider, rest);
    }
    private String groqResponse(String report) throws Exception {
        return mapper.writeValueAsString(Map.of("choices", List.of(Map.of("message", Map.of("content", report)))));
    }
    @Test void missingKeysDoNotMakeNetworkRequests() {
        var error = assertThrows(IllegalStateException.class, () -> client("auto", "", "").generate("JSON"));
        assertTrue(error.getMessage().contains("not configured"));
        server.verify();
    }
    @Test void autoFallsBackWhenGeminiRejectsKey() throws Exception {
        server.expect(requestTo(GEMINI)).andExpect(header("x-goog-api-key", "fake-gemini"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("sensitive provider detail"));
        server.expect(requestTo(GROQ)).andExpect(header("Authorization", "Bearer fake-groq"))
                .andExpect(jsonPath("max_completion_tokens").value(4000))
                .andExpect(jsonPath("response_format.type").value("json_object"))
                .andRespond(withSuccess(groqResponse(AiTestData.REPORT), MediaType.APPLICATION_JSON));
        var report = mapper.readTree(client("auto", "fake-gemini", "fake-groq").generate("JSON"));
        assertEquals("groq", report.path("provider").asText());
        server.verify();
    }
    @Test void autoFallsBackOnInvalidGeminiOutput() throws Exception {
        String response = mapper.writeValueAsString(Map.of("candidates", List.of(Map.of("content", Map.of("parts", List.of(Map.of("text", "{\"summary\":\"incomplete\"}")))))));
        server.expect(requestTo(GEMINI)).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        server.expect(requestTo(GROQ)).andRespond(withSuccess(groqResponse(AiTestData.REPORT), MediaType.APPLICATION_JSON));
        assertTrue(client("auto", "fake", "fake").generate("JSON").contains("groq"));
        server.verify();
    }
    @Test void explicitProviderDoesNotSilentlySendDataToOtherProvider() {
        server.expect(requestTo(GROQ)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("secret"));
        var error = assertThrows(IllegalStateException.class, () -> client("groq", "fake", "fake").generate("JSON"));
        assertTrue(error.getMessage().contains("quota"));
        assertFalse(error.getMessage().contains("secret"));
        server.verify();
    }
    @Test void invalidConfidenceAndExcessiveListsAreRejected() throws Exception {
        var report = mapper.readTree(AiTestData.REPORT);
        ((com.fasterxml.jackson.databind.node.ObjectNode)report.path("developerType")).put("confidence", "expert");
        assertThrows(IllegalStateException.class, () -> AiInsights.validate(report));
    }
    @Test void modelUnavailableIncludesSafeProviderAndStatus() {
        server.expect(requestTo(GROQ)).andRespond(withStatus(HttpStatus.NOT_FOUND).body("secret provider details"));
        var error = assertThrows(IllegalStateException.class, () -> client("groq", "", "fake").generate("JSON"));
        assertTrue(error.getMessage().contains("groq (HTTP 404)"));
        assertFalse(error.getMessage().contains("secret"));
        server.verify();
    }
    @Test void autoReportsBothFailuresWithoutProviderBodies() {
        server.expect(requestTo(GEMINI)).andRespond(withStatus(HttpStatus.BAD_REQUEST).body("fake-gemini-key"));
        server.expect(requestTo(GROQ)).andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED).body("fake-groq-key"));
        var error = assertThrows(IllegalStateException.class, () -> client("auto", "fake", "fake").generate("JSON"));
        assertTrue(error.getMessage().contains("gemini (HTTP 400)"));
        assertTrue(error.getMessage().contains("groq (HTTP 402)"));
        assertFalse(error.getMessage().contains("fake-"));
        server.verify();
    }
    @Test void geminiRequestDoesNotSendLegacyThinkingBudget() throws Exception {
        String response = mapper.writeValueAsString(Map.of("candidates", List.of(Map.of("content", Map.of("parts", List.of(Map.of("text", AiTestData.REPORT)))))));
        server.expect(requestTo(GEMINI)).andExpect(jsonPath("generationConfig.thinkingConfig").doesNotExist())
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertTrue(client("gemini", "fake", "").generate("JSON").contains("gemini"));
        server.verify();
    }
}

