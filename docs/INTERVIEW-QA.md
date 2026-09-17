# NoteNest — Persiapan Wawancara (Tanya-Jawab)

Pertanyaan yang mungkin diajukan tentang project ini, dengan jawaban ringkas yang bisa kamu ucapkan sendiri. Pertanyaan umum microservices, JWT, dan JPA yang jawabannya identik dengan ShopNest tidak diulang panjang di sini — fokusnya pada hal yang **khas NoteNest**.

> Tips: kalau ditanya sesuatu yang tidak kamu tahu, jujur + tunjukkan cara berpikir. Interviewer menilai penalaran, bukan hafalan.

---

## A. Kenapa project ini ada

**Q: Kamu sudah punya ShopNest. Kenapa membuat project kedua dengan arsitektur yang sama?**
Karena ShopNest tidak pernah sampai frontend — domainnya terlalu besar (auth, user, product, order, keranjang, checkout). Saya ingin satu project yang **utuh** dari database sampai layar. NoteNest mempertahankan semua konsep arsitekturnya tapi memilih domain yang cukup kecil untuk diselesaikan. Keputusan ini juga menunjukkan bahwa saya bisa memilah: yang dipertahankan konsepnya, yang dipangkas scope-nya.

**Q: Aplikasi catatan sekecil ini butuh microservices?**
Tidak. Untuk produk nyata sebesar ini monolith jelas lebih praktis, dan saya akan mengatakan itu lebih dulu. Microservices di sini adalah bahan latihan — dan karena domainnya kecil, kompleksitas yang tersisa murni kompleksitas arsitektur, bukan kompleksitas bisnis. Itu justru membuatnya lebih mudah dijelaskan.

**Q: Kenapa fitur share tidak dibuang saja supaya lebih sederhana?**
Karena tanpa share, tidak ada alasan jujur untuk panggilan antar service. Saya tidak mau Feign yang dipaksakan. Share memberi kebutuhan nyata: client mengirim email, note-service hanya bisa menyimpan `userId`, dan hanya user-service yang tahu pemetaannya.

---

## B. Komunikasi antar service (Feign)

**Q: Jelaskan alur share note.**
Controller menerima `X-User-Id` dari gateway dan `targetEmail` dari body. Service mencari note (404 kalau tidak ada), memastikan pemanggil adalah pemilik (403), lalu memanggil `UserClient.getUserByEmail` lewat Feign ke `/internal/users/by-email`. Kalau target sama dengan pemilik → 400. Kalau sudah pernah di-share → kembalikan share lama. Kalau belum → simpan `NoteShare` dengan snapshot email.

**Q: Kenapa cek pemilik dilakukan sebelum Feign, bukan sesudah?**
Dua alasan. Pertama, hemat — tidak ada gunanya bertanya ke service lain untuk request yang pasti ditolak. Kedua, dan ini yang lebih penting: kalau urutannya terbalik, siapa pun dengan token sah bisa memakai endpoint share pada note orang lain untuk menguji apakah suatu email terdaftar — 404 berarti tidak, 403 berarti ya. Urutan ini dikunci oleh test `shareNote_byNonOwner_isForbiddenBeforeAnyFeignCall`.

**Q: Apa yang terjadi kalau email tujuan tidak terdaftar?**
user-service membalas 404. Feign melemparnya sebagai `FeignException.NotFound`. `NoteServiceImpl` menangkap **hanya** tipe itu dan menerjemahkannya jadi `UserNotFoundException` dengan pesan yang berarti. Client menerima 404 "No NoteNest user registered with email: ...", bukan detail internal Feign.

**Q: Dan kalau user-service mati?**
Itu `FeignException` jenis lain, dan sengaja **tidak** ditangkap di service. Ia naik ke `GlobalExceptionHandler` dan dijawab 502. Membedakannya penting: 404 adalah kesalahan pemakai, 502 adalah masalah infrastruktur. Kalau keduanya dijawab 404, client akan mengira email-nya salah padahal servernya yang bermasalah. Timeout Feign juga saya set eksplisit (connect 3 detik, read 5 detik) supaya service yang menggantung tidak ikut menggantungkan note-service.

**Q: Kenapa email disimpan di `note_shares`? Bukankah itu duplikasi data?**
Ya, disengaja — pola snapshot yang sama dengan harga di `order_items` ShopNest. Tanpa snapshot, menampilkan "dibagikan ke siapa" butuh satu panggilan Feign per penerima. Harganya: kalau suatu hari email bisa diubah, snapshot itu basi. Saat ini auth-service tidak punya fitur ubah email, jadi trade-off-nya murah.

**Q: `shared-with-me` memanggil Feign untuk tiap note. Itu N+1, kan?**
Sebagian. Hasil lookup di-cache per request dengan `computeIfAbsent`, jadi pemilik yang sama hanya ditanya sekali — lima note dari satu orang = satu panggilan. Tapi lima pemilik berbeda tetap lima panggilan. Solusi proper-nya endpoint batch di user-service (`GET /internal/users?ids=...`). Belum saya buat karena di skala ini jumlah pemilik per user kecil.

**Q: Ada kelemahan di `shared-with-me`?**
Ada, dan saya menemukannya saat menulis dokumentasi. Kalau satu pemilik tidak punya profil — misalnya ia share note tapi tidak pernah membuka halaman profil — lookup-nya 404, diterjemahkan jadi `UserNotFoundException`, dan **seluruh daftar** gagal, bukan cuma satu item. Perbaikannya sederhana: kembalikan field pemilik `null` untuk item itu. Sudah dicatat di ROADMAP.

---

## C. Security & otorisasi

**Q: Siapa yang boleh membaca sebuah note?**
Pemiliknya, atau user yang punya baris di `note_shares` untuk note itu. Response membawa flag `owned` supaya frontend tahu kapan menyembunyikan tombol edit dan hapus. Ubah, hapus, share, cabut share, dan lihat daftar share: hanya pemilik.

**Q: Kenapa otorisasi di service, bukan di gateway?**
Karena semua aturan NoteNest berbasis kepemilikan, dan menjawab "apakah B boleh membaca note X" butuh membaca `notes` dan `note_shares` — database milik note-service. Gateway tidak punya akses itu dan tidak boleh punya. NoteNest bahkan tidak punya satu pun aturan berbasis role.

**Q: Kenapa 403 untuk note orang lain, bukan 404? Bukankah 403 membocorkan bahwa note itu ada?**
Pertanyaan bagus, dan saya mempertimbangkannya. 404 memang lebih rapat. Saya memilih 403 karena id note adalah UUID acak — tidak bisa ditebak atau di-enumerasi — jadi kebocorannya praktis nol, sementara 403 lebih jujur untuk debugging dan untuk frontend. Kalau id-nya berurutan, jawaban saya akan berbeda.

**Q: Bagaimana endpoint internal dilindungi?**
`/internal/**` ada di `INTERNAL_PATTERNS` gateway dan ditolak 403 **sebelum** token diperiksa — token sah pun tidak cukup, karena endpoint itu tidak punya konsep pemilik. Rute-nya sengaja didaftarkan di gateway supaya jawabannya 403 yang jelas, bukan 404. Feign tidak lewat gateway, jadi tidak terpengaruh. Ini kontrol perimeter, bukan defense in depth; langkah berikutnya token service-to-service atau mTLS.

**Q: Header `X-User-*` bisa dipalsukan client?**
Tidak. Di path terlindungi gateway menimpanya dengan `headers.set` dari klaim token. Di path publik gateway membuangnya dengan `headers.remove`. Asumsinya: gateway satu-satunya pintu. Di Compose asumsi itu dijaga karena hanya port 8080 dan 8761 yang di-publish.

**Q: Beda 401 dan 403 di project ini?**
401 = identitas bermasalah (token tidak ada, salah, kedaluwarsa, atau login salah) — frontend harus logout. 403 = identitas sah tapi tidak berhak (bukan pemilik, atau path internal) — frontend **tidak boleh** logout. Ada juga 401 dari service kalau header `X-User-*` hilang, artinya request tidak lewat gateway; tanpa handler khusus, itu akan muncul sebagai 500.

---

## D. Desain data

**Q: Kenapa profil tidak dibuat saat register?**
Kalau auth-service memanggil user-service saat register dan panggilan itu gagal, user sudah tersimpan tapi profilnya tidak. Itu transaksi lintas service yang butuh Saga untuk dibereskan. Saya menghindarinya: auth-service menaruh `userId`, `email`, dan `name` di token; user-service membuat profil saat `/api/users/me` pertama kali dipanggil, dari header yang sudah terverifikasi. Register tidak bisa gagal separuh jalan.

**Q: Apa konsekuensi buruknya?**
User yang baru register tapi belum pernah membuka profil tidak bisa ditemukan untuk di-share, karena baris profilnya belum ada. Saya menutupnya di sisi frontend: panggil `/api/users/me` tepat setelah login. Solusi yang lebih bersih adalah event `UserRegistered` lewat message broker.

**Q: Kenapa tag pakai `@ElementCollection`, bukan entity?**
Tag di sini cuma string milik satu note — tidak punya id, tidak diubah sendiri, tidak punya atribut lain. Entity terpisah hanya menambah id dan repository yang tidak dipakai. Blueprint awal menyebut `NoteTag` sebagai entity; saya mengubahnya saat menulis kode. Tag juga disimpan lowercase supaya "Java" dan "java" tidak jadi dua filter berbeda.

**Q: Query pencarian note ditulis tangan. Kenapa, dan apa risikonya?**
Satu query melayani tiga kombinasi — tanpa filter, per tag, dan cari teks di judul/isi — dengan parameter `null` berarti filter mati. Derived query untuk itu akan jadi tiga method dengan nama sangat panjang. Risikonya: karena join ke tag, satu note bisa muncul berkali-kali, dan hitungan halaman bisa salah. Makanya ada `distinct` dan `countQuery` eksplisit — dan karena test bermock tidak pernah menjalankan query, saya menambah `@DataJpaTest` dengan H2 khusus untuk repository ini.

**Q: Share ulang ke orang yang sama?**
Mengembalikan share yang sudah ada, bukan error. Dari sisi pemakai hasilnya sama — orang itu punya akses. Duplikasi tetap dijaga di database dengan unique constraint `(note_id, shared_with_user_id)`, jadi dua request bersamaan pun tidak bisa menghasilkan dua baris.

---

## E. Testing

**Q: Bagaimana kamu menguji Feign tanpa menjalankan user-service?**
`UserClient` adalah interface, jadi di-mock seperti repository. Untuk skenario error saya pakai `thenThrow(mock(FeignException.NotFound.class))` dan `mock(FeignException.ServiceUnavailable.class)`. Yang diverifikasi: 404 diterjemahkan jadi `UserNotFoundException`, 503 diteruskan apa adanya, dan di kedua kasus `save()` tidak pernah dipanggil.

**Q: Berapa test dan apa yang tidak diuji?**
29 test: 4 auth, 6 user, 14 note service, 5 repository. Yang tidak diuji: controller (MockMvc) dan full context. Saya juga jujur bahwa sistemnya belum diverifikasi end-to-end lewat Docker Compose — itu item terbuka di ROADMAP.

---

## F. Pertanyaan lanjutan yang mungkin muncul

**Q: Bagaimana menambah permission EDIT?**
Enum sudah disiapkan (bukan boolean) untuk itu. Tambah `EDIT`, tambah field di `ShareNoteRequest`, ubah `updateNote` dari "hanya pemilik" jadi "pemilik atau share EDIT". Satu jebakan: Hibernate membuat check constraint untuk enum dan `ddl-auto=update` tidak memperbaruinya, jadi constraint itu harus diubah manual — alasan bagus untuk pindah ke Flyway.

**Q: Bagaimana membuat share jadi realtime?**
Server-Sent Events dari note-service lebih sederhana dari WebSocket karena arahnya satu: server memberi tahu "ada note baru dibagikan ke kamu". Tantangannya di gateway (koneksi panjang harus lolos filter JWT) dan kalau note-service punya beberapa instance, notifikasinya butuh broker supaya sampai ke instance yang memegang koneksi user.

**Q: Kalau harus pakai Config Server sungguhan?**
Server dan `config-repo`-nya sudah ada. Yang kurang: `spring-cloud-starter-config` dan `spring.config.import=optional:configserver:` di tiap service, lalu pindahkan konfigurasi bersama (Eureka URL, actuator) dari properties per service ke `config-repo`. Saya tidak melakukannya karena di Compose env var sudah menyelesaikan masalah yang sama dengan komponen lebih sedikit.

---

## G. Behavioral

**Q: Apa yang kamu pelajari dari ShopNest yang langsung dipakai di sini?**
Otorisasi. Di ShopNest saya baru sadar belakangan bahwa identitas dari gateway diterima tapi tidak pernah dipakai untuk mengecek kepemilikan — tujuh endpoint terdampak. Di NoteNest cek kepemilikan dan penanganan header yang hilang sudah ada sejak commit pertama, begitu juga CORS yang di ShopNest baru ketahuan saat frontend mulai dibuat.

**Q: Apa yang akan kamu tingkatkan?**
Verifikasi end-to-end, perbaikan `shared-with-me` agar tidak gagal total, endpoint batch untuk lookup pemilik, lalu frontend-nya. Setelah itu: Flyway, dan refresh token.
