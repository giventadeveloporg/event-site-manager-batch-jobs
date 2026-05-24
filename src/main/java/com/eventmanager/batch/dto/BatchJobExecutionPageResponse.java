package com.eventmanager.batch.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BatchJobExecutionPageResponse {
    private List<BatchJobExecutionDTO> content;
    private long totalElements;
    private int totalPages;
    private int page;
    private int size;
}
