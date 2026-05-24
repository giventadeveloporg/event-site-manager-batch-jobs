package com.eventmanager.batch.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class EventCompetitionSummaryRow {

  private String competitionName;
  private Long confirmedCount;
  private Long pendingCount;
  private Long cancelledCount;
  private BigDecimal totalFees;
}
