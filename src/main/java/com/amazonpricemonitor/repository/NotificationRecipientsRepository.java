package com.amazonpricemonitor.repository;

import com.amazonpricemonitor.domain.NotificationRecipients;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRecipientsRepository extends JpaRepository<NotificationRecipients, Short> {}
