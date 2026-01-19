package com.eventmanager.batch.service;

import com.eventmanager.batch.domain.BatchJobExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Nightly aggregation job for fee-free manual payments.
 * Inserts daily snapshots into manual_payment_summary_report.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ManualPaymentSummaryJobService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final BatchJobExecutionService batchJobExecutionService;

    @Transactional
    public ManualPaymentSummaryStats runManualPaymentSummaryJob(
        String tenantId,
        Long eventId,
        LocalDate snapshotDate,
        String triggeredBy
    ) {
        LocalDate snapshot = snapshotDate != null ? snapshotDate : LocalDate.now(ZoneId.systemDefault());
        String resolvedTriggeredBy = triggeredBy != null ? triggeredBy : "SCHEDULED";

        BatchJobExecution execution = batchJobExecutionService.createJobExecution(
            "manualPaymentSummaryJob",
            "MANUAL_PAYMENT_SUMMARY",
            tenantId,
            resolvedTriggeredBy,
            String.format("{\"tenantId\":\"%s\",\"eventId\":%s,\"snapshotDate\":\"%s\"}",
                tenantId != null ? tenantId : "ALL",
                eventId != null ? eventId.toString() : "null",
                snapshot)
        );

        ManualPaymentSummaryStats stats = new ManualPaymentSummaryStats();
        stats.jobExecutionId = execution.getId();
        stats.snapshotDate = snapshot;
        stats.tenantId = tenantId;
        stats.eventId = eventId;

        try {
            MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("snapshotDate", snapshot)
                .addValue("tenantId", tenantId)
                .addValue("eventId", eventId);

            // Diagnostic queries to identify why no records are selected
            logDiagnosticInfo(params);

            int deleted = jdbcTemplate.update(
                "DELETE FROM manual_payment_summary_report " +
                    "WHERE snapshot_date = :snapshotDate " +
                    "AND (:tenantId IS NULL OR tenant_id = :tenantId) " +
                    "AND (:eventId IS NULL OR event_id = :eventId)",
                params
            );

            int inserted = jdbcTemplate.update(
                "INSERT INTO manual_payment_summary_report " +
                    "(tenant_id, event_id, payment_method_type, status, total_amount, transaction_count, snapshot_date, created_at) " +
                    "SELECT r.tenant_id, r.event_id, r.payment_method_type, r.status, " +
                    "       COALESCE(SUM(r.amount_due), 0), COUNT(*), :snapshotDate, now() " +
                    "FROM manual_payment_request r " +
                    "JOIN event_details e ON e.id = r.event_id " +
                    "WHERE r.event_id IS NOT NULL " +
                    "  AND e.manual_payment_enabled = true " +
                    "  AND (:tenantId IS NULL OR r.tenant_id = :tenantId) " +
                    "  AND (:eventId IS NULL OR r.event_id = :eventId) " +
                    "GROUP BY r.tenant_id, r.event_id, r.payment_method_type, r.status",
                params
            );

            // Log diagnostic info if no records were inserted
            if (inserted == 0) {
                log.warn("No records inserted. Running diagnostic queries to identify the issue...");
                logDiagnosticInfo(params);
            }

            stats.deletedRows = deleted;
            stats.insertedRows = inserted;

            batchJobExecutionService.completeJobExecution(
                execution.getId(),
                "COMPLETED",
                (long) inserted,
                (long) inserted,
                0L,
                null
            );

            log.info("Manual payment summary job completed. snapshotDate={}, tenantId={}, eventId={}, deletedRows={}, insertedRows={}",
                snapshot, tenantId, eventId, deleted, inserted);

            return stats;
        } catch (Exception e) {
            log.error("Manual payment summary job failed. snapshotDate={}, tenantId={}, eventId={}, error={}",
                snapshot, tenantId, eventId, e.getMessage(), e);

            batchJobExecutionService.completeJobExecution(
                execution.getId(),
                "FAILED",
                0L,
                0L,
                0L,
                e.getMessage()
            );

            throw e;
        }
    }

    /**
     * Log diagnostic information to help identify why no records are selected.
     * Checks:
     * 1. Total manual_payment_request records
     * 2. Records with event_id IS NOT NULL
     * 3. Records matching tenantId filter (if provided)
     * 4. Records matching eventId filter (if provided)
     * 5. Records where event has manual_payment_enabled = true
     * 6. Records matching all conditions
     */
    private void logDiagnosticInfo(MapSqlParameterSource params) {
        try {
            // Count total manual payment requests
            Long totalRequests = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM manual_payment_request",
                new MapSqlParameterSource(),
                Long.class
            );
            log.info("Diagnostic: Total manual_payment_request records: {}", totalRequests);

            // Count records with event_id IS NOT NULL
            Long withEventId = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM manual_payment_request WHERE event_id IS NOT NULL",
                new MapSqlParameterSource(),
                Long.class
            );
            log.info("Diagnostic: Records with event_id IS NOT NULL: {}", withEventId);

            // Count records matching tenantId filter (if provided)
            if (params.getValue("tenantId") != null) {
                Long matchingTenant = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM manual_payment_request WHERE tenant_id = :tenantId",
                    params,
                    Long.class
                );
                log.info("Diagnostic: Records matching tenantId '{}': {}", params.getValue("tenantId"), matchingTenant);
            }

            // Count records matching eventId filter (if provided)
            if (params.getValue("eventId") != null) {
                Long matchingEvent = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM manual_payment_request WHERE event_id = :eventId",
                    params,
                    Long.class
                );
                log.info("Diagnostic: Records matching eventId {}: {}", params.getValue("eventId"), matchingEvent);
            }

            // Count records where event has manual_payment_enabled = true
            Long withEnabledEvent = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM manual_payment_request r " +
                    "JOIN event_details e ON e.id = r.event_id " +
                    "WHERE r.event_id IS NOT NULL AND e.manual_payment_enabled = true",
                new MapSqlParameterSource(),
                Long.class
            );
            log.info("Diagnostic: Records with event_id IS NOT NULL AND event.manual_payment_enabled = true: {}", withEnabledEvent);

            // Count records matching all conditions (without GROUP BY)
            String countQuery = "SELECT COUNT(*) FROM manual_payment_request r " +
                "JOIN event_details e ON e.id = r.event_id " +
                "WHERE r.event_id IS NOT NULL " +
                "  AND e.manual_payment_enabled = true " +
                "  AND (:tenantId IS NULL OR r.tenant_id = :tenantId) " +
                "  AND (:eventId IS NULL OR r.event_id = :eventId)";
            
            Long matchingAllConditions = jdbcTemplate.queryForObject(
                countQuery,
                params,
                Long.class
            );
            log.info("Diagnostic: Records matching all conditions (before GROUP BY): {}", matchingAllConditions);

            // If matching all conditions but tenantId filter might be excluding records
            if (params.getValue("tenantId") != null && matchingAllConditions == 0) {
                // Check if there are records for other tenants
                Long otherTenants = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM manual_payment_request r " +
                        "JOIN event_details e ON e.id = r.event_id " +
                        "WHERE r.event_id IS NOT NULL " +
                        "  AND e.manual_payment_enabled = true " +
                        "  AND r.tenant_id != :tenantId",
                    params,
                    Long.class
                );
                log.info("Diagnostic: Records for other tenants (with manual_payment_enabled = true): {}", otherTenants);
            }

            // Check specific events that have manual payment requests but manual_payment_enabled might be false
            if (params.getValue("eventId") != null) {
                Boolean eventEnabled = jdbcTemplate.queryForObject(
                    "SELECT manual_payment_enabled FROM event_details WHERE id = :eventId",
                    params,
                    Boolean.class
                );
                log.info("Diagnostic: Event {} has manual_payment_enabled = {}", params.getValue("eventId"), eventEnabled);
            }

        } catch (Exception e) {
            log.warn("Failed to run diagnostic queries: {}", e.getMessage(), e);
        }
    }

    public static class ManualPaymentSummaryStats {
        public Long jobExecutionId;
        public String tenantId;
        public Long eventId;
        public LocalDate snapshotDate;
        public int deletedRows;
        public int insertedRows;
    }
}
