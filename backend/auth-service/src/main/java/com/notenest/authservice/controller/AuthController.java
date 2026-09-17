package com.notenest.authservice.controller;

import com.notenest.authservice.dto.request.LoginRequest;
import com.notenest.authservice.dto.request.RegisterRequest;
import com.notenest.authservice.dto.response.ApiResponse;
import com.notenest.authservice.dto.response.AuthResponse;
import com.notenest.authservice.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    // Dipakai frontend untuk mengecek token yang tersimpan masih hidup atau tidak.
    // Kalau token tidak valid, request tidak pernah sampai ke sini (ditolak filter).
    @GetMapping("/validate")
    public ResponseEntity<ApiResponse<Void>> validate() {
        return ResponseEntity.ok(ApiResponse.success("Token is valid", null));
    }
}
