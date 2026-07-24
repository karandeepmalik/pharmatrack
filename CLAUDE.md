# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Rules

- No local npm — run tests via `make` or Docker only.
- Every feature must add or modify existing JUnit, UI, or E2E tests.
- Never commit to main — always use a feature branch.
- Never deploy directly to cloud — push to GitHub; Actions deploys to Cloud Run.
- UI must be responsive, mobile-viewable, and center-aligned.
- Every new UI page needs back navigation.
- Always verify DB schema compatibility when changing services.
- Token-minimize all LLM interactions.
- Before merging: run the full suite (backend, frontend, e2e) locally, then push/open the PR and wait for GitHub Actions CI to go green too. If everything passes, squash-merge and delete the branch automatically — no need to ask each time. Merging to `main` deploys to Cloud Run, so confirm the deployed service is actually healthy (`/actuator/health`) before considering the task done; a green test suite doesn't guarantee a working deploy.

## Testing

```bash
make test-backend                              # mvn verify, H2 in-memory, no Docker — already includes Spring Boot integration-style tests, no separate IT suite exists
make test-frontend                             # Jest unit tests
cd backend && ./mvnw test -Dtest=ClassName -q  # single backend test
make up && (cd e2e && node auth.test.js); make down   # E2E — needs the full stack running
```

## Non-Obvious Architecture

**Dual inventory:** `AdminInventory` (system/master stock) and `Inventory` (per-user allocations) are separate entities. `ReportService` valuation excludes admin inventory from user-facing totals.

**Transaction state machine:** `PENDING → APPROVED | REJECTED` enforced in `TransactionService`; illegal transitions throw `InvalidStateTransitionException`. Approval triggers inventory adjustment.

**Auth:** JWT stored in localStorage; Axios interceptor in `frontend/src/api/api.js` injects Bearer token. 401 clears token and redirects to `/login`.

**DDL_AUTO:** `update` (dev) · `validate` (prod) · `create-drop` (test). `DataInitializer` seeds demo data on startup.

**Production database:** Postgres runs on a self-hosted Compute Engine VM (`pharmatrack-db-vm`, `us-central1-a`, free-tier `e2-micro`), not Cloud SQL (decommissioned). No public IP — Cloud Run reaches it via a private Serverless VPC Access connector (`pharmatrack-connector`, `asia-south1`); SSH is IAP-tunnel-only. `DB_URL` is plain TCP (`jdbc:postgresql://10.128.0.2:5432/pharma_inventory`), no Cloud SQL socket factory. All connection config (URL, username, secrets, VPC connector/egress) is declared explicitly in `.github/workflows/ci-cd.yml`'s backend deploy step (`flags` for env vars via `--set-env-vars`, `secrets` for Secret Manager refs) — if you hotfix it live via `gcloud run services update`, update `ci-cd.yml` to match or the next deploy reverts it. The VM sits in `us-central1` rather than `asia-south1` (where Cloud Run/users are) purely because Compute Engine's free tier is US-only — an accepted tradeoff of added cross-region latency for near-zero cost. Daily local `pg_dump` backups via cron (`/etc/cron.d/pharmatrack-db-backup`), 7-day rotation, disk-local only (no offsite copy). `DataMigrationService`'s startup migrations are plain Postgres SQL, unaffected by the host change.

## Reports

- Pharma name as heading; list its medicine specifications beneath — never repeat the name per spec.
- All timestamp-ordered reports: recent → past.
