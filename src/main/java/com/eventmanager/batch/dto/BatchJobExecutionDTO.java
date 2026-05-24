package com.eventmanager.batch.dto;

import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;

@Data
@Builder
public class BatchJobExecutionDTO {
    private Long id;
    private String jobName;
    private String jobType;
    private String status;
    private String tenantId;
    private ZonedDateTime startedAt;
    private ZonedDateTime completedAt;
    private Long durationMs;
    private Long processedCount;
    private Long successCount;
    private Long failedCount;
    private String errorMessage;
    private String triggeredBy;
    private String parametersJson;
}
