package com.notenest.noteservice.repository;

import com.notenest.noteservice.entity.NoteShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NoteShareRepository extends JpaRepository<NoteShare, UUID> {

    List<NoteShare> findByNoteIdOrderByCreatedAtAsc(UUID noteId);

    Optional<NoteShare> findByNoteIdAndSharedWithUserId(UUID noteId, UUID sharedWithUserId);

    boolean existsByNoteIdAndSharedWithUserId(UUID noteId, UUID sharedWithUserId);
}
