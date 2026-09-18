# Deploy: build gabungan untuk free tier

Dokumen ini menjelaskan modul `backend/monolith` — kenapa ada, apa yang berubah
dari susunan microservices, dan cara mendeploy-nya.

---

## 1. Kenapa ada

Susunan asli NoteNest adalah 6 service Java + Postgres. Untuk dijalankan
sungguhan, itu butuh ~2,4 GB RAM dan komunikasi privat antar-service.
Tidak ada free tier PaaS yang menyediakan keduanya:

| Kendala | Free tier | Akibat |
|---|---|---|
| 750 jam/bulan per workspace | Render | 6 service 24/7 = ~4.380 jam. Kuota habis ~5 hari. |
| Tanpa private networking | Render | Eureka & Feign harus lewat URL publik. |
| Tanpa persistent disk | Render | `config-repo` di-mount sebagai volume → Config Server mati. |
| 512 MB per service | Render, Koyeb | Muat untuk satu JVM, tidak untuk enam. |
| $1 kredit/bulan | Railway | Satu service Java saja ~$4/bulan. Habis dalam ~7 hari. |

Satu service selalu hidup = ~730 jam/bulan, **muat** dalam kuota 750 jam Render.
Karena itu targetnya satu service, bukan enam.

Ini profil **deploy**, bukan penulisan ulang. Arsitektur microservices tetap
bentuk aslinya di branch utama dan tetap jalan lewat `docker-compose.yml`.

---

## 2. Yang berubah

| Komponen | Nasib | Alasan |
|---|---|---|
| Eureka Server | Hilang | Satu proses tidak perlu menemukan dirinya sendiri. |
| Config Server | Hilang | Config-nya cuma alamat Eureka + actuator. |
| API Gateway — routing | Hilang | Semua controller sudah satu app. |
| API Gateway — verifikasi JWT | **Tetap**, ditulis ulang | Satu-satunya tempat token diperiksa. |
| auth + user + note | Jadi satu proses | Tetap tiga paket terpisah. |
| Feign `UserClient` | Diganti panggilan langsung | Tidak ada HTTP antar-service lagi. |
| `/internal/**` | Hilang | Tidak punya pemanggil HTTP lagi. |

### Kode bisnisnya tidak disalin

`backend/monolith` **tidak** menyalin satu baris pun logika auth/user/note.
Modul ini mengompilasi source ketiganya langsung dari foldernya lewat
`build-helper-maven-plugin`:

```xml
<source>${project.basedir}/../auth-service/src/main/java</source>
<source>${project.basedir}/../user-service/src/main/java</source>
<source>${project.basedir}/../note-service/src/main/java</source>
```

Perbaikan bug di `note-service` otomatis ikut ke build gabungan. Yang ditulis
khusus untuk modul ini hanya kelas yang memang bertabrakan saat digabung:

| Kelas monolith | Menggantikan |
|---|---|
| `MonolithApplication` | 3 × `@SpringBootApplication`, dan `@EnableJpaAuditing` |
| `security/JwtAuthFilter` | `JwtAuthFilter` reaktif milik gateway |
| `security/SecurityConfig` | `SecurityConfig` milik auth-service |
| `exception/GlobalExceptionHandler` | 3 × `@RestControllerAdvice` |
| `config/CorsConfig` | `CorsConfig` reaktif milik gateway |
| `client/LocalUserClient` | Proxy Feign untuk `UserClient` |

### Dua hal yang gampang salah

**Pola exclude di pom harus menyebut paket service-nya.** Pola dicocokkan
relatif terhadap *setiap* source root, termasuk milik monolith sendiri. Pola
longgar seperti `**/exception/GlobalExceptionHandler.java` ikut membuang file
pengganti — dan javac diam saja, karena file yang di-exclude masih bisa
terkompilasi lewat sourcepath, tapi **tanpa menjalankan Lombok**.

**`@EnableJpaAuditing` harus menempel di kelas utama.** Kalau
`@EnableJpaRepositories` ditulis eksplisit (dan di sini harus, karena entity-nya
ada di paket lain), EntityManagerFactory dibangun lebih dulu daripada config
auditing yang cuma di-scan. Akibatnya `AuditingEntityListener` tidak terpasang
dan setiap insert gagal dengan `created_at` NULL.

### Yang sengaja dilepas

Di susunan microservices tiap service punya schema sendiri
(`auth_schema` / `user_schema` / `note_schema`) lewat `hibernate.default_schema`.
Satu aplikasi hanya punya satu konfigurasi JPA, jadi di build ini semua tabel
hidup di schema default. Pemisahan itu memang konsep deployment — dan di
deployment ini memang cuma ada satu service.

---

## 3. Uji dulu di laptop

```bash
# Test: seluruh context gabungan di-start di H2, lalu alur
# register -> buat note -> share -> shared-with-me dijalankan sungguhan.
cd backend/monolith && ./mvnw test

# Jalan sungguhan, dengan batas memori yang meniru free tier
cp .env.example .env          # lalu isi nilainya
docker compose -f docker-compose.monolith.yml up --build
```

`MonolithSmokeTest` sengaja tidak menguji logika bisnis — itu sudah punya test
sendiri di masing-masing service. Yang diuji cuma yang bisa rusak **karena
penggabungan**: bean ganda, JWT → header `X-User-*`, penolakan header palsu,
dan share tanpa Feign.

---

## 4. Deploy ke Render

### Database dulu — jangan pakai Postgres bawaan Render

Postgres gratis Render **kedaluwarsa 30 hari** lalu dihapus beserta datanya.
Pakai [Neon](https://neon.com): gratis permanen, tanpa kartu kredit, 0,5 GB.

Dari connection string Neon, isi tiga variabel terpisah:

```
DB_URL=jdbc:postgresql://<host>/<database>?sslmode=require
DB_USER=<user>
DB_PASSWORD=<password>
```

### Lalu service-nya

Cara cepat: **New → Blueprint**, pilih repo ini, branch `deploy/monolith`.
`render.yaml` sudah mengatur Dockerfile, context, health check, dan daftar
env var yang harus diisi manual.

Kalau mau manual — **New → Web Service**, Runtime **Docker**:

| Field | Nilai |
|---|---|
| Dockerfile Path | `backend/monolith/Dockerfile` |
| Docker Build Context Directory | `backend` |
| Health Check Path | `/actuator/health` |
| Region | Singapore |

Bikin `JWT_SECRET` dengan `openssl rand -base64 48`. Harus Base64 yang sah —
`JwtAuthFilter` men-decode-nya sebagai Base64, bukan teks biasa.

### Supaya tidak tidur

Render free menidurkan service setelah 15 menit idle, dan cold start Spring Boot
di 0,1 CPU terasa 1,5–2 menit. Karena satu service 24/7 cuma memakai ~730 dari
750 jam, service boleh dijaga tetap bangun: ping `/actuator/health` tiap ~14
menit lewat [cron-job.org](https://cron-job.org) atau UptimeRobot — dua-duanya
gratis dan tanpa kartu kredit.

Konsekuensinya: jatah itu per workspace, jadi **hanya boleh ada satu** service
yang dijaga hidup.

---

## 5. Sebelum dipakai orang lain

- [ ] `JWT_SECRET` baru, bukan yang ada di `application.properties.example`.
      Yang lama sudah pernah masuk git dan harus dianggap bocor.
- [ ] `CORS_ALLOWED_ORIGINS` diarahkan ke domain frontend, bukan `localhost:5173`.
- [ ] Password database bukan `notenest123`.
