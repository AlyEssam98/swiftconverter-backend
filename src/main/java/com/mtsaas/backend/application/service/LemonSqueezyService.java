package com.mtsaas.backend.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mtsaas.backend.application.exception.PaymentConfigurationException;
import com.mtsaas.backend.application.exception.PaymentGatewayUnavailableException;
import com.mtsaas.backend.domain.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class LemonSqueezyService {

    @Value("${lemon-squeezy.api-key:placeholder}")
    private String apiKey;

    @Value("${lemon-squeezy.store-id:placeholder}")
    private String storeId;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String API_URL = "https://api.lemonsqueezy.com/v1/checkouts";

    @PostConstruct
    public void init() {
        List<String> missingConfig = getMissingConfigFields();
        if (!missingConfig.isEmpty()) {
            log.warn("Lemon Squeezy configuration missing fields: {}. Payment functionality will be disabled.",
                    String.join(", ", missingConfig));
        } else {
            log.info("✅ Lemon Squeezy initialized successfully");
        }
    }

    public String createCheckoutSession(User user, String variantId, long credits) {
        if (apiKey == null || apiKey.contains("placeholder") ||
                storeId == null || storeId.contains("placeholder")) {
            log.error("Attempted checkout with invalid Lemon Squeezy configuration");
            throw new PaymentConfigurationException("Payment system is not configured. Please contact support.");
        }

        if (variantId == null || variantId.isBlank() || variantId.contains("placeholder")) {
            log.error("Attempted checkout with invalid variant ID configuration: {}", variantId);
            throw new PaymentConfigurationException("Payment variant is not configured. Please contact support.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("Accept", "application/vnd.api+json");
        headers.set("Content-Type", "application/vnd.api+json");

        // Build the JSON:API payload per Lemon Squeezy spec
        // checkout_data: pre-fill customer info + custom metadata
        Map<String, Object> customData = new HashMap<>();
        customData.put("user_id", user.getId().toString());
        customData.put("credits", String.valueOf(credits));

        Map<String, Object> checkoutData = new HashMap<>();
        checkoutData.put("email", user.getEmail());
        checkoutData.put("custom", customData);

        // product_options: redirect_url belongs here (NOT in checkout_options)
        String redirectUrl = frontendUrl + "/dashboard/credits/success?session_id={checkout_session_id}";
        Map<String, Object> productOptions = new HashMap<>();
        productOptions.put("redirect_url", redirectUrl);

        // checkout_options: display settings object (NOT an array)
        Map<String, Object> checkoutOptions = new HashMap<>();
        checkoutOptions.put("embed", false);

        // Assemble attributes
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("checkout_data", checkoutData);
        attributes.put("product_options", productOptions);
        attributes.put("checkout_options", checkoutOptions);

        // Relationships
        Map<String, Object> storeData = new HashMap<>();
        storeData.put("type", "stores");
        storeData.put("id", storeId);

        Map<String, Object> variantData = new HashMap<>();
        variantData.put("type", "variants");
        variantData.put("id", variantId);

        Map<String, Object> relationships = new HashMap<>();
        relationships.put("store", Map.of("data", storeData));
        relationships.put("variant", Map.of("data", variantData));

        // Top-level data envelope
        Map<String, Object> data = new HashMap<>();
        data.put("type", "checkouts");
        data.put("attributes", attributes);
        data.put("relationships", relationships);

        Map<String, Object> payload = new HashMap<>();
        payload.put("data", data);

        try {
            ObjectMapper mapper = new ObjectMapper();
            String jsonPayload = mapper.writeValueAsString(payload);
            
            log.info("📤 Lemon Squeezy checkout request for user: {}, variant: {}", user.getEmail(), variantId);
            log.debug("📤 Lemon Squeezy payload: {}", jsonPayload);
            
            HttpEntity<String> request = new HttpEntity<>(jsonPayload, headers);
            ResponseEntity<String> response = restTemplate.exchange(API_URL, HttpMethod.POST, request, String.class);
            
            log.info("✅ Lemon Squeezy checkout created successfully for user: {}", user.getEmail());
            
            Map<String, Object> responseBody = mapper.readValue(response.getBody(), Map.class);
            if (responseBody != null && responseBody.containsKey("data")) {
                Map<String, Object> responseData = (Map<String, Object>) responseBody.get("data");
                Map<String, Object> responseAttributes = (Map<String, Object>) responseData.get("attributes");
                return (String) responseAttributes.get("url");
            }
            throw new RuntimeException("Invalid response format from Lemon Squeezy");
        } catch (RestClientResponseException e) {
            log.error("❌ Lemon Squeezy checkout failed with status {} and body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new PaymentGatewayUnavailableException("Payment provider rejected checkout request", e);
        } catch (PaymentConfigurationException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ Failed to create Lemon Squeezy checkout session", e);
            throw new PaymentGatewayUnavailableException("Payment provider is currently unavailable", e);
        }
    }

    /**
     * Verify a checkout session by fetching its order from Lemon Squeezy API.
     * Returns order info if paid, null otherwise.
     */
    public VerifiedOrder verifyCheckoutSession(String sessionId) {
        if (apiKey == null || apiKey.contains("placeholder")) {
            throw new PaymentConfigurationException("Payment system is not configured");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("Accept", "application/vnd.api+json");

        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            // Fetch orders for this checkout session
            String url = "https://api.lemonsqueezy.com/v1/orders?filter[checkout_id]=" + sessionId;
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);

            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> responseBody = mapper.readValue(response.getBody(), Map.class);

            List<Map<String, Object>> orders = (List<Map<String, Object>>) responseBody.get("data");
            if (orders == null || orders.isEmpty()) {
                log.info("No order found for checkout session: {}", sessionId);
                return null;
            }

            // Get the first (should be only) order
            Map<String, Object> orderData = orders.get(0);
            String orderId = (String) orderData.get("id");
            Map<String, Object> attributes = (Map<String, Object>) orderData.get("attributes");
            String status = (String) attributes.get("status");

            if (!"paid".equals(status)) {
                log.info("Order {} for session {} has status: {} (not paid)", orderId, sessionId, status);
                return null;
            }

            // Extract custom_data from meta
            Map<String, Object> meta = (Map<String, Object>) orderData.get("meta");
            Map<String, Object> customData = meta != null ? (Map<String, Object>) meta.get("custom_data") : null;

            String userId = customData != null ? (String) customData.get("user_id") : null;
            String credits = customData != null ? (String) customData.get("credits") : null;

            log.info("Verified paid order: id={}, userId={}, credits={}, sessionId={}", orderId, userId, credits, sessionId);

            return new VerifiedOrder(orderId, userId, credits, sessionId);
        } catch (RestClientResponseException e) {
            log.error("Lemon Squeezy order verify failed with status {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("Failed to verify Lemon Squeezy checkout session: {}", sessionId, e);
            return null;
        }
    }

    public static class VerifiedOrder {
        private final String orderId;
        private final String userId;
        private final String credits;
        private final String sessionId;

        public VerifiedOrder(String orderId, String userId, String credits, String sessionId) {
            this.orderId = orderId;
            this.userId = userId;
            this.credits = credits;
            this.sessionId = sessionId;
        }

        public String getOrderId() { return orderId; }
        public String getUserId() { return userId; }
        public String getCredits() { return credits; }
        public String getSessionId() { return sessionId; }
    }

    private List<String> getMissingConfigFields() {
        List<String> missing = new ArrayList<>();
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("placeholder")) {
            missing.add("LEMON_SQUEEZY_API_KEY");
        }
        if (storeId == null || storeId.isBlank() || storeId.contains("placeholder")) {
            missing.add("LEMON_SQUEEZY_STORE_ID");
        }
        if (frontendUrl == null || frontendUrl.isBlank() || frontendUrl.contains("localhost")) {
            missing.add("FRONTEND_URL");
        }
        return missing;
    }
}
