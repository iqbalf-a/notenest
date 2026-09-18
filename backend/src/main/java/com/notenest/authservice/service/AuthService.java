package com.notenest.authservice.service;

import com.notenest.authservice.dto.request.LoginRequest;
import com.notenest.authservice.dto.request.RegisterRequest;
import com.notenest.authservice.dto.response.AuthResponse;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}
