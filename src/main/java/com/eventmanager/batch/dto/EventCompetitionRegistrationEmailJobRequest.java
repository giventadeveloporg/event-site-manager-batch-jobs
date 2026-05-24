package com.eventmanager.batch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class EventCompetitionRegistrationEmailJobRequest {

  @NotNull
  private Long eventId;

  @NotEmpty
  private List<Long> registrationIds;

  @NotBlank
  @Size(max = 255)
  private String tenantId;

  private Long templateId;
}
