package com.mtsaas.backend.api;

import com.mtsaas.backend.application.service.CreditService;
import com.mtsaas.backend.domain.User;
import com.mtsaas.backend.infrastructure.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments/webhook")
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookController {

    private final CreditService creditService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${lemon-squeezy.webhook.secret:placeholder}")
    private String webhookSecret;

    @PostMapping
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("X-Signature") String signatureHeader) {

        if (webhookSecret == null || webhookSecret.contains("placeholder")) {
            log.error("Lemon Squeezy webhook secret is not configured.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Webhook secret not configured");
        }

        if (!verifySignature(payload, signatureHeader, webhookSecret)) {
            log.error("Invalid Lemon Squeezy signature.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }

        try {
            JsonNode rootNode = objectMapper.readTree(payload);
            JsonNode metaNode = rootNode.path("meta");
            String eventName = metaNode.path("event_name").asText();

            log.info("Received Lemon Squeezy event: {}", eventName);

            if ("order_created".equals(eventName)) {
                JsonNode dataNode = rootNode.path("data");
                String orderId = dataNode.path("id").asText();
                String status = dataNode.path("attributes").path("status").asText();

                if ("paid".equals(status)) {
                    JsonNode customData = metaNode.path("custom_data");
                    String userIdStr = customData.path("user_id").asText();
                    String creditsStr = customData.path("credits").asText();

                    if (!userIdStr.isEmpty() && !creditsStr.isEmpty()) {
                        log.info("Parsed Lemon Squeezy order: id={}, userId={}, credits={}", orderId, userIdStr,
                                creditsStr);
                        handleOrderCreated(orderId, userIdStr, creditsStr);
                    } else {
                        log.warn("Missing custom_data in order: {}", orderId);
                    }
                } else {
                    log.warn("Order {} has status: {} (not processing)", orderId, status);
                }
            } else {
                log.debug("Ignoring unhandled Lemon Squeezy event type: {}", eventName);
            }
        } catch (Exception e) {
            log.error("Failed to parse Lemon Squeezy webhook event: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok("Received");
    }

    private boolean verifySignature(String payload, String signature, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hashBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            StringBuilder hashString = new StringBuilder();
            for (byte b : hashBytes) {
                hashString.append(String.format("%02x", b));
            }

            return hashString.toString().equals(signature);
        } catch (Exception e) {
            log.error("Error verifying signature: {}", e.getMessage());
            return false;
        }
    }

    private void handleOrderCreated(String orderId, String userIdStr, String creditsStr) {
        try {
            UUID userId = UUID.fromString(userIdStr);
            long credits = Long.parseLong(creditsStr);

            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                log.info("Fulfilling purchase: {} credits for user: {}", credits, user.getEmail());
                creditService.addPurchasedCredits(user, credits, orderId);
                log.info("Successfully added {} credits to user {}", credits, user.getEmail());
            } else {
                log.warn("User not found for purchase fulfillment: {}", userId);
            }
        } catch (Exception e) {
            log.error("Failed to process purchase fulfillment for order {}: {}", orderId, e.getMessage(), e);
        }
    }
}
