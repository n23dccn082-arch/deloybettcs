package com.taskmanager.service;

import com.taskmanager.dto.request.*;
import com.taskmanager.dto.response.*;
import com.taskmanager.entity.PasswordResetToken;
import com.taskmanager.entity.User;
import com.taskmanager.exception.BadRequestException;
import com.taskmanager.exception.ConflictException;
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
    private final EmailService emailService;

    public AuthResponse register(RegisterRequest request) {
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
        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(token, toSummary(user));
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new BadCredentialsException("Email hoặc mật khẩu không đúng"));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BadCredentialsException("Email hoặc mật khẩu không đúng");
        }
        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(token, toSummary(user));
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.email()).ifPresent(user -> {
            resetTokenRepository.deleteByUserId(user.getId());
            byte[] bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            String token = HexFormat.of().formatHex(bytes);
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

    private UserSummary toSummary(User u) {
        return new UserSummary(u.getId(), u.getUsername(), u.getEmail(), u.getAvatarUrl());
    }
}
