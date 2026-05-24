package com.eventmanager.batch.service;

import com.eventmanager.batch.domain.BatchJobExecution;
import com.eventmanager.batch.dto.EventCompetitionRegistrationSummaryJobRequest;
import com.eventmanager.batch.dto.EventCompetitionRegistrationSummaryJobResponse;
import com.eventmanager.batch.dto.EventCompetitionSummaryRow;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventCompetitionRegistrationSummaryJobService {

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final BatchJobExecutionService batchJobExecutionService;
  private final ManualPaymentConfirmationEmailJobService manualPaymentConfirmationEmailJobService;
  private final EventCompetitionEmailContentBuilderService eventCompetitionEmailContentBuilderService;
  private final EmailService emailService;
  private final BackendApiService backendApiService;

  public EventCompetitionRegistrationSummaryJobResponse triggerRegistrationSummaryJob(
    EventCompetitionRegistrationSummaryJobRequest request
  ) {
    if (request.getTenantId() == null || request.getTenantId().isBlank()) {
      return failureResponse("Tenant ID is required");
    }
    if (request.getEventId() == null) {
      return failureResponse("Event ID is required");
    }
    if (request.getRecipientEmail() == null || request.getRecipientEmail().isBlank()) {
      return failureResponse("Recipient email is required");
    }

    String parametersJson = String.format(
      "{\"eventId\":%d,\"tenantId\":\"%s\",\"recipientEmail\":\"%s\"}",
      request.getEventId(),
      request.getTenantId(),
      request.getRecipientEmail()
    );

    BatchJobExecution execution = batchJobExecutionService.createJobExecution(
      "eventCompetitionRegistrationSummaryJob",
      "EVENT_COMPETITION_REGISTRATION_SUMMARY",
      request.getTenantId(),
      "API",
      parametersJson
    );

    processRegistrationSummaryAsync(execution.getId(), request);

    return EventCompetitionRegistrationSummaryJobResponse.builder()
      .success(true)
      .message("Event competition registration summary job accepted for processing")
      .jobExecutionId(execution.getId())
      .processedCount(0L)
      .successCount(0L)
      .failedCount(0L)
      .build();
  }

  public EventCompetitionRegistrationSummaryJobResponse runScheduledSummaryJob(
    String tenantId,
    Long eventId,
    String recipientEmail
  ) {
    EventCompetitionRegistrationSummaryJobRequest request = new EventCompetitionRegistrationSummaryJobRequest();
    request.setTenantId(tenantId);
    request.setEventId(eventId);
    request.setRecipientEmail(recipientEmail);
    return triggerRegistrationSummaryJob(request);
  }

  @Async
  protected void processRegistrationSummaryAsync(Long executionId, EventCompetitionRegistrationSummaryJobRequest request) {
    long processedCount = 0L;
    long successCount = 0L;
    long failedCount = 0L;

    try {
      String fromAddress = manualPaymentConfirmationEmailJobService.resolveFromEmail(request.getTenantId());
      if (fromAddress == null || fromAddress.isEmpty()) {
        batchJobExecutionService.completeJobExecution(
          executionId,
          "FAILED",
          processedCount,
          successCount,
          failedCount,
          "No FROM address configured for tenant"
        );
        return;
      }

      MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("eventId", request.getEventId())
        .addValue("tenantId", request.getTenantId());

      List<EventCompetitionSummaryRow> summaryRows = jdbcTemplate.query(
        "SELECT c.name AS competition_name, " +
        "       SUM(CASE WHEN r.registration_status IN ('CONFIRMED', 'PAID', 'COMPLETED') THEN 1 ELSE 0 END) AS confirmed_count, " +
        "       SUM(CASE WHEN r.registration_status = 'PENDING_PAYMENT' THEN 1 ELSE 0 END) AS pending_count, " +
        "       SUM(CASE WHEN r.registration_status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_count, " +
        "       COALESCE(SUM(CASE WHEN r.registration_status IN ('CONFIRMED', 'PAID', 'COMPLETED') THEN r.fee_amount ELSE 0 END), 0) AS total_fees " +
        "FROM event_competition_registration r " +
        "JOIN event_competition c ON c.id = r.competition_id " +
        "WHERE r.event_id = :eventId AND r.tenant_id = :tenantId " +
        "GROUP BY c.id, c.name " +
        "ORDER BY c.display_order ASC, c.name ASC",
        params,
        (rs, rowNum) ->
          EventCompetitionSummaryRow.builder()
            .competitionName(rs.getString("competition_name"))
            .confirmedCount(rs.getLong("confirmed_count"))
            .pendingCount(rs.getLong("pending_count"))
            .cancelledCount(rs.getLong("cancelled_count"))
            .totalFees(rs.getBigDecimal("total_fees"))
            .build()
      );

      Long totalRegistrations = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM event_competition_registration " +
        "WHERE event_id = :eventId AND tenant_id = :tenantId " +
        "AND registration_status IN ('CONFIRMED', 'PAID', 'COMPLETED')",
        params,
        Long.class
      );

      long total = totalRegistrations != null ? totalRegistrations : 0L;
      String eventTitle = resolveEventTitle(request.getEventId());

      String subject = String.format(
        "Competition Registration Summary%s",
        eventTitle != null ? " - " + eventTitle : ""
      );
      String bodyHtml = eventCompetitionEmailContentBuilderService.buildRegistrationSummaryEmailBody(
        eventTitle,
        summaryRows != null ? summaryRows : new ArrayList<>(),
        total,
        request.getTenantId()
      );

      processedCount = 1L;
      emailService.sendEmail(fromAddress, null, request.getRecipientEmail(), subject, bodyHtml, true);
      successCount = 1L;

      batchJobExecutionService.completeJobExecution(
        executionId,
        "COMPLETED",
        processedCount,
        successCount,
        failedCount,
        null
      );
    } catch (Exception e) {
      log.error("Failed to process registration summary job {}: {}", executionId, e.getMessage(), e);
      failedCount = 1L;
      batchJobExecutionService.completeJobExecution(
        executionId,
        "FAILED",
        processedCount,
        successCount,
        failedCount,
        e.getMessage()
      );
    }
  }

  private String resolveEventTitle(Long eventId) {
    try {
      JsonNode eventDetails = backendApiService.getEventDetails(eventId);
      if (eventDetails != null && eventDetails.has("title")) {
        return eventDetails.get("title").asText();
      }
    } catch (Exception e) {
      log.warn("Could not fetch event title for eventId {}: {}", eventId, e.getMessage());
    }
    return null;
  }

  private EventCompetitionRegistrationSummaryJobResponse failureResponse(String message) {
    return EventCompetitionRegistrationSummaryJobResponse.builder()
      .success(false)
      .message(message)
      .processedCount(0L)
      .successCount(0L)
      .failedCount(0L)
      .build();
  }
}
