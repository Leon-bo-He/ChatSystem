package life.hebo;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    private final ServerRateLimitProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private LoadingCache<String, Bucket> buckets;

    public RateLimitFilter(ServerRateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void initFilterBean() {
        int cap = Math.max(1, properties.getCapacity());
        this.buckets = Caffeine.newBuilder()
                .maximumSize(50_000)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build(key -> newBucket(cap));
    }

    private Bucket newBucket(int capacity) {
        int windowSec = Math.max(1, properties.getWindowSeconds());
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, Duration.ofSeconds(windowSec))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        for (String excluded : properties.getExcludedPaths()) {
            if (pathMatcher.match(excluded, path)) {
                return true;
            }
        }
        List<String> included = properties.getPaths();
        if (included == null || included.isEmpty()) {
            return true;
        }
        return included.stream().noneMatch(p -> pathMatcher.match(p, path));
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientKey = clientKey(request);
        Bucket bucket = buckets.get(clientKey);
        var probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.setHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSec = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1;
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(Math.max(1, retryAfterSec)));
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"rate_limit_exceeded\",\"message\":\"Too many requests. Try again later.\"}");
    }

    private String clientKey(HttpServletRequest request) {
        if (properties.isTrustForwardedHeaders()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String first = forwarded.split(",")[0].trim();
                if (!first.isEmpty()) {
                    return first;
                }
            }
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
