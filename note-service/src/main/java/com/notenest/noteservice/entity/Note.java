package com.notenest.noteservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "notes", indexes = {
        @Index(name = "idx_notes_owner_id", columnList = "owner_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Note extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // id user di auth-service. Semua aturan kepemilikan dicek terhadap kolom ini.
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    // Tag cuma daftar string milik note, tidak punya identitas sendiri -
    // @ElementCollection pas: satu tabel note_tags(note_id, tag), tanpa entity terpisah.
    @ElementCollection
    @CollectionTable(
            name = "note_tags",
            joinColumns = @JoinColumn(name = "note_id"),
            indexes = @Index(name = "idx_note_tags_tag", columnList = "tag"))
    @Column(name = "tag", nullable = false)
    @Builder.Default
    private Set<String> tags = new LinkedHashSet<>();

    // Share ikut terhapus kalau note-nya dihapus (orphanRemoval + cascade).
    @OneToMany(mappedBy = "note", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<NoteShare> shares = new ArrayList<>();
}
