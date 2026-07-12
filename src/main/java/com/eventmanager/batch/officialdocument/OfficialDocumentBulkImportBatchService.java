package com.eventmanager.batch.officialdocument;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Planned job: bulk-import official documents from an S3 prefix into {@code event_media}.
 *
 * <p>Expected S3 layout (matches main backend):
 * {@code media/{tenantId}/official_document/{categorySlug}/{year}/filename}
 *
 * <p>Scaffolded only — listing / insert logic is not implemented. Prefer calling the main
 * backend bulk-upload API when possible to keep validation + year-bundle creation centralized.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OfficialDocumentBulkImportBatchService {

    private final OfficialDocumentPagedReader pagedReader;

    @Value("${batch.official-documents.bulk-import.enabled:false}")
    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @param s3Prefix optional prefix under the tenant official_document root (may be null for dry inventory)
     */
    public OfficialDocumentBatchJobResult run(
        String tenantId,
        String s3Prefix,
        boolean dryRun,
        Integer pageSizeOverride
    ) {
        if (!enabled && !dryRun) {
            return OfficialDocumentBatchJobResult.builder()
                .jobName(OfficialDocumentBatchConstants.JOB_BULK_IMPORT)
                .tenantId(tenantId)
                .executed(false)
                .dryRun(false)
                .status("DISABLED")
                .message("Set batch.official-documents.bulk-import.enabled=true after S3 listing + backend upload wiring is ready.")
                .build();
        }

        // Safe inventory of existing DB rows so operators can compare against S3 later.
        long[] scanned = {0};
        int pages = pagedReader.forEachOfficialDocumentPage(tenantId, pageSizeOverride, (content, pageIndex, total) -> {
            scanned[0] += content.size();
            log.info(
                "[official-documents][bulk-import] existingDb page={} size={} total≈{} s3Prefix={}",
                pageIndex,
                content.size(),
                total,
                s3Prefix
            );
            return true;
        });

        return OfficialDocumentBatchJobResult.builder()
            .jobName(OfficialDocumentBatchConstants.JOB_BULK_IMPORT)
            .tenantId(tenantId)
            .executed(true)
            .dryRun(true)
            .status("SCAFFOLD_DRY_RUN")
            .message(
                "Bulk import not implemented yet. Inventories existing official documents page-by-page; " +
                "S3 listing / insert remains TODO. prefix=" + (s3Prefix == null ? "(none)" : s3Prefix)
            )
            .scanned(scanned[0])
            .processed(0)
            .skipped(scanned[0])
            .pagesVisited(pages)
            .build();
    }
}
