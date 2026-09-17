# NoteNest — Alur Pembuatan Code

Dokumen ini menjawab **urutan** menulis kode: service mana dulu, file mana dulu, dan kenapa. Struktur akhirnya ada di [`DOCUMENTATION.md`](DOCUMENTATION.md#9-struktur-folder).

Prinsipnya satu kalimat: **tulis yang tidak bergantung pada apa pun lebih dulu.** Kalau file A meng-import file B, B harus sudah ada.

---

## 1. Urutan membangun sistem

```
  1. Eureka (:8761)        — tidak bergantung siapa pun
       ↓
  2. auth-service (:8081)  — penghasil token; tanpa ini tidak ada yang bisa login
       ↓  butuh token untuk diverifikasi
  3. api-gateway (:8080)   — JwtAuthFilter; jwt.secret harus SAMA dengan auth
       ↓  mulai sini semua service dapat X-User-* gratis
   ┌───┴───┐
   ▼       ▼
4. user   5. note   ← note TERAKHIR: dia memanggil user lewat Feign
  :8082     :8083     (UserClient tidak bisa ditulis sebelum user-service
                        punya /internal/users/{id} dan /internal/users/by-email)

  config-server + config-repo bisa disiapkan kapan saja — tidak ada service
  yang butuh dia untuk start.
```

**Kenapa gateway sebelum user dan note?** Kedua service itu membaca identitas dari header `X-User-Id`. Tanpa gateway, header itu harus diketik manual di Postman — bisa, tapi artinya kamu menguji sesuatu yang tidak akan pernah terjadi di produksi.

**Kenapa auth-service meletakkan `name` di token?** Keputusan ini harus diambil **sebelum** user-service ditulis. user-service membuat profil dari header `X-User-Name`; kalau klaimnya belum ada, user-service terpaksa memanggil auth-service — dan urutan di atas jadi terbalik.

---

## 2. Urutan file di dalam satu service

Sama persis untuk `auth-service`, `user-service`, `note-service`:

```
0. pom.xml + application.properties + XxxApplication.java    kerangka, aplikasi bisa start
1. entity/BaseEntity.java + entity utama                       bentuk tabel
   config/JpaConfig.java                                       @EnableJpaAuditing
   ⏸ checkpoint: jalankan, lihat tabel terbentuk di schema-nya
2. repository/XxxRepository.java                               import: entity
3. dto/request/*.java, dto/response/*.java                     tidak import apa pun
4. service/XxxService.java (interface)                         import: dto
5. exception/XxxNotFoundException.java                         dibuat saat impl butuh
6. service/impl/XxxServiceImpl.java                            LOGIKA BISNIS
   ⏸ checkpoint: unit test ditulis di sini
7. controller/XxxController.java                               import: dto + interface (bukan impl)
8. exception/GlobalExceptionHandler.java                       petakan exception → HTTP status
   ⏸ checkpoint: uji end-to-end lewat gateway
```

---

## 3. Penjelasan tiap langkah

### Langkah 1 — Entity dulu
Entity menentukan bentuk data, dan semua lapisan di atasnya menyesuaikan diri. Di note-service ini tempat keputusan terbesar diambil:
- **Tag sebagai `@ElementCollection`**, bukan entity `NoteTag`. Tag tidak punya identitas, tidak diubah sendiri, tidak dibagi antar note — jadi tidak butuh id. (Blueprint awal menyebut `NoteTag` sebagai entity; ini diubah saat menulis kode.)
- **`NoteShare.sharedWithEmail` sebagai snapshot.** Diputuskan di sini supaya `GET /{id}/shares` kelak tidak butuh Feign.
- **`@UniqueConstraint(note_id, shared_with_user_id)`.** Aturan "tidak ada share ganda" dijaga database, bukan hanya service.

Checkpoint-nya penting: kalau tabel tidak muncul di `note_schema`, masalahnya konfigurasi — lebih murah ketahuan sekarang daripada setelah controller jadi.

### Langkah 2 — Repository
Derived query dulu (`findByNoteIdAndSharedWithUserId`). Kalau nama method mulai tidak terbaca, tulis `@Query`. `searchOwnedNotes` ditulis tangan karena satu query harus melayani tiga kombinasi filter; akibatnya ia butuh test sendiri (lihat [`TESTING-NOTES.md`](TESTING-NOTES.md)).

### Langkah 3 — DTO
Tidak meng-import entity, jadi bisa ditulis kapan saja setelah bentuk data jelas. Aturan validasi (`@Size(max = 200)` untuk judul, maks 10 tag) diputuskan di sini — dan frontend akan menirunya persis.

### Langkah 4 — Interface service
Kontraknya dulu. Perhatikan parameter identitas selalu eksplisit: `updateNote(noteId, ownerId, request)`. Service tidak membaca header sendiri; controller yang menyerahkannya.

### Langkah 5 — Exception
Jangan dibuat di muka. `UserNotFoundException` baru ada ketika `shareNote` perlu menerjemahkan Feign 404.

### Langkah 6 — Implementasi
Urutan di dalam method juga penting. Di `shareNote`:
1. cari note (404)
2. **cek pemilik (403) — sebelum Feign**
3. panggil Feign
4. tolak share ke diri sendiri
5. simpan atau kembalikan share lama

Cek pemilik sebelum Feign bukan sekadar hemat panggilan: tanpa itu, siapa pun bisa memakai endpoint share untuk menguji apakah sebuah email terdaftar. Ada test yang mengunci urutan ini (`shareNote_byNonOwner_isForbiddenBeforeAnyFeignCall`).

### Langkah 7 — Controller
Tipis. Ambil header, ambil body, panggil interface. Satu jebakan: `@GetMapping("/shared-with-me")` harus dideklarasikan **sebelum** `@GetMapping("/{id}")`, atau `shared-with-me` dicoba di-parse jadi UUID.

### Langkah 8 — GlobalExceptionHandler
Terakhir, karena baru sekarang semua exception diketahui. Urutan handler dari spesifik ke umum; yang perlu diingat:
- `FeignException` → `502` (setelah `UserNotFoundException` sudah menangkap kasus 404)
- `MissingRequestHeaderException` → `401`, bukan tertelan `Exception.class` jadi `500`

---

## 4. Arah dependensi

```
controller ──► service (interface) ◄── service/impl ──► repository ──► entity
     │                                       │
     └──────────────► dto ◄──────────────────┘
                                             └──► client (Feign) ──► client/dto
```

Controller tidak pernah meng-import `impl`. Karena itu `NoteServiceImpl` bisa diuji dengan `UserClient` palsu tanpa menyentuh HTTP sama sekali.

---

## 5. Kasus khusus

### note-service — tambahan Feign
`client/UserClient.java` + `client/dto/*` ditulis **setelah langkah 6 dimulai**, tepat saat `shareNote` membutuhkannya. Dua catatan:
- `client/dto/UserResponse` adalah **salinan milik note-service**, bukan class yang di-share dari user-service. Antar service tidak berbagi kode.
- Tambahkan `@EnableFeignClients` di `NoteServiceApplication` dan timeout Feign di properties sebelum mencoba end-to-end.

### user-service — endpoint internal
`InternalUserController` dipisah dari `UserController` supaya mudah dilihat mana yang publik. Ia tidak membaca header `X-User-*` — pemanggilnya service, bukan orang. Penjagaannya ada di gateway (`INTERNAL_PATTERNS`), jadi rute gateway dan filter-nya harus diperbarui di langkah yang sama.

### auth-service — tambahan security
Setelah langkah 1–3: `security/JwtService` → `UserDetailsServiceImpl` → `JwtAuthFilter` → `SecurityConfig`. Baru kemudian `AuthServiceImpl`, karena ia butuh `AuthenticationManager` dan `JwtService`.

### api-gateway — tanpa entity
Urutannya: properties (rute) → `JwtAuthFilter` → `CorsConfig` → `static/docs.html`. Tidak ada database, tidak ada service layer.

---

## 6. Menambah fitur ke service yang sudah jadi

Contoh: permission `EDIT` untuk share.

1. `SharePermission` tambah `EDIT` (entity) — kolom sudah string, tapi Hibernate membuat `CHECK (permission IN ('READ'))` dan `ddl-auto=update` **tidak** memperbaruinya; constraint itu harus di-drop/diubah manual
2. `ShareNoteRequest` tambah `permission` (dto)
3. `NoteServiceImpl.updateNote` — ganti `requireOwner` dengan "pemilik atau share EDIT" (impl)
4. test baru untuk penerima `READ` yang mencoba update
5. controller tidak berubah

Urutannya tetap sama dengan membangun dari nol: data → kontrak → logika → HTTP.

---

## Dokumen terkait
- [`HANDOVER.md`](HANDOVER.md) — blueprint awal proyek
- [`DOCUMENTATION.md`](DOCUMENTATION.md) — hasil akhirnya
- [`TESTING-NOTES.md`](TESTING-NOTES.md) — checkpoint langkah 6
