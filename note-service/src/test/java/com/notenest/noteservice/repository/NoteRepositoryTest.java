package com.notenest.noteservice.repository;

import com.notenest.noteservice.config.JpaConfig;
import com.notenest.noteservice.entity.Note;
import com.notenest.noteservice.entity.NoteShare;
import com.notenest.noteservice.entity.SharePermission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// NoteRepository memakai @Query HQL yang ditulis tangan - satu-satunya bagian
// yang tidak bisa dibuktikan benar oleh unit test bermock. Test ini menjalankannya
// sungguhan di H2 (mode PostgreSQL) supaya salah ketik atau salah join ketahuan.
@DataJpaTest
// @DataJpaTest hanya memuat irisan JPA - JpaConfig (@EnableJpaAuditing) tidak ikut
// ter-scan, jadi harus di-import manual. Tanpa ini createdAt/updatedAt tetap null.
@Import(JpaConfig.class)
class NoteRepositoryTest {

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private NoteShareRepository noteShareRepository;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID otherOwnerId = UUID.randomUUID();
    private final UUID readerId = UUID.randomUUID();

    private Note save(UUID owner, String title, String content, Set<String> tags) {
        return noteRepository.save(Note.builder()
                .ownerId(owner)
                .title(title)
                .content(content)
                .tags(new java.util.LinkedHashSet<>(tags))
                .build());
    }

    @BeforeEach
    void seed() {
        save(ownerId, "Belajar Spring", "Feign dan Eureka", Set.of("java", "spring"));
        save(ownerId, "Daftar belanja", "Kopi dan gula", Set.of("pribadi"));
        save(ownerId, "Catatan rapat", "Bahas roadmap", Set.of());
        save(otherOwnerId, "Punya orang lain", "Rahasia", Set.of("java"));
    }

    @Test
    void searchOwnedNotes_withoutFilters_returnsOnlyOwnNotesWithoutDuplicates() {
        Page<Note> page = noteRepository.searchOwnedNotes(ownerId, null, null, PageRequest.of(0, 10));

        // "Belajar Spring" punya 2 tag - tanpa distinct dia akan muncul dua kali
        assertEquals(3, page.getTotalElements());
        assertEquals(3, page.getContent().size());
        assertTrue(page.getContent().stream().allMatch(note -> note.getOwnerId().equals(ownerId)));
    }

    @Test
    void searchOwnedNotes_filterByTag_returnsOnlyMatchingNotes() {
        Page<Note> page = noteRepository.searchOwnedNotes(ownerId, "spring", null, PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals("Belajar Spring", page.getContent().get(0).getTitle());
    }

    @Test
    void searchOwnedNotes_freeTextMatchesTitleAndContentCaseInsensitively() {
        assertEquals(1, noteRepository.searchOwnedNotes(ownerId, null, "BELANJA", PageRequest.of(0, 10))
                .getTotalElements());
        // "roadmap" hanya ada di content, bukan di title
        assertEquals(1, noteRepository.searchOwnedNotes(ownerId, null, "roadmap", PageRequest.of(0, 10))
                .getTotalElements());
        assertEquals(0, noteRepository.searchOwnedNotes(ownerId, null, "tidak-ada", PageRequest.of(0, 10))
                .getTotalElements());
    }

    @Test
    void searchOwnedNotes_supportsSortingAndPaging() {
        Page<Note> page = noteRepository.searchOwnedNotes(
                ownerId, null, null, PageRequest.of(0, 2, Sort.by("title")));

        assertEquals(3, page.getTotalElements());
        assertEquals(2, page.getContent().size());
        assertEquals("Belajar Spring", page.getContent().get(0).getTitle());
        assertEquals(2, page.getTotalPages());
    }

    @Test
    void findNotesSharedWith_returnsNotesOwnedByOthersThatWereSharedToThisUser() {
        Note shared = save(otherOwnerId, "Dibagikan", "Isi catatan", Set.of("kerja"));
        noteShareRepository.save(NoteShare.builder()
                .note(shared)
                .sharedWithUserId(readerId)
                .sharedWithEmail("reader@example.com")
                .permission(SharePermission.READ)
                .build());

        List<Note> notes = noteRepository.findNotesSharedWith(readerId);

        assertEquals(1, notes.size());
        assertEquals("Dibagikan", notes.get(0).getTitle());
        assertTrue(noteRepository.findNotesSharedWith(ownerId).isEmpty());
    }
}
