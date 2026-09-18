# NoteNest

Aplikasi catatan dengan fitur berbagi (share), dibangun sebagai sistem **microservices** dengan Spring Boot + Spring Cloud.

> **Kamu sedang di branch `deploy/monolith`.**
> Repo tetap monorepo, tapi `backend/` di branch ini adalah **satu aplikasi Spring Boot**,
> bukan 6 modul — profil deploy untuk free tier yang cuma memberi satu service 512 MB.
> Bentuk microservices yang dijelaskan di bawah ada di branch **`dev`**.
> Apa saja yang berubah dan kenapa: [`docs/DEPLOY-MONOLITH.md`](docs/DEPLOY-MONOLITH.md).

NoteNest adalah *sister project* dari [ShopNest](https://github.com/iqbalfirman/shopnest): arsitekturnya sama persis — service discovery, JWT diverifikasi di satu pintu, otorisasi berbasis kepemilikan, schema-per-service, panggilan antar-service lewat Feign, endpoint internal — tapi domainnya jauh lebih kecil (3 domain, bukan 4). Tujuannya supaya backend **dan** frontend sama-sama bisa diselesaikan, bukan cuma backend-nya.

Branch ini merakit ketiga domain itu jadi satu aplikasi. Yang hilang adalah infrastruktur antar-proses; batas domainnya tetap berdiri.

---

## Arsitektur

```
                         ┌──────────────────┐
                         │ Client / Postman │
                         └────────┬─────────┘
                                  │
                    ┌─────────────▼──────────────┐
                    │   NoteNest          :8080  │
                    │                            │
                    │   JwtAuthFilter            │  verifikasi token,
                    │         │                  │  pasang X-User-*
                    │   ┌─────▼──────┐           │
                    │   │  auth      │           │
                    │   │  user   ◄──┼───────┐   │
                    │   │  note   ───┼───────┘   │
                    │   └────────────┘  UserClient│
                    └─────────────┬──────────────┘
                                  ▼
                    ┌────────────────────────────┐
                    │   PostgreSQL — satu schema │
                    └────────────────────────────┘
```

**Satu jalur masuk.** `JwtAuthFilter` memverifikasi JWT satu kali di depan, lalu meneruskan identitas sebagai header `X-User-Id` / `X-User-Email` / `X-User-Role` / `X-User-Name`. Controller di belakangnya tidak pernah melihat token. Header `X-User-*` yang dikirim client sendiri dibuang lebih dulu, jadi tidak bisa dipalsukan.

**Batas antar domain dijaga di kode, bukan jaringan.** `noteservice` tidak pernah meng-import apa pun dari `userservice` — ia hanya tahu interface `UserClient`. Yang mengisinya adalah `LocalUserClient` di `common`, satu-satunya kelas yang melihat kedua sisi. Di `dev`, interface yang sama diisi proxy Feign lewat Eureka.

---

## Tech stack

| Kategori | Teknologi |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.4.6 |
| Cloud / Microservices | **tidak ada di branch ini** — di `dev`: Spring Cloud 2024.0.1 (Eureka, Gateway, OpenFeign, Config Server) |
| Security | Spring Security, JWT (`jjwt` 0.12.5) |
| Database | PostgreSQL 16 (satu schema; di `dev`: schema-per-service) |
| ORM | Spring Data JPA / Hibernate |
| API Docs | springdoc-openapi |
| Testing | JUnit 5 + Mockito, H2 untuk repository & end-to-end |
| Containerization | Docker (multi-stage) + Docker Compose |
| Build | Maven (wrapper disertakan) |

---

## Paket

Satu aplikasi di port 8080. Batas domainnya dijaga di tingkat paket, bukan proses.

| Paket | Peran |
|---|---|
| `common` | Verifikasi JWT, rantai security, CORS, handler exception, `LocalUserClient` |
| `authservice` | Register, login, penerbit JWT. |
| `userservice` | Profil user + pencarian user (untuk fitur share). |
| `noteservice` | CRUD note, tag, dan share. Pemilik interface `UserClient`. |

Di branch `dev`, keempatnya adalah enam modul Maven terpisah: `eureka-server` (8761),
`config-server` (8888), `api-gateway` (8080), `auth-service` (8081), `user-service` (8082),
`note-service` (8083).

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

Container `api` dibatasi `mem_limit: 512m`, meniru free tier Render — masalah memori ketahuan
di laptop, bukan setelah dideploy.

Dokumentasi API interaktif (Scalar): **http://localhost:8080/docs.html** — satu spec,
bisa langsung mengirim request.

Langkah verifikasi end-to-end dengan dua akun: [`docs/DOCKER-STEPS.md`](docs/DOCKER-STEPS.md#bagian-5--verifikasi-end-to-end).

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

Semua di `http://localhost:8080`. Semua response memakai amplop yang sama:

```json
{ "success": true, "message": "Note created", "data": { } }
```

### auth-service — publik

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

Dua endpoint `/internal/users/**` yang ada di `dev` tidak ada di sini — itu pintu masuk untuk
Feign, dan dari luar memang selalu dijawab `403`.

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

Dokumentasi interaktif: `/docs.html` (Scalar). Spec OpenAPI mentah: `/v3/api-docs` — satu spec, bukan tiga.

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
Sisi auth tidak menyentuh sisi user sama sekali. Sebagai gantinya, `displayName` ikut sebagai klaim di JWT, `JwtAuthFilter` meneruskannya sebagai `X-User-Name`, dan profil dibuat saat `/api/users/me` pertama kali diakses. Di `dev` ini menghindari transaksi lintas service yang butuh saga pattern; di sini polanya dipertahankan supaya kedua branch berperilaku identik.

**Email disalin ke `profiles`.**
Supaya pencarian user (fitur share) bisa dijawab sisi user sendiri, tanpa membaca tabel `users`. Salinan ini hanya dibaca, tidak pernah jadi sumber kebenaran untuk login.

**Email penerima di-snapshot di `note_shares`.**
Pola yang sama dipakai ShopNest untuk menyimpan nama & harga produk di `order_items`. Efeknya: menampilkan "note ini dibagikan ke siapa" tidak memicu satu pun lookup user.

**"Tidak ketemu" adalah jawaban, bukan error.**
`UserClient` mengembalikan `Optional`; yang memutuskan pesannya adalah pemanggil. Share ke email yang tidak terdaftar → `UserNotFoundException` → 404 dengan pesan yang bisa dibaca orang. Di `dev` ada satu kasus lagi — `user-service` mati → 502 — yang di sini mustahil karena tidak ada jaringan di antaranya.

**Batas domain tanpa foreign key.**
`notes.owner_id` menunjuk ke `users.id` tanpa FK, meski sekarang keduanya di schema yang sama dan secara teknis sudah bisa. Menambahkannya akan mengikat kedua domain di level database dan menghapus satu-satunya hal yang membuat pemisahannya masih bisa dikembalikan.

---

## Simplifikasi yang disengaja

| Simplifikasi | Kenapa | Solusi "proper" |
|---|---|---|
| Share tidak realtime (harus refresh) | Di luar scope inti | WebSocket / Server-Sent Events |
| Permission cuma `READ` | Cukup untuk menunjukkan konsep sharing; sudah berupa enum, jadi menambah level tidak mengubah bentuk tabel | READ / EDIT / ADMIN |
| `/shared-with-me` gagal seluruhnya kalau satu pemilik tidak punya profil | Belum diperbaiki — tercatat di ROADMAP | Kembalikan field pemilik `null` untuk item itu |
| Backend dirakit jadi satu aplikasi | Free tier hanya memberi satu service 512 MB tanpa private networking | Bayar, atau VPS yang menjalankan `dev` apa adanya |
| Satu schema, bukan schema-per-service | Satu aplikasi hanya punya satu konfigurasi JPA | `@Table(schema = ...)` per entity |
| Tidak ada tracing / cache | Bukan fundamental untuk scope ini | Zipkin, Redis |
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
