package com.amazonpricemonitor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "notification_recipients")
public class NotificationRecipients {

    @Id
    @Column(name = "id", nullable = false)
    private Short id;

    @Column(name = "email_to_csv", nullable = false, length = 4000)
    private String emailToCsv = "";

    @Column(name = "sms_to_csv", nullable = false, length = 500)
    private String smsToCsv = "";

    public Short getId() {
        return id;
    }

    public void setId(Short id) {
        this.id = id;
    }

    public String getEmailToCsv() {
        return emailToCsv;
    }

    public void setEmailToCsv(String emailToCsv) {
        this.emailToCsv = emailToCsv;
    }

    public String getSmsToCsv() {
        return smsToCsv;
    }

    public void setSmsToCsv(String smsToCsv) {
        this.smsToCsv = smsToCsv;
    }
}
