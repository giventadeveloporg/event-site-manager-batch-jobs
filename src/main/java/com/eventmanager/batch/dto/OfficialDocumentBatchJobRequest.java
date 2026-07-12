package com.eventmanager.batch.dto;

import lombok.Data;

/**
 * Request for official-document scaffold / dry-run batch endpoints.
 */
@Data
public class OfficialDocumentBatchJobRequest {
    /** Required tenant scope. */
    private String tenantId;

    /**
     * Optional page size override (clamped to max 50; default from
     * {@code batch.official-documents.page-size}, typically 25).
     */
    private Integer pageSize;

    /**
     * Optional S3 prefix for bulk-import dry inventory (ignored by other jobs).
     */
    private String s3Prefix;
}
