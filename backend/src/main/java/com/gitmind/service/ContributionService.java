package com.gitmind.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.gitmind.client.GitHubGraphQLClient;
import com.gitmind.dto.ContributionCalendarDto;
import com.gitmind.dto.ContributionDayDto;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Builds the contribution heatmap (like the one on a user's GitHub profile
 * page) from raw GraphQL data, including derived streak statistics.
 *
 * Wrapped with the same resilience pattern as GitHubAnalysisService: a short-TTL
 * cache for repeat lookups , a circuit breaker + retry with backoff around
 * the GraphQL call , and a fail-open fallback to the last known-good
 * calendar if GitHub's GraphQL API is degraded.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContributionService {

    private static final String RESILIENCE_INSTANCE = "githubGraphQL";

    private final GitHubGraphQLClient graphQLClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final Cache<String, ContributionCalendarDto> freshContributionCache;
    private final Cache<String, ContributionCalendarDto> staleContributionCache;

    public ContributionCalendarDto getContributionCalendar(String inputUsername) {
        String username = Usernames.normalize(inputUsername);
        ContributionCalendarDto cached = freshContributionCache.getIfPresent(username);
        if (cached != null) {
            log.debug("Fresh cache hit for contribution calendar: {}", username);
            return cached;
        }

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(RESILIENCE_INSTANCE);
        Retry retry = retryRegistry.retry(RESILIENCE_INSTANCE);

        Supplier<ContributionCalendarDto> supplier = () -> fetchAndBuildCalendar(username);
        Supplier<ContributionCalendarDto> decorated =
                Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, supplier));

        try {
            ContributionCalendarDto result = decorated.get();
            freshContributionCache.put(username, result);
            staleContributionCache.put(username, result);
            return result;
        } catch (CallNotPermittedException ex) {
            log.warn("Circuit breaker OPEN for {} - attempting fail-open for user {}", RESILIENCE_INSTANCE, username);
            return failOpenOrThrow(username, ex);
        } catch (Exception ex) {
            log.error("getContributionCalendar failed for {} after retries: {}", username, ex.getMessage());
            return failOpenOrThrow(username, ex);
        }
    }

    private ContributionCalendarDto failOpenOrThrow(String username, Exception ex) {
        ContributionCalendarDto stale = staleContributionCache.getIfPresent(username);
        if (stale != null) {
            log.warn("Fail-open: serving last known-good contribution calendar for {} while GitHub is degraded", username);
            return stale;
        }
        if (ex instanceof RuntimeException re) {
            throw re;
        }
        throw new IllegalStateException("Failed to fetch contribution calendar for: " + username, ex);
    }

    private ContributionCalendarDto fetchAndBuildCalendar(String username) {
        log.info("Fetching contribution calendar for user: {}", username);

        JsonNode calendarNode = graphQLClient.fetchContributionCalendar(username);
        int total = calendarNode.path("totalContributions").asInt(0);

        List<ContributionDayDto> rawDays = new ArrayList<>();
        JsonNode weeks = calendarNode.path("weeks");
        for (JsonNode week : weeks) {
            for (JsonNode day : week.path("contributionDays")) {
                int count = day.path("contributionCount").asInt(0);
                rawDays.add(ContributionDayDto.builder()
                        .date(day.path("date").asText())
                        .count(count)
                        .level(switch (day.path("contributionLevel").asText()) {
                            case "FIRST_QUARTILE" -> 1;
                            case "SECOND_QUARTILE" -> 2;
                            case "THIRD_QUARTILE" -> 3;
                            case "FOURTH_QUARTILE" -> 4;
                            default -> 0;
                        })
                        .build());
            }
        }

        rawDays.sort(Comparator.comparing(ContributionDayDto::getDate));

        int[] streaks = computeStreaks(rawDays);
        String busiestDay = "N/A";
        int busiestCount = 0;
        for (ContributionDayDto d : rawDays) {
            if (d.getCount() > busiestCount) {
                busiestCount = d.getCount();
                busiestDay = d.getDate();
            }
        }

        return ContributionCalendarDto.builder()
                .totalContributions(total)
                .currentStreak(streaks[0])
                .longestStreak(streaks[1])
                .busiestDay(busiestDay)
                .busiestDayCount(busiestCount)
                .days(rawDays)
                .build();
    }

    /**
     * Computes current streak (consecutive days with >=1 contribution,
     * counting back from today or the most recent contributing day) and
     * the longest streak in the whole window.
     *
     * @return [currentStreak, longestStreak]
     */
    private int[] computeStreaks(List<ContributionDayDto> days) {
        int longest = 0;
        int running = 0;
        for (ContributionDayDto d : days) {
            if (d.getCount() > 0) {
                running++;
                longest = Math.max(longest, running);
            } else {
                running = 0;
            }
        }

        // Current streak: walk backwards from the last day. If today has
        // zero contributions yet (common - day isn't over), allow skipping
        // just the most recent day so an active streak isn't shown as broken.
        int current = 0;
        Iterator<ContributionDayDto> it = reversed(days);
        boolean first = true;
        while (it.hasNext()) {
            ContributionDayDto d = it.next();
            if (d.getCount() > 0) {
                current++;
            } else if (first) {
                // today/most recent day has no contributions yet - skip, don't break streak
            } else {
                break;
            }
            first = false;
        }

        return new int[]{current, longest};
    }

    private Iterator<ContributionDayDto> reversed(List<ContributionDayDto> days) {
        List<ContributionDayDto> copy = new ArrayList<>(days);
        java.util.Collections.reverse(copy);
        return copy.iterator();
    }
}
