package com.mtsaas.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "credit_usage", indexes = {
        @Index(name = "idx_credit_usage_user_id", columnList = "user_id"),
        @Index(name = "idx_credit_usage_conversion_id", columnList = "conversion_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private Long creditsUsed;

    @Column(name = "service_type", nullable = false)
    private String serviceType; // e.g., "MT_TO_MX", "MX_TO_MT"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversion_id")
    private Conversion conversion;

    @Column(nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "description")
    private String description;
}
