package com.taskmanager.service;

import com.taskmanager.repository.EmailVerificationTokenRepository;
import com.taskmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationCleanupService {

    private static final int VERIFICATION_HOURS = 24;

    private final EmailVerificationTokenRepository verifyTokenRepository;
    private final UserRepository userRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    @Transactional
    public void onStartup() {
        try {
            int legacyTokens = verifyTokenRepository.deleteLegacyRows();
            if (legacyTokens > 0) {
                log.info("Removed {} legacy email verification token(s)", legacyTokens);
            }
            cleanupExpired();
        } catch (Exception e) {
            log.warn("Registration cleanup on startup skipped: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpired() {
        LocalDateTime now = LocalDateTime.now();
        int expiredTokens = verifyTokenRepository.deleteByExpiresAtBefore(now);
        int staleUsers = userRepository.deleteOrphanUnverifiedCreatedBefore(now.minusHours(VERIFICATION_HOURS));
        if (expiredTokens > 0 || staleUsers > 0) {
            log.info("Registration cleanup: {} expired token(s), {} stale unverified user(s)",
                expiredTokens, staleUsers);
        }
    }
}
