package com.eventmanager.batch.job.refund.writer;

import com.eventmanager.batch.domain.EventTicketTransaction;
import com.eventmanager.batch.job.refund.processor.dto.RefundProcessingResult;
import com.eventmanager.batch.repository.EventTicketTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

/**
 * Writer for Stripe Ticket Batch Refund Job.
 * Updates database with refund status for successfully processed tickets.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefundStatusWriter implements ItemWriter<RefundProcessingResult> {

    private final EventTicketTransactionRepository repository;

    @Override
    @Transactional
    public void write(Chunk<? extends RefundProcessingResult> chunk) throws Exception {
        for (RefundProcessingResult result : chunk.getItems()) {
            if (result == null || result.getTicket() == null) {
                continue;
            }

            // Only update database for successful refunds
            if (result.isSuccess()) {
                updateTicketWithRefund(result);
            } else if (result.isSkipped() || result.isFailed()) {
                // Log skipped/failed tickets but don't update database
                log.debug("Ticket {} - Status: {}, Reason: {}",
                    result.getTicket().getId(), result.getStatus(), result.getErrorMessage());
            }
        }

        log.info("Processed {} refund results", chunk.size());
    }

    /**
     * Update ticket transaction with refund information.
     */
    private void updateTicketWithRefund(RefundProcessingResult result) {
        try {
            EventTicketTransaction ticket = result.getTicket();

            // Reload from database to ensure we have the latest version
            EventTicketTransaction ticketToUpdate = repository.findById(ticket.getId())
                .orElse(null);

            if (ticketToUpdate == null) {
                log.warn("Ticket {} not found in database, cannot update refund status", ticket.getId());
                return;
            }

            // Idempotency check: Only update if not already refunded
            if ("REFUNDED".equals(ticketToUpdate.getStatus())) {
                log.warn("Ticket {} already marked as REFUNDED, skipping update", ticket.getId());
                return;
            }

            // Update refund fields
            ticketToUpdate.setStatus("REFUNDED");
            ticketToUpdate.setRefundAmount(result.getRefundAmount());
            ticketToUpdate.setRefundDate(ZonedDateTime.now());
            ticketToUpdate.setRefundReason("Event canceled - Batch refund");
            ticketToUpdate.setStripePaymentStatus("refunded");
            ticketToUpdate.setUpdatedAt(ZonedDateTime.now());

            // Save to database
            repository.save(ticketToUpdate);

            log.info("Updated ticket {} with refund status - refund ID: {}, amount: ${}",
                ticket.getId(), result.getStripeRefundId(), result.getRefundAmount());

        } catch (Exception e) {
            log.error("Failed to update ticket {} with refund status: {}",
                result.getTicket().getId(), e.getMessage(), e);
            // Continue processing other tickets
        }
    }
}
