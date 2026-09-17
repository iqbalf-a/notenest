# Dokumentasi NoteNest

Folder ini berisi seluruh dokumentasi project. Halaman ini daftar isinya — mulai dari sini, bukan dari daftar file.

> Untuk **menjalankan** sistemnya, lihat [`../README.md`](../README.md) di root: satu perintah `docker compose up -d --build`, lalu buka http://localhost:8080/docs.html.

---

## Mulai dari mana

| Kalau kamu ingin… | Baca berurutan |
|---|---|
| **Paham sistemnya** | [DOCUMENTATION](DOCUMENTATION.md) → [ROADMAP](ROADMAP.md) |
| **Menulis kode / menambah fitur backend** | [BUILD-ORDER](BUILD-ORDER.md) → [DOCUMENTATION](DOCUMENTATION.md) → [TESTING-NOTES](TESTING-NOTES.md) |
| **Membangun frontend-nya** | [FRONTEND-README](FRONTEND-README.md) — paket serah-terima dengan urutan baca sendiri |
| **Mendesain antarmukanya** | [DESIGN-HANDOVER](DESIGN-HANDOVER.md) |
| **Bersiap wawancara** | [INTERVIEW-QA](INTERVIEW-QA.md) → [ROADMAP](ROADMAP.md) bagian celah & simplifikasi |
| **Menyalakan dan memverifikasi lewat Docker** | [DOCKER-NOTES](DOCKER-NOTES.md) → [DOCKER-STEPS](DOCKER-STEPS.md) |
| **Tahu kenapa project ini ada** | [HANDOVER](HANDOVER.md) |

---

## Daftar dokumen

### Backend — referensi

| Dokumen | Isi |
|---|---|
| [`DOCUMENTATION.md`](DOCUMENTATION.md) | Acuan utama. Arsitektur, konsep yang dipakai, alur request (register, share, jalur 403), model otorisasi, endpoint + hak akses, struktur folder |
| [`BUILD-ORDER.md`](BUILD-ORDER.md) | Urutan menulis kode — service mana dulu, file mana dulu, dan kenapa |
| [`TESTING-NOTES.md`](TESTING-NOTES.md) | 29 test: kenapa di service layer, test error Feign, test repository H2, gotcha |
| [`ROADMAP.md`](ROADMAP.md) | Fase yang selesai, yang tersisa, celah yang diketahui |
| [`INTERVIEW-QA.md`](INTERVIEW-QA.md) | Tanya-jawab keputusan desain yang khas NoteNest |
| [`HANDOVER.md`](HANDOVER.md) | Blueprint awal — ditulis sebelum kode ada. Beberapa detail berubah saat implementasi (lihat catatan di bawah) |

### Docker

| Dokumen | Isi |
|---|---|
| [`DOCKER-NOTES.md`](DOCKER-NOTES.md) | Konsep: image, container, volume, network, profil `docker`, urutan start |
| [`DOCKER-STEPS.md`](DOCKER-STEPS.md) | Runbook: arti tiap blok Compose, langkah menjalankan, verifikasi end-to-end dua akun, troubleshooting |

### Frontend — paket serah-terima

Audiensnya berbeda: siapa pun yang akan membangun `../frontend/`. Dua file HTML-nya berdiri sendiri — cukup dibuka di browser.

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

## Catatan soal HANDOVER.md

`HANDOVER.md` ditulis sebelum kode ada dan sengaja **tidak** diperbarui. Selisih dengan sistem yang dibangun:

| Di blueprint | Kenyataannya |
|---|---|
| Entity `NoteTag` | `@ElementCollection` — tabel `note_tags` tanpa entity |
| Profil mungkin dibuat saat register ("kalau opsi auto-create profile dipakai") | Tidak pernah memanggil user-service saat register; profil dibuat lazy di `/api/users/me` |
| `/internal/users/{id}` saja | Ditambah `/internal/users/by-email` — share memakai email |
| — | Ditambah `GET /api/auth/validate` dan `GET /api/notes/{id}/shares` |
| `note_shares` tanpa email | Ditambah `shared_with_email` sebagai snapshot |
| Service ada di root repo | Dipindah ke `backend/` |
| Unit test service saja | Ditambah `@DataJpaTest` untuk `NoteRepository` |

---

## Bahasa

| Dokumen | Bahasa |
|---|---|
| `ROADMAP.md` | Inggris |
| Selebihnya | Indonesia |
