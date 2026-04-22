package com.mtsaas.backend.application.exception;

public class InvalidPurchaseRequestException extends RuntimeException {
    public InvalidPurchaseRequestException(String message) {
        super(message);
    }
}
