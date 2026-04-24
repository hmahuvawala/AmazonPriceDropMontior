package com.amazonpricemonitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "alterlab")
public class AlterLabProperties {

    private String baseUrl = "https://api.alterlab.io/api/v1";
    private String apiKey = "";
    private String scrapePath = "/scrape";
    private int requestTimeoutSeconds = 90;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getScrapePath() {
        return scrapePath;
    }

    public void setScrapePath(String scrapePath) {
        this.scrapePath = scrapePath;
    }

    public int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
