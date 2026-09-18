# Panduan Docker NoteNest — Step by Step (branch `deploy/monolith`)

Runbook dari Docker Desktop mati sampai seluruh sistem jalan dan terverifikasi.
Konsepnya ada di [`DOCKER-NOTES.md`](DOCKER-NOTES.md).

**Prasyarat:** Docker Desktop terpasang (Windows butuh WSL2), status "Engine running".

---

## BAGIAN 1 — File yang terlibat

| File | Isi |
|---|---|
| `docker-compose.yml` (root) | 2 container, 1 network, 1 volume |
| `.env` (root, gitignored) | Nilai yang dibaca Compose — salin dari `.env.example` |
| `backend/Dockerfile` | Multi-stage build, satu-satunya |
| `backend/src/main/resources/application.properties` | Di-commit; semua nilai sensitif lewat `${ENV}` |

`build:` di Compose menunjuk ke `./backend`, jadi perintah `docker compose` dijalankan dari
**root repo**.

---

## BAGIAN 2 — Siapkan `.env`

```bash
cp .env.example .env
```

Lalu isi:

```properties
DB_NAME=notenest_db
DB_USER=notenest
DB_PASSWORD=<pilih sendiri>
JWT_SECRET=<hasil: openssl rand -base64 48>
CORS_ALLOWED_ORIGINS=http://localhost:5173
```

Dua hal yang sering salah:

- **`JWT_SECRET` harus Base64 yang sah**, minimal 32 byte setelah didecode. `JwtAuthFilter`
  men-decode-nya sebagai Base64 — teks biasa akan membuat aplikasi gagal memverifikasi token.
- **`CORS_ALLOWED_ORIGINS` harus berisi origin dev server frontend.** Tanpa itu, semua request
  dari browser gagal sementara Postman tetap lancar — gejala yang menyesatkan.

`.env` ada di `.gitignore`. Jangan pernah meng-commit-nya.

---

## BAGIAN 3 — Membaca `docker-compose.yml`

### postgres
```yaml
image: postgres:16-alpine
environment:
  POSTGRES_USER: ${DB_USER}
  POSTGRES_PASSWORD: ${DB_PASSWORD}
  POSTGRES_DB: ${DB_NAME}
volumes:
  - postgres_data:/var/lib/postgresql/data
ports:
  - "5432:5432"
healthcheck:
  test: ["CMD-SHELL", "pg_isready -U ${DB_USER} -d ${DB_NAME}"]
```
- Satu database, **satu schema** (default). Di `dev` ada tiga schema yang dibuat Hibernate
  sendiri lewat `hbm2ddl.create_namespaces=true`; satu aplikasi tidak bisa punya tiga
  `default_schema`.
- `5432` dibuka supaya DB bisa dilihat dari DBeaver/psql. **Tutup baris ini** kalau Compose
  dipakai di server yang terekspos ke internet.

### api
```yaml
build: ./backend
ports:
  - "8080:8080"
environment:
  DB_URL: jdbc:postgresql://postgres:5432/${DB_NAME}
  DB_USER: ${DB_USER}
  DB_PASSWORD: ${DB_PASSWORD}
  JWT_SECRET: ${JWT_SECRET}
  CORS_ALLOWED_ORIGINS: ${CORS_ALLOWED_ORIGINS}
mem_limit: 512m
cpus: 0.5
depends_on:
  postgres: { condition: service_healthy }
```
- Host `postgres` = nama service di Compose, bukan `localhost`.
- **`mem_limit: 512m` bukan hiasan.** Itu batas instance free tier Render. Kalau aplikasi
  OOM di sini, ia juga akan OOM di sana — lebih murah ketahuan sekarang.
- Tidak ada `SPRING_PROFILES_ACTIVE`: tidak ada profil di branch ini.
- Tidak ada `EUREKA_URL`: tidak ada yang perlu ditemukan.

---

## BAGIAN 4 — Jalankan

**Step 1 — Masuk ke root repo**
```bash
cd /path/ke/notenest
```

**Step 2 — Build + jalankan** (pertama kali lama: download base image JDK/JRE + dependency Maven)
```bash
docker compose up -d --build
```

**Step 3 — Cek status**
```bash
docker compose ps
```
2 container `Up`, postgres `healthy`.

**Step 4 — Tunggu aplikasi siap**
```bash
curl -s localhost:8080/actuator/health
# {"status":"UP"}
```
Biasanya 10–20 detik. Di `dev` langkah ini butuh 30–60 detik karena harus menunggu keempat
service mendaftar ke Eureka lebih dulu, dan request sebelum itu dijawab `503`.

**Step 5 — Buka dokumentasi**
http://localhost:8080/docs.html — satu spec di Scalar (di `dev`: tiga).

---

## BAGIAN 5 — Verifikasi end-to-end

Jalankan lewat Scalar atau curl. Butuh **dua akun** untuk menguji share.

```bash
# 1. Register A dan B
curl -s -X POST localhost:8080/api/auth/register -H "Content-Type: application/json" \
  -d '{"displayName":"Ani","email":"ani@example.com","password":"rahasia123"}'
curl -s -X POST localhost:8080/api/auth/register -H "Content-Type: application/json" \
  -d '{"displayName":"Budi","email":"budi@example.com","password":"rahasia123"}'
# simpan accessToken masing-masing sebagai $A dan $B

# 2. Buat profil (WAJIB sebelum B bisa ditemukan lewat email)
curl -s localhost:8080/api/users/me -H "Authorization: Bearer $A"
curl -s localhost:8080/api/users/me -H "Authorization: Bearer $B"

# 3. A membuat note
curl -s -X POST localhost:8080/api/notes -H "Authorization: Bearer $A" -H "Content-Type: application/json" \
  -d '{"title":"Rapat","content":"Bahas roadmap","tags":["Kerja"]}'
# simpan id sebagai $NOTE

# 4. B belum boleh membaca → 403
curl -s localhost:8080/api/notes/$NOTE -H "Authorization: Bearer $B"

# 5. A share ke B → 201
curl -s -X POST localhost:8080/api/notes/$NOTE/share -H "Authorization: Bearer $A" \
  -H "Content-Type: application/json" -d '{"targetEmail":"budi@example.com"}'

# 6. B sekarang bisa membaca → 200, owned=false; dan muncul di shared-with-me dengan ownerEmail
curl -s localhost:8080/api/notes/$NOTE -H "Authorization: Bearer $B"
curl -s localhost:8080/api/notes/shared-with-me -H "Authorization: Bearer $B"

# 7. Jalur negatif
curl -s localhost:8080/api/notes                                    # 401 tanpa token
curl -s localhost:8080/api/notes -H "Authorization: Bearer ngawur"  # 401 token palsu
curl -s -X POST localhost:8080/api/notes/$NOTE/share -H "Authorization: Bearer $A" \
  -H "Content-Type: application/json" -d '{"targetEmail":"hantu@example.com"}'   # 404

# 8. Header identitas palsu diabaikan — tetap melihat note milik A sendiri
curl -s localhost:8080/api/notes -H "Authorization: Bearer $A" -H "X-User-Id: <id milik B>"

# 9. A mencabut akses B → B kembali 403
curl -s -X DELETE localhost:8080/api/notes/$NOTE/share/<userId B> -H "Authorization: Bearer $A"
```

Langkah 1–9 di atas adalah versi manual dari `ApiEndToEndTest`, yang menjalankan alur yang
sama di H2 setiap kali `./mvnw test` dipanggil. Kalau test itu hijau, langkah ini seharusnya
hijau juga — yang dibuktikan di sini adalah Docker dan PostgreSQL-nya, bukan logikanya.

Satu langkah dari `dev` yang **tidak ada** di sini: memanggil `/internal/users/by-email` dan
mengharapkan `403`. Endpoint itu memang sudah tidak ada.

---

## Perintah harian

```bash
docker compose up -d                  # nyalakan tanpa rebuild
docker compose up -d --build          # setelah ubah kode
docker compose ps
docker compose logs -f api
docker compose restart api
docker compose stop                   # matikan, container + data tetap
docker compose down                   # hapus container, volume TETAP
docker compose down -v                # hapus container + volume (data HILANG)
```

Di `dev` ada `docker compose up -d --build note-service` untuk membangun ulang satu service
saja. Di sini tidak relevan — rebuild selalu membangun seluruh aplikasi, yang juga berarti
perubahan di satu domain memaksa yang lain ikut dibangun ulang. Itu salah satu harga yang
dibayar monolith.

---

## Troubleshooting

| Gejala | Penyebab | Solusi |
|---|---|---|
| Container `api` restart terus | Salah satu env var kosong; `${DB_URL}` tidak terisi | Cek `.env` ada dan lengkap: `docker compose config` |
| `Illegal base64 character` saat request pertama | `JWT_SECRET` bukan Base64 | `openssl rand -base64 48` |
| Login sukses, request lain `401` | `JWT_SECRET` berubah setelah token dibuat | Login ulang, atau kembalikan nilai lama |
| `api` mati dengan `OutOfMemoryError` | `mem_limit: 512m` memang ketat | Naikkan sementara untuk memastikan, lalu turunkan lagi — ingat free tier tidak bisa dinaikkan |
| Share → `404` padahal email ada | Target belum pernah memanggil `/api/users/me`, profilnya belum dibuat | Panggil `/api/users/me` dengan token target |
| Browser: CORS error, Postman lancar | Origin frontend tidak ada di `CORS_ALLOWED_ORIGINS` | Tambahkan di `.env`, `docker compose up -d` ulang |
| Port 5432/8080 dipakai | Postgres lokal atau aplikasi dari IDE masih jalan | Matikan, atau ubah sisi host di `ports:` |
| Kode diubah tapi tidak berubah | Image lama | `docker compose up -d --build` |
| `created_at` NULL saat insert | `@EnableJpaAuditing` tidak terpasang | Pastikan ada di `NoteNestApplication`, bukan di kelas config terpisah |
| `docker-credential-desktop not found` | PATH terminal belum diperbarui setelah install Docker | Tutup-buka terminal/VS Code |

Dua gejala khas `dev` yang **tidak bisa terjadi** di sini: `503` beberapa detik setelah start
(cache Eureka belum segar) dan `502` saat share (user-service tidak terjangkau). Keduanya
gejala komunikasi antar-proses.
