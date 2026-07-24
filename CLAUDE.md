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
- **Before merging any branch, always run the full suite — backend, frontend, and e2e — and only merge if all of it passes.** If everything's green, push, open/update the PR, wait for GitHub Actions CI to go green too, then squash-merge and delete the branch automatically — don't stop to ask for merge confirmation each time. Since merging to `main` deploys to Cloud Run, treat the task as unfinished until you've also confirmed the deployed service is actually healthy (`/actuator/health`) post-merge — a green test suite does not guarantee a working deploy (e.g. the 2026-07-24 CI/CD incident: passing tests + passing CI checks still shipped a broken `CORS_ALLOWED_ORIGINS`, only caught by inspecting the live service afterward).

## Testing

```bash
make test-backend                                          # mvn verify, H2 in-memory, no Docker (this already includes Spring Boot integration-style @SpringBootTest tests — there is no separate integration-test suite in this repo)
make test-frontend                                         # Jest unit tests
cd backend && ./mvnw test -Dtest=ClassName -q             # single backend test
make up                                                     # start the full stack for e2e (docker compose)
cd e2e && node auth.test.js                                # E2E — requires the running stack from `make up`
make down                                                   # tear the stack back down when done
```

## Non-Obvious Architecture

**Dual inventory:** `AdminInventory` (system/master stock) and `Inventory` (per-user allocations) are separate entities. `ReportService` valuation excludes admin inventory from user-facing totals.

**Transaction state machine:** `PENDING → APPROVED | REJECTED` enforced in `TransactionService`; illegal transitions throw `InvalidStateTransitionException`. Approval triggers inventory adjustment.

**Auth:** JWT stored in localStorage; Axios interceptor in `frontend/src/api/api.js` injects Bearer token. 401 clears token and redirects to `/login`.

**DDL_AUTO:** `update` (dev) · `validate` (prod) · `create-drop` (test). `DataInitializer` seeds demo data on startup.

**Production database (migrated 2026-07-24):** Postgres runs on a self-hosted Compute Engine VM (`pharmatrack-db-vm`, `us-central1-a`, GCP Always-Free-tier `e2-micro`), not Cloud SQL. The old Cloud SQL instance (`pharmatrack-db`, asia-south1) has been fully decommissioned and deleted — cost dropped from ~$11/month to ~$0–2/month. Key facts, none of which are derivable from application code:
- The VM has **no public IP**. Cloud Run reaches it over a private Serverless VPC Access connector (`pharmatrack-connector`, `asia-south1`) attached to the `default` VPC; SSH is IAP-tunnel-only (`gcloud compute ssh pharmatrack-db-vm --zone=us-central1-a --tunnel-through-iap`).
- `DB_URL` is a plain TCP JDBC URL (`jdbc:postgresql://10.128.0.2:5432/pharma_inventory`), not a `cloudSqlInstance=`/`socketFactory=` URL — the `postgres-socket-factory` Maven dependency was removed as dead weight (PR #97).
- **DB connection config (`DB_URL`, `DB_USERNAME`, secrets, VPC connector/egress) is declared explicitly in `.github/workflows/ci-cd.yml`'s `deploy-cloud-run` job** (backend step's `env_vars`/`secrets`/`flags` inputs) — no longer invisible/manual-only. If you change it live via `gcloud run services update` for a hotfix, update `ci-cd.yml` to match or the next deploy will silently revert it.
- The VM was deliberately placed in `us-central1` (not `asia-south1`, where Cloud Run/users are) because Compute Engine's Always Free tier only exists in `us-west1`/`us-central1`/`us-east1` — this trades ~250ms+ of added cross-region latency per DB-touching request for near-zero hosting cost.
- Daily backups: a cron on the VM (`/usr/local/bin/pharmatrack-db-backup.sh`, `/etc/cron.d/pharmatrack-db-backup`) runs `pg_dump` to `/var/backups/postgres/` with 7-day local rotation. This is local-disk-only (no offsite/GCS copy) — a deliberate tradeoff to avoid granting the VM's default service account broader storage scope, which would have required a VM restart (outage) to apply.
- `DataMigrationService`'s raw-JDBC startup migrations are standard Postgres SQL (`pg_catalog`/`information_schema` only, no Cloud-SQL-specific syntax) — they run unchanged against this VM.

## Reports

- Pharma name as heading; list its medicine specifications beneath — never repeat the name per spec.
- All timestamp-ordered reports: recent → past.
