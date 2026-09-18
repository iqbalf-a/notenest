package com.notenest.noteservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NoteResponse {

    private UUID id;
    private UUID ownerId;
    private String title;
    private String content;
    private Set<String> tags;

    // true kalau yang meminta adalah pemiliknya. Frontend memakai ini untuk
    // menyembunyikan tombol edit/hapus pada note yang cuma dibagikan.
    private boolean owned;

    // Hanya terisi di /api/notes/shared-with-me, hasil lookup lewat UserClient ke
    // user-service - supaya kelihatan "dibagikan oleh siapa". Di endpoint lain
    // null, karena note-nya milik sendiri dan lookup-nya cuma buang waktu.
    private String ownerEmail;
    private String ownerDisplayName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
