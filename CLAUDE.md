# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

PharmaTrack — pharmaceutical inventory management system. React 18 frontend (served via Nginx) + Spring Boot 3.2 (Java 17) backend + PostgreSQL 15, deployed to Cloud Run via GitHub Actions.

```
frontend/   React 18 app (CRA + Jest)
backend/    Spring Boot 3.2 API (Maven)
e2e/        End-to-end tests against a running stack (Node.js, zero deps)
infra/k8s/  Dead — not used by the real deploy path (see CI/CD below)
```

## Commands

### Local stack (Docker)
```bash
make up              # docker compose up --build -d — frontend :80, backend :8080
make down            # stop containers
make down-v          # stop AND wipe the Postgres volume (clean-slate reseed)
make logs-backend    # tail backend logs
make shell-db        # psql shell into the local postgres container
```
Demo credentials seeded locally by `DataInitializer` on first boot: `admin`/`Admin@123`, `john.doe`/`User@123`, `jane.smith`/`User@123`. (Production has since diverged — see Auth below.)

### Testing
```bash
make test-backend                                   # mvn verify, H2 in-memory, no Docker needed — already includes Spring Boot integration-style @SpringBootTest tests, no separate IT suite
make test-frontend                                   # Jest unit tests
cd backend && ./mvnw test -Dtest=ClassName -q       # single backend test
make up && BACKEND_URL=http://localhost:8080 FRONTEND_URL=http://localhost \
  (cd e2e && node auth.test.js); make down            # e2e — MUST set these env vars for local runs, or it defaults to hitting production
```
No local npm/Maven install required — everything above runs through `make`/Docker except the single-test `./mvnw` invocation, which needs `JAVA_HOME` pointed at a JDK 17 (not whatever `mvn` resolves by default — see Gotchas).

## Rules

- Every feature must add or modify existing JUnit, UI, or E2E tests.
- Never commit to main — always use a feature branch, open a PR.
- Never deploy directly to Cloud Run — push to GitHub; Actions builds and deploys.
- UI must be responsive, mobile-viewable, and center-aligned; every new UI page needs back navigation.
- Before merging: run the full suite (backend, frontend, e2e) locally, then push/open the PR and wait for GitHub Actions CI to go green too. If everything passes, squash-merge and delete the branch — no need to ask each time. Merging to `main` deploys to Cloud Run, so confirm the deployed service is actually healthy (`/actuator/health`) before considering the task done; a green test suite does not guarantee a working deploy.

## Architecture

### Backend package layout (`backend/src/main/java/com/pharma/inventory/`)
`controller` → `service` → `repository`/`entity`, with `dto` as the API boundary shape and `mapper`/inline `toResponse()` helpers converting entities to DTOs. Controllers should be thin HTTP adapters — no business logic, no manual validation, no untyped `Map` bodies (this was a real, since-fixed problem in `MedicineController`). `security` holds JWT + rate-limiting; `scheduler` holds the one `@Scheduled` job (in-transit adjustment expiry); `exception/GlobalExceptionHandler` is the single centralized exception→HTTP-status mapping for every controller — check its class-level Javadoc table before adding a new exception type rather than handling errors ad hoc in a controller.

### Dual inventory model
`Inventory` rows are typed by `inventoryType`: `REGULAR_MEDICINE_STOCK` (per-user allocation) vs `ADMIN_MEDICINE_STOCK` (system/master stock). `ReportService` valuation excludes admin stock from user-facing totals. The same (user, medicine) pair can have both types simultaneously — this is why the unique constraint is `(user_id, medicine_id, inventory_type)`, not just `(user_id, medicine_id)`.

### Current stock is forward-reconstructed, not cached
`CurrentStockCalculator` computes "available now" by summing `InventoryAdjustment` + non-rejected `Transaction` history from scratch — it does **not** trust `Inventory.quantity` as a cache. This is deliberate: a cached-field approach previously drifted from reality in production (the drift was root-caused and fixed). If you ever see `Inventory.quantity` disagree with reconstructed stock, the reconstruction is the source of truth — reconcile via a direct `UPDATE`, never by inserting a synthetic adjustment row to explain away the drift (that double-counts). Every seeded/backfilled `Inventory` row must have a matching genesis `InventoryAdjustment`, or it's invisible to reconstruction and shows as 0 available.

### Transaction state machine
`PENDING → APPROVED | REJECTED`, enforced in `TransactionService`; illegal transitions throw `InvalidStateTransitionException` (409). Approval triggers the actual inventory deduction/adjustment — submission itself only reserves against reconstructed settled stock, it does not move inventory.

### Quantities vs money
Quantities are `BigDecimal` scale-1 (`NUMERIC(10,1)` in Postgres), rounded HALF_UP via `QuantityUtil.round()` — submitted values are rounded, never rejected for having extra decimals. Money is always a whole number (`long`, rupees) — every `price × quantity` calculation rounds HALF_UP to an integer; there's no shared money-rounding helper yet (duplicated per report method in `ReportService`, a known cleanup target). `BigDecimal.equals()` is scale-sensitive (`new BigDecimal("10").equals(new BigDecimal("10.0"))` is `false`) — tests must use AssertJ's `.isEqualByComparingTo(...)`, not `.isEqualTo(...)`, for quantity assertions.

### Auth
JWT (HS256, self-issued) stored in a cookie set by the backend; frontend also keeps it for the `Authorization: Bearer` header via an Axios interceptor in `frontend/src/api/api.js` — a 401 clears it and redirects to `/login`. `LoginRateLimiter` tracks failures on two independent dimensions (per-username and per-IP via `X-Forwarded-For`, since Cloud Run always proxies) — a successful login resets both. `DataInitializer`'s demo-account seeding is gated `@Profile("!test & !prod")` and requires `SPRING_PROFILES_ACTIVE=prod` to actually take effect in production — it will NOT skip prod without that env var being set correctly.

### DDL_AUTO
`update` (dev) · `validate` (prod) · `create-drop` (test), controlled by the `DDL_AUTO` env var (not by Spring profile alone). Because prod uses `validate`, Hibernate never auto-migrates production schema — real schema changes go through `DataMigrationService`'s raw-JDBC startup migrations (idempotent, guarded by checking `information_schema` before altering, since a redundant `ALTER COLUMN TYPE` — even to the same type — bumps Postgres's cache-invalidation counter and breaks any pooled connection with a cached prepared statement against that table).

### Production database
Postgres runs on a self-hosted Compute Engine VM (`pharmatrack-db-vm`, `us-central1-a`, GCP Always-Free-tier `e2-micro`), not Cloud SQL. Facts that aren't derivable from application code:
- No public IP. Cloud Run reaches it over a private Serverless VPC Access connector (`pharmatrack-connector`, `asia-south1`); SSH is IAP-tunnel-only (`gcloud compute ssh pharmatrack-db-vm --zone=us-central1-a --tunnel-through-iap`).
- `DB_URL` is plain TCP JDBC (`jdbc:postgresql://10.128.0.2:5432/pharma_inventory`), no Cloud SQL socket factory.
- **All Cloud Run env vars/secrets/VPC config are declared explicitly in `.github/workflows/ci-cd.yml`'s `deploy-cloud-run` job** (`flags` for plain env vars via `--set-env-vars` with a custom delimiter, `secrets` for Secret Manager refs) — this is the source of truth, not the live service. If you hotfix live via `gcloud run services update`, update `ci-cd.yml` to match or the next deploy silently reverts it. The `env_vars` input on `deploy-cloudrun@v3` is deliberately unused — it splits on commas even inside a single entry, which once silently corrupted a comma-joined value.
- The VM sits in `us-central1` (not `asia-south1`, where Cloud Run/users are) purely because Compute Engine's free tier is US-only — an accepted latency-for-cost tradeoff.
- Daily `pg_dump` backups via cron on the VM (`/etc/cron.d/pharmatrack-db-backup`), 7-day local rotation, disk-local only (no offsite copy).

### CI/CD (`.github/workflows/ci-cd.yml`)
GitHub Actions is the only live pipeline — `cloudbuild.yaml`, `render.yaml`, `fly.toml`, `railway.toml`, and `infra/k8s/*` are all dead/unused alternate deploy paths left over from exploration. Jobs: `backend-test` → `frontend-test` → `docker-build-push` → `deploy-cloud-run` (last two gated to `main` pushes only). There's no path filtering, so **every** merge to `main` — even a docs-only change — triggers a full rebuild and redeploy of both services.

### Reports
Plain-text report generation lives in `ReportService` (the largest file in the codebase, mixing report assembly, money math, and string formatting — a known refactor target). Formatting conventions: pharma name as heading with its medicine specs listed beneath (never repeat the name per spec); all timestamp-ordered reports go recent → past.

## Gotchas

- **Local `mvn` may resolve the wrong JDK.** Homebrew's `mvn` can default to a JDK that silently breaks Lombok annotation processing (every `@Getter`/`@Builder`/`@Slf4j`-generated method vanishes, producing hundreds of misleading "cannot find symbol" errors). `make test-backend` sets `JAVA_HOME` correctly; if running `mvn` directly for faster iteration, set `JAVA_HOME` to a JDK 17 explicitly.
- **Maven's incremental compiler can mask real errors.** `mvn test-compile` may reuse stale bytecode for a test class whose own source didn't change, even when a dependency (e.g. an entity's field type) did. Use `mvn clean test-compile` when checking compile status after a type change.
- **e2e defaults to production.** `e2e/auth.test.js` falls back to the live Cloud Run URLs unless `BACKEND_URL`/`FRONTEND_URL` are set — always set them for a local run, or you'll be exercising (and potentially mutating) production data.
