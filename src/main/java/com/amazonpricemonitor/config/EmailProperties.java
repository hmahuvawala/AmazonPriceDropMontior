package com.amazonpricemonitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notification.email")
public class EmailProperties {

    private boolean enabled = false;

    private String from = "";

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

    public String getSubjectPrefix() {
        if (subjectPrefix == null || subjectPrefix.isBlank()) {
            return "[Amazon Price Monitor]";
        }
        return subjectPrefix;
    }

    public void setSubjectPrefix(String subjectPrefix) {
        this.subjectPrefix = subjectPrefix;
    }

    public boolean hasFrom() {
        return from != null && !from.isBlank();
    }
}
