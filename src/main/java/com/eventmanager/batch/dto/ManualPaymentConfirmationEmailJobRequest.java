package com.eventmanager.batch.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request DTO for manual payment confirmation email job.
 * Sent immediately after payment request creation.
 */
@Data
public class ManualPaymentConfirmationEmailJobRequest {

    @NotNull
    private Long paymentRequestId;

    @NotNull
    private Long eventId;

    @NotBlank
    @Email
    @Size(max = 255)
    private String recipientEmail;

    @NotBlank
    @Size(max = 255)
    private String recipientName;

    @NotBlank
    @Size(max = 100)
    private String paymentMethod;

    @NotNull
    private BigDecimal amount;

    @Size(max = 255)
    private String paymentHandle;

    @Size(max = 1000)
    private String paymentInstructions;

    @NotBlank
    @Size(max = 255)
    private String eventTitle;

    @Size(max = 50)
    private String eventDate;

    @Size(max = 50)
    private String eventTime;

    @Size(max = 255)
    private String eventLocation;

    @Size(max = 500)
    private String eventAddress;

    private List<TicketSummaryItem> ticketSummary;

    @NotBlank
    @Size(max = 255)
    private String tenantId;

    @Size(max = 100)
    private String transactionReference;

    /**
     * Inner class for ticket summary items.
     */
    @Data
    public static class TicketSummaryItem {
        @NotBlank
        @Size(max = 255)
        private String ticketTypeName;

        @NotNull
        private Integer quantity;

        @NotNull
        private BigDecimal pricePerUnit;

        @NotNull
        private BigDecimal totalAmount;
    }
}
