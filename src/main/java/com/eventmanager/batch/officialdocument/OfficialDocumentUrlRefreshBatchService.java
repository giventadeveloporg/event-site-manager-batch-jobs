package com.eventmanager.batch.officialdocument;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;

/**
 * Planned job: refresh expired / soon-to-expire file and thumbnail presigned URLs.
 *
 * <p>Scaffolded only — prefer calling the main backend download-url / thumbnail endpoints
 * rather than duplicating S3 signing here. Enable when wired to BackendApiService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OfficialDocumentUrlRefreshBatchService {

    private final OfficialDocumentPagedReader pagedReader;

    @Value("${batch.official-documents.url-refresh.enabled:false}")
    private boolean enabled;

    @Value("${batch.official-documents.url-refresh.expires-within-hours:24}")
    private long expiresWithinHours;

    public boolean isEnabled() {
        return enabled;
    }

    public OfficialDocumentBatchJobResult run(String tenantId, boolean dryRun, Integer pageSizeOverride) {
        if (!enabled && !dryRun) {
            return OfficialDocumentBatchJobResult.builder()
                .jobName(OfficialDocumentBatchConstants.JOB_URL_REFRESH)
                .tenantId(tenantId)
                .executed(false)
                .dryRun(false)
                .status("DISABLED")
                .message("Set batch.official-documents.url-refresh.enabled=true after BackendApiService URL refresh is wired.")
                .build();
        }

        ZonedDateTime cutoff = ZonedDateTime.now().plusHours(Math.max(1, expiresWithinHours));
        long[] scanned = {0};
        int pages = pagedReader.forEachExpiringPresignedUrlPage(
            tenantId,
            cutoff,
            pageSizeOverride,
            (content, pageIndex, total) -> {
                scanned[0] += content.size();
                log.info(
                    "[official-documents][url-refresh] page={} size={} totalExpiring≈{} cutoff={}",
                    pageIndex,
                    content.size(),
                    total,
                    cutoff
                );
                // Future: call backend GET /api/event-medias/{id}/download-url and thumbnail refresh; PATCH expiry fields.
                return true;
            }
        );

        return OfficialDocumentBatchJobResult.builder()
            .jobName(OfficialDocumentBatchConstants.JOB_URL_REFRESH)
            .tenantId(tenantId)
            .executed(true)
            .dryRun(true)
            .status("SCAFFOLD_DRY_RUN")
            .message(
                "Presigned URL refresh not implemented yet. Scanned expiring candidates with paged reader only."
            )
            .scanned(scanned[0])
            .processed(0)
            .skipped(scanned[0])
            .pagesVisited(pages)
            .build();
    }
}
