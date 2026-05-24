package com.eventmanager.batch.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EventCompetitionWinnerHighlight {

  private String competitionName;
  private Integer placement;
  private String displayName;
  private String placementLabel;
  private String prizeTitle;
}
