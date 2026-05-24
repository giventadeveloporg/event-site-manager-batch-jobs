package com.eventmanager.batch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.io.Serializable;
import java.time.ZonedDateTime;

@Entity
@Table(name = "event_competition_result")
@Data
public class EventCompetitionResult implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "competition_id", nullable = false)
    private Long competitionId;

    @Column(name = "participant_profile_id")
    private Long participantProfileId;

    @Column(name = "registration_id")
    private Long registrationId;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "placement")
    private Integer placement;

    @Column(name = "placement_label", length = 50)
    private String placementLabel;

    @Column(name = "prize_title", length = 255)
    private String prizeTitle;

    @Column(name = "prize_details", columnDefinition = "text")
    private String prizeDetails;

    @Column(name = "points_awarded", nullable = false)
    private Integer pointsAwarded = 0;

    @Column(name = "winner_photo_url", length = 1024)
    private String winnerPhotoUrl;

    @Column(name = "winner_media_id")
    private Long winnerMediaId;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "is_published", nullable = false)
    private Boolean isPublished = false;

    @Column(name = "published_at")
    private ZonedDateTime publishedAt;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;
}
