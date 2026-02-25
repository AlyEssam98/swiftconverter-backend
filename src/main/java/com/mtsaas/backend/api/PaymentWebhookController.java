package com.mtsaas.backend.api;

import com.mtsaas.backend.application.service.CreditService;
import com.mtsaas.backend.domain.User;
import com.mtsaas.backend.infrastructure.repository.UserRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments/webhook")
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookController {

    private final CreditService creditService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${stripe.webhook.secret:whsec_placeholder}")
    private String webhookSecret;

    @PostMapping
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            log.info("Stripe signature verified successfully for event: {}", event.getId());
        } catch (SignatureVerificationException e) {
            log.error("Invalid Stripe signature: {}. Secret prefix: {}", e.getMessage(),
                    webhookSecret != null && webhookSecret.length() > 7 ? webhookSecret.substring(0, 7) : "none");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }

        log.info("Received Stripe event: {} (ID: {})", event.getType(), event.getId());

        if ("checkout.session.completed".equals(event.getType())) {
            try {
                // Parse the raw payload JSON directly to avoid deserialization issues
                JsonNode rootNode = objectMapper.readTree(payload);
                JsonNode dataNode = rootNode.path("data").path("object");
                
                String sessionId = dataNode.path("id").asText();
                String clientRefId = dataNode.path("client_reference_id").asText();
                JsonNode metadataNode = dataNode.path("metadata");
                String creditsStr = metadataNode.path("credits").asText();
                String paymentStatus = dataNode.path("payment_status").asText();
                
                log.info("Parsed checkout session: id={}, clientRef={}, credits={}, paymentStatus={}", 
                        sessionId, clientRefId, creditsStr, paymentStatus);
                
                // Only process if payment was successful
                if ("paid".equals(paymentStatus)) {
                    if (clientRefId != null && !clientRefId.isEmpty() && creditsStr != null && !creditsStr.isEmpty()) {
                        handleCheckoutSessionCompletedManual(sessionId, clientRefId, creditsStr);
                    } else {
                        log.warn("Missing metadata in checkout session: {}", sessionId);
                    }
                } else {
                    log.warn("Checkout session {} has payment_status: {} (not processing)", sessionId, paymentStatus);
                }
            } catch (Exception e) {
                log.error("Failed to parse checkout.session.completed event: {}", e.getMessage(), e);
            }
        } else {
            log.debug("Ignoring unhandled Stripe event type: {}", event.getType());
        }

        return ResponseEntity.ok("Received");
    }

    private void handleCheckoutSessionCompletedManual(String sessionId, String userIdStr, String creditsStr) {
        try {
            UUID userId = UUID.fromString(userIdStr);
            long credits = Long.parseLong(creditsStr);

            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                log.info("Fulfilling purchase: {} credits for user: {}", credits, user.getEmail());
                creditService.addPurchasedCredits(user, credits, sessionId);
                log.info("Successfully added {} credits to user {}", credits, user.getEmail());
            } else {
                log.warn("User not found for purchase fulfillment: {}", userId);
            }
        } catch (Exception e) {
            log.error("Failed to process purchase fulfillment for session {}: {}", sessionId, e.getMessage(), e);
        }
    }
}
