package com.eventmanager.batch.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EventCompetitionRegistrationEmailJobResponse {

  private Boolean success;
  private String message;
  private Long jobExecutionId;
  private Long processedCount;
  private Long successCount;
  private Long failedCount;
}
