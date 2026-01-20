package com.eventmanager.batch.service;

import com.eventmanager.batch.domain.BatchJobExecution;
import com.eventmanager.batch.dto.StripeTicketBatchRefundRequest;
import com.eventmanager.batch.dto.StripeTicketBatchRefundResponse;
import com.eventmanager.batch.job.refund.reader.EligibleTicketReader;
import com.eventmanager.batch.repository.EventTicketTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service for orchestrating Stripe ticket batch refund job execution.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeTicketBatchRefundService {

    private final JobLauncher jobLauncher;

    @Qualifier("stripeTicketBatchRefundJob")
    private final Job stripeTicketBatchRefundJob;

    private final EligibleTicketReader eligibleTicketReader;
    private final BatchJobExecutionService batchJobExecutionService;
    private final EventTicketTransactionRepository transactionRepository;

    @Value("${batch.stripe-refund.batch-size:100}")
    private int defaultBatchSize;

    /**
     * Trigger Stripe ticket batch refund job.
     *
     * @param request The refund request
     * @return Response with job details
     */
    @Async
    public CompletableFuture<StripeTicketBatchRefundResponse> processBatchRefund(StripeTicketBatchRefundRequest request) {
        String jobId = request.getJobId();
        Long eventId = request.getEventId();
        String tenantId = request.getTenantId();
        ZonedDateTime startDate = request.getStartDate();
        ZonedDateTime endDate = request.getEndDate();

        log.info("Starting Stripe ticket batch refund job - jobId: {}, eventId: {}, tenantId: {}, startDate: {}, endDate: {}",
            jobId, eventId, tenantId, startDate, endDate);

        // Create job execution record
        BatchJobExecution execution = batchJobExecutionService.createJobExecution(
            "stripeTicketBatchRefundJob",
            "STRIPE_TICKET_BATCH_REFUND",
            tenantId,
            "API",
            String.format("{\"jobId\":\"%s\",\"eventId\":%d,\"tenantId\":\"%s\",\"startDate\":\"%s\",\"endDate\":\"%s\"}",
                jobId, eventId, tenantId,
                startDate != null ? startDate.toString() : null,
                endDate != null ? endDate.toString() : null)
        );

        try {
            // Count eligible tickets
            Timestamp startTimestamp = startDate != null ? Timestamp.from(startDate.toInstant()) : null;
            Timestamp endTimestamp = endDate != null ? Timestamp.from(endDate.toInstant()) : null;
            long totalEligibleTickets = transactionRepository.countEligibleTicketsForRefund(
                eventId, tenantId, startTimestamp, endTimestamp
            );

            log.info("Found {} eligible tickets for refund - jobId: {}, eventId: {}", totalEligibleTickets, jobId, eventId);

            if (totalEligibleTickets == 0) {
                // No eligible tickets - complete job immediately
                batchJobExecutionService.completeJobExecution(
                    execution.getId(),
                    "COMPLETED",
                    0L, 0L, 0L,
                    "No eligible tickets found"
                );

                StripeTicketBatchRefundResponse response = StripeTicketBatchRefundResponse.builder()
                    .jobId(jobId)
                    .status("COMPLETED")
                    .eventId(eventId)
                    .tenantId(tenantId)
                    .startDate(startDate)
                    .endDate(endDate)
                    .totalEligibleTickets(0L)
                    .processedCount(0L)
                    .successCount(0L)
                    .failedCount(0L)
                    .skippedCount(0L)
                    .totalRefundAmount(BigDecimal.ZERO)
                    .startTime(ZonedDateTime.now())
                    .message("No eligible tickets found")
                    .build();

                return CompletableFuture.completedFuture(response);
            }

            // Initialize reader with parameters
            eligibleTicketReader.initialize(eventId, tenantId, startDate, endDate);

            // Build job parameters
            JobParameters jobParameters = new JobParametersBuilder()
                .addString("jobId", jobId)
                .addLong("eventId", eventId)
                .addString("tenantId", tenantId)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

            // Launch job asynchronously
            jobLauncher.run(stripeTicketBatchRefundJob, jobParameters);

            // Estimate completion time (rough estimate: 2 seconds per ticket with delays)
            ZonedDateTime estimatedCompletion = ZonedDateTime.now().plusSeconds(totalEligibleTickets * 2);

            StripeTicketBatchRefundResponse response = StripeTicketBatchRefundResponse.builder()
                .jobId(jobId)
                .status("STARTED")
                .eventId(eventId)
                .tenantId(tenantId)
                .startDate(startDate)
                .endDate(endDate)
                .totalEligibleTickets(totalEligibleTickets)
                .processedCount(0L)
                .successCount(0L)
                .failedCount(0L)
                .skippedCount(0L)
                .totalRefundAmount(BigDecimal.ZERO)
                .startTime(ZonedDateTime.now())
                .estimatedCompletionTime(estimatedCompletion)
                .message("Batch refund job started successfully")
                .build();

            log.info("Stripe ticket batch refund job started - jobId: {}, executionId: {}", jobId, execution.getId());

            return CompletableFuture.completedFuture(response);

        } catch (Exception e) {
            log.error("Failed to start Stripe ticket batch refund job - jobId: {}: {}",
                jobId, e.getMessage(), e);

            batchJobExecutionService.completeJobExecution(
                execution.getId(),
                "FAILED",
                0L, 0L, 0L,
                e.getMessage()
            );

            StripeTicketBatchRefundResponse response = StripeTicketBatchRefundResponse.builder()
                .jobId(jobId)
                .status("FAILED")
                .eventId(eventId)
                .tenantId(tenantId)
                .startDate(startDate)
                .endDate(endDate)
                .message("Failed to start job: " + e.getMessage())
                .build();

            return CompletableFuture.completedFuture(response);
        }
    }
}
