package com.amazonpricemonitor.web.dto;

public record UpdateNotificationRecipientsRequest(String emailToCsv, String smsToCsv) {}
