package com.eventmanager.batch.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EventCompetitionRegistrationSummaryJobRequest {

  @NotNull
  private Long eventId;

  @NotBlank
  @Size(max = 255)
  private String tenantId;

  @NotBlank
  @Email
  @Size(max = 255)
  private String recipientEmail;
}
