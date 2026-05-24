package com.eventmanager.batch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "event_competition")
@Data
public class EventCompetition implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "competition_day_id")
    private Long competitionDayId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "competition_type", nullable = false, length = 20)
    private String competitionType;

    @Column(name = "eligible_audience", nullable = false, length = 20)
    private String eligibleAudience;

    @Column(name = "category_code", length = 20)
    private String categoryCode;

    @Column(name = "division_label", length = 100)
    private String divisionLabel;

    @Column(name = "track", length = 50)
    private String track;

    @Column(name = "fee_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal feeAmount;

    @Column(name = "max_participants")
    private Integer maxParticipants;

    @Column(name = "min_group_size")
    private Integer minGroupSize;

    @Column(name = "max_group_size")
    private Integer maxGroupSize;

    @Column(name = "time_limit_minutes")
    private Integer timeLimitMinutes;

    @Column(name = "requires_soundtrack", nullable = false)
    private Boolean requiresSoundtrack = false;

    @Column(name = "judgment_criteria_json", columnDefinition = "text")
    private String judgmentCriteriaJson;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;
}
