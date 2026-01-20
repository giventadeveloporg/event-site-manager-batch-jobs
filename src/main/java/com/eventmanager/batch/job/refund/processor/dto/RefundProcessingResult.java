package com.eventmanager.batch.job.refund.processor.dto;

import com.eventmanager.batch.domain.EventTicketTransaction;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Result of refund processing for a single ticket.
 */
@Data
public class RefundProcessingResult {
    private EventTicketTransaction ticket;
    private String status; // SUCCESS, FAILED, SKIPPED
    private String stripeRefundId;
    private BigDecimal refundAmount;
    private String errorMessage;
    private String errorType;

    private RefundProcessingResult() {
    }

    public static RefundProcessingResult success(EventTicketTransaction ticket, String stripeRefundId, BigDecimal refundAmount) {
        RefundProcessingResult result = new RefundProcessingResult();
        result.ticket = ticket;
        result.status = "SUCCESS";
        result.stripeRefundId = stripeRefundId;
        result.refundAmount = refundAmount;
        return result;
    }

    public static RefundProcessingResult failed(Long ticketId, String errorMessage, String errorType) {
        RefundProcessingResult result = new RefundProcessingResult();
        // Create a minimal ticket object with just the ID for failed cases
        EventTicketTransaction ticket = new EventTicketTransaction();
        ticket.setId(ticketId);
        result.ticket = ticket;
        result.status = "FAILED";
        result.errorMessage = errorMessage;
        result.errorType = errorType;
        return result;
    }

    public static RefundProcessingResult skipped(Long ticketId, String reason) {
        RefundProcessingResult result = new RefundProcessingResult();
        EventTicketTransaction ticket = new EventTicketTransaction();
        ticket.setId(ticketId);
        result.ticket = ticket;
        result.status = "SKIPPED";
        result.errorMessage = reason;
        return result;
    }

    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }

    public boolean isFailed() {
        return "FAILED".equals(status);
    }

    public boolean isSkipped() {
        return "SKIPPED".equals(status);
    }
}
