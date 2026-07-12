package com.eventmanager.batch.officialdocument;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Planned job: generate / attach card thumbnails for official documents missing {@code thumbnail_url}.
 *
 * <p>Currently scaffolded only — mutations are not implemented.
 * Always uses {@link OfficialDocumentPagedReader} (page size 25 by default).
 * Enable with {@code batch.official-documents.thumbnail.enabled=true} when implementation is ready.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OfficialDocumentThumbnailBatchService {

    private final OfficialDocumentPagedReader pagedReader;

    @Value("${batch.official-documents.thumbnail.enabled:false}")
    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Dry-run / scaffold entry point. Counts candidates page-by-page; does not write thumbnails yet.
     */
    public OfficialDocumentBatchJobResult run(String tenantId, boolean dryRun, Integer pageSizeOverride) {
        if (!enabled && !dryRun) {
            return OfficialDocumentBatchJobResult.builder()
                .jobName(OfficialDocumentBatchConstants.JOB_THUMBNAIL)
                .tenantId(tenantId)
                .executed(false)
                .dryRun(false)
                .status("DISABLED")
                .message("Set batch.official-documents.thumbnail.enabled=true after thumbnail generation is implemented.")
                .build();
        }

        long[] scanned = {0};
        int pages = pagedReader.forEachMissingThumbnailPage(tenantId, pageSizeOverride, (content, pageIndex, total) -> {
            scanned[0] += content.size();
            log.info(
                "[official-documents][thumbnail] page={} size={} totalCandidates≈{}",
                pageIndex,
                content.size(),
                total
            );
            // Future: for each EventMedia without thumbnail, render PDF first page / use placeholder, upload to S3, PATCH thumbnail_url.
            return true;
        });

        return OfficialDocumentBatchJobResult.builder()
            .jobName(OfficialDocumentBatchConstants.JOB_THUMBNAIL)
            .tenantId(tenantId)
            .executed(true)
            .dryRun(true)
            .status("SCAFFOLD_DRY_RUN")
            .message(
                "Thumbnail generation not implemented yet. Scanned missing-thumbnail candidates with paged reader only."
            )
            .scanned(scanned[0])
            .processed(0)
            .skipped(scanned[0])
            .pagesVisited(pages)
            .build();
    }
}
