package com.notenest.authservice.service.impl;

import com.notenest.authservice.dto.request.LoginRequest;
import com.notenest.authservice.dto.request.RegisterRequest;
import com.notenest.authservice.dto.response.AuthResponse;
import com.notenest.authservice.entity.Role;
import com.notenest.authservice.entity.User;
import com.notenest.authservice.exception.EmailAlreadyExistsException;
import com.notenest.authservice.repository.UserRepository;
import com.notenest.authservice.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void register_newEmail_savesHashedPasswordAndReturnsToken() {
        RegisterRequest request = new RegisterRequest();
        request.setDisplayName("Iqbal");
        request.setEmail("iqbal@example.com");
        request.setPassword("rahasia123");

        when(userRepository.existsByEmail("iqbal@example.com")).thenReturn(false);
        when(passwordEncoder.encode("rahasia123")).thenReturn("$2a$10$hashed");
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt-token");

        AuthResponse response = authService.register(request);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());

        // Yang disimpan harus hash, bukan password asli
        assertEquals("$2a$10$hashed", saved.getValue().getPassword());
        assertNotEquals("rahasia123", saved.getValue().getPassword());
        assertEquals(Role.USER, saved.getValue().getRole());

        assertEquals("jwt-token", response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals("Iqbal", response.getDisplayName());
        assertEquals("USER", response.getRole());
    }

    @Test
    void register_emailAlreadyTaken_throwsAndSavesNothing() {
        RegisterRequest request = new RegisterRequest();
        request.setDisplayName("Iqbal");
        request.setEmail("iqbal@example.com");
        request.setPassword("rahasia123");

        when(userRepository.existsByEmail("iqbal@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_validCredentials_returnsTokenForThatUser() {
        LoginRequest request = new LoginRequest();
        request.setEmail("iqbal@example.com");
        request.setPassword("rahasia123");

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("iqbal@example.com")
                .password("$2a$10$hashed")
                .displayName("Iqbal")
                .role(Role.USER)
                .build();

        when(userRepository.findByEmail("iqbal@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("jwt-token");

        AuthResponse response = authService.login(request);

        assertEquals("jwt-token", response.getAccessToken());
        assertEquals(user.getId(), response.getUserId());
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void login_wrongPassword_propagatesBadCredentialsAndNeverIssuesToken() {
        LoginRequest request = new LoginRequest();
        request.setEmail("iqbal@example.com");
        request.setPassword("salah");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
        verify(jwtService, never()).generateToken(any());
    }
}
