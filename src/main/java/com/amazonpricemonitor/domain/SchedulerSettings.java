package com.amazonpricemonitor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "scheduler_settings")
public class SchedulerSettings {

    @Id
    @Column(name = "id", nullable = false)
    private Short id;

    @Column(name = "check_interval_ms", nullable = false)
    private long checkIntervalMs;

    public Short getId() {
        return id;
    }

    public void setId(Short id) {
        this.id = id;
    }

    public long getCheckIntervalMs() {
        return checkIntervalMs;
    }

    public void setCheckIntervalMs(long checkIntervalMs) {
        this.checkIntervalMs = checkIntervalMs;
    }
}
