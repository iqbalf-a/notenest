package com.notenest.noteservice.client;

import com.notenest.noteservice.client.dto.ClientApiResponse;
import com.notenest.noteservice.client.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

// name = spring.application.name milik user-service di Eureka.
// Feign + Eureka: panggil method Java biasa -> jadi HTTP request ke service lain,
// alamatnya ditanyakan ke Eureka (bukan hardcode host:port).
//
// Kedua endpoint ada di /internal/** yang ditutup di gateway, jadi jalur ini
// memang hanya bisa ditempuh service, bukan client dari luar.
@FeignClient(name = "user-service")
public interface UserClient {

    // Dipakai saat menampilkan "dibagikan oleh ..." di /api/notes/shared-with-me
    @GetMapping("/internal/users/{id}")
    ClientApiResponse<UserResponse> getUserById(@PathVariable("id") UUID id);

    // Dipakai saat share: email tujuan -> userId, sekaligus validasi user ada.
    // 404 dari sini = email tidak terdaftar, dan itu yang dilaporkan ke client.
    @GetMapping("/internal/users/by-email")
    ClientApiResponse<UserResponse> getUserByEmail(@RequestParam("email") String email);
}
