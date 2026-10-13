package com.gitmind.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.gitmind.client.GitHubApiClient;
import com.gitmind.dto.*;
import com.gitmind.exception.GitHubUserNotFoundException;
import com.gitmind.mapper.EventMapper;
import com.gitmind.mapper.ProfileMapper;
import com.gitmind.mapper.RepositoryMapper;
import com.gitmind.model.GitHubEvent;
import com.gitmind.model.GitHubProfile;
import com.gitmind.model.GitHubRepository;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Service that orchestrates GitHub data fetching and analysis.
 * Aggregates profile, repositories, events, and computes statistics.
 *
 * Resilience features:
 *  - profile/repos/events are fetched concurrently (githubFetchExecutor)
 *  - results are pre-cached in-memory for a short TTL (freshAnalysisCache)
 *  - circuit breaker "githubApi" trips on sustained failures and the service
 *    fails open by serving the last known-good result (staleAnalysisCache) instead of a
 *    hard error, so the core API stays available during a GitHub outage.
 *  - transient failures are retried with exponential backoff before the
 *    circuit breaker or fail-open path is ever engaged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubAnalysisService {

    private static final String RESILIENCE_INSTANCE = "githubApi";

    private final GitHubApiClient gitHubApiClient;
    private final ProfileMapper profileMapper;
    private final RepositoryMapper repositoryMapper;
    private final EventMapper eventMapper;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final Executor githubFetchExecutor;
    private final Cache<String, GitHubAnalysisDto> freshAnalysisCache;
    private final Cache<String, GitHubAnalysisDto> staleAnalysisCache;

    /**
     * Analyzes a GitHub user's profile comprehensively.
     *
     * @param username the GitHub username to analyze
     * @return comprehensive analysis DTO
     */
    public GitHubAnalysisDto analyzeUser(String inputUsername) {
        String username = Usernames.normalize(inputUsername);
        // pre-caching - a repeat lookup within the TTL skips GitHub entirely.
        GitHubAnalysisDto cached = freshAnalysisCache.getIfPresent(username);
        if (cached != null) {
            log.debug("Fresh cache hit for user: {}", username);
            return cached;
        }

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(RESILIENCE_INSTANCE);
        Retry retry = retryRegistry.retry(RESILIENCE_INSTANCE);

        Supplier<GitHubAnalysisDto> supplier = () -> fetchAndBuildAnalysis(username);
        Supplier<GitHubAnalysisDto> decorated =
                Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, supplier));

        try {
            GitHubAnalysisDto result = decorated.get();
            freshAnalysisCache.put(username, result);
            staleAnalysisCache.put(username, result);
            return result;
        } catch (CallNotPermittedException ex) {
            // Circuit is OPEN - GitHub has been failing consistently, don't even attempt the call.
            log.warn("Circuit breaker OPEN for {} - attempting fail-open for user {}", RESILIENCE_INSTANCE, username);
            return failOpenOrThrow(username, ex);
        } catch (GitHubUserNotFoundException ex) {
            // A genuine 404 is not an infrastructure failure - never fail-open on this, just propagate it.
            throw ex;
        } catch (Exception ex) {
            log.error("analyzeUser failed for {} after retries: {}", username, ex.getMessage());
            return failOpenOrThrow(username, ex);
        }
    }

    /**
     * if we have a previously-successful result for this user,
     * serve it - stale data beats a 5xx. Otherwise there is genuinely nothing we can do,
     * so the original failure propagates to the client as normal.
     */
    private GitHubAnalysisDto failOpenOrThrow(String username, Exception ex) {
        GitHubAnalysisDto stale = staleAnalysisCache.getIfPresent(username);
        if (stale != null) {
            log.warn("Fail-open: serving last known-good analysis for {} while GitHub API is degraded", username);
            return stale;
        }
        if (ex instanceof RuntimeException re) {
            throw re;
        }
        throw new IllegalStateException("Failed to analyze user: " + username, ex);
    }

    private GitHubAnalysisDto fetchAndBuildAnalysis(String username) {
        log.info("Starting analysis for user: {}", username);
        long startTime = System.currentTimeMillis();

        // fetch profile, repos, and events concurrently instead of sequentially.
        CompletableFuture<GitHubProfile> profileFuture =
                CompletableFuture.supplyAsync(() -> gitHubApiClient.fetchUserProfile(username), githubFetchExecutor);
        CompletableFuture<List<GitHubRepository>> reposFuture =
                CompletableFuture.supplyAsync(() -> gitHubApiClient.fetchUserRepositories(username), githubFetchExecutor);
        CompletableFuture<List<GitHubEvent>> eventsFuture =
                CompletableFuture.supplyAsync(() -> gitHubApiClient.fetchUserEvents(username), githubFetchExecutor);

        GitHubProfile profile = joinUnwrapped(profileFuture);
        List<GitHubRepository> repositories = joinUnwrapped(reposFuture);
        List<GitHubEvent> events = joinUnwrapped(eventsFuture);

        // Map to DTOs
        ProfileDto profileDto = profileMapper.toDto(profile);
        List<RepositoryDto> repositoryDtos = repositories.stream()
                .map(repositoryMapper::toDto)
                .collect(Collectors.toList());
        List<EventDto> eventDtos = events.stream()
                .map(eventMapper::toDto)
                .collect(Collectors.toList());

        // Compute statistics
        List<LanguageStatsDto> languageStats = computeLanguageStats(repositories);
        RepositoryStatsDto repositoryStats = computeRepositoryStats(repositories);

        GitHubAnalysisDto result = GitHubAnalysisDto.builder()
                .profile(profileDto)
                .repositories(repositoryDtos)
                .languageStats(languageStats)
                .repositoryStats(repositoryStats)
                .recentEvents(eventDtos)
                .build();

        long duration = System.currentTimeMillis() - startTime;
        log.info("Analysis completed for user: {} in {}ms", username, duration);

        return result;
    }

    /**
     * CompletableFuture.join() always wraps the original exception in a
     * CompletionException. Unwrap it so Resilience4j's retry-exceptions matching
     * (and our own catch blocks) see the real GitHubApiException / GitHubRateLimitException
     * / GitHubUserNotFoundException, not a generic wrapper.
     */
    private <T> T joinUnwrapped(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw ex;
        }
    }

    /**
     * Computes language distribution statistics from repositories.
     */
    private List<LanguageStatsDto> computeLanguageStats(List<GitHubRepository> repositories) {
        if (repositories == null || repositories.isEmpty()) {
            return Collections.emptyList();
        }

        // Count repositories per language
        Map<String, Integer> repoCountByLanguage = new HashMap<>();
        Map<String, Long> totalSizeByLanguage = new HashMap<>();

        for (GitHubRepository repo : repositories) {
            if (Boolean.TRUE.equals(repo.getFork())) continue;
            String language = repo.getLanguage();
            if (language != null && !language.isEmpty()) {
                repoCountByLanguage.merge(language, 1, Integer::sum);
                totalSizeByLanguage.merge(language, repo.getSize() != null ? repo.getSize() : 0, Long::sum);
            }
        }

        int totalReposWithLanguage = repoCountByLanguage.values().stream()
                .mapToInt(Integer::intValue)
                .sum();

        if (totalReposWithLanguage == 0) {
            return Collections.emptyList();
        }

        return repoCountByLanguage.entrySet().stream()
                .map(entry -> {
                    String lang = entry.getKey();
                    int count = entry.getValue();
                    return LanguageStatsDto.builder()
                            .language(lang)
                            .repositoryCount(count)
                            .totalSize(totalSizeByLanguage.getOrDefault(lang, 0L))
                            .percentage(Math.round((count * 100.0 / totalReposWithLanguage) * 100.0) / 100.0)
                            .build();
                })
                .sorted((a, b) -> Integer.compare(b.getRepositoryCount(), a.getRepositoryCount()))
                .collect(Collectors.toList());
    }

    /**
     * Computes aggregated repository statistics.
     */
    private RepositoryStatsDto computeRepositoryStats(List<GitHubRepository> repositories) {
        if (repositories == null || repositories.isEmpty()) {
            return RepositoryStatsDto.builder()
                    .totalStars(0)
                    .totalForks(0)
                    .totalOpenIssues(0)
                    .averageRepoSize(0L)
                    .mostUsedLanguage("N/A")
                    .totalRepositories(0)
                    .originalRepositories(0)
                    .forkedRepositories(0)
                    .build();
        }

        int totalStars = 0;
        int totalForks = 0;
        int totalOpenIssues = 0;
        long totalSize = 0;
        int originalCount = 0;
        int forkedCount = 0;

        Map<String, Integer> languageFrequency = new HashMap<>();

        for (GitHubRepository repo : repositories) {
            if (Boolean.TRUE.equals(repo.getFork())) { forkedCount++; continue; }
            totalStars += repo.getStargazersCount() != null ? repo.getStargazersCount() : 0;
            totalForks += repo.getForksCount() != null ? repo.getForksCount() : 0;
            totalOpenIssues += repo.getOpenIssuesCount() != null ? repo.getOpenIssuesCount() : 0;
            long size = repo.getSize() != null ? repo.getSize() : 0;
            totalSize += size;

            originalCount++;



            if (repo.getLanguage() != null) {
                languageFrequency.merge(repo.getLanguage(), 1, Integer::sum);
            }


        }

        String mostUsedLanguage = languageFrequency.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("N/A");

        return RepositoryStatsDto.builder()
                .totalStars(totalStars)
                .totalForks(totalForks)
                .totalOpenIssues(totalOpenIssues)
                .averageRepoSize(originalCount == 0 ? 0 : totalSize / originalCount)
                .mostUsedLanguage(mostUsedLanguage)
                .totalRepositories(repositories.size())
                .originalRepositories(originalCount)
                .forkedRepositories(forkedCount)
                .build();
    }
}
