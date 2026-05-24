package com.eventmanager.batch.service;

import com.eventmanager.batch.domain.BatchJobExecution;
import com.eventmanager.batch.dto.BatchJobExecutionDTO;
import com.eventmanager.batch.dto.BatchJobExecutionPageResponse;
import com.eventmanager.batch.dto.BatchJobSummaryDTO;
import com.eventmanager.batch.dto.ConfiguredBatchJobDTO;
import com.eventmanager.batch.repository.BatchJobExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BatchJobMonitoringService {

    private final BatchJobExecutionRepository batchJobExecutionRepository;
    private final Environment environment;

    public BatchJobExecutionPageResponse getExecutions(
        String status,
        String jobName,
        String tenantId,
        ZonedDateTime startedAfter,
        ZonedDateTime startedBefore,
        int page,
        int size,
        String sort
    ) {
        Pageable pageable = PageRequest.of(page, size, parseSort(sort));
        Specification<BatchJobExecution> spec = buildSpec(status, jobName, tenantId, startedAfter, startedBefore);
        Page<BatchJobExecution> executions = batchJobExecutionRepository.findAll(spec, pageable);
        return BatchJobExecutionPageResponse.builder()
            .content(executions.getContent().stream().map(this::toDto).toList())
            .totalElements(executions.getTotalElements())
            .totalPages(executions.getTotalPages())
            .page(page)
            .size(size)
            .build();
    }

    public BatchJobExecutionDTO getExecution(Long id) {
        BatchJobExecution execution = batchJobExecutionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Job execution not found: " + id));
        return toDto(execution);
    }

    public List<BatchJobExecutionDTO> getFailedExecutions(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "startedAt"));
        Specification<BatchJobExecution> spec = (root, query, cb) -> cb.equal(root.get("status"), "FAILED");
        return batchJobExecutionRepository.findAll(spec, pageable).getContent().stream().map(this::toDto).toList();
    }

    public List<BatchJobExecutionDTO> getRunningExecutions(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "startedAt"));
        Specification<BatchJobExecution> spec = (root, query, cb) -> cb.equal(root.get("status"), "RUNNING");
        return batchJobExecutionRepository.findAll(spec, pageable).getContent().stream().map(this::toDto).toList();
    }

    public BatchJobSummaryDTO getSummary(
        String tenantId,
        ZonedDateTime startedAfter,
        ZonedDateTime startedBefore
    ) {
        Specification<BatchJobExecution> baseSpec = buildSpec(null, null, tenantId, startedAfter, startedBefore);
        long totalCount = batchJobExecutionRepository.count(baseSpec);
        long runningCount = batchJobExecutionRepository.count(baseSpec.and(statusSpec("RUNNING")));
        long completedCount = batchJobExecutionRepository.count(baseSpec.and(statusSpec("COMPLETED")));
        long failedCount = batchJobExecutionRepository.count(baseSpec.and(statusSpec("FAILED")));

        return BatchJobSummaryDTO.builder()
            .runningCount(runningCount)
            .completedCount(completedCount)
            .failedCount(failedCount)
            .totalCount(totalCount)
            .build();
    }

    public List<ConfiguredBatchJobDTO> getConfiguredJobs() {
        List<ConfiguredBatchJobDTO> jobs = new ArrayList<>();
        jobs.add(configuredJob("SUBSCRIPTION_RENEWAL", "subscription-renewal", "Subscription renewal job"));
        jobs.add(configuredJob("EMAIL_BATCH", "email", "Batch email processing job"));
        jobs.add(configuredJob("STRIPE_FEES_TAX_UPDATE", "stripe-fees-tax", "Stripe fees/tax update job"));
        jobs.add(configuredJob("MANUAL_PAYMENT_SUMMARY", "manual-payment-summary", "Manual payment summary aggregation"));

        jobs.add(onDemandJob("CONTACT_FORM_EMAIL", "On-demand contact form email batch job"));
        jobs.add(onDemandJob("PROMOTION_TEST_EMAIL", "On-demand promotion test email job"));
        jobs.add(onDemandJob("MANUAL_PAYMENT_CONFIRMATION_EMAIL", "On-demand manual payment confirmation email job"));
        jobs.add(onDemandJob("MANUAL_PAYMENT_TICKET_EMAIL", "On-demand manual payment ticket email job"));
        jobs.add(onDemandJob("STRIPE_TICKET_BATCH_REFUND", "On-demand Stripe ticket batch refund job"));
        jobs.add(onDemandJob("DONATION_EMAIL", "On-demand donation email job"));
        jobs.add(onDemandJob("DONATION_QRCODE", "On-demand donation QR code generation job"));
        jobs.add(onDemandJob("EVENT_COMPETITION_REGISTRATION_EMAIL", "On-demand event competition registration confirmation email job"));
        jobs.add(onDemandJob("EVENT_COMPETITION_WINNERS_PUBLISHED_EMAIL", "On-demand event competition winners published notification job"));
        jobs.add(onDemandJob("EVENT_COMPETITION_REGISTRATION_SUMMARY", "On-demand event competition registration summary report job"));
        return jobs;
    }

    private ConfiguredBatchJobDTO configuredJob(String jobName, String propertyPrefix, String description) {
        String enabled = environment.getProperty("batch." + propertyPrefix + ".enabled");
        String cron = environment.getProperty("batch." + propertyPrefix + ".schedule-cron");
        return ConfiguredBatchJobDTO.builder()
            .jobName(jobName)
            .jobType("SCHEDULED")
            .enabled(enabled == null ? null : Boolean.parseBoolean(enabled))
            .scheduleCron(cron)
            .description(description)
            .build();
    }

    private ConfiguredBatchJobDTO onDemandJob(String jobName, String description) {
        return ConfiguredBatchJobDTO.builder()
            .jobName(jobName)
            .jobType("ON_DEMAND")
            .enabled(true)
            .scheduleCron(null)
            .description(description)
            .build();
    }

    private Specification<BatchJobExecution> buildSpec(
        String status,
        String jobName,
        String tenantId,
        ZonedDateTime startedAfter,
        ZonedDateTime startedBefore
    ) {
        Specification<BatchJobExecution> spec = Specification.where(null);
        if (status != null && !status.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (jobName != null && !jobName.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("jobName"), jobName));
        }
        if (tenantId != null && !tenantId.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("tenantId"), tenantId));
        }
        if (startedAfter != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("startedAt"), startedAfter));
        }
        if (startedBefore != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("startedAt"), startedBefore));
        }
        return spec;
    }

    private Specification<BatchJobExecution> statusSpec(String status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "startedAt");
        }
        String[] parts = sort.split(",");
        String property = parts[0];
        Sort.Direction direction = (parts.length > 1 && "asc".equalsIgnoreCase(parts[1]))
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;
        return Sort.by(direction, property);
    }

    private BatchJobExecutionDTO toDto(BatchJobExecution execution) {
        return BatchJobExecutionDTO.builder()
            .id(execution.getId())
            .jobName(execution.getJobName())
            .jobType(execution.getJobType())
            .status(execution.getStatus())
            .tenantId(execution.getTenantId())
            .startedAt(execution.getStartedAt())
            .completedAt(execution.getCompletedAt())
            .durationMs(execution.getDurationMs())
            .processedCount(execution.getProcessedCount())
            .successCount(execution.getSuccessCount())
            .failedCount(execution.getFailedCount())
            .errorMessage(execution.getErrorMessage())
            .triggeredBy(execution.getTriggeredBy())
            .parametersJson(execution.getParametersJson())
            .build();
    }
}
