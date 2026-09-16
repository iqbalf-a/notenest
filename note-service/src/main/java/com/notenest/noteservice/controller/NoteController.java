package com.notenest.noteservice.controller;

import com.notenest.noteservice.dto.request.NoteRequest;
import com.notenest.noteservice.dto.request.ShareNoteRequest;
import com.notenest.noteservice.dto.response.ApiResponse;
import com.notenest.noteservice.dto.response.NoteResponse;
import com.notenest.noteservice.dto.response.PageResponse;
import com.notenest.noteservice.dto.response.ShareResponse;
import com.notenest.noteservice.service.NoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// X-User-Id diisi gateway dari klaim JWT, dan gateway membuang header X-User-*
// kiriman client - jadi tidak ada endpoint di sini yang menerima userId dari
// body atau query. Cek kepemilikan sendiri ada di service layer.
@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @PostMapping
    public ResponseEntity<ApiResponse<NoteResponse>> createNote(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody NoteRequest request) {
        NoteResponse response = noteService.createNote(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Note created", response));
    }

    // ?page=0&size=10&sort=updatedAt,desc&tag=java&q=spring
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NoteResponse>>> getMyNotes(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 10, sort = "updatedAt") Pageable pageable) {
        PageResponse<NoteResponse> response = noteService.getMyNotes(userId, tag, q, pageable);
        return ResponseEntity.ok(ApiResponse.success("Notes found", response));
    }

    // Didaftarkan SEBELUM /{id}: kalau tidak, "shared-with-me" akan ditangkap
    // sebagai {id} dan gagal di-parse jadi UUID.
    @GetMapping("/shared-with-me")
    public ResponseEntity<ApiResponse<List<NoteResponse>>> getNotesSharedWithMe(
            @RequestHeader("X-User-Id") UUID userId) {
        List<NoteResponse> response = noteService.getNotesSharedWithMe(userId);
        return ResponseEntity.ok(ApiResponse.success("Shared notes found", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NoteResponse>> getNote(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID id) {
        NoteResponse response = noteService.getNoteById(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Note found", response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<NoteResponse>> updateNote(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody NoteRequest request) {
        NoteResponse response = noteService.updateNote(id, userId, request);
        return ResponseEntity.ok(ApiResponse.success("Note updated", response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNote(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID id) {
        noteService.deleteNote(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Note deleted", null));
    }

    @GetMapping("/{id}/shares")
    public ResponseEntity<ApiResponse<List<ShareResponse>>> getShares(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID id) {
        List<ShareResponse> response = noteService.getShares(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Shares found", response));
    }

    // Satu-satunya endpoint yang memicu panggilan Feign ke user-service:
    // email tujuan harus diterjemahkan jadi userId lebih dulu.
    @PostMapping("/{id}/share")
    public ResponseEntity<ApiResponse<ShareResponse>> shareNote(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody ShareNoteRequest request) {
        ShareResponse response = noteService.shareNote(id, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Note shared", response));
    }

    @DeleteMapping("/{id}/share/{userId}")
    public ResponseEntity<ApiResponse<Void>> revokeShare(
            @RequestHeader("X-User-Id") UUID requesterId,
            @PathVariable UUID id,
            @PathVariable UUID userId) {
        noteService.revokeShare(id, requesterId, userId);
        return ResponseEntity.ok(ApiResponse.success("Share revoked", null));
    }
}
