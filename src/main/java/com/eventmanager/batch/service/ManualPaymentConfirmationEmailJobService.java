package com.eventmanager.batch.service;

import com.eventmanager.batch.domain.BatchJobExecution;
import com.eventmanager.batch.domain.TenantEmailAddress;
import com.eventmanager.batch.domain.enumeration.TenantEmailType;
import com.eventmanager.batch.dto.ManualPaymentConfirmationEmailJobRequest;
import com.eventmanager.batch.dto.ManualPaymentConfirmationEmailJobResponse;
import com.eventmanager.batch.repository.TenantEmailAddressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service responsible for handling manual payment confirmation email jobs.
 * Sends confirmation email immediately after payment request creation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ManualPaymentConfirmationEmailJobService {

    private final BatchJobExecutionService batchJobExecutionService;
    private final TenantEmailAddressRepository tenantEmailAddressRepository;
    private final EmailContentBuilderService emailContentBuilderService;
    private final EmailService emailService;

    /**
     * Entry point used by REST controller to trigger a manual payment confirmation email job.
     * Creates a job execution record and starts asynchronous processing.
     */
    public ManualPaymentConfirmationEmailJobResponse triggerManualPaymentConfirmationEmailJob(
        ManualPaymentConfirmationEmailJobRequest request
    ) {
        // Basic validation
        if (request.getTenantId() == null || request.getTenantId().isBlank()) {
            return ManualPaymentConfirmationEmailJobResponse.builder()
                .success(false)
                .message("Tenant ID is required")
                .processedCount(0L)
                .successCount(0L)
                .failedCount(0L)
                .build();
        }

        if (request.getPaymentRequestId() == null) {
            return ManualPaymentConfirmationEmailJobResponse.builder()
                .success(false)
                .message("Payment request ID is required")
                .processedCount(0L)
                .successCount(0L)
                .failedCount(0L)
                .build();
        }

        String parametersJson = String.format(
            "{\"paymentRequestId\":%d,\"eventId\":%d,\"tenantId\":\"%s\",\"recipientEmail\":\"%s\"}",
            request.getPaymentRequestId(),
            request.getEventId(),
            request.getTenantId(),
            request.getRecipientEmail()
        );

        BatchJobExecution execution = batchJobExecutionService.createJobExecution(
            "manualPaymentConfirmationEmailJob",
            "MANUAL_PAYMENT_CONFIRMATION_EMAIL",
            request.getTenantId(),
            "API",
            parametersJson
        );

        // Fire-and-forget async processing
        processManualPaymentConfirmationEmailAsync(execution.getId(), request);

        return ManualPaymentConfirmationEmailJobResponse.builder()
            .success(true)
            .message("Manual payment confirmation email job accepted for processing")
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
    protected void processManualPaymentConfirmationEmailAsync(
        Long executionId,
        ManualPaymentConfirmationEmailJobRequest request
    ) {
        long processedCount = 0L;
        long successCount = 0L;
        long failedCount = 0L;

        try {
            String tenantId = request.getTenantId();

            String fromAddress = resolveFromEmail(tenantId);
            if (fromAddress == null || fromAddress.isEmpty()) {
                log.warn("No FROM address resolved for tenant: {}, aborting manual payment confirmation email", tenantId);
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
                "Payment Request Received - %s - %s",
                request.getEventTitle(),
                request.getTransactionReference() != null ? request.getTransactionReference() : "MANUAL-" + request.getPaymentRequestId()
            );

            String bodyHtml = emailContentBuilderService.buildManualPaymentConfirmationEmailBody(request);

            processedCount = 1L;

            // Send confirmation email
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
            log.error("Failed to process manual payment confirmation email job execution {}: {}", executionId, e.getMessage(), e);
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
