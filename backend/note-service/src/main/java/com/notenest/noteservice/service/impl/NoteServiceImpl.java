
package com.notenest.noteservice.service.impl;

import com.notenest.noteservice.client.UserClient;
import com.notenest.noteservice.client.dto.UserResponse;
import com.notenest.noteservice.dto.request.NoteRequest;
import com.notenest.noteservice.dto.request.ShareNoteRequest;
import com.notenest.noteservice.dto.response.NoteResponse;
import com.notenest.noteservice.dto.response.PageResponse;
import com.notenest.noteservice.dto.response.ShareResponse;
import com.notenest.noteservice.entity.Note;
import com.notenest.noteservice.entity.NoteShare;
import com.notenest.noteservice.entity.SharePermission;
import com.notenest.noteservice.exception.ForbiddenException;
import com.notenest.noteservice.exception.NoteNotFoundException;
import com.notenest.noteservice.exception.UserNotFoundException;
import com.notenest.noteservice.repository.NoteRepository;
import com.notenest.noteservice.repository.NoteShareRepository;
import com.notenest.noteservice.service.NoteService;
import feign.FeignException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoteServiceImpl implements NoteService {

    private final NoteRepository noteRepository;
    private final NoteShareRepository noteShareRepository;
    private final UserClient userClient;

    @Override
    @Transactional
    public NoteResponse createNote(UUID ownerId, NoteRequest request) {
        Note note = Note.builder()
                .ownerId(ownerId)
                .title(request.getTitle())
                .content(request.getContent())
                .tags(normalizeTags(request))
                .build();

        return toResponse(noteRepository.save(note), true);
    }

    // @Transactional pada method baca: session tetap terbuka saat toResponse()
    // menyentuh note.getTags() yang lazy (open-in-view sengaja dimatikan).
    @Override
    @Transactional
    public PageResponse<NoteResponse> getMyNotes(UUID ownerId, String tag, String q, Pageable pageable) {
        Page<Note> page = noteRepository.searchOwnedNotes(ownerId, normalizeTag(tag), blankToNull(q), pageable);

        return PageResponse.<NoteResponse>builder()
                .content(page.getContent().stream().map(note -> toResponse(note, true)).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Override
    @Transactional
    public List<NoteResponse> getNotesSharedWithMe(UUID userId) {
        List<Note> notes = noteRepository.findNotesSharedWith(userId);

        // Satu pemilik bisa membagikan banyak note. Hasil lookup di-cache per
        // request supaya tidak ada panggilan Feign berulang untuk orang yang sama.
        Map<UUID, UserResponse> ownerCache = new HashMap<>();

        return notes.stream()
                .map(note -> {
                    UserResponse owner = ownerCache.computeIfAbsent(note.getOwnerId(), this::lookupUser);
                    NoteResponse response = toResponse(note, false);
                    response.setOwnerEmail(owner.getEmail());
                    response.setOwnerDisplayName(owner.getDisplayName());
                    return response;
                })
                .toList();
    }

    @Override
    @Transactional
    public NoteResponse getNoteById(UUID noteId, UUID requesterId) {
        Note note = findNoteOrThrow(noteId);

        boolean owned = note.getOwnerId().equals(requesterId);
        if (!owned && !noteShareRepository.existsByNoteIdAndSharedWithUserId(noteId, requesterId)) {
            // Pesan yang sama dengan kasus "note tidak ada" akan lebih rapat,
            // tapi 403 di sini sudah cukup: id note berupa UUID acak, tidak bisa ditebak.
            throw new ForbiddenException("You do not have access to this note");
        }

        return toResponse(note, owned);
    }

    @Override
    @Transactional
    public NoteResponse updateNote(UUID noteId, UUID ownerId, NoteRequest request) {
        Note note = findNoteOrThrow(noteId);
        requireOwner(note, ownerId);

        note.setTitle(request.getTitle());
        note.setContent(request.getContent());
        // Diisi ulang, bukan diganti objeknya: Hibernate melacak koleksi yang sama.
        note.getTags().clear();
        note.getTags().addAll(normalizeTags(request));

        return toResponse(noteRepository.save(note), true);
    }

    @Override
    @Transactional
    public void deleteNote(UUID noteId, UUID ownerId) {
        Note note = findNoteOrThrow(noteId);
        requireOwner(note, ownerId);

        // Share ikut terhapus lewat cascade + orphanRemoval di entity Note
        noteRepository.delete(note);
    }

    @Override
    @Transactional
    public List<ShareResponse> getShares(UUID noteId, UUID ownerId) {
        requireOwner(findNoteOrThrow(noteId), ownerId);

        // Tidak ada panggilan Feign di sini: email tujuan sudah disnapshot
        // di note_shares saat share dibuat.
        return noteShareRepository.findByNoteIdOrderByCreatedAtAsc(noteId).stream()
                .map(this::toShareResponse)
                .toList();
    }

    @Override
    @Transactional
    public ShareResponse shareNote(UUID noteId, UUID ownerId, ShareNoteRequest request) {
        Note note = findNoteOrThrow(noteId);
        requireOwner(note, ownerId);

        // Inilah alasan note-service butuh Feign: client mengirim email, sedangkan
        // yang bisa disimpan sebagai identitas hanyalah userId - dan hanya
        // user-service yang tahu pemetaannya.
        UserResponse target = lookupUserByEmail(request.getTargetEmail());

        if (target.getUserId().equals(ownerId)) {
            throw new IllegalArgumentException("You cannot share a note with yourself");
        }

        // Share ulang ke orang yang sama bukan error - kembalikan yang sudah ada.
        // Dari sisi pemakai hasilnya sama saja: orang itu punya akses.
        return noteShareRepository.findByNoteIdAndSharedWithUserId(noteId, target.getUserId())
                .map(this::toShareResponse)
                .orElseGet(() -> {
                    NoteShare share = NoteShare.builder()
                            .note(note)
                            .sharedWithUserId(target.getUserId())
                            .sharedWithEmail(target.getEmail())
                            .permission(SharePermission.READ)
                            .build();
                    return toShareResponse(noteShareRepository.save(share));
                });
    }

    @Override
    @Transactional
    public void revokeShare(UUID noteId, UUID ownerId, UUID targetUserId) {
        requireOwner(findNoteOrThrow(noteId), ownerId);

        NoteShare share = noteShareRepository.findByNoteIdAndSharedWithUserId(noteId, targetUserId)
                .orElseThrow(() -> new NoteNotFoundException(
                        "Note " + noteId + " is not shared with user " + targetUserId));

        noteShareRepository.delete(share);
    }

    // ===== helper =====

    // Cek kepemilikan ada di service, bukan controller: keputusannya butuh
    // entity-nya lebih dulu, dan harus terjadi sebelum apa pun diubah.
    private void requireOwner(Note note, UUID requesterId) {
        if (!note.getOwnerId().equals(requesterId)) {
            throw new ForbiddenException("Note does not belong to the current user");
        }
    }

    private Note findNoteOrThrow(UUID noteId) {
        return noteRepository.findById(noteId)
                .orElseThrow(() -> new NoteNotFoundException("Note not found: " + noteId));
    }

    // 404 dari user-service diterjemahkan jadi exception milik domain ini,
    // supaya client menerima pesan yang berarti ("email tidak terdaftar"),
    // bukan bocoran detail Feign. Error lain (service mati, 5xx) sengaja
    // dibiarkan lewat dan dipetakan jadi 502 di GlobalExceptionHandler -
    // itu masalah infrastruktur, bukan kesalahan pemakai.
    private UserResponse lookupUserByEmail(String email) {
        try {
            return userClient.getUserByEmail(email).getData();
        } catch (FeignException.NotFound ex) {
            throw new UserNotFoundException("No NoteNest user registered with email: " + email);
        }
    }

    private UserResponse lookupUser(UUID userId) {
        try {
            return userClient.getUserById(userId).getData();
        } catch (FeignException.NotFound ex) {
            throw new UserNotFoundException("Note owner no longer exists: " + userId);
        }
    }

    private Set<String> normalizeTags(NoteRequest request) {
        if (request.getTags() == null) {
            return new LinkedHashSet<>();
        }
        // Disimpan lowercase supaya "Java" dan "java" tidak jadi dua tag berbeda
        // saat difilter lewat ?tag=.
        return request.getTags().stream()
                .map(tag -> tag.trim().toLowerCase())
                .filter(tag -> !tag.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    // Tag disimpan lowercase, jadi filter ?tag= harus dinormalkan dengan cara yang sama.
    private String normalizeTag(String tag) {
        String normalized = blankToNull(tag);
        return normalized == null ? null : normalized.toLowerCase();
    }

    private NoteResponse toResponse(Note note, boolean owned) {
        return NoteResponse.builder()
                .id(note.getId())
                .ownerId(note.getOwnerId())
                .title(note.getTitle())
                .content(note.getContent())
                .tags(new LinkedHashSet<>(note.getTags()))
                .owned(owned)
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .build();
    }

    private ShareResponse toShareResponse(NoteShare share) {
        return ShareResponse.builder()
                .id(share.getId())
                .noteId(share.getNote().getId())
                .sharedWithUserId(share.getSharedWithUserId())
                .sharedWithEmail(share.getSharedWithEmail())
                .permission(share.getPermission().name())
                .createdAt(share.getCreatedAt())
                .build();
    }
}
