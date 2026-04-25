package com.amazonpricemonitor.repository;

import com.amazonpricemonitor.domain.SchedulerSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchedulerSettingsRepository extends JpaRepository<SchedulerSettings, Short> {}
