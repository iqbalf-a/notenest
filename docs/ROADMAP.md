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
- [ ] **Verify the Docker build itself on this branch** — the Maven build and the full Spring
      context are green, but `docker compose up --build` has not been run since the restructure

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

## Phase 5 — Deployment ⏳ IN PROGRESS *(this branch's reason for existing)*

- [x] Survey free tiers — Render, Railway, Fly.io, Koyeb, Northflank, Oracle Cloud
- [x] Merge the backend into one deployable application
- [x] `render.yaml` blueprint
- [ ] Create the Neon database and fill in `DB_URL` / `DB_USER` / `DB_PASSWORD`
- [ ] First deploy to Render, confirm `/actuator/health` responds
- [ ] Keep-alive ping every ~14 min (cron-job.org / UptimeRobot) — 730 h/month fits the 750 h quota
- [ ] Point `CORS_ALLOWED_ORIGINS` at the deployed frontend

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
| Docker build unverified on this branch | Might fail on something the Maven build doesn't catch | Run `docker compose up --build` once |
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
