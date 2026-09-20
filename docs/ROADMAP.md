# NoteNest Roadmap — branch `deploy/monolith`

Notes-with-sharing REST API — sister project of ShopNest, built so that both the backend **and**
the frontend can realistically be finished.

> **This branch ships the backend as a single Spring Boot application.** The microservices
> form lives on **`dev`** and is still the project's primary design. Everything below is
> written from this branch's point of view; see [`DEPLOY-MONOLITH.md`](DEPLOY-MONOLITH.md)
> for why it exists.

**Legend:** `[x]` done · `[ ]` to do · ⏭️ **SKIP** = deliberately postponed

---

## Phase 0 — Foundation ✅ DONE (on `dev`)

Built in one pass from the blueprint in [`HANDOVER.md`](HANDOVER.md), reusing every lesson
ShopNest learned the hard way.

- [x] Eureka Server (8761)
- [x] Config Server (8888) + `config-repo`
- [x] Auth Service (8081) — register/login/validate, JWT with `userId` + `name` claims, BCrypt
- [x] API Gateway (8080) — routes, `JwtAuthFilter`, `X-User-*` propagation + anti-spoofing
- [x] User Service (8082) — lazily created profile, user search, internal endpoints
- [x] Note Service (8083) — notes, tags (`@ElementCollection`), paginated search, sharing via **OpenFeign**
- [x] Schema-per-service (`auth_schema`, `user_schema`, `note_schema`), auto-created
- [x] Secrets hygiene — real `application.properties` gitignored, `.example` committed

**Stack on `dev`:** Java 21 · Spring Boot 3.4.6 · Spring Cloud 2024.0.1 · PostgreSQL 16

---

## Phase 1 — Security & Authorization ✅ DONE (from day one)

ShopNest added authorization in its Phase 6, after discovering identity headers were ignored.
NoteNest started with it.

- [x] Public paths: `/api/auth/register`, `/api/auth/login`, docs
- [x] Ownership enforced in `NoteServiceImpl` — update/delete/share/revoke/list-shares
- [x] Read access = owner **or** share recipient
- [x] `MissingRequestHeaderException` → `401`, not `500`
- [x] CORS with origins from `notenest.cors.allowed-origins`
- [x] `/internal/**` blocked at the gateway with `403` *(on `dev`; the endpoints are gone here)*

---

## Phase 2 — Docker ✅ DONE

- [x] Multi-stage `Dockerfile`
- [x] `docker-compose.yml` — postgres + api
- [x] Postgres `healthcheck` + `service_healthy`
- [x] `spring-boot-starter-actuator`, `/actuator/health` as the health check target
- [x] **Verified end-to-end** — automated as `ApiEndToEndTest`, run on every `./mvnw test`
- [x] Container memory limit mirroring the free tier (512 MB)
- [x] Docker build verified — Railway builds this same `Dockerfile` on every push
      (`docker compose up --build` has still never been run locally; Docker Desktop was down)

---

## Phase 3 — Testing ✅ DONE

- [x] Service layer with Mockito — auth (4), user (6), note (13)
- [x] `@DataJpaTest` + H2 for the hand-written JPQL in `NoteRepository` (5)
- [x] `@SpringBootTest` end-to-end (8) — security chain, token → `X-User-*`, anti-spoofing, share
- [x] Feign error paths *(on `dev`: 404 → `UserNotFoundException`, 503 → `502`)* — reduced to
      the 404 case here; "service unreachable" cannot happen in one process
- ⏭️ **SKIP** standalone `MockMvc` controller tests — covered by the end-to-end test

**36 tests, all green.**

---

## Phase 4 — Documentation ✅ DONE

- [x] Scalar page at `/docs.html`
- [x] `docs/` — documentation, build order, testing notes, interview Q&A, Docker notes, schema snapshot
- [x] Frontend handover package — `FRONTEND-README.md`, service atlas, brief, design handover
- [x] Monorepo layout — `backend/`, `frontend/`, `docs/`
- [x] Monolith-specific docs — [`DEPLOY-MONOLITH.md`](DEPLOY-MONOLITH.md), and every backend
      doc rewritten for this branch
- ⏭️ **SKIP** `@Operation` / `@Schema` annotations — spec is generated from controllers already
- ⏭️ **SKIP** Postman collection — Scalar can send requests itself

---

## Phase 5 — Deployment ✅ LIVE on Railway *(this branch's reason for existing)*

Live at **https://notenest-production-8997.up.railway.app** — API docs at `/docs.html`.

- [x] Survey free tiers — Render, Railway, Fly.io, Koyeb, Northflank, Oracle Cloud
- [x] Merge the backend into one deployable application
- [x] `render.yaml` blueprint
- [x] Deploy to Railway: Postgres plugin + Docker build from `backend/Dockerfile`
      (service Root Directory must be `backend`, or Railway falls back to Railpack
      and the build fails in ~15 seconds)
- [x] Verify the Docker build — Railway builds the same `backend/Dockerfile`, so the
      image is proven even though `docker compose up --build` was never run locally
- [x] Verify end to end against real PostgreSQL — 20/20 checks pass. This is what
      caught the `lower(bytea)` bug that all 36 tests missed.
- [ ] Remove three stray variables copied from the Postgres service into `notenest`:
      `DATABASE_URL` (resolves to a broken self-referencing URL), `SSL_CERT_DAYS`,
      `RAILWAY_DEPLOYMENT_DRAINING_SECONDS`
- [ ] Point `CORS_ALLOWED_ORIGINS` at the deployed frontend
- [ ] Before the trial runs out: move to Render + Neon for a permanent home
      (`render.yaml` is ready; only the way `DB_URL` is filled in differs)

**Railway is a trial, not a free tier.** $5 one-off, ~3–4 weeks for one Java service
plus Postgres. Render's 750 h/month covers one always-on service permanently, with
Neon for the database — Render's own free Postgres expires after 30 days.

---

## Phase 6 — Frontend ⏳ NEXT

- [x] Scaffold `frontend/` (React + TypeScript + Vite)
- [x] Phase 1 against MSW dummy data
- [ ] Phase 2 against the real backend
- [ ] Deploy the frontend and connect it to the deployed API

---

## Known gaps worth fixing

| Gap | Impact | Fix |
|---|---|---|
| `shared-with-me` fails entirely if one owner's profile is missing | A user who shared notes but never opened `/api/users/me` has no profile → `UserNotFoundException` → whole list returns `404` | Fall back to `null` owner fields instead of throwing |
| Share target must have opened the app once | Profiles are created lazily, so a freshly registered user who never called `/api/users/me` cannot be found by email | Frontend calls `/api/users/me` right after login (cheap, and the brief requires it) |
| Profile email/name can drift from auth | Copied from token claims at first access; auth has no "change email" today, so harmless for now | Refresh on every `/me` call |
| The `lower(bytea)` query bug is still present on `dev` | `GET /api/notes` returns 500 against real PostgreSQL there too; its ROADMAP still lists end-to-end verification as open | Port the `cast(:q as string)` fix from this branch |
| `ddl-auto=update` against a deployed database | Fine for a demo, risky for anything else | Flyway |

## Known deliberate simplifications

| Simplification | Proper solution |
|---|---|
| Backend assembled into one application | Pay for compute, or run `dev` as-is on a VPS |
| Single schema instead of schema-per-service | `@Table(schema = ...)` per entity |
| Sharing is not realtime | WebSocket / Server-Sent Events |
| Only `READ` permission | `READ` / `EDIT` / `ADMIN` — enum already in place |
| Single shared PostgreSQL instance | Physically separate database per domain |
| No circuit breaker / tracing / cache | Resilience4j, Zipkin, Redis |
| No refresh token | Refresh token + rotation |

## What this branch gave up

Worth stating plainly, because the point of the project is the architecture:

| Lost | Why it mattered on `dev` |
|---|---|
| Service discovery | Services found each other without hardcoded addresses |
| Independent deployment | One domain could ship without rebuilding the others |
| Independent scaling | `note-service` could scale while `auth-service` stayed at one instance |
| Fault isolation | `user-service` dying meant a `502` on share, not the whole API down |
| Schema-per-service | Each service physically could not read another's tables |

What survived: the domain boundaries themselves. `noteservice` still cannot import from
`userservice` — only from the `UserClient` interface. That is the one thing that makes the
split reversible.
