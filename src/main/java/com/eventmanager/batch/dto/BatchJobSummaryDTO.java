package com.eventmanager.batch.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BatchJobSummaryDTO {
    private long runningCount;
    private long completedCount;
    private long failedCount;
    private long totalCount;
}
