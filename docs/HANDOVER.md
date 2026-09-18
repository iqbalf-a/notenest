# NoteNest — Handover: Membangun BE (mengikuti pola ShopNest)

> **Dokumen historis — sengaja tidak diperbarui.**
> Ini blueprint yang ditulis **sebelum** ada satu baris kode pun, dan menggambarkan bentuk
> microservices yang sekarang ada di branch `dev`. Nilainya justru pada jarak antara rencana
> dan hasil; menyuntingnya menghapus catatan itu.
>
> Branch ini (`deploy/monolith`) menjalankan backend sebagai **satu aplikasi**. Selisihnya
> dengan blueprint ada di [`README.md`](README.md#selisih-handovermd-dengan-kode); alasan
> arsitekturnya dirakit ulang ada di [`DEPLOY-MONOLITH.md`](DEPLOY-MONOLITH.md).

Dokumen ini adalah blueprint untuk membangun backend **NoteNest** — aplikasi notes dengan fitur sharing, dibangun dengan arsitektur microservices yang sama persis dengan [`shopnest`](../../shopnest), tapi domain lebih kecil (3 service domain, bukan 4) supaya realistis diselesaikan sebagai portfolio piece.

> Referensi struktur nyata: [`shopnest/auth-service`](../../shopnest/auth-service), [`shopnest/order-service`](../../shopnest/order-service) (contoh service dengan Feign client), [`shopnest/api-gateway`](../../shopnest/api-gateway), [`shopnest/docs/BUILD-ORDER.md`](../../shopnest/docs/BUILD-ORDER.md) (filosofi urutan membangun).

---

## 1. Kenapa proyek ini ada

ShopNest terlalu kompleks buat FE diselesaikan (4 domain: auth/user/product/order, konsep cart/checkout). NoteNest mempertahankan **semua konsep arsitektur** yang sama (service discovery, JWT-at-gateway, ownership-based authorization, schema-per-service, Feign inter-service call, internal-only endpoint) tapi dengan domain notes yang jauh lebih ringan, supaya BE **dan** FE-nya sama-sama bisa selesai.

Fitur sharing (note bisa dibagikan read-only ke user lain) sengaja dipertahankan supaya tetap ada kebutuhan Feign call yang genuine — mirror alasan `order-service` butuh memanggil `product-service`, bukan Feign yang dipaksakan tanpa alasan bisnis.

---

## 2. Tech stack

Identik dengan ShopNest:

| Kategori | Teknologi |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.4.6 |
| Cloud / Microservices | Spring Cloud 2024.0.1 — Eureka, Gateway, OpenFeign, Config Server |
| Security | Spring Security, JWT (`jjwt`) |
| Database | PostgreSQL (schema-per-service) |
| ORM | Spring Data JPA / Hibernate |
| API Docs | springdoc-openapi + Scalar |
| Testing | JUnit 5 + Mockito |
| Containerization | Docker (multi-stage) + Docker Compose |
| Build | Maven |

---

## 3. Arsitektur

```
                         ┌──────────────────┐
                         │ Client / Postman │
                         └────────┬─────────┘
                                  │ (satu-satunya entry point publik)
                          ┌───────▼────────┐
                          │  API Gateway    │  :8080
                          │  + JWT filter   │
                          └───────┬────────┘
              ┌───────────┬───────┴───────┐
              ▼           ▼               ▼
        ┌──────────┐┌──────────┐   ┌─────────────┐
        │  Auth    ││  User    │   │  Note       │
        │  :8081   ││  :8082   │   │  :8083      │
        └────┬─────┘└────┬─────┘   └──────┬──────┘
             │            │       ▲        │
             │            │       │ Feign  │ (share note →
             │            │       └────────┘  validasi target user)
             ▼            ▼                  ▼
        ┌─────────────────────────────────────────────┐
        │      PostgreSQL — satu schema per service     │
        └─────────────────────────────────────────────┘

        Semua service daftar ke Eureka Server (:8761).
        Config terpusat via Config Server (:8888) + config-repo.
```

- **Client → Gateway → Service**: satu-satunya jalur eksternal. Gateway verifikasi JWT, teruskan identitas sebagai header `X-User-Id` / `X-User-Email` / `X-User-Role` — service percaya gateway, bukan client.
- **Service → Service**: internal only. `note-service` memanggil `user-service` lewat OpenFeign saat share note, bypass gateway.
- **Database**: satu instance PostgreSQL, schema-per-service (`auth_schema`, `user_schema`, `note_schema`).

---

## 4. Service breakdown & struktur folder

```
notenest/
│
├── eureka-server/                  → copy hampir 1:1 dari shopnest
├── config-server/                  → copy hampir 1:1 (port :8888, mode native)
├── config-repo/
│   └── application.properties      → shared config (eureka url, dll)
│
├── api-gateway/                    → :8080, struktur & logic JWT filter dipakai ulang
│   └── src/main/java/.../apigateway/
│       ├── config/CorsConfig.java
│       └── filter/JwtAuthFilter.java
│
├── auth-service/                   → :8081, copy struktur persis dari shopnest
│   └── src/main/java/.../authservice/
│       ├── config/JpaConfig.java
│       ├── controller/AuthController.java
│       ├── dto/request/{LoginRequest,RegisterRequest}.java
│       ├── dto/response/{ApiResponse,AuthResponse}.java
│       ├── entity/{BaseEntity,Role,User}.java
│       ├── exception/{EmailAlreadyExistsException,GlobalExceptionHandler}.java
│       ├── repository/UserRepository.java
│       ├── security/{JwtAuthFilter,JwtService,SecurityConfig,UserDetailsServiceImpl}.java
│       └── service/{AuthService, impl/AuthServiceImpl}.java
│
├── user-service/                   → :8082, baru, niru pola product-service
│   └── src/main/java/.../userservice/
│       ├── config/JpaConfig.java
│       ├── controller/UserController.java
│       ├── dto/request/UpdateProfileRequest.java
│       ├── dto/response/{ApiResponse,ProfileResponse}.java
│       ├── entity/{BaseEntity,Profile}.java
│       ├── exception/{ProfileNotFoundException,GlobalExceptionHandler}.java
│       ├── repository/ProfileRepository.java
│       └── service/{ProfileService, impl/ProfileServiceImpl}.java
│
└── note-service/                   → :8083, baru, niru pola order-service (punya Feign client)
    └── src/main/java/.../noteservice/
        ├── client/
        │   ├── UserClient.java                    (Feign, niru ProductClient)
        │   └── dto/{ClientApiResponse,UserResponse}.java
        ├── config/JpaConfig.java
        ├── controller/NoteController.java
        ├── dto/request/{NoteRequest,ShareNoteRequest}.java
        ├── dto/response/{ApiResponse,PageResponse,NoteResponse,ShareResponse}.java
        ├── entity/{BaseEntity,Note,NoteTag,NoteShare}.java
        ├── exception/{NoteNotFoundException,ForbiddenException,UserNotFoundException,GlobalExceptionHandler}.java
        ├── repository/{NoteRepository,NoteShareRepository}.java
        └── service/{NoteService, impl/NoteServiceImpl}.java
```

Tiap service (kecuali eureka/config-server) punya 3 file config seperti ShopNest: `application.properties` (lokal, di-gitignore), `application-docker.properties` (buat compose), `application.properties.example` (template, di-commit).

---

## 5. Entity & schema

**auth-service** — `auth_schema.users`
`id (UUID), email, password_hash, role, created_at, updated_at`

**user-service** — `user_schema.profiles`
`id (UUID), user_id, display_name, bio, avatar_url, created_at, updated_at`

**note-service** — `note_schema`
- `notes`: `id, owner_id, title, content, created_at, updated_at`
- `note_tags`: `note_id, tag` (many-to-many sederhana)
- `note_shares`: `id, note_id, shared_with_user_id, permission (READ), created_at`

Keputusan desain sama seperti ShopNest: `UUID` untuk primary key, `BaseEntity` (`@MappedSuperclass`) untuk `createdAt`/`updatedAt`, `@EnableJpaAuditing` di tiap `JpaConfig`.

---

## 6. Endpoint (lewat gateway, prefix `/api/...`)

**auth-service**
- `POST /api/auth/register {email, password, displayName}`
- `POST /api/auth/login {email, password}` → JWT

**user-service**
- `GET /api/users/me`
- `PUT /api/users/me {displayName, bio}`
- `GET /api/users/search?email=` — buat fitur "cari user untuk di-share"
- `GET /internal/users/{id}` — **internal-only**, diblokir di gateway, hanya bisa diakses lewat Feign

**note-service**
- `POST /api/notes {title, content, tags[]}`
- `GET /api/notes?tag=&q=&page=`
- `GET /api/notes/shared-with-me`
- `GET /api/notes/{id}` — owner atau ada entry di `note_shares`
- `PUT /api/notes/{id}` — owner only
- `DELETE /api/notes/{id}` — owner only
- `POST /api/notes/{id}/share {targetEmail}` — Feign call ke `user-service` buat validasi + resolve email→userId
- `DELETE /api/notes/{id}/share/{userId}` — revoke, owner only

---

## 7. Urutan membangun sistem

Mengikuti filosofi ShopNest: **tulis yang tidak bergantung pada apa pun lebih dulu.**

```
  1. Eureka (:8761)        — tidak bergantung siapa pun
       ↓
  2. auth-service (:8081)  — penghasil token, tanpa ini tidak ada yang bisa login
       ↓  butuh token untuk diverifikasi
  3. api-gateway (:8080)   — JwtAuthFilter, jwt.secret harus SAMA dengan auth
       ↓  mulai sini semua service dapat X-User-Id gratis
   ┌───┴───┐
   ▼       ▼
4. user   5. note   ← note TERAKHIR: dia memanggil user lewat Feign
  :8082     :8083        (UserClient tidak bisa ditulis sebelum
                          user-service punya endpoint /internal/users/{id})

  (config-server + config-repo bisa disiapkan kapan saja sebelum
   langkah 1, atau di-skip dulu di awal dan dipasang belakangan —
   tidak ada service lain yang *butuh* dia buat start)
```

**Urutan file di dalam satu service** (sama persis untuk `auth-service`, `user-service`, `note-service`):

```
0. pom.xml + application.properties + XxxApplication.java   — kerangka, aplikasi bisa start
1. entity/BaseEntity.java + entity utama                     — bentuk tabel
   config/JpaConfig.java                                     — @EnableJpaAuditing
   ⏸ checkpoint: jalankan, lihat tabel terbentuk
2. repository/XxxRepository.java                              — import: entity
3. dto/request/*.java, dto/response/*.java                    — tidak import apa pun
4. service/XxxService.java (interface)                        — import: dto
5. exception/XxxNotFoundException.java                        — dibuat saat impl butuh
6. service/impl/XxxServiceImpl.java                            — LOGIKA BISNIS
   ⏸ checkpoint: unit test ditulis di sini
7. controller/XxxController.java                               — import: dto + interface (bukan impl)
8. exception/GlobalExceptionHandler.java                       — petakan exception → HTTP status
   ⏸ checkpoint: uji end-to-end via Postman
```

Untuk `note-service`, `client/UserClient.java` + `client/dto/*` ditulis **setelah** langkah 6 (service impl butuh dia buat fitur share), niru posisi `ProductClient` di `order-service`.

---

## 8. Docker Compose

Root-level `docker-compose.yml`, pola sama seperti ShopNest: 1 network, Postgres + Eureka + config-server + 3 service + gateway, health-checked startup order (`depends_on: condition: service_healthy`). Hanya `8080` (gateway) dan `8761` (Eureka dashboard) yang di-expose ke host.

---

## 9. Testing

Mockito service-layer test per service, niru `AuthServiceImplTest` dan `OrderServiceImplTest`:
- `AuthServiceImplTest` — happy path register/login, email sudah terdaftar
- `ProfileServiceImplTest` — happy path, profile not found
- `NoteServiceImplTest` — CRUD happy path, ownership violation (403), **Feign error-path test** saat share ke email yang tidak ditemukan (404 dari `user-service` di-propagate jadi error yang jelas ke client) — ini bagian paling penting untuk ditunjukkan karena itu bukti nyata memahami Feign, bukan cuma nulis annotation.

---

## 10. Simplifikasi yang disengaja (talking point interview)

| Simplifikasi | Kenapa | Solusi "proper" |
|---|---|---|
| Share tidak realtime (harus refresh) | Di luar scope inti (WebSocket/SSE nambah kompleksitas besar) | WebSocket / Server-Sent Events |
| Tidak ada permission bertingkat (cuma READ) | Cukup buat nunjukkin konsep sharing | READ/EDIT/ADMIN permission |
| Kalau `user-service` down saat register, profil gagal dibuat (kalau opsi auto-create profile dipakai) | Cross-service rollback kompleks, sama kayak known limitation ShopNest | Saga pattern / compensating transaction |
| Single Postgres instance, schema-per-service | Simple buat local dev | Physically separate DB per service |
| Tidak ada circuit breaker / tracing / cache | Bukan fundamental untuk scope ini | Resilience4j, Zipkin, Redis |
| Internal endpoint cuma dijaga di gateway | Service tidak reachable langsung di Compose | Signed service-to-service token / mTLS |

---

## 11. Checklist langkah berikutnya

- [x] `git init` di `D:\github-repos\notenest`, buat `.gitignore` (copy dari ShopNest)
- [x] Scaffold `eureka-server` (copy dari shopnest, ganti `spring.application.name`)
- [x] Scaffold `config-server` + `config-repo`
- [x] Bangun `auth-service` mengikuti urutan file di §7
- [x] Bangun `api-gateway` (routes ke auth/user/note, JwtAuthFilter)
- [x] Bangun `user-service`
- [x] Bangun `note-service` (termasuk `UserClient` Feign di akhir)
- [x] `docker-compose.yml` + Dockerfile per service
- [x] Unit test per service
- [x] README.md (mirror gaya ShopNest, jelasin relasi ke ShopNest sebagai "sister project")

---

*Domain, endpoint, dan urutan build di atas adalah hasil diskusi — bukan keputusan final yang kaku. Kalau ada yang mau diubah pas mulai coding (nama field, tambah/kurang endpoint), sesuaikan saja, dokumen ini cuma peta awal.*
