package com.gitmind.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.gitmind.client.*;
import com.gitmind.dto.*;
import com.gitmind.mapper.*;
import com.gitmind.model.*;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalysisRegressionTest {
    @Test void forksDoNotInflateMetricsAndMixedCaseUsesSameCache() {
        var client = mock(GitHubApiClient.class);
        var original = new GitHubRepository();
        original.setFork(false);
        original.setLanguage("Java");
        original.setStargazersCount(2);
        original.setSize(10L);
        var fork = new GitHubRepository();
        fork.setFork(true);
        fork.setLanguage("Python");
        fork.setStargazersCount(1000);
        fork.setSize(1000L);
        when(client.fetchUserRepositories("user")).thenReturn(List.of(original, fork));
        when(client.fetchUserEvents("user")).thenReturn(List.of());
        var service = new GitHubAnalysisService(client, mock(ProfileMapper.class), mock(RepositoryMapper.class),
                mock(EventMapper.class), CircuitBreakerRegistry.ofDefaults(), RetryRegistry.ofDefaults(),
                Runnable::run, Caffeine.newBuilder().build(), Caffeine.newBuilder().build());
        var result = service.analyzeUser("User");
        assertEquals(2, result.getRepositoryStats().getTotalStars());
        assertEquals(10L, result.getRepositoryStats().getAverageRepoSize());
        assertEquals(2, result.getRepositoryStats().getTotalRepositories());
        assertEquals(1, result.getRepositoryStats().getForkedRepositories());
        assertEquals(1, result.getLanguageStats().size());
        assertEquals("Java", result.getLanguageStats().getFirst().getLanguage());
        assertSame(result, service.analyzeUser("USER"));
        verify(client, times(1)).fetchUserRepositories("user");
    }

    @Test void calendarUsesGithubLevelsInsteadOfFixedCounts() throws Exception {
        var client = mock(GitHubGraphQLClient.class);
        when(client.fetchContributionCalendar("user")).thenReturn(new ObjectMapper().readTree("""
            {"totalContributions":100,"weeks":[{"contributionDays":[
              {"date":"2026-10-11","contributionCount":100,"contributionLevel":"FIRST_QUARTILE"},
              {"date":"2026-10-12","contributionCount":1,"contributionLevel":"FOURTH_QUARTILE"}
            ]}]}
            """));
        var service = new ContributionService(client, CircuitBreakerRegistry.ofDefaults(), RetryRegistry.ofDefaults(),
                Caffeine.newBuilder().build(), Caffeine.newBuilder().build());
        var result = service.getContributionCalendar("User");
        assertEquals(1, result.getDays().get(0).getLevel());
        assertEquals(4, result.getDays().get(1).getLevel());
        assertSame(result, service.getContributionCalendar("USER"));
        verify(client, times(1)).fetchContributionCalendar("user");
    }
}
