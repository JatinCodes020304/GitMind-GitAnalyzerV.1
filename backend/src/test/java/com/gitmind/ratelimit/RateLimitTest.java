package com.gitmind.ratelimit;
import org.junit.jupiter.api.Test;
import com.gitmind.config.AppProperties;
import org.springframework.mock.web.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class RateLimitTest {
    @Test void simultaneousRequestsCannotExceedLimit() throws Exception {
        var limiter = new RollingWindowRateLimiter(60, 5);
        var allowed = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(20)) {
            for (int i=0;i<100;i++) pool.submit(() -> { if(limiter.tryAcquire("client")) allowed.incrementAndGet(); });
        }
        assertEquals(5, allowed.get());
    }
    @Test void forwardedHeadersCannotBypassLimit() throws Exception {
        var properties = new AppProperties();
        properties.getRateLimit().setMaxRequestsPerWindow(1);
        var filter = new RateLimitFilter(properties);
        for (int i=0;i<2;i++) {
            var request = new MockHttpServletRequest("GET", "/api/github/user");
            request.setRemoteAddr("127.0.0.1");
            request.addHeader("X-Forwarded-For", "fake"+i);
            var response = new MockHttpServletResponse();
            filter.doFilter(request,response,new MockFilterChain());
            assertEquals(i==0?200:429,response.getStatus());
        }
    }
}
