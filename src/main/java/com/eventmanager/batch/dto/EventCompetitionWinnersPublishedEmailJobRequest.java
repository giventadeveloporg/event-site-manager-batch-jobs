package com.eventmanager.batch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EventCompetitionWinnersPublishedEmailJobRequest {

  @NotNull
  private Long eventId;

  @NotBlank
  @Size(max = 255)
  private String tenantId;

  private Long templateId;
}
