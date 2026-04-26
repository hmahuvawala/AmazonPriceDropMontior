package com.amazonpricemonitor.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notification.email")
public class EmailProperties {

    private boolean enabled = false;

    private String from = "";

    /** Comma-separated recipient addresses. */
    private String to = "";

    private String subjectPrefix = "[Amazon Price Monitor]";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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

    public String getSubjectPrefix() {
        if (subjectPrefix == null || subjectPrefix.isBlank()) {
            return "[Amazon Price Monitor]";
        }
        return subjectPrefix;
    }

    public void setSubjectPrefix(String subjectPrefix) {
        this.subjectPrefix = subjectPrefix;
    }

    public List<String> getToAddresses() {
        if (to == null || to.isBlank()) {
            return List.of();
        }
        List<String> addresses = new ArrayList<>();
        for (String part : to.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                addresses.add(trimmed);
            }
        }
        return Collections.unmodifiableList(addresses);
    }

    public boolean isConfigured() {
        return from != null && !from.isBlank() && !getToAddresses().isEmpty();
    }
}
