# Frontend Handover — NoteNest

Paket serah-terima untuk siapa pun yang akan membangun frontend NoteNest di folder [`../frontend/`](../frontend/). Dua dokumen HTML-nya berdiri sendiri — cukup dibuka lewat browser, tanpa server, tanpa build.

Semua yang tertulis di dalamnya dibaca langsung dari kode di `backend/`: entity JPA, controller, DTO, `GlobalExceptionHandler`, dan `JwtAuthFilter`. Tidak ada yang dikarang dari rencana di atas kertas.

> **Branch ini memakai backend monolith.** Kontrak API-nya **sama persis** dengan branch `dev`
> — base URL, path, body, status code, semuanya. Yang berbeda cuma berapa proses yang melayaninya
> di balik layar, dan itu tidak terlihat dari sisi frontend. Dua selisih kecil ditandai di bawah.

---

## Isi paket

| File | Isi | Untuk siapa |
|---|---|---|
| [`FRONTEND-SERVICE-ATLAS.html`](FRONTEND-SERVICE-ATLAS.html) | Peta service, ERD 5 tabel, perjalanan satu share, 15 endpoint publik | Siapa pun yang menyentuh data |
| [`FRONTEND-BRIEF.html`](FRONTEND-BRIEF.html) | PRD (scope, rute, kontrak API, dummy data, celah backend) + design system | Developer frontend |
| [`DESIGN-HANDOVER.md`](DESIGN-HANDOVER.md) | Brief untuk desainer / Claude Design: layar, keadaan, batasan keras | Desainer |

`FRONTEND-SERVICE-ATLAS.html` sebenarnya **mendokumentasikan backend**. Ia diberi prefix yang sama karena disertakan sebagai acuan kontrak untuk frontend.

---

## Urutan baca

1. **Service Atlas** (±6 menit) — bentuk data dan siapa pemilik apa. Perhatikan perjalanan share: itu satu-satunya fitur yang menyeberangi batas domain. (Atlas menggambarkannya sebagai panggilan antar-service, sesuai branch `dev`; di sini panggilan itu terjadi di dalam satu proses.)
2. **Frontend Brief** (±20 menit) — §1.6 (kontrak API) dan §1.8 (celah backend) yang paling sering dibuka ulang.
3. **Design Handover** — kalau kamu yang mendesain layarnya.

---

## Tiga hal yang harus dipegang

**1. Satu note, dua cara memilikinya.**
Setiap `NoteResponse` membawa `owned: boolean`. `true` = milik sendiri, boleh diubah, dihapus, dibagikan. `false` = dibagikan orang lain, **hanya baca**. Jangan menebak dari `ownerId` — pakai `owned`. Semua tombol aksi bergantung pada flag ini.

**2. Profil lahir saat `/api/users/me` pertama kali dipanggil.**
Register tidak membuat profil. Sampai seseorang memanggil `GET /api/users/me`, ia **tidak bisa ditemukan** oleh fitur share. Panggil endpoint itu segera setelah login dan register sukses — bukan hanya saat halaman profil dibuka.

**3. Share memakai email, cabut share memakai userId.**
`POST /api/notes/{id}/share` menerima `{ targetEmail }`. `DELETE /api/notes/{id}/share/{userId}` menerima UUID. Ambil `sharedWithUserId` dari `GET /api/notes/{id}/shares` untuk tombol cabut.

---

## Sebelum menyambung ke API asli

Fase pertama berjalan dengan dummy data (MSW) dan tidak menyentuh backend. Daftar ini baru menggigit saat penyambungan — tapi baca sekarang, karena sebagian mengubah cara kamu menulis komponen.

- [ ] **Kirim `sort=updatedAt,desc` secara eksplisit.** Default `GET /api/notes` adalah `updatedAt` **naik** — note paling lama di atas.
- [ ] **Tidak ada endpoint daftar tag.** Filter tag harus dibangun dari note yang sudah dimuat, atau berupa input bebas.
- [ ] **`shared-with-me` tidak paginated dan tidak bisa dicari.** Dan kalau salah satu pemilik tidak punya profil, **seluruh daftar** gagal `404`.
- [ ] **Tangani `403` terpisah dari `401`.** `401` = hapus token, ke `/login`. `403` = tidak berhak, **jangan logout**.
- [ ] **`502` saat share tidak ada di branch ini.** Di `dev` status itu berarti user-service tidak terjangkau. Tetap tangani kalau kodemu harus jalan di kedua branch — biayanya satu baris.
- [ ] **Origin dev harus ada di `CORS_ALLOWED_ORIGINS`.** Default hanya `http://localhost:5173`.
- [ ] **Tidak ada refresh token.** Token berlaku 24 jam; setelah itu `401`.

Uraian lengkap tiap celah ada di **§1.8** `FRONTEND-BRIEF.html`.

---

## Ringkasan keputusan teknis

| Hal | Keputusan | Alasan singkat |
|---|---|---|
| Lokasi | **Folder `frontend/` di repo ini** | Satu repo untuk portfolio yang utuh; kontrak API dan UI berubah di commit yang sama |
| Framework | **React + TypeScript + Vite** | Auth pakai header `Bearer`, bukan cookie — SSR tidak memberi imbalan |
| Data fase 1 | **MSW** | Komponen tidak boleh tahu datanya palsu |
| Ambil data | TanStack Query | Invalidasi setelah create/update/share jadi seragam |
| Validasi | Zod, meniru `@Valid` backend | Error muncul sebelum request dikirim |
| Editor | `<textarea>` polos | Backend menyimpan teks biasa, bukan rich text |
| Styling | Tailwind v4 + token pastel (§2.7 brief) | Sama dengan prototipe Lovable; warna dan font diambil dari satu file token |
| Tema | Pastel, terang saja; font Geist | Warna dari Lovable, font dari ian-portfolio; tema gelap belum didesain |

Base URL saat penyambungan: `http://localhost:8080` — satu-satunya port yang ada di branch ini.
Di `dev` ada 8081–8083 untuk tiap service dan 8761 untuk Eureka; semuanya tidak untuk dipanggil
browser. Endpoint `/internal/**` yang disebut di Atlas juga sudah tidak ada di sini.

---

## Cara membuka

```bash
# Windows
start docs\FRONTEND-BRIEF.html
# macOS
open docs/FRONTEND-BRIEF.html
```

Font ditarik dari Google Fonts; tanpa internet isinya tetap terbaca dengan font sistem. Tema terang saja.

---

## Dokumen backend terkait

- [`schema.sql`](schema.sql) — snapshot skema, versi teks dari ERD
- [`DOCUMENTATION.md`](DOCUMENTATION.md) — arsitektur, alur request, tabel endpoint
- [`DOCKER-STEPS.md`](DOCKER-STEPS.md) — menyalakan backend untuk fase penyambungan
- http://localhost:8080/docs.html — Scalar, saat backend hidup
