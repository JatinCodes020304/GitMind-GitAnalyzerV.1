package com.gitmind.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the {@code app.cache.*} and {@code app.rate-limit.*} properties
 * from application.yml.
 */
@Configuration
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    private final Cache cache = new Cache();
    private final RateLimit rateLimit = new RateLimit();

    @Getter
    @Setter
    public static class Cache {
        /** How long a fresh result is served before we go back to the source. */
        private long freshTtlMinutes = 10;
        /** How long a "last known good" result is kept around purely as a fail-open fallback. */
        private long staleTtlHours = 6;
        private long maxEntries = 2000;
    }

    @Getter
    @Setter
    public static class RateLimit {
        private boolean enabled = true;
        /** Rolling window size, in seconds. */
        private int windowSeconds = 60;
        /** Max requests allowed per client within the rolling window. */
        private int maxRequestsPerWindow = 30;
    }
}
