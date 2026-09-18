package com.notenest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// NoteNest sebagai satu aplikasi: auth + user + note + verifikasi token,
// satu proses, satu port, satu koneksi database.
//
// Branch ini adalah profil deploy untuk free tier, yang cuma memberi satu
// service 512 MB tanpa private networking. Bentuk aslinya - 6 service dengan
// Eureka, Config Server, API Gateway dan Feign - ada di branch dev.
//
// Yang hilang dibanding bentuk aslinya cuma infrastruktur antar-service:
// discovery, config terpusat, routing, dan panggilan HTTP antar-service.
// Batas domainnya sendiri tidak dibongkar - auth, user dan note tetap tiga
// paket terpisah yang hanya bicara lewat interface, bukan satu tumpukan kode.
@SpringBootApplication
@EnableJpaAuditing
public class NoteNestApplication {

    public static void main(String[] args) {
        SpringApplication.run(NoteNestApplication.class, args);
    }
}
