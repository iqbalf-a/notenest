package com.notenest.userservice.service;

import com.notenest.userservice.dto.request.UpdateProfileRequest;
import com.notenest.userservice.dto.response.ProfileResponse;
import com.notenest.userservice.dto.response.UserSummaryResponse;

import java.util.List;
import java.util.UUID;

public interface ProfileService {

    // email & displayName datang dari klaim token (header X-User-*), bukan dari
    // body request - jadi profil bisa dibuat otomatis saat pertama kali diakses.
    ProfileResponse getMyProfile(UUID userId, String email, String displayName);

    ProfileResponse updateMyProfile(UUID userId, String email, String displayName, UpdateProfileRequest request);

    List<UserSummaryResponse> searchByEmail(UUID requesterId, String email);

    UserSummaryResponse getByUserId(UUID userId);

    UserSummaryResponse getByEmail(String email);
}
