package com.notenest.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

// CORS = izin browser memanggil origin lain (localhost:5173 -> localhost:8080).
// Postman/curl tidak terpengaruh, jadi tanpa ini backend terlihat baik-baik saja
// sampai dipanggil dari browser.
@Configuration
public class CorsConfig {

    // Bisa ditimpa lewat properties/env tanpa ubah kode, mis. saat frontend dideploy:
    // notenest.cors.allowed-origins=https://notenest.example
    @Value("${notenest.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // Daftar eksplisit, bukan "*": origin yang tidak terdaftar tetap ditolak browser.
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Frontend mengirim Authorization + Content-Type; keduanya bukan header "sederhana",
        // artinya setiap request memicu preflight OPTIONS lebih dulu.
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        // false: autentikasi memakai header Bearer, bukan cookie.
        // Kalau suatu saat pindah ke cookie, ini harus true DAN origin tidak boleh "*".
        config.setAllowCredentials(false);

        // Browser menyimpan hasil preflight 1 jam, jadi tidak ada OPTIONS di tiap request.
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
