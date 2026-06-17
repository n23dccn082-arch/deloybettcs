package com.taskmanager.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseSchemaMigrationService {

    private final JdbcTemplate jdbcTemplate;
    private volatile boolean legacyUserIdColumnPresent = true;

    @EventListener(ApplicationReadyEvent.class)
    @Order(0)
    public void migrateEmailVerificationTokens() {
        if (!columnExists("email_verification_tokens", "user_id")) {
            legacyUserIdColumnPresent = false;
            return;
        }

        int removed = jdbcTemplate.update("DELETE FROM email_verification_tokens WHERE user_id IS NOT NULL");
        if (removed > 0) {
            log.info("Removed {} legacy email verification token(s) linked by user_id", removed);
        }

        dropForeignKeysOnColumn("email_verification_tokens", "user_id");
        jdbcTemplate.execute("ALTER TABLE email_verification_tokens DROP COLUMN user_id");
        legacyUserIdColumnPresent = false;
        log.info("Dropped legacy user_id column from email_verification_tokens");
    }

    public void deleteLegacyTokensForUser(Long userId) {
        if (!legacyUserIdColumnPresent || userId == null) {
            return;
        }
        try {
            jdbcTemplate.update("DELETE FROM email_verification_tokens WHERE user_id = ?", userId);
        } catch (Exception e) {
            log.warn("Could not delete legacy tokens for user {}: {}", userId, e.getMessage());
        }
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """,
            Integer.class,
            table,
            column
        );
        return count != null && count > 0;
    }

    private void dropForeignKeysOnColumn(String table, String column) {
        List<Map<String, Object>> constraints = jdbcTemplate.queryForList(
            """
                SELECT CONSTRAINT_NAME
                FROM information_schema.KEY_COLUMN_USAGE
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                  AND REFERENCED_TABLE_NAME IS NOT NULL
                """,
            table,
            column
        );
        for (Map<String, Object> row : constraints) {
            String name = String.valueOf(row.get("CONSTRAINT_NAME"));
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP FOREIGN KEY " + name);
            log.info("Dropped foreign key {} on {}.{}", name, table, column);
        }
    }
}
