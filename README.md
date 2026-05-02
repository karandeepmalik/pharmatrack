# PharmaTrack

Pharmaceutical inventory management system  React 18 frontend + Spring Boot 3.2 backend + PostgreSQL 15.

## Structure
```
 frontend/      # React 18 app (Nginx, served on port 8080)
 backend/       # Spring Boot 3.2 API
 e2e/           # End-to-end auth tests (Node.js, no deps)
```

## Deployed URLs
- **Frontend**: https://pharmatrack-frontend-558147403401.asia-south1.run.app
- **Backend**: https://pharmatrack-backend-xhlza2c2ua-el.a.run.app

## Demo Credentials
| Role  | Username  | Password   |
|-------|-----------|------------|
| Admin | admin     | Admin@123  |
| User  | john.doe  | User@123   |
| User  | jane.smith| User@123   |

## Running E2E Tests
```bash
cd e2e
node auth.test.js   # no npm install needed  zero dependencies
```

## Local Development
```bash
# Backend
cd backend && ./mvnw spring-boot:run

# Frontend
cd frontend && npm install && npm start
```

## Copyright
Copyright (c) 2024 Karandeep Malik. All rights reserved.
