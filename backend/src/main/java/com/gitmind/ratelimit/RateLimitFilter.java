package com.gitmind.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitmind.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * applies the rolling-window rate limiter to every /api/** call, keyed by
 * client IP. Requests over the limit get a 429 with a Retry-After hint instead of being
 * allowed through to burn GitHub API quota or Gemini spend.
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RollingWindowRateLimiter limiter;
    private final AppProperties appProperties;
    private final ObjectMapper mapper = new ObjectMapper();

    public RateLimitFilter(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.limiter = new RollingWindowRateLimiter(
                appProperties.getRateLimit().getWindowSeconds(),
                appProperties.getRateLimit().getMaxRequestsPerWindow());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !appProperties.getRateLimit().isEnabled()
                || "OPTIONS".equals(request.getMethod())
                || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String clientKey = resolveClientKey(request);

        if (limiter.tryAcquire(clientKey)) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = limiter.secondsUntilSlotFrees(clientKey);
        log.warn("Rate limit exceeded for client {} on {}", clientKey, request.getRequestURI());

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));

        Map<String, Object> body = Map.of(
                "timestamp", Instant.now().toString(),
                "status", 429,
                "error", "Too Many Requests",
                "message", "Rate limit exceeded. Try again in " + retryAfterSeconds + "s.",
                "path", request.getRequestURI()
        );
        response.getWriter().write(mapper.writeValueAsString(body));
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60000)
    public void cleanup() { limiter.evictStaleKeys(); }

    private String resolveClientKey(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
