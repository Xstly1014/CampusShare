package com.campushare.user.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseMigrationRunner {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        log.info("Running database migrations...");
        addCreatorLevelColumn();
        addVerificationTypeColumn();
        addReviewNoteColumn();
        updateExistingCreatorsLevel();
        log.info("Database migrations completed");
    }

    private void addCreatorLevelColumn() {
        try {
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN creator_level VARCHAR(20) DEFAULT 'NONE'");
            log.info("Added creator_level column to users table");
        } catch (Exception e) {
            log.debug("creator_level column may already exist: {}", e.getMessage());
        }
    }

    private void addVerificationTypeColumn() {
        try {
            jdbcTemplate.execute("ALTER TABLE creator_verifications ADD COLUMN verification_type VARCHAR(20) DEFAULT 'INITIAL'");
            log.info("Added verification_type column to creator_verifications table");
        } catch (Exception e) {
            log.debug("verification_type column may already exist: {}", e.getMessage());
        }
    }

    private void addReviewNoteColumn() {
        try {
            jdbcTemplate.execute("ALTER TABLE creator_verifications ADD COLUMN review_note VARCHAR(500)");
            log.info("Added review_note column to creator_verifications table");
        } catch (Exception e) {
            log.debug("review_note column may already exist: {}", e.getMessage());
        }
    }

    private void updateExistingCreatorsLevel() {
        try {
            jdbcTemplate.update(
                    "UPDATE users SET creator_level = 'JUNIOR' WHERE role = 'CREATOR' AND (creator_level IS NULL OR creator_level = '' OR creator_level = 'NONE')"
            );
            log.info("Updated existing CREATOR users to JUNIOR level");
        } catch (Exception e) {
            log.warn("Failed to update existing creators level: {}", e.getMessage());
        }
    }
}
