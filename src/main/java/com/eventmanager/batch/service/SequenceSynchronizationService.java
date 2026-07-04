package com.eventmanager.batch.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    /**
     * Sequences excluded from bulk application sync — Spring Batch framework and audit log (synced separately).
     */
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
            if (shouldSkipSequenceSync(seqName, null)) {
                log.debug("Skipping sequence {} — excluded from application sync", seqName);
                continue;
            }
            String tableName = resolveTableName(seqName);
            if (tableName == null) {
                log.debug("Skipping sequence {} — no matching public table", seqName);
                continue;
            }
            if (shouldSkipSequenceSync(seqName, tableName)) {
                log.debug("Skipping sequence {} / table {} — not a user id table", seqName, tableName);
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
        if (shouldSkipSequenceSync(sequenceName, tableName)) {
            log.debug("Skipping sequence sync for {} / {} — excluded from application sync", sequenceName, tableName);
            return null;
        }

        String qualifiedSeq = "public." + sequenceName;

        if (!tableExists(tableName)) {
            log.warn("Table public.{} not found — skipping sequence sync", tableName);
            return null;
        }

        if (!sequenceExists(sequenceName)) {
            log.warn("Sequence {} not found — skipping", qualifiedSeq);
            return null;
        }

        if (!hasIdColumn(tableName)) {
            log.warn("Table public.{} has no id column — skipping sequence {}", tableName, qualifiedSeq);
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
            if (isBenignSyncFailure(e)) {
                log.warn("Skipping sequence {} for table {} — {}", qualifiedSeq, tableName, e.getMessage());
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
            long sequenceValue = Math.max(maxId, 1L);

            String syncSql = String.format(
                "SELECT setval('public.batch_job_execution_log_id_seq', %d, true)",
                sequenceValue
            );

            Object result = entityManager.createNativeQuery(syncSql).getSingleResult();
            Long newSequenceValue = result != null ? ((Number) result).longValue() : sequenceValue;

            log.info(
                "batch_job_execution_log_id_seq synchronized. Max ID: {}, sequence last_value: {}",
                maxId,
                newSequenceValue
            );
            return newSequenceValue;
        } catch (Exception e) {
            if (isBenignSyncFailure(e)) {
                log.warn("Skipping batch_job_execution_log_id_seq synchronization — {}", e.getMessage());
                return null;
            }
            log.error("Failed to synchronize batch_job_execution_log_id_seq", e);
            return null;
        }
    }

    /**
     * Returns true when a sequence/table must not participate in application MAX(id) sync.
     * Spring Batch / Spring Integration framework objects and JHipster join tables are excluded.
     */
    boolean shouldSkipSequenceSync(String sequenceName, String tableName) {
        if (sequenceName == null) {
            return true;
        }
        String seq = sequenceName.toLowerCase(Locale.ROOT);
        if (EXCLUDED_SEQUENCES.contains(seq)) {
            return true;
        }
        if (seq.startsWith("rel_")) {
            return true;
        }
        if (seq.startsWith("batch_")) {
            return true;
        }
        if (seq.startsWith("int_") || seq.contains("integration")) {
            return true;
        }
        if (seq.startsWith("databasechangelog")) {
            return true;
        }
        if (tableName != null) {
            String tbl = tableName.toLowerCase(Locale.ROOT);
            if (tbl.startsWith("rel_")) {
                return true;
            }
            if (tbl.startsWith("batch_") && !tbl.equals("batch_job_execution_log")) {
                return true;
            }
        }
        return false;
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

    private boolean hasIdColumn(String tableName) {
        Number count = (Number) entityManager
            .createNativeQuery(
                """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = :tableName AND column_name = 'id'
                """
            )
            .setParameter("tableName", tableName)
            .getSingleResult();
        return count != null && count.longValue() > 0;
    }

    private boolean isBenignSyncFailure(Exception e) {
        String message = buildMessageChain(e);
        return message.contains("column \"id\" does not exist")
            || message.contains("permission denied")
            || message.contains("does not exist");
    }

    private String buildMessageChain(Exception e) {
        StringBuilder sb = new StringBuilder();
        Throwable t = e;
        while (t != null) {
            if (t.getMessage() != null) {
                sb.append(t.getMessage()).append(' ');
            }
            t = t.getCause();
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
