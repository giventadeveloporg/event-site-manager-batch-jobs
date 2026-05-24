package com.eventmanager.batch.repository;

import com.eventmanager.batch.domain.EventCompetitionRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface EventCompetitionRegistrationRepository extends JpaRepository<EventCompetitionRegistration, Long> {

  @Query(
    "SELECT r FROM EventCompetitionRegistration r " +
    "WHERE r.id IN :registrationIds " +
    "AND r.tenantId = :tenantId " +
    "AND r.eventId = :eventId " +
    "AND r.confirmationEmailSent = false " +
    "AND r.registrationStatus IN ('CONFIRMED', 'PAID', 'COMPLETED')"
  )
  List<EventCompetitionRegistration> findEligibleForConfirmationEmail(
    @Param("registrationIds") Collection<Long> registrationIds,
    @Param("tenantId") String tenantId,
    @Param("eventId") Long eventId
  );

  @Query(
    value =
      "SELECT DISTINCT p.email FROM event_competition_registration r " +
      "JOIN event_competition_participant p ON p.id = r.participant_profile_id " +
      "WHERE r.event_id = :eventId " +
      "AND r.tenant_id = :tenantId " +
      "AND r.registration_status IN ('CONFIRMED', 'PAID', 'COMPLETED') " +
      "AND p.email IS NOT NULL AND p.email <> ''",
    nativeQuery = true
  )
  List<String> findDistinctParticipantEmailsForEvent(
    @Param("eventId") Long eventId,
    @Param("tenantId") String tenantId
  );

  @Query(
    "SELECT r FROM EventCompetitionRegistration r " +
    "WHERE r.eventId = :eventId " +
    "AND r.tenantId = :tenantId " +
    "AND r.registrationStatus IN ('CONFIRMED', 'PAID', 'COMPLETED')"
  )
  List<EventCompetitionRegistration> findConfirmedByEventIdAndTenantId(
    @Param("eventId") Long eventId,
    @Param("tenantId") String tenantId
  );
}
