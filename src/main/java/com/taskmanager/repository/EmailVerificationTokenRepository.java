package com.taskmanager.repository;

import com.taskmanager.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    Optional<EmailVerificationToken> findByToken(String token);

    Optional<EmailVerificationToken> findByEmailIgnoreCase(String email);

    Optional<EmailVerificationToken> findByUsernameIgnoreCase(String username);

    @Modifying
    @Query("DELETE FROM EmailVerificationToken e WHERE e.expiresAt < :cutoff")
    int deleteByExpiresAtBefore(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query("DELETE FROM EmailVerificationToken e WHERE e.email IS NULL")
    int deleteLegacyRows();
}
