# NoteNest — Dokumentasi Project (branch `deploy/monolith`)

REST API aplikasi catatan dengan fitur berbagi (share), Spring Boot + PostgreSQL.

> Branch ini menjalankan NoteNest sebagai **satu aplikasi**. Bentuk aslinya — 6 service
> dengan Eureka, Config Server, API Gateway dan Feign — ada di branch **`dev`**.
> Alasan dan daftar perubahannya: [`DEPLOY-MONOLITH.md`](DEPLOY-MONOLITH.md).

---

## 1. Ringkasan

NoteNest adalah aplikasi catatan dengan tiga domain — auth, user, note — plus fitur berbagi
catatan read-only ke pengguna lain.

Di branch `dev` ketiga domain itu adalah proses terpisah yang bicara lewat HTTP. Di branch
ini ketiganya satu proses. **Batas domainnya tidak berubah:** `authservice`, `userservice`
dan `noteservice` tetap tiga paket yang hanya saling menyentuh lewat interface.

**Kenapa ada dua bentuk?**
Bentuk microservices adalah desain aslinya. Bentuk monolith lahir dari kendala nyata: tidak
ada free tier PaaS yang sanggup menjalankan enam JVM dengan private networking. Daripada
membayar atau memalsukan demo, arsitekturnya dirakit ulang untuk target deploy-nya — dan
tetap bisa dijelaskan sebagai keputusan, bukan kompromi diam-diam.

**Kenapa fitur share dipertahankan?**
Karena ia satu-satunya alasan jujur untuk ada batas antara note dan user. Client mengirim
email tujuan, sedangkan sisi note hanya bisa menyimpan `userId` — dan hanya sisi user yang
tahu pemetaannya. Di `dev` batas itu diseberangi Feign; di sini oleh pemanggilan method.
Yang penting: batasnya tetap ada.

---

## 2. Tech Stack

| Kategori | Teknologi |
|----------|-----------|
| Bahasa | Java 21 |
| Framework | Spring Boot 3.4.6 |
| Cloud | **tidak ada** — Spring Cloud sepenuhnya keluar dari pom |
| Security | Spring Security + JWT (library jjwt 0.12.5) |
| Database | PostgreSQL 16 (container Docker atau Neon), satu schema |
| ORM | Spring Data JPA + Hibernate |
| Dokumentasi API | springdoc-openapi + Scalar UI |
| Testing | JUnit 5 + Mockito, H2 untuk test repository & end-to-end |
| Build | Maven (wrapper disertakan) |
| Boilerplate | Lombok |

Di `dev`, baris "Cloud" berisi Spring Cloud 2024.0.1 (Eureka, Gateway, OpenFeign, Config).
Semuanya infrastruktur untuk bicara antar-proses; satu proses tidak butuh satu pun.

---

## 3. Arsitektur

```
                    ┌──────────────────┐
                    │ Client / Postman │
                    └────────┬─────────┘
                             │
                    ┌────────▼─────────┐
                    │  NoteNest  :8080 │
                    │                  │
                    │  JwtAuthFilter   │  verifikasi token, pasang X-User-*
                    │        │         │
                    │  ┌─────┴─────┐   │
                    │  │  auth     │   │
                    │  │  user  ◄──┼───┼── UserClient (interface)
                    │  │  note  ───┼───┘
                    │  └───────────┘   │
                    └────────┬─────────┘
                             │
                    ┌────────▼─────────┐
                    │   PostgreSQL     │
                    └──────────────────┘
```

| Paket | Tanggung jawab |
|---|---|
| `common` | Verifikasi token, rantai security, CORS, handler exception, `LocalUserClient` |
| `authservice` | Register, login, penerbit JWT |
| `userservice` | Profil (dibuat otomatis), cari user |
| `noteservice` | CRUD note, tag, share |

**Satu jalur komunikasi.** Semua request masuk lewat port 8080, melewati `JwtAuthFilter`,
lalu ke controller. Tidak ada hop HTTP kedua.

**Batas antar domain dijaga di tingkat kode, bukan jaringan.** `noteservice` tidak pernah
meng-import entity, repository, atau service milik `userservice`. Ia hanya tahu interface
`UserClient` dan DTO `UserResponse` versinya sendiri. Yang mengisi interface itu adalah
`common/client/LocalUserClient` — satu-satunya kelas di aplikasi ini yang melihat kedua sisi.

**Database:** satu instance PostgreSQL, **satu schema**. Di `dev` tiap service punya schema
sendiri (`auth_schema`, `user_schema`, `note_schema`) lewat `hibernate.default_schema`; satu
aplikasi cuma punya satu konfigurasi JPA, jadi pemisahan itu tidak terbawa. Snapshot lengkap:
[`schema.sql`](schema.sql).

**Letak kode:** satu modul Maven di `backend/`. Root repo berisi `docker-compose.yml`,
`render.yaml`, `docs/`, dan `frontend/`.

---

## 4. Materi / Konsep yang Dipelajari

### Spring Boot inti
- **Dependency Injection** — constructor injection via `@RequiredArgsConstructor` pada field `final`
- **MVC** — `@RestController`, `@RequestHeader`, `@PathVariable`, `@RequestParam`, `@PageableDefault`
- **Validation** — `@Valid`, `@NotBlank`, `@Email`, `@Size`, termasuk validasi elemen koleksi (`Set<@NotBlank @Size(max = 30) String>`)
- **Exception handling** — satu `@RestControllerAdvice` untuk seluruh aplikasi, termasuk `MissingRequestHeaderException` → `401`
- **Layered architecture** — Controller → Service (interface) → ServiceImpl → Repository

### Data / JPA
- **`@ElementCollection`** — tag sebagai `Set<String>` dengan tabel `note_tags`, tanpa entity terpisah
- **`@OneToMany` + `cascade` + `orphanRemoval`** — share ikut terhapus bersama note-nya
- **`@UniqueConstraint`** — satu share per (note, user tujuan), dijaga di level database
- **JPQL buatan tangan** — satu query untuk tiga kombinasi filter (tanpa filter / tag / teks) dengan parameter `null`, plus `countQuery` eksplisit karena join menggandakan baris
- **Pagination & sorting** — `Pageable` dibungkus `PageResponse<T>`
- **Auditing** — `@CreatedDate`/`@LastModifiedDate` lewat `BaseEntity` + `@EnableJpaAuditing`
- **`open-in-view=false`** — method baca yang menyentuh koleksi lazy diberi `@Transactional`
- **Urutan `@EnableJpaAuditing`** — kalau `@EnableJpaRepositories` ditulis eksplisit, EntityManagerFactory bisa dibangun lebih dulu daripada config auditing dan `created_at` jadi NULL di setiap insert

### Security
- **JWT** — dibuat (sign) di `authservice`, diverifikasi (verify) di `common/security/JwtAuthFilter`
- **BCrypt** — kolom dinamai `password_hash` supaya tidak ada yang mengira isinya plaintext
- **Satu titik verifikasi** — token dicek sekali, di filter paling depan
- **Claims propagation** — `userId`, `email`, `role`, `name` dari token → header `X-User-*`
- **`HttpServletRequestWrapper`** — servlet tidak mengizinkan header request diubah, jadi request dibungkus untuk membuang `X-User-*` kiriman client lalu memasang versi server
- **Anti-spoofing** — header `X-User-*` dari luar selalu dibuang, di path publik maupun terlindungi
- **Autentikasi vs otorisasi** — filter membuktikan *siapa*, service memutuskan *boleh apa*
- **Ownership + share check** — baca boleh untuk pemilik **atau** penerima share; ubah/hapus/share hanya pemilik
- **CORS** — `CorsConfigurationSource` dipasang ke `SecurityFilterChain`; preflight `OPTIONS` lolos tanpa token

### Batas modul tanpa jaringan
- **Interface sebagai batas** — `UserClient` memisahkan note dari user meski satu proses
- **Anti-corruption layer** — `UserResponse` milik note adalah salinan, bukan class bersama
- **`Optional` sebagai kontrak** — "tidak ketemu" adalah jawaban yang sah dari pencarian; pemanggil yang menentukan pesan errornya
- **Snapshot antar domain** — email tujuan disalin ke `note_shares`, jadi daftar share tidak perlu lookup
- **Menghindari transaksi lintas domain** — profil dibuat lazy dari klaim token, bukan dipanggil saat register

### Tooling
- **OpenAPI + Scalar** — spec otomatis dari controller, satu halaman di `/docs.html`
- **Docker** — multi-stage build, Compose dengan healthcheck Postgres, batas memori 512 MB
- **Tuning JVM untuk container** — `MaxRAMPercentage`, `UseSerialGC`, `Xss512k`
- **Secrets hygiene** — semua nilai sensitif lewat environment variable; tidak ada `application.properties` yang perlu di-gitignore

---

## 5. Request Flow (alur penting)

### A. Register + profil pertama kali
```
POST /api/auth/register {displayName, email, password}
  → JwtAuthFilter: path publik, buang header X-User-* palsu, teruskan
  → AuthServiceImpl.register():
      existsByEmail? → 409 EmailAlreadyExistsException
      simpan User (password di-hash BCrypt, role USER)
      JwtService.generateToken() → klaim: sub=email, role, userId, name
  → 201 + accessToken
  (TIDAK ada pembuatan profil di sini)

GET /api/users/me   (Authorization: Bearer <token>)
  → JwtAuthFilter: verifikasi token → pasang X-User-Id / X-User-Email / X-User-Name / X-User-Role
  → ProfileServiceImpl.getMyProfile(): findByUserId → tidak ada → buat dari header
  → 200 + profil
```
**Kenapa begitu?** Ini warisan desain microservices yang sengaja dipertahankan. Di `dev`,
register yang memanggil user-service bisa gagal separuh jalan — user tersimpan, profil tidak.
Find-or-create dari klaim token menghilangkan kemungkinan itu. Di monolith satu `@Transactional`
sebenarnya sudah cukup, tapi polanya dibiarkan supaya kedua branch berperilaku identik.

### B. Share note
```
POST /api/notes/{id}/share {targetEmail}   (token milik A)
  → JwtAuthFilter → X-User-Id = A → NoteController
  → NoteServiceImpl.shareNote(id, A, request):
      1. findNoteOrThrow(id)             → 404 kalau tidak ada
      2. requireOwner(note, A)           → 403 kalau bukan pemilik (SEBELUM lookup user)
      3. userClient.findByEmail(email)   → LocalUserClient → ProfileService
           Optional kosong → UserNotFoundException → 404
      4. target == A?                    → 400 "You cannot share a note with yourself"
      5. sudah pernah di-share ke target? → kembalikan share lama (idempoten)
         belum → simpan NoteShare (snapshot email, permission READ)
  → 201 + share
```
Di `dev` langkah 3 adalah panggilan HTTP yang bisa gagal karena jaringan, dan `502` adalah
status yang mungkin. Di sini `502` mustahil — tidak ada jaringan di antaranya.

### C. Membaca note milik orang lain (jalur 403)
```
GET /api/notes/{id}  (token milik B, note milik A)
  → NoteServiceImpl.getNoteById(id, B)
      note.ownerId == B?                          → tidak
      existsByNoteIdAndSharedWithUserId(id, B)?   → tidak
      → ForbiddenException → 403
  (Kalau ada baris share: 200 dengan owned=false)
```
Dua nilai yang dibandingkan punya asal berbeda: `id` dari client (bisa apa saja), `X-User-Id`
dari token terverifikasi (tidak bisa dipalsukan). Perbandingan itulah otorisasinya.

### D. Daftar "dibagikan ke saya"
```
GET /api/notes/shared-with-me
  → findNotesSharedWith(userId)   (join note_shares → notes)
  → untuk tiap note: userClient.findById(ownerId)
     hasil di-cache per request → pemilik yang sama hanya ditanya sekali
  → tiap item berisi ownerEmail + ownerDisplayName, owned=false
```
Cache per request tetap dipertahankan meski sekarang cuma query database: lima note dari satu
pemilik tetap satu query, bukan lima.

### Model otorisasi — siapa memutuskan apa

| Lapisan | Memutuskan | Contoh |
|---|---|---|
| `JwtAuthFilter` | Token asli? | `401` tanpa token atau token kedaluwarsa |
| Controller | — (hanya mengambil identitas dari header) | `@RequestHeader("X-User-Id")` |
| Service | Pemilik? Penerima share? | `requireOwner()` · cek `note_shares` di `getNoteById` |

NoteNest tidak punya aturan berbasis role — `ADMIN` ada di enum tapi tidak dipakai endpoint
mana pun. Semua otorisasi berbasis kepemilikan, dan itu butuh membaca entity, jadi tempatnya
di service.

---

## 6. Cara Menjalankan

### Docker Compose (disarankan)
```bash
cp .env.example .env          # lalu isi nilainya
docker compose up --build
```
Detail tiap baris konfigurasinya: [`DOCKER-STEPS.md`](DOCKER-STEPS.md).

### Dari IDE
**Prasyarat:** Java 21, PostgreSQL (boleh container `postgres` dari Compose).

Tidak ada `application.properties` yang perlu disalin — semua nilai datang dari environment
variable. Set empat variabel ini, lalu jalankan `NoteNestApplication`:

```
DB_URL=jdbc:postgresql://localhost:5432/notenest_db
DB_USER=notenest
DB_PASSWORD=<password>
JWT_SECRET=<hasil: openssl rand -base64 48>
```

`JWT_SECRET` harus Base64 yang sah — `JwtAuthFilter` men-decode-nya sebagai Base64, bukan
teks biasa.

**Akses:**
- API: http://localhost:8080
- Dokumentasi API (Scalar): http://localhost:8080/docs.html
- Health check: http://localhost:8080/actuator/health

---

## 7. Ringkasan Endpoint

Semua di `http://localhost:8080`. Selain register dan login, semua butuh header
`Authorization: Bearer <token>`.

Kolom **Akses**: **publik** · **login** (token saja cukup) · **pemilik** (`403` kalau bukan) ·
**pemilik/penerima** (pemilik atau ada di `note_shares`)

| Method | Path | Fungsi | Akses |
|--------|------|--------|-------|
| POST | /api/auth/register | daftar akun, dapat token (selalu role USER) | publik |
| POST | /api/auth/login | login, dapat token | publik |
| GET | /api/auth/validate | cek token masih hidup | login |
| GET | /api/users/me | profil saya (dibuat otomatis saat pertama kali) | login |
| PUT | /api/users/me | ubah displayName, bio, avatarUrl | login |
| GET | /api/users/search?email= | cari user (maks 10, diri sendiri tidak ikut) | login |
| POST | /api/notes | buat note | login |
| GET | /api/notes?tag=&q=&page=&size=&sort= | note milik saya (paginated) | login |
| GET | /api/notes/shared-with-me | note orang lain yang dibagikan ke saya | login |
| GET | /api/notes/{id} | detail note | pemilik/penerima |
| PUT | /api/notes/{id} | ubah note (tag diganti seluruhnya) | pemilik |
| DELETE | /api/notes/{id} | hapus note + semua share-nya | pemilik |
| GET | /api/notes/{id}/shares | daftar penerima share | pemilik |
| POST | /api/notes/{id}/share | share ke email | pemilik |
| DELETE | /api/notes/{id}/share/{userId} | cabut akses | pemilik |

**15 endpoint, sama persis dengan `dev`.** Yang hilang cuma dua endpoint `/internal/users/**`
— itu memang pintu untuk Feign, bukan untuk client, dan di `dev` pun selalu dijawab `403`
dari luar.

**Format response** selalu `{ "success": bool, "message": string, "data": ... }`, termasuk
error. Error validasi mengisi `data` dengan peta `field → pesan`.

**Kode status yang perlu dikenali client:**

| Status | Arti | Dari |
|---|---|---|
| `400` | validasi gagal, atau share ke diri sendiri | service |
| `401` | token tidak ada/salah/kedaluwarsa, atau login salah | `JwtAuthFilter`, `authservice` |
| `403` | bukan pemilik/penerima | `noteservice` |
| `404` | note tidak ada, email tujuan tidak terdaftar, share tidak ada | `noteservice` |
| `409` | email sudah terdaftar | `authservice` |

`502` ada di daftar `dev` tapi **tidak di sini** — status itu khusus "user-service tidak
terjangkau", dan tidak ada service terpisah yang bisa tidak terjangkau.

Spec OpenAPI mentah: `/v3/api-docs` (satu spec, bukan tiga).

---

## 8. Keputusan Desain & Penyederhanaan (disengaja)

| Penyederhanaan | Alasan | Solusi "proper" |
|----------------|--------|-----------------|
| Backend dirakit jadi satu aplikasi | Free tier hanya memberi satu service 512 MB tanpa private networking | Bayar, atau VPS yang menjalankan `dev` apa adanya |
| Satu schema, bukan schema-per-service | Satu aplikasi hanya punya satu konfigurasi JPA | `@Table(schema = ...)` per entity kalau pemisahan itu tetap diinginkan |
| Share tidak realtime | WebSocket/SSE menambah kompleksitas besar | WebSocket / Server-Sent Events |
| Permission hanya `READ` | Cukup untuk menunjukkan konsep sharing; enum sudah siap ditambah | `READ` / `EDIT` / `ADMIN` |
| Profil dibuat lazy, bukan saat register | Warisan desain microservices; dipertahankan supaya kedua branch identik | Di monolith: satu `@Transactional` saat register |
| Email/nama di profil adalah salinan klaim token | Profil tidak perlu memanggil sisi auth | Baca langsung dari `users` kalau batasnya memang mau dilepas |
| Tidak ada circuit breaker / tracing / cache | Bukan fundamental untuk scope ini | Resilience4j, Zipkin, Redis |
| Tidak ada refresh token | Satu access token (24 jam) cukup untuk scope ini | Refresh token + rotasi, blacklist untuk logout instan |
| `ddl-auto=update`, bukan migrasi | Cukup untuk dev dan demo | Flyway |

---

## 9. Struktur Folder

```
notenest/
├── backend/                         satu modul Maven
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       ├── main/java/com/notenest/
│       │   ├── NoteNestApplication.java
│       │   ├── common/              lapisan perakitan — hanya ada di branch ini
│       │   ├── authservice/
│       │   ├── userservice/
│       │   └── noteservice/
│       ├── main/resources/          application.properties + static/docs.html
│       └── test/java/com/notenest/
├── frontend/                        aplikasi web — lihat frontend/README.md
├── docs/                            semua dokumentasi — mulai dari docs/README.md
├── docker-compose.yml               1 aplikasi + 1 database
├── render.yaml                      blueprint deploy
└── README.md
```

Isi `common/` — kelas-kelas yang lahir dari perakitan, tidak ada padanannya di `dev`:

```
common/
├── security/        JwtAuthFilter (servlet), SecurityConfig
├── exception/       GlobalExceptionHandler gabungan
├── config/          CorsConfig
├── client/          LocalUserClient
└── dto/             ApiResponse
```

Di dalam tiap paket domain:

```
com/notenest/<domain>/
├── controller/      REST endpoint (@RestController)
├── dto/
│   ├── request/     input (divalidasi @Valid)
│   └── response/    output
├── entity/          JPA entity (+ BaseEntity)
├── exception/       custom exception (handler-nya di common/)
├── repository/      Spring Data JPA interface
├── service/         interface (kontrak)
│   └── impl/        implementasi (logic) — *ServiceImpl
├── (security/)      khusus authservice — JwtService, UserDetailsServiceImpl
└── (client/)        khusus noteservice — interface UserClient + UserResponse
```

Untuk **urutan membuatnya** — file mana dulu dan kenapa — lihat [`BUILD-ORDER.md`](BUILD-ORDER.md).
