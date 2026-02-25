package com.mtsaas.backend.domain.swift.mt;

import com.mtsaas.backend.domain.swift.mx.MxMessage;

import java.util.Map;

/**
 * Base class for MT message generators from MX messages.
 */
public abstract class BaseMtGenerator implements MtGenerator {

    @Override
    public String generate(MxMessage mxMessage) {
        validateInput(mxMessage);
        return generateMt(mxMessage);
    }

    /**
     * Validate the input MX message.
     */
    protected abstract void validateInput(MxMessage mxMessage);

    /**
     * Generate the MT message string.
     */
    protected abstract String generateMt(MxMessage mxMessage);

    /**
     * Escape special characters for MT format.
     */
    protected String escapeMt(String value) {
        if (value == null) return "";
        return value.replace("\n", " ")
                   .replace("\r", "")
                   .replace(":", "-");
    }

    /**
     * Format amount for MT (no decimal, implied 2 decimal places).
     */
    protected String formatAmount(String amount, String currency) {
        if (amount == null) return "";
        // Remove any non-numeric characters except decimal point
        String cleanAmount = amount.replaceAll("[^0-9.]", "");
        
        // Parse and format
        try {
            double amt = Double.parseDouble(cleanAmount);
            // Format with commas for thousands, no decimal point (implied 2 decimals)
            long amtLong = Math.round(amt * 100);
            return String.format("%s%d", currency != null ? currency : "USD", amtLong);
        } catch (NumberFormatException e) {
            return currency + amount;
        }
    }

    /**
     * Format date from ISO format to YYMMDD.
     */
    protected String formatDate(String isoDate) {
        if (isoDate == null || isoDate.length() < 10) return "";
        // ISO format: 2023-12-04 or 2023-12-04T10:00:00
        String datePart = isoDate.substring(0, 10);
        String[] parts = datePart.split("-");
        if (parts.length == 3) {
            String year = parts[0].substring(2); // Get last 2 digits
            return year + parts[1] + parts[2];
        }
        return "";
    }

    /**
     * Build MT message blocks.
     */
    protected String buildMtMessage(String messageType, String senderBic, String receiverBic, String block4Content) {
        StringBuilder mt = new StringBuilder();
        
        // Block 1: Basic Header
        mt.append("{1:F01").append(formatBic(senderBic)).append("AXXX0000000000}\n");
        
        // Block 2: Application Header (Output)
        mt.append("{2:O").append(messageType).append("0000000000");
        mt.append(formatBic(receiverBic)).append("AXXX00000000000000000000000}\n");
        
        // Block 4: Text
        mt.append("{4:\n");
        mt.append(block4Content);
        mt.append("-}\n");
        
        return mt.toString();
    }

    /**
     * Format BIC for MT (8 characters, padded if needed).
     */
    protected String formatBic(String bic) {
        if (bic == null) return "XXXXXXXX";
        // Remove branch code if present (11 chars -> 8 chars)
        if (bic.length() >= 11) {
            bic = bic.substring(0, 8);
        }
        // Pad to 8 characters
        while (bic.length() < 8) {
            bic += "X";
        }
        return bic.toUpperCase();
    }

    /**
     * Get field value from MxMessage with fallback.
     */
    protected String getField(MxMessage mxMessage, String key, String defaultValue) {
        Map<String, String> fields = mxMessage.getFields();
        if (fields == null) return defaultValue;
        String value = fields.get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Get field value from MxMessage.
     */
    protected String getField(MxMessage mxMessage, String key) {
        return getField(mxMessage, key, "");
    }
}
