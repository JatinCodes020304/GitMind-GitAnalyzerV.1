package com.gitmind.client;

import com.gitmind.config.GitHubProperties;
import com.gitmind.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GitHubApiClientTest {
    @Test void permissionDeniedRemainsForbidden() {
        var rest = new RestTemplate();
        var server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo("https://api.github.com/users/user"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("Access denied"));
        var client = new GitHubApiClient(rest, new GitHubProperties());
        var error = assertThrows(GitHubApiException.class, () -> client.fetchUserProfile("user"));
        assertEquals(403, error.getStatusCode());
        server.verify();
    }

    @Test void exhaustedQuotaIsRecognizedAsRateLimit() {
        var rest = new RestTemplate();
        var server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo("https://api.github.com/users/user"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).header("X-RateLimit-Remaining", "0")
                        .header("X-RateLimit-Reset", "1800000000"));
        var client = new GitHubApiClient(rest, new GitHubProperties());
        assertThrows(GitHubRateLimitException.class, () -> client.fetchUserProfile("user"));
        server.verify();
    }
}
