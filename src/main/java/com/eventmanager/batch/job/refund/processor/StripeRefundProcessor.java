package com.eventmanager.batch.job.refund.processor;

import com.eventmanager.batch.domain.EventTicketTransaction;
import com.eventmanager.batch.job.refund.processor.dto.RefundProcessingResult;
import com.eventmanager.batch.service.StripeRefundService;
import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Processor for Stripe Ticket Batch Refund Job.
 * Processes each ticket by calling Stripe refund API.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StripeRefundProcessor implements ItemProcessor<EventTicketTransaction, RefundProcessingResult> {

    private final StripeRefundService stripeRefundService;

    private String jobId;
    private Long eventId;
    private String tenantId;

    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        JobExecution jobExecution = stepExecution.getJobExecution();
        this.jobId = jobExecution.getJobParameters().getString("jobId");
        this.eventId = jobExecution.getJobParameters().getLong("eventId");
        this.tenantId = jobExecution.getJobParameters().getString("tenantId");
        log.debug("Initialized StripeRefundProcessor - jobId: {}, eventId: {}, tenantId: {}",
            jobId, eventId, tenantId);
    }

    @Override
    public RefundProcessingResult process(EventTicketTransaction ticket) throws Exception {
        if (ticket == null) {
            log.warn("Received null ticket, skipping");
            return null;
        }

        // Idempotency check: Skip if already refunded
        if ("REFUNDED".equals(ticket.getStatus())) {
            log.info("Ticket {} already refunded, skipping", ticket.getId());
            return RefundProcessingResult.skipped(ticket.getId(), "Already refunded");
        }

        // Validate ticket still meets eligibility criteria
        if (ticket.getStripePaymentIntentId() == null || ticket.getStripePaymentIntentId().isEmpty()) {
            log.warn("Ticket {} has no stripe_payment_intent_id, skipping", ticket.getId());
            return RefundProcessingResult.skipped(ticket.getId(), "No stripe_payment_intent_id");
        }

        if (!"succeeded".equals(ticket.getStripePaymentStatus()) && !"paid".equals(ticket.getStripePaymentStatus())) {
            log.warn("Ticket {} has invalid stripe_payment_status: {}, skipping",
                ticket.getId(), ticket.getStripePaymentStatus());
            return RefundProcessingResult.skipped(ticket.getId(), "Invalid stripe_payment_status: " + ticket.getStripePaymentStatus());
        }

        try {
            log.info("Processing refund for ticket {} - payment intent: {}",
                ticket.getId(), ticket.getStripePaymentIntentId());

            // Call Stripe refund API
            Refund refund = stripeRefundService.createRefund(
                tenantId,
                ticket.getStripePaymentIntentId(),
                ticket.getId(),
                jobId,
                eventId
            );

            // Convert refund amount from cents to dollars
            BigDecimal refundAmount = refund.getAmount() != null
                ? BigDecimal.valueOf(refund.getAmount()).divide(BigDecimal.valueOf(100))
                : ticket.getFinalAmount();

            log.info("Successfully created Stripe refund {} for ticket {} - amount: ${}",
                refund.getId(), ticket.getId(), refundAmount);

            return RefundProcessingResult.success(
                ticket,
                refund.getId(),
                refundAmount
            );

        } catch (StripeException e) {
            String errorType = determineErrorType(e);
            String errorMessage = e.getMessage();

            log.error("Failed to create refund for ticket {} - payment intent: {}: {}",
                ticket.getId(), ticket.getStripePaymentIntentId(), errorMessage, e);

            // Handle specific error cases
            if (isAlreadyRefundedError(e)) {
                log.warn("Ticket {} payment intent {} already refunded in Stripe",
                    ticket.getId(), ticket.getStripePaymentIntentId());
                return RefundProcessingResult.skipped(ticket.getId(), "Already refunded in Stripe: " + errorMessage);
            }

            return RefundProcessingResult.failed(
                ticket.getId(),
                errorMessage,
                errorType
            );
        } catch (Exception e) {
            log.error("Unexpected error processing refund for ticket {}: {}",
                ticket.getId(), e.getMessage(), e);
            return RefundProcessingResult.failed(
                ticket.getId(),
                e.getMessage(),
                "UNEXPECTED_ERROR"
            );
        }
    }

    /**
     * Determine error type from Stripe exception.
     */
    private String determineErrorType(StripeException e) {
        String code = e.getCode();
        Integer statusCode = e.getStatusCode();

        if (statusCode != null && statusCode == 429) {
            return "RATE_LIMIT_EXCEEDED";
        }

        if (code != null) {
            if (code.contains("already_refunded") || code.contains("refund")) {
                return "ALREADY_REFUNDED";
            }
            if (code.contains("invalid") || code.contains("not_found")) {
                return "INVALID_PAYMENT_INTENT";
            }
            if (code.contains("insufficient")) {
                return "INSUFFICIENT_FUNDS";
            }
        }

        return "STRIPE_API_ERROR";
    }

    /**
     * Check if error indicates payment intent is already refunded.
     */
    private boolean isAlreadyRefundedError(StripeException e) {
        String code = e.getCode();
        String message = e.getMessage();

        if (code != null && (code.contains("already_refunded") || code.contains("refund"))) {
            return true;
        }

        if (message != null && (
            message.toLowerCase().contains("already refunded") ||
            message.toLowerCase().contains("already been refunded")
        )) {
            return true;
        }

        return false;
    }
}
