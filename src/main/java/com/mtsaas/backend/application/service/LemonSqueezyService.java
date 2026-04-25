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

    public CheckoutResult createCheckoutSession(User user, String variantId, long credits) {
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
                String url = (String) responseAttributes.get("url");
                String checkoutId = (String) responseData.get("id");
                return new CheckoutResult(url, checkoutId);
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

    public void testApiConnection() {
        if (apiKey == null || apiKey.contains("placeholder") ||
                storeId == null || storeId.contains("placeholder")) {
            throw new PaymentConfigurationException("Lemon Squeezy is not properly configured");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("Accept", "application/vnd.api+json");

        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                "https://api.lemonsqueezy.com/v1/stores",
                HttpMethod.GET,
                request,
                String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✓ Lemon Squeezy API connection successful");
            } else {
                throw new RuntimeException("API returned status: " + response.getStatusCode());
            }
        } catch (RestClientResponseException e) {
            log.error("Lemon Squeezy API test failed with status {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("API test failed: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Lemon Squeezy API test failed", e);
            throw new RuntimeException("API test failed: " + e.getMessage());
        }
    }

    /**
     * Fetch checkout status from Lemon Squeezy API.
     * Returns a map with: status (string), orderId (string or null), orderStatus (string or null)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getCheckoutStatus(String checkoutId) {
        if (apiKey == null || apiKey.contains("placeholder")) {
            throw new PaymentConfigurationException("Lemon Squeezy API key is not configured");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("Accept", "application/vnd.api+json");
        headers.set("Content-Type", "application/vnd.api+json");

        HttpEntity<String> request = new HttpEntity<>(headers);
        String url = "https://api.lemonsqueezy.com/v1/checkouts/" + checkoutId;

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, request, String.class);

            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> body = mapper.readValue(response.getBody(), Map.class);
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            Map<String, Object> attributes = (Map<String, Object>) data.get("attributes");

            String status = (String) attributes.get("status");

            // Check relationships for order
            Map<String, Object> relationships = (Map<String, Object>) data.get("relationships");
            String orderId = null;
            String orderStatus = null;

            if (relationships != null && relationships.containsKey("order")) {
                Map<String, Object> orderRel = (Map<String, Object>) relationships.get("order");
                Map<String, Object> orderData = (Map<String, Object>) orderRel.get("data");
                if (orderData != null) {
                    orderId = (String) orderData.get("id");
                }
            }

            // If an order exists, fetch its status too
            if (orderId != null) {
                try {
                    String orderUrl = "https://api.lemonsqueezy.com/v1/orders/" + orderId;
                    ResponseEntity<String> orderResponse = restTemplate.exchange(
                        orderUrl, HttpMethod.GET, request, String.class);
                    Map<String, Object> orderBody = mapper.readValue(orderResponse.getBody(), Map.class);
                    Map<String, Object> orderData = (Map<String, Object>) orderBody.get("data");
                    Map<String, Object> orderAttrs = (Map<String, Object>) orderData.get("attributes");
                    orderStatus = (String) orderAttrs.get("status");
                } catch (Exception e) {
                    log.warn("Could not fetch order status for orderId={}: {}", orderId, e.getMessage());
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("status", status);
            result.put("orderId", orderId);
            result.put("orderStatus", orderStatus);
            return result;

        } catch (RestClientResponseException e) {
            log.error("Failed to fetch checkout status for {}: status={}, body={}",
                checkoutId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new PaymentGatewayUnavailableException(
                "Failed to verify checkout with payment provider", e);
        } catch (Exception e) {
            log.error("Unexpected error fetching checkout status for {}", checkoutId, e);
            throw new PaymentGatewayUnavailableException(
                "Payment provider verification failed", e);
        }
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

    public record CheckoutResult(String url, String checkoutId) {}
}
