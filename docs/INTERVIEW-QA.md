# NoteNest — Persiapan Wawancara (Tanya-Jawab)

Pertanyaan yang mungkin diajukan tentang project ini, dengan jawaban ringkas yang bisa kamu
ucapkan sendiri. Pertanyaan umum microservices, JWT, dan JPA yang jawabannya identik dengan
ShopNest tidak diulang panjang di sini — fokusnya pada hal yang **khas NoteNest**.

> Branch ini punya satu bahan wawancara yang tidak dimiliki `dev`: project yang sama
> punya **dua bentuk arsitektur**, dan ada alasan konkret di balik keduanya.
> Bagian A2 khusus membahas itu.

> Tips: kalau ditanya sesuatu yang tidak kamu tahu, jujur + tunjukkan cara berpikir.
> Interviewer menilai penalaran, bukan hafalan.

---

## A. Kenapa project ini ada

**Q: Kamu sudah punya ShopNest. Kenapa membuat project kedua dengan arsitektur yang sama?**
Karena ShopNest tidak pernah sampai frontend — domainnya terlalu besar (auth, user, product,
order, keranjang, checkout). Saya ingin satu project yang **utuh** dari database sampai layar.
NoteNest mempertahankan semua konsep arsitekturnya tapi memilih domain yang cukup kecil untuk
diselesaikan. Keputusan ini juga menunjukkan bahwa saya bisa memilah: yang dipertahankan
konsepnya, yang dipangkas scope-nya.

**Q: Aplikasi catatan sekecil ini butuh microservices?**
Tidak. Untuk produk nyata sebesar ini monolith jelas lebih praktis, dan saya akan mengatakan
itu lebih dulu. Microservices di sini adalah bahan latihan — dan karena domainnya kecil,
kompleksitas yang tersisa murni kompleksitas arsitektur, bukan kompleksitas bisnis. Itu justru
membuatnya lebih mudah dijelaskan.

Yang menarik, saya akhirnya benar-benar membuktikan kalimat itu: ada branch monolith-nya, dan
ia jalan dengan fitur yang sama persis.

**Q: Kenapa fitur share tidak dibuang saja supaya lebih sederhana?**
Karena tanpa share, tidak ada alasan jujur untuk ada batas antar domain. Saya tidak mau Feign
yang dipaksakan. Share memberi kebutuhan nyata: client mengirim email, sisi note hanya bisa
menyimpan `userId`, dan hanya sisi user yang tahu pemetaannya.

---

## A2. Dua bentuk arsitektur

**Q: Kenapa ada branch `deploy/monolith`?**
Karena saya ingin project ini bisa dilihat orang lain, dan arsitektur aslinya tidak muat di
mana pun yang gratis. Enam JVM butuh ~2,4 GB RAM; free tier memberi satu service 512 MB.
Render juga memberi 750 jam per bulan **per workspace** — enam service yang hidup 24/7 memakai
~4.380 jam, habis dalam lima hari. Satu service memakai ~730 jam, dan itu muat.

Ada kendala ketiga yang lebih halus: free tier tidak punya private networking. Eureka dan Feign
jadi harus lewat URL publik, dan tiap hop ke service yang sedang tidur kena cold start satu
menit. Arsitekturnya bukan cuma mahal di sana — ia tidak akan berfungsi.

Jadi pilihannya: bayar, palsukan demo, atau rakit ulang untuk target deploy-nya. Saya pilih
yang ketiga, dan mendokumentasikan alasannya.

**Q: Bukankah itu artinya microservices-nya sia-sia?**
Tidak, dan justru sebaliknya yang saya pelajari. Merakit ulang enam service jadi satu memaksa
saya menjawab pertanyaan yang tidak pernah muncul saat membangunnya: mana yang benar-benar
batas domain, dan mana yang cuma konsekuensi dari adanya jaringan?

Ternyata sebagian besar infrastruktur yang saya tulis termasuk kategori kedua. Eureka, Config
Server, routing gateway, amplop JSON untuk parsing balasan Feign — semuanya hilang tanpa
menyentuh satu baris pun logika bisnis. Yang tersisa dan tetap ada di monolith: batas antara
note dan user. Itu batas yang nyata.

**Q: Apa yang tetap dipertahankan?**
`noteservice` masih tidak boleh meng-import apa pun dari `userservice`. Ia hanya tahu interface
`UserClient` dan DTO `UserResponse` versinya sendiri, dan yang mengisinya adalah `LocalUserClient`
di paket `common` — satu-satunya kelas yang melihat kedua sisi.

Itu bukan formalitas. Selama batas itu berdiri, pemisahannya bisa dikembalikan tanpa menyentuh
logika bisnis: tinggal ganti isi `UserClient` dari panggilan method jadi panggilan HTTP.
Begitu ada satu `import com.notenest.userservice` di dalam `noteservice`, kemampuan itu hilang.

**Q: Apa yang paling sulit saat menggabungkannya?**
Gateway-nya, karena ia satu-satunya yang tidak bisa disalin. Spring Cloud Gateway itu reaktif —
`ServerWebExchange`, `Mono`, `GlobalFilter` — sedangkan ketiga service domain pakai Spring MVC
yang servlet. Dua stack itu tidak bisa hidup dalam satu aplikasi, jadi `JwtAuthFilter`-nya
harus ditulis ulang.

Yang saya jaga adalah **kontraknya**, bukan kodenya: controller tetap cuma melihat header
`X-User-*` dan tidak pernah melihat JWT. Karena itu `UserController` dan `NoteController` tidak
berubah sebaris pun. Satu detail teknis: servlet tidak mengizinkan header request diubah, jadi
request dibungkus `HttpServletRequestWrapper` yang membuang `X-User-*` kiriman client lalu
memasang versi server.

**Q: Ada bug yang muncul karena penggabungan itu?**
Dua, dan dua-duanya lolos kompilasi dan lolos semua unit test.

Pertama, `@EnableJpaAuditing` saya taruh di kelas `@Configuration` terpisah, seperti aslinya.
Karena `@EnableJpaRepositories` harus ditulis eksplisit (entity-nya ada di paket lain),
EntityManagerFactory dibangun lebih dulu daripada config auditing — `AuditingEntityListener`
tidak pernah terpasang dan setiap insert gagal dengan `created_at` NULL.

Kedua, saya mengeluarkan kelas-kelas yang bentrok lewat pola exclude di Maven. Pola
`**/exception/GlobalExceptionHandler.java` ternyata dicocokkan ke *setiap* source root — termasuk
milik monolith sendiri — jadi handler gabungan yang baru saya tulis ikut terbuang. Gejalanya:
`404` muncul sebagai `500`.

Keduanya ketahuan oleh `@SpringBootTest` yang men-start seluruh context. Itu yang membuat saya
menambahkannya di branch ini, padahal di `dev` sengaja saya lewatkan.

**Q: Kalau harus memilih satu untuk produksi, mana?**
Untuk produk sebesar NoteNest: monolith, tanpa ragu. Satu proses, satu deploy, satu tempat
melihat log, dan tidak ada kelas kegagalan yang datang dari jaringan. Microservices baru
terbayar kalau ada tim yang perlu rilis terpisah atau bagian sistem yang perlu diskalakan
sendiri — dan NoteNest tidak punya keduanya.

Saya tetap membangun versi microservices-nya karena tujuannya belajar. Tapi saya tidak akan
merekomendasikannya untuk kasus ini kalau ditanya sebagai engineer.

---

## B. Batas antar domain

**Q: Jelaskan alur share note.**
Controller menerima `X-User-Id` dari filter dan `targetEmail` dari body. Service mencari note
(404 kalau tidak ada), memastikan pemanggil adalah pemilik (403), lalu memanggil
`userClient.findByEmail`. Kalau hasilnya `Optional` kosong → 404 dengan pesan yang berarti.
Kalau target sama dengan pemilik → 400. Kalau sudah pernah di-share → kembalikan share lama.
Kalau belum → simpan `NoteShare` dengan snapshot email.

Di branch `dev`, langkah `findByEmail` itu panggilan Feign ke `/internal/users/by-email` lewat
Eureka. Alur logisnya sama persis.

**Q: Kenapa cek pemilik dilakukan sebelum lookup user, bukan sesudah?**
Dua alasan. Pertama, hemat — tidak ada gunanya mencari user untuk request yang pasti ditolak.
Kedua, dan ini yang lebih penting: kalau urutannya terbalik, siapa pun dengan token sah bisa
memakai endpoint share pada note orang lain untuk menguji apakah suatu email terdaftar — 404
berarti tidak, 403 berarti ya. Urutan ini dikunci oleh test
`shareNote_byNonOwner_isForbiddenBeforeAnyUserLookup`.

**Q: Apa yang terjadi kalau email tujuan tidak terdaftar?**
`ProfileService` melempar `ProfileNotFoundException`. `LocalUserClient` menangkapnya dan
mengembalikan `Optional.empty()` — karena kontrak `UserClient` bilang "tidak ketemu" itu
jawaban, bukan error. `NoteServiceImpl` yang memutuskan pesannya: 404 "No NoteNest user
registered with email: ...".

Pemisahan itu disengaja. Yang tahu konteksnya adalah pemanggil: `findById` yang gagal berarti
"pemilik note sudah tidak ada", `findByEmail` yang gagal berarti "email tujuan tidak terdaftar".
Dua pesan berbeda dari satu jenis kegagalan.

**Q: Di `dev` ada status 502 untuk share. Ke mana perginya?**
Hilang, dan itu benar. `502` artinya "user-service tidak terjangkau" — kelas kegagalan yang
lahir dari adanya jaringan di antara keduanya. Di satu proses tidak ada jaringan yang bisa
putus, jadi status itu mustahil. Saya juga menghapus test-nya, bukan memperbaikinya: menyimpan
test dengan mock untuk skenario yang tidak bisa terjadi itu menyesatkan.

Ini salah satu hal yang paling jelas menunjukkan biaya microservices. `502`, timeout Feign,
dan urutan start antar-service semuanya adalah kompleksitas yang **kita ciptakan sendiri**
dengan memisahkan prosesnya.

**Q: Kenapa email disimpan di `note_shares`? Bukankah itu duplikasi data?**
Ya, disengaja — pola snapshot yang sama dengan harga di `order_items` ShopNest. Tanpa snapshot,
menampilkan "dibagikan ke siapa" butuh satu lookup per penerima. Harganya: kalau suatu hari
email bisa diubah, snapshot itu basi. Saat ini tidak ada fitur ubah email, jadi trade-off-nya
murah.

**Q: `shared-with-me` melakukan lookup untuk tiap note. Itu N+1, kan?**
Sebagian. Hasilnya di-cache per request dengan `computeIfAbsent`, jadi pemilik yang sama hanya
dicari sekali — lima note dari satu orang = satu lookup. Di `dev` ini penting karena tiap
lookup adalah round-trip HTTP; di sini "cuma" query database, tapi cache-nya tetap saya
pertahankan karena alasannya tidak berubah.

**Q: Ada kelemahan di `shared-with-me`?**
Ada, dan saya menemukannya saat menulis dokumentasi. Kalau satu pemilik tidak punya profil —
misalnya ia share note tapi tidak pernah membuka halaman profil — lookup-nya gagal dan
**seluruh daftar** ikut gagal, bukan cuma satu item. Perbaikannya sederhana: kembalikan field
pemilik `null` untuk item itu. Sudah dicatat di ROADMAP, dan berlaku di kedua branch.

---

## C. Security & otorisasi

**Q: Siapa yang boleh membaca sebuah note?**
Pemiliknya, atau user yang punya baris di `note_shares` untuk note itu. Response membawa flag
`owned` supaya frontend tahu kapan menyembunyikan tombol edit dan hapus. Ubah, hapus, share,
cabut share, dan lihat daftar share: hanya pemilik.

**Q: Kenapa otorisasi di service, bukan di filter?**
Karena semua aturan NoteNest berbasis kepemilikan, dan menjawab "apakah B boleh membaca note X"
butuh membaca `notes` dan `note_shares`. Filter berjalan sebelum ada entity yang dibaca.
NoteNest bahkan tidak punya satu pun aturan berbasis role — `ADMIN` ada di enum tapi tidak
dipakai endpoint mana pun.

Di `dev` jawabannya lebih kuat lagi: gateway secara fisik tidak punya akses ke database
note-service, dan tidak boleh punya.

**Q: Kenapa 403 untuk note orang lain, bukan 404? Bukankah 403 membocorkan bahwa note itu ada?**
Pertanyaan bagus, dan saya mempertimbangkannya. 404 memang lebih rapat. Saya memilih 403 karena
id note adalah UUID acak — tidak bisa ditebak atau di-enumerasi — jadi kebocorannya praktis
nol, sementara 403 lebih jujur untuk debugging dan untuk frontend. Kalau id-nya berurutan,
jawaban saya akan berbeda.

**Q: Header `X-User-*` bisa dipalsukan client?**
Tidak. `JwtAuthFilter` membungkus request dengan `HttpServletRequestWrapper` yang membuang
keempat header itu dari kiriman luar, lalu memasang versi yang diturunkan dari klaim token.
Di path publik header-nya dibuang tanpa diganti.

Ada test khusus untuk ini — `clientSuppliedIdentityHeadersAreIgnored` — karena ini justru yang
paling mudah rusak diam-diam. Di `dev`, perlindungannya ada di gateway dan asumsinya "gateway
satu-satunya pintu", yang dijaga dengan tidak mem-publish port service domain di Compose.

**Q: Beda 401 dan 403 di project ini?**
401 = identitas bermasalah (token tidak ada, salah, kedaluwarsa, atau login salah) — frontend
harus logout. 403 = identitas sah tapi tidak berhak (bukan pemilik) — frontend **tidak boleh**
logout. Ada juga 401 kalau header `X-User-*` hilang, yang artinya ada yang salah di filter;
tanpa handler khusus, itu akan muncul sebagai 500.

**Q: Ke mana perginya `/internal/**`?**
Dihapus. Endpoint itu ada supaya Feign punya pintu masuk yang tidak melewati gateway, dan di
`dev` ia ditolak `403` dari luar sebelum token diperiksa — token sah pun tidak cukup, karena
endpoint itu tidak punya konsep pemilik. Tanpa panggilan HTTP antar-domain, pintu itu tidak
punya pemakai.

Saya menyisakan satu aturan `denyAll` untuk `/internal/**` di `SecurityConfig` sebagai jaring
pengaman kalau suatu saat ada controller dengan path itu ikut masuk lagi.

---

## D. Desain data

**Q: Kenapa profil tidak dibuat saat register?**
Ini warisan desain microservices. Kalau auth-service memanggil user-service saat register dan
panggilan itu gagal, user sudah tersimpan tapi profilnya tidak — transaksi lintas service yang
butuh Saga untuk dibereskan. Solusinya: `userId`, `email`, dan `name` ikut sebagai klaim token,
dan profil dibuat saat `/api/users/me` pertama kali dipanggil.

Di monolith sebenarnya satu `@Transactional` sudah cukup dan pola ini tidak lagi perlu. Saya
mempertahankannya supaya kedua branch berperilaku identik — frontend yang sama harus jalan di
keduanya. Kalau branch ini jadi satu-satunya, ini hal pertama yang saya sederhanakan.

**Q: Apa konsekuensi buruknya?**
User yang baru register tapi belum pernah membuka profil tidak bisa ditemukan untuk di-share,
karena baris profilnya belum ada. Saya menutupnya di sisi frontend: panggil `/api/users/me`
tepat setelah login.

**Q: Kenapa tag pakai `@ElementCollection`, bukan entity?**
Tag di sini cuma string milik satu note — tidak punya id, tidak diubah sendiri, tidak punya
atribut lain. Entity terpisah hanya menambah id dan repository yang tidak dipakai. Blueprint
awal menyebut `NoteTag` sebagai entity; saya mengubahnya saat menulis kode. Tag juga disimpan
lowercase supaya "Java" dan "java" tidak jadi dua filter berbeda.

**Q: Query pencarian note ditulis tangan. Kenapa, dan apa risikonya?**
Satu query melayani tiga kombinasi — tanpa filter, per tag, dan cari teks di judul/isi — dengan
parameter `null` berarti filter mati. Derived query untuk itu akan jadi tiga method dengan nama
sangat panjang. Risikonya: karena join ke tag, satu note bisa muncul berkali-kali, dan hitungan
halaman bisa salah. Makanya ada `distinct` dan `countQuery` eksplisit — dan karena test bermock
tidak pernah menjalankan query, saya menambah `@DataJpaTest` dengan H2 khusus untuk repository
ini.

**Q: Kenapa tidak ada foreign key dari `notes.owner_id` ke `users.id`? Sekarang kan satu schema.**
Justru itu yang paling ingin saya jaga. Di `dev` FK itu mustahil karena tabelnya di schema
berbeda milik service berbeda. Di sini secara teknis sudah bisa — dan saya sengaja tidak
membuatnya.

Menambahkannya akan mengikat kedua domain di level database, dan menghapus satu-satunya hal
yang membuat branch ini masih bisa dipecah lagi tanpa migrasi data. Relasi antar domain tetap
dipegang lewat nilai UUID dan interface, sama seperti sebelumnya.

**Q: Share ulang ke orang yang sama?**
Mengembalikan share yang sudah ada, bukan error. Dari sisi pemakai hasilnya sama — orang itu
punya akses. Duplikasi tetap dijaga di database dengan unique constraint
`(note_id, shared_with_user_id)`, jadi dua request bersamaan pun tidak bisa menghasilkan dua
baris.

---

## E. Testing

**Q: Bagaimana kamu menguji batas note → user tanpa menjalankan sisi user?**
`UserClient` adalah interface, jadi di-mock seperti repository. Untuk "tidak ketemu" saya pakai
`thenReturn(Optional.empty())`. Yang diverifikasi: hasilnya jadi `UserNotFoundException` dengan
email di pesannya, dan `save()` tidak pernah dipanggil.

Cara ini sama persis dengan di `dev` — di sana yang di-mock menggantikan HTTP, di sini
menggantikan panggilan method. Itu bukti bahwa batasnya memang di interface, bukan di jaringan.

**Q: Berapa test dan apa yang tidak diuji?**
36 test: 4 auth, 6 user, 13 note service, 5 repository, 8 end-to-end. Yang tidak diuji:
controller secara terpisah dengan MockMvc — sudah tercakup test end-to-end yang memanggilnya
lewat HTTP.

**Q: Kenapa `@SpringBootTest` ada di sini tapi di-skip di `dev`?**
Di `dev` alasan melewatkannya kuat: ia butuh PostgreSQL dan Eureka hidup, jadi gampang gagal
karena lingkungan, bukan karena kode. Di sini tidak ada Eureka dan databasenya cukup H2.

Dan di branch inilah ia paling dibutuhkan, karena merakit tiga aplikasi jadi satu menciptakan
kelas kesalahan yang unit test tidak bisa lihat — bean ganda, rantai security yang putus,
filter yang tidak pernah terpasang. Dua bug yang saya ceritakan di bagian A2 tertangkap justru
oleh test ini.

---

## F. Pertanyaan lanjutan yang mungkin muncul

**Q: Bagaimana mengembalikan branch ini jadi microservices?**
Tiga langkah, dan urutannya penting. Pertama, pecah `backend/src` kembali jadi modul per domain
— bisa karena tidak ada import lintas domain. Kedua, ganti isi `UserClient` dari
`LocalUserClient` jadi `@FeignClient`, dan hidupkan lagi `InternalUserController` di sisi user.
Ketiga, kembalikan gateway, Eureka, dan `JwtAuthFilter` versi reaktif.

Logika bisnisnya tidak tersentuh sama sekali. Itu hasil dari menjaga batasnya, bukan kebetulan.
Praktisnya saya tidak akan melakukan ini — saya akan `git checkout dev`, karena bentuk itu
memang masih ada di sana.

**Q: Bagaimana menambah permission EDIT?**
Enum sudah disiapkan (bukan boolean) untuk itu. Tambah `EDIT`, tambah field di
`ShareNoteRequest`, ubah `updateNote` dari "hanya pemilik" jadi "pemilik atau share EDIT". Satu
jebakan: Hibernate membuat check constraint untuk enum dan `ddl-auto=update` tidak
memperbaruinya, jadi constraint itu harus diubah manual — alasan bagus untuk pindah ke Flyway.

**Q: Bagaimana membuat share jadi realtime?**
Server-Sent Events lebih sederhana dari WebSocket karena arahnya satu: server memberi tahu "ada
note baru dibagikan ke kamu". Di branch ini relatif mudah — satu proses yang memegang semua
koneksi. Di `dev` ada dua tantangan tambahan: gateway harus meloloskan koneksi panjang lewat
filter JWT, dan kalau note-service punya beberapa instance, notifikasinya butuh broker supaya
sampai ke instance yang memegang koneksi user itu.

**Q: Bagaimana kamu memilih platform deploy-nya?**
Saya bandingkan free tier-nya dengan kebutuhan nyata, bukan dengan daftar fitur. Railway
memberi $1 kredit per bulan — satu service Java sekitar $4/bulan, habis dalam seminggu. Fly.io
sudah tidak punya free tier sejak 2024. Koyeb dan Google Cloud butuh kartu kredit. Oracle Cloud
Always Free sebenarnya paling kuat — 2 OCPU/12 GB, cukup untuk menjalankan `dev` apa adanya —
tapi mensyaratkan kartu kredit fisik.

Yang tersisa: Render, 750 jam per bulan per workspace. Satu service 24/7 memakai ~730 jam, jadi
muat — dan itu yang menentukan targetnya satu service. Databasenya saya pisah ke Neon, karena
Postgres gratis Render kedaluwarsa 30 hari lalu dihapus beserta datanya.

---

## G. Behavioral

**Q: Apa yang kamu pelajari dari ShopNest yang langsung dipakai di sini?**
Otorisasi. Di ShopNest saya baru sadar belakangan bahwa identitas dari gateway diterima tapi
tidak pernah dipakai untuk mengecek kepemilikan — tujuh endpoint terdampak. Di NoteNest cek
kepemilikan dan penanganan header yang hilang sudah ada sejak commit pertama, begitu juga CORS
yang di ShopNest baru ketahuan saat frontend mulai dibuat.

**Q: Ceritakan keputusan teknis yang kamu ubah di tengah jalan.**
Bentuk deploy-nya. Saya membangun NoteNest sebagai microservices, lalu menemukan bahwa tidak
ada free tier yang sanggup menjalankannya. Reaksi pertama saya adalah mencari platform lain —
saya bandingkan enam, dan tidak ada yang cocok tanpa bayar atau tanpa kartu kredit.

Saat itu saya bisa memilih: memaksakan demo yang tidur setiap 15 menit, atau mengubah bentuk
yang dideploy. Saya pilih yang kedua, tapi dengan syarat: arsitektur aslinya tidak boleh hilang,
dan logika bisnisnya tidak boleh diduplikasi. Hasilnya dua branch — `dev` yang microservices,
`deploy/monolith` yang dideploy — dan satu dokumen yang menjelaskan apa yang dikorbankan.

Yang saya pelajari: kendala deployment adalah masukan desain, bukan sesuatu yang diurus
belakangan.

**Q: Apa yang akan kamu tingkatkan?**
Verifikasi Docker build di branch monolith, perbaikan `shared-with-me` agar tidak gagal total,
lalu menyambungkan frontend ke API yang sudah dideploy. Setelah itu: Flyway, dan refresh token.
