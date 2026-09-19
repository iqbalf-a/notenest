# Dokumentasi NoteNest — branch `deploy/monolith`

Folder ini berisi seluruh dokumentasi project. Halaman ini daftar isinya — mulai dari sini, bukan dari daftar file.

> Dokumen di sini menggambarkan backend sebagai **satu aplikasi Spring Boot**.
> Versi microservices-nya ada di branch `dev`; kenapa keduanya ada:
> [`DEPLOY-MONOLITH.md`](DEPLOY-MONOLITH.md).

> Untuk **menjalankan**: `cp .env.example .env` lalu `docker compose up --build`,
> kemudian buka http://localhost:8080/docs.html.

---

## Mulai dari mana

| Kalau kamu ingin… | Baca berurutan |
|---|---|
| **Paham kenapa branch ini ada** | [DEPLOY-MONOLITH](DEPLOY-MONOLITH.md) |
| **Paham sistemnya** | [DOCUMENTATION](DOCUMENTATION.md) → [ROADMAP](ROADMAP.md) |
| **Menulis kode / menambah fitur backend** | [BUILD-ORDER](BUILD-ORDER.md) → [DOCUMENTATION](DOCUMENTATION.md) → [TESTING-NOTES](TESTING-NOTES.md) |
| **Membangun frontend-nya** | [FRONTEND-README](FRONTEND-README.md) — paket serah-terima dengan urutan baca sendiri |
| **Mendesain antarmukanya** | [DESIGN-HANDOVER](DESIGN-HANDOVER.md) |
| **Bersiap wawancara** | [INTERVIEW-QA](INTERVIEW-QA.md) → [ROADMAP](ROADMAP.md) bagian celah & simplifikasi |
| **Menyalakan lewat Docker** | [DOCKER-NOTES](DOCKER-NOTES.md) → [DOCKER-STEPS](DOCKER-STEPS.md) |
| **Mendeploy ke free tier** | [DEPLOY-MONOLITH](DEPLOY-MONOLITH.md) bagian 5 |
| **Tahu kenapa project ini ada** | [HANDOVER](HANDOVER.md) |

---

## Daftar dokumen

### Backend — referensi

| Dokumen | Isi |
|---|---|
| [`DEPLOY-MONOLITH.md`](DEPLOY-MONOLITH.md) | **Khas branch ini.** Kenapa dirakit jadi satu aplikasi, apa yang dihapus, cara deploy, cara menarik perubahan dari `dev` |
| [`DOCUMENTATION.md`](DOCUMENTATION.md) | Acuan utama. Arsitektur, konsep yang dipakai, alur request, model otorisasi, endpoint + hak akses, struktur folder |
| [`BUILD-ORDER.md`](BUILD-ORDER.md) | Urutan menulis kode — paket mana dulu, file mana dulu, dan kenapa |
| [`TESTING-NOTES.md`](TESTING-NOTES.md) | 36 test: unit test service, test repository H2, test end-to-end satu context, gotcha |
| [`ROADMAP.md`](ROADMAP.md) | Fase yang selesai, yang tersisa, celah yang diketahui |
| [`INTERVIEW-QA.md`](INTERVIEW-QA.md) | Tanya-jawab keputusan desain — termasuk kenapa arsitekturnya punya dua bentuk |
| [`HANDOVER.md`](HANDOVER.md) | Blueprint awal — ditulis sebelum kode ada, menggambarkan bentuk microservices |

### Docker

| Dokumen | Isi |
|---|---|
| [`DOCKER-NOTES.md`](DOCKER-NOTES.md) | Konsep: image, container, volume, network, multi-stage build, batas memori |
| [`DOCKER-STEPS.md`](DOCKER-STEPS.md) | Runbook: arti tiap blok Compose, langkah menjalankan, verifikasi end-to-end dua akun, troubleshooting |

### Frontend — paket serah-terima

Audiensnya berbeda: siapa pun yang akan membangun `../frontend/`. Dua file HTML-nya berdiri sendiri — cukup dibuka di browser.

Kontrak API-nya **sama persis di kedua branch** — base URL, path, body, status code. Yang berbeda hanya siapa yang melayaninya di balik layar.

| Dokumen | Isi |
|---|---|
| [`FRONTEND-README.md`](FRONTEND-README.md) | **Pintu masuk paket ini** — urutan baca, tiga hal yang harus dipegang, checklist penyambungan |
| [`FRONTEND-SERVICE-ATLAS.html`](FRONTEND-SERVICE-ATLAS.html) | Peta service, ERD 5 tabel, perjalanan satu share, 15 endpoint publik |
| [`FRONTEND-BRIEF.html`](FRONTEND-BRIEF.html) | PRD (scope, rute, kontrak API, dummy data, celah backend) + design system |
| [`DESIGN-HANDOVER.md`](DESIGN-HANDOVER.md) | Brief untuk desainer: sembilan layar, keadaan wajib, batasan keras |

### Aset

| File | Isi |
|---|---|
| [`schema.sql`](schema.sql) | Snapshot skema database — versi teks dari ERD. Untuk dibaca, bukan dijalankan |

---

## Dokumen yang sengaja tidak disesuaikan

| Dokumen | Alasan |
|---|---|
| `HANDOVER.md` | Blueprint yang ditulis **sebelum** kode ada. Nilainya justru pada jarak antara rencana dan hasil; menyuntingnya menghapus catatan itu. Lihat tabel selisih di bawah |
| `FRONTEND-SERVICE-ATLAS.html` | Menggambarkan peta service versi `dev`. Kontrak API-nya tetap benar; yang berbeda cuma jumlah proses di belakangnya |
| `DESIGN-HANDOVER.md` | Murni soal antarmuka — tidak menyentuh arsitektur backend |

### Selisih `HANDOVER.md` dengan kode

| Di blueprint | Kenyataannya |
|---|---|
| Entity `NoteTag` | `@ElementCollection` — tabel `note_tags` tanpa entity |
| Profil mungkin dibuat saat register | Tidak pernah; profil dibuat lazy di `/api/users/me` |
| `/internal/users/{id}` saja | Ditambah `/internal/users/by-email` — lalu **dihapus** di branch ini |
| — | Ditambah `GET /api/auth/validate` dan `GET /api/notes/{id}/shares` |
| `note_shares` tanpa email | Ditambah `shared_with_email` sebagai snapshot |
| Service ada di root repo | Dipindah ke `backend/` |
| Unit test service saja | Ditambah `@DataJpaTest` dan `ApiEndToEndTest` |
| Enam modul Maven | **Satu** modul di branch ini |

---

## Bahasa

| Dokumen | Bahasa |
|---|---|
| `ROADMAP.md` | Inggris |
| Selebihnya | Indonesia |
