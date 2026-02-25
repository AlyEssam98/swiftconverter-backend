package com.mtsaas.backend.domain.swift.mx;

import lombok.Data;
import java.util.Map;

/**
 * Represents a parsed ISO 20022 MX message.
 */
@Data
public class MxMessage {
    private String messageType; // e.g., "pacs.008.001.08", "pacs.009.001.08", "camt.053.001.08"
    private String businessMessageId;
    private String messageDefinitionId;
    private String senderBic;
    private String receiverBic;
    private String creationDateTime;
    private Map<String, String> fields; // Parsed fields from the XML
    private String rawXml; // Original XML content

    public MxMessage() {
        this.fields = new java.util.HashMap<>();
    }
}
