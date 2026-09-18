package com.notenest.common.client;

import com.notenest.noteservice.client.UserClient;
import com.notenest.noteservice.client.UserResponse;
import com.notenest.userservice.dto.response.UserSummaryResponse;
import com.notenest.userservice.exception.ProfileNotFoundException;
import com.notenest.userservice.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

// Satu-satunya tempat di aplikasi ini di mana sisi note bertemu sisi user.
//
// Di branch dev, UserClient dipenuhi proxy Feign: alamat user-service
// ditanyakan ke Eureka, lalu HTTP ke /internal/users/**. Di sini pengisinya
// panggilan method biasa ke ProfileService - satu proses, satu transaksi,
// tanpa kemungkinan gagal di jaringan.
//
// Diletakkan di common, bukan di dalam paket note atau user: kelas ini milik
// perakitannya, bukan milik salah satu domain. Note tetap tidak tahu apa-apa
// tentang ProfileService, dan user tetap tidak tahu ada yang memakainya.
@Component
@RequiredArgsConstructor
public class LocalUserClient implements UserClient {

    private final ProfileService profileService;

    @Override
    public Optional<UserResponse> findById(UUID id) {
        try {
            return Optional.of(toUserResponse(profileService.getByUserId(id)));
        } catch (ProfileNotFoundException ex) {
            // ProfileService memakai exception untuk "tidak ada"; kontrak
            // UserClient memakai Optional. Penerjemahannya berhenti di sini.
            return Optional.empty();
        }
    }

    @Override
    public Optional<UserResponse> findByEmail(String email) {
        try {
            return Optional.of(toUserResponse(profileService.getByEmail(email)));
        } catch (ProfileNotFoundException ex) {
            return Optional.empty();
        }
    }

    // Pemetaan tetap dilakukan meski kedua DTO berbentuk sama: note memang
    // tidak pernah memakai class milik user, dan itu tidak berubah hanya
    // karena keduanya sekarang satu proses.
    private UserResponse toUserResponse(UserSummaryResponse summary) {
        return UserResponse.builder()
                .userId(summary.getUserId())
                .email(summary.getEmail())
                .displayName(summary.getDisplayName())
                .build();
    }
}
