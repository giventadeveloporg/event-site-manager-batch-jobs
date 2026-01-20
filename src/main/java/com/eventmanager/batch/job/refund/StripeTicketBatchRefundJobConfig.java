package com.eventmanager.batch.job.refund;

import com.eventmanager.batch.job.refund.processor.dto.RefundProcessingResult;
import com.eventmanager.batch.job.refund.processor.StripeRefundProcessor;
import com.eventmanager.batch.job.refund.reader.EligibleTicketReader;
import com.eventmanager.batch.job.refund.writer.RefundStatusWriter;
import com.eventmanager.batch.domain.EventTicketTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Configuration for Stripe Ticket Batch Refund Job.
 * Processes eligible tickets and creates Stripe refunds.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class StripeTicketBatchRefundJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EligibleTicketReader reader;
    private final StripeRefundProcessor processor;
    private final RefundStatusWriter writer;

    @Value("${batch.stripe-refund.batch-size:100}")
    private int batchSize;

    /**
     * Stripe Ticket Batch Refund Job.
     */
    @Bean
    public Job stripeTicketBatchRefundJob() {
        return new JobBuilder("stripeTicketBatchRefundJob", jobRepository)
            .start(stripeTicketBatchRefundStep())
            .build();
    }

    /**
     * Stripe Ticket Batch Refund Step.
     */
    @Bean
    public Step stripeTicketBatchRefundStep() {
        return new StepBuilder("stripeTicketBatchRefundStep", jobRepository)
            .<EventTicketTransaction, RefundProcessingResult>chunk(batchSize, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build();
    }
}
