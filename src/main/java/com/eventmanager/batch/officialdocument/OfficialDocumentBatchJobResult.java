package com.eventmanager.batch.officialdocument;

import lombok.Builder;
import lombok.Data;

/**
 * Result envelope for planned official-document batch jobs.
 * Processing mutations are gated by {@code batch.official-documents.*.enabled}.
 */
@Data
@Builder
public class OfficialDocumentBatchJobResult {
    private String jobName;
    private String tenantId;
    private boolean executed;
    private boolean dryRun;
    private String status;
    private String message;
    private long scanned;
    private long processed;
    private long skipped;
    private int pagesVisited;
}
