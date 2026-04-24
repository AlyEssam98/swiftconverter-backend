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

        // Constructing the complex JSON-API payload
        Map<String, Object> customData = new HashMap<>();
        customData.put("user_id", user.getId().toString());
        customData.put("credits", String.valueOf(credits));

        Map<String, Object> checkoutData = new HashMap<>();
        checkoutData.put("email", user.getEmail());
        checkoutData.put("custom", customData);

        List<Map<String, Object>> checkoutOptions = new ArrayList<>();
        Map<String, Object> option = new HashMap<>();
        option.put("redirect_url", frontendUrl + "/dashboard/credits/success?session_id={checkout_session_id}");
        checkoutOptions.add(option);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("checkout_data", checkoutData);
        attributes.put("checkout_options", checkoutOptions);

        Map<String, Object> storeData = new HashMap<>();
        storeData.put("type", "stores");
        storeData.put("id", storeId);

        Map<String, Object> variantData = new HashMap<>();
        variantData.put("type", "variants");
        variantData.put("id", variantId);

        Map<String, Object> relationships = new HashMap<>();
        relationships.put("store", Map.of("data", storeData));
        relationships.put("variant", Map.of("data", variantData));

        Map<String, Object> data = new HashMap<>();
        data.put("type", "checkouts");
        data.put("attributes", attributes);
        data.put("relationships", relationships);

        Map<String, Object> payload = new HashMap<>();
        payload.put("data", data);

        try {
            ObjectMapper mapper = new ObjectMapper();
            
            // Build JSON manually to ensure checkout_options is properly formatted as array
            String jsonPayload = "{"
                + "\"data\":{"
                + "\"type\":\"checkouts\","
                + "\"attributes\":{"
                + "\"checkout_data\":{\"email\":\"" + user.getEmail() + "\",\"custom\":{\"user_id\":\"" + user.getId() + "\",\"credits\":\"" + credits + "\"}},"
                + "\"checkout_options\":[{\"redirect_url\":\"" + frontendUrl.replaceAll("\"", "\\\\\"") + "/dashboard/credits/success?session_id={checkout_session_id}\"}]"
                + "},"
                + "\"relationships\":{"
                + "\"store\":{\"data\":{\"type\":\"stores\",\"id\":\"" + storeId + "\"}},"
                + "\"variant\":{\"data\":{\"type\":\"variants\",\"id\":\"" + variantId + "\"}}"
                + "}"
                + "}"
                + "}";
            
            log.error("📤 DEBUG: Raw JSON payload:\n{}", jsonPayload);
            
            HttpEntity<String> request = new HttpEntity<>(jsonPayload, headers);
            ResponseEntity<String> response = restTemplate.exchange(API_URL, HttpMethod.POST, request, String.class);
            
            log.error("✅ DEBUG: Lemon Squeezy response SUCCESS: {}", response.getBody());
            
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
            // Test with a simple GET request to check API connectivity
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
}
