package com.eventmanager.batch.service;

import com.eventmanager.batch.domain.BatchJobExecution;
import com.eventmanager.batch.domain.TenantEmailAddress;
import com.eventmanager.batch.domain.enumeration.TenantEmailType;
import com.eventmanager.batch.dto.ManualPaymentTicketEmailJobRequest;
import com.eventmanager.batch.dto.ManualPaymentTicketEmailJobResponse;
import com.eventmanager.batch.repository.TenantEmailAddressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service responsible for handling manual payment ticket email jobs.
 * Sends ticket email with QR code after admin confirms payment receipt.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ManualPaymentTicketEmailJobService {

    private final BatchJobExecutionService batchJobExecutionService;
    private final TenantEmailAddressRepository tenantEmailAddressRepository;
    private final EmailContentBuilderService emailContentBuilderService;
    private final EmailService emailService;

    /**
     * Entry point used by REST controller to trigger a manual payment ticket email job.
     * Creates a job execution record and starts asynchronous processing.
     */
    public ManualPaymentTicketEmailJobResponse triggerManualPaymentTicketEmailJob(
        ManualPaymentTicketEmailJobRequest request
    ) {
        // Basic validation
        if (request.getTenantId() == null || request.getTenantId().isBlank()) {
            return ManualPaymentTicketEmailJobResponse.builder()
                .success(false)
                .message("Tenant ID is required")
                .processedCount(0L)
                .successCount(0L)
                .failedCount(0L)
                .build();
        }

        if (request.getTicketTransactionId() == null) {
            return ManualPaymentTicketEmailJobResponse.builder()
                .success(false)
                .message("Ticket transaction ID is required")
                .processedCount(0L)
                .successCount(0L)
                .failedCount(0L)
                .build();
        }

        if (request.getQrCodeImageUrl() == null || request.getQrCodeImageUrl().isBlank()) {
            return ManualPaymentTicketEmailJobResponse.builder()
                .success(false)
                .message("QR code image URL is required")
                .processedCount(0L)
                .successCount(0L)
                .failedCount(0L)
                .build();
        }

        String parametersJson = String.format(
            "{\"paymentRequestId\":%d,\"eventId\":%d,\"ticketTransactionId\":%d,\"tenantId\":\"%s\",\"recipientEmail\":\"%s\"}",
            request.getPaymentRequestId(),
            request.getEventId(),
            request.getTicketTransactionId(),
            request.getTenantId(),
            request.getRecipientEmail()
        );

        BatchJobExecution execution = batchJobExecutionService.createJobExecution(
            "manualPaymentTicketEmailJob",
            "MANUAL_PAYMENT_TICKET_EMAIL",
            request.getTenantId(),
            "API",
            parametersJson
        );

        // Fire-and-forget async processing
        processManualPaymentTicketEmailAsync(execution.getId(), request);

        return ManualPaymentTicketEmailJobResponse.builder()
            .success(true)
            .message("Manual payment ticket email job accepted for processing")
            .jobExecutionId(execution.getId())
            .processedCount(0L)
            .successCount(0L)
            .failedCount(0L)
            .build();
    }

    /**
     * Asynchronous handler that performs the actual email sending.
     */
    @Async
    protected void processManualPaymentTicketEmailAsync(
        Long executionId,
        ManualPaymentTicketEmailJobRequest request
    ) {
        long processedCount = 0L;
        long successCount = 0L;
        long failedCount = 0L;

        try {
            String tenantId = request.getTenantId();

            String fromAddress = resolveFromEmail(tenantId);
            if (fromAddress == null || fromAddress.isEmpty()) {
                log.warn("No FROM address resolved for tenant: {}, aborting manual payment ticket email", tenantId);
                failedCount = 1L;
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

            String subject = String.format(
                "Your Tickets for %s - %s",
                request.getEventTitle(),
                request.getTransactionReference() != null ? request.getTransactionReference() : "MANUAL-" + request.getPaymentRequestId()
            );

            String bodyHtml = emailContentBuilderService.buildManualPaymentTicketEmailBody(request);

            processedCount = 1L;

            // Send ticket email
            emailService.sendEmail(
                fromAddress,
                null,
                request.getRecipientEmail(),
                subject,
                bodyHtml,
                true
            );

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
            log.error("Failed to process manual payment ticket email job execution {}: {}", executionId, e.getMessage(), e);
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

    /**
     * Resolve FROM address for manual payment emails for a tenant.
     * Prefers CONTACT type, then default, then any active email address.
     */
    @Cacheable(cacheNames = "tenantEmailFromCache", key = "#tenantId", unless = "#result == null || #result.isEmpty()")
    public String resolveFromEmail(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return "";
        }

        // 1. Prefer CONTACT type, active and default first
        Optional<TenantEmailAddress> preferredContact = tenantEmailAddressRepository
            .findFirstByTenantIdAndEmailTypeAndIsActiveTrueOrderByIsDefaultDesc(tenantId, TenantEmailType.CONTACT);

        if (preferredContact.isPresent()) {
            return preferredContact.get().getEmailAddress();
        }

        // 2. Fall back to default active email
        Optional<TenantEmailAddress> defaultEmail = tenantEmailAddressRepository
            .findFirstByTenantIdAndIsDefaultTrueAndIsActiveTrueOrderByIdAsc(tenantId);
        if (defaultEmail.isPresent()) {
            return defaultEmail.get().getEmailAddress();
        }

        // 3. Fall back to any active email
        List<TenantEmailAddress> activeEmails = tenantEmailAddressRepository
            .findByTenantIdAndIsActiveTrue(tenantId);
        if (!activeEmails.isEmpty()) {
            return activeEmails.get(0).getEmailAddress();
        }

        log.warn("No active tenant email address found for tenant: {}", tenantId);
        return "";
    }
}
