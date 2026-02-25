package com.mtsaas.backend.infrastructure.repository;

import com.mtsaas.backend.domain.CreditUsage;
import com.mtsaas.backend.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CreditUsageRepository extends JpaRepository<CreditUsage, java.util.UUID> {
    
    List<CreditUsage> findByUserOrderByCreatedAtDesc(User user);
    
    @Query("SELECT COALESCE(SUM(cu.creditsUsed), 0) FROM CreditUsage cu WHERE cu.user = :user")
    Long getTotalCreditsUsedByUser(@Param("user") User user);
    
    @Query("SELECT COALESCE(SUM(cu.creditsUsed), 0) FROM CreditUsage cu WHERE cu.user = :user AND cu.createdAt >= :startDate")
    Long getCreditsUsedByUserSince(@Param("user") User user, @Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT cu.serviceType, COUNT(cu), SUM(cu.creditsUsed) FROM CreditUsage cu WHERE cu.user = :user GROUP BY cu.serviceType")
    List<Object[]> getUsageSummaryByServiceType(@Param("user") User user);
    
    @Query("SELECT cu FROM CreditUsage cu WHERE cu.user = :user ORDER BY cu.createdAt DESC")
    List<CreditUsage> findRecentUsageByUser(@Param("user") User user);
}
