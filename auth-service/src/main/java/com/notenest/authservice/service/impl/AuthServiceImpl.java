package com.notenest.authservice.service.impl;

import com.notenest.authservice.dto.request.LoginRequest;
import com.notenest.authservice.dto.request.RegisterRequest;
import com.notenest.authservice.dto.response.AuthResponse;
import com.notenest.authservice.entity.Role;
import com.notenest.authservice.entity.User;
import com.notenest.authservice.exception.EmailAlreadyExistsException;
import com.notenest.authservice.repository.UserRepository;
import com.notenest.authservice.security.JwtService;
import com.notenest.authservice.service.AuthService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("Email already registered: " + request.getEmail());
        }

        User user = User.builder()
                .displayName(request.getDisplayName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .enabled(true)
                .build();

        userRepository.save(user);

        // Tidak ada panggilan ke user-service di sini. Profil dibuat oleh
        // user-service sendiri saat pertama kali diakses, dari klaim yang
        // sudah ada di token - jadi register tidak bisa gagal separuh jalan.
        return toAuthResponse(user);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        // Melempar BadCredentialsException kalau email/password salah;
        // dipetakan jadi 401 di GlobalExceptionHandler.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        User user = userRepository.findByEmail(request.getEmail()).orElseThrow();

        return toAuthResponse(user);
    }

    private AuthResponse toAuthResponse(User user) {
        return AuthResponse.builder()
                .accessToken(jwtService.generateToken(user))
                .userId(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole().name())
                .build();
    }
}
