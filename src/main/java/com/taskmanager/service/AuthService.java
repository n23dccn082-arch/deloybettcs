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

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final EmailVerificationTokenRepository verifyTokenRepository;
    private final EmailService emailService;

    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email đã được sử dụng");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username đã được sử dụng");
        }
        User user = User.builder()
            .username(request.username())
            .email(request.email())
            .password(passwordEncoder.encode(request.password()))
            .build();
        user = userRepository.save(user);
        String token = generateHexToken();
        verifyTokenRepository.save(EmailVerificationToken.builder()
            .token(token)
            .user(user)
            .expiresAt(LocalDateTime.now().plusHours(24))
            .build());
        emailService.sendVerificationEmail(user.getEmail(), token);
    }

    @Transactional
    public AuthResponse verifyEmail(String token) {
        EmailVerificationToken vt = verifyTokenRepository.findByToken(token)
            .orElseThrow(() -> new BadRequestException("Link xác nhận không hợp lệ hoặc đã hết hạn"));
        if (vt.isExpired()) {
            throw new BadRequestException("Link xác nhận đã hết hạn");
        }
        User user = vt.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);
        verifyTokenRepository.delete(vt);
        String jwt = jwtTokenProvider.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(jwt, toSummary(user));
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
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
        userRepository.findByEmail(request.email()).ifPresent(user -> {
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

    private String generateHexToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private UserSummary toSummary(User u) {
        return new UserSummary(u.getId(), u.getUsername(), u.getEmail(), u.getAvatarUrl());
    }
}
