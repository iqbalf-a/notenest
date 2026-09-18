package com.notenest.monolith;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// Build gabungan: auth + user + note + verifikasi token, satu proses, satu port.
//
// Ini BUKAN pengganti arsitektur microservices di branch utama - itu tetap
// bentuk aslinya. Ini profil deploy untuk free tier yang cuma memberi satu
// service 512 MB, di mana Eureka dan Config Server tidak ada gunanya dan
// private networking antar-service tidak tersedia.
//
// Kode auth/user/note TIDAK disalin ke sini. Modul ini mengompilasi source
// ketiganya langsung dari folder aslinya (lihat build-helper di pom.xml),
// jadi tidak ada logika bisnis yang perlu disinkronkan dua kali.
//
// Ketiga anotasi di bawah harus menyebut com.notenest, bukan paket default
// kelas ini: entity dan repository-nya hidup di com.notenest.authservice,
// .userservice dan .noteservice.
//
// @EnableJpaAuditing sengaja di sini, bukan di kelas @Configuration terpisah
// seperti JpaConfig milik tiap service. Kalau @EnableJpaRepositories ditulis
// eksplisit, EntityManagerFactory dibangun lebih dulu daripada config auditing
// yang cuma di-scan - akibatnya AuditingEntityListener tidak pernah terpasang
// dan setiap insert gagal dengan created_at NULL.
@SpringBootApplication(scanBasePackages = "com.notenest")
@EntityScan("com.notenest")
@EnableJpaRepositories("com.notenest")
@EnableJpaAuditing
public class MonolithApplication {

    public static void main(String[] args) {
        SpringApplication.run(MonolithApplication.class, args);
    }
}
