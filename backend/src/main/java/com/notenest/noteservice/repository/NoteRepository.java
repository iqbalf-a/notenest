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
    //
    // cast(:q as string) bukan hiasan. Tanpa itu Hibernate mengirim :q tanpa tipe
    // saat nilainya null, dan PostgreSQL - yang harus menebak tipe parameter -
    // menyimpulkan bytea, lalu gagal dengan "function lower(bytea) does not exist".
    // Artinya GET /api/notes tanpa ?q= akan selalu 500. H2 mode PostgreSQL jauh
    // lebih longgar soal null tanpa tipe, jadi test tidak bisa menangkap ini;
    // yang menemukannya adalah uji terhadap PostgreSQL sungguhan setelah deploy.
    @Query(value = """
            select distinct n from Note n
            left join n.tags t
            where n.ownerId = :ownerId
              and (cast(:tag as string) is null or t = cast(:tag as string))
              and (cast(:q as string) is null
                   or lower(n.title) like lower(concat('%', cast(:q as string), '%'))
                   or lower(n.content) like lower(concat('%', cast(:q as string), '%')))
            """,
            countQuery = """
            select count(distinct n) from Note n
            left join n.tags t
            where n.ownerId = :ownerId
              and (cast(:tag as string) is null or t = cast(:tag as string))
              and (cast(:q as string) is null
                   or lower(n.title) like lower(concat('%', cast(:q as string), '%'))
                   or lower(n.content) like lower(concat('%', cast(:q as string), '%')))
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
