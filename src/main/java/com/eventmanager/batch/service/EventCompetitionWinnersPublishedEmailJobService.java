package com.eventmanager.batch.service;

import com.eventmanager.batch.domain.BatchJobExecution;
import com.eventmanager.batch.domain.EventCompetition;
import com.eventmanager.batch.domain.EventCompetitionResult;
import com.eventmanager.batch.domain.EventCompetitionSettings;
import com.eventmanager.batch.domain.PromotionEmailTemplate;
import com.eventmanager.batch.dto.EventCompetitionWinnerHighlight;
import com.eventmanager.batch.dto.EventCompetitionWinnersPublishedEmailJobRequest;
import com.eventmanager.batch.dto.EventCompetitionWinnersPublishedEmailJobResponse;
import com.eventmanager.batch.repository.EventCompetitionRepository;
import com.eventmanager.batch.repository.EventCompetitionRegistrationRepository;
import com.eventmanager.batch.repository.EventCompetitionResultRepository;
import com.eventmanager.batch.repository.EventCompetitionSettingsRepository;
import com.eventmanager.batch.repository.PromotionEmailTemplateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventCompetitionWinnersPublishedEmailJobService {

  private final BatchJobExecutionService batchJobExecutionService;
  private final EventCompetitionSettingsRepository settingsRepository;
  private final EventCompetitionRegistrationRepository registrationRepository;
  private final EventCompetitionResultRepository resultRepository;
  private final EventCompetitionRepository competitionRepository;
  private final PromotionEmailTemplateRepository promotionEmailTemplateRepository;
  private final ManualPaymentConfirmationEmailJobService manualPaymentConfirmationEmailJobService;
  private final EventCompetitionEmailContentBuilderService eventCompetitionEmailContentBuilderService;
  private final EmailContentBuilderService emailContentBuilderService;
  private final EmailService emailService;
  private final BackendApiService backendApiService;

  @Value("${qr.code.email-host-url-prefix:${NEXT_PUBLIC_APP_URL:http://localhost:3000}}")
  private String emailHostUrlPrefix;

  public EventCompetitionWinnersPublishedEmailJobResponse triggerWinnersPublishedEmailJob(
    EventCompetitionWinnersPublishedEmailJobRequest request
  ) {
    if (request.getTenantId() == null || request.getTenantId().isBlank()) {
      return failureResponse("Tenant ID is required");
    }
    if (request.getEventId() == null) {
      return failureResponse("Event ID is required");
    }

    String parametersJson = String.format(
      "{\"eventId\":%d,\"tenantId\":\"%s\",\"templateId\":%s}",
      request.getEventId(),
      request.getTenantId(),
      request.getTemplateId() != null ? request.getTemplateId() : "null"
    );

    BatchJobExecution execution = batchJobExecutionService.createJobExecution(
      "eventCompetitionWinnersPublishedEmailJob",
      "EVENT_COMPETITION_WINNERS_PUBLISHED_EMAIL",
      request.getTenantId(),
      "API",
      parametersJson
    );

    processWinnersPublishedEmailAsync(execution.getId(), request);

    return EventCompetitionWinnersPublishedEmailJobResponse.builder()
      .success(true)
      .message("Event competition winners published email job accepted for processing")
      .jobExecutionId(execution.getId())
      .processedCount(0L)
      .successCount(0L)
      .failedCount(0L)
      .build();
  }

  @Async
  protected void processWinnersPublishedEmailAsync(Long executionId, EventCompetitionWinnersPublishedEmailJobRequest request) {
    long processedCount = 0L;
    long successCount = 0L;
    long failedCount = 0L;

    try {
      Optional<EventCompetitionSettings> settingsOpt = settingsRepository.findByEventIdAndTenantId(
        request.getEventId(),
        request.getTenantId()
      );

      if (settingsOpt.isPresent() && settingsOpt.get().getWinnersPublishedEmailSentAt() != null) {
        log.info(
          "Winners published email already sent for event {} at {}",
          request.getEventId(),
          settingsOpt.get().getWinnersPublishedEmailSentAt()
        );
        batchJobExecutionService.completeJobExecution(executionId, "COMPLETED", 0L, 0L, 0L, "Already sent");
        return;
      }

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

      PromotionEmailTemplate template = null;
      if (request.getTemplateId() != null) {
        template = promotionEmailTemplateRepository
          .findByIdAndTenantId(request.getTemplateId(), request.getTenantId())
          .orElse(null);
        if (template == null) {
          batchJobExecutionService.completeJobExecution(
            executionId,
            "FAILED",
            processedCount,
            successCount,
            failedCount,
            "Template not found for tenant: templateId=" + request.getTemplateId()
          );
          return;
        }
      }

      String eventTitle = resolveEventTitle(request.getEventId());
      String winnersUrl = buildWinnersUrl(request.getEventId());
      List<EventCompetitionWinnerHighlight> highlights = buildHighlights(request.getEventId(), request.getTenantId());

      List<String> recipientEmails = registrationRepository.findDistinctParticipantEmailsForEvent(
        request.getEventId(),
        request.getTenantId()
      );

      if (recipientEmails.isEmpty()) {
        batchJobExecutionService.completeJobExecution(
          executionId,
          "COMPLETED",
          0L,
          0L,
          0L,
          "No recipient emails found"
        );
        return;
      }

      String defaultSubject = String.format(
        "Competition Results Published%s",
        eventTitle != null ? " - " + eventTitle : ""
      );
      String defaultBodyHtml = eventCompetitionEmailContentBuilderService.buildWinnersPublishedEmailBody(
        eventTitle,
        winnersUrl,
        highlights,
        request.getTenantId()
      );

      if (template != null) {
        emailContentBuilderService.ensureHeaderAndFooterReady(request.getTenantId(), 10);
      }

      for (String recipientEmail : recipientEmails) {
        processedCount++;
        try {
          String subject = defaultSubject;
          String bodyHtml = defaultBodyHtml;

          if (template != null) {
            Map<String, String> emailContent = emailContentBuilderService.buildEmailContent(
              template,
              null,
              null,
              request.getTenantId()
            );
            subject = eventCompetitionEmailContentBuilderService.applyTemplatePlaceholders(
              emailContent.get("subject"),
              null,
              null,
              null,
              eventTitle,
              winnersUrl
            );
            bodyHtml = eventCompetitionEmailContentBuilderService.applyTemplatePlaceholders(
              emailContent.get("bodyHtml"),
              null,
              null,
              null,
              eventTitle,
              winnersUrl
            );
          }

          emailService.sendEmail(fromAddress, null, recipientEmail, subject, bodyHtml, true);
          successCount++;
        } catch (Exception e) {
          log.error("Failed to send winners published email to {}: {}", recipientEmail, e.getMessage(), e);
          failedCount++;
        }
      }

      if (failedCount == 0) {
        EventCompetitionSettings settings = settingsOpt.orElseGet(() -> {
          EventCompetitionSettings newSettings = new EventCompetitionSettings();
          newSettings.setEventId(request.getEventId());
          newSettings.setTenantId(request.getTenantId());
          return newSettings;
        });
        if (settings.getId() == null) {
          log.warn("No competition settings row for event {}, skipping winners_published_email_sent_at update", request.getEventId());
        } else {
          settings.setWinnersPublishedEmailSentAt(ZonedDateTime.now());
          settings.setUpdatedAt(ZonedDateTime.now());
          settingsRepository.save(settings);
        }
      }

      batchJobExecutionService.completeJobExecution(
        executionId,
        failedCount > 0 && successCount == 0 ? "FAILED" : "COMPLETED",
        processedCount,
        successCount,
        failedCount,
        failedCount > 0 ? "Some winners published emails failed" : null
      );
    } catch (Exception e) {
      log.error("Failed to process winners published email job {}: {}", executionId, e.getMessage(), e);
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

  private List<EventCompetitionWinnerHighlight> buildHighlights(Long eventId, String tenantId) {
    List<EventCompetitionResult> results = resultRepository.findPublishedTopPlacementsByEventIdAndTenantId(eventId, tenantId);
    Map<Long, String> competitionNames = new HashMap<>();

    List<EventCompetitionWinnerHighlight> highlights = new ArrayList<>();
    for (EventCompetitionResult result : results) {
      String competitionName = competitionNames.computeIfAbsent(
        result.getCompetitionId(),
        id -> competitionRepository.findById(id).map(EventCompetition::getName).orElse("Competition")
      );
      highlights.add(
        EventCompetitionWinnerHighlight.builder()
          .competitionName(competitionName)
          .placement(result.getPlacement())
          .displayName(result.getDisplayName())
          .placementLabel(result.getPlacementLabel())
          .prizeTitle(result.getPrizeTitle())
          .build()
      );
    }
    return highlights;
  }

  private String buildWinnersUrl(Long eventId) {
    String baseUrl = emailHostUrlPrefix != null ? emailHostUrlPrefix.trim() : "";
    if (baseUrl.endsWith("/")) {
      baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
    }
    return baseUrl + "/events/" + eventId + "/competitions/winners";
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

  private EventCompetitionWinnersPublishedEmailJobResponse failureResponse(String message) {
    return EventCompetitionWinnersPublishedEmailJobResponse.builder()
      .success(false)
      .message(message)
      .processedCount(0L)
      .successCount(0L)
      .failedCount(0L)
      .build();
  }
}
