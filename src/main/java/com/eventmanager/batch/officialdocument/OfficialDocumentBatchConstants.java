package com.eventmanager.batch.officialdocument;

/**
 * Shared constants for official-document batch work.
 * Page size matches the frontend downloads / admin hardening pattern (size=25)
 * that avoids OOM from loading large event_media pages.
 */
public final class OfficialDocumentBatchConstants {

    /**
     * Default page size for all official-document batch readers.
     * Do not raise without verifying heap + Postgres result buffer headroom.
     */
    public static final int DEFAULT_PAGE_SIZE = 25;

    /** Hard ceiling so callers cannot accidentally request huge pages. */
    public static final int MAX_PAGE_SIZE = 50;

    public static final String JOB_THUMBNAIL = "OFFICIAL_DOCUMENT_THUMBNAIL";
    public static final String JOB_URL_REFRESH = "OFFICIAL_DOCUMENT_URL_REFRESH";
    public static final String JOB_BULK_IMPORT = "OFFICIAL_DOCUMENT_BULK_IMPORT";

    private OfficialDocumentBatchConstants() {
    }

    public static int clampPageSize(int requested) {
        if (requested <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(requested, MAX_PAGE_SIZE);
    }
}
