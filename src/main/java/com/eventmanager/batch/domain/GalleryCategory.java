package com.eventmanager.batch.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * Tenant-scoped gallery album category (e.g. Ecumenical Visits).
 * Mirrors the backend {@code gallery_category} table for batch job processing.
 */
@Entity
@Table(name = "gallery_category")
@Data
public class GalleryCategory implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id", length = 255, nullable = false)
    private String tenantId;

    @Column(name = "slug", length = 64, nullable = false)
    private String slug;

    @Column(name = "display_name", length = 128, nullable = false)
    private String displayName;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;
}
