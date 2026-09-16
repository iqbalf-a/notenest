package com.notenest.userservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

// Versi ringkas sebuah user: dipakai hasil pencarian (frontend) dan endpoint
// internal (note-service lewat Feign). Sengaja tidak membawa bio/avatar -
// pemanggil hanya butuh "siapa ini", bukan seluruh profil.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserSummaryResponse {
    private UUID userId;
    private String email;
    private String displayName;
}
