package com.mtsaas.backend.application.service;

import com.mtsaas.backend.domain.User;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
@Slf4j
public class StripeService {

        @Value("${stripe.secret.key:sk_test_placeholder}")
        private String secretKey;

        @Value("${app.frontend.url:http://localhost:3000}")
        private String frontendUrl;

        @PostConstruct
        public void init() {
                if (secretKey == null || secretKey.contains("placeholder")) {
                        log.warn("⚠️  Stripe secret key not configured. Payment functionality will be disabled.");
                        log.warn("⚠️  To enable payments, set 'stripe.secret.key' in application.properties");
                } else {
                        Stripe.apiKey = secretKey;
                        log.info("✅ Stripe initialized successfully");
                }
        }

        public String createCheckoutSession(User user, String packageName, long credits, long amountCents)
                        throws StripeException {

                // Validate Stripe configuration
                if (secretKey == null || secretKey.contains("placeholder")) {
                        log.error("Attempted to create checkout session without valid Stripe configuration");
                        throw new RuntimeException("Payment system is not configured. Please contact support.");
                }
                SessionCreateParams params = SessionCreateParams.builder()
                                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                                .setMode(SessionCreateParams.Mode.PAYMENT)
                                .setSuccessUrl(frontendUrl
                                                + "/dashboard/credits/success?session_id={CHECKOUT_SESSION_ID}")
                                .setCancelUrl(frontendUrl + "/dashboard/credits/cancel")
                                .setCustomerEmail(user.getEmail())
                                .setClientReferenceId(user.getId().toString())
                                .addLineItem(
                                                SessionCreateParams.LineItem.builder()
                                                                .setQuantity(1L)
                                                                .setPriceData(
                                                                                SessionCreateParams.LineItem.PriceData
                                                                                                .builder()
                                                                                                .setCurrency("usd")
                                                                                                .setUnitAmount(amountCents)
                                                                                                .setProductData(
                                                                                                                SessionCreateParams.LineItem.PriceData.ProductData
                                                                                                                                .builder()
                                                                                                                                .setName(packageName)
                                                                                                                                .setDescription("Purchase of "
                                                                                                                                                + credits
                                                                                                                                                + " conversion credits")
                                                                                                                                .build())
                                                                                                .build())
                                                                .build())
                                .putMetadata("credits", String.valueOf(credits))
                                .putMetadata("package_id", packageName.toLowerCase().replace(" ", "-"))
                                .build();

                Session session = Session.create(params);
                return session.getUrl();
        }
}
