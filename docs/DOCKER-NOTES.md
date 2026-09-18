# Catatan Docker — NoteNest (branch `deploy/monolith`)

Konsep Docker yang dipakai branch ini. Untuk langkah menjalankan dan arti tiap baris
konfigurasi, lihat [`DOCKER-STEPS.md`](DOCKER-STEPS.md).

> Di branch `dev`, Compose menjalankan **7 container**. Di sini **2**.

---

## Kenapa Docker untuk project ini

- **Satu perintah.** `docker compose up` — tidak perlu memasang PostgreSQL atau JDK di laptop.
- **"Di komputerku jalan"** — image berisi Java 21 dan `.jar` yang sama di mesin mana pun.
  Frontend developer tidak perlu menyentuh Java untuk mendapatkan API yang hidup.
- **Image yang sama dipakai untuk deploy.** Render membangun `backend/Dockerfile` ini juga,
  jadi apa yang jalan di laptop adalah apa yang jalan di produksi.
- **Batas memori bisa ditiru.** `mem_limit: 512m` membuat masalah memori ketahuan di laptop,
  bukan setelah dideploy ke free tier.

---

## Istilah inti

| Istilah | Analogi | Fungsi |
|---------|---------|--------|
| **Image** | resep beku | Template read-only: Java runtime + `.jar` |
| **Container** | masakan jadi | Image yang sedang berjalan. Satu image → banyak container |
| **Dockerfile** | cara memasak | Langkah membuat image |
| **Volume** | lemari | Data permanen di luar container — data Postgres tidak hilang saat container dihapus |
| **Network** | ruang obrolan | Container saling panggil pakai **nama**: `postgres` |
| **docker compose** | dalang | Satu `docker-compose.yml` menjalankan semua container, network, dan volume |

Di dalam network Compose, alamat database adalah `jdbc:postgresql://postgres:5432/...` —
nama service, bukan `localhost`. `localhost` di dalam container berarti container itu sendiri.

Istilah **bind mount** tidak lagi terpakai di branch ini. Di `dev` ia dibutuhkan untuk
menyuntikkan `config-repo` ke config-server; tanpa Config Server, tidak ada yang perlu di-mount
— dan itu kebetulan yang membuat branch ini bisa jalan di free tier, yang memang tidak
menyediakan persistent disk.

---

## Gambaran NoteNest di Compose

```
docker-compose.yml (root repo)
  ├─ postgres   image resmi + volume notenest-postgres-data   :5432 dibuka*
  └─ api        build ./backend                                :8080 dibuka
                (tunggu postgres healthy, dibatasi 512 MB / 0.5 CPU)

  Keduanya di network notenest.
  * 5432 dibuka supaya DB bisa dilihat dari DBeaver/psql saat mengembangkan.
```

Bandingkan dengan `dev`: `postgres` + `eureka-server` + `config-server` + `auth-service` +
`user-service` + `note-service` + `api-gateway`, dengan `config-repo` di-mount ke config-server.

Di `dev`, service domain sengaja tidak punya `ports:` — itulah yang membuat `/internal/**`
aman walau hanya dijaga gateway. Di sini persoalan itu hilang bersama endpoint-nya.

---

## Dockerfile multi-stage

```
STAGE 1 (builder): eclipse-temurin:21-jdk → copy pom → go-offline → copy src → package → .jar
STAGE 2 (runtime): eclipse-temurin:21-jre → copy .jar dari stage 1 → java -jar
```

- **Kenapa dua stage:** JDK dan Maven hanya dibutuhkan untuk build. Image akhir cukup JRE +
  `.jar`, jauh lebih kecil.
- **Trik cache:** `pom.xml` di-copy dan dependency diunduh **sebelum** `src/`. Kalau hanya
  kode yang berubah, layer download dipakai ulang.
- **`-DskipTests`:** test dijalankan terpisah dengan `./mvnw test`; build image tidak perlu
  mengulanginya.
- **Satu Dockerfile**, bukan enam. Di `dev` keenam modul memakai Dockerfile yang isinya sama
  persis, hanya `EXPOSE`-nya berbeda — pengulangan yang hilang dengan sendirinya di sini.

### Tuning JVM di stage runtime

```dockerfile
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k"
```

| Flag | Kenapa |
|---|---|
| `MaxRAMPercentage=70` | Tanpa ini JVM menghitung heap dari RAM **host**, bukan batas container. Di mesin 16 GB, satu container 512 MB akan mengira boleh pakai 4 GB dan langsung OOM |
| `UseSerialGC` | GC paralel menyalakan thread per core. Di 0,1 CPU itu justru memperlambat |
| `Xss512k` | Stack per thread dari 1 MB jadi 512 KB. Tomcat punya puluhan thread, jadi lumayan |

Tiga flag ini tidak ada di `dev` — di sana tiap service dapat memori sebanyak yang laptop
punya. Mereka ada di sini karena targetnya instance 512 MB.

---

## Konfigurasi: environment variable, bukan profil

Di `dev` tiap service punya `application-docker.properties`, aktif karena Compose men-set
`SPRING_PROFILES_ACTIVE=docker`. Profil itu perlu karena `application.properties` asli
di-gitignore, jadi di clone yang bersih file itu tidak ada.

Di sini tidak ada profil sama sekali. Satu `application.properties` di-commit, dan setiap
nilai yang berbeda antar-lingkungan ditulis sebagai `${ENV_VAR}`:

```properties
spring.datasource.url=${DB_URL}
jwt.secret=${JWT_SECRET}
server.port=${PORT:8080}
```

Konsekuensinya menyenangkan: **file yang sama dipakai di laptop dan di Render**. Yang berbeda
cuma dari mana nilainya datang — `.env` lewat Compose, atau dashboard Render.

`${PORT:8080}` ada karena Render menyuntikkan nomor port sendiri; `8080` dipakai kalau
variabel itu tidak ada.

---

## Urutan start

| Container | Menunggu | Kondisi |
|---|---|---|
| `api` | `postgres` | `service_healthy` — `pg_isready` lulus |

Cuma satu baris. Di `dev` tabel ini punya tiga baris dan satu catatan khusus tentang kenapa
note-service sengaja **tidak** menunggu user-service — masalah yang lahir dari adanya urutan
start antar-service, dan hilang ketika service-nya tinggal satu.

Yang tersisa: `api` tidak boleh start sebelum Postgres siap menerima koneksi, karena Hibernate
membuat tabel saat startup.
