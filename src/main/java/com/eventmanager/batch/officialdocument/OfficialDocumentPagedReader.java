package com.eventmanager.batch.officialdocument;

import com.eventmanager.batch.domain.EventMedia;
import com.eventmanager.batch.repository.EventMediaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Paged reader for official documents.
 * Always uses small batches (default 25) — never loads the full official-document set into memory.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OfficialDocumentPagedReader {

    private final EventMediaRepository eventMediaRepository;

    @Value("${batch.official-documents.page-size:" + OfficialDocumentBatchConstants.DEFAULT_PAGE_SIZE + "}")
    private int configuredPageSize;

    public int resolvePageSize(Integer override) {
        if (override != null) {
            return OfficialDocumentBatchConstants.clampPageSize(override);
        }
        return OfficialDocumentBatchConstants.clampPageSize(configuredPageSize);
    }

    /**
     * Walk all official documents for a tenant page-by-page.
     *
     * @return pages visited
     */
    @Transactional(readOnly = true)
    public int forEachOfficialDocumentPage(String tenantId, Integer pageSizeOverride, OfficialDocumentPageConsumer consumer) {
        return forEachPage(
            tenantId,
            pageSizeOverride,
            (tenant, pageable) -> eventMediaRepository.findOfficialDocumentsByTenantId(tenant, pageable),
            consumer
        );
    }

    @Transactional(readOnly = true)
    public int forEachMissingThumbnailPage(String tenantId, Integer pageSizeOverride, OfficialDocumentPageConsumer consumer) {
        return forEachPage(
            tenantId,
            pageSizeOverride,
            (tenant, pageable) -> eventMediaRepository.findOfficialDocumentsMissingThumbnail(tenant, pageable),
            consumer
        );
    }

    @Transactional(readOnly = true)
    public int forEachExpiringPresignedUrlPage(
        String tenantId,
        ZonedDateTime expiresBefore,
        Integer pageSizeOverride,
        OfficialDocumentPageConsumer consumer
    ) {
        Objects.requireNonNull(expiresBefore, "expiresBefore");
        return forEachPage(
            tenantId,
            pageSizeOverride,
            (tenant, pageable) ->
                eventMediaRepository.findOfficialDocumentsWithExpiringPresignedUrls(tenant, expiresBefore, pageable),
            consumer
        );
    }

    @Transactional(readOnly = true)
    public OfficialDocumentPreviewStats preview(String tenantId, Integer pageSizeOverride) {
        Objects.requireNonNull(tenantId, "tenantId");
        int pageSize = resolvePageSize(pageSizeOverride);
        long total = eventMediaRepository.countByTenantIdAndIsEventManagementOfficialDocumentTrue(tenantId);

        Pageable firstPage = PageRequest.of(0, pageSize);
        Page<EventMedia> missingThumb = eventMediaRepository.findOfficialDocumentsMissingThumbnail(tenantId, firstPage);
        ZonedDateTime soon = ZonedDateTime.now().plusHours(24);
        Page<EventMedia> expiring = eventMediaRepository.findOfficialDocumentsWithExpiringPresignedUrls(
            tenantId,
            soon,
            firstPage
        );

        int estimatedPages = total == 0 ? 0 : (int) Math.ceil((double) total / pageSize);

        return OfficialDocumentPreviewStats.builder()
            .tenantId(tenantId)
            .pageSize(pageSize)
            .totalOfficialDocuments(total)
            .estimatedPages(estimatedPages)
            .missingThumbnailCount(missingThumb.getTotalElements())
            .presignedUrlExpiringWithin24hCount(expiring.getTotalElements())
            .build();
    }

    private int forEachPage(
        String tenantId,
        Integer pageSizeOverride,
        BiFunction<String, Pageable, Page<EventMedia>> pageLoader,
        OfficialDocumentPageConsumer consumer
    ) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(consumer, "consumer");

        int pageSize = resolvePageSize(pageSizeOverride);
        int pageIndex = 0;
        int visited = 0;

        while (pageIndex < 10_000) {
            Pageable pageable = PageRequest.of(pageIndex, pageSize);
            Page<EventMedia> page = pageLoader.apply(tenantId, pageable);
            visited++;

            if (page.isEmpty()) {
                break;
            }

            boolean continuePaging = consumer.accept(page.getContent(), pageIndex, page.getTotalElements());
            if (!continuePaging || page.isLast()) {
                break;
            }
            pageIndex++;
        }

        log.debug(
            "[official-documents] paged reader finished tenantId={} pageSize={} pagesVisited={}",
            tenantId,
            pageSize,
            visited
        );
        return visited;
    }
}
