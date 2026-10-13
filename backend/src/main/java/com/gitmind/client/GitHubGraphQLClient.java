package com.gitmind.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.gitmind.config.GitHubProperties;
import com.gitmind.exception.GitHubApiException;
import com.gitmind.exception.GitHubUserNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;

/**
 * Client for GitHub's GraphQL API. Used exclusively to fetch the
 * contributionsCollection data (the commit heatmap), which is NOT
 * available anywhere in the REST API.
 *
 * Note: GitHub's GraphQL API requires authentication for every request,
 * even for public data, so a token is mandatory for this client to work.
 */
@Slf4j
@Component
public class GitHubGraphQLClient {

    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final GitHubProperties githubProperties;

    private static final String CONTRIBUTIONS_QUERY = """
            query($username: String!) {
              user(login: $username) {
                contributionsCollection {
                  contributionCalendar {
                    totalContributions
                    weeks {
                      contributionDays {
                        date
                        contributionCount
                        contributionLevel
                      }
                    }
                  }
                }
              }
            }
            """;

    public GitHubGraphQLClient(GitHubProperties githubProperties) {
        this.githubProperties = githubProperties;
        this.apiUrl = githubProperties.getGraphqlUrl();
        this.restTemplate = new RestTemplateBuilder().setConnectTimeout(Duration.ofSeconds(3)).setReadTimeout(Duration.ofSeconds(15)).build();
    }

    /**
     * Fetches the raw contribution calendar JSON node for a user,
     * covering the trailing 12 months from today (GitHub's default window).
     *
     * @param username the GitHub username
     * @return the "contributionCalendar" JSON node
     */
    public JsonNode fetchContributionCalendar(String username) {
        String token = githubProperties.getToken();
        if (token == null || token.isBlank()) {
            throw new GitHubApiException(
                    "Contribution heatmap requires a GITHUB_TOKEN to be configured on the server.",
                    HttpStatus.SERVICE_UNAVAILABLE.value(), null);
        }

        Map<String, Object> body = Map.of(
                "query", CONTRIBUTIONS_QUERY,
                "variables", Map.of("username", username)
        );

        try {
            var headers = new org.springframework.http.HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            JsonNode response = restTemplate.postForObject(apiUrl,
                    new org.springframework.http.HttpEntity<>(body, headers), JsonNode.class);

            if (response == null) {
                throw new GitHubApiException("Empty response from GitHub GraphQL API",
                        HttpStatus.BAD_GATEWAY.value(), null);
            }

            if (response.has("errors") && response.get("errors").isArray()
                    && response.get("errors").size() > 0) {
                Iterator<JsonNode> errors = response.get("errors").elements();
                StringBuilder msg = new StringBuilder();
                while (errors.hasNext()) {
                    msg.append(errors.next().path("message").asText()).append("; ");
                }
                log.warn("GitHub GraphQL returned errors for user {}: {}", username, msg);
            }

            JsonNode userNode = response.path("data").path("user");
            if (userNode.isMissingNode() || userNode.isNull()) {
                throw new GitHubUserNotFoundException(username);
            }

            return userNode.path("contributionsCollection").path("contributionCalendar");
        } catch (HttpClientErrorException.Unauthorized ex) {
            throw new GitHubApiException("GitHub token is invalid or expired",
                    HttpStatus.UNAUTHORIZED.value(), ex);
        } catch (HttpClientErrorException ex) {
            throw new GitHubApiException("GitHub GraphQL API error: " + ex.getMessage(),
                    ex.getStatusCode().value(), ex);
        }
    }
}
