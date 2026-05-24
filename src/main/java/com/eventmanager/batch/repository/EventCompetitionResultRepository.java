package com.eventmanager.batch.repository;

import com.eventmanager.batch.domain.EventCompetitionResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventCompetitionResultRepository extends JpaRepository<EventCompetitionResult, Long> {

  @Query(
    "SELECT r FROM EventCompetitionResult r " +
    "WHERE r.eventId = :eventId " +
    "AND r.tenantId = :tenantId " +
    "AND r.isPublished = true " +
    "AND r.placement IS NOT NULL AND r.placement <= 3 " +
    "ORDER BY r.competitionId ASC, r.placement ASC"
  )
  List<EventCompetitionResult> findPublishedTopPlacementsByEventIdAndTenantId(
    @Param("eventId") Long eventId,
    @Param("tenantId") String tenantId
  );
}
