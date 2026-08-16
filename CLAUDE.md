# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

PharmaTrack — pharmaceutical medicine stock management system. React 18 frontend (served via Nginx) + Spring Boot 3.2 (Java 17) backend + PostgreSQL 15, deployed to Cloud Run via GitHub Actions.

```
frontend/    React 18 app (CRA + Jest)
backend/     Spring Boot 3.2 API (Maven)
e2e/         API-only end-to-end tests against a running stack (Node.js, zero deps, no browser)
e2e-browser/ Browser-driven e2e tests (Playwright) — renders the real frontend and clicks
             through it, so it catches frontend rendering/wiring bugs e2e/ structurally can't
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
make up && make test-e2e-browser; make down          # browser e2e (Playwright) — needs `npx playwright install chromium` once
```
No local npm/Maven install required — everything above runs through `make`/Docker except the single-test `./mvnw` invocation, which needs `JAVA_HOME` pointed at a JDK 17 (not whatever `mvn` resolves by default — see Gotchas).

## Rules

- Every feature must add or modify existing JUnit, UI, or E2E tests.
- Never commit to main — always use a feature branch, open a PR.
- Never deploy directly to Cloud Run — push to GitHub; Actions builds and deploys.
- UI must be responsive, mobile-viewable, and center-aligned; every new UI page needs back navigation.
- Before merging: run the full suite (backend, frontend, e2e; e2e-browser too for frontend-behavior changes — run `e2e/auth.test.js` *before* `e2e-browser`, or `make down-v` between them, see Gotchas) locally, then push/open the PR and squash-merge and delete the branch — no need to ask each time. Merging to `main` deploys to Cloud Run, so confirm the deployed service is actually healthy (`/actuator/health`) before considering the task done; a green test suite does not guarantee a working deploy.

## Architecture

### Backend package layout (`backend/src/main/java/com/pharma/medicinestock/`)
`controller` → `service` → `repository`/`entity`, with `dto` as the API boundary shape and `mapper`/inline `toResponse()` helpers converting entities to DTOs. Controllers should be thin HTTP adapters — no business logic, no manual validation, no untyped `Map` bodies (this was a real, since-fixed problem in `MedicineController`, the drug-catalog CRUD controller — not to be confused with `MedicineStockController`, which handles stock levels). `security` holds JWT + rate-limiting; `scheduler` holds the one `@Scheduled` job (in-transit adjustment expiry); `exception/GlobalExceptionHandler` is the single centralized exception→HTTP-status mapping for every controller — check its class-level Javadoc table before adding a new exception type rather than handling errors ad hoc in a controller.

### Dual medicine stock model
`MedicineStock` rows are typed by `medicineStockType`: `REGULAR_MEDICINE_STOCK` (per-user allocation) vs `ADMIN_MEDICINE_STOCK` (system/master stock). `ReportService` valuation excludes admin stock from user-facing totals. The same (user, medicine) pair can have both types simultaneously — this is why the unique constraint is `(user_id, medicine_id, medicine_stock_type)`, not just `(user_id, medicine_id)`.

### Current stock is forward-reconstructed, not cached
`CurrentStockCalculator` computes "available now" by summing `MedicineStockAdjustment` + non-rejected `Transaction` history from scratch — it does **not** trust `MedicineStock.quantity` as a cache. This is deliberate: a cached-field approach previously drifted from reality in production (the drift was root-caused and fixed). If you ever see `MedicineStock.quantity` disagree with reconstructed stock, the reconstruction is the source of truth — reconcile via a direct `UPDATE`, never by inserting a synthetic adjustment row to explain away the drift (that double-counts). Every seeded/backfilled `MedicineStock` row must have a matching genesis `MedicineStockAdjustment`, or it's invisible to reconstruction and shows as 0 available.

### Transaction state machine
`PENDING → APPROVED | REJECTED`, enforced in `TransactionService`; illegal transitions throw `InvalidStateTransitionException` (409). Approval triggers the actual medicine stock deduction/adjustment — submission itself only reserves against reconstructed settled stock, it does not move medicine stock.

### Paginated list filtering must be server-side
Any filter exposed on a paginated list (e.g. admin's "View Past Medicine Dispatches") has to be applied in the query itself (`TransactionRepository.searchHistory`, driven by `(:param IS NULL OR t.field = :param)` clauses so one query serves both the filtered and unfiltered case) — never as a client-side `.filter()` over rows already loaded into browser state. Once total results exceed one page, a client-side filter silently misses real matches sitting on unloaded pages instead of surfacing them. This was a real, shipped regression, caught by the Playwright browser suite (`e2e-browser/`) rather than unit tests, since unit tests mock the API layer and never exercise a full multi-page round-trip.

### Quantities vs money
Quantities are `BigDecimal` scale-1 (`NUMERIC(10,1)` in Postgres), rounded HALF_UP via `QuantityUtil.round()` — submitted values are rounded, never rejected for having extra decimals. Money is always a whole number (`long`, rupees) — every `price × quantity` calculation rounds HALF_UP to an integer; there's no shared money-rounding helper yet (duplicated per report method in `ReportService`, a known cleanup target). `BigDecimal.equals()` is scale-sensitive (`new BigDecimal("10").equals(new BigDecimal("10.0"))` is `false`) — tests must use AssertJ's `.isEqualByComparingTo(...)`, not `.isEqualTo(...)`, for quantity assertions.

### Auth
JWT (HS256, self-issued) stored in a cookie set by the backend; frontend also keeps it for the `Authorization: Bearer` header via an Axios interceptor in `frontend/src/api/api.js` — a 401 clears it and redirects to `/login`, **except** a 401 from `/auth/login` itself (wrong credentials, not session expiry — the interceptor checks `error.config.url`, since redirecting there would hard-reload the login page and wipe out the error message before the user ever sees it; this was a real bug, found by the Playwright suite in `e2e-browser/`). `LoginRateLimiter` tracks failures on two independent dimensions (per-username and per-IP via `X-Forwarded-For`, since Cloud Run always proxies) — a successful login resets both. `DataInitializer`'s demo-account seeding is gated `@Profile("!test & !prod")` and requires `SPRING_PROFILES_ACTIVE=prod` to actually take effect in production — it will NOT skip prod without that env var being set correctly.

### DDL_AUTO
`update` (dev) · `validate` (prod) · `create-drop` (test), controlled by the `DDL_AUTO` env var (not by Spring profile alone). Because prod uses `validate`, Hibernate never auto-migrates production schema — real schema changes go through `DataMigrationService`'s raw-JDBC startup migrations (idempotent, guarded by checking `information_schema` before altering, since a redundant `ALTER COLUMN TYPE` — even to the same type — bumps Postgres's cache-invalidation counter and breaks any pooled connection with a cached prepared statement against that table).

### Production database
Postgres runs on a self-hosted Compute Engine VM (`pharmatrack-db-vm`, `us-central1-a`, GCP Always-Free-tier `e2-micro`), not Cloud SQL. Facts that aren't derivable from application code:
- No public IP. Cloud Run reaches it via Direct VPC egress (`--network=default --subnet=default`, the `default` VPC network's auto-created `asia-south1` subnet, `10.160.0.0/20`) — the same VPC network the VM's NIC is on (`us-central1`'s `default` subnet), so standard global VPC routing reaches it with no peering needed. Covered by the existing `default-allow-internal` firewall rule (source `10.128.0.0/9`), so no dedicated firewall rule exists for it (unlike SSH, below). SSH is IAP-tunnel-only (`gcloud compute ssh pharmatrack-db-vm --zone=us-central1-a --tunnel-through-iap`).
- Previously reached over a Serverless VPC Access connector (`pharmatrack-connector`, `asia-south1`) instead — replaced because that connector's 2-3 always-on `e2-micro` instances aren't Compute Engine free-tier eligible (the free tier only covers `us-west1`/`us-central1`/`us-east1`, never `asia-south1`), making it a real, avoidable ~Rs 50/day charge. Direct VPC egress has no always-on billed instances for this. If you ever see `pharmatrack-connector` referenced again, it's stale — the connector was deleted.
- `DB_URL` is plain TCP JDBC (`jdbc:postgresql://10.128.0.2:5432/pharma_inventory`), no Cloud SQL socket factory. The Postgres *database* name (`pharma_inventory`) was deliberately left unchanged during the Inventory→MedicineStock rename — renaming a live database requires dropping all connections first, meaningfully more disruptive than the table/column renames that were in scope. Only tables/columns inside it (`medicine_stock`, `medicine_stock_adjustments`, `*.medicine_stock_type`) were renamed.
- **All Cloud Run env vars/secrets/VPC config are declared explicitly in `.github/workflows/ci-cd.yml`'s `deploy-cloud-run` job** (`flags` for plain env vars via `--set-env-vars` with a custom delimiter, `secrets` for Secret Manager refs) — this is the source of truth, not the live service. If you hotfix live via `gcloud run services update`, update `ci-cd.yml` to match or the next deploy silently reverts it. The `env_vars` input on `deploy-cloudrun@v3` is deliberately unused — it splits on commas even inside a single entry, which once silently corrupted a comma-joined value.
- The VM sits in `us-central1` (not `asia-south1`, where Cloud Run/users are) purely because Compute Engine's free tier is US-only — an accepted latency-for-cost tradeoff.
- Daily `pg_dump` backups via cron on the VM (`/etc/cron.d/pharmatrack-db-backup`), 7-day local rotation, disk-local only (no offsite copy).

### CI/CD (`.github/workflows/ci-cd.yml`)
GitHub Actions is the only live pipeline. Jobs: `backend-test` → `frontend-test` → `docker-build-push` → `deploy-cloud-run` (last two gated to `main` pushes only). There's no path filtering, so **every** merge to `main` — even a docs-only change — triggers a full rebuild and redeploy of both services.

### Reports
Plain-text report generation lives in `ReportService` (the largest file in the codebase, mixing report assembly, money math, and string formatting — a known refactor target). Formatting conventions: pharma name as heading with its medicine specs listed beneath (never repeat the name per spec); all timestamp-ordered reports go recent → past.

## Gotchas

- **Local `mvn` may resolve the wrong JDK.** Homebrew's `mvn` can default to a JDK that silently breaks Lombok annotation processing (every `@Getter`/`@Builder`/`@Slf4j`-generated method vanishes, producing hundreds of misleading "cannot find symbol" errors). `make test-backend` sets `JAVA_HOME` correctly; if running `mvn` directly for faster iteration, set `JAVA_HOME` to a JDK 17 explicitly.
- **Maven's incremental compiler can mask real errors.** `mvn test-compile` may reuse stale bytecode for a test class whose own source didn't change, even when a dependency (e.g. an entity's field type) did. Use `mvn clean test-compile` when checking compile status after a type change.
- **e2e defaults to production.** `e2e/auth.test.js` falls back to the live Cloud Run URLs unless `BACKEND_URL`/`FRONTEND_URL` are set — always set them for a local run, or you'll be exercising (and potentially mutating) production data.
- **`frontend/nginx.conf`'s `/api/` proxy only matters locally.** The local `docker compose` build never sets `REACT_APP_API_URL`, so the bundle falls back to a relative `/api` base — without the proxy block, a real browser hitting `http://localhost/api/...` gets nginx's own 404/405, not the backend, and nothing in the UI can authenticate. In prod, `REACT_APP_API_URL` is baked in at build time to the absolute Cloud Run backend URL (see `ci-cd.yml`), so the frontend bundle never even constructs a relative `/api` request there and this proxy block is inert.
- **A `null` bind parameter passed into `LOWER()`/`CONCAT()` in JPQL crashes on real Postgres, not H2.** Postgres's JDBC driver can't infer a type for a bind parameter that's only ever used as an argument to a SQL function, and defaults it to `bytea` — producing `ERROR: function lower(bytea) does not exist` whenever that parameter is `null` (e.g. an optional search filter left blank). This does not reproduce against H2 (used in backend unit tests), so it passes `make test-backend` and CI and only surfaces against real Postgres. Fix pattern: build the complete pattern/string in Java (e.g. `"%" + notes.trim().toLowerCase() + "%"`) and bind it as a plain parameter directly against a real column (`LOWER(t.notes) LIKE :notesPattern`) — never wrap the bind parameter itself in a SQL function at the query level. See `TransactionRepository.searchHistory` / `TransactionService.getHistory`.
- **`e2e-browser/` permanently alters the local dev DB — run `e2e/auth.test.js` first, or `make down-v` between them.** `e2e-browser`'s Manage Medicines and Modify Medicine Stock tests add medicines and change john.doe's medicineStock that never get cleaned up (unlike `e2e/auth.test.js`, which is self-contained setup/teardown). `e2e/auth.test.js` hardcodes baseline assumptions ("exactly 5 Shield FX medicines exist", john.doe ends at exactly 56.5 units) that a *single* `e2e-browser` run already breaks — confirmed by running both back to back. `make down-v` (wipes the Postgres volume, `DataInitializer` reseeds on next `make up`) is required between them, not just after repeated `e2e-browser` runs. Tests that look up a just-created item scroll (via the `scrollUntilVisible` helper in `tests/helpers.js`) rather than assuming it lands on page 0, since PAGE_SIZE=10/20 lists sort by `submittedAt` and many same-day items (dispatch date, not wall-clock time, drives `submittedAt`) can tie and push a specific item past the first page.
