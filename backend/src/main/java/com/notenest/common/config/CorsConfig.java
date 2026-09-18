package com.notenest.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

// Versi servlet dari CorsConfig milik api-gateway. Isinya identik; yang berbeda
// cuma tipenya - gateway reaktif memakai CorsWebFilter, aplikasi MVC memakai
// CorsConfigurationSource yang dipasang ke SecurityFilterChain.
@Configuration
public class CorsConfig {

    // Saat frontend sudah dideploy, timpa lewat env:
    // CORS_ALLOWED_ORIGINS=https://notenest.vercel.app
    @Value("${notenest.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Daftar eksplisit, bukan "*": origin yang tidak terdaftar tetap ditolak browser.
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Frontend mengirim Authorization + Content-Type; keduanya bukan header "sederhana",
        // artinya setiap request memicu preflight OPTIONS lebih dulu.
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        // false: autentikasi memakai header Bearer, bukan cookie.
        config.setAllowCredentials(false);

        // Browser menyimpan hasil preflight 1 jam, jadi tidak ada OPTIONS di tiap request.
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
