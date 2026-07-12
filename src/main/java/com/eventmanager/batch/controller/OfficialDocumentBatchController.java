package com.eventmanager.batch.controller;

import com.eventmanager.batch.dto.OfficialDocumentBatchJobRequest;
import com.eventmanager.batch.officialdocument.OfficialDocumentBatchJobResult;
import com.eventmanager.batch.officialdocument.OfficialDocumentBulkImportBatchService;
import com.eventmanager.batch.officialdocument.OfficialDocumentPagedReader;
import com.eventmanager.batch.officialdocument.OfficialDocumentPreviewStats;
import com.eventmanager.batch.officialdocument.OfficialDocumentThumbnailBatchService;
import com.eventmanager.batch.officialdocument.OfficialDocumentUrlRefreshBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Official-document batch foundation endpoints.
 * Preview and dry-run only — mutation jobs remain disabled until implemented.
 */
@RestController
@RequestMapping("/api/batch-jobs/official-documents")
@RequiredArgsConstructor
@Slf4j
public class OfficialDocumentBatchController {

    private final OfficialDocumentPagedReader pagedReader;
    private final OfficialDocumentThumbnailBatchService thumbnailBatchService;
    private final OfficialDocumentUrlRefreshBatchService urlRefreshBatchService;
    private final OfficialDocumentBulkImportBatchService bulkImportBatchService;

    /**
     * Read-only workload preview for a tenant (counts + page estimates).
     */
    @GetMapping("/preview")
    public ResponseEntity<?> preview(
        @RequestParam String tenantId,
        @RequestParam(required = false) Integer pageSize
    ) {
        if (tenantId == null || tenantId.isBlank()) {
            return ResponseEntity.badRequest().body("tenantId is required");
        }
        try {
            OfficialDocumentPreviewStats stats = pagedReader.preview(tenantId.trim(), pageSize);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("[official-documents] preview failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Preview failed: " + e.getMessage());
        }
    }

    @PostMapping("/thumbnail/dry-run")
    public ResponseEntity<?> thumbnailDryRun(@RequestBody OfficialDocumentBatchJobRequest request) {
        return runDryRun("thumbnail", request, () ->
            thumbnailBatchService.run(request.getTenantId().trim(), true, request.getPageSize())
        );
    }

    @PostMapping("/url-refresh/dry-run")
    public ResponseEntity<?> urlRefreshDryRun(@RequestBody OfficialDocumentBatchJobRequest request) {
        return runDryRun("url-refresh", request, () ->
            urlRefreshBatchService.run(request.getTenantId().trim(), true, request.getPageSize())
        );
    }

    @PostMapping("/bulk-import/dry-run")
    public ResponseEntity<?> bulkImportDryRun(@RequestBody OfficialDocumentBatchJobRequest request) {
        return runDryRun("bulk-import", request, () ->
            bulkImportBatchService.run(
                request.getTenantId().trim(),
                request.getS3Prefix(),
                true,
                request.getPageSize()
            )
        );
    }

    private ResponseEntity<?> runDryRun(
        String jobLabel,
        OfficialDocumentBatchJobRequest request,
        java.util.concurrent.Callable<OfficialDocumentBatchJobResult> action
    ) {
        if (request == null || request.getTenantId() == null || request.getTenantId().isBlank()) {
            return ResponseEntity.badRequest().body("tenantId is required");
        }
        try {
            log.info("[official-documents] dry-run {} for tenant {}", jobLabel, request.getTenantId());
            return ResponseEntity.ok(action.call());
        } catch (Exception e) {
            log.error("[official-documents] dry-run {} failed: {}", jobLabel, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Dry-run failed: " + e.getMessage());
        }
    }
}
