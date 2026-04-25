package com.amazonpricemonitor.service.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Google Gemini-based price-change summarizer.
 * <p>
 * The notifier path is intentionally tolerant of an unconfigured / disabled Gemini —
 * a deterministic fallback summary always ships, so {@link #isEnabled()} only gates
 * whether we make the outbound HTTP call.
 */
@ConfigurationProperties(prefix = "app.ai.gemini")
public class GeminiProperties {

    /**
     * Master switch. Must be {@code true} AND {@link #apiKey} non-blank for the
     * client to issue a request. Any other state ⇒ deterministic fallback only.
     */
    private boolean enabled = true;

    private String apiKey = "";

    private String model = "gemini-2.0-flash";

    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";

    /**
     * Hard upper bound for both connect and read; keeps the alert path bounded
     * even when the upstream is slow or unreachable. Keep this small.
     */
    private int timeoutMs = 5000;

    private int maxOutputTokens = 120;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public boolean isCallable() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
