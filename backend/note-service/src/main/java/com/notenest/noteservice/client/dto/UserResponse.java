package com.notenest.noteservice.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

// Salinan milik note-service - hanya field yang dibutuhkan.
// Antar service TIDAK berbagi class: loose coupling. Kalau user-service
// menambah field, service ini tidak perlu ikut berubah.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {
    private UUID userId;
    private String email;
    private String displayName;
}
