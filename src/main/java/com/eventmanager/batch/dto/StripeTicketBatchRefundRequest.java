package com.eventmanager.batch.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.ZonedDateTime;

/**
 * Request DTO for Stripe ticket batch refund job.
 */
@Data
public class StripeTicketBatchRefundRequest {
    /**
     * Event ID to process refunds for (required).
     */
    @NotNull(message = "eventId is required")
    private Long eventId;

    /**
     * Tenant ID for data isolation (required).
     */
    @NotNull(message = "tenantId is required")
    private String tenantId;

    /**
     * Unique job identifier (required).
     */
    @NotNull(message = "jobId is required")
    private String jobId;

    /**
     * Optional start date filter for purchase date (ISO 8601 format).
     * Process transactions with purchase date on or after this date.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private ZonedDateTime startDate;

    /**
     * Optional end date filter for purchase date (ISO 8601 format).
     * Process transactions with purchase date on or before this date.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private ZonedDateTime endDate;
}
