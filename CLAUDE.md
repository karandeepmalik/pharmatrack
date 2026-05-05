# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Conventions

- No local npm installed — do not search for or run npm to execute tests; use `make` targets or Docker.
- Every feature implementation must add or modify existing JUnit, UI, or E2E tests.
- Always commit to a non-main branch; never commit directly to main.
- All UI code must be responsive, mobile-viewable, and center-aligned.
- Every new UI page must include back navigation.
- When changing services, verify DB schema compatibility with the change.
- In reports, never repeat the pharma name per specification — render the pharma name as a heading and list its specifications beneath it.
- Timestamp-ordered reports must be sorted recent → past.
- Never deploy directly to cloud — push to GitHub and let GitHub Actions handle deployment to Cloud Run.
- Always use token-minimizing strategies when working with LLM interactions.

## Commands

All common tasks are available via `make`. Run `make` with no args to see available targets.

**Local development:**
```bash
make up               # Start full stack (PostgreSQL + backend + frontend)
make down             # Stop containers
make down-v           # Stop and wipe volumes (resets DB)
make logs             # Tail all service logs
make seed             # Re-seed demo data
make shell-db         # Open psql to PostgreSQL
```

**Testing:**
```bash
make test             # Run backend + frontend tests
make test-backend     # mvn verify (uses H2 in-memory DB, no Docker needed)
make test-frontend    # Jest unit tests
make test-frontend-coverage  # Jest with coverage report

# Run a single backend test class:
cd backend && ./mvnw test -Dtest=TransactionServiceTest -q

# Run a single frontend test file:
cd frontend && npx jest src/__tests__/pages/user/SubmitTransaction.test.jsx

# E2E tests (requires running stack):
cd e2e && node auth.test.js
```

## Architecture

**Stack:** React 18 SPA → Nginx (port 80) → Spring Boot 3.2 REST API (port 8080) → PostgreSQL 15

The frontend is a pure SPA (React Router 6) served by Nginx, which reverse-proxies `/api` to the backend. JWT tokens are stored in localStorage and injected via an Axios interceptor in `frontend/src/api/api.js`. A 401 response clears the token and redirects to `/login`.

The backend is a stateless Spring Security + JWT service. All auth logic lives in `backend/src/main/java/com/pharma/inventory/security/`. The `JwtAuthenticationFilter` extracts bearer tokens per request; `SecurityConfig` defines which routes require which roles (`ADMIN` vs `USER`).

**Transaction workflow:** Users submit transactions (with an optional screenshot upload), which enter `PENDING` status. Admins view and approve/reject them. Approval triggers inventory adjustments. The status machine is enforced in `TransactionService` and raises `InvalidStateTransitionException` for illegal transitions.

**Inventory model:** There are two levels — system inventory (admin-managed master stock) and per-user allocations. `AdminInventory` and `Inventory` entities are separate. Valuation calculations in `ReportService` exclude admin inventory from user-facing totals.

**Data seeding:** `DataInitializer` runs on startup (controlled by `DDL_AUTO`). It seeds demo users, medicines, pharma companies, and initial stock. Set `DDL_AUTO=create-drop` to reset between test runs.

## Key Locations

| Concern | Location |
|---|---|
| REST controllers | `backend/src/main/java/com/pharma/inventory/controller/` |
| Business logic | `backend/src/main/java/com/pharma/inventory/service/` |
| JPA entities | `backend/src/main/java/com/pharma/inventory/entity/` |
| Security (JWT) | `backend/src/main/java/com/pharma/inventory/security/` |
| API client (Axios) | `frontend/src/api/api.js` |
| Auth context/hook | `frontend/src/context/AuthContext.jsx` |
| Admin pages | `frontend/src/pages/admin/` |
| User pages | `frontend/src/pages/user/` |
| Backend tests | `backend/src/test/java/com/pharma/inventory/` |
| Frontend tests | `frontend/src/__tests__/` |

## Environment & Config

Copy `.env.example` to `.env` before running locally. Key variables:

- `JWT_SECRET` — must be 256+ bits; generate with `make secret`
- `DDL_AUTO` — use `update` for dev, `validate` for prod, `create-drop` for tests
- `CORS_ORIGINS` — comma-separated whitelist of allowed origins
- `REACT_APP_API_URL` — frontend build-time API base URL (defaults to `/api` if unset)

Backend tests use `application-test.properties` with H2 in-memory DB — no external DB needed for `make test-backend`.

## CI/CD

GitHub Actions (`.github/workflows/ci-cd.yml`) runs on push to `main`/`develop` and PRs to `main`:
1. Backend tests (`mvn verify`) and frontend tests + build run in parallel
2. On `main` only: Docker images are built and pushed to Google Artifact Registry (`asia-south1-docker.pkg.dev`)
3. Both services are deployed to Google Cloud Run (`asia-south1`) via Workload Identity Federation — no stored service account keys
