package life.hebo;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.rate-limit")
public class ServerRateLimitProperties {

    private boolean enabled = true;
    /**
     * Maximum requests allowed per client in each refill window.
     */
    private int capacity = 120;
    /**
     * Period over which the full capacity is restored (token bucket refill interval).
     */
    private int windowSeconds = 60;
    /**
     * Ant-style patterns for paths to rate limit (e.g. /api/**).
     */
    private List<String> paths = new ArrayList<>(List.of("/api/**"));
    /**
     * Paths excluded from rate limiting entirely.
     */
    private List<String> excludedPaths = new ArrayList<>(List.of("/health", "/error"));
    /**
     * When true, the first IP in X-Forwarded-For is used (only behind a trusted proxy).
     */
    private boolean trustForwardedHeaders = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public List<String> getPaths() {
        return paths;
    }

    public void setPaths(List<String> paths) {
        this.paths = paths;
    }

    public List<String> getExcludedPaths() {
        return excludedPaths;
    }

    public void setExcludedPaths(List<String> excludedPaths) {
        this.excludedPaths = excludedPaths;
    }

    public boolean isTrustForwardedHeaders() {
        return trustForwardedHeaders;
    }

    public void setTrustForwardedHeaders(boolean trustForwardedHeaders) {
        this.trustForwardedHeaders = trustForwardedHeaders;
    }
}
