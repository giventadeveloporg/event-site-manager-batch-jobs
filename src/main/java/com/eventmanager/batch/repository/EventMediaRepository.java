package com.eventmanager.batch.repository;

import com.eventmanager.batch.domain.EventMedia;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;

/**
 * Event media repository.
 * Official-document queries are intentionally paged — never use findAll() for official docs
 * (large hierarchy_path TEXT / URL rows can OOM the JVM / Postgres result buffer).
 */
@Repository
public interface EventMediaRepository extends JpaRepository<EventMedia, Long> {

    /**
     * Tenant official documents, newest first. Always call with a small page size (e.g. 25).
     */
    @Query(
        "SELECT e FROM EventMedia e WHERE e.tenantId = :tenantId " +
        "AND e.isEventManagementOfficialDocument = true " +
        "ORDER BY COALESCE(e.displayPriority, e.priorityRanking, 999999) ASC, e.createdAt DESC"
    )
    Page<EventMedia> findOfficialDocumentsByTenantId(
        @Param("tenantId") String tenantId,
        Pageable pageable
    );

    /**
     * Official documents missing a stable thumbnail URL (candidates for thumbnail generation).
     */
    @Query(
        "SELECT e FROM EventMedia e WHERE e.tenantId = :tenantId " +
        "AND e.isEventManagementOfficialDocument = true " +
        "AND (e.thumbnailUrl IS NULL OR TRIM(e.thumbnailUrl) = '') " +
        "ORDER BY e.createdAt DESC"
    )
    Page<EventMedia> findOfficialDocumentsMissingThumbnail(
        @Param("tenantId") String tenantId,
        Pageable pageable
    );

    /**
     * Official documents whose file or thumbnail presigned URL expires at or before the cutoff.
     */
    @Query(
        "SELECT e FROM EventMedia e WHERE e.tenantId = :tenantId " +
        "AND e.isEventManagementOfficialDocument = true " +
        "AND (" +
        "  (e.preSignedUrlExpiresAt IS NOT NULL AND e.preSignedUrlExpiresAt <= :expiresBefore) " +
        "  OR (e.thumbnailPreSignedUrlExpiresAt IS NOT NULL AND e.thumbnailPreSignedUrlExpiresAt <= :expiresBefore)" +
        ") " +
        "ORDER BY e.updatedAt ASC"
    )
    Page<EventMedia> findOfficialDocumentsWithExpiringPresignedUrls(
        @Param("tenantId") String tenantId,
        @Param("expiresBefore") ZonedDateTime expiresBefore,
        Pageable pageable
    );

    /**
     * Public official documents for a tenant (downloads-library style filter).
     */
    @Query(
        "SELECT e FROM EventMedia e WHERE e.tenantId = :tenantId " +
        "AND e.isEventManagementOfficialDocument = true " +
        "AND e.isPublic = true " +
        "ORDER BY COALESCE(e.displayPriority, e.priorityRanking, 999999) ASC, e.createdAt DESC"
    )
    Page<EventMedia> findPublicOfficialDocumentsByTenantId(
        @Param("tenantId") String tenantId,
        Pageable pageable
    );

    long countByTenantIdAndIsEventManagementOfficialDocumentTrue(String tenantId);
}
