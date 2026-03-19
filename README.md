# Multi-Tenant Expense Tracker with Approval Workflows

A full-stack expense management system supporting multi-tenant organizations with role-based access control, a state-machine-driven approval workflow, receipt uploads, and admin analytics dashboards.

## Architecture Overview

The system follows a monolithic full-stack architecture with clear internal boundaries:

- **Backend:** Spring Boot 3.x REST API with layered architecture (Controller / Service / Repository)
- **Frontend:** React 18 SPA with TypeScript, Vite, and Tailwind CSS
- **Database:** PostgreSQL 15 with Flyway migrations
- **Multi-Tenancy:** Shared database with `tenant_id` column on all tenant-scoped tables
- **Auth:** JWT access tokens (15 min) + refresh token rotation with reuse detection
- **Rate Limiting:** Token-bucket per tenant, per-IP for auth endpoints

For detailed architecture documentation, schema design, and trade-off analysis, see [DESIGN.md](../DESIGN.md).

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 17, Spring Boot 3.3, Spring Security 6, Spring Data JPA, Hibernate |
| Frontend | React 18, TypeScript 5, Vite 5, React Router 6, Axios, Recharts, Tailwind CSS 3 |
| Database | PostgreSQL 15, Flyway migrations |
| Auth | JJWT 0.12, BCrypt |
| Build | Maven 3.9+ (backend), npm (frontend) |
| Infrastructure | Docker, Docker Compose, Railway |

## Quick Start with Docker Compose

The fastest way to run the entire stack:

```bash
docker-compose up --build
```

Once all services are healthy:

- **Backend API:** http://localhost:8081/api/v1/
- **Frontend UI:** http://localhost:3000

The database is automatically provisioned with schema migrations and demo seed data on startup.

## Manual Development Setup

### Prerequisites

- Java 17+
- Node.js 18+
- PostgreSQL 15 (running on port 5433, or adjust config)
- Maven 3.8+

### Database

Start PostgreSQL and create the database:

```bash
psql -U postgres -c "CREATE USER expense_user WITH PASSWORD 'expense_pass';"
psql -U postgres -c "CREATE DATABASE expense_tracker OWNER expense_user;"
```

### Backend

```bash
cd expense-tracker-api
mvn spring-boot:run
```

The backend starts on http://localhost:8081. Flyway migrations run automatically on startup.

### Frontend

```bash
cd expense-tracker-ui
npm install
npm run dev
```

The frontend starts on http://localhost:5173 with API requests proxied to `localhost:8081`.

## Demo Credentials

The application is pre-seeded with demo data across two organizations for testing.
All accounts use the password: **Password1**

### Acme Corp (primary demo org)

| Role | Email | Name |
|------|-------|------|
| Admin | admin@acme.com | Alice Admin |
| Manager | manager@acme.com | Mike Manager |
| Employee | john@acme.com | John Doe |
| Employee | jane@acme.com | Jane Smith |

### Globex Inc (cross-tenant isolation testing)

| Role | Email | Name |
|------|-------|------|
| Admin | admin@globex.com | Gary Admin |
| Manager | manager@globex.com | Sarah Manager |
| Employee | bob@globex.com | Bob Wilson |

Use both organizations to verify that tenant data isolation is enforced correctly.

The Acme Corp organization includes 10 demo expenses in various states (DRAFT, SUBMITTED, APPROVED, REJECTED, CANCELLED) with full audit logs spread across Jan-Mar 2026 for realistic analytics charts.

## Deploy to Railway

### Prerequisites

- A [Railway](https://railway.app) account
- Code pushed to a GitHub repository

### Steps

1. **Create a new Railway project** and add a **PostgreSQL** plugin. Railway will automatically provision the database and set environment variables.

2. **Add the backend service:**
   - Click "New Service" > "GitHub Repo" and select your repository
   - Set the **Root Directory** to `expense-tracker-api`
   - Add the following environment variables:
     ```
     DATABASE_URL=<from Railway PostgreSQL plugin, use JDBC format: jdbc:postgresql://...>
     DATABASE_USERNAME=<from Railway PostgreSQL plugin>
     DATABASE_PASSWORD=<from Railway PostgreSQL plugin>
     JWT_SECRET=<generate a secure random string, at least 32 characters>
     SPRING_PROFILES_ACTIVE=railway
     ```

3. **Add the frontend service:**
   - Click "New Service" > "GitHub Repo" and select your repository
   - Set the **Root Directory** to `expense-tracker-ui`
   - Add the following environment variables:
     ```
     BACKEND_URL=http://<backend-service-internal-hostname>:8080
     PORT=80
     ```
   - Use the backend service's **internal** Railway hostname for `BACKEND_URL` (found in the backend service's Settings > Networking)

4. **Generate a domain** for the frontend service under Settings > Networking > Public Networking.

5. **Deploy.** Railway will automatically build and deploy both services. The backend runs Flyway migrations on first start to create the schema and seed demo data.

### Environment Variables Reference

See `.env.example` for a complete list of configurable environment variables.

## API Endpoints

All endpoints are prefixed with `/api/v1/`. Authenticated endpoints require `Authorization: Bearer <jwt>`.

### Authentication (Public)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/auth/register` | Register a new user in an existing org |
| POST | `/auth/login` | Authenticate with email + password |
| POST | `/auth/refresh` | Exchange refresh token for new token pair |
| POST | `/auth/logout` | Revoke the current refresh token |

### Expenses

| Method | Endpoint | Roles | Description |
|--------|----------|-------|-------------|
| POST | `/expenses` | Employee, Manager | Create a new expense (DRAFT) |
| GET | `/expenses` | All | List current user's expenses (filtered, paginated) |
| GET | `/expenses/{id}` | All | Get expense detail with receipts and audit trail |
| PUT | `/expenses/{id}` | Employee, Manager | Update a DRAFT or REJECTED expense |
| DELETE | `/expenses/{id}` | Employee, Manager | Delete a DRAFT expense |
| POST | `/expenses/{id}/submit` | Employee, Manager | Submit expense for approval |
| POST | `/expenses/{id}/approve` | Manager, Admin | Approve a submitted expense |
| POST | `/expenses/{id}/reject` | Manager, Admin | Reject a submitted expense (comment required) |

### Receipts

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/expenses/{id}/receipts` | Upload a receipt (JPEG, PNG, PDF; max 5 MB) |
| GET | `/expenses/{id}/receipts/{receiptId}` | Download/stream a receipt file |
| DELETE | `/expenses/{id}/receipts/{receiptId}` | Delete a receipt (DRAFT expenses only) |

### Approvals

| Method | Endpoint | Roles | Description |
|--------|----------|-------|-------------|
| GET | `/approvals/pending` | Manager, Admin | List pending expenses for approval |
| POST | `/approvals/bulk` | Manager, Admin | Bulk approve or reject (max 50 per request) |

### Categories

| Method | Endpoint | Roles | Description |
|--------|----------|-------|-------------|
| GET | `/categories` | All | List active categories |
| POST | `/categories` | Admin | Create a category |
| PUT | `/categories/{id}` | Admin | Rename a category |
| DELETE | `/categories/{id}` | Admin | Deactivate a category |

### Users (Admin Only)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/users` | List org users (filter by role, search by name/email) |
| PUT | `/users/{id}/role` | Change a user's role |
| PUT | `/users/{id}/manager` | Assign a manager to a user |
| PUT | `/users/{id}/deactivate` | Deactivate a user |

### Analytics

| Method | Endpoint | Roles | Description |
|--------|----------|-------|-------------|
| GET | `/analytics/summary` | Admin | Org-wide summary stats |
| GET | `/analytics/by-category` | Admin | Spend breakdown by category |
| GET | `/analytics/by-month` | Admin | Monthly spending trends |
| GET | `/analytics/by-team` | Admin | Spend breakdown by team |
| GET | `/analytics/my-team` | Manager | Category breakdown for own team |

## Running Tests

### Backend

```bash
cd expense-tracker-api
mvn test
```

Integration tests use Testcontainers to run against a real PostgreSQL instance, ensuring Flyway migrations and queries work against the actual database engine.

### Frontend

```bash
cd expense-tracker-ui
npm run lint
```

## Project Structure

```
Problem_1/
├── docker-compose.yml              # Full-stack orchestration
├── railway.toml                    # Railway monorepo config
├── .env.example                    # Environment variable reference
├── README.md                       # This file
├── AI_USAGE.md                     # AI tool usage documentation
│
├── expense-tracker-api/            # Spring Boot backend
│   ├── Dockerfile                  # Multi-stage build
│   ├── railway.toml                # Railway backend service config
│   ├── pom.xml                     # Maven config (Spring Boot 3.3, Java 17)
│   └── src/
│       ├── main/
│       │   ├── java/com/expensetracker/
│       │   │   ├── config/         # Security config, CORS, etc.
│       │   │   ├── controller/     # REST controllers
│       │   │   │   ├── AuthController.java
│       │   │   │   ├── ExpenseController.java
│       │   │   │   ├── ApprovalController.java
│       │   │   │   ├── ReceiptController.java
│       │   │   │   ├── CategoryController.java
│       │   │   │   ├── UserController.java
│       │   │   │   └── AnalyticsController.java
│       │   │   ├── dto/            # Request/Response DTOs
│       │   │   │   ├── request/    # Incoming payloads
│       │   │   │   └── response/   # Outgoing payloads
│       │   │   ├── exception/      # Custom exceptions + global handler
│       │   │   ├── filter/         # JWT auth, tenant context, rate limit filters
│       │   │   ├── model/          # JPA entities
│       │   │   │   └── enums/      # ExpenseStatus, Role
│       │   │   ├── ratelimit/      # Token-bucket rate limiter
│       │   │   ├── repository/     # Spring Data JPA repositories
│       │   │   ├── security/       # JWT utilities, SecurityUtils
│       │   │   └── service/        # Business logic layer
│       │   │       └── impl/       # Service implementations
│       │   └── resources/
│       │       ├── application.yml           # Default config
│       │       ├── application-dev.yml       # Dev profile (verbose logging)
│       │       ├── application-docker.yml    # Docker profile (service hostnames)
│       │       └── application-railway.yml   # Railway production profile
│       └── test/                   # JUnit 5 + Testcontainers tests
│
├── expense-tracker-ui/             # React frontend
│   ├── Dockerfile                  # Multi-stage build (Node + Nginx)
│   ├── railway.toml                # Railway frontend service config
│   ├── nginx.conf                  # Nginx config template (envsubst)
│   ├── .env.production             # Production env vars for Vite
│   ├── package.json                # React 18, Vite, Tailwind CSS
│   ├── vite.config.ts              # Dev server with API proxy
│   └── src/
│       ├── api/                    # Axios instance + API modules
│       ├── components/             # Reusable UI components
│       │   ├── approvals/          # ApprovalTable, BulkActions, RejectModal
│       │   ├── auth/               # ProtectedRoute, RoleGuard
│       │   ├── common/             # LoadingSpinner, Pagination, Toast
│       │   ├── dashboard/          # Charts, SummaryCards, DateRangePicker
│       │   ├── expenses/           # ExpenseForm, ExpenseTable, ReceiptUpload
│       │   ├── layout/             # AppLayout, Header, Sidebar
│       │   └── users/              # UserTable, RoleChangeModal
│       ├── context/                # AuthContext (token lifecycle)
│       ├── hooks/                  # Custom hooks (useExpenses, useAuth, etc.)
│       ├── pages/                  # Route-level page components
│       ├── types/                  # TypeScript type definitions
│       └── utils/                  # Formatters (currency, date)
```

## Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Multi-tenancy model | Shared DB with `tenant_id` column | Simplest to implement and operate; sufficient for MVP scale |
| Approval workflow | State machine (DRAFT / SUBMITTED / APPROVED / REJECTED / CANCELLED) | Enforced in service layer; prevents invalid transitions with 409 responses |
| Team structure | Flat `manager_id` FK on users | Simple one-manager-per-employee model; avoids hierarchy complexity |
| Password storage | BCrypt with cost factor 12 | Industry standard; adaptive cost |
| JWT strategy | Short-lived access (15 min) + refresh rotation | Balances security (limits token theft window) with UX (transparent refresh) |
| File storage | Local filesystem with API-gated access | Abstracted behind `FileStorageService` for future S3 migration |
| Rate limiting | Token-bucket per tenant + per-IP for auth | Prevents noisy-neighbor and brute-force attacks |
| Frontend styling | Tailwind CSS (utility-first) | Full design control; small production bundle; no opinionated component library |
| Deployment | Railway with Docker | Simple PaaS deployment with separate backend/frontend services and managed PostgreSQL |

For comprehensive design rationale, database schema, security architecture, and trade-off analysis, see [DESIGN.md](../DESIGN.md).
