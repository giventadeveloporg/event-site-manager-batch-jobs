package com.eventmanager.batch.officialdocument;

import com.eventmanager.batch.domain.EventMedia;

/**
 * Callback for each official-document page loaded by {@link OfficialDocumentPagedReader}.
 *
 * @return {@code true} to continue paging, {@code false} to stop early
 */
@FunctionalInterface
public interface OfficialDocumentPageConsumer {

    boolean accept(java.util.List<EventMedia> pageContent, int pageIndex, long totalElements);
}
