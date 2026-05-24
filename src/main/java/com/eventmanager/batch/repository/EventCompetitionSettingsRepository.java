package com.eventmanager.batch.repository;

import com.eventmanager.batch.domain.EventCompetitionSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EventCompetitionSettingsRepository extends JpaRepository<EventCompetitionSettings, Long> {

  Optional<EventCompetitionSettings> findByEventIdAndTenantId(Long eventId, String tenantId);
}
