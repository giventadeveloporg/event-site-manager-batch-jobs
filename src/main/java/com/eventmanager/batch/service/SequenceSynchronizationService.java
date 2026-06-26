package com.eventmanager.batch.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Synchronizes per-table PostgreSQL id sequences ({table}_id_seq) with MAX(id) after manual imports.
 * Spring Batch framework sequences are handled separately (sync_spring_batch_sequences.sql).
 */
@Service
@Slf4j
public class SequenceSynchronizationService {

    /** Spring Batch framework + app audit log — not application {table}_id_seq tables. */
    private static final List<String> EXCLUDED_SEQUENCES = List.of(
        "batch_job_seq",
        "batch_job_execution_seq",
        "batch_step_execution_seq",
        "batch_job_execution_log_id_seq"
    );

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Sync all application per-table id sequences discovered in pg_sequences.
     *
     * @return map of sequence name to new last_value
     */
    @Transactional
    public Map<String, Long> synchronizeAllTableSequences() {
        log.info("Synchronizing per-table id sequences...");
        Map<String, Long> results = new LinkedHashMap<>();

        @SuppressWarnings("unchecked")
        List<String> sequences = entityManager
            .createNativeQuery(
                """
                SELECT sequencename
                FROM pg_sequences
                WHERE schemaname = 'public'
                  AND sequencename LIKE '%\\_id_seq' ESCAPE '\\'
                ORDER BY sequencename
                """
            )
            .getResultList();

        for (String seqName : sequences) {
            if (EXCLUDED_SEQUENCES.contains(seqName)) {
                continue;
            }
            String tableName = resolveTableName(seqName);
            if (tableName == null) {
                log.debug("Skipping sequence {} — no matching public table", seqName);
                continue;
            }
            Long newValue = synchronizeTableSequence(tableName, seqName);
            if (newValue != null) {
                results.put(seqName, newValue);
            }
        }

        log.info("Synchronized {} per-table sequences", results.size());
        return results;
    }

    /**
     * @deprecated Use {@link #synchronizeAllTableSequences()}. Kept for callers that expect a single Long.
     */
    @Transactional
    public Long synchronizeSequence() {
        Map<String, Long> results = synchronizeAllTableSequences();
        return results.isEmpty() ? null : results.values().stream().max(Long::compareTo).orElse(null);
    }

    /**
     * Sync a single table's sequence (used after duplicate-key recovery).
     */
    @Transactional
    public Long synchronizeTableSequence(String tableName) {
        return synchronizeTableSequence(tableName, tableName + "_id_seq");
    }

    @Transactional
    public Long synchronizeTableSequence(String tableName, String sequenceName) {
        String qualifiedSeq = "public." + sequenceName;

        if (!tableExists(tableName)) {
            log.warn("Table public.{} not found — skipping sequence sync", tableName);
            return null;
        }

        if (!sequenceExists(sequenceName)) {
            log.warn("Sequence {} not found — skipping", qualifiedSeq);
            return null;
        }

        String sql = String.format(
            "SELECT pg_catalog.setval('%s', GREATEST(COALESCE((SELECT MAX(id) FROM public.%s), 0), 1), true)",
            qualifiedSeq.replace("'", "''"),
            tableName.replace("\"", "\"\"")
        );

        try {
            Object result = entityManager.createNativeQuery(sql).getSingleResult();
            Long newValue = result != null ? ((Number) result).longValue() : null;
            log.info("Synced {} -> {} (table {})", qualifiedSeq, newValue, tableName);
            return newValue;
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("permission denied")) {
                log.warn("Permission denied syncing sequence {} — skipping", qualifiedSeq);
                return null;
            }
            log.error("Failed to sync sequence {} for table {}", qualifiedSeq, tableName, e);
            throw new RuntimeException("Failed to sync sequence " + qualifiedSeq + ": " + e.getMessage(), e);
        }
    }

    /**
     * Synchronizes batch_job_execution_log_id_seq (BIGSERIAL / IDENTITY audit table).
     * Separate from Spring Batch framework sequences and application per-table id_seq sync.
     */
    @Transactional
    public Long synchronizeBatchJobExecutionLogSequence() {
        log.debug("Synchronizing batch_job_execution_log_id_seq sequence...");

        try {
            if (!sequenceExists("batch_job_execution_log_id_seq")) {
                log.warn("Sequence batch_job_execution_log_id_seq does not exist — skipping");
                return null;
            }

            Query getMaxIdQuery = entityManager.createNativeQuery(
                "SELECT COALESCE(MAX(id), 0) FROM public.batch_job_execution_log"
            );
            Object maxIdResult = getMaxIdQuery.getSingleResult();
            Long maxId = maxIdResult != null ? ((Number) maxIdResult).longValue() : 0L;
            long nextSequenceValue = Math.max(maxId + 1, 1L);

            String syncSql = String.format(
                "SELECT setval('public.batch_job_execution_log_id_seq', %d, true)",
                nextSequenceValue
            );

            Object result = entityManager.createNativeQuery(syncSql).getSingleResult();
            Long newSequenceValue = result != null ? ((Number) result).longValue() : nextSequenceValue;

            log.info(
                "batch_job_execution_log_id_seq synchronized. Max ID: {}, New sequence value: {}",
                maxId,
                newSequenceValue
            );
            return newSequenceValue;
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("permission denied")) {
                log.warn("Permission denied for batch_job_execution_log_id_seq synchronization — skipping");
                return null;
            }
            log.error("Failed to synchronize batch_job_execution_log_id_seq", e);
            return null;
        }
    }

    private String resolveTableName(String sequenceName) {
        if (!sequenceName.endsWith("_id_seq")) {
            return null;
        }
        String base = sequenceName.substring(0, sequenceName.length() - "_id_seq".length());
        if (tableExists(base)) {
            return base;
        }
        return null;
    }

    private boolean tableExists(String tableName) {
        Number count = (Number) entityManager
            .createNativeQuery(
                """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = :tableName
                """
            )
            .setParameter("tableName", tableName)
            .getSingleResult();
        return count != null && count.longValue() > 0;
    }

    private boolean sequenceExists(String sequenceName) {
        Number count = (Number) entityManager
            .createNativeQuery(
                """
                SELECT COUNT(*) FROM pg_sequences
                WHERE schemaname = 'public' AND sequencename = :seqName
                """
            )
            .setParameter("seqName", sequenceName)
            .getSingleResult();
        return count != null && count.longValue() > 0;
    }
}
