package com.amazonpricemonitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.admin")
public class AdminProperties {

    /**
     * When true, exposes {@code POST /api/admin/send-test-notification} to fire a synthetic
     * threshold alert through the real notifier stack (log / email / SMS). Keep false in any
     * shared or production deployment — there is no auth on {@code /api/admin}.
     */
    private boolean allowTestNotification = false;

    public boolean isAllowTestNotification() {
        return allowTestNotification;
    }

    public void setAllowTestNotification(boolean allowTestNotification) {
        this.allowTestNotification = allowTestNotification;
    }
}
