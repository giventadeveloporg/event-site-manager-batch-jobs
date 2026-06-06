package com.eventmanager.batch.service;

import com.eventmanager.batch.domain.BatchJobExecution;
import com.eventmanager.batch.domain.TenantEmailAddress;
import com.eventmanager.batch.domain.enumeration.TenantEmailType;
import com.eventmanager.batch.dto.ContactFormEmailJobRequest;
import com.eventmanager.batch.dto.ContactFormEmailJobResponse;
import com.eventmanager.batch.repository.TenantEmailAddressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service responsible for handling contact form email jobs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContactFormEmailJobService {

    private final BatchJobExecutionService batchJobExecutionService;
    private final TenantEmailAddressRepository tenantEmailAddressRepository;
    private final EmailContentBuilderService emailContentBuilderService;
    private final EmailService emailService;

    /**
     * Entry point used by REST controller to trigger a contact form email job.
     * Creates a job execution record and starts asynchronous processing.
     */
    public ContactFormEmailJobResponse triggerContactFormEmailJob(ContactFormEmailJobRequest request) {
        if (request.getTenantId() == null || request.getTenantId().isBlank()) {
            return ContactFormEmailJobResponse.builder()
                .success(false)
                .message("Tenant ID is required")
                .processedCount(0L)
                .successCount(0L)
                .failedCount(0L)
                .build();
        }

        String parametersJson = String.format(
            "{\"tenantId\":\"%s\",\"senderEmail\":\"%s\",\"emailType\":\"%s\"}",
            request.getTenantId(),
            request.getSenderEmail(),
            request.getEmailType()
        );

        BatchJobExecution execution = batchJobExecutionService.createJobExecution(
            "contactFormEmailJob",
            "CONTACT_FORM_EMAIL",
            request.getTenantId(),
            "API",
            parametersJson
        );

        processContactFormEmailAsync(execution.getId(), request);

        return ContactFormEmailJobResponse.builder()
            .success(true)
            .message("Contact form email job accepted for processing")
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
    protected void processContactFormEmailAsync(Long executionId, ContactFormEmailJobRequest request) {
        long processedCount = 0L;
        long successCount = 0L;
        long failedCount = 0L;

        try {
            String tenantId = request.getTenantId();
            TenantEmailType emailType = resolveEmailType(request);

            String fromAddress = resolveFromEmailByType(tenantId, emailType);
            if (fromAddress == null || fromAddress.isEmpty()) {
                log.warn("No FROM address resolved for tenant {} and emailType {}", tenantId, emailType);
                failedCount = 1L;
                batchJobExecutionService.completeJobExecution(
                    executionId,
                    "FAILED",
                    processedCount,
                    successCount,
                    failedCount,
                    "No FROM address configured for tenant and email type " + emailType
                );
                return;
            }

            String toAddress = request.getToEmail();
            if (toAddress == null || toAddress.isBlank()) {
                toAddress = resolveToEmailByType(tenantId, emailType);
            }
            if (toAddress == null || toAddress.isBlank()) {
                log.warn("No TO address resolved for tenant {} and emailType {}", tenantId, emailType);
                failedCount = 1L;
                batchJobExecutionService.completeJobExecution(
                    executionId,
                    "FAILED",
                    processedCount,
                    successCount,
                    failedCount,
                    "No TO address configured for tenant and email type " + emailType
                );
                return;
            }

            String copyToAddress = resolveCopyToEmailByType(tenantId, emailType);
            String replyToAddress = resolveEffectiveReplyTo(tenantId, emailType, request);

            String subject = String.format(
                "Contact Form Submission from %s %s",
                request.getFirstName(),
                request.getLastName()
            );

            String mainBody = emailContentBuilderService.buildContactEmailBody(request);
            String confirmationBody = emailContentBuilderService.buildContactConfirmationEmailBody(request);

            processedCount = 1L;

            // Main email to tenant inbox with Reply-To from tenant config or visitor email
            emailService.sendEmail(
                fromAddress,
                replyToAddress,
                toAddress,
                subject,
                mainBody,
                true
            );

            // Optional CC copy from tenant_email_addresses.copy_to_email_address
            if (copyToAddress != null && !copyToAddress.isBlank() && !copyToAddress.equalsIgnoreCase(toAddress)) {
                emailService.sendEmail(
                    fromAddress,
                    replyToAddress,
                    copyToAddress,
                    subject + " (Copy)",
                    mainBody,
                    true
                );
            }

            // Confirmation copy to visitor for CONTACT-type submissions
            if (TenantEmailType.CONTACT.equals(emailType)) {
                emailService.sendEmail(
                    fromAddress,
                    null,
                    request.getSenderEmail(),
                    "We received your message",
                    confirmationBody,
                    true
                );
            }

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
            log.error("Failed to process contact form email job execution {}: {}", executionId, e.getMessage(), e);
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

    private TenantEmailType resolveEmailType(ContactFormEmailJobRequest request) {
        if (request.getEmailType() == null || request.getEmailType().isBlank()) {
            return TenantEmailType.CONTACT;
        }
        try {
            return TenantEmailType.valueOf(request.getEmailType().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown emailType '{}', defaulting to CONTACT", request.getEmailType());
            return TenantEmailType.CONTACT;
        }
    }

    private Optional<TenantEmailAddress> selectTenantEmailAddress(String tenantId, TenantEmailType emailType) {
        Optional<TenantEmailAddress> preferred = tenantEmailAddressRepository
            .findFirstByTenantIdAndEmailTypeAndIsActiveTrueOrderByIsDefaultDesc(tenantId, emailType);
        if (preferred.isPresent()) {
            return preferred;
        }

        Optional<TenantEmailAddress> defaultEmail = tenantEmailAddressRepository
            .findFirstByTenantIdAndIsDefaultTrueAndIsActiveTrueOrderByIdAsc(tenantId);
        if (defaultEmail.isPresent()) {
            return defaultEmail;
        }

        List<TenantEmailAddress> activeEmails = tenantEmailAddressRepository.findByTenantIdAndIsActiveTrue(tenantId);
        if (!activeEmails.isEmpty()) {
            return Optional.of(activeEmails.get(0));
        }

        return Optional.empty();
    }

    /**
     * Resolve verified SES FROM address for the given tenant and email type.
     */
    @Cacheable(cacheNames = "tenantEmailFromCache", key = "#tenantId + ':' + #emailType", unless = "#result == null || #result.isEmpty()")
    public String resolveFromEmailByType(String tenantId, TenantEmailType emailType) {
        if (tenantId == null || tenantId.isBlank()) {
            return "";
        }

        return selectTenantEmailAddress(tenantId, emailType)
            .map(TenantEmailAddress::getEmailAddress)
            .orElseGet(() -> {
                log.warn("No active tenant email address found for tenant {} and type {}", tenantId, emailType);
                return "";
            });
    }

    /**
     * Resolve primary inbox (TO) for contact submissions.
     * Prefers copy_to_email_address when set, otherwise uses the verified from address.
     */
    @Cacheable(cacheNames = "tenantEmailToCache", key = "#tenantId + ':' + #emailType", unless = "#result == null || #result.isEmpty()")
    public String resolveToEmailByType(String tenantId, TenantEmailType emailType) {
        if (tenantId == null || tenantId.isBlank()) {
            return "";
        }

        Optional<TenantEmailAddress> selected = selectTenantEmailAddress(tenantId, emailType);
        if (selected.isEmpty()) {
            return "";
        }

        TenantEmailAddress address = selected.get();
        String copyTo = address.getCopyToEmailAddress();
        if (copyTo != null && !copyTo.isBlank()) {
            return copyTo;
        }
        return address.getEmailAddress();
    }

    /**
     * Resolve optional CC copy address from tenant_email_addresses.copy_to_email_address.
     */
    @Cacheable(cacheNames = "tenantEmailCopyToCache", key = "#tenantId + ':' + #emailType", unless = "#result == null || #result.isEmpty()")
    public String resolveCopyToEmailByType(String tenantId, TenantEmailType emailType) {
        if (tenantId == null || tenantId.isBlank()) {
            return "";
        }

        return selectTenantEmailAddress(tenantId, emailType)
            .map(TenantEmailAddress::getCopyToEmailAddress)
            .filter(copyTo -> copyTo != null && !copyTo.isBlank())
            .orElse("");
    }

    /**
     * Resolve optional Reply-To from tenant_email_addresses.reply_to_email_address.
     */
    @Cacheable(cacheNames = "tenantEmailReplyToCache", key = "#tenantId + ':' + #emailType", unless = "#result == null || #result.isEmpty()")
    public String resolveReplyToEmailByType(String tenantId, TenantEmailType emailType) {
        if (tenantId == null || tenantId.isBlank()) {
            return "";
        }

        return selectTenantEmailAddress(tenantId, emailType)
            .map(TenantEmailAddress::getReplyToEmailAddress)
            .filter(replyTo -> replyTo != null && !replyTo.isBlank())
            .orElse("");
    }

    /**
     * Reply-To for outbound mail: tenant reply_to when configured, else visitor senderEmail (contact forms).
     */
    private String resolveEffectiveReplyTo(String tenantId, TenantEmailType emailType, ContactFormEmailJobRequest request) {
        String configuredReplyTo = resolveReplyToEmailByType(tenantId, emailType);
        if (configuredReplyTo != null && !configuredReplyTo.isBlank()) {
            return configuredReplyTo;
        }
        return request.getSenderEmail();
    }
}
