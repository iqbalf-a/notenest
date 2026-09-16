package com.notenest.noteservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "note_shares", uniqueConstraints = {
        // Satu note hanya boleh punya satu baris share per user tujuan.
        // Dijaga di level DB, bukan cuma di service.
        @UniqueConstraint(name = "uk_note_shares_note_user", columnNames = {"note_id", "shared_with_user_id"})
}, indexes = {
        @Index(name = "idx_note_shares_target", columnList = "shared_with_user_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteShare extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "note_id", nullable = false)
    private Note note;

    @Column(name = "shared_with_user_id", nullable = false)
    private UUID sharedWithUserId;

    // Snapshot email tujuan saat share dibuat - pola yang sama dipakai ShopNest
    // untuk menyimpan nama & harga produk di order_items. Gunanya: menampilkan
    // daftar "dibagikan ke siapa" tidak perlu memanggil user-service sama sekali.
    @Column(name = "shared_with_email", nullable = false)
    private String sharedWithEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SharePermission permission = SharePermission.READ;
}
