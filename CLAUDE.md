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

## Testing

```bash
make test-backend                                          # mvn verify, H2 in-memory, no Docker
make test-frontend                                         # Jest unit tests
cd backend && ./mvnw test -Dtest=ClassName -q             # single backend test
cd e2e && node auth.test.js                               # E2E (requires running stack)
```

## Non-Obvious Architecture

**Dual inventory:** `AdminInventory` (system/master stock) and `Inventory` (per-user allocations) are separate entities. `ReportService` valuation excludes admin inventory from user-facing totals.

**Transaction state machine:** `PENDING → APPROVED | REJECTED` enforced in `TransactionService`; illegal transitions throw `InvalidStateTransitionException`. Approval triggers inventory adjustment.

**Auth:** JWT stored in localStorage; Axios interceptor in `frontend/src/api/api.js` injects Bearer token. 401 clears token and redirects to `/login`.

**DDL_AUTO:** `update` (dev) · `validate` (prod) · `create-drop` (test). `DataInitializer` seeds demo data on startup.

**Production database (2026-07-24):** Postgres now runs on a self-hosted Compute Engine VM (`pharmatrack-db-vm`, `us-central1-a`, GCP Always-Free-tier `e2-micro`), not Cloud SQL — migrated off the old Cloud SQL instance (`pharmatrack-db`, asia-south1) to cut cost from ~$11/month to ~$0–2/month. The old Cloud SQL instance is kept around temporarily post-cutover as a rollback safety net before final deletion — check `gcloud sql instances list` to confirm whether it still exists. Key facts, none of which are derivable from this repo:
- The VM has **no public IP**. Cloud Run reaches it over a private Serverless VPC Access connector (`pharmatrack-connector`, `asia-south1`) attached to the `default` VPC; SSH is IAP-tunnel-only (`gcloud compute ssh pharmatrack-db-vm --zone=us-central1-a --tunnel-through-iap`).
- `DB_URL` on the Cloud Run service is a plain TCP JDBC URL (`jdbc:postgresql://10.128.0.2:5432/pharma_inventory`), not a `cloudSqlInstance=`/`socketFactory=` URL — the `postgres-socket-factory` Maven dependency was removed as dead weight (PR #97).
- **DB connection config (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, VPC connector) lives only on the live Cloud Run service, not in this repo or in `ci-cd.yml`** — the GitHub Actions deploy step only passes `service`/`region`/`image` and a new revision inherits the previous revision's env vars/networking. Inspect the live service (`gcloud run services describe pharmatrack-backend --region=asia-south1`) to see current values.
- The VM was deliberately placed in `us-central1` (not `asia-south1`, where Cloud Run/users are) because Compute Engine's Always Free tier only exists in `us-west1`/`us-central1`/`us-east1` — this trades ~250ms+ of added cross-region latency per DB-touching request for near-zero hosting cost.
- Daily backups: a cron on the VM (`/usr/local/bin/pharmatrack-db-backup.sh`, `/etc/cron.d/pharmatrack-db-backup`) runs `pg_dump` to `/var/backups/postgres/` with 7-day local rotation. This is local-disk-only (no offsite/GCS copy) — a deliberate tradeoff to avoid granting the VM's default service account broader storage scope, which would have required a VM restart (outage) to apply.
- `DataMigrationService`'s raw-JDBC startup migrations are standard Postgres SQL (`pg_catalog`/`information_schema` only, no Cloud-SQL-specific syntax) — they run unchanged against this VM.

## Reports

- Pharma name as heading; list its medicine specifications beneath — never repeat the name per spec.
- All timestamp-ordered reports: recent → past.
