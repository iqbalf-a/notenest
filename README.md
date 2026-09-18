# NoteNest

Aplikasi catatan dengan fitur berbagi (share), dibangun sebagai sistem **microservices** dengan Spring Boot + Spring Cloud.

> **Kamu sedang di branch `deploy/monolith`.**
> Repo tetap monorepo, tapi `backend/` di branch ini adalah **satu aplikasi Spring Boot**,
> bukan 6 modul — profil deploy untuk free tier yang cuma memberi satu service 512 MB.
> Bentuk microservices yang dijelaskan di bawah ada di branch **`dev`**.
> Apa saja yang berubah dan kenapa: [`docs/DEPLOY-MONOLITH.md`](docs/DEPLOY-MONOLITH.md).

NoteNest adalah *sister project* dari [ShopNest](https://github.com/iqbalfirman/shopnest): arsitekturnya sama persis — service discovery, JWT diverifikasi di gateway, otorisasi berbasis kepemilikan, schema-per-service, panggilan antar-service lewat Feign, endpoint internal — tapi domainnya jauh lebih kecil (3 service domain, bukan 4). Tujuannya supaya backend **dan** frontend sama-sama bisa diselesaikan, bukan cuma backend-nya.

---

## Arsitektur

```
                         ┌──────────────────┐
                         │ Client / Postman │
                         └────────┬─────────┘
                                  │ (satu-satunya entry point publik)
                          ┌───────▼────────┐
                          │  API Gateway   │  :8080
                          │  + JWT filter  │
                          └───────┬────────┘
              ┌───────────┬───────┴───────┐
              ▼           ▼               ▼
        ┌──────────┐┌──────────┐   ┌─────────────┐
        │  Auth    ││  User    │   │  Note       │
        │  :8081   ││  :8082   │   │  :8083      │
        └────┬─────┘└────┬─────┘   └──────┬──────┘
             │           │   ▲            │
             │           │   │  Feign     │  (share note →
             │           │   └────────────┘   resolve email → userId)
             ▼           ▼                    ▼
        ┌─────────────────────────────────────────────┐
        │     PostgreSQL — satu schema per service    │
        └─────────────────────────────────────────────┘

        Semua service daftar ke Eureka Server (:8761).
        Config terpusat tersedia via Config Server (:8888) + config-repo.
```

**Client → Gateway → Service** adalah satu-satunya jalur dari luar. Gateway memverifikasi JWT satu kali, lalu meneruskan identitas sebagai header `X-User-Id` / `X-User-Email` / `X-User-Role` / `X-User-Name`. Service di belakang tidak pernah melihat token — mereka percaya gateway, bukan client. Header `X-User-*` yang dikirim client sendiri dibuang lebih dulu, jadi tidak bisa dipalsukan.

**Service → Service** hanya lewat jaringan internal. `note-service` memanggil `user-service` lewat OpenFeign saat sebuah note dibagikan.

---

## Tech stack

| Kategori | Teknologi |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.4.6 |
| Cloud / Microservices | Spring Cloud 2024.0.1 — Eureka, Gateway, OpenFeign, Config Server |
| Security | Spring Security, JWT (`jjwt` 0.12.5) |
| Database | PostgreSQL 16 (schema-per-service) |
| ORM | Spring Data JPA / Hibernate |
| API Docs | springdoc-openapi |
| Testing | JUnit 5 + Mockito |
| Containerization | Docker (multi-stage) + Docker Compose |
| Build | Maven (wrapper disertakan) |

---

## Modul

| Modul | Port | Peran |
|---|---|---|
| `eureka-server` | 8761 | Service registry. Tidak bergantung pada siapa pun. |
| `config-server` + `config-repo` | 8888 | Config bersama, mode `native` (folder lokal, bukan git). |
| `api-gateway` | 8080 | Satu-satunya pintu publik. Verifikasi JWT, routing, CORS, pemblokiran endpoint internal. |
| `auth-service` | 8081 | Register, login, penerbit JWT. |
| `user-service` | 8082 | Profil user + pencarian user (untuk fitur share). Punya endpoint internal. |
| `note-service` | 8083 | CRUD note, tag, dan share. Satu-satunya pemilik Feign client. |

---

## Menjalankan

### Dengan Docker Compose (paling cepat)

```bash
docker compose up -d --build
```

Yang terbuka ke host hanya:
- `http://localhost:8080` — API (lewat gateway)
- `http://localhost:8761` — dashboard Eureka
- `localhost:5432` — PostgreSQL (supaya bisa menjalankan service dari IDE sambil DB tetap di Docker)

Service domain **tidak** punya port ke host. Itu disengaja: satu-satunya cara menyentuh mereka dari luar adalah lewat gateway.

Dokumentasi API interaktif (Scalar): **http://localhost:8080/docs.html** — ketiga service dalam satu halaman, bisa langsung mengirim request.

Langkah verifikasi end-to-end dengan dua akun: [`docs/DOCKER-STEPS.md`](docs/DOCKER-STEPS.md#bagian-4--verifikasi-end-to-end).

### Manual (dari IDE)

> Bagian ini berlaku di branch `dev`. **Di branch ini** cuma ada satu aplikasi, dan
> konfigurasinya datang dari environment variable — tidak ada `application.properties`
> yang perlu disalin:
>
> ```bash
> cp .env.example .env    # lalu isi nilainya
> docker compose up --build
> ```
>
> Menjalankan dari IDE: set `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`
> sebagai environment variable, lalu jalankan `NoteNestApplication`.

### Test

```bash
cd backend && ./mvnw test   # 36 test
```

Semua test hidup di satu modul: 4 auth + 6 user + 18 note (13 service + 5 repository),
plus 8 `ApiEndToEndTest` yang men-start seluruh context di H2 lalu memanggil API-nya
sungguhan. Dua test khusus Feign dari branch `dev` disesuaikan — "user-service mati"
tidak punya arti lagi kalau tidak ada jaringan di antaranya.

Sebagian besar adalah unit test service-layer dengan Mockito — tidak butuh database maupun service lain yang hidup. Yang paling penting ada di `NoteServiceImplTest`: pelanggaran kepemilikan (403), dan jalur error saat note di-share ke email yang tidak terdaftar (`UserClient` menjawab `Optional` kosong, diterjemahkan jadi 404 dengan pesan yang berarti).

Satu pengecualian: `NoteRepositoryTest` adalah `@DataJpaTest` di atas H2 (mode PostgreSQL). `NoteRepository` memakai HQL yang ditulis tangan — satu-satunya bagian yang tidak bisa dibuktikan benar oleh test bermock — jadi query-nya dijalankan sungguhan untuk memastikan `distinct`, filter tag, pencarian teks, sorting, dan paging benar-benar bekerja.

---

## Endpoint

Semua lewat gateway (`http://localhost:8080`). Semua response memakai amplop yang sama:

```json
{ "success": true, "message": "Note created", "data": { } }
```

### auth-service — publik

| Method | Path | Keterangan |
|---|---|---|
| `POST` | `/api/auth/register` | `{displayName, email, password}` → JWT |
| `POST` | `/api/auth/login` | `{email, password}` → JWT |
| `GET` | `/api/auth/validate` | Cek token yang tersimpan masih hidup |

### user-service — butuh token

| Method | Path | Keterangan |
|---|---|---|
| `GET` | `/api/users/me` | Profil sendiri. Dibuat otomatis saat pertama kali diakses. |
| `PUT` | `/api/users/me` | `{displayName, bio, avatarUrl}` |
| `GET` | `/api/users/search?email=` | Cari user untuk di-share (cocok sebagian, diri sendiri dibuang) |
| `GET` | `/internal/users/{id}` | **Internal** — diblokir gateway, hanya untuk Feign |
| `GET` | `/internal/users/by-email?email=` | **Internal** — resolve email → userId saat share |

### note-service — butuh token

| Method | Path | Keterangan |
|---|---|---|
| `POST` | `/api/notes` | `{title, content, tags[]}` |
| `GET` | `/api/notes?tag=&q=&page=&size=&sort=` | Note milik sendiri, dengan filter tag + pencarian teks |
| `GET` | `/api/notes/shared-with-me` | Note orang lain yang dibagikan ke saya |
| `GET` | `/api/notes/{id}` | Pemilik **atau** penerima share |
| `PUT` | `/api/notes/{id}` | Pemilik saja |
| `DELETE` | `/api/notes/{id}` | Pemilik saja |
| `GET` | `/api/notes/{id}/shares` | Daftar penerima share. Pemilik saja. |
| `POST` | `/api/notes/{id}/share` | `{targetEmail}` — memicu Feign ke user-service |
| `DELETE` | `/api/notes/{id}/share/{userId}` | Cabut akses. Pemilik saja. |

Dokumentasi interaktif: `/docs.html` (Scalar). Spec OpenAPI mentah tiap service: `/docs/specs/auth`, `/docs/specs/users`, `/docs/specs/notes`.

---

## Skema database

Satu instance PostgreSQL, satu schema per service — service tidak pernah membaca tabel milik service lain.

**`auth_schema.users`** — `id`, `email`, `password_hash`, `display_name`, `role`, `enabled`, `created_at`, `updated_at`

**`user_schema.profiles`** — `id`, `user_id`, `email`, `display_name`, `bio`, `avatar_url`, `created_at`, `updated_at`

**`note_schema.notes`** — `id`, `owner_id`, `title`, `content`, `created_at`, `updated_at`
**`note_schema.note_tags`** — `note_id`, `tag`
**`note_schema.note_shares`** — `id`, `note_id`, `shared_with_user_id`, `shared_with_email`, `permission`, `created_at`, `updated_at`

`user_id` dan `owner_id` bukan foreign key: service tidak berbagi tabel. Relasinya dijamin oleh JWT, bukan oleh constraint database.

---

## Keputusan desain

**Profil dibuat saat dibutuhkan, bukan saat register.**
`auth-service` tidak memanggil `user-service` sama sekali. Sebagai gantinya, `displayName` ikut sebagai klaim di JWT, gateway meneruskannya sebagai `X-User-Name`, dan `user-service` membuat profil sendiri saat `/api/users/me` pertama kali diakses. Konsekuensinya: tidak ada kemungkinan "user terdaftar tapi profilnya gagal dibuat" — masalah klasik yang biasanya butuh saga pattern untuk diselesaikan.

**Email disalin ke `user_schema.profiles`.**
Supaya pencarian user (fitur share) bisa dijawab `user-service` sendiri, tanpa memanggil balik `auth-service`. Salinan ini hanya dibaca, tidak pernah jadi sumber kebenaran untuk login.

**Email penerima di-snapshot di `note_shares`.**
Pola yang sama dipakai ShopNest untuk menyimpan nama & harga produk di `order_items`. Efeknya: menampilkan "note ini dibagikan ke siapa" tidak memicu satu pun panggilan Feign.

**404 dari Feign diterjemahkan, error lain dibiarkan lewat.**
Share ke email yang tidak terdaftar → `UserNotFoundException` → 404 dengan pesan yang bisa dibaca orang. `user-service` mati → `FeignException` dibiarkan naik → 502. Keduanya masalah yang berbeda dan pantas dijawab berbeda.

**Endpoint internal dijaga di gateway.**
`/internal/**` sengaja didaftarkan sebagai route di gateway supaya filter sempat menolaknya dengan **403 yang jelas**, bukan 404 yang ambigu. Request tidak pernah benar-benar diteruskan.

---

## Simplifikasi yang disengaja

| Simplifikasi | Kenapa | Solusi "proper" |
|---|---|---|
| Share tidak realtime (harus refresh) | Di luar scope inti | WebSocket / Server-Sent Events |
| Permission cuma `READ` | Cukup untuk menunjukkan konsep sharing; sudah berupa enum, jadi menambah level tidak mengubah bentuk tabel | READ / EDIT / ADMIN |
| `/shared-with-me` gagal seluruhnya kalau `user-service` mati | Memilih jujur (502) daripada menampilkan daftar setengah jadi | Circuit breaker + fallback |
| Satu instance PostgreSQL, schema-per-service | Sederhana untuk local dev | DB terpisah secara fisik per service |
| Tidak ada circuit breaker / tracing / cache | Bukan fundamental untuk scope ini | Resilience4j, Zipkin, Redis |
| Endpoint internal cuma dijaga di gateway | Di Compose, service domain tidak punya port ke host | Token service-to-service / mTLS |
| Tag dimuat lazy per note saat listing (N+1) | Jumlah note per user kecil | `@EntityGraph` atau batch fetching |

---

## Struktur folder

```
notenest/
├── backend/                         :8080 — satu aplikasi Spring Boot
│   ├── pom.xml                      satu modul, tanpa Spring Cloud
│   ├── Dockerfile
│   └── src/main/java/com/notenest/
│       ├── NoteNestApplication.java
│       ├── common/                  lapisan perakitan — hanya ada di branch ini
│       │   ├── security/            JwtAuthFilter (servlet), SecurityConfig
│       │   ├── exception/           GlobalExceptionHandler gabungan
│       │   ├── config/              CorsConfig
│       │   ├── client/              LocalUserClient
│       │   └── dto/                 ApiResponse
│       ├── authservice/             entity/User, security/*, service/AuthService
│       ├── userservice/             entity/Profile, controller/UserController
│       └── noteservice/             entity/{Note,NoteShare}, client/UserClient
├── frontend/             aplikasi web — lihat frontend/README.md
├── docs/                 seluruh dokumentasi — mulai dari docs/README.md
├── docker-compose.yml    1 aplikasi + 1 database
└── render.yaml           blueprint deploy
```

Di branch `dev`, `backend/` berisi 6 modul Maven terpisah (`eureka-server`,
`config-server`, `api-gateway`, `auth-service`, `user-service`, `note-service`).

Tiap service domain mengikuti lapisan yang sama: `entity` → `repository` → `dto` → `service` (interface) → `service/impl` (logika bisnis) → `controller` → `exception/GlobalExceptionHandler`. Controller hanya bergantung pada interface, tidak pernah pada `impl`.

---

## Dokumentasi

| Dokumen | Isi |
|---|---|
| [`docs/README.md`](docs/README.md) | **Mulai di sini** — indeks semua dokumen dengan urutan baca per tujuan |
| [`docs/DOCUMENTATION.md`](docs/DOCUMENTATION.md) | Arsitektur, konsep, alur request, model otorisasi |
| [`docs/FRONTEND-README.md`](docs/FRONTEND-README.md) | Paket serah-terima untuk membangun frontend |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | Fase yang selesai, yang tersisa, dan celah yang diketahui |
| [`docs/INTERVIEW-QA.md`](docs/INTERVIEW-QA.md) | Tanya-jawab keputusan desain |
| [`docs/HANDOVER.md`](docs/HANDOVER.md) | Blueprint awal proyek ini |
