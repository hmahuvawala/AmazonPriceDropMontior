package com.amazonpricemonitor.service;

import com.amazonpricemonitor.domain.NotificationRecipients;
import com.amazonpricemonitor.repository.NotificationRecipientsRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationRecipientsService {

    static final int MAX_EMAIL_CSV_LENGTH = 4000;
    static final int MAX_SMS_CSV_LENGTH = 500;

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{6,14}$");

    private final NotificationRecipientsRepository repository;

    public NotificationRecipientsService(NotificationRecipientsRepository repository) {
        this.repository = repository;
    }

    /**
     * Ensures row {@code id=1} exists (for H2 tests without Flyway). Production DB is seeded by Flyway V4.
     */
    @Transactional
    public void ensureDefaultRowIfMissing() {
        if (repository.findById((short) 1).isEmpty()) {
            NotificationRecipients row = new NotificationRecipients();
            row.setId((short) 1);
            repository.save(row);
        }
    }

    @Transactional(readOnly = true)
    public List<String> getEmailRecipients() {
        return splitCsv(loadRow().getEmailToCsv());
    }

    @Transactional(readOnly = true)
    public List<String> getSmsRecipients() {
        return splitCsv(loadRow().getSmsToCsv());
    }

    @Transactional(readOnly = true)
    public String getEmailToCsv() {
        return loadRow().getEmailToCsv();
    }

    @Transactional(readOnly = true)
    public String getSmsToCsv() {
        return loadRow().getSmsToCsv();
    }

    @Transactional
    public void updateEmailRecipients(String csv) {
        String normalized = normalize(csv);
        validateLength(normalized, MAX_EMAIL_CSV_LENGTH, "email");
        for (String entry : splitCsv(normalized)) {
            if (!EMAIL.matcher(entry).matches()) {
                throw new IllegalArgumentException("Invalid email address: " + entry);
            }
        }
        NotificationRecipients row = loadRow();
        row.setEmailToCsv(normalized);
        repository.save(row);
    }

    @Transactional
    public void updateSmsRecipients(String csv) {
        String normalized = normalize(csv);
        validateLength(normalized, MAX_SMS_CSV_LENGTH, "sms");
        for (String entry : splitCsv(normalized)) {
            if (!E164.matcher(entry).matches()) {
                throw new IllegalArgumentException("Invalid E.164 phone number: " + entry);
            }
        }
        NotificationRecipients row = loadRow();
        row.setSmsToCsv(normalized);
        repository.save(row);
    }

    private NotificationRecipients loadRow() {
        return repository
                .findById((short) 1)
                .orElseThrow(() -> new IllegalStateException(
                        "notification_recipients row missing; run Flyway or ensureDefaultRowIfMissing"));
    }

    private static String normalize(String csv) {
        if (csv == null || csv.isBlank()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (String raw : csv.split(",")) {
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                parts.add(trimmed);
            }
        }
        return String.join(",", parts);
    }

    private static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        for (String raw : csv.split(",")) {
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                parts.add(trimmed);
            }
        }
        return Collections.unmodifiableList(parts);
    }

    private static void validateLength(String csv, int max, String label) {
        if (csv.length() > max) {
            throw new IllegalArgumentException(label + " recipient list exceeds " + max + " characters");
        }
    }
}
