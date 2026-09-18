package com.notenest.userservice.repository;

import com.notenest.userservice.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    Optional<Profile> findByUserId(UUID userId);

    Optional<Profile> findByEmailIgnoreCase(String email);

    // Untuk kotak "cari user" di halaman share. Cocok sebagian, bukan persis,
    // supaya user tidak harus mengetik email lengkap.
    List<Profile> findTop10ByEmailContainingIgnoreCaseOrderByEmailAsc(String email);
}
