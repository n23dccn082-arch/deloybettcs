package com.taskmanager.service;

import com.taskmanager.dto.request.RegisterRequest;
import com.taskmanager.entity.EmailVerificationToken;
import com.taskmanager.entity.User;
import com.taskmanager.exception.ConflictException;
import com.taskmanager.repository.EmailVerificationTokenRepository;
import com.taskmanager.repository.PasswordResetTokenRepository;
import com.taskmanager.repository.UserRepository;
import com.taskmanager.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock PasswordResetTokenRepository resetTokenRepository;
    @Mock EmailVerificationTokenRepository verifyTokenRepository;
    @Mock EmailService emailService;
    @Mock DatabaseSchemaMigrationService schemaMigrationService;
    @InjectMocks AuthService authService;

    @Test
    void register_verifiedEmail_throwsConflict() {
        User verified = User.builder().id(1L).email("test@test.com").emailVerified(true).build();
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(verified));

        RegisterRequest req = new RegisterRequest("user", "test@test.com", "password");
        assertThatThrownBy(() -> authService.register(req))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("Email");
    }

    @Test
    void register_success_savesPendingTokenAndSendsEmail_notUser() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(verifyTokenRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(verifyTokenRepository.findByUsernameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");

        RegisterRequest req = new RegisterRequest("user", "Test@Test.com", "password");
        authService.register(req);

        verify(userRepository, never()).save(any());
        verify(verifyTokenRepository).save(argThat(token ->
            token.getUsername().equals("user")
                && token.getEmail().equals("test@test.com")
                && token.getPasswordHash().equals("hashed")
        ));
        verify(emailService).sendVerificationEmail(eq("test@test.com"), anyString());
    }

    @Test
    void register_replacesPendingRegistrationForSameEmail() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("new-hashed");

        EmailVerificationToken existing = EmailVerificationToken.builder()
            .id(5L)
            .token("old")
            .username("olduser")
            .email("test@test.com")
            .passwordHash("old-hashed")
            .expiresAt(java.time.LocalDateTime.now().plusHours(12))
            .build();
        when(verifyTokenRepository.findByEmailIgnoreCase("test@test.com")).thenReturn(Optional.of(existing));
        when(verifyTokenRepository.findByUsernameIgnoreCase("newuser")).thenReturn(Optional.empty());

        RegisterRequest req = new RegisterRequest("newuser", "test@test.com", "newpass");
        authService.register(req);

        verify(verifyTokenRepository).delete(existing);
        verify(verifyTokenRepository).save(any());
        verify(userRepository, never()).save(any());
    }
}
