package com.eventmanager.batch.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Response DTO for Stripe ticket batch refund job.
 */
@Data
@Builder
public class StripeTicketBatchRefundResponse {
    /**
     * Unique job ID for tracking.
     */
    private String jobId;

    /**
     * Job status: STARTED, IN_PROGRESS, COMPLETED, FAILED
     */
    private String status;

    /**
     * Event ID being processed.
     */
    private Long eventId;

    /**
     * Tenant ID being processed.
     */
    private String tenantId;

    /**
     * Start date filter (null if not provided).
     */
    private ZonedDateTime startDate;

    /**
     * End date filter (null if not provided).
     */
    private ZonedDateTime endDate;

    /**
     * Total number of eligible tickets found.
     */
    private Long totalEligibleTickets;

    /**
     * Number of tickets processed (success + failure).
     */
    private Long processedCount;

    /**
     * Number of successful refunds.
     */
    private Long successCount;

    /**
     * Number of failed refunds.
     */
    private Long failedCount;

    /**
     * Number of skipped tickets (already refunded, etc.).
     */
    private Long skippedCount;

    /**
     * Total refund amount (sum of all successful refunds).
     */
    private BigDecimal totalRefundAmount;

    /**
     * Job start time.
     */
    private ZonedDateTime startTime;

    /**
     * Estimated completion time.
     */
    private ZonedDateTime estimatedCompletionTime;

    /**
     * Human-readable message.
     */
    private String message;

    /**
     * List of failed ticket IDs with error messages (optional, for detailed reporting).
     */
    private List<FailedRefund> failedRefunds;

    /**
     * Inner class for failed refund details.
     */
    @Data
    @Builder
    public static class FailedRefund {
        private Long ticketTransactionId;
        private String errorMessage;
        private String errorType;
    }
}
