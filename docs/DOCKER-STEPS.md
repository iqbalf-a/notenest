# Panduan Docker NoteNest — Step by Step

Runbook dari Docker Desktop mati sampai seluruh sistem jalan dan terverifikasi. Konsepnya ada di [`DOCKER-NOTES.md`](DOCKER-NOTES.md).

**Prasyarat:** Docker Desktop terpasang (Windows butuh WSL2), status "Engine running".

---

## BAGIAN 1 — File yang terlibat

| File | Isi |
|---|---|
| `docker-compose.yml` (root) | 7 container, 1 network, 1 volume |
| `backend/<modul>/Dockerfile` | Multi-stage build, 6 buah |
| `backend/<service>/src/main/resources/application-docker.properties` | Konfigurasi saat profil `docker` aktif |
| `backend/config-repo/application.properties` | Di-mount ke config-server, bukan di-copy |

Semua `build:` di Compose menunjuk ke `./backend/<modul>`, jadi perintah `docker compose` dijalankan dari **root repo**.

---

## BAGIAN 2 — Membaca `docker-compose.yml`

### postgres
```yaml
image: postgres:16-alpine
environment:
  POSTGRES_USER: notenest
  POSTGRES_PASSWORD: notenest123
  POSTGRES_DB: notenest_db
volumes:
  - postgres_data:/var/lib/postgresql/data
ports:
  - "5432:5432"
healthcheck:
  test: ["CMD-SHELL", "pg_isready -U notenest -d notenest_db"]
```
- Satu database `notenest_db`. Schema `auth_schema`, `user_schema`, `note_schema` **tidak** dibuat di sini — Hibernate membuatnya sendiri (`hbm2ddl.create_namespaces=true`).
- `5432` dibuka supaya bisa menjalankan satu service dari IDE sambil DB tetap di container.
- Kredensial ini hanya untuk lokal.

### config-server
```yaml
build: ./backend/config-server
environment:
  SPRING_PROFILES_ACTIVE: native,docker
volumes:
  - ./backend/config-repo:/config-repo:ro
```
`config-repo` di-mount read-only: ubah config tidak perlu rebuild image. Saat ini belum ada service yang membaca dari config-server.

### auth-service (pola yang sama untuk user dan note)
```yaml
build: ./backend/auth-service
environment:
  SPRING_PROFILES_ACTIVE: docker
  DB_URL: jdbc:postgresql://postgres:5432/notenest_db
  DB_USER: notenest
  DB_PASSWORD: notenest123
  EUREKA_URL: http://eureka-server:8761/eureka/
  JWT_SECRET: 5BA782B9...                 # hanya auth dan gateway
depends_on:
  postgres:      { condition: service_healthy }
  eureka-server: { condition: service_started }
```
- Host `postgres` dan `eureka-server` = nama service di Compose.
- `JWT_SECRET` harus **identik** di auth-service dan api-gateway. Kalau beda: login sukses, tapi semua request berikutnya `401 Invalid or expired token`.
- user-service dan note-service tidak butuh `JWT_SECRET` — mereka tidak pernah melihat token.

### api-gateway
```yaml
build: ./backend/api-gateway
ports:
  - "8080:8080"
environment:
  EUREKA_URL: http://eureka-server:8761/eureka/
  JWT_SECRET: 5BA782B9...
  CORS_ALLOWED_ORIGINS: http://localhost:5173
```
`CORS_ALLOWED_ORIGINS` diisi origin dev server frontend. Pisahkan dengan koma kalau lebih dari satu. Tanpa port ini di daftar, semua request dari browser gagal — Postman tetap lancar, jadi gejalanya menyesatkan.

---

## BAGIAN 3 — Jalankan

**Step 1 — Masuk ke root repo**
```bash
cd C:\Users\<kamu>\Documents\github-repos\notenest
```

**Step 2 — Build + jalankan** (pertama kali lama: 6 image, download base image JDK/JRE)
```bash
docker compose up -d --build
```

**Step 3 — Cek status**
```bash
docker compose ps
```
7 container `Up`, postgres `healthy`.

**Step 4 — Tunggu registrasi Eureka**
Buka http://localhost:8761. Tunggu sampai `API-GATEWAY`, `AUTH-SERVICE`, `USER-SERVICE`, `NOTE-SERVICE` terdaftar (±30–60 detik). Request ke gateway sebelum itu bisa dijawab `503`.

**Step 5 — Buka dokumentasi**
http://localhost:8080/docs.html — tiga spec di sidebar Scalar.

---

## BAGIAN 4 — Verifikasi end-to-end

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

# 5. A share ke B (Feign) → 201
curl -s -X POST localhost:8080/api/notes/$NOTE/share -H "Authorization: Bearer $A" \
  -H "Content-Type: application/json" -d '{"targetEmail":"budi@example.com"}'

# 6. B sekarang bisa membaca → 200, owned=false; dan muncul di shared-with-me dengan ownerEmail
curl -s localhost:8080/api/notes/$NOTE -H "Authorization: Bearer $B"
curl -s localhost:8080/api/notes/shared-with-me -H "Authorization: Bearer $B"

# 7. Jalur negatif
curl -s localhost:8080/api/notes                                   # 401 tanpa token
curl -s localhost:8080/internal/users/by-email?email=ani@example.com -H "Authorization: Bearer $A"  # 403
curl -s -X POST localhost:8080/api/notes/$NOTE/share -H "Authorization: Bearer $A" \
  -H "Content-Type: application/json" -d '{"targetEmail":"hantu@example.com"}'                      # 404

# 8. A mencabut akses B → B kembali 403
```

Kalau langkah 1–8 sesuai, tandai Phase 2 selesai di [`ROADMAP.md`](ROADMAP.md).

---

## Perintah harian

```bash
docker compose up -d                  # nyalakan tanpa rebuild
docker compose up -d --build          # setelah ubah kode
docker compose up -d --build note-service   # rebuild satu service saja
docker compose ps
docker compose logs -f note-service
docker compose restart user-service
docker compose stop                   # matikan, container + data tetap
docker compose down                   # hapus container, volume TETAP
docker compose down -v                # hapus container + volume (data HILANG)
```

---

## Troubleshooting

| Gejala | Penyebab | Solusi |
|---|---|---|
| `503` dari gateway beberapa detik setelah start | Cache Eureka gateway belum segar | Tunggu ±30 detik |
| Login sukses, request lain `401` | `JWT_SECRET` auth ≠ gateway | Samakan di Compose, `up -d` ulang |
| Share → `404` padahal email ada | Target belum pernah memanggil `/api/users/me`, profilnya belum dibuat | Panggil `/api/users/me` dengan token target |
| Share → `502` | user-service mati atau belum terdaftar di Eureka | `docker compose logs user-service` |
| Browser: CORS error, Postman lancar | Origin frontend tidak ada di `CORS_ALLOWED_ORIGINS` | Tambahkan, restart gateway |
| Port 5432/8080/8761 dipakai | Postgres lokal atau service `mvnw` masih jalan | Matikan, atau ubah sisi host di `ports:` |
| Kode diubah tapi tidak berubah | Image lama | `docker compose up -d --build` |
| `docker-credential-desktop not found` | PATH terminal belum diperbarui setelah install Docker | Tutup-buka terminal/VS Code |
