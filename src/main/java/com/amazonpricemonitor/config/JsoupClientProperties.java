package com.amazonpricemonitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jsoup")
public class JsoupClientProperties {

    private int readTimeoutMs = 15_000;
    private String userAgent =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
}
