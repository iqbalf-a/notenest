package com.notenest.userservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "profiles", indexes = {
        @Index(name = "idx_profiles_user_id", columnList = "user_id", unique = true),
        @Index(name = "idx_profiles_email", columnList = "email", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Profile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // id user di auth-service. Bukan foreign key - service tidak berbagi tabel,
    // jadi relasinya hanya "by convention", diverifikasi oleh JWT.
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    // Salinan email dari token. Disimpan di sini supaya fitur "cari user untuk
    // di-share" bisa dijawab user-service sendiri, tanpa memanggil auth-service.
    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(length = 500)
    private String bio;

    @Column(name = "avatar_url")
    private String avatarUrl;
}
