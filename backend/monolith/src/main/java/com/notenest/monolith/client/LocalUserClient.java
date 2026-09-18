package com.notenest.monolith.client;

import com.notenest.noteservice.client.UserClient;
import com.notenest.noteservice.client.dto.ClientApiResponse;
import com.notenest.noteservice.client.dto.UserResponse;
import com.notenest.noteservice.exception.UserNotFoundException;
import com.notenest.userservice.dto.response.UserSummaryResponse;
import com.notenest.userservice.exception.ProfileNotFoundException;
import com.notenest.userservice.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Isi dari UserClient saat tidak ada jaringan di antaranya.
//
// Interface-nya tetap milik note-service, lengkap dengan @FeignClient - tidak
// diubah sebaris pun. Yang berbeda cuma siapa yang mengimplementasikannya:
// tanpa @EnableFeignClients, Spring tidak membuat proxy HTTP apa pun, jadi
// bean bertipe UserClient di context ini hanya kelas ini. NoteServiceImpl
// menyuntiknya seperti biasa dan tidak pernah tahu bedanya.
//
// Dulu: Feign tanya alamat user-service ke Eureka -> HTTP ke /internal/users/**.
// Sekarang: panggil ProfileService langsung - satu proses, satu transaksi,
// tanpa kemungkinan gagal di jaringan.
@Component
@RequiredArgsConstructor
public class LocalUserClient implements UserClient {

    private final ProfileService profileService;

    // Dipakai saat menampilkan "dibagikan oleh ..." di /api/notes/shared-with-me
    @Override
    public ClientApiResponse<UserResponse> getUserById(UUID id) {
        try {
            return found(profileService.getByUserId(id));
        } catch (ProfileNotFoundException ex) {
            // Pesan dibuat sama persis dengan yang dulu dihasilkan NoteServiceImpl
            // saat menerjemahkan FeignException.NotFound, supaya response yang
            // dilihat client tidak berubah.
            throw new UserNotFoundException("Note owner no longer exists: " + id);
        }
    }

    // Dipakai saat share: email tujuan -> userId, sekaligus validasi user ada.
    @Override
    public ClientApiResponse<UserResponse> getUserByEmail(String email) {
        try {
            return found(profileService.getByEmail(email));
        } catch (ProfileNotFoundException ex) {
            throw new UserNotFoundException("No NoteNest user registered with email: " + email);
        }
    }

    // Pemetaan tetap dilakukan meski kedua DTO berbentuk sama: note-service
    // memang tidak pernah memakai class milik user-service, dan itu tidak
    // berubah hanya karena keduanya sekarang satu proses.
    private ClientApiResponse<UserResponse> found(UserSummaryResponse summary) {
        return new ClientApiResponse<>(true, "User found", UserResponse.builder()
                .userId(summary.getUserId())
                .email(summary.getEmail())
                .displayName(summary.getDisplayName())
                .build());
    }
}
