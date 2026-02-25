package com.mtsaas.backend.infrastructure.database;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSchemaFixer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        log.info("Checking and fixing database schema constraints...");
        try {
            jdbcTemplate.execute("ALTER TABLE conversions ALTER COLUMN user_id DROP NOT NULL");
            log.info("Successfully dropped NOT NULL constraint on conversions.user_id");
        } catch (Exception e) {
            log.debug("Constraint fix skipped or already applied: {}", e.getMessage());
        }
    }
}
