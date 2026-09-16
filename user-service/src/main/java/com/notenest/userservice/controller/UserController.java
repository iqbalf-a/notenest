package com.notenest.userservice.controller;

import com.notenest.userservice.dto.request.UpdateProfileRequest;
import com.notenest.userservice.dto.response.ApiResponse;
import com.notenest.userservice.dto.response.ProfileResponse;
import com.notenest.userservice.dto.response.UserSummaryResponse;
import com.notenest.userservice.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Identitas datang dari header X-User-*, yang diisi gateway dari klaim JWT.
// Gateway juga membuang header X-User-* kiriman client, jadi nilainya tepercaya
// dan tidak ada endpoint yang menerima userId dari body/query.
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final ProfileService profileService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<ProfileResponse>> getMyProfile(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Email") String email,
            @RequestHeader("X-User-Name") String displayName) {
        ProfileResponse response = profileService.getMyProfile(userId, email, displayName);
        return ResponseEntity.ok(ApiResponse.success("Profile found", response));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<ProfileResponse>> updateMyProfile(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Email") String email,
            @RequestHeader("X-User-Name") String displayName,
            @Valid @RequestBody UpdateProfileRequest request) {
        ProfileResponse response = profileService.updateMyProfile(userId, email, displayName, request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated", response));
    }

    // Dipakai halaman share di frontend: ketik sebagian email -> pilih orangnya.
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<UserSummaryResponse>>> searchByEmail(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam String email) {
        List<UserSummaryResponse> response = profileService.searchByEmail(userId, email);
        return ResponseEntity.ok(ApiResponse.success("Users found", response));
    }
}
