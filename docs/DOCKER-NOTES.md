# Catatan Docker — NoteNest

Konsep Docker yang dipakai project ini. Untuk langkah menjalankan dan arti tiap baris konfigurasi, lihat [`DOCKER-STEPS.md`](DOCKER-STEPS.md).

---

## Kenapa Docker untuk project ini

- **Satu perintah, bukan enam terminal.** Tanpa Docker: nyalakan Postgres, lalu Eureka, auth, user, note, gateway — berurutan, masing-masing di terminal sendiri. Dengan Compose: `docker compose up -d`.
- **Tidak ada database yang tertidur.** ShopNest awalnya memakai Supabase free tier yang auto-pause setelah idle. NoteNest langsung memakai PostgreSQL dalam container sejak awal.
- **"Di komputerku jalan"** — image berisi Java 21 dan `.jar` yang sama di mesin mana pun. Frontend developer tidak perlu memasang JDK untuk mendapatkan API yang hidup.

---

## Istilah inti

| Istilah | Analogi | Fungsi |
|---------|---------|--------|
| **Image** | resep beku | Template read-only: Java runtime + `.jar` service |
| **Container** | masakan jadi | Image yang sedang berjalan. Satu image → banyak container |
| **Dockerfile** | cara memasak | Langkah membuat image |
| **Volume** | lemari | Data permanen di luar container — data Postgres tidak hilang saat container dihapus |
| **Bind mount** | jendela ke laptop | Folder laptop yang terlihat di dalam container — dipakai untuk `config-repo` |
| **Network** | ruang obrolan | Container saling panggil pakai **nama**: `postgres`, `eureka-server` |
| **docker compose** | dalang | Satu `docker-compose.yml` menjalankan semua container, network, dan volume |

Di dalam network Compose, alamat database adalah `jdbc:postgresql://postgres:5432/...` — nama service, bukan `localhost`. `localhost` di dalam container berarti container itu sendiri.

---

## Gambaran NoteNest di Compose

```
docker-compose.yml (root repo)
  ├─ postgres        image resmi + volume notenest-postgres-data      :5432 dibuka*
  ├─ eureka-server   build ./backend/eureka-server                     :8761 dibuka
  ├─ config-server   build ./backend/config-server + mount config-repo
  ├─ auth-service    build ./backend/auth-service   (tunggu postgres healthy)
  ├─ user-service    build ./backend/user-service   (tunggu postgres healthy)
  ├─ note-service    build ./backend/note-service   (tunggu postgres healthy)
  └─ api-gateway     build ./backend/api-gateway                       :8080 dibuka

  Semua di network notenest-network.
  * 5432 dibuka supaya service bisa dijalankan dari IDE sambil DB tetap di Docker.
```

Hanya gateway (8080) dan dashboard Eureka (8761) yang bisa dijangkau dari laptop untuk urusan API. Service domain tidak punya `ports:` — itulah yang membuat `/internal/**` aman walau hanya dijaga gateway.

---

## Dockerfile multi-stage

```
STAGE 1 (builder): eclipse-temurin:21-jdk → copy pom → go-offline → copy src → package → .jar
STAGE 2 (runtime): eclipse-temurin:21-jre → copy .jar dari stage 1 → java -jar
```

- **Kenapa dua stage:** JDK dan Maven hanya dibutuhkan untuk build. Image akhir cukup JRE + `.jar`, jauh lebih kecil.
- **Trik cache:** `pom.xml` di-copy dan dependency diunduh **sebelum** `src/`. Kalau hanya kode yang berubah, layer download dipakai ulang.
- **`-DskipTests`:** test dijalankan terpisah dengan `./mvnw test`; build image tidak perlu mengulanginya.
- Semua enam modul memakai Dockerfile yang sama, hanya `EXPOSE`-nya berbeda.

---

## Profil `docker`

Tiap service punya `application-docker.properties`, aktif karena Compose men-set `SPRING_PROFILES_ACTIVE=docker`.

Berbeda dari ShopNest, profil ini **lengkap** — bukan hanya menimpa beberapa baris. Alasannya: `application.properties` di-gitignore, jadi di clone yang bersih file itu tidak ada dan tidak bisa dijadikan basis. Nilai rahasia (`DB_PASSWORD`, `JWT_SECRET`) tetap datang dari environment variable `${...}`, jadi file ini aman di-commit.

Pengecualian: `config-server` memakai `SPRING_PROFILES_ACTIVE=native,docker` — `native` untuk membaca folder lokal, `docker` untuk menunjuk ke `/config-repo` hasil mount.

---

## Urutan start

| Service | Menunggu | Kondisi |
|---|---|---|
| auth, user, note | postgres | `service_healthy` — `pg_isready` lulus |
| auth, user, note, gateway | eureka-server | `service_started` |
| note-service | user-service | **tidak menunggu** |

note-service sengaja tidak menunggu user-service. Feign menanyakan alamat ke Eureka **saat dipakai**, bukan saat start. Kalau user-service belum siap ketika seseorang share note, jawabannya `502` — bukan crash saat boot.

Semua service Spring sudah punya actuator (`/actuator/health`), jadi `service_healthy` bisa dipakai untuk mereka juga. Belum diaktifkan karena `service_started` sudah cukup; gejalanya hanya gateway membalas `503` beberapa detik pertama sampai cache Eureka-nya segar.
