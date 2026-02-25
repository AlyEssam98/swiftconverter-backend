package com.mtsaas.backend.infrastructure.repository;

import com.mtsaas.backend.domain.ContactUs;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ContactUsRepository extends JpaRepository<ContactUs, UUID> {
    List<ContactUs> findByUserEmailOrderByCreatedAtDesc(String email);
    List<ContactUs> findByStatusOrderByCreatedAtDesc(ContactUs.ContactUsStatus status);
    List<ContactUs> findAllByOrderByCreatedAtDesc();
}
