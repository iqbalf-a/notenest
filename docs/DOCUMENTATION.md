# NoteNest — Dokumentasi Project

REST API aplikasi catatan dengan fitur berbagi (share), berbasis **microservices** dengan Spring Boot & Spring Cloud.
*Sister project* dari ShopNest: arsitekturnya sama, domainnya jauh lebih kecil, supaya backend **dan** frontend-nya bisa selesai.

---

## 1. Ringkasan

NoteNest memecah aplikasi catatan menjadi tiga service domain (auth, user, note) yang berdiri sendiri, saling terhubung lewat HTTP, dengan satu pintu masuk (API Gateway) dan satu direktori alamat (Eureka). Setiap service punya schema database sendiri dan tanggung jawab tunggal.

**Kenapa domain catatan, bukan e-commerce lagi?**
ShopNest (auth/user/product/order + konsep keranjang dan checkout) terlalu besar untuk diselesaikan sampai frontend. NoteNest mempertahankan **semua konsep arsitekturnya** — service discovery, JWT di gateway, otorisasi berbasis kepemilikan, schema-per-service, Feign antar service, endpoint internal — dengan domain yang jauh lebih ringan.

**Kenapa fitur share dipertahankan?**
Supaya kebutuhan Feign-nya **asli**, bukan dipaksakan. Client mengirim email tujuan, sedangkan note-service hanya bisa menyimpan `userId` — dan hanya user-service yang tahu pemetaan email → userId. Posisinya sama dengan order-service yang harus bertanya ke product-service di ShopNest.

---

## 2. Tech Stack

| Kategori | Teknologi |
|----------|-----------|
| Bahasa | Java 21 |
| Framework | Spring Boot 3.4.6 |
| Cloud | Spring Cloud 2024.0.1 (Eureka, Gateway, OpenFeign, Config) |
| Security | Spring Security + JWT (library jjwt 0.12.5) |
| Database | PostgreSQL 16 (container Docker), schema-per-service |
| ORM | Spring Data JPA + Hibernate |
| Dokumentasi API | springdoc-openapi + Scalar UI |
| Testing | JUnit 5 + Mockito, H2 untuk test repository |
| Build | Maven (wrapper disertakan) |
| Boilerplate | Lombok |

---

## 3. Arsitektur

| Service | Port | Tanggung jawab | Status |
|---------|------|----------------|--------|
| Eureka Server | 8761 | Service discovery (buku telepon service) | ✅ |
| Config Server | 8888 | Config terpusat, mode `native` | ✅ dibangun, belum dikonsumsi service lain |
| API Gateway | 8080 | Pintu masuk + JWT filter + CORS + blok `/internal/**` + halaman docs | ✅ |
| Auth Service | 8081 | Register, login, penerbit JWT | ✅ |
| User Service | 8082 | Profil (dibuat otomatis), cari user, endpoint internal | ✅ |
| Note Service | 8083 | CRUD note, tag, share (Feign → user-service) | ✅ |

**Dua jalur komunikasi:**
1. **Client → Gateway → Service** (jalur luar; kena JWT filter). Gateway memakai `lb://nama-service` → tanya Eureka.
2. **Service → Service** (jalur internal; note → user via OpenFeign `@FeignClient(name = "user-service")`). Tidak lewat gateway.

**Database:** satu instance PostgreSQL, **schema terpisah** per service (`auth_schema`, `user_schema`, `note_schema`). Snapshot lengkapnya di [`schema.sql`](schema.sql).

**Letak kode:** semua modul Maven ada di `backend/`. Root repo hanya berisi `docker-compose.yml`, `docs/`, dan `frontend/`.

---

## 4. Materi / Konsep yang Dipelajari

### Spring Boot inti
- **Dependency Injection** — constructor injection via `@RequiredArgsConstructor` pada field `final`
- **MVC** — `@RestController`, `@RequestHeader`, `@PathVariable`, `@RequestParam`, `@PageableDefault`
- **Validation** — `@Valid`, `@NotBlank`, `@Email`, `@Size`, termasuk validasi elemen koleksi (`Set<@NotBlank @Size(max = 30) String>`)
- **Exception handling** — `@RestControllerAdvice` per service, termasuk `MissingRequestHeaderException` → `401`
- **Layered architecture** — Controller → Service (interface) → ServiceImpl → Repository

### Data / JPA
- **`@ElementCollection`** — tag sebagai `Set<String>` dengan tabel `note_tags`, tanpa entity terpisah
- **`@OneToMany` + `cascade` + `orphanRemoval`** — share ikut terhapus bersama note-nya
- **`@UniqueConstraint`** — satu share per (note, user tujuan), dijaga di level database
- **JPQL buatan tangan** — satu query untuk tiga kombinasi filter (tanpa filter / tag / teks) dengan parameter `null`, plus `countQuery` eksplisit karena join menggandakan baris
- **Pagination & sorting** — `Pageable` dibungkus `PageResponse<T>`
- **Auditing** — `@CreatedDate`/`@LastModifiedDate` lewat `BaseEntity` + `@EnableJpaAuditing`
- **`open-in-view=false`** — method baca yang menyentuh koleksi lazy diberi `@Transactional`

### Security
- **JWT** — dibuat (sign) di auth-service, diverifikasi (verify) di gateway
- **BCrypt** — kolom dinamai `password_hash` supaya tidak ada yang mengira isinya plaintext
- **Perimeter security** — token dicek sekali di gateway
- **Claims propagation** — `userId`, `email`, `role`, `name` dari token → header `X-User-*`
- **Anti-spoofing** — header `X-User-*` kiriman client dibuang di path publik, ditimpa di path terlindungi
- **Autentikasi vs otorisasi** — gateway membuktikan *siapa*, note-service memutuskan *boleh apa*
- **Ownership + share check** — baca boleh untuk pemilik **atau** penerima share; ubah/hapus/share hanya pemilik
- **Internal endpoint** — `/internal/**` ditolak gateway dengan `403`, hanya terjangkau lewat Feign
- **CORS** — `CorsWebFilter` di gateway; preflight dijawab sebelum filter JWT

### Spring Cloud / Microservices
- **Service discovery** — Eureka
- **API Gateway** — routing `lb://`, predicate `Path=`, filter `SetPath` untuk spec docs
- **OpenFeign** — `UserClient`, termasuk **menerjemahkan** `FeignException.NotFound` jadi exception domain dan memetakan sisa error Feign ke `502`
- **Feign timeout eksplisit** — connect 3 detik, read 5 detik
- **Snapshot antar service** — email tujuan disalin ke `note_shares`, jadi daftar share tidak perlu Feign
- **Menghindari distributed transaction** — profil dibuat lazy dari klaim token, bukan dipanggil saat register

### Tooling
- **OpenAPI + Scalar** — spec otomatis dari controller, satu halaman untuk tiga service
- **Docker** — multi-stage build, Compose dengan healthcheck Postgres
- **Secrets hygiene** — `application.properties` di-gitignore, `.example` di-commit

---

## 5. Request Flow (alur penting)

### A. Register + profil pertama kali
```
POST /api/auth/register {displayName, email, password}
  → Gateway: path publik, buang header X-User-* palsu, teruskan
  → AuthServiceImpl.register():
      existsByEmail? → 409 EmailAlreadyExistsException
      simpan User (password di-hash BCrypt, role USER)
      JwtService.generateToken() → klaim: sub=email, role, userId, name
  → 201 + accessToken
  (TIDAK ada panggilan ke user-service)

GET /api/users/me   (Authorization: Bearer <token>)
  → Gateway: verifikasi token → set X-User-Id / X-User-Email / X-User-Name / X-User-Role
  → ProfileServiceImpl.getMyProfile(): findByUserId → tidak ada → buat dari header
  → 200 + profil
```
**Kenapa begitu?** Kalau register memanggil user-service dan panggilan itu gagal, user sudah tersimpan tapi profilnya tidak — kasus "gagal separuh jalan" yang butuh Saga untuk dibereskan. Dengan find-or-create dari klaim token, register tidak pernah bisa gagal separuh.

### B. Share note (jalur Feign)
```
POST /api/notes/{id}/share {targetEmail}   (token milik A)
  → Gateway JwtAuthFilter → X-User-Id = A → note-service
  → NoteServiceImpl.shareNote(id, A, request):
      1. findNoteOrThrow(id)             → 404 kalau tidak ada
      2. requireOwner(note, A)           → 403 kalau bukan pemilik (SEBELUM Feign dipanggil)
      3. UserClient.getUserByEmail(email)  [Feign → user-service /internal/users/by-email]
           404 → UserNotFoundException → 404 "No NoteNest user registered with email"
           mati / 5xx → FeignException → 502 "Failed to reach user-service"
      4. target == A?                    → 400 "You cannot share a note with yourself"
      5. sudah pernah di-share ke target? → kembalikan share lama (idempoten)
         belum → simpan NoteShare (snapshot email, permission READ)
  → 201 + share
```

### C. Membaca note milik orang lain (jalur 403)
```
GET /api/notes/{id}  (token milik B, note milik A)
  → NoteServiceImpl.getNoteById(id, B)
      note.ownerId == B?                          → tidak
      existsByNoteIdAndSharedWithUserId(id, B)?   → tidak
      → ForbiddenException → 403
  (Kalau ada baris share: 200 dengan owned=false)
```
Dua nilai yang dibandingkan punya asal berbeda: `id` dari client (bisa apa saja), `X-User-Id` dari token terverifikasi (tidak bisa dipalsukan). Perbandingan itulah otorisasinya.

### D. Daftar "dibagikan ke saya"
```
GET /api/notes/shared-with-me
  → findNotesSharedWith(userId)   (join note_shares → notes)
  → untuk tiap note: lookup pemilik via Feign /internal/users/{id}
     hasil di-cache per request → pemilik yang sama hanya ditanya sekali
  → tiap item berisi ownerEmail + ownerDisplayName, owned=false
```

### Model otorisasi — siapa memutuskan apa

| Lapisan | Memutuskan | Contoh |
|---|---|---|
| Gateway | Token asli? Path ini boleh dari luar? | `401` tanpa token · `403` untuk `/internal/**` |
| Controller | — (hanya mengambil identitas dari header) | `@RequestHeader("X-User-Id")` |
| Service | Pemilik? Penerima share? | `requireOwner()` · cek `note_shares` di `getNoteById` |

NoteNest tidak punya aturan berbasis role — `ADMIN` ada di enum tapi tidak dipakai endpoint mana pun. Semua otorisasi berbasis kepemilikan, dan itu butuh membaca entity, jadi tempatnya di service.

---

## 6. Cara Menjalankan

### Docker Compose (disarankan)
```bash
docker compose up -d --build
```
Detail tiap baris konfigurasinya: [`DOCKER-STEPS.md`](DOCKER-STEPS.md).

### Lokal per service
**Prasyarat:** Java 21, PostgreSQL (boleh container `postgres` dari Compose yang membuka `5432`).

Setiap service di `backend/`: copy `application.properties.example` → `application.properties`, isi kredensial. `jwt.secret` di auth-service dan api-gateway **harus sama**.

Urutan start:
```
1. eureka-server  (8761)   cd backend/eureka-server && ./mvnw spring-boot:run
2. auth-service   (8081)
3. user-service   (8082)
4. note-service   (8083)
5. api-gateway    (8080)   ← terakhir
   config-server  (8888)   opsional, tidak ada yang bergantung padanya
```

**Akses:**
- Eureka dashboard: http://localhost:8761
- API (semua lewat gateway): http://localhost:8080
- Dokumentasi API (Scalar): http://localhost:8080/docs.html

---

## 7. Ringkasan Endpoint

Semua diakses lewat gateway `http://localhost:8080`. Selain register dan login, semua butuh header `Authorization: Bearer <token>`.

Kolom **Akses**: **publik** · **login** (token saja cukup) · **pemilik** (`403` kalau bukan) · **pemilik/penerima** (pemilik atau ada di `note_shares`) · **internal** (ditolak gateway, hanya Feign)

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
| POST | /api/notes/{id}/share | share ke email (Feign ke user-service) | pemilik |
| DELETE | /api/notes/{id}/share/{userId} | cabut akses | pemilik |
| GET | /internal/users/{id} | ringkasan user by id | **internal** |
| GET | /internal/users/by-email?email= | ringkasan user by email | **internal** |

**Format response** selalu `{ "success": bool, "message": string, "data": ... }`, termasuk error dari gateway. Error validasi mengisi `data` dengan peta `field → pesan`.

**Kode status yang perlu dikenali client:**

| Status | Arti | Dari |
|---|---|---|
| `400` | validasi gagal, atau share ke diri sendiri | service |
| `401` | token tidak ada/salah/kedaluwarsa, atau login salah | gateway, auth-service |
| `403` | bukan pemilik/penerima, atau path internal | note-service, gateway |
| `404` | note tidak ada, email tujuan tidak terdaftar, share tidak ada | note-service |
| `409` | email sudah terdaftar | auth-service |
| `502` | user-service tidak terjangkau saat share / shared-with-me | note-service |

Spec OpenAPI mentah: `/docs/specs/auth`, `/docs/specs/users`, `/docs/specs/notes`.

---

## 8. Keputusan Desain & Penyederhanaan (disengaja)

| Penyederhanaan | Alasan | Solusi "proper" |
|----------------|--------|-----------------|
| Share tidak realtime | WebSocket/SSE menambah kompleksitas besar | WebSocket / Server-Sent Events |
| Permission hanya `READ` | Cukup untuk menunjukkan konsep sharing; enum sudah siap ditambah | `READ` / `EDIT` / `ADMIN` |
| Profil dibuat lazy, bukan saat register | Menghindari transaksi lintas service | Event `UserRegistered` lewat message broker |
| Email/nama di profil adalah salinan klaim token | Profil tidak perlu memanggil auth-service | Sinkronisasi lewat event kalau email bisa diubah |
| Satu DB instance, schema per service | Sederhana untuk dev | Database fisik terpisah per service |
| Tidak ada circuit breaker / tracing / cache | Bukan fundamental untuk scope ini | Resilience4j, Zipkin, Redis |
| Endpoint internal hanya dijaga gateway | Di Compose port service tidak di-publish | Token service-to-service bertanda tangan atau mTLS |
| Tidak ada refresh token | Satu access token (24 jam) cukup untuk scope ini | Refresh token + rotasi, blacklist untuk logout instan |
| Config Server belum dikonsumsi | Env var sudah cukup di skala ini | Tambah `spring-cloud-starter-config` ke tiap service |

---

## 9. Struktur Folder

```
notenest/
├── backend/
│   ├── eureka-server/
│   ├── config-server/
│   ├── config-repo/          config bersama yang di-serve config-server
│   ├── api-gateway/          + static/docs.html (Scalar)
│   ├── auth-service/
│   ├── user-service/
│   └── note-service/
├── frontend/                 aplikasi web (belum dimulai — lihat frontend/README.md)
├── docs/                     semua dokumentasi — mulai dari docs/README.md
├── docker-compose.yml
└── README.md
```

Di dalam tiap service domain:

```
backend/<service>/src/main/java/com/notenest/<service>/
├── config/          @EnableJpaAuditing
├── controller/      REST endpoint (@RestController)
├── dto/
│   ├── request/     input (divalidasi @Valid)
│   └── response/    output (ApiResponse<T> wrapper)
├── entity/          JPA entity (+ BaseEntity)
├── exception/       custom exception + GlobalExceptionHandler
├── repository/      Spring Data JPA interface
├── service/         interface (kontrak)
│   └── impl/        implementasi (logic) — *ServiceImpl
├── (security/)      khusus auth-service
└── (client/)        Feign client — khusus note-service
```

Untuk **urutan membuatnya** — file mana dulu dan kenapa — lihat [`BUILD-ORDER.md`](BUILD-ORDER.md).
