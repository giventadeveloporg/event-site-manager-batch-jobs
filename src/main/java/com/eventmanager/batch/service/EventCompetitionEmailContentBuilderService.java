package com.eventmanager.batch.service;

import com.eventmanager.batch.dto.EventCompetitionSummaryRow;
import com.eventmanager.batch.dto.EventCompetitionWinnerHighlight;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * HTML email builders for event competition batch jobs.
 * Kept separate from {@link EmailContentBuilderService} to avoid merge conflicts on that large file.
 */
@Service
@RequiredArgsConstructor
public class EventCompetitionEmailContentBuilderService {

  private final EmailContentBuilderService emailContentBuilderService;

  public String buildRegistrationConfirmationEmailBody(
    String participantName,
    String competitionName,
    BigDecimal feeAmount,
    String eventTitle,
    String tenantId
  ) {
    return buildRegistrationConfirmationEmailBody(participantName, competitionName, feeAmount, eventTitle, tenantId, null, null);
  }

  public String buildRegistrationConfirmationEmailBody(
    String participantName,
    String competitionName,
    BigDecimal feeAmount,
    String eventTitle,
    String tenantId,
    String teamName,
    List<String> rosterMemberNames
  ) {
    StringBuilder html = new StringBuilder();
    html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body>");
    appendHeader(html, tenantId);

    html.append("<div style='max-width: 600px; margin: 0 auto; padding: 20px; font-family: Arial, sans-serif; line-height: 1.6; color: #333;'>");
    html.append("<h1 style='color: #1f4c8f;'>Competition Registration Confirmed</h1>");
    html.append("<p>Dear ").append(escapeHtml(participantName)).append(",</p>");
    html.append("<p>Your registration has been confirmed");
    if (eventTitle != null && !eventTitle.isEmpty()) {
      html.append(" for <strong>").append(escapeHtml(eventTitle)).append("</strong>");
    }
    html.append(".</p>");

    html.append("<h2 style='color: #1f4c8f;'>Registration Details</h2>");
    html.append("<table style='width: 100%; border-collapse: collapse; margin: 20px 0;'>");
    html.append("<tr><td style='padding: 8px; border-bottom: 1px solid #ddd;'><strong>Competition:</strong></td>")
      .append("<td style='padding: 8px; border-bottom: 1px solid #ddd;'>")
      .append(escapeHtml(competitionName))
      .append("</td></tr>");
    if (feeAmount != null) {
      html.append("<tr><td style='padding: 8px; border-bottom: 1px solid #ddd;'><strong>Fee Paid:</strong></td>")
        .append("<td style='padding: 8px; border-bottom: 1px solid #ddd;'>$")
        .append(escapeHtml(feeAmount.toString()))
        .append("</td></tr>");
    }
    html.append("<tr><td style='padding: 8px; border-bottom: 1px solid #ddd;'><strong>Status:</strong></td>")
      .append("<td style='padding: 8px; border-bottom: 1px solid #ddd;'><span style='color: #059669; font-weight: bold;'>Confirmed</span></td></tr>");
    if (teamName != null && !teamName.isBlank()) {
      html.append("<tr><td style='padding: 8px; border-bottom: 1px solid #ddd;'><strong>Team:</strong></td>")
        .append("<td style='padding: 8px; border-bottom: 1px solid #ddd;'>")
        .append(escapeHtml(teamName))
        .append("</td></tr>");
    }
    html.append("</table>");

    if (rosterMemberNames != null && !rosterMemberNames.isEmpty()) {
      html.append("<h2 style='color: #1f4c8f;'>Team Roster</h2><ul style='margin: 0 0 20px 20px;'>");
      for (String member : rosterMemberNames) {
        html.append("<li>").append(escapeHtml(member)).append("</li>");
      }
      html.append("</ul>");
    }

    html.append("<p>We look forward to seeing you at the competition. Good luck!</p>");
    html.append("</div>");

    appendFooter(html, tenantId);
    html.append("</body></html>");
    return html.toString();
  }

  public String buildWinnersPublishedEmailBody(
    String eventTitle,
    String winnersUrl,
    List<EventCompetitionWinnerHighlight> highlights,
    String tenantId
  ) {
    StringBuilder html = new StringBuilder();
    html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body>");
    appendHeader(html, tenantId);

    html.append("<div style='max-width: 600px; margin: 0 auto; padding: 20px; font-family: Arial, sans-serif; line-height: 1.6; color: #333;'>");
    html.append("<h1 style='color: #1f4c8f;'>Competition Results Published</h1>");
    if (eventTitle != null && !eventTitle.isEmpty()) {
      html.append("<p>The results for <strong>").append(escapeHtml(eventTitle)).append("</strong> are now available.</p>");
    } else {
      html.append("<p>Competition results are now available.</p>");
    }

    if (highlights != null && !highlights.isEmpty()) {
      html.append("<h2 style='color: #1f4c8f;'>Top Placements</h2>");
      html.append("<table style='width: 100%; border-collapse: collapse; margin: 20px 0;'>");
      html.append("<tr style='background-color: #f0f7ff;'>")
        .append("<th style='padding: 8px; text-align: left; border-bottom: 2px solid #0066cc;'>Competition</th>")
        .append("<th style='padding: 8px; text-align: left; border-bottom: 2px solid #0066cc;'>Place</th>")
        .append("<th style='padding: 8px; text-align: left; border-bottom: 2px solid #0066cc;'>Winner</th>")
        .append("</tr>");
      for (EventCompetitionWinnerHighlight highlight : highlights) {
        String placeLabel = highlight.getPlacementLabel() != null && !highlight.getPlacementLabel().isEmpty()
          ? highlight.getPlacementLabel()
          : (highlight.getPlacement() != null ? "#" + highlight.getPlacement() : "-");
        html.append("<tr>")
          .append("<td style='padding: 8px; border-bottom: 1px solid #ddd;'>")
          .append(escapeHtml(highlight.getCompetitionName()))
          .append("</td>")
          .append("<td style='padding: 8px; border-bottom: 1px solid #ddd;'>")
          .append(escapeHtml(placeLabel))
          .append("</td>")
          .append("<td style='padding: 8px; border-bottom: 1px solid #ddd;'>")
          .append(escapeHtml(highlight.getDisplayName()))
          .append("</td>")
          .append("</tr>");
      }
      html.append("</table>");
    }

    if (winnersUrl != null && !winnersUrl.isEmpty()) {
      html.append("<p style='text-align: center; margin: 30px 0;'>")
        .append("<a href='")
        .append(escapeHtml(winnersUrl))
        .append("' style='background-color: #0066cc; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; font-weight: bold;'>View Full Results</a>")
        .append("</p>");
    }
    html.append("</div>");

    appendFooter(html, tenantId);
    html.append("</body></html>");
    return html.toString();
  }

  public String buildRegistrationSummaryEmailBody(
    String eventTitle,
    List<EventCompetitionSummaryRow> summaryRows,
    long totalRegistrations,
    String tenantId
  ) {
    StringBuilder html = new StringBuilder();
    html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body>");
    appendHeader(html, tenantId);

    html.append("<div style='max-width: 700px; margin: 0 auto; padding: 20px; font-family: Arial, sans-serif; line-height: 1.6; color: #333;'>");
    html.append("<h1 style='color: #1f4c8f;'>Competition Registration Summary</h1>");
    if (eventTitle != null && !eventTitle.isEmpty()) {
      html.append("<p>Registration summary for <strong>").append(escapeHtml(eventTitle)).append("</strong>.</p>");
    }
    html.append("<p><strong>Total confirmed registrations:</strong> ").append(totalRegistrations).append("</p>");

    if (summaryRows != null && !summaryRows.isEmpty()) {
      html.append("<table style='width: 100%; border-collapse: collapse; margin: 20px 0;'>");
      html.append("<tr style='background-color: #f0f7ff;'>")
        .append("<th style='padding: 8px; text-align: left; border-bottom: 2px solid #0066cc;'>Competition</th>")
        .append("<th style='padding: 8px; text-align: right; border-bottom: 2px solid #0066cc;'>Confirmed</th>")
        .append("<th style='padding: 8px; text-align: right; border-bottom: 2px solid #0066cc;'>Pending</th>")
        .append("<th style='padding: 8px; text-align: right; border-bottom: 2px solid #0066cc;'>Cancelled</th>")
        .append("<th style='padding: 8px; text-align: right; border-bottom: 2px solid #0066cc;'>Total Fees</th>")
        .append("</tr>");
      for (EventCompetitionSummaryRow row : summaryRows) {
        html.append("<tr>")
          .append("<td style='padding: 8px; border-bottom: 1px solid #ddd;'>")
          .append(escapeHtml(row.getCompetitionName()))
          .append("</td>")
          .append("<td style='padding: 8px; border-bottom: 1px solid #ddd; text-align: right;'>")
          .append(row.getConfirmedCount() != null ? row.getConfirmedCount() : 0)
          .append("</td>")
          .append("<td style='padding: 8px; border-bottom: 1px solid #ddd; text-align: right;'>")
          .append(row.getPendingCount() != null ? row.getPendingCount() : 0)
          .append("</td>")
          .append("<td style='padding: 8px; border-bottom: 1px solid #ddd; text-align: right;'>")
          .append(row.getCancelledCount() != null ? row.getCancelledCount() : 0)
          .append("</td>")
          .append("<td style='padding: 8px; border-bottom: 1px solid #ddd; text-align: right;'>$")
          .append(row.getTotalFees() != null ? escapeHtml(row.getTotalFees().toString()) : "0.00")
          .append("</td>")
          .append("</tr>");
      }
      html.append("</table>");
    } else {
      html.append("<p>No registration data found for this event.</p>");
    }
    html.append("</div>");

    appendFooter(html, tenantId);
    html.append("</body></html>");
    return html.toString();
  }

  public String applyTemplatePlaceholders(
    String content,
    String participantName,
    String competitionName,
    BigDecimal feeAmount,
    String eventTitle,
    String winnersUrl
  ) {
    if (content == null) {
      return "";
    }
    String result = content;
    result = result.replace("{{participantName}}", participantName != null ? participantName : "");
    result = result.replace("{{competitionName}}", competitionName != null ? competitionName : "");
    result = result.replace("{{feeAmount}}", feeAmount != null ? feeAmount.toString() : "");
    result = result.replace("{{eventTitle}}", eventTitle != null ? eventTitle : "");
    result = result.replace("{{winnersUrl}}", winnersUrl != null ? winnersUrl : "");
    return result;
  }

  private void appendHeader(StringBuilder html, String tenantId) {
    String headerImageUrl = emailContentBuilderService.getTenantEmailHeaderImageUrl(tenantId);
    if (headerImageUrl != null && !headerImageUrl.isEmpty()) {
      html.append("<div style='text-align: center; margin-bottom: 20px;'>")
        .append("<img src='")
        .append(headerImageUrl)
        .append("' alt='Header' style='max-width: 100%; height: auto;' />")
        .append("</div>");
    }
  }

  private void appendFooter(StringBuilder html, String tenantId) {
    String footerHtml = emailContentBuilderService.getTenantEmailFooterHtml(tenantId);
    if (footerHtml != null && !footerHtml.isEmpty()) {
      html.append("<div>").append(footerHtml).append("</div>");
    }
  }

  private String escapeHtml(String input) {
    if (input == null) {
      return "";
    }
    return input
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&#39;");
  }
}
