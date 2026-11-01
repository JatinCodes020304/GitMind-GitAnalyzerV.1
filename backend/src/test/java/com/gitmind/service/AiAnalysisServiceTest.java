package com.gitmind.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.gitmind.model.AiAnalysis;
import com.gitmind.repository.AiAnalysisRepository;
import com.gitmind.dto.*;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AiAnalysisServiceTest {
    private final AiProviderClient gemini = mock(AiProviderClient.class);
    private final AiAnalysisRepository repository = mock(AiAnalysisRepository.class);
    private final com.github.benmanes.caffeine.cache.Cache<String, AiAnalysis> cache = Caffeine.newBuilder().build();
    private final AiAnalysisService service = new AiAnalysisService(cache, new ObjectMapper(), gemini, repository,
            CircuitBreakerRegistry.ofDefaults(), RetryRegistry.ofDefaults());
    { when(gemini.cacheVariant()).thenReturn("insights-v2"); }
    @Test void freshSummaryUsesNormalizedCache() {
        var record = new AiAnalysis("torvalds", "insights-v2", "{\"summary\":\"cached\"}");
        when(repository.findByUsernameAndAnalysisType("torvalds", "insights-v2")).thenReturn(Optional.of(record));
        assertEquals(record.getResultJson(), service.getDeveloperSummary("Torvalds", "{}"));
        assertEquals(record.getResultJson(), service.getDeveloperSummary("TORVALDS", "{}"));
        verify(repository, times(1)).findByUsernameAndAnalysisType("torvalds", "insights-v2");
        verify(gemini, never()).generate(anyString());
    }
    @Test void expiredSummaryIsUpdatedInsteadOfDuplicated() {
        var record = new AiAnalysis("torvalds", "insights-v2", "old");
        record.setCreatedAt(LocalDateTime.now().minusHours(25));
        cache.put("torvalds:insights-v2", record);
        when(repository.findByUsernameAndAnalysisType("torvalds", "insights-v2")).thenReturn(Optional.of(record));
        when(gemini.generate(anyString())).thenReturn(AiTestData.REPORT);
        assertTrue(service.getDeveloperSummary("Torvalds", "{}").contains("new"));
        verify(repository).save(record);
        assertTrue(record.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(1)));
    }
    @Test void invalidAiOutputIsDegradedAndNeverCached() {
        when(repository.findByUsernameAndAnalysisType(anyString(), anyString())).thenReturn(Optional.empty());
        when(gemini.generate(anyString())).thenReturn("not json");
        assertTrue(service.getDeveloperSummary("user", "{}").contains("\"degraded\":true"));
        assertNull(cache.getIfPresent("user"));
        verify(repository, never()).save(any());
    }
    @Test void promptIsBoundedAndExcludesForks() throws Exception {
        var repos = new ArrayList<RepositoryDto>();
        for (int i=0; i<1000; i++) repos.add(RepositoryDto.builder().name("repo"+i).description("x".repeat(1000)).fork(i%2==0).stargazersCount(i).build());
        var data = GitHubAnalysisDto.builder().repositories(repos).languageStats(List.of()).build();
        String prompt = service.compactData(data);
        var items = new ObjectMapper().readTree(prompt).path("selectedOriginalRepositories");
        assertEquals(12, items.size());
        assertEquals("repo999", items.get(0).path("name").asText());
        assertEquals(200, items.get(0).path("description").asText().length());
        assertTrue(prompt.length()<5000);
    }
    @Test void usernamesValidateAndNormalize() {
        assertEquals("torvalds", Usernames.normalize("Torvalds"));
        for (String invalid : List.of("-user", "user-", "a--b", "a/b", "", "a".repeat(40)))
            assertThrows(IllegalArgumentException.class, () -> Usernames.normalize(invalid));
    }
    @Test void inventedRepositoryReferencesAreRejected() throws Exception {
        when(repository.findByUsernameAndAnalysisType(anyString(), anyString())).thenReturn(Optional.empty());
        var report = new ObjectMapper().readTree(AiTestData.REPORT);
        ((com.fasterxml.jackson.databind.node.ArrayNode) report.path("notableProjects")).addObject()
                .put("repository", "invented-repository").put("reason", "Invented evidence");
        when(gemini.generate(anyString())).thenReturn(report.toString());
        assertTrue(service.getDeveloperSummary("user", "{}").contains("\"degraded\":true"));
        verify(repository, never()).save(any());
    }
}

