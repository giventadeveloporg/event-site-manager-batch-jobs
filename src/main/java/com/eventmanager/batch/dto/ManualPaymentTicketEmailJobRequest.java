package com.eventmanager.batch.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request DTO for manual payment ticket email job.
 * Sent after admin confirms payment receipt.
 */
@Data
public class ManualPaymentTicketEmailJobRequest {

    @NotNull
    private Long paymentRequestId;

    @NotNull
    private Long eventId;

    @NotNull
    private Long ticketTransactionId;

    @NotBlank
    @Email
    @Size(max = 255)
    private String recipientEmail;

    @NotBlank
    @Size(max = 255)
    private String recipientName;

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

    @NotBlank
    @Size(max = 2048)
    private String qrCodeImageUrl;

    private List<TicketItem> ticketItems;

    @NotNull
    private BigDecimal totalAmount;

    @NotBlank
    @Size(max = 100)
    private String transactionReference;

    @NotBlank
    @Size(max = 255)
    private String tenantId;

    /**
     * Inner class for ticket items.
     */
    @Data
    public static class TicketItem {
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
