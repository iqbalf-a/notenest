# NoteNest Roadmap

Notes-with-sharing microservices REST API — sister project of ShopNest, built so that both the backend **and** the frontend can realistically be finished. Runs locally via `docker compose up`; public deployment is not currently planned.

**Legend:** `[x]` done · `[ ]` to do · ⏭️ **SKIP** = deliberately postponed

---

## Phase 0 — Foundation ✅ DONE

Built in one pass from the blueprint in [`HANDOVER.md`](HANDOVER.md), reusing every lesson ShopNest learned the hard way.

- [x] Eureka Server (8761)
- [x] Config Server (8888) + `config-repo` — built, not yet consumed by any service
- [x] Auth Service (8081) — register/login/validate, JWT with `userId` + `name` claims, BCrypt
- [x] API Gateway (8080) — routes, `JwtAuthFilter`, `X-User-*` propagation + anti-spoofing
- [x] User Service (8082) — lazily created profile, user search, internal endpoints
- [x] Note Service (8083) — notes, tags (`@ElementCollection`), paginated search, sharing via **OpenFeign**
- [x] Schema-per-service (`auth_schema`, `user_schema`, `note_schema`), auto-created
- [x] Secrets hygiene — real `application.properties` gitignored, `.example` committed

**Stack:** Java 21 · Spring Boot 3.4.6 · Spring Cloud 2024.0.1 · PostgreSQL 16

---

## Phase 1 — Security & Authorization ✅ DONE (from day one)

ShopNest added authorization in its Phase 6, after discovering identity headers were ignored. NoteNest started with it.

- [x] Public paths: `/api/auth/register`, `/api/auth/login`, `/docs/specs/`
- [x] `/internal/**` blocked at the gateway with `403`, before the token is parsed
- [x] Ownership enforced in `NoteServiceImpl` — update/delete/share/revoke/list-shares
- [x] Read access = owner **or** share recipient
- [x] `MissingRequestHeaderException` → `401`, not `500`
- [x] `CorsWebFilter` at the gateway, origins from `notenest.cors.allowed-origins`

---

## Phase 2 — Docker ✅ DONE (not yet verified end-to-end)

- [x] Multi-stage `Dockerfile` for all six modules, config-server included
- [x] `docker-compose.yml` — postgres + eureka + config + 3 services + gateway
- [x] Postgres `healthcheck` + `service_healthy`
- [x] `spring-boot-starter-actuator` in every domain service and the gateway
- [ ] **Verify end-to-end:** register → login → create note → share → shared-with-me as recipient → revoke
- ⏭️ **SKIP (for now)** `service_healthy` for Spring containers — actuator is in place, compose still uses `service_started`

---

## Phase 3 — Testing ✅ DONE

- [x] Service layer with Mockito — auth (4), user (6), note (14)
- [x] Feign error paths — 404 translated to `UserNotFoundException`, 503 propagated as `502`
- [x] `@DataJpaTest` + H2 for the hand-written JPQL in `NoteRepository` (5)
- ⏭️ **SKIP** `MockMvc` controller tests, `@SpringBootTest`

---

## Phase 4 — Documentation ✅ DONE

- [x] Scalar multi-service page at `/docs.html`
- [x] `docs/` — documentation, build order, testing notes, interview Q&A, Docker notes, schema snapshot
- [x] Frontend handover package — `FRONTEND-README.md`, service atlas, brief, design handover
- [x] Monorepo layout — `backend/`, `frontend/`, `docs/`
- ⏭️ **SKIP** `@Operation` / `@Schema` annotations — spec is generated from controllers already
- ⏭️ **SKIP** Postman collection — Scalar can send requests itself

---

## Phase 5 — Frontend ⏳ NEXT

- [ ] Scaffold `frontend/` (React + TypeScript + Vite) — see [`FRONTEND-README.md`](FRONTEND-README.md)
- [ ] Phase 1 against MSW dummy data
- [ ] Phase 2 against the real gateway

---

## Known gaps worth fixing

| Gap | Impact | Fix |
|---|---|---|
| `shared-with-me` fails entirely if one owner's profile is missing | A user who shared notes but never opened `/api/users/me` has no profile → Feign 404 → whole list returns `404` | Fall back to `null` owner fields instead of throwing, or create profiles from note-service's side too |
| Share target must have opened the app once | Profiles are created lazily, so a freshly registered user who never called `/api/users/me` cannot be found by email | Frontend calls `/api/users/me` right after login (cheap, and the brief requires it) |
| Profile email/name can drift from auth | Copied from token claims at first access; auth has no "change email" today, so harmless for now | Event on change, or refresh on every `/me` call |
| `management.endpoint.health.show-details` duplicated | Cosmetic, in `note-service/application-docker.properties` | Delete one line |

## Known deliberate simplifications

| Simplification | Proper solution |
|---|---|
| Sharing is not realtime | WebSocket / Server-Sent Events |
| Only `READ` permission | `READ` / `EDIT` / `ADMIN` — enum already in place |
| Internal endpoints guarded only at the gateway | Signed service-to-service tokens or mTLS |
| Single shared PostgreSQL instance | Physically separate database per service |
| No circuit breaker / tracing / cache | Resilience4j, Zipkin, Redis |
| No refresh token | Refresh token + rotation |
