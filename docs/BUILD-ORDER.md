# NoteNest — Alur Pembuatan Code (branch `deploy/monolith`)

Dokumen ini menjawab **urutan** menulis kode: paket mana dulu, file mana dulu, dan kenapa.
Struktur akhirnya ada di [`DOCUMENTATION.md`](DOCUMENTATION.md#9-struktur-folder).

Prinsipnya satu kalimat: **tulis yang tidak bergantung pada apa pun lebih dulu.** Kalau file A
meng-import file B, B harus sudah ada.

> Branch ini punya sejarah yang tidak biasa: kodenya **tidak** ditulis dalam urutan di bawah.
> Ia lahir dari merakit ulang enam service yang sudah jadi (lihat [`DEPLOY-MONOLITH.md`](DEPLOY-MONOLITH.md)).
> Urutan di sini adalah urutan yang masuk akal kalau seseorang membangunnya dari nol —
> dan urutan yang harus diikuti saat menambah fitur.

---

## 1. Urutan membangun sistem

```
  1. common/security        — JwtAuthFilter + SecurityConfig
       ↓                      tanpa ini, tidak ada X-User-* untuk dibaca siapa pun
  2. authservice (:8080)    — penghasil token; tanpa ini tidak ada yang bisa login
       ↓
   ┌───┴───┐
   ▼       ▼
3. user   4. note   ← note TERAKHIR: dia memanggil user lewat UserClient
                      (interface-nya tidak bisa ditulis sebelum jelas
                       ProfileService menyediakan apa)
  5. common/exception       — GlobalExceptionHandler, terakhir, karena baru
                              sekarang semua exception diketahui
```

**Kenapa security sebelum controller mana pun?** Semua controller selain `/api/auth/**`
membaca identitas dari header `X-User-Id`. Kalau filter belum ada, header itu harus diketik
manual di Postman — bisa, tapi artinya kamu menguji sesuatu yang tidak akan pernah terjadi
di produksi.

**Kenapa `authservice` meletakkan `name` di token?** Keputusan ini harus diambil **sebelum**
`userservice` ditulis. Profil dibuat dari header `X-User-Name`; kalau klaimnya belum ada,
`userservice` terpaksa membaca tabel `users` langsung — dan batas antar domain bocor sejak hari
pertama.

### Perbandingan dengan `dev`

Di `dev` urutannya: Eureka → auth → gateway → user → note, dan yang memaksa gateway ada di
posisi ketiga adalah kebutuhan akan `X-User-*`. Di sini posisi itu diisi `common/security`.
Peran yang sama, hanya tanpa proses terpisah dan tanpa Eureka di depannya.

---

## 2. Urutan file di dalam satu domain

Sama persis untuk `authservice`, `userservice`, `noteservice`:

```
1. entity/BaseEntity.java + entity utama                       bentuk tabel
   ⏸ checkpoint: jalankan, lihat tabel terbentuk
2. repository/XxxRepository.java                               import: entity
3. dto/request/*.java, dto/response/*.java                     tidak import apa pun
4. service/XxxService.java (interface)                         import: dto
5. exception/XxxNotFoundException.java                         dibuat saat impl butuh
6. service/impl/XxxServiceImpl.java                            LOGIKA BISNIS
   ⏸ checkpoint: unit test ditulis di sini
7. controller/XxxController.java                               import: dto + interface (bukan impl)
   ⏸ checkpoint: uji end-to-end
```

Dua langkah yang ada di `dev` dan hilang di sini: `XxxApplication.java` (satu untuk seluruh
aplikasi) dan `config/JpaConfig.java` (`@EnableJpaAuditing` kini menempel di
`NoteNestApplication`). `exception/GlobalExceptionHandler.java` juga tidak lagi per domain —
ia pindah ke `common/` dan ditulis sekali.

---

## 3. Penjelasan tiap langkah

### Langkah 1 — Entity dulu
Entity menentukan bentuk data, dan semua lapisan di atasnya menyesuaikan diri. Di `noteservice`
ini tempat keputusan terbesar diambil:
- **Tag sebagai `@ElementCollection`**, bukan entity `NoteTag`. Tag tidak punya identitas,
  tidak diubah sendiri, tidak dibagi antar note — jadi tidak butuh id.
- **`NoteShare.sharedWithEmail` sebagai snapshot.** Diputuskan di sini supaya `GET /{id}/shares`
  kelak tidak butuh lookup user sama sekali.
- **`@UniqueConstraint(note_id, shared_with_user_id)`.** Aturan "tidak ada share ganda" dijaga
  database, bukan hanya service.

### Langkah 2 — Repository
Derived query dulu (`findByNoteIdAndSharedWithUserId`). Kalau nama method mulai tidak terbaca,
tulis `@Query`. `searchOwnedNotes` ditulis tangan karena satu query harus melayani tiga
kombinasi filter; akibatnya ia butuh test sendiri (lihat [`TESTING-NOTES.md`](TESTING-NOTES.md)).

### Langkah 3 — DTO
Tidak meng-import entity, jadi bisa ditulis kapan saja setelah bentuk data jelas. Aturan
validasi (`@Size(max = 200)` untuk judul, maks 10 tag) diputuskan di sini — dan frontend akan
menirunya persis.

### Langkah 4 — Interface service
Kontraknya dulu. Perhatikan parameter identitas selalu eksplisit:
`updateNote(noteId, ownerId, request)`. Service tidak membaca header sendiri; controller yang
menyerahkannya.

### Langkah 5 — Exception
Jangan dibuat di muka. `UserNotFoundException` baru ada ketika `shareNote` perlu menerjemahkan
`Optional` kosong dari `UserClient`.

### Langkah 6 — Implementasi
Urutan di dalam method juga penting. Di `shareNote`:
1. cari note (404)
2. **cek pemilik (403) — sebelum lookup user**
3. panggil `userClient.findByEmail`
4. tolak share ke diri sendiri
5. simpan atau kembalikan share lama

Cek pemilik sebelum lookup bukan sekadar hemat panggilan: tanpa itu, siapa pun bisa memakai
endpoint share untuk menguji apakah sebuah email terdaftar. Ada test yang mengunci urutan ini
(`shareNote_byNonOwner_isForbiddenBeforeAnyUserLookup`).

### Langkah 7 — Controller
Tipis. Ambil header, ambil body, panggil interface. Satu jebakan:
`@GetMapping("/shared-with-me")` harus dideklarasikan **sebelum** `@GetMapping("/{id}")`, atau
`shared-with-me` dicoba di-parse jadi UUID.

---

## 4. Arah dependensi

```
controller ──► service (interface) ◄── service/impl ──► repository ──► entity
     │                                       │
     └──────────────► dto ◄──────────────────┘
                                             └──► client/UserClient (interface)
                                                        ▲
                                          common/client/LocalUserClient
                                                        │
                                                        └──► userservice/ProfileService
```

Perhatikan panahnya. `noteservice` menunjuk ke **interface** `UserClient`, bukan ke
`userservice`. Yang menyambungkan keduanya adalah `LocalUserClient` di `common/` — satu-satunya
kelas yang meng-import dari dua domain sekaligus.

Itulah kenapa `NoteServiceImpl` masih bisa diuji dengan `UserClient` palsu tanpa menyentuh
`userservice` sama sekali, persis seperti di `dev` ketika yang palsu itu menggantikan HTTP.

Aturan yang tidak boleh dilanggar: **tidak ada `import com.notenest.userservice.*` di dalam
`com.notenest.noteservice`**, dan sebaliknya. Kalau suatu saat butuh, tambahkan method di
`UserClient` — jangan pintas lewat import langsung. Batas ini yang membuat pemisahan bisa
dikembalikan kalau suatu hari dibutuhkan.

---

## 5. Kasus khusus

### `noteservice` — batas ke user
`client/UserClient.java` + `client/UserResponse.java` ditulis **saat langkah 6 dimulai**, tepat
ketika `shareNote` membutuhkannya. Dua catatan:
- `UserResponse` adalah **salinan milik `noteservice`**, bukan class yang dipakai bersama.
  Bentuknya kebetulan sama dengan `UserSummaryResponse`, dan itu tetap tidak dijadikan alasan
  untuk menyatukannya.
- Kembalikan `Optional`, bukan lempar exception. "Tidak ketemu" adalah jawaban yang sah dari
  pencarian; pemanggil yang tahu pesan apa yang berarti bagi pemakai.

Di `dev`, dua file ini ditemani `client/dto/ClientApiResponse` — amplop untuk mem-parse JSON
balasan Feign. Tanpa HTTP, amplop itu tidak punya alasan untuk ada.

### `authservice` — tambahan security
Urutannya: `security/JwtService` → `UserDetailsServiceImpl`. Baru kemudian `AuthServiceImpl`,
karena ia butuh `AuthenticationManager` (dari `common/security/SecurityConfig`) dan `JwtService`.

Di `dev`, `authservice` juga punya `JwtAuthFilter` dan `SecurityConfig` sendiri. Keduanya tidak
ikut ke sini — perannya diambil alih `common/security`.

### `common` — yang lahir dari perakitan
Tidak ada padanannya di `dev`. Urutannya: `dto/ApiResponse` → `config/CorsConfig` →
`security/JwtAuthFilter` → `security/SecurityConfig` → `client/LocalUserClient` →
`exception/GlobalExceptionHandler`.

`GlobalExceptionHandler` terakhir karena ia harus tahu semua exception dari ketiga domain.
Urutan handler di dalamnya dari spesifik ke umum; yang perlu diingat:
- `MissingRequestHeaderException` → `401`, bukan tertelan `Exception.class` jadi `500`
- `AccessDeniedException` → `403`, dengan alasan yang sama

---

## 6. Menambah fitur

Contoh: permission `EDIT` untuk share.

1. `SharePermission` tambah `EDIT` (entity) — kolom sudah string, tapi Hibernate membuat
   `CHECK (permission IN ('READ'))` dan `ddl-auto=update` **tidak** memperbaruinya; constraint
   itu harus di-drop/diubah manual
2. `ShareNoteRequest` tambah `permission` (dto)
3. `NoteServiceImpl.updateNote` — ganti `requireOwner` dengan "pemilik atau share EDIT" (impl)
4. test baru untuk penerima `READ` yang mencoba update
5. controller tidak berubah

Urutannya tetap sama dengan membangun dari nol: data → kontrak → logika → HTTP.

**Kalau fitur itu juga harus ada di `dev`,** tulis di `dev` lebih dulu lalu porting ke sini.
Arah itu lebih mudah: `dev` → monolith hanya perlu menyalin file ke `backend/src/main/java/`,
sedangkan arah sebaliknya harus memecah file ke modul yang benar. Lihat
[`DEPLOY-MONOLITH.md`](DEPLOY-MONOLITH.md#6-menarik-perubahan-dari-dev).

---

## Dokumen terkait
- [`DEPLOY-MONOLITH.md`](DEPLOY-MONOLITH.md) — kenapa branch ini ada, dan cara sinkron dengan `dev`
- [`HANDOVER.md`](HANDOVER.md) — blueprint awal proyek
- [`DOCUMENTATION.md`](DOCUMENTATION.md) — hasil akhirnya
- [`TESTING-NOTES.md`](TESTING-NOTES.md) — checkpoint langkah 6
