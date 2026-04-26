package com.amazonpricemonitor.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notification.sms")
public class SmsProperties {

    private boolean enabled = false;

    private String accountSid = "";

    private String authToken = "";

    private String from = "";

    /** Comma-separated E.164 phone numbers. */
    private String to = "";

    private String baseUrl = "https://api.twilio.com/2010-04-01";

    private int timeoutMs = 5000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAccountSid() {
        return accountSid;
    }

    public void setAccountSid(String accountSid) {
        this.accountSid = accountSid;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
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

    public List<String> getToNumbers() {
        if (to == null || to.isBlank()) {
            return List.of();
        }
        List<String> numbers = new ArrayList<>();
        for (String part : to.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                numbers.add(trimmed);
            }
        }
        return Collections.unmodifiableList(numbers);
    }

    public boolean isConfigured() {
        return accountSid != null
                && !accountSid.isBlank()
                && authToken != null
                && !authToken.isBlank()
                && from != null
                && !from.isBlank()
                && !getToNumbers().isEmpty();
    }
}
