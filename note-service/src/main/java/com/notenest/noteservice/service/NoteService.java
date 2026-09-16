package com.notenest.noteservice.service;

import com.notenest.noteservice.dto.request.NoteRequest;
import com.notenest.noteservice.dto.request.ShareNoteRequest;
import com.notenest.noteservice.dto.response.NoteResponse;
import com.notenest.noteservice.dto.response.PageResponse;
import com.notenest.noteservice.dto.response.ShareResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface NoteService {

    NoteResponse createNote(UUID ownerId, NoteRequest request);

    PageResponse<NoteResponse> getMyNotes(UUID ownerId, String tag, String q, Pageable pageable);

    List<NoteResponse> getNotesSharedWithMe(UUID userId);

    NoteResponse getNoteById(UUID noteId, UUID requesterId);

    NoteResponse updateNote(UUID noteId, UUID ownerId, NoteRequest request);

    void deleteNote(UUID noteId, UUID ownerId);

    List<ShareResponse> getShares(UUID noteId, UUID ownerId);

    ShareResponse shareNote(UUID noteId, UUID ownerId, ShareNoteRequest request);

    void revokeShare(UUID noteId, UUID ownerId, UUID targetUserId);
}
