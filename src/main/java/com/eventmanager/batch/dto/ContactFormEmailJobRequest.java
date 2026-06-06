package com.eventmanager.batch.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO representing a single contact form email job.
 */
@Data
public class ContactFormEmailJobRequest {

    @NotBlank
    @Size(max = 255)
    private String tenantId;

    @NotBlank
    @Size(max = 255)
    private String firstName;

    @NotBlank
    @Size(max = 255)
    private String lastName;

    @NotBlank
    @Size(max = 4096)
    private String messageBody;

    /**
     * Visitor email address (Reply-To and confirmation copy for CONTACT type).
     */
    @NotBlank
    @Email
    @Size(max = 255)
    @JsonAlias("fromEmail")
    private String senderEmail;

    /**
     * Tenant email type used to resolve SES from/to from tenant_email_addresses (e.g. CONTACT).
     */
    @NotBlank
    @Size(max = 50)
    private String emailType;

    /**
     * Optional inbox override. When blank, resolved from tenant_email_addresses by emailType.
     */
    @Email
    @Size(max = 255)
    private String toEmail;

    // Optional metadata for logging/auditing
    private Long submittedAtEpochMillis;

    private Long userId;
}
