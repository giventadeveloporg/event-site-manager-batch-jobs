package com.eventmanager.batch.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * Gallery album entity for collections of media not tied to a single event.
 * Mirrors the backend {@code gallery_album} table for batch job processing.
 */
@Entity
@Table(name = "gallery_album")
@Data
public class GalleryAlbum implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator", allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id", length = 255, nullable = false)
    private String tenantId;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "description", length = 2048)
    private String description;

    @Column(name = "cover_image_url", length = 2048)
    private String coverImageUrl;

    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = true;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "album_year")
    private Integer albumYear;

    @Column(name = "event_date_start")
    private LocalDate eventDateStart;

    @Column(name = "event_date_end")
    private LocalDate eventDateEnd;

    @Column(name = "event_location", length = 256)
    private String eventLocation;

    @Column(name = "gallery_category_id")
    private Long galleryCategoryId;

    @Column(name = "created_by_id")
    private Long createdById;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;
}
