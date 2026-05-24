package com.eventmanager.batch.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConfiguredBatchJobDTO {
    private String jobName;
    private String jobType;
    private Boolean enabled;
    private String scheduleCron;
    private String description;
}
