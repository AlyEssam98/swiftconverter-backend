package com.mtsaas.backend.infrastructure.repository;

import com.mtsaas.backend.domain.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {
    List<Feedback> findByUserEmailOrderByCreatedAtDesc(String email);
    List<Feedback> findByStatusOrderByCreatedAtDesc(Feedback.FeedbackStatus status);
    List<Feedback> findAllByOrderByCreatedAtDesc();
}
