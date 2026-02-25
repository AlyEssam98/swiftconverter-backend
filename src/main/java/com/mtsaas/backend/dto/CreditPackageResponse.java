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
public class CreditPackageResponse {
    private String id;
    private String name;
    private String description;
    private Long credits;
    private BigDecimal price;
    private String currency;
    private Boolean popular;
    private String features;
    private String billingPeriod; // "MONTHLY" to indicate subscription
}
