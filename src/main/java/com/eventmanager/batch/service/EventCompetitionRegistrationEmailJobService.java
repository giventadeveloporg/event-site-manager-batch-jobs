package com.eventmanager.batch.service;

import com.eventmanager.batch.domain.BatchJobExecution;
import com.eventmanager.batch.domain.EventCompetition;
import com.eventmanager.batch.domain.EventCompetitionParticipant;
import com.eventmanager.batch.domain.EventCompetitionRegistration;
import com.eventmanager.batch.domain.PromotionEmailTemplate;
import com.eventmanager.batch.dto.EventCompetitionRegistrationEmailJobRequest;
import com.eventmanager.batch.dto.EventCompetitionRegistrationEmailJobResponse;
import com.eventmanager.batch.repository.EventCompetitionGroupMemberRepository;
import com.eventmanager.batch.repository.EventCompetitionParticipantRepository;
import com.eventmanager.batch.repository.EventCompetitionRegistrationRepository;
import com.eventmanager.batch.repository.EventCompetitionRepository;
import com.eventmanager.batch.repository.PromotionEmailTemplateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventCompetitionRegistrationEmailJobService {

  private final BatchJobExecutionService batchJobExecutionService;
  private final EventCompetitionRegistrationRepository registrationRepository;
  private final EventCompetitionParticipantRepository participantRepository;
  private final EventCompetitionRepository competitionRepository;
  private final EventCompetitionGroupMemberRepository groupMemberRepository;
  private final PromotionEmailTemplateRepository promotionEmailTemplateRepository;
  private final ManualPaymentConfirmationEmailJobService manualPaymentConfirmationEmailJobService;
  private final EventCompetitionEmailContentBuilderService eventCompetitionEmailContentBuilderService;
  private final EmailContentBuilderService emailContentBuilderService;
  private final EmailService emailService;
  private final BackendApiService backendApiService;

  public EventCompetitionRegistrationEmailJobResponse triggerRegistrationEmailJob(
    EventCompetitionRegistrationEmailJobRequest request
  ) {
    if (request.getTenantId() == null || request.getTenantId().isBlank()) {
      return failureResponse("Tenant ID is required");
    }
    if (request.getEventId() == null) {
      return failureResponse("Event ID is required");
    }
    if (request.getRegistrationIds() == null || request.getRegistrationIds().isEmpty()) {
      return failureResponse("At least one registration ID is required");
    }

    String parametersJson = String.format(
      "{\"eventId\":%d,\"tenantId\":\"%s\",\"registrationIds\":%s,\"templateId\":%s}",
      request.getEventId(),
      request.getTenantId(),
      request.getRegistrationIds(),
      request.getTemplateId() != null ? request.getTemplateId() : "null"
    );

    BatchJobExecution execution = batchJobExecutionService.createJobExecution(
      "eventCompetitionRegistrationEmailJob",
      "EVENT_COMPETITION_REGISTRATION_EMAIL",
      request.getTenantId(),
      "API",
      parametersJson
    );

    processRegistrationEmailsAsync(execution.getId(), request);

    return EventCompetitionRegistrationEmailJobResponse.builder()
      .success(true)
      .message("Event competition registration email job accepted for processing")
      .jobExecutionId(execution.getId())
      .processedCount(0L)
      .successCount(0L)
      .failedCount(0L)
      .build();
  }

  @Async
  protected void processRegistrationEmailsAsync(Long executionId, EventCompetitionRegistrationEmailJobRequest request) {
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

      String eventTitle = resolveEventTitle(request.getEventId());
      PromotionEmailTemplate template = resolveTemplate(request.getTemplateId(), request.getTenantId());
      if (request.getTemplateId() != null && template == null) {
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

      List<EventCompetitionRegistration> registrations = registrationRepository.findEligibleForConfirmationEmail(
        request.getRegistrationIds(),
        request.getTenantId(),
        request.getEventId()
      );

      for (EventCompetitionRegistration registration : registrations) {
        processedCount++;
        try {
          Optional<EventCompetitionParticipant> participantOpt = participantRepository.findById(
            registration.getParticipantProfileId()
          );
          Optional<EventCompetition> competitionOpt = competitionRepository.findById(registration.getCompetitionId());

          if (participantOpt.isEmpty()) {
            log.warn("Participant not found for registration {}", registration.getId());
            failedCount++;
            continue;
          }
          if (competitionOpt.isEmpty()) {
            log.warn("Competition not found for registration {}", registration.getId());
            failedCount++;
            continue;
          }

          EventCompetitionParticipant participant = participantOpt.get();
          EventCompetition competition = competitionOpt.get();

          String recipientEmail = participant.getEmail();
          if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("No email for participant {} on registration {}", participant.getId(), registration.getId());
            failedCount++;
            continue;
          }

          String participantName = resolveParticipantName(participant);
          String teamDisplayName = registration.getTeamDisplayName() != null && !registration.getTeamDisplayName().isBlank()
            ? registration.getTeamDisplayName()
            : registration.getTeamName();
          List<String> rosterNames = resolveRosterNames(registration);
          String subject;
          String bodyHtml;

          if (template != null) {
            emailContentBuilderService.ensureHeaderAndFooterReady(request.getTenantId(), 10);
            Map<String, String> emailContent = emailContentBuilderService.buildEmailContent(
              template,
              null,
              null,
              request.getTenantId()
            );
            subject = eventCompetitionEmailContentBuilderService.applyTemplatePlaceholders(
              emailContent.get("subject"),
              participantName,
              competition.getName(),
              registration.getFeeAmount(),
              eventTitle,
              null
            );
            bodyHtml = eventCompetitionEmailContentBuilderService.applyTemplatePlaceholders(
              emailContent.get("bodyHtml"),
              participantName,
              competition.getName(),
              registration.getFeeAmount(),
              eventTitle,
              null
            );
          } else {
            subject = String.format(
              "Competition Registration Confirmed%s",
              eventTitle != null ? " - " + eventTitle : ""
            );
            bodyHtml = eventCompetitionEmailContentBuilderService.buildRegistrationConfirmationEmailBody(
              participantName,
              competition.getName(),
              registration.getFeeAmount(),
              eventTitle,
              request.getTenantId(),
              teamDisplayName,
              rosterNames
            );
          }

          emailService.sendEmail(fromAddress, null, recipientEmail, subject, bodyHtml, true);

          registration.setConfirmationEmailSent(true);
          registration.setUpdatedAt(ZonedDateTime.now());
          registrationRepository.save(registration);
          successCount++;
        } catch (Exception e) {
          log.error("Failed to send registration confirmation for registration {}: {}", registration.getId(), e.getMessage(), e);
          failedCount++;
        }
      }

      batchJobExecutionService.completeJobExecution(
        executionId,
        failedCount > 0 && successCount == 0 ? "FAILED" : "COMPLETED",
        processedCount,
        successCount,
        failedCount,
        failedCount > 0 ? "Some registration emails failed" : null
      );
    } catch (Exception e) {
      log.error("Failed to process event competition registration email job {}: {}", executionId, e.getMessage(), e);
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

  private PromotionEmailTemplate resolveTemplate(Long templateId, String tenantId) {
    if (templateId == null) {
      return null;
    }
    return promotionEmailTemplateRepository.findByIdAndTenantId(templateId, tenantId).orElse(null);
  }

  private String resolveEventTitle(Long eventId) {
    if (eventId == null) {
      return null;
    }
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

  private String resolveParticipantName(EventCompetitionParticipant participant) {
    if (participant.getDisplayName() != null && !participant.getDisplayName().isBlank()) {
      return participant.getDisplayName();
    }
    return participant.getFirstName() + " " + participant.getLastName();
  }

  private List<String> resolveRosterNames(EventCompetitionRegistration registration) {
    List<com.eventmanager.batch.domain.EventCompetitionGroupMember> members = groupMemberRepository.findByRegistrationIdAndTenantIdOrderBySortOrderAsc(
      registration.getId(),
      registration.getTenantId()
    );
    if (members.isEmpty()) {
      return List.of();
    }
    List<String> names = new java.util.ArrayList<>();
    for (com.eventmanager.batch.domain.EventCompetitionGroupMember member : members) {
      participantRepository.findById(member.getParticipantProfileId()).ifPresent(p -> names.add(resolveParticipantName(p)));
    }
    return names;
  }

  private EventCompetitionRegistrationEmailJobResponse failureResponse(String message) {
    return EventCompetitionRegistrationEmailJobResponse.builder()
      .success(false)
      .message(message)
      .processedCount(0L)
      .successCount(0L)
      .failedCount(0L)
      .build();
  }
}
