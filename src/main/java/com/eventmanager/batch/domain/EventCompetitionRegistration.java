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
@Table(name = "event_competition_registration")
@Data
public class EventCompetitionRegistration implements Serializable {

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

    @Column(name = "participant_profile_id", nullable = false)
    private Long participantProfileId;

    @Column(name = "registration_status", nullable = false, length = 32)
    private String registrationStatus;

    @Column(name = "fee_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal feeAmount;

    @Column(name = "effective_category", length = 20)
    private String effectiveCategory;

    @Column(name = "stripe_payment_intent_id", length = 255)
    private String stripePaymentIntentId;

    @Column(name = "group_leader_registration_id")
    private Long groupLeaderRegistrationId;

    @Column(name = "team_name", length = 200)
    private String teamName;

    @Column(name = "team_display_name", length = 200)
    private String teamDisplayName;

    @Column(name = "registered_by_user_profile_id", nullable = false)
    private Long registeredByUserProfileId;

    @Column(name = "confirmation_email_sent", nullable = false)
    private Boolean confirmationEmailSent = false;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;
}
