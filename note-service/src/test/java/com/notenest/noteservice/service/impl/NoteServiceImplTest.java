package com.notenest.noteservice.service.impl;

import com.notenest.noteservice.client.UserClient;
import com.notenest.noteservice.client.dto.ClientApiResponse;
import com.notenest.noteservice.client.dto.UserResponse;
import com.notenest.noteservice.dto.request.NoteRequest;
import com.notenest.noteservice.dto.request.ShareNoteRequest;
import com.notenest.noteservice.dto.response.NoteResponse;
import com.notenest.noteservice.dto.response.ShareResponse;
import com.notenest.noteservice.entity.Note;
import com.notenest.noteservice.entity.NoteShare;
import com.notenest.noteservice.entity.SharePermission;
import com.notenest.noteservice.exception.ForbiddenException;
import com.notenest.noteservice.exception.NoteNotFoundException;
import com.notenest.noteservice.exception.UserNotFoundException;
import com.notenest.noteservice.repository.NoteRepository;
import com.notenest.noteservice.repository.NoteShareRepository;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteServiceImplTest {

    @Mock
    private NoteRepository noteRepository;

    @Mock
    private NoteShareRepository noteShareRepository;

    @Mock
    private UserClient userClient;

    @InjectMocks
    private NoteServiceImpl noteService;

    private Note note(UUID id, UUID ownerId) {
        return Note.builder()
                .id(id)
                .ownerId(ownerId)
                .title("Catatan rapat")
                .content("Bahas roadmap Q3")
                .build();
    }

    // ===== CRUD & kepemilikan =====

    @Test
    void createNote_storesTagsLowercasedAndMarksResponseAsOwned() {
        UUID ownerId = UUID.randomUUID();

        NoteRequest request = new NoteRequest();
        request.setTitle("Belajar Spring");
        request.setContent("Feign, Eureka, Gateway");
        request.setTags(Set.of("Java", "  SPRING  "));

        when(noteRepository.save(any(Note.class))).thenAnswer(inv -> inv.getArgument(0));

        NoteResponse response = noteService.createNote(ownerId, request);

        ArgumentCaptor<Note> saved = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(saved.capture());

        assertEquals(Set.of("java", "spring"), saved.getValue().getTags());
        assertEquals(ownerId, saved.getValue().getOwnerId());
        assertTrue(response.isOwned());
    }

    @Test
    void getNoteById_sharedWithRequester_isReadableButNotOwned() {
        UUID noteId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID readerId = UUID.randomUUID();

        when(noteRepository.findById(noteId)).thenReturn(Optional.of(note(noteId, ownerId)));
        when(noteShareRepository.existsByNoteIdAndSharedWithUserId(noteId, readerId)).thenReturn(true);

        NoteResponse response = noteService.getNoteById(noteId, readerId);

        assertEquals(noteId, response.getId());
        assertFalse(response.isOwned());
    }

    @Test
    void getNoteById_neitherOwnerNorShared_isForbidden() {
        UUID noteId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();

        when(noteRepository.findById(noteId)).thenReturn(Optional.of(note(noteId, UUID.randomUUID())));
        when(noteShareRepository.existsByNoteIdAndSharedWithUserId(noteId, strangerId)).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> noteService.getNoteById(noteId, strangerId));
    }

    @Test
    void updateNote_byNonOwner_isForbiddenAndChangesNothing() {
        UUID noteId = UUID.randomUUID();

        NoteRequest request = new NoteRequest();
        request.setTitle("Diambil alih");

        when(noteRepository.findById(noteId)).thenReturn(Optional.of(note(noteId, UUID.randomUUID())));

        assertThrows(ForbiddenException.class,
                () -> noteService.updateNote(noteId, UUID.randomUUID(), request));
        verify(noteRepository, never()).save(any(Note.class));
    }

    @Test
    void deleteNote_byNonOwner_isForbiddenAndDeletesNothing() {
        UUID noteId = UUID.randomUUID();

        when(noteRepository.findById(noteId)).thenReturn(Optional.of(note(noteId, UUID.randomUUID())));

        assertThrows(ForbiddenException.class, () -> noteService.deleteNote(noteId, UUID.randomUUID()));
        verify(noteRepository, never()).delete(any(Note.class));
    }

    @Test
    void getNoteById_unknownId_throwsNoteNotFound() {
        UUID noteId = UUID.randomUUID();
        when(noteRepository.findById(noteId)).thenReturn(Optional.empty());

        assertThrows(NoteNotFoundException.class, () -> noteService.getNoteById(noteId, UUID.randomUUID()));
    }

    // ===== Share: jalur Feign =====

    @Test
    void shareNote_resolvesTargetEmailThroughFeignAndSnapshotsIt() {
        UUID noteId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        ShareNoteRequest request = new ShareNoteRequest();
        request.setTargetEmail("budi@example.com");

        UserResponse target = UserResponse.builder()
                .userId(targetId)
                .email("budi@example.com")
                .displayName("Budi")
                .build();

        when(noteRepository.findById(noteId)).thenReturn(Optional.of(note(noteId, ownerId)));
        when(userClient.getUserByEmail("budi@example.com"))
                .thenReturn(new ClientApiResponse<>(true, "User found", target));
        when(noteShareRepository.findByNoteIdAndSharedWithUserId(noteId, targetId)).thenReturn(Optional.empty());
        when(noteShareRepository.save(any(NoteShare.class))).thenAnswer(inv -> inv.getArgument(0));

        ShareResponse response = noteService.shareNote(noteId, ownerId, request);

        ArgumentCaptor<NoteShare> saved = ArgumentCaptor.forClass(NoteShare.class);
        verify(noteShareRepository).save(saved.capture());

        assertEquals(targetId, saved.getValue().getSharedWithUserId());
        // Email disimpan sebagai snapshot supaya daftar share tidak perlu Feign lagi
        assertEquals("budi@example.com", saved.getValue().getSharedWithEmail());
        assertEquals(SharePermission.READ, saved.getValue().getPermission());
        assertEquals("READ", response.getPermission());
    }

    // Inti dari fitur ini: 404 dari user-service tidak boleh bocor apa adanya
    // ke client sebagai FeignException, tapi jadi error domain yang jelas.
    @Test
    void shareNote_targetEmailNotRegistered_translatesFeign404ToUserNotFound() {
        UUID noteId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        ShareNoteRequest request = new ShareNoteRequest();
        request.setTargetEmail("hantu@example.com");

        when(noteRepository.findById(noteId)).thenReturn(Optional.of(note(noteId, ownerId)));
        when(userClient.getUserByEmail("hantu@example.com")).thenThrow(mock(FeignException.NotFound.class));

        UserNotFoundException ex = assertThrows(UserNotFoundException.class,
                () -> noteService.shareNote(noteId, ownerId, request));

        assertTrue(ex.getMessage().contains("hantu@example.com"));
        verify(noteShareRepository, never()).save(any(NoteShare.class));
    }

    // user-service mati -> FeignException umum dibiarkan lewat, supaya
    // GlobalExceptionHandler bisa menjawab 502 (bukan 404 yang menyesatkan).
    @Test
    void shareNote_userServiceUnreachable_propagatesFeignException() {
        UUID noteId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        ShareNoteRequest request = new ShareNoteRequest();
        request.setTargetEmail("budi@example.com");

        when(noteRepository.findById(noteId)).thenReturn(Optional.of(note(noteId, ownerId)));
        when(userClient.getUserByEmail("budi@example.com"))
                .thenThrow(mock(FeignException.ServiceUnavailable.class));

        assertThrows(FeignException.class, () -> noteService.shareNote(noteId, ownerId, request));
        verify(noteShareRepository, never()).save(any(NoteShare.class));
    }

    @Test
    void shareNote_byNonOwner_isForbiddenBeforeAnyFeignCall() {
        UUID noteId = UUID.randomUUID();

        ShareNoteRequest request = new ShareNoteRequest();
        request.setTargetEmail("budi@example.com");

        when(noteRepository.findById(noteId)).thenReturn(Optional.of(note(noteId, UUID.randomUUID())));

        assertThrows(ForbiddenException.class,
                () -> noteService.shareNote(noteId, UUID.randomUUID(), request));
        verify(userClient, never()).getUserByEmail(any());
    }

    @Test
    void shareNote_toSelf_isRejected() {
        UUID noteId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        ShareNoteRequest request = new ShareNoteRequest();
        request.setTargetEmail("iqbal@example.com");

        when(noteRepository.findById(noteId)).thenReturn(Optional.of(note(noteId, ownerId)));
        when(userClient.getUserByEmail("iqbal@example.com")).thenReturn(new ClientApiResponse<>(
                true, "User found",
                UserResponse.builder().userId(ownerId).email("iqbal@example.com").displayName("Iqbal").build()));

        assertThrows(IllegalArgumentException.class, () -> noteService.shareNote(noteId, ownerId, request));
        verify(noteShareRepository, never()).save(any(NoteShare.class));
    }

    @Test
    void shareNote_sameTargetTwice_returnsExistingShareInsteadOfDuplicating() {
        UUID noteId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Note existingNote = note(noteId, ownerId);

        ShareNoteRequest request = new ShareNoteRequest();
        request.setTargetEmail("budi@example.com");

        NoteShare existing = NoteShare.builder()
                .id(UUID.randomUUID())
                .note(existingNote)
                .sharedWithUserId(targetId)
                .sharedWithEmail("budi@example.com")
                .permission(SharePermission.READ)
                .build();

        when(noteRepository.findById(noteId)).thenReturn(Optional.of(existingNote));
        when(userClient.getUserByEmail("budi@example.com")).thenReturn(new ClientApiResponse<>(
                true, "User found",
                UserResponse.builder().userId(targetId).email("budi@example.com").displayName("Budi").build()));
        when(noteShareRepository.findByNoteIdAndSharedWithUserId(noteId, targetId))
                .thenReturn(Optional.of(existing));

        ShareResponse response = noteService.shareNote(noteId, ownerId, request);

        assertEquals(existing.getId(), response.getId());
        verify(noteShareRepository, never()).save(any(NoteShare.class));
    }

    @Test
    void getNotesSharedWithMe_looksUpEachOwnerOnlyOnce() {
        UUID readerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        when(noteRepository.findNotesSharedWith(readerId)).thenReturn(List.of(
                note(UUID.randomUUID(), ownerId),
                note(UUID.randomUUID(), ownerId)));
        when(userClient.getUserById(ownerId)).thenReturn(new ClientApiResponse<>(
                true, "User found",
                UserResponse.builder().userId(ownerId).email("iqbal@example.com").displayName("Iqbal").build()));

        List<NoteResponse> responses = noteService.getNotesSharedWithMe(readerId);

        assertEquals(2, responses.size());
        assertEquals("Iqbal", responses.get(0).getOwnerDisplayName());
        assertFalse(responses.get(0).isOwned());
        // Dua note, satu pemilik -> cukup satu panggilan Feign
        verify(userClient, times(1)).getUserById(ownerId);
    }

    @Test
    void revokeShare_whenNotShared_throwsNoteNotFound() {
        UUID noteId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        when(noteRepository.findById(noteId)).thenReturn(Optional.of(note(noteId, ownerId)));
        when(noteShareRepository.findByNoteIdAndSharedWithUserId(noteId, targetId)).thenReturn(Optional.empty());

        assertThrows(NoteNotFoundException.class, () -> noteService.revokeShare(noteId, ownerId, targetId));
        verify(noteShareRepository, never()).delete(any(NoteShare.class));
    }
}
