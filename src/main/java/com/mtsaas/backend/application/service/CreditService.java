package com.mtsaas.backend.application.service;

import com.mtsaas.backend.domain.User;
import com.mtsaas.backend.domain.CreditUsage;
import com.mtsaas.backend.domain.CreditPurchase;
import com.mtsaas.backend.domain.Conversion;
import com.mtsaas.backend.infrastructure.repository.UserRepository;
import com.mtsaas.backend.infrastructure.repository.CreditUsageRepository;
import com.mtsaas.backend.infrastructure.repository.CreditPurchaseRepository;
import com.mtsaas.backend.infrastructure.email.EmailService;
import com.mtsaas.backend.dto.CreditBalanceResponse;
import com.mtsaas.backend.dto.CreditPackageResponse;
import com.mtsaas.backend.dto.PurchaseCreditsResponse;
import com.mtsaas.backend.dto.CreditUsageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreditService {

        private final UserRepository userRepository;
        private final CreditUsageRepository creditUsageRepository;
        private final CreditPurchaseRepository creditPurchaseRepository;
        private final StripeService stripeService;
        private final EmailService emailService;


        public CreditBalanceResponse getUserCreditBalance(String email) {
                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new RuntimeException("User not found: " + email));

                LocalDateTime now = LocalDateTime.now();
                log.info("=== Getting credit balance for user: {} at: {}", email, now);
                
                // Mark expired purchases as expired
                List<CreditPurchase> expiredPurchases = creditPurchaseRepository.findExpiredPurchases(user, now);
                log.info("Found {} expired purchases to mark", expiredPurchases.size());
                for (CreditPurchase purchase : expiredPurchases) {
                        purchase.setExpired(true);
                        creditPurchaseRepository.save(purchase);
                        log.info("  - Marked expired: {} credits, Expiry was: {}", 
                                purchase.getCreditAmount(), purchase.getExpiryDate());
                }

                // Get available credits from valid (non-expired) purchases
                Long purchaseCredits = creditPurchaseRepository.getAvailableCredits(user, now);
                if (purchaseCredits == null) {
                        purchaseCredits = 0L;
                }
                
                // Add user's direct credits (from signup bonus)
                Long userDirectCredits = user.getCredits();
                
                // Total available credits = purchase credits + direct user credits
                Long availableCredits = purchaseCredits + userDirectCredits;
                
                log.info("✓ Purchase credits: {}, User direct credits: {}, Total available: {}", 
                        purchaseCredits, userDirectCredits, availableCredits);

                Long totalUsed = creditUsageRepository.getTotalCreditsUsedByUser(user);
                
                // Get earliest expiry date from valid purchases
                List<CreditPurchase> validPurchases = creditPurchaseRepository.findValidPurchases(user, now);
                log.info("Found {} valid (non-expired) purchases", validPurchases.size());
                validPurchases.forEach(p -> {
                        long daysLeft = ChronoUnit.DAYS.between(now, p.getExpiryDate());
                        log.info("  - Purchase: {} credits, Expires: {} ({} days remaining)", 
                                p.getCreditAmount(), p.getExpiryDate(), daysLeft);
                });
                
                String subscriptionStatus = validPurchases.isEmpty() ? "NOT_SUBSCRIBED" : "ACTIVE";
                Long daysUntilExpiry = null;
                String expiryDateStr = null;
                
                if (!validPurchases.isEmpty()) {
                        // Get the earliest expiry date
                        CreditPurchase earliestExpiry = validPurchases.stream()
                                .min((a, b) -> a.getExpiryDate().compareTo(b.getExpiryDate()))
                                .orElse(null);
                        
                        if (earliestExpiry != null) {
                                daysUntilExpiry = ChronoUnit.DAYS.between(now, earliestExpiry.getExpiryDate());
                                expiryDateStr = earliestExpiry.getExpiryDate().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                                log.info("✓ Earliest expiry: {} ({} days remaining)", expiryDateStr, daysUntilExpiry);
                        }
                }

                log.info("=== Balance Response: Available={}, Status={}, DaysUntilExpiry={}", 
                        availableCredits, subscriptionStatus, daysUntilExpiry);

                return CreditBalanceResponse.builder()
                                .availableCredits(availableCredits)
                                .totalCreditsUsed(totalUsed != null ? totalUsed : 0L)
                                .totalCreditsPurchased(availableCredits)
                                .lastUpdated(user.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                                .isMonthly(!validPurchases.isEmpty())
                                .subscriptionStatus(subscriptionStatus)
                                .creditsExpiryDate(expiryDateStr)
                                .daysUntilExpiry(daysUntilExpiry)
                                .build();
        }

        public List<CreditPackageResponse> getAvailablePackages() {
                return Arrays.asList(
                                CreditPackageResponse.builder()
                                                .id("starter")
                                                .name("Starter Pack")
                                                .description("50 credits per month - Perfect for trying out our service")
                                                .credits(50L)
                                                .price(new BigDecimal("9.99"))
                                                .currency("USD")
                                                .popular(false)
                                                .features("50 conversions/month, Basic support, 30-day expiry")
                                                .billingPeriod("MONTHLY")
                                                .build(),
                                CreditPackageResponse.builder()
                                                .id("professional")
                                                .name("Professional Pack")
                                                .description("200 credits per month - Best value for regular users")
                                                .credits(200L)
                                                .price(new BigDecimal("29.99"))
                                                .currency("USD")
                                                .popular(true)
                                                .features("200 conversions/month, Priority support, Advanced features, 30-day expiry")
                                                .billingPeriod("MONTHLY")
                                                .build(),
                                CreditPackageResponse.builder()
                                                .id("enterprise")
                                                .name("Enterprise Pack")
                                                .description("500 credits per month - For heavy users and teams")
                                                .credits(500L)
                                                .price(new BigDecimal("69.99"))
                                                .currency("USD")
                                                .popular(false)
                                                .features("500 conversions/month, Dedicated support, Custom integrations, 30-day expiry")
                                                .billingPeriod("MONTHLY")
                                                .build());
        }

        @Transactional
        public PurchaseCreditsResponse purchaseCredits(String email, String packageId) {
                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new RuntimeException("User not found: " + email));

                CreditPackageResponse selectedPackage = getAvailablePackages().stream()
                                .filter(pkg -> pkg.getId().equals(packageId))
                                .findFirst()
                                .orElseThrow(() -> new RuntimeException("Invalid package: " + packageId));

                try {
                        long amountCents = selectedPackage.getPrice().multiply(new java.math.BigDecimal(100))
                                        .longValue();
                        String checkoutUrl = stripeService.createCheckoutSession(
                                        user, selectedPackage.getName(), selectedPackage.getCredits(), amountCents);

                        log.info("Created checkout session for user: {}, package: {}", email, packageId);

                        return PurchaseCreditsResponse.builder()
                                        .success(true)
                                        .message("Redirecting to checkout...")
                                        .checkoutUrl(checkoutUrl)
                                        .build();
                } catch (Exception e) {
                        log.error("Failed to create Stripe session: {}", e.getMessage());
                        throw new RuntimeException("Payment gateway initialization failed");
                }
        }

        @Transactional
        public void addPurchasedCredits(User user, long amount, String transactionId) {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime expiryDate = now.plusDays(30);
                
                log.info("Starting credit purchase for user: {}", user.getEmail());
                log.info("Amount: {} credits, Expiry: {}", amount, expiryDate);
                
                // Create a new credit purchase record with 30-day expiry
                CreditPurchase creditPurchase = CreditPurchase.builder()
                                .user(user)
                                .creditAmount(amount)
                                .transactionId(transactionId)
                                .purchasedAt(now)
                                .expiryDate(expiryDate)
                                .expired(false)
                                .build();
                
                CreditPurchase savedPurchase = creditPurchaseRepository.save(creditPurchase);
                log.info("✓ Credit purchase saved to database - ID: {}, Amount: {}, Expires: {}", 
                        savedPurchase.getId(), amount, expiryDate);

                // Record in usage ledger
                CreditUsage usage = CreditUsage.builder()
                                .user(user)
                                .creditsUsed(0L)
                                .serviceType("PURCHASE")
                                .description("Credit Purchase: " + amount + " credits (Session: " + transactionId + ")")
                                .createdAt(now)
                                .build();
                creditUsageRepository.save(usage);
                log.info("✓ Recorded in usage ledger - Transaction ID: {}", transactionId);

                log.info("Fulfilled purchase: {} credits for user: {}. Transaction: {}",
                                amount, user.getEmail(), transactionId);

                // Email sending disabled
                // try {
                //         String packageName = determinePackageName(amount);
                //         BigDecimal packagePrice = determinePackagePrice(amount);
                //         
                //         log.info("Sending admin notification for purchase: Package={}, Amount=${}", packageName, packagePrice);
                //         
                //         // Send admin notification
                //         emailService.sendAdminPurchaseNotification(
                //                 user.getEmail(),
                //                 amount,
                //                 packageName,
                //                 packagePrice
                //         );
                //         log.info("✓ Sent admin purchase notification");
                //         
                //         log.info("✓ Purchase notification email sent for transaction: {}", transactionId);
                // } catch (Exception e) {
                //         log.error("⚠ Failed to send purchase notification email for transaction {}: {}",
                //                 transactionId, e.getMessage(), e);
                //         // Don't throw exception - email is not critical to credit fulfillment
                // }
        }

        private String determinePackageName(long credits) {
                return switch ((int) credits) {
                        case 50 -> "Starter Pack - 50 credits/month";
                        case 200 -> "Professional Pack - 200 credits/month";
                        case 500 -> "Enterprise Pack - 500 credits/month";
                        default -> "Custom Package (" + credits + " credits)";
                };
        }

        private BigDecimal determinePackagePrice(long credits) {
                return switch ((int) credits) {
                        case 50 -> new BigDecimal("9.99");
                        case 200 -> new BigDecimal("29.99");
                        case 500 -> new BigDecimal("69.99");
                        default -> BigDecimal.ZERO;
                };
        }

        public CreditUsageResponse getCreditUsage(String email) {
                // Handle mock user for development
                if ("mock@example.com".equals(email)) {
                        return CreditUsageResponse.builder()
                                        .totalCreditsUsed(0L) // Mock usage data
                                        .creditsUsedThisMonth(0L)
                                        .creditsUsedToday(0L)
                                        .recentUsage(Arrays.asList(
                                                        CreditUsageResponse.UsageRecord.builder()
                                                                        .id(UUID.randomUUID())
                                                                        .creditsUsed(1L)
                                                                        .serviceType("MT_TO_MX")
                                                                        .description("Converted MT103 to MX")
                                                                        .createdAt(LocalDateTime.now().minusHours(2))
                                                                        .build(),
                                                        CreditUsageResponse.UsageRecord.builder()
                                                                        .id(UUID.randomUUID())
                                                                        .creditsUsed(1L)
                                                                        .serviceType("MX_TO_MT")
                                                                        .description("Converted MX to MT202")
                                                                        .createdAt(LocalDateTime.now().minusDays(1))
                                                                        .build()))
                                        .build();
                }

                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new RuntimeException("User not found: " + email));

                LocalDateTime now = LocalDateTime.now();
                LocalDateTime startOfMonth = now.with(TemporalAdjusters.firstDayOfMonth()).withHour(0).withMinute(0)
                                .withSecond(0);
                LocalDateTime startOfDay = now.withHour(0).withMinute(0).withSecond(0);

                Long totalUsed = creditUsageRepository.getTotalCreditsUsedByUser(user);
                Long monthlyUsed = creditUsageRepository.getCreditsUsedByUserSince(user, startOfMonth);
                Long dailyUsed = creditUsageRepository.getCreditsUsedByUserSince(user, startOfDay);

                List<CreditUsage> recentUsageList = creditUsageRepository.findRecentUsageByUser(user);
                List<CreditUsageResponse.UsageRecord> usageRecords = recentUsageList.stream()
                                .limit(10)
                                .map(usage -> CreditUsageResponse.UsageRecord.builder()
                                                .id(usage.getId())
                                                .creditsUsed(usage.getCreditsUsed())
                                                .serviceType(usage.getServiceType())
                                                .description(usage.getDescription())
                                                .createdAt(usage.getCreatedAt())
                                                .build())
                                .collect(Collectors.toList());

                return CreditUsageResponse.builder()
                                .totalCreditsUsed(totalUsed != null ? totalUsed : 0L)
                                .creditsUsedThisMonth(monthlyUsed != null ? monthlyUsed : 0L)
                                .creditsUsedToday(dailyUsed != null ? dailyUsed : 0L)
                                .recentUsage(usageRecords)
                                .build();
        }

        @Transactional
        public void recordCreditUsage(User user, Long creditsUsed, String serviceType, String description,
                        Conversion conversion) {
                if (user == null) {
                        log.warn("Attempted to record credit usage for null user");
                        return;
                }

                LocalDateTime now = LocalDateTime.now();
                List<CreditPurchase> validPurchases = creditPurchaseRepository.findValidPurchases(user, now);
                
                // Sort by expiry date (oldest first - use those first)
                validPurchases.sort((a, b) -> a.getExpiryDate().compareTo(b.getExpiryDate()));
                
                long remainingCreditsToDeduct = creditsUsed;
                
                // Deduct from purchases one by one (oldest expiring first)
                for (CreditPurchase purchase : validPurchases) {
                        if (remainingCreditsToDeduct <= 0) {
                                break;
                        }
                        
                        long deductFromThisPurchase = Math.min(remainingCreditsToDeduct, purchase.getCreditAmount());
                        purchase.setCreditAmount(purchase.getCreditAmount() - deductFromThisPurchase);
                        
                        if (purchase.getCreditAmount() <= 0) {
                                purchase.setExpired(true);
                                log.info("Credit purchase fully consumed and marked expired for user: {}", user.getEmail());
                        }
                        
                        creditPurchaseRepository.save(purchase);
                        remainingCreditsToDeduct -= deductFromThisPurchase;
                        log.info("Deducted {} credits from purchase {} (remaining: {})", 
                                deductFromThisPurchase, purchase.getId(), purchase.getCreditAmount());
                }
                
                if (remainingCreditsToDeduct > 0) {
                        log.warn("User {} tried to use {} credits but only had {} available",
                                user.getEmail(), creditsUsed, creditsUsed - remainingCreditsToDeduct);
                }

                // Record in ledger
                CreditUsage usage = CreditUsage.builder()
                                .user(user)
                                .creditsUsed(creditsUsed)
                                .serviceType(serviceType)
                                .description(description)
                                .conversion(conversion)
                                .createdAt(LocalDateTime.now())
                                .build();
                
                creditUsageRepository.save(usage);
                log.info("Recorded usage: {} credits for user: {}", creditsUsed, user.getEmail());
        }
}
