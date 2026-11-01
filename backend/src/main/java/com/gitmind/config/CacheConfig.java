package com.gitmind.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.gitmind.dto.ContributionCalendarDto;
import com.gitmind.dto.GitHubAnalysisDto;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * In-memory caches used for two distinct purposes:
 *
 * <ul>
 *   <li><b>fresh*</b> caches (pre-caching): short TTL, checked first on every
 *       request so repeat lookups skip GitHub/Gemini entirely and shrink response payloads
 *       to "already computed" instead of "recompute from scratch".</li>
 *   <li><b>stale*</b> caches (fail-open): much longer TTL, updated every time we
 *       get a good result, and only ever read when the circuit breaker is open or GitHub is
 *       failing. This is what lets the API keep answering with a slightly-old result instead
 *       of a hard error during an upstream outage.</li>
 * </ul>
 *
 * These are plain Caffeine caches (not Spring's {@code @Cacheable} abstraction) so the
 * service layer has full manual control over when to fall back to stale data.
 */
@Configuration
@RequiredArgsConstructor
public class CacheConfig {

    private final AppProperties appProperties;

    @Bean
    public Cache<String, GitHubAnalysisDto> freshAnalysisCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(appProperties.getCache().getFreshTtlMinutes()))
                .maximumSize(appProperties.getCache().getMaxEntries())
                .build();
    }

    @Bean
    public Cache<String, GitHubAnalysisDto> staleAnalysisCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(appProperties.getCache().getStaleTtlHours()))
                .maximumSize(appProperties.getCache().getMaxEntries())
                .build();
    }

    @Bean
    public Cache<String, ContributionCalendarDto> freshContributionCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(appProperties.getCache().getFreshTtlMinutes()))
                .maximumSize(appProperties.getCache().getMaxEntries())
                .build();
    }

    @Bean
    public Cache<String, ContributionCalendarDto> staleContributionCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(appProperties.getCache().getStaleTtlHours()))
                .maximumSize(appProperties.getCache().getMaxEntries())
                .build();
    }

    @Bean
    public Cache<String, com.gitmind.model.AiAnalysis> freshAiSummaryCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(appProperties.getCache().getFreshTtlMinutes()))
                .maximumSize(appProperties.getCache().getMaxEntries())
                .build();
    }
}
