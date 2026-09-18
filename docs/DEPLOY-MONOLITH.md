# Branch `deploy/monolith` — NoteNest sebagai satu aplikasi

Branch ini tetap monorepo (`backend/` + `frontend/` + `docs/`). Yang berbeda dari
`dev` hanya isi `backend/`: di sana 6 modul Maven, di sini satu aplikasi Spring Boot.

---

## 1. Kenapa ada

Susunan asli NoteNest butuh ~2,4 GB RAM dan komunikasi privat antar-service.
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

---

## 2. Struktur

```
backend/
├── pom.xml                  satu modul, tanpa Spring Cloud sama sekali
├── Dockerfile
└── src/main/java/com/notenest/
    ├── NoteNestApplication.java
    ├── common/              lapisan perakitan — hanya ada di branch ini
    │   ├── security/        JwtAuthFilter (servlet), SecurityConfig
    │   ├── exception/       GlobalExceptionHandler gabungan
    │   ├── config/          CorsConfig
    │   ├── client/          LocalUserClient
    │   └── dto/             ApiResponse
    ├── authservice/         apa adanya dari dev
    ├── userservice/         apa adanya dari dev
    └── noteservice/         apa adanya dari dev
```

Batas domainnya tidak dibongkar. `auth`, `user` dan `note` tetap tiga paket
terpisah; yang hilang cuma infrastruktur untuk bicara antar-proses.

---

## 3. Yang berubah dari `dev`

| Komponen | Nasib | Alasan |
|---|---|---|
| Eureka Server | Dihapus | Satu proses tidak perlu menemukan dirinya sendiri. |
| Config Server + `config-repo` | Dihapus | Isinya cuma alamat Eureka + actuator. |
| API Gateway — routing | Dihapus | Semua controller sudah satu app. |
| API Gateway — verifikasi JWT | **Tetap**, ditulis ulang | Satu-satunya tempat token diperiksa. |
| 3 × `@SpringBootApplication` | Jadi `NoteNestApplication` | — |
| 3 × `JpaConfig` | Jadi `@EnableJpaAuditing` di kelas utama | Tiga pendaftaran = context gagal start. |
| 3 × `GlobalExceptionHandler` | Digabung jadi satu | Handler-nya tumpang tindih. |
| `SecurityConfig` auth-service | Jadi `common/security/SecurityConfig` | Satu rantai security. |
| `@FeignClient UserClient` | Jadi interface biasa + `LocalUserClient` | Tidak ada HTTP antar-service. |
| `ClientApiResponse` | Dihapus | Amplop JSON Feign, tidak ada gunanya di satu proses. |
| `InternalUserController` (`/internal/**`) | Dihapus | Tidak punya pemanggil HTTP lagi. |

### JwtAuthFilter: satu-satunya port yang tidak sepele

Gateway itu **reaktif** (`ServerWebExchange`, `Mono`, `GlobalFilter`); auth/user/note
**servlet** (Spring MVC). Dua stack itu tidak bisa hidup dalam satu aplikasi, jadi
filternya ditulis ulang, bukan disalin.

Kontraknya dipertahankan persis: controller tetap hanya melihat header `X-User-*`
dan tidak pernah melihat JWT — jadi `UserController` dan `NoteController` tidak
berubah sebaris pun. Karena servlet tidak mengizinkan header request diubah,
request dibungkus `HttpServletRequestWrapper` yang membuang `X-User-*` kiriman
client (anti-spoofing) lalu memasang versi server.

### Yang sengaja dilepas

Di `dev` tiap service punya schema sendiri (`auth_schema` / `user_schema` /
`note_schema`) lewat `hibernate.default_schema`. Satu aplikasi hanya punya satu
konfigurasi JPA, jadi di sini semua tabel hidup di schema default. Pemisahan itu
memang konsep deployment — dan di deployment ini memang cuma ada satu service.

---

## 4. Uji dulu di laptop

```bash
cd backend && ./mvnw test          # 36 test
```

Selain unit test yang ikut dari `dev`, ada `ApiEndToEndTest`: seluruh context
di-start di H2, lalu API-nya dipanggil sungguhan — register, buat note, share,
`shared-with-me`, plus penolakan token palsu dan header `X-User-*` palsu.

```bash
cp .env.example .env               # lalu isi nilainya
docker compose up --build          # dibatasi 512 MB, meniru free tier
```

---

## 5. Deploy ke Render

### Database dulu — jangan pakai Postgres bawaan Render

Postgres gratis Render **kedaluwarsa 30 hari** lalu dihapus beserta datanya.
Pakai [Neon](https://neon.com): gratis permanen, tanpa kartu kredit, 0,5 GB.

```
DB_URL=jdbc:postgresql://<host>/<database>?sslmode=require
DB_USER=<user>
DB_PASSWORD=<password>
```

### Lalu service-nya

Cara cepat: **New → Blueprint**, pilih repo ini, branch `deploy/monolith`.
`render.yaml` sudah mengatur semuanya.

Manual — **New → Web Service**, Runtime **Docker**:

| Field | Nilai |
|---|---|
| Dockerfile Path | `backend/Dockerfile` |
| Docker Build Context Directory | `backend` |
| Health Check Path | `/actuator/health` |
| Region | Singapore |

Bikin `JWT_SECRET` dengan `openssl rand -base64 48`. Harus Base64 yang sah —
`JwtAuthFilter` men-decode-nya sebagai Base64, bukan teks biasa.

### Supaya tidak tidur

Render free menidurkan service setelah 15 menit idle, dan cold start Spring Boot
di 0,1 CPU terasa 1,5–2 menit. Karena satu service 24/7 cuma memakai ~730 dari
750 jam, service boleh dijaga tetap bangun: ping `/actuator/health` tiap ~14
menit lewat [cron-job.org](https://cron-job.org) atau UptimeRobot.

Konsekuensinya: jatah itu per workspace, jadi **hanya boleh ada satu** service
yang dijaga hidup.

---

## 6. Menarik perubahan dari `dev`

Branch ini memindahkan file, jadi `git merge dev` akan sering konflik: `dev`
mengubah file di path yang sudah tidak ada di sini. Porting perubahan dilakukan
manual — biasanya cukup menyalin file yang berubah ke `backend/src/main/java/`.

Yang perlu diperiksa setiap kali menarik perubahan:

- Controller/service/entity baru → tinggal salin, paketnya sama.
- `@Configuration` baru di salah satu service → cek apakah bentrok dengan `common/`.
- Perubahan di `api-gateway` → tidak ada padanannya; lihat `common/security/`.

---

## 7. Sebelum dipakai orang lain

- [ ] `JWT_SECRET` baru, bukan yang ada di `application.properties.example` milik
      `dev`. Yang lama sudah pernah masuk git dan harus dianggap bocor.
- [ ] `CORS_ALLOWED_ORIGINS` diarahkan ke domain frontend, bukan `localhost:5173`.
- [ ] Password database bukan `notenest123`.
- [ ] Baris `ports: 5432` di `docker-compose.yml` ditutup kalau dipakai di server.
