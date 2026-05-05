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

## Reports

- Pharma name as heading; list its medicine specifications beneath — never repeat the name per spec.
- All timestamp-ordered reports: recent → past.
