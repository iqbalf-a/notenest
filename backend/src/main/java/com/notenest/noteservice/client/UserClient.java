package com.notenest.noteservice.client;

import java.util.Optional;
import java.util.UUID;

// Batas antara note dan user. Note tidak pernah menyentuh entity, repository
// atau DTO milik user - ia cuma tahu interface ini dan UserResponse versinya
// sendiri. Batas itu dipertahankan meski keduanya kini satu proses: ia yang
// membuat note-service dulu bisa berdiri sendiri, dan ia juga yang membuat
// pemisahan itu bisa dikembalikan tanpa membongkar logika bisnis.
//
// Di branch dev, interface ini punya @FeignClient dan dipenuhi proxy HTTP yang
// alamatnya ditanyakan ke Eureka. Di sini pengisinya LocalUserClient, yang
// memanggil ProfileService langsung.
//
// Optional, bukan exception: "tidak ketemu" adalah jawaban yang sah dari
// pencarian, dan pemanggillah yang tahu pesan apa yang berarti bagi pemakai.
public interface UserClient {

    // Dipakai saat menampilkan "dibagikan oleh ..." di /api/notes/shared-with-me
    Optional<UserResponse> findById(UUID id);

    // Dipakai saat share: email tujuan -> userId, sekaligus validasi user ada.
    Optional<UserResponse> findByEmail(String email);
}
