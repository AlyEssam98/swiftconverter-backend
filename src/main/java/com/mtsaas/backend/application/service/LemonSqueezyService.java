package com.mtsaas.backend.application.service;

import com.mtsaas.backend.domain.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import jakarta.annotation.PostConstruct;

import java.util.HashMap;
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
        if (apiKey == null || apiKey.contains("placeholder") || storeId == null || storeId.contains("placeholder")) {
            log.warn("⚠️  Lemon Squeezy API key or Store ID not configured. Payment functionality will be disabled.");
        } else {
            log.info("✅ Lemon Squeezy initialized successfully");
        }
    }

    public String createCheckoutSession(User user, String variantId, long credits) {
        if (apiKey == null || apiKey.contains("placeholder")) {
            log.error("Attempted to create checkout session without valid Lemon Squeezy configuration");
            throw new RuntimeException("Payment system is not configured. Please contact support.");
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

        Map<String, Object> checkoutOptions = new HashMap<>();
        checkoutOptions.put("redirect_url", frontendUrl + "/dashboard/credits/success?session_id={checkout_session_id}");

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

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(API_URL, HttpMethod.POST, request, Map.class);
            Map<String, Object> responseBody = response.getBody();
            
            if (responseBody != null && responseBody.containsKey("data")) {
                Map<String, Object> responseData = (Map<String, Object>) responseBody.get("data");
                Map<String, Object> responseAttributes = (Map<String, Object>) responseData.get("attributes");
                return (String) responseAttributes.get("url");
            }
            throw new RuntimeException("Invalid response format from Lemon Squeezy");
        } catch (Exception e) {
            log.error("Failed to create Lemon Squeezy checkout session: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize payment session");
        }
    }
}
