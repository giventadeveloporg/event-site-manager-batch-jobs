package com.eventmanager.batch.controller;

import com.eventmanager.batch.dto.BatchJobExecutionDTO;
import com.eventmanager.batch.dto.BatchJobExecutionPageResponse;
import com.eventmanager.batch.dto.BatchJobSummaryDTO;
import com.eventmanager.batch.dto.ConfiguredBatchJobDTO;
import com.eventmanager.batch.service.BatchJobMonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/batch-jobs")
@RequiredArgsConstructor
public class BatchJobMonitoringController {

    private final BatchJobMonitoringService batchJobMonitoringService;

    @GetMapping("/executions")
    public ResponseEntity<BatchJobExecutionPageResponse> getExecutions(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String jobName,
        @RequestParam(required = false) String tenantId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime startedAfter,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime startedBefore,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String sort
    ) {
        return ResponseEntity.ok(
            batchJobMonitoringService.getExecutions(status, jobName, tenantId, startedAfter, startedBefore, page, size, sort)
        );
    }

    @GetMapping("/executions/{id}")
    public ResponseEntity<BatchJobExecutionDTO> getExecution(@PathVariable Long id) {
        return ResponseEntity.ok(batchJobMonitoringService.getExecution(id));
    }

    @GetMapping("/executions/failed")
    public ResponseEntity<List<BatchJobExecutionDTO>> getFailedExecutions(
        @RequestParam(defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(batchJobMonitoringService.getFailedExecutions(limit));
    }

    @GetMapping("/executions/running")
    public ResponseEntity<List<BatchJobExecutionDTO>> getRunningExecutions(
        @RequestParam(defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(batchJobMonitoringService.getRunningExecutions(limit));
    }

    @GetMapping("/summary")
    public ResponseEntity<BatchJobSummaryDTO> getSummary(
        @RequestParam(required = false) String tenantId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime startedAfter,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime startedBefore
    ) {
        return ResponseEntity.ok(batchJobMonitoringService.getSummary(tenantId, startedAfter, startedBefore));
    }

    @GetMapping("/configured-jobs")
    public ResponseEntity<List<ConfiguredBatchJobDTO>> getConfiguredJobs() {
        return ResponseEntity.ok(batchJobMonitoringService.getConfiguredJobs());
    }
}
