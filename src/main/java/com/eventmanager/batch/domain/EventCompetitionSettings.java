package com.eventmanager.batch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.io.Serializable;
import java.time.ZonedDateTime;

@Entity
@Table(name = "event_competition_settings")
@Data
public class EventCompetitionSettings implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "audience_mode", nullable = false, length = 20)
    private String audienceMode;

    @Column(name = "registration_mode", nullable = false, length = 32)
    private String registrationMode;

    @Column(name = "registration_deadline")
    private ZonedDateTime registrationDeadline;

    @Column(name = "registration_open", nullable = false)
    private Boolean registrationOpen = true;

    @Column(name = "allow_ticket_sales", nullable = false)
    private Boolean allowTicketSales = false;

    @Column(name = "points_first", nullable = false)
    private Integer pointsFirst = 5;

    @Column(name = "points_second", nullable = false)
    private Integer pointsSecond = 3;

    @Column(name = "points_third", nullable = false)
    private Integer pointsThird = 1;

    @Column(name = "champion_enabled", nullable = false)
    private Boolean championEnabled = false;

    @Column(name = "champion_exclude_group_points", nullable = false)
    private Boolean championExcludeGroupPoints = true;

    @Column(name = "champion_max_category")
    private Integer championMaxCategory;

    @Column(name = "results_display_mode", length = 32)
    private String resultsDisplayMode;

    @Column(name = "eligibility_text", columnDefinition = "text")
    private String eligibilityText;

    @Column(name = "winners_published_email_sent_at")
    private ZonedDateTime winnersPublishedEmailSentAt;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;
}
