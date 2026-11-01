package com.gitmind.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.Executor;

/**
 * Application configuration for HTTP clients and common beans.
 */
@Configuration
public class AppConfig {

    /**
     * Configures RestTemplate for GitHub API communication.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder, 
                                      GitHubProperties githubProperties) {
        return builder
                .setConnectTimeout(Duration.ofMillis(githubProperties.getConnectTimeout()))
                .setReadTimeout(Duration.ofMillis(githubProperties.getReadTimeout()))
                .build();
    }

    /**
     * dedicated pool so profile/repos/events fetches for a single
     * analysis run truly in parallel instead of blocking one after another.
     * Sized small on purpose - these are short-lived I/O-bound calls, not CPU work.
     */
    @Bean(name = "githubFetchExecutor")
    public Executor githubFetchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("github-fetch-");
        executor.initialize();
        return executor;
    }
}
