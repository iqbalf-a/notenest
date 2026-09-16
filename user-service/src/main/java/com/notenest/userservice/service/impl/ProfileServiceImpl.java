package com.notenest.userservice.service.impl;

import com.notenest.userservice.dto.request.UpdateProfileRequest;
import com.notenest.userservice.dto.response.ProfileResponse;
import com.notenest.userservice.dto.response.UserSummaryResponse;
import com.notenest.userservice.entity.Profile;
import com.notenest.userservice.exception.ProfileNotFoundException;
import com.notenest.userservice.repository.ProfileRepository;
import com.notenest.userservice.service.ProfileService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final ProfileRepository profileRepository;

    @Override
    @Transactional
    public ProfileResponse getMyProfile(UUID userId, String email, String displayName) {
        return toResponse(findOrCreate(userId, email, displayName));
    }

    @Override
    @Transactional
    public ProfileResponse updateMyProfile(UUID userId, String email, String displayName,
                                           UpdateProfileRequest request) {
        Profile profile = findOrCreate(userId, email, displayName);

        profile.setDisplayName(request.getDisplayName());
        profile.setBio(request.getBio());
        profile.setAvatarUrl(request.getAvatarUrl());

        return toResponse(profileRepository.save(profile));
    }

    @Override
    public List<UserSummaryResponse> searchByEmail(UUID requesterId, String email) {
        if (email == null || email.isBlank()) {
            return List.of();
        }

        // Diri sendiri dibuang dari hasil: tidak ada gunanya men-share catatan
        // ke diri sendiri, dan itu cuma bikin bingung di daftar pilihan.
        return profileRepository.findTop10ByEmailContainingIgnoreCaseOrderByEmailAsc(email.trim()).stream()
                .filter(profile -> !profile.getUserId().equals(requesterId))
                .map(this::toSummary)
                .toList();
    }

    @Override
    public UserSummaryResponse getByUserId(UUID userId) {
        return toSummary(profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ProfileNotFoundException("Profile not found for user: " + userId)));
    }

    @Override
    public UserSummaryResponse getByEmail(String email) {
        return toSummary(profileRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ProfileNotFoundException("No user found with email: " + email)));
    }

    // Profil dibuat saat pertama kali dibutuhkan, dari identitas yang sudah
    // terbukti benar (gateway yang mengisi X-User-*). Konsekuensinya: register
    // di auth-service tidak perlu memanggil service ini, jadi tidak ada
    // kemungkinan "user terdaftar tapi profilnya gagal dibuat".
    private Profile findOrCreate(UUID userId, String email, String displayName) {
        return profileRepository.findByUserId(userId)
                .orElseGet(() -> profileRepository.save(Profile.builder()
                        .userId(userId)
                        .email(email)
                        .displayName(displayName)
                        .build()));
    }

    private ProfileResponse toResponse(Profile profile) {
        return ProfileResponse.builder()
                .id(profile.getId())
                .userId(profile.getUserId())
                .email(profile.getEmail())
                .displayName(profile.getDisplayName())
                .bio(profile.getBio())
                .avatarUrl(profile.getAvatarUrl())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    private UserSummaryResponse toSummary(Profile profile) {
        return UserSummaryResponse.builder()
                .userId(profile.getUserId())
                .email(profile.getEmail())
                .displayName(profile.getDisplayName())
                .build();
    }
}
