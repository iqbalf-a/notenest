package com.notenest.userservice.controller;

import com.notenest.userservice.dto.response.ApiResponse;
import com.notenest.userservice.dto.response.UserSummaryResponse;
import com.notenest.userservice.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// INTERNAL - hanya dipanggil note-service lewat Feign (lb://user-service),
// langsung ke service tanpa melewati gateway. Karena itu di sini TIDAK ada
// header X-User-* untuk diperiksa: pemanggilnya service, bukan orang.
//
// Pintu dari luar ditutup di gateway (JwtAuthFilter.INTERNAL_PATTERNS),
// bukan di sini. Batasannya memang cuma di gateway - di Compose service ini
// tidak punya port yang di-expose ke host, jadi tidak terjangkau dari luar.
// Solusi yang lebih kuat: token service-to-service atau mTLS.
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final ProfileService profileService;

    // Dipakai saat note-service perlu menampilkan siapa pemilik sebuah catatan
    // yang dibagikan ("dibagikan oleh ...").
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserSummaryResponse>> getByUserId(@PathVariable UUID id) {
        UserSummaryResponse response = profileService.getByUserId(id);
        return ResponseEntity.ok(ApiResponse.success("User found", response));
    }

    // Dipakai saat share: mengubah email tujuan jadi userId, sekaligus memastikan
    // usernya memang ada. 404 dari sini yang membuat share gagal dengan jelas.
    @GetMapping("/by-email")
    public ResponseEntity<ApiResponse<UserSummaryResponse>> getByEmail(@RequestParam String email) {
        UserSummaryResponse response = profileService.getByEmail(email);
        return ResponseEntity.ok(ApiResponse.success("User found", response));
    }
}
