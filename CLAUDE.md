# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Rules

| Domain | Rule |
|---|---|
| Branch | feature branch → PR → Actions → Cloud Run; never commit main or deploy direct |
| Tests | every feature needs JUnit + Jest (or E2E) changes |
| UI | responsive · mobile · center-aligned · new pages need back nav |
| DB | verify schema compat before changing services |
| Reports | pharma name as section heading; specs below it; timestamps desc |
| LLM | minimize tokens |

## Stack

React 18 · Spring Boot 3.2 · Java 17 · PostgreSQL 15  
Nginx (port 80) reverse-proxies `/api/*` → backend (port 8080)  
Roles: `ADMIN` `USER` · Medicine types: `VIAL` `TABLET` `CAPSULE` `SYRUP`  
Demo: `admin/Admin@123` · `john.doe/User@123` · `jane.smith/User@123`

## Testing

```bash
make test-backend                                    # mvn verify · H2 in-memory · no Docker
make test-frontend                                   # all Jest tests
cd backend && ./mvnw test -Dtest=ClassName -q        # single backend test class
cd frontend && npx jest path/to/test.jsx             # single frontend test file
cd e2e && node auth.test.js                          # E2E (needs running stack)
```

No local npm — use make/Docker.

## Non-Obvious Architecture

**API layer:** `frontend/src/api/api.js` is the single source of truth for all HTTP calls. All new endpoints go here as named exports.

**Route security (SecurityConfig):**
- Public: `/api/auth/**` `/actuator/health`
- USER-only: `GET /api/inventory/available` · `GET /api/transactions/my` · `POST /api/transactions`
- ADMIN forbidden from USER-only endpoints; must use admin-specific paths
- ADMIN: all of `/api/inventory/**` `/api/transactions/**` `/api/users/**`

**Dual inventory:** `AdminInventory` (master stock) and `Inventory` (per-user) are separate entities. `ReportService` valuation excludes admin inventory from user totals.

**Transaction state machine:** `PENDING → APPROVED | REJECTED` in `TransactionService`. Illegal transitions throw `InvalidStateTransitionException`. Approval triggers inventory adjustment.

**Auth:** JWT (24 h) in localStorage. Axios interceptor in `api.js` injects Bearer token. 401 → clear token + redirect `/login`.

**Entities:** All use Lombok `@Data @NoArgsConstructor @AllArgsConstructor @Builder` — no manual getters/setters.

**DDL_AUTO:** `update` (dev) · `validate` (prod) · `create-drop` (test). `DataInitializer` seeds demo data on startup.

**npm overrides:** `frontend/package.json` has an `overrides` block pinning safe transitive dep versions from `react-scripts`. Do not remove or individually upgrade those packages.
