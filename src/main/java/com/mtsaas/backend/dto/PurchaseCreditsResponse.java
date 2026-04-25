package com.mtsaas.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseCreditsResponse {
    private Boolean success;
    private String message;
    private Long newBalance;
    private Long creditsAdded;
    private String transactionId;
    private BigDecimal amountPaid;
    private String checkoutUrl;
}
