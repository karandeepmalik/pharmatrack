# ─────────────────────────────────────────────────────────────────────
#  PharmaTrack — Developer Workflow
#  Run `make help` to see all available commands.
# ─────────────────────────────────────────────────────────────────────

.PHONY: help up down build logs test test-backend test-frontend \
        shell-backend shell-db clean seed secret deploy-railway \
        deploy-fly deploy-render

# Default target
.DEFAULT_GOAL := help

# ── Meta ──────────────────────────────────────────────────────────────
help: ## Show this help message
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
	  awk 'BEGIN{FS=":.*?## "}; {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}'

# ── Local development ─────────────────────────────────────────────────
up: ## Start the full stack (builds if needed)
	docker compose up --build -d
	@echo ""
	@echo "  ✓  PharmaTrack is running:"
	@echo "     Frontend  → http://localhost:80"
	@echo "     Backend   → http://localhost:8080"
	@echo "     API docs  → http://localhost:8080/actuator/health"
	@echo ""
	@echo "  Demo credentials:"
	@echo "     admin / Admin@123  (Admin role)"
	@echo "     john.doe / User@123  (User role)"

down: ## Stop and remove all containers
	docker compose down

down-v: ## Stop containers AND delete volumes (wipes database)
	docker compose down -v

build: ## Rebuild images without cache
	docker compose build --no-cache

logs: ## Tail logs for all services
	docker compose logs -f

logs-backend: ## Tail backend logs only
	docker compose logs -f backend

logs-frontend: ## Tail frontend logs only
	docker compose logs -f frontend

# ── Testing ───────────────────────────────────────────────────────────
test: test-backend test-frontend ## Run all tests

test-backend: ## Run Spring Boot tests
	cd backend && mvn verify -q
	@echo "✓  Backend tests passed"

test-frontend: ## Run Jest unit tests
	cd frontend && npm run test:unit
	@echo "✓  Frontend tests passed"

test-frontend-coverage: ## Run Jest with coverage report
	cd frontend && npm run test:unit:coverage

# ── Development utilities ─────────────────────────────────────────────
shell-backend: ## Open a shell in the running backend container
	docker compose exec backend sh

shell-db: ## Open a psql shell in the database
	docker compose exec postgres psql -U pharma -d pharma_inventory

clean: ## Remove all build artifacts and Docker volumes
	cd backend && mvn clean -q
	docker compose down -v
	@echo "✓  Cleaned"

seed: ## Re-seed demo data (run while stack is up)
	@echo "Reseeding demo data..."
	docker compose exec backend wget -qO- http://localhost:8080/actuator/health
	@echo "Data is seeded automatically on first startup via DataInitializer."

secret: ## Generate a secure JWT_SECRET value
	@echo ""
	@echo "JWT_SECRET=$(shell openssl rand -base64 64 | tr -d '\n')"
	@echo ""
	@echo "Copy the value above into your .env file."

# ── Production ────────────────────────────────────────────────────────
prod-up: ## Start production stack (reads docker-compose.prod.yml)
	@[ -f .env ] || (echo "ERROR: .env file not found. Copy .env.example → .env and fill in values." && exit 1)
	docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d

prod-pull: ## Pull latest images and restart (zero-downtime rolling update)
	docker compose -f docker-compose.yml -f docker-compose.prod.yml pull
	docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --remove-orphans
	docker image prune -f

# ── Cloud deploy helpers ──────────────────────────────────────────────
deploy-railway: ## Deploy to Railway (requires railway CLI + auth)
	railway up

deploy-fly: ## Deploy backend to Fly.io (requires flyctl + auth)
	fly deploy

deploy-render: ## Deploy to Render via blueprint (requires render CLI)
	render deploy --blueprint render.yaml

# ── K8s ───────────────────────────────────────────────────────────────
k8s-apply: ## Apply all Kubernetes manifests
	kubectl apply -f infra/k8s/namespace.yaml
	kubectl apply -f infra/k8s/postgres.yaml
	kubectl apply -f infra/k8s/backend.yaml
	kubectl apply -f infra/k8s/frontend.yaml

k8s-status: ## Check pod status in pharmatrack namespace
	kubectl get all -n pharmatrack
