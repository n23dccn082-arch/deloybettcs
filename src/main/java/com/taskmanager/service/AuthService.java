package com.taskmanager.service;

import com.taskmanager.dto.request.*;
import com.taskmanager.dto.response.*;
import com.taskmanager.entity.EmailVerificationToken;
import com.taskmanager.entity.PasswordResetToken;
import com.taskmanager.entity.User;
import com.taskmanager.exception.BadRequestException;
import com.taskmanager.exception.ConflictException;
import com.taskmanager.repository.EmailVerificationTokenRepository;
import com.taskmanager.repository.PasswordResetTokenRepository;
import com.taskmanager.repository.UserRepository;
import com.taskmanager.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int VERIFICATION_HOURS = 24;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final EmailVerificationTokenRepository verifyTokenRepository;
    private final EmailService emailService;
    private final DatabaseSchemaMigrationService schemaMigrationService;

    @Transactional
    public void register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        String username = request.username().trim();

        ensureVerifiedEmailAvailable(email);
        ensureVerifiedUsernameAvailable(username);
        removeLegacyUnverifiedUser(email, username);

        verifyTokenRepository.findByEmailIgnoreCase(email).ifPresent(verifyTokenRepository::delete);

        verifyTokenRepository.findByUsernameIgnoreCase(username).ifPresent(existing -> {
            if (!existing.isExpired() && !existing.getEmail().equalsIgnoreCase(email)) {
                throw new ConflictException("Username đã được sử dụng");
            }
            verifyTokenRepository.delete(existing);
        });

        String token = generateHexToken();
        verifyTokenRepository.save(EmailVerificationToken.builder()
            .token(token)
            .username(username)
            .email(email)
            .passwordHash(passwordEncoder.encode(request.password()))
            .expiresAt(LocalDateTime.now().plusHours(VERIFICATION_HOURS))
            .build());
        emailService.sendVerificationEmail(email, token);
    }

    @Transactional
    public AuthResponse verifyEmail(String token) {
        EmailVerificationToken pending = verifyTokenRepository.findByToken(token)
            .orElseThrow(() -> new BadRequestException("Link xác nhận không hợp lệ hoặc đã hết hạn"));
        if (pending.isExpired()) {
            verifyTokenRepository.delete(pending);
            throw new BadRequestException("Link xác nhận đã hết hạn");
        }

        String email = pending.getEmail();
        String username = pending.getUsername();

        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.isEmailVerified()) {
                throw new ConflictException("Email đã được sử dụng");
            }
            if (userRepository.hasUserDependencies(user.getId())) {
                throw new BadRequestException("Không thể xác nhận email do tài khoản cũ chưa được dọn dẹp");
            }
            schemaMigrationService.deleteLegacyTokensForUser(user.getId());
            schemaMigrationService.deleteLegacyTokensForUser(user.getId());
            userRepository.delete(user);
        });

        userRepository.findByUsername(username).ifPresent(user -> {
            if (user.isEmailVerified()) {
                throw new ConflictException("Username đã được sử dụng");
            }
            if (!user.getEmail().equalsIgnoreCase(email)) {
                if (userRepository.hasUserDependencies(user.getId())) {
                    throw new ConflictException("Username đã được sử dụng");
                }
                schemaMigrationService.deleteLegacyTokensForUser(user.getId());
                userRepository.delete(user);
            }
        });

        User user = User.builder()
            .username(username)
            .email(email)
            .password(pending.getPasswordHash())
            .emailVerified(true)
            .build();
        user = userRepository.save(user);
        verifyTokenRepository.delete(pending);

        String jwt = jwtTokenProvider.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(jwt, toSummary(user));
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
            .orElseThrow(() -> new BadCredentialsException("Email hoặc mật khẩu không đúng"));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BadCredentialsException("Email hoặc mật khẩu không đúng");
        }
        if (!user.isEmailVerified()) {
            throw new BadRequestException("Vui lòng xác nhận email trước khi đăng nhập");
        }
        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(token, toSummary(user));
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(normalizeEmail(request.email())).ifPresent(user -> {
            if (!user.isEmailVerified()) {
                return;
            }
            resetTokenRepository.deleteByUserId(user.getId());
            String token = generateHexToken();
            resetTokenRepository.save(PasswordResetToken.builder()
                .token(token)
                .user(user)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .build());
            emailService.sendPasswordResetEmail(user.getEmail(), token);
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = resetTokenRepository.findByToken(request.token())
            .orElseThrow(() -> new BadRequestException("Token không hợp lệ hoặc đã hết hạn"));
        if (resetToken.isExpired() || resetToken.isUsed()) {
            throw new BadRequestException("Token đã hết hạn hoặc đã được sử dụng");
        }
        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        resetToken.setUsed(true);
        resetTokenRepository.save(resetToken);
    }

    private void ensureVerifiedEmailAvailable(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.isEmailVerified()) {
                throw new ConflictException("Email đã được sử dụng");
            }
        });
    }

    private void ensureVerifiedUsernameAvailable(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            if (user.isEmailVerified()) {
                throw new ConflictException("Username đã được sử dụng");
            }
        });
    }

    private void removeLegacyUnverifiedUser(String email, String username) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (!user.isEmailVerified() && !userRepository.hasUserDependencies(user.getId())) {
                schemaMigrationService.deleteLegacyTokensForUser(user.getId());
                userRepository.delete(user);
            }
        });
        userRepository.findByUsername(username).ifPresent(user -> {
            if (!user.isEmailVerified()
                && !user.getEmail().equalsIgnoreCase(email)
                && !userRepository.hasUserDependencies(user.getId())) {
                schemaMigrationService.deleteLegacyTokensForUser(user.getId());
                userRepository.delete(user);
            }
        });
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String generateHexToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private UserSummary toSummary(User u) {
        return new UserSummary(u.getId(), u.getUsername(), u.getEmail(), u.getAvatarUrl());
    }
}
