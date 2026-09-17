# Design Handover — NoteNest

Dokumen ini adalah **input untuk desainer atau Claude Design**. Isinya satu hal: apa yang harus didesain, dan batasan apa yang tidak boleh dilanggar.

Semua nama field, batas panjang, dan aturan akses di bawah dibaca langsung dari kode di `backend/`. Kalau ada yang terasa kurang (tidak ada rich text, tidak ada folder, tidak ada foto profil yang bisa di-upload), itu memang kondisi backend hari ini, bukan kelalaian penulisan.

**Bahasa antarmuka: Bahasa Indonesia.**

---

## 1. Produknya

**NoteNest adalah buku catatan pribadi yang halamannya bisa dipinjamkan untuk dibaca.**

Bukan Notion, bukan Google Docs. Tidak ada kolaborasi, tidak ada edit bersama, tidak ada komentar. Seseorang menulis catatan untuk dirinya sendiri; sesekali ia meminjamkan satu catatan ke orang lain **untuk dibaca saja**.

Setiap kali muncul pertanyaan "elemen ini masuk atau tidak", ukurannya satu kalimat itu.

Konsekuensi desain yang sering terlewat:
- **Tidak ada avatar bertumpuk, indikator "sedang mengetik", atau kursor orang lain.** Tidak ada yang bisa mengedit bersamaan.
- **Penerima share tidak boleh melihat tombol yang tidak bisa ia pakai.** Bukan tombol abu-abu — tidak ada sama sekali. Yang ia lihat adalah label jelas: *dibagikan oleh Ani · hanya baca*.
- **Perubahan tidak realtime.** Kalau pemilik mengubah catatan, penerima baru melihatnya setelah memuat ulang. Jangan desain notifikasi atau badge "diperbarui".

---

## 2. Masalah desain utama: dua dunia di satu layar

Pengguna yang sama melihat dua jenis catatan yang terlihat mirip tapi berperilaku sangat berbeda:

| | Catatan saya | Dibagikan ke saya |
|---|---|---|
| Sumber | `GET /api/notes` | `GET /api/notes/shared-with-me` |
| Flag | `owned: true` | `owned: false` |
| Aksi | ubah, hapus, bagikan, cabut | **tidak ada** |
| Info pemilik | tidak ditampilkan | `ownerDisplayName` + `ownerEmail` |
| Pencarian, filter tag | ✅ | ❌ tidak didukung backend |
| Pagination | ✅ | ❌ semua sekaligus |

**Yang tidak boleh:** mencampur keduanya dalam satu daftar yang diurutkan bersama. Backend memisahkannya di dua endpoint dengan kemampuan berbeda; satu daftar gabungan akan membuat pencarian "sebagian bekerja".

**Arah yang direkomendasikan:** dua ruang yang jelas terpisah (tab, atau navigasi samping *Catatan saya* / *Dibagikan ke saya*), dengan penanda visual yang konsisten di mana pun catatan pinjaman muncul — garis tepi putus-putus dan chip label pemilik (warna kartu tidak bisa dipakai, lihat §6). Penanda yang sama dipakai di halaman detail, bukan hanya di daftar.

---

## 3. Batasan keras

| Batasan | Sumber | Artinya untuk desain |
|---|---|---|
| Isi catatan teks polos | `content TEXT`, tanpa format | Tidak ada toolbar bold/heading. Pertahankan baris baru apa adanya (`white-space: pre-wrap`) |
| Judul maks 200 karakter, wajib | `NoteRequest` | Judul panjang harus dipotong rapi di kartu, utuh di detail |
| Isi maks 20.000 karakter, boleh kosong | `NoteRequest` | Kartu catatan tanpa isi tetap harus terlihat disengaja |
| Maks 10 tag, masing-masing maks 30 karakter | `NoteRequest` | Tag disimpan **lowercase** — tampilkan lowercase, jangan kapitalisasi |
| Tidak ada daftar semua tag | tidak ada endpoint | Filter tag = chip dari tag yang muncul di halaman saat ini, atau input bebas |
| Permission hanya `READ` | `SharePermission` | Jangan desain pemilih izin "bisa edit" |
| Tidak ada upload gambar | `avatarUrl` hanya string URL | Avatar = inisial dari `displayName`; URL opsional kalau diisi |
| Tidak ada folder, pin, arsip | tidak ada kolom | Jangan tambahkan |
| Tidak ada kolom warna catatan | tidak ada kolom | Warna pastel diturunkan dari `id`; pengguna tidak bisa memilihnya |
| Tidak ada "keluar dari catatan yang dibagikan" | tidak ada endpoint untuk penerima | Penerima tidak punya aksi apa pun |
| Tidak ada lupa password, ubah password, ubah email | tidak ada endpoint | Halaman profil hanya nama tampilan, bio, URL avatar |

---

## 4. Data yang nyata

```
NoteResponse
  id                 "3f1c…" (UUID)
  ownerId            "9a0b…"
  title              "Rapat mingguan"
  content            "Bahas roadmap Q3\n- API share\n- Frontend"   (boleh null)
  tags               ["kerja", "rapat"]                            (lowercase)
  owned              true | false
  ownerEmail         "ani@example.com"   ← hanya di shared-with-me, lainnya null
  ownerDisplayName   "Ani"               ← hanya di shared-with-me, lainnya null
  createdAt          "2026-09-17T10:14:03.221"
  updatedAt          "2026-09-17T11:02:45.908"

ShareResponse
  id, noteId, sharedWithUserId
  sharedWithEmail    "budi@example.com"
  permission         "READ"
  createdAt

UserSummaryResponse   (hasil cari user)
  userId, email, displayName

ProfileResponse
  id, userId, email, displayName
  bio                maks 500, boleh null
  avatarUrl          maks 255, boleh null
  createdAt, updatedAt
```

### Aturan penulisan tanggal
- Waktu dari backend **tanpa zona waktu**. Perlakukan sebagai waktu lokal pengguna.
- Di daftar: relatif untuk hari ini (*12 menit lalu*, *3 jam lalu*), lalu *Kemarin*, lalu tanggal pendek (*14 Sep*), lalu dengan tahun (*14 Sep 2025*).
- Di detail: lengkap — *Diubah 17 September 2026, 11.02*. Format jam Indonesia memakai titik.

---

## 5. Layar yang perlu didesain

| # | Layar | Rute | Endpoint |
|---|---|---|---|
| 1 | Masuk | `/login` | `POST /api/auth/login` |
| 2 | Daftar | `/register` | `POST /api/auth/register` |
| 3 | Catatan saya | `/notes` | `GET /api/notes?q=&tag=&page=&sort=updatedAt,desc` |
| 4 | Tulis / ubah catatan | `/notes/new`, `/notes/:id/edit` | `POST` / `PUT /api/notes/{id}` |
| 5 | Detail catatan (milik sendiri) | `/notes/:id` | `GET /api/notes/{id}`, `DELETE` |
| 6 | Bagikan catatan (panel/dialog di atas #5) | — | `GET /{id}/shares`, `GET /api/users/search`, `POST /{id}/share`, `DELETE /{id}/share/{userId}` |
| 7 | Dibagikan ke saya | `/shared` | `GET /api/notes/shared-with-me` |
| 8 | Detail catatan (pinjaman) | `/notes/:id` | `GET /api/notes/{id}` → `owned: false` |
| 9 | Profil | `/profile` | `GET` / `PUT /api/users/me` |

Layar #5 dan #8 adalah **rute yang sama** dengan tampilan berbeda berdasarkan `owned`.

### Panel bagikan (#6) — detail
- Kolom cari: ketik sebagian email → `GET /api/users/search?email=` → maks 10 hasil, diri sendiri sudah dibuang backend.
- Hasil hanya berisi orang yang **pernah membuka aplikasi** (profil dibuat saat pertama login). Keadaan kosong harus menjelaskan ini tanpa jargon: *"Tidak ketemu? Orang itu perlu masuk ke NoteNest sekali dulu."*
- Boleh juga mengetik email lengkap dan langsung membagikan — backend akan membalas `404` kalau tidak terdaftar.
- Membagikan ke orang yang sudah ada di daftar **bukan error** — backend mengembalikan share yang sama. Jangan tampilkan duplikat.
- Daftar penerima: email + tanggal dibagikan + tombol cabut. Cabut butuh konfirmasi ringan (satu klik lagi), bukan modal besar.

### Keadaan yang wajib ikut didesain

| Layar | Kosong | Memuat | Gagal |
|---|---|---|---|
| Catatan saya | Belum ada catatan → ajakan menulis yang pertama | Kerangka kartu | Pesan + coba lagi |
| Catatan saya (hasil cari/filter) | *Tidak ada catatan yang cocok* + hapus filter | — | — |
| Dibagikan ke saya | Belum ada yang membagikan — jelaskan cara kerjanya | Kerangka kartu | `404` pemilik hilang → pesan umum, bukan detail teknis |
| Detail | — | Kerangka | `404` tidak ada · `403` tidak punya akses (kembali ke daftar, **tidak logout**) |
| Panel bagikan | Belum dibagikan ke siapa pun | Spinner kecil di tombol | `404` email tidak terdaftar (inline) · `400` diri sendiri (inline) · `502` layanan sedang bermasalah (coba lagi) |
| Hapus catatan | — | — | Konfirmasi wajib menyebut bahwa **semua akses yang dibagikan ikut hilang** |

### Aturan validasi (tiru persis)

| Form | Aturan |
|---|---|
| Daftar | nama tampilan wajib, maks 60 · email wajib + format · password wajib, min 8 |
| Masuk | email wajib + format · password wajib |
| Catatan | judul wajib, maks 200 · isi maks 20.000 · tag maks 10, masing-masing tidak kosong, maks 30 |
| Bagikan | email wajib + format |
| Profil | nama tampilan wajib, maks 60 · bio maks 500 · URL avatar maks 255 |

Penghitung karakter hanya muncul saat mendekati batas (≥ 80%), bukan terus-menerus.

---

## 6. Design system

Token lengkap, komponen, dan contoh visualnya ada di **Bagian 2 `FRONTEND-BRIEF.html`**. Ringkasan arahnya:

- **Tema pastel, terang saja.** Latar biru keabuan muda `#F0F5FA`, panel putih, aksi navy `#1E3976`. Warna diambil dari prototipe Lovable.
- **Lima pastel hanya untuk kartu catatan:** blue `#6DCFFA`, pink `#FFB0B2`, yellow `#F5DF74`, mint `#99E4C0`, lilac `#D5BDFC`. Warna **diturunkan dari `id` catatan** (backend tidak menyimpan warna), jadi satu catatan selalu berwarna sama di mana pun, termasuk di layar penerima share.
- **Satu font: Geist** (400/500/600), sama dengan ian-portfolio. Hierarki dari ukuran dan berat, bukan font kedua.
- **Teks di atas pastel selalu navy** (`#181F2E` atau `#3D4659`). Abu-abu gagal kontras di semua pastel.
- **Tag berupa pil putih transparan** di atas kartu — bukan pil berwarna, karena kartunya sudah berwarna.
- **Catatan pinjaman ditandai bentuk, bukan warna:** garis tepi putus-putus navy + chip putih *dibagikan oleh Ani · hanya baca*. Kelima pastel sudah terpakai, jadi warna tidak bisa jadi penanda.
- **Radius 8px** (tombol, input), **12px** (kartu), **16px** (panel); bayangan lembut ber-tint navy.
- Merah `#D33944` untuk garis/ikon hapus dan error; teks merah memakai `#B4232F`.

---

## 7. Responsif & aksesibilitas

- Layar sempit (375px): navigasi jadi bar bawah atau menu; panel bagikan jadi layar penuh.
- Editor catatan harus nyaman di ponsel: judul dan isi saling bersambung, tombol simpan selalu terjangkau.
- Tema terang saja — belum ada tema gelap.
- Kontras teks minimal 4.5:1, termasuk di atas kartu pastel. Penanda catatan pinjaman selalu garis putus-putus **dan** label teks.
- Semua aksi bisa dijangkau keyboard; fokus terlihat.

---

## 8. Yang tidak boleh didesain

- Editor rich text, markdown preview, lampiran file
- Edit bersama, komentar, riwayat versi
- Pemilih izin (baca/edit)
- Folder, pin, arsip, favorit, pemilih warna catatan (warna otomatis dari `id`)
- Notifikasi, badge "baru dibagikan"
- Login dengan Google, lupa password
- Panel admin

---

## 9. Definition of done

- Kesembilan layar di §5, di lebar 375px dan ≥1280px
- Setiap layar dengan keadaan kosong, memuat, dan gagal dari tabel §5
- Tema pastel terang, font Geist, token persis dari §2.7 `FRONTEND-BRIEF.html`
- Catatan pinjaman terbedakan tanpa bergantung pada warna
- Tidak ada satu pun elemen dari §8

---

## Rujukan
- [`FRONTEND-BRIEF.html`](FRONTEND-BRIEF.html) — PRD + design system lengkap
- [`FRONTEND-SERVICE-ATLAS.html`](FRONTEND-SERVICE-ATLAS.html) — bentuk data
- [`schema.sql`](schema.sql) — kolom persis
