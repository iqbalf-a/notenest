# NoteNest

Aplikasi catatan dengan fitur berbagi (share) — REST API dengan Spring Boot + PostgreSQL,
dan frontend React di folder terpisah.

NoteNest adalah *sister project* dari [ShopNest](https://github.com/iqbalfirman/shopnest).
Konsep yang dipelajari sama — JWT diverifikasi di satu pintu, otorisasi berbasis kepemilikan,
batas domain yang tegas, snapshot data antar domain — tapi domainnya jauh lebih kecil
(3 domain, bukan 4). Tujuannya supaya backend **dan** frontend sama-sama bisa diselesaikan,
bukan cuma backend-nya.

> Versi microservices-nya — Eureka, Config Server, API Gateway, Feign — ada di branch `dev`.
> Alasan keduanya ada: [`docs/DEPLOY-MONOLITH.md`](docs/DEPLOY-MONOLITH.md).

---

## Arsitektur

```
                         ┌──────────────────┐
                         │ Client / Postman │
                         └────────┬─────────┘
                                  │
                    ┌─────────────▼───────────────┐
                    │   NoteNest           :8080  │
                    │                             │
                    │   JwtAuthFilter             │  verifikasi token,
                    │         │                   │  pasang X-User-*
                    │   ┌─────▼──────┐            │
                    │   │  auth      │            │
                    │   │  user   ◄──┼────────┐   │
                    │   │  note   ───┼────────┘   │
                    │   └────────────┘  UserClient│
                    └─────────────┬───────────────┘
                                  ▼
                    ┌─────────────────────────────┐
                    │          PostgreSQL         │
                    └─────────────────────────────┘
```

**Satu jalur masuk.** `JwtAuthFilter` memverifikasi JWT satu kali di depan, lalu meneruskan
identitas sebagai header `X-User-Id` / `X-User-Email` / `X-User-Role` / `X-User-Name`.
Controller di belakangnya tidak pernah melihat token. Header `X-User-*` yang dikirim client
sendiri dibuang lebih dulu, jadi tidak bisa dipalsukan.

**Batas antar domain dijaga di kode.** `noteservice` tidak pernah meng-import apa pun dari
`userservice` — ia hanya tahu interface `UserClient`. Yang mengisinya adalah `LocalUserClient`
di `common`, satu-satunya kelas yang melihat kedua sisi. Selama batas itu berdiri, ketiga
domain bisa dipisah lagi tanpa menyentuh logika bisnis.

---

## Tech stack

| Kategori | Teknologi |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.4.6 |
| Security | Spring Security, JWT (`jjwt` 0.12.5) |
| Database | PostgreSQL 16 |
| ORM | Spring Data JPA / Hibernate |
| API Docs | springdoc-openapi + Scalar |
| Testing | JUnit 5 + Mockito, H2 untuk repository & end-to-end |
| Containerization | Docker (multi-stage) + Docker Compose |
| Build | Maven (wrapper disertakan) |

---

## Paket

Satu aplikasi di port 8080. Batas domainnya dijaga di tingkat paket.

| Paket | Peran |
|---|---|
| `common` | Verifikasi JWT, rantai security, CORS, handler exception, `LocalUserClient` |
| `authservice` | Register, login, penerbit JWT |
| `userservice` | Profil user + pencarian user (untuk fitur share) |
| `noteservice` | CRUD note, tag, dan share. Pemilik interface `UserClient` |

---

## Menjalankan

### Dengan Docker Compose (paling cepat)

```bash
cp .env.example .env    # lalu isi nilainya
docker compose up -d --build
```

Dua container: `api` dan `postgres`. Yang terbuka ke host:
- `http://localhost:8080` — API
- `localhost:5432` — PostgreSQL (supaya bisa dilihat dari DBeaver/psql)

Container `api` dibatasi `mem_limit: 512m`, meniru batas free tier — masalah memori ketahuan
di laptop, bukan setelah dideploy.

Dokumentasi API interaktif (Scalar): **http://localhost:8080/docs.html**, bisa langsung
mengirim request.

Langkah verifikasi end-to-end dengan dua akun:
[`docs/DOCKER-STEPS.md`](docs/DOCKER-STEPS.md#bagian-5--verifikasi-end-to-end).

### Dari IDE

Tidak ada `application.properties` yang perlu disalin — semua nilai datang dari environment
variable. Set empat variabel ini, lalu jalankan `NoteNestApplication`:

```
DB_URL=jdbc:postgresql://localhost:5432/notenest_db
DB_USER=notenest
DB_PASSWORD=<password>
JWT_SECRET=<hasil: openssl rand -base64 48>
```

`JWT_SECRET` harus Base64 yang sah — `JwtAuthFilter` men-decode-nya sebagai Base64, bukan teks
biasa. Databasenya boleh memakai container `postgres` dari Compose.

### Test

```bash
cd backend && ./mvnw test   # 36 test
```

| Kelas | Test | Jenis |
|---|---|---|
| `AuthServiceImplTest` | 4 | unit, Mockito |
| `ProfileServiceImplTest` | 6 | unit, Mockito |
| `NoteServiceImplTest` | 13 | unit, Mockito |
| `NoteRepositoryTest` | 5 | `@DataJpaTest` + H2 |
| `ApiEndToEndTest` | 8 | `@SpringBootTest` + H2 |

Sebagian besar unit test service-layer — tidak butuh database hidup, jalan dalam milidetik.
Yang paling penting ada di `NoteServiceImplTest`: pelanggaran kepemilikan (403), dan jalur
error saat note di-share ke email yang tidak terdaftar (`UserClient` menjawab `Optional`
kosong, diterjemahkan jadi 404 dengan pesan yang berarti).

Dua pengecualian memakai H2. `NoteRepositoryTest` menjalankan HQL tulis tangan milik
`NoteRepository` secara sungguhan — satu-satunya bagian yang tidak bisa dibuktikan benar oleh
test bermock, karena mock tidak pernah mengeksekusi query. `ApiEndToEndTest` men-start seluruh
context lalu memanggil API lewat HTTP: register → buat note → share → `shared-with-me`, plus
penolakan token palsu dan header `X-User-*` palsu.

---

## Endpoint

Semua di `http://localhost:8080`. Semua response memakai amplop yang sama:

```json
{ "success": true, "message": "Note created", "data": { } }
```

### Auth — publik

| Method | Path | Keterangan |
|---|---|---|
| `POST` | `/api/auth/register` | `{displayName, email, password}` → JWT |
| `POST` | `/api/auth/login` | `{email, password}` → JWT |
| `GET` | `/api/auth/validate` | Cek token yang tersimpan masih hidup |

### Profil & pencarian user — butuh token

| Method | Path | Keterangan |
|---|---|---|
| `GET` | `/api/users/me` | Profil sendiri. Dibuat otomatis saat pertama kali diakses. |
| `PUT` | `/api/users/me` | `{displayName, bio, avatarUrl}` |
| `GET` | `/api/users/search?email=` | Cari user untuk di-share (cocok sebagian, diri sendiri dibuang) |

### Catatan & share — butuh token

| Method | Path | Keterangan |
|---|---|---|
| `POST` | `/api/notes` | `{title, content, tags[]}` |
| `GET` | `/api/notes?tag=&q=&page=&size=&sort=` | Note milik sendiri, dengan filter tag + pencarian teks |
| `GET` | `/api/notes/shared-with-me` | Note orang lain yang dibagikan ke saya |
| `GET` | `/api/notes/{id}` | Pemilik **atau** penerima share |
| `PUT` | `/api/notes/{id}` | Pemilik saja |
| `DELETE` | `/api/notes/{id}` | Pemilik saja |
| `GET` | `/api/notes/{id}/shares` | Daftar penerima share. Pemilik saja. |
| `POST` | `/api/notes/{id}/share` | `{targetEmail}` — memicu lookup lewat `UserClient` |
| `DELETE` | `/api/notes/{id}/share/{userId}` | Cabut akses. Pemilik saja. |

Dokumentasi interaktif: `/docs.html` (Scalar). Spec OpenAPI mentah: `/v3/api-docs`.

---

## Skema database

Lima tabel, dibuat otomatis oleh Hibernate saat startup. Snapshot lengkapnya — termasuk
indeks dan constraint — ada di [`docs/schema.sql`](docs/schema.sql).

**`users`** — `id`, `email`, `password_hash`, `display_name`, `role`, `enabled`, `created_at`, `updated_at`

**`profiles`** — `id`, `user_id`, `email`, `display_name`, `bio`, `avatar_url`, `created_at`, `updated_at`

**`notes`** — `id`, `owner_id`, `title`, `content`, `created_at`, `updated_at`
**`note_tags`** — `note_id`, `tag`
**`note_shares`** — `id`, `note_id`, `shared_with_user_id`, `shared_with_email`, `permission`, `created_at`, `updated_at`

`profiles.user_id` dan `notes.owner_id` menunjuk ke `users.id` **tanpa foreign key**, dan itu
disengaja — lihat bagian keputusan desain di bawah.

---

## Keputusan desain

**Profil dibuat saat dibutuhkan, bukan saat register.**
Sisi auth tidak menyentuh sisi user sama sekali. Sebagai gantinya, `displayName` ikut sebagai
klaim di JWT, `JwtAuthFilter` meneruskannya sebagai `X-User-Name`, dan profil dibuat saat
`/api/users/me` pertama kali diakses. Konsekuensinya: register tidak pernah bisa gagal separuh
jalan — tidak ada keadaan "user terdaftar tapi profilnya tidak".

**Email disalin ke `profiles`.**
Supaya pencarian user (fitur share) bisa dijawab sisi user sendiri, tanpa membaca tabel `users`.
Salinan ini hanya dibaca, tidak pernah jadi sumber kebenaran untuk login.

**Email penerima di-snapshot di `note_shares`.**
Pola yang sama dipakai ShopNest untuk menyimpan nama & harga produk di `order_items`. Efeknya:
menampilkan "note ini dibagikan ke siapa" tidak memicu satu pun lookup user.

**"Tidak ketemu" adalah jawaban, bukan error.**
`UserClient` mengembalikan `Optional`; yang memutuskan pesannya adalah pemanggil. Share ke email
yang tidak terdaftar → `UserNotFoundException` → 404 dengan pesan yang bisa dibaca orang.

**Cek kepemilikan sebelum lookup user.**
Bukan sekadar hemat: kalau urutannya terbalik, siapa pun dengan token sah bisa memakai endpoint
share pada note orang lain untuk menguji apakah sebuah email terdaftar. Urutan ini dikunci oleh
test `shareNote_byNonOwner_isForbiddenBeforeAnyUserLookup`.

**Batas domain tanpa foreign key.**
`notes.owner_id` menunjuk ke `users.id` tanpa FK, meski secara teknis sudah bisa dibuat.
Menambahkannya akan mengikat kedua domain di level database dan menghapus satu-satunya hal yang
membuat ketiganya masih bisa dipisah lagi tanpa migrasi data.

---

## Simplifikasi yang disengaja

| Simplifikasi | Kenapa | Solusi "proper" |
|---|---|---|
| Share tidak realtime (harus refresh) | Di luar scope inti | WebSocket / Server-Sent Events |
| Permission cuma `READ` | Cukup untuk menunjukkan konsep sharing; sudah berupa enum, jadi menambah level tidak mengubah bentuk tabel | READ / EDIT / ADMIN |
| `/shared-with-me` gagal seluruhnya kalau satu pemilik tidak punya profil | Belum diperbaiki — tercatat di ROADMAP | Kembalikan field pemilik `null` untuk item itu |
| Tidak ada refresh token | Satu access token (24 jam) cukup untuk scope ini | Refresh token + rotasi |
| `ddl-auto=update`, bukan migrasi | Cukup untuk dev dan demo | Flyway |
| Tidak ada tracing / cache | Bukan fundamental untuk scope ini | Zipkin, Redis |
| Tag dimuat lazy per note saat listing (N+1) | Jumlah note per user kecil | `@EntityGraph` atau batch fetching |

---

## Struktur folder

```
notenest/
├── backend/                         :8080 — satu aplikasi Spring Boot
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/notenest/
│       ├── NoteNestApplication.java
│       ├── common/
│       │   ├── security/            JwtAuthFilter, SecurityConfig
│       │   ├── exception/           GlobalExceptionHandler
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

Tiap paket domain mengikuti lapisan yang sama: `entity` → `repository` → `dto` → `service`
(interface) → `service/impl` (logika bisnis) → `controller`. Controller hanya bergantung pada
interface, tidak pernah pada `impl`. Penanganan exception terpusat di
`common/exception/GlobalExceptionHandler`.

---

## Dokumentasi

| Dokumen | Isi |
|---|---|
| [`docs/README.md`](docs/README.md) | **Mulai di sini** — indeks semua dokumen dengan urutan baca per tujuan |
| [`docs/DOCUMENTATION.md`](docs/DOCUMENTATION.md) | Arsitektur, konsep, alur request, model otorisasi |
| [`docs/DEPLOY-MONOLITH.md`](docs/DEPLOY-MONOLITH.md) | Cara deploy ke free tier, dan kenapa bentuknya seperti ini |
| [`docs/FRONTEND-README.md`](docs/FRONTEND-README.md) | Paket serah-terima untuk membangun frontend |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | Fase yang selesai, yang tersisa, dan celah yang diketahui |
| [`docs/INTERVIEW-QA.md`](docs/INTERVIEW-QA.md) | Tanya-jawab keputusan desain |
| [`docs/HANDOVER.md`](docs/HANDOVER.md) | Blueprint awal proyek ini |
