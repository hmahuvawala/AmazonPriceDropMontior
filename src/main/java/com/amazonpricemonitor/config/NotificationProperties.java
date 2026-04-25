package com.amazonpricemonitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notification")
public class NotificationProperties {

    /**
     * One of: log (default), slack, noop.
     */
    private String type = "log";

    private final Slack slack = new Slack();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Slack getSlack() {
        return slack;
    }

    public static class Slack {

        private String webhookUrl = "";

        public String getWebhookUrl() {
            return webhookUrl;
        }

        public void setWebhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
        }

        public boolean isConfigured() {
            return webhookUrl != null && !webhookUrl.isBlank();
        }
    }
}
