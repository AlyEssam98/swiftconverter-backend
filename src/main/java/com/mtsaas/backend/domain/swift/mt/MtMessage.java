package com.mtsaas.backend.domain.swift.mt;

import lombok.Data;
import java.util.Map;

@Data
public class MtMessage {
    private String type; // e.g., "103"
    private String sender;
    private String receiver;
    private Map<String, String> tags; // e.g., "20" -> "REF123", "32A" -> "DATE CURRENCY AMOUNT"

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }
}
