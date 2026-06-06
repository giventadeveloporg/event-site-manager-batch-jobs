package com.eventmanager.batch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.ZonedDateTime;
import lombok.Data;

@Entity
@Table(name = "event_competition_group_member")
@Data
public class EventCompetitionGroupMember implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  @Column(name = "id")
  private Long id;

  @Column(name = "tenant_id", nullable = false, length = 255)
  private String tenantId;

  @Column(name = "registration_id", nullable = false)
  private Long registrationId;

  @Column(name = "participant_profile_id", nullable = false)
  private Long participantProfileId;

  @Column(name = "member_role", length = 32)
  private String memberRole;

  @Column(name = "sort_order")
  private Integer sortOrder;

  @Column(name = "created_at", nullable = false)
  private ZonedDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private ZonedDateTime updatedAt;
}
