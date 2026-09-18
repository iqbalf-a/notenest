package com.notenest.noteservice.repository;

import com.notenest.noteservice.entity.Note;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NoteRepository extends JpaRepository<Note, UUID> {

    // Satu query untuk tiga kombinasi filter (tanpa filter / per tag / cari teks).
    // Parameter null = filter itu dimatikan, jadi tidak perlu tiga method terpisah.
    // countQuery ditulis eksplisit karena join ke tags bisa menggandakan baris.
    @Query(value = """
            select distinct n from Note n
            left join n.tags t
            where n.ownerId = :ownerId
              and (:tag is null or t = :tag)
              and (:q is null
                   or lower(n.title) like lower(concat('%', :q, '%'))
                   or lower(n.content) like lower(concat('%', :q, '%')))
            """,
            countQuery = """
            select count(distinct n) from Note n
            left join n.tags t
            where n.ownerId = :ownerId
              and (:tag is null or t = :tag)
              and (:q is null
                   or lower(n.title) like lower(concat('%', :q, '%'))
                   or lower(n.content) like lower(concat('%', :q, '%')))
            """)
    Page<Note> searchOwnedNotes(@Param("ownerId") UUID ownerId,
                                @Param("tag") String tag,
                                @Param("q") String q,
                                Pageable pageable);

    // Note milik orang lain yang dibagikan ke user ini.
    @Query("""
            select s.note from NoteShare s
            where s.sharedWithUserId = :userId
            order by s.createdAt desc
            """)
    List<Note> findNotesSharedWith(@Param("userId") UUID userId);
}
