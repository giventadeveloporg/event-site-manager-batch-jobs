package com.eventmanager.batch.officialdocument;

import lombok.Builder;
import lombok.Data;

/**
 * Read-only preview of official-document workload for a tenant.
 * Safe to expose via API — does not mutate data.
 */
@Data
@Builder
public class OfficialDocumentPreviewStats {
    private String tenantId;
    private int pageSize;
    private long totalOfficialDocuments;
    private int estimatedPages;
    private long missingThumbnailCount;
    private long presignedUrlExpiringWithin24hCount;
}
