# DESIGN.md -- Multi-Tenant Expense Tracker with Approval Workflows

**Author:** Karthik Raj
**Date:** 2026-03-18
**Status:** Approved for Implementation
**Related Docs:** PM_ANALYSIS_Problem1.md, TECHNICAL_STORIES_Problem1.md

---

## Table of Contents

1. [High-Level Architecture](#1-high-level-architecture)
2. [Tech Stack & Justification](#2-tech-stack--justification)
3. [Database Schema Design](#3-database-schema-design)
4. [API Design](#4-api-design)
5. [Authentication & Security Architecture](#5-authentication--security-architecture)
6. [Approval Workflow State Machine](#6-approval-workflow-state-machine)
7. [Multi-Tenancy Architecture](#7-multi-tenancy-architecture)
8. [File Storage Architecture](#8-file-storage-architecture)
9. [Rate Limiting Design](#9-rate-limiting-design)
10. [Frontend Architecture](#10-frontend-architecture)
11. [Project Structure](#11-project-structure)
12. [Trade-offs & Alternatives](#12-trade-offs--alternatives)
13. [What I'd Improve With More Time](#13-what-id-improve-with-more-time)

---

## 1. High-Level Architecture

### System Overview

The system follows a **monolithic full-stack architecture** with clear internal boundaries. A single Spring Boot backend serves both the REST API and static frontend assets. PostgreSQL handles all persistent state, and the local filesystem stores uploaded receipt files behind an authenticated API layer.

```
                          +--------------------------------------------------+
                          |                   CLIENTS                         |
                          |  +--------------------------------------------+  |
                          |  |       React SPA (TypeScript + Vite)         |  |
                          |  |                                            |  |
                          |  |  +----------+  +--------+  +----------+   |  |
                          |  |  | Auth Ctx  |  | React  |  | Axios    |   |  |
                          |  |  | Provider  |  | Router |  | Instance |   |  |
                          |  |  +----------+  +--------+  +----------+   |  |
                          |  +--------------------------------------------+  |
                          +----------------------|---------------------------+
                                                 | HTTPS (REST/JSON)
                                                 v
+------------------------------------------------------------------------------------+
|                           SPRING BOOT APPLICATION                                  |
|                                                                                    |
|  +------------------+  +-------------------+  +------------------+                 |
|  | Rate Limit       |  | JWT Auth          |  | Tenant Context   |                 |
|  | Filter           |->| Filter            |->| Filter           |                 |
|  | (Token Bucket)   |  | (Validate+Parse)  |  | (Set ThreadLocal)|                 |
|  +------------------+  +-------------------+  +------------------+                 |
|           |                                            |                           |
|           v                                            v                           |
|  +---------------------------------------------------------------------+           |
|  |                     SPRING SECURITY FILTER CHAIN                     |           |
|  |  - CORS Filter                                                       |           |
|  |  - RateLimitFilter (pre-auth for /auth/*, post-auth for tenant)      |           |
|  |  - JwtAuthenticationFilter                                           |           |
|  |  - TenantContextFilter                                               |           |
|  |  - UsernamePasswordAuthenticationFilter (disabled)                    |           |
|  |  - ExceptionTranslationFilter                                        |           |
|  |  - AuthorizationFilter (@PreAuthorize)                               |           |
|  +---------------------------------------------------------------------+           |
|           |                                                                        |
|           v                                                                        |
|  +---------------------------------------------------------------------+           |
|  |                       REST CONTROLLERS                               |           |
|  |  AuthController | ExpenseController | ApprovalController             |           |
|  |  UserController | CategoryController | AnalyticsController           |           |
|  +---------------------------------------------------------------------+           |
|           |                                                                        |
|           v                                                                        |
|  +---------------------------------------------------------------------+           |
|  |                       SERVICE LAYER                                  |           |
|  |  AuthService | ExpenseService | ApprovalService                     |           |
|  |  UserService | CategoryService | AnalyticsService                   |           |
|  |  FileStorageService | TenantContext (ThreadLocal)                    |           |
|  +---------------------------------------------------------------------+           |
|           |                          |                                             |
|           v                          v                                             |
|  +-------------------+    +--------------------+                                   |
|  | REPOSITORY LAYER  |    | FILE STORAGE       |                                   |
|  | (Spring Data JPA) |    | (Local Filesystem) |                                   |
|  | Tenant-scoped     |    | uploads/{tenant}/  |                                   |
|  | queries           |    | {expense}/{file}   |                                   |
|  +-------------------+    +--------------------+                                   |
|           |                                                                        |
+-----------|------------------------------------------------------------------------+
            |
            v
   +------------------+
   |   PostgreSQL      |
   |                   |
   |  Shared DB with   |
   |  tenant_id on     |
   |  every table      |
   +------------------+
```

### Component Breakdown

| Component | Technology | Responsibility |
|-----------|-----------|----------------|
| **Frontend SPA** | React 18, TypeScript, Vite | User interface, form management, data visualization, auth token lifecycle |
| **API Gateway Layer** | Spring Security Filters | Rate limiting, JWT validation, tenant context injection, CORS |
| **REST Controllers** | Spring MVC | Request routing, input validation, HTTP semantics |
| **Service Layer** | Spring Services | Business logic, state machine enforcement, authorization rules |
| **Repository Layer** | Spring Data JPA | Tenant-scoped data access, query generation |
| **Database** | PostgreSQL 15+ | Persistent storage with tenant isolation via column-level filtering |
| **File Storage** | Local Filesystem | Receipt file persistence with structured directory layout |

### Request Flow (End-to-End)

A typical authenticated request follows this path:

```
1. Browser sends GET /api/v1/expenses?status=SUBMITTED
   Header: Authorization: Bearer <access_token>

2. CORS Filter: validates Origin header

3. RateLimitFilter:
   - For /auth/* endpoints: check per-IP bucket
   - For other endpoints: passes through (tenant not yet known)

4. JwtAuthenticationFilter:
   - Extracts token from Authorization header
   - Validates signature, expiry, structure
   - Extracts claims: sub (userId), tenantId, role
   - Creates Authentication object, sets SecurityContext
   - FAILS: returns 401 JSON response, short-circuits chain

5. TenantContextFilter:
   - Reads tenantId from SecurityContext
   - Sets TenantContext.setCurrentTenant(tenantId)
   - (ThreadLocal, cleared in finally block)

6. Post-Auth RateLimitFilter:
   - Checks per-tenant bucket using TenantContext.getCurrentTenant()
   - FAILS: returns 429 with Retry-After header

7. AuthorizationFilter:
   - Evaluates @PreAuthorize("hasRole('MANAGER')") on controller method
   - FAILS: returns 403

8. ExpenseController.listExpenses():
   - Reads query params, constructs filter/page request
   - Calls expenseService.listExpenses(filter, pageable)

9. ExpenseService:
   - Gets tenantId from TenantContext
   - Gets userId from SecurityContext
   - Calls repository with tenant-scoped query

10. ExpenseRepository (Spring Data JPA):
    - Executes: SELECT * FROM expenses
      WHERE tenant_id = :tenantId AND submitter_id = :userId
      AND status = :status
      ORDER BY created_at DESC
      LIMIT 20 OFFSET 0

11. Response flows back through layers:
    - Service maps entities to DTOs
    - Controller wraps in ResponseEntity with 200 OK
    - Jackson serializes to JSON
    - TenantContextFilter finally block clears ThreadLocal

12. Browser receives JSON response
```

---

## 2. Tech Stack & Justification

### Backend

| Technology | Version | Why This Choice |
|-----------|---------|-----------------|
| **Java** | 17+ | LTS release with modern features (records, sealed classes, pattern matching). Widely adopted in enterprise environments. Required by the assignment. |
| **Spring Boot** | 3.x | De facto standard for Java web applications. Convention-over-configuration accelerates development. Built-in support for security, data access, validation, and testing. Version 3.x requires Java 17+ and is built on Jakarta EE (namespace migration from javax). |
| **Spring Security** | 6.x (via Boot 3.x) | Mature, battle-tested security framework. Provides the filter chain architecture we need for JWT validation, RBAC, and custom filters. Method-level security with `@PreAuthorize` keeps authorization rules close to business logic. |
| **Spring Data JPA** | Via Boot starter | Eliminates boilerplate repository code. Custom `@Query` annotations allow tenant-scoped queries. Derived query methods (e.g., `findByTenantIdAndStatus`) are readable and type-safe. |
| **Hibernate** | 6.x (via JPA starter) | JPA implementation. Provides entity lifecycle management, lazy loading, and `@Filter` for automatic tenant scoping. Understood trade-off: Hibernate's "magic" (N+1 queries, lazy init exceptions) requires discipline--we use `@EntityGraph` and explicit fetch joins where needed. |
| **PostgreSQL** | 15+ | Robust relational database with strong ACID guarantees. Native UUID support (`uuid-ossp`), excellent indexing (B-tree, partial indexes), `DECIMAL` precision for financial data, and `JSONB` if we need schemaless extension points later. Chosen over MySQL for better standards compliance, richer data types, and superior concurrency handling. |
| **Flyway** | Via Boot starter | Schema migration tool with versioned SQL files. Chosen over Liquibase because SQL migrations are more transparent--reviewers can read raw DDL. Version-number ordering (`V1__`, `V2__`) makes the migration sequence explicit. |
| **BCrypt** | Via Spring Security | Password hashing with configurable cost factor (we use 12). Adaptive: cost factor can increase over time as hardware improves. Industry standard--no reason to choose anything else for password hashing. |
| **Maven** | 3.9+ | Build tool. Chosen over Gradle for its declarative XML format, which is easier for reviewers unfamiliar with Groovy/Kotlin DSLs. Spring Initializr defaults to Maven. Trade-off: more verbose than Gradle, but the project is small enough that this does not matter. |
| **JJWT** | 0.12.x | JSON Web Token library. Provides builder/parser API for creating and validating JWTs. Chosen over Spring's built-in OAuth2 resource server because we are implementing custom JWT issuance (not delegating to an external IdP), and JJWT gives us full control over token structure and signing. |

### Frontend

| Technology | Version | Why This Choice |
|-----------|---------|-----------------|
| **React** | 18 | Component-based UI library. Required by the assignment. Version 18 provides concurrent features and automatic batching, though we do not depend on these heavily. |
| **TypeScript** | 5.x | Static typing catches bugs at compile time, provides IDE autocompletion, and serves as living documentation for data shapes. Essential for a project with complex DTOs flowing from backend to frontend. |
| **Vite** | 5.x | Build tool. Chosen over Create React App (deprecated) and Webpack. Near-instant dev server startup via native ESM, fast HMR, and minimal configuration. Produces optimized production bundles. |
| **React Router** | 6.x | Client-side routing. The `<Outlet>` pattern allows nested layouts (e.g., sidebar + content area). `loader`/`action` functions could be used for data fetching but we keep it simple with `useEffect` for transparency. |
| **Axios** | 1.x | HTTP client. Chosen over native `fetch` because Axios provides interceptors (critical for automatic token refresh), automatic JSON parsing, request/response transformation, and better error handling. The interceptor pattern is the cleanest way to implement transparent JWT refresh. |
| **Recharts** | 2.x | Charting library built on D3 and React. Chosen over Chart.js because Recharts uses a declarative React component API (`<BarChart>`, `<LineChart>`) that fits naturally into our component tree. Chart.js uses an imperative canvas API that requires refs and manual lifecycle management in React. Trade-off: Recharts has a larger bundle size, but the DX improvement is worth it for this project's scope. |
| **Tailwind CSS** | 3.x | Utility-first CSS framework. Enables rapid UI development without context-switching to separate CSS files. Produces small production bundles via PurgeCSS. Chosen over Material UI or Ant Design to avoid opinionated component libraries--we want full control over the design and to demonstrate CSS understanding. |

### Infrastructure & Tooling

| Technology | Why |
|-----------|-----|
| **Docker + Docker Compose** | Single-command local setup (`docker-compose up`). Ensures reviewers can run the project without installing Java, Node, or PostgreSQL locally. |
| **JUnit 5 + Mockito** | Standard Java testing stack. `@SpringBootTest` for integration tests, `@WebMvcTest` for controller slice tests, Mockito for unit test isolation. |
| **Testcontainers** | Runs PostgreSQL in Docker during integration tests. Tests against real database behavior (not H2 quirks). Ensures Flyway migrations work against actual PostgreSQL. |
| **React Testing Library** | Tests components from the user's perspective (by text, role, label) rather than implementation details. Encourages accessible markup. |

---

## 3. Database Schema Design

### Multi-Tenancy Pattern

**Chosen: Shared database, shared schema, `tenant_id` column on every tenant-scoped table.**

This is the simplest multi-tenancy pattern and appropriate for the MVP scope. Every row in a tenant-scoped table carries a `tenant_id` foreign key to the `organizations` table. All queries include a `WHERE tenant_id = ?` predicate, enforced at the repository layer.

See [Section 7](#7-multi-tenancy-architecture) for a deeper discussion of why this pattern was chosen over alternatives.

### Entity Relationship Diagram

```
+----------------------------+
|       organizations        |
+----------------------------+
| id          UUID       PK  |
| name        VARCHAR(100)   |
| slug        VARCHAR(50) UQ |
| currency    VARCHAR(3)     |
| is_active   BOOLEAN        |
| created_at  TIMESTAMP      |
| updated_at  TIMESTAMP      |
+----------------------------+
        |
        | 1:N
        |
+----------------------------+       +----------------------------+
|          users             |       |     refresh_tokens         |
+----------------------------+       +----------------------------+
| id          UUID       PK  |  1:N  | id          UUID       PK  |
| tenant_id   UUID       FK --|------| user_id     UUID       FK  |
| email       VARCHAR(255) UQ|       | token_hash  VARCHAR(255)   |
| password_hash VARCHAR(255) |       | expires_at  TIMESTAMP      |
| first_name  VARCHAR(100)   |       | is_revoked  BOOLEAN        |
| last_name   VARCHAR(100)   |       | replaced_by UUID       FK  |
| role        VARCHAR(20)    |       | created_at  TIMESTAMP      |
| manager_id  UUID       FK  |--+    +----------------------------+
| is_active   BOOLEAN        |  |
| failed_login_attempts INT  |  |
| locked_until TIMESTAMP     |  |
| created_at  TIMESTAMP      |  |
| updated_at  TIMESTAMP      |  |
+----------------------------+  |
        |                       |
        | self-referential      |
        +-----------------------+
        |
        | 1:N (as submitter)
        | 1:N (as manager/approver)
        |
+----------------------------+       +----------------------------+
|        expenses            |       |    expense_categories      |
+----------------------------+       +----------------------------+
| id          UUID       PK  |       | id          UUID       PK  |
| tenant_id   UUID       FK  |  N:1  | tenant_id   UUID       FK  |
| submitter_id UUID      FK --|      | name        VARCHAR(100)   |
| manager_id  UUID       FK  |      | is_active   BOOLEAN        |
| amount      DECIMAL(12,2)  |      | created_at  TIMESTAMP      |
| currency    VARCHAR(3)     |      +----------------------------+
| category_id UUID       FK --|----------^
| merchant_name VARCHAR(200) |
| expense_date DATE          |
| notes       TEXT           |
| status      VARCHAR(20)    |
| rejection_comment TEXT     |
| approved_by_id UUID    FK  |
| approved_at TIMESTAMP      |
| created_at  TIMESTAMP      |
| updated_at  TIMESTAMP      |
+----------------------------+
        |
        | 1:N                          1:N
        |                               |
+----------------------------+  +----------------------------+
|    expense_receipts        |  |   expense_audit_log        |
+----------------------------+  +----------------------------+
| id          UUID       PK  |  | id          UUID       PK  |
| expense_id  UUID       FK  |  | expense_id  UUID       FK  |
| file_name   VARCHAR(255)   |  | action      VARCHAR(20)    |
| file_path   VARCHAR(500)   |  | performed_by UUID     FK   |
| content_type VARCHAR(100)  |  | comment     TEXT           |
| file_size   BIGINT         |  | old_status  VARCHAR(20)    |
| created_at  TIMESTAMP      |  | new_status  VARCHAR(20)    |
+----------------------------+  | created_at  TIMESTAMP      |
                                +----------------------------+
```

### Table Definitions (Detailed)

#### organizations

```sql
CREATE TABLE organizations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(50)  NOT NULL UNIQUE,
    currency    VARCHAR(3)   NOT NULL DEFAULT 'USD',
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
```

**Notes:**
- `slug` provides human-readable identification (e.g., for invite URLs: `/join/acme-corp`)
- `currency` is fixed at org creation. All expenses in this org use this currency.
- Not tenant-scoped (this IS the tenant table)

#### users

```sql
CREATE TABLE users (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID         NOT NULL REFERENCES organizations(id),
    email                 VARCHAR(255) NOT NULL UNIQUE,
    password_hash         VARCHAR(255) NOT NULL,
    first_name            VARCHAR(100) NOT NULL,
    last_name             VARCHAR(100) NOT NULL,
    role                  VARCHAR(20)  NOT NULL DEFAULT 'EMPLOYEE'
                          CHECK (role IN ('EMPLOYEE', 'MANAGER', 'ADMIN')),
    manager_id            UUID         REFERENCES users(id),
    is_active             BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_attempts INTEGER      NOT NULL DEFAULT 0,
    locked_until          TIMESTAMP,
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_tenant_id ON users(tenant_id);
CREATE INDEX idx_users_tenant_role ON users(tenant_id, role);
CREATE INDEX idx_users_tenant_manager ON users(tenant_id, manager_id);
CREATE INDEX idx_users_email ON users(email);
```

**Notes:**
- `email` is globally unique (not just per-tenant) to prevent confusion during login
- `manager_id` is a self-referential FK. Must point to a MANAGER or ADMIN in the same tenant (enforced at application level)
- `role` uses a VARCHAR with CHECK constraint rather than a PostgreSQL ENUM type. Reason: PostgreSQL ENUMs require `ALTER TYPE` to add values, which is cumbersome in migrations. VARCHAR with CHECK is easier to evolve.
- `locked_until`: set to `NOW() + 15 minutes` after 5 failed login attempts. Login logic checks `locked_until > NOW()`.

#### refresh_tokens

```sql
CREATE TABLE refresh_tokens (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID         NOT NULL REFERENCES users(id),
    token_hash     VARCHAR(255) NOT NULL,
    expires_at     TIMESTAMP    NOT NULL,
    is_revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    replaced_by_id UUID         REFERENCES refresh_tokens(id),
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens(token_hash);
```

**Notes:**
- We store a SHA-256 hash of the refresh token, not the raw token. The raw token is returned to the client once and never stored server-side.
- `replaced_by_id` creates a linked list (token family chain). When reuse is detected, we walk the chain and revoke all tokens.
- No `tenant_id` on this table--we join through `user_id` to get tenant context. This table is an auth infrastructure table, not a business data table.

#### expense_categories

```sql
CREATE TABLE expense_categories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL REFERENCES organizations(id),
    name        VARCHAR(100) NOT NULL,
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, name)
);

CREATE INDEX idx_categories_tenant ON expense_categories(tenant_id);
```

**Notes:**
- Unique constraint on `(tenant_id, name)` prevents duplicate category names within an org
- `is_active` supports soft-delete. Deactivated categories stop appearing in dropdowns but remain on existing expenses.
- Seeded with defaults on org creation: Travel, Meals, Office Supplies, Software, Equipment, Other

#### expenses

```sql
CREATE TABLE expenses (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID          NOT NULL REFERENCES organizations(id),
    submitter_id     UUID          NOT NULL REFERENCES users(id),
    manager_id       UUID          REFERENCES users(id),
    amount           DECIMAL(12,2),
    currency         VARCHAR(3),
    category_id      UUID          REFERENCES expense_categories(id),
    merchant_name    VARCHAR(200),
    expense_date     DATE,
    notes            TEXT,
    status           VARCHAR(20)   NOT NULL DEFAULT 'DRAFT'
                     CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','CANCELLED')),
    rejection_comment TEXT,
    approved_by_id   UUID          REFERENCES users(id),
    approved_at      TIMESTAMP,
    created_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_expenses_tenant_submitter ON expenses(tenant_id, submitter_id);
CREATE INDEX idx_expenses_tenant_manager_status ON expenses(tenant_id, manager_id, status);
CREATE INDEX idx_expenses_tenant_status ON expenses(tenant_id, status);
CREATE INDEX idx_expenses_tenant_category ON expenses(tenant_id, category_id);
CREATE INDEX idx_expenses_tenant_date ON expenses(tenant_id, expense_date);
```

**Notes:**
- `amount`, `category_id`, `expense_date` are nullable because DRAFT expenses can be partially filled
- `manager_id` on the expense is a snapshot of the submitter's manager at submission time. This ensures that if the employee is later reassigned, the original approver remains correct. New submissions go to the new manager.
- `DECIMAL(12,2)` supports amounts up to 9,999,999,999.99--sufficient for any reasonable expense
- `currency` denormalized from org for query convenience (avoids joining org table on every expense read)

#### expense_receipts

```sql
CREATE TABLE expense_receipts (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expense_id   UUID         NOT NULL REFERENCES expenses(id),
    file_name    VARCHAR(255) NOT NULL,
    file_path    VARCHAR(500) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size    BIGINT       NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_receipts_expense ON expense_receipts(expense_id);
```

**Notes:**
- No `tenant_id` here--tenant context is inferred through the expense. This is acceptable because receipts are always accessed through the expense (not queried independently).
- `file_path` stores the relative path within the uploads directory. Never exposed to clients.

#### expense_audit_log

```sql
CREATE TABLE expense_audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expense_id      UUID        NOT NULL REFERENCES expenses(id),
    action          VARCHAR(20) NOT NULL
                    CHECK (action IN ('CREATED','SUBMITTED','APPROVED','REJECTED',
                                      'RESUBMITTED','CANCELLED','REASSIGNED')),
    performed_by_id UUID        NOT NULL REFERENCES users(id),
    comment         TEXT,
    old_status      VARCHAR(20),
    new_status      VARCHAR(20),
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_expense ON expense_audit_log(expense_id);
CREATE INDEX idx_audit_created ON expense_audit_log(created_at);
```

**Notes:**
- Append-only table. No UPDATE or DELETE operations.
- `performed_by_id` records who triggered the transition (employee, manager, admin, or SYSTEM for automated transitions)
- `old_status` and `new_status` are denormalized for quick reading without needing to reconstruct from sequence

### Indexing Strategy

The indexing strategy is driven by the query patterns identified from the API design:

| Query Pattern | Index | Justification |
|--------------|-------|---------------|
| Employee lists own expenses | `(tenant_id, submitter_id)` | Every "my expenses" query filters by both |
| Manager views pending approvals | `(tenant_id, manager_id, status)` | The most performance-critical query--used frequently by managers. Composite index avoids table scan. |
| Admin filters by status | `(tenant_id, status)` | Dashboard summary queries count by status |
| Analytics by category | `(tenant_id, category_id)` | Category breakdown queries |
| Analytics by date range | `(tenant_id, expense_date)` | Monthly trend queries |
| User login | `(email)` | Login query looks up by email (globally unique) |
| Tenant-scoped user queries | `(tenant_id, role)`, `(tenant_id, manager_id)` | Admin user management views |

**Why not more indexes?** Every index has a write-time cost. For an MVP with moderate data volumes, these indexes cover the hot paths. We can add more based on query profiling in production.

**Partial indexes considered but deferred:** A partial index like `CREATE INDEX ... ON expenses(tenant_id, manager_id) WHERE status = 'SUBMITTED'` would optimize the pending-approvals query further, but PostgreSQL handles this well enough with the composite index at MVP scale.

---

## 4. API Design

### Conventions

- **Base Path:** `/api/v1/`
- **Authentication:** All endpoints require `Authorization: Bearer <jwt>` unless marked PUBLIC
- **Pagination:** `page` (0-indexed), `size` (default 20, max 100). Response wrapper: `{ content: [], page, size, totalElements, totalPages }`
- **Dates:** ISO 8601 format (`2026-03-18`)
- **IDs:** UUID v4, represented as strings
- **Timestamps:** UTC, ISO 8601 with timezone (`2026-03-18T14:30:00Z`)
- **Sorting:** `sort` parameter, format `field,direction` (e.g., `sort=createdAt,desc`)

### Error Response Format

All errors return a consistent JSON structure:

```json
{
  "error": "Human-readable error message",
  "code": "MACHINE_READABLE_CODE",
  "details": {
    "field": "amount",
    "constraint": "must be greater than 0"
  },
  "timestamp": "2026-03-18T14:30:00Z",
  "path": "/api/v1/expenses"
}
```

Validation errors include a `fieldErrors` array:

```json
{
  "error": "Validation failed",
  "code": "VALIDATION_ERROR",
  "fieldErrors": [
    { "field": "amount", "message": "must be greater than 0" },
    { "field": "categoryId", "message": "must not be null" }
  ]
}
```

### HTTP Status Code Conventions

| Code | Meaning | When Used |
|------|---------|-----------|
| 200 | OK | Successful GET, PUT, action endpoints |
| 201 | Created | Successful POST that creates a resource |
| 204 | No Content | Successful DELETE |
| 400 | Bad Request | Validation errors, malformed input |
| 401 | Unauthorized | Missing, expired, or invalid JWT |
| 403 | Forbidden | Valid JWT but insufficient role/permissions |
| 404 | Not Found | Resource not found OR tenant-isolated resource not visible |
| 409 | Conflict | Invalid state transition, duplicate resource, precondition violation |
| 413 | Payload Too Large | File upload exceeds 5MB |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Unexpected server errors |

### Endpoint Reference

---

#### Authentication Endpoints (PUBLIC)

##### POST /api/v1/auth/register

Register a new user and join an existing organization.

```
Request:
{
  "email": "john@example.com",
  "password": "SecurePass1",
  "firstName": "John",
  "lastName": "Doe",
  "organizationId": "uuid"
}

Response (201):
{
  "accessToken": "eyJhbG...",
  "refreshToken": "dGhpcyBpcyBh...",
  "user": {
    "id": "uuid",
    "email": "john@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "role": "EMPLOYEE",
    "organizationId": "uuid",
    "organizationName": "Acme Corp"
  }
}

Errors:
  409 - Email already exists
  400 - Validation errors (password policy, missing fields)
  400 - Invalid or inactive organizationId
```

##### POST /api/v1/auth/login

Authenticate with email and password.

```
Request:
{
  "email": "john@example.com",
  "password": "SecurePass1"
}

Response (200):
{
  "accessToken": "eyJhbG...",
  "refreshToken": "dGhpcyBpcyBh...",
  "user": { ... }  // same shape as register response
}

Errors:
  401 - Invalid email or password (generic message)
  429 - Account locked (includes Retry-After header)
```

##### POST /api/v1/auth/refresh

Exchange a refresh token for new access + refresh tokens.

```
Request:
{
  "refreshToken": "dGhpcyBpcyBh..."
}

Response (200):
{
  "accessToken": "eyJhbG...(new)...",
  "refreshToken": "bmV3IHJlZnJl...(new)..."
}

Errors:
  401 - Refresh token expired, revoked, or not found
  401 - Reuse detected (all tokens in family revoked, user must re-login)
```

##### POST /api/v1/auth/logout

Revoke the current refresh token.

```
Request:
{
  "refreshToken": "dGhpcyBpcyBh..."
}

Response (200):
{ "message": "Logged out successfully" }
```

---

#### User Management Endpoints (ADMIN only)

##### GET /api/v1/users

List all users in the current organization.

```
Query Params:
  role     - Filter by role (EMPLOYEE, MANAGER, ADMIN)
  search   - Search by name or email (substring match)
  isActive - Filter by active status (default: true)
  page     - Page number (default: 0)
  size     - Page size (default: 20)

Response (200):
{
  "content": [
    {
      "id": "uuid",
      "email": "john@example.com",
      "firstName": "John",
      "lastName": "Doe",
      "role": "EMPLOYEE",
      "managerId": "uuid",
      "managerName": "Jane Manager",
      "isActive": true,
      "createdAt": "2026-03-18T14:30:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 45,
  "totalPages": 3
}
```

##### PUT /api/v1/users/{id}/role

Change a user's role.

```
Request:
{ "role": "MANAGER" }

Response (200):
{ ... updated user object ... }

Errors:
  409 - "Reassign employees before changing this user's role" (if demoting a Manager with reports)
  400 - Invalid role value
  404 - User not found in this tenant
```

##### PUT /api/v1/users/{id}/manager

Assign a manager to a user.

```
Request:
{ "managerId": "uuid" }

Response (200):
{ ... updated user object ... }

Errors:
  400 - managerId must reference a MANAGER or ADMIN in the same tenant
  404 - User or manager not found in this tenant
```

##### PUT /api/v1/users/{id}/deactivate

Deactivate a user.

```
Response (200):
{ ... updated user with isActive: false ... }

Errors:
  409 - "Reassign employees before deactivating" (if Manager with active reports)
  409 - "Cannot deactivate yourself"
  404 - User not found in this tenant
```

---

#### Expense Endpoints

##### POST /api/v1/expenses

Create a new expense (starts as DRAFT). Roles: EMPLOYEE, MANAGER.

```
Request:
{
  "amount": 45.99,          // optional for draft
  "categoryId": "uuid",     // optional for draft
  "merchantName": "Starbucks",
  "expenseDate": "2026-03-15",
  "notes": "Client meeting coffee"
}

Response (201):
{
  "id": "uuid",
  "amount": 45.99,
  "currency": "USD",
  "category": { "id": "uuid", "name": "Meals" },
  "merchantName": "Starbucks",
  "expenseDate": "2026-03-15",
  "notes": "Client meeting coffee",
  "status": "DRAFT",
  "submitter": { "id": "uuid", "name": "John Doe" },
  "receiptCount": 0,
  "createdAt": "2026-03-18T14:30:00Z",
  "updatedAt": "2026-03-18T14:30:00Z"
}
```

##### PUT /api/v1/expenses/{id}

Update an expense. Only DRAFT or REJECTED expenses, only by the submitter.

```
Request: (same shape as POST, all fields optional)
Response (200): (full expense object)

Errors:
  409 - "Expense can only be edited in DRAFT or REJECTED status"
  403 - "Only the submitter can edit this expense"
  404 - Not found (or not in this tenant)
```

##### GET /api/v1/expenses/{id}

Get expense details with receipts and audit trail.

```
Response (200):
{
  "id": "uuid",
  "amount": 45.99,
  "currency": "USD",
  "category": { "id": "uuid", "name": "Meals" },
  "merchantName": "Starbucks",
  "expenseDate": "2026-03-15",
  "notes": "Client meeting coffee",
  "status": "APPROVED",
  "submitter": { "id": "uuid", "name": "John Doe" },
  "manager": { "id": "uuid", "name": "Jane Manager" },
  "approvedBy": { "id": "uuid", "name": "Jane Manager" },
  "approvedAt": "2026-03-17T10:00:00Z",
  "rejectionComment": null,
  "receipts": [
    {
      "id": "uuid",
      "fileName": "receipt.jpg",
      "contentType": "image/jpeg",
      "fileSize": 245000,
      "createdAt": "2026-03-15T14:30:00Z"
    }
  ],
  "auditTrail": [
    {
      "action": "CREATED",
      "performedBy": "John Doe",
      "comment": null,
      "oldStatus": null,
      "newStatus": "DRAFT",
      "createdAt": "2026-03-15T14:30:00Z"
    },
    {
      "action": "SUBMITTED",
      "performedBy": "John Doe",
      "oldStatus": "DRAFT",
      "newStatus": "SUBMITTED",
      "createdAt": "2026-03-15T14:31:00Z"
    }
  ],
  "createdAt": "2026-03-15T14:30:00Z",
  "updatedAt": "2026-03-17T10:00:00Z"
}

Access Rules:
  - Submitter: always (own expenses)
  - Assigned manager: can view team member expenses
  - Admin: can view any expense in the org
```

##### GET /api/v1/expenses

List the current user's expenses with filtering.

```
Query Params:
  status      - Filter by status (DRAFT, SUBMITTED, APPROVED, REJECTED, CANCELLED)
  categoryId  - Filter by category
  fromDate    - Filter expense_date >= fromDate
  toDate      - Filter expense_date <= toDate
  page        - Page number (default: 0)
  size        - Page size (default: 20)
  sort        - Sort field and direction (default: createdAt,desc)

Response (200): Paginated list of expense summary objects
```

##### POST /api/v1/expenses/{id}/submit

Submit a draft expense for approval. Also used to resubmit after rejection.

```
Response (200): Updated expense with status SUBMITTED

Errors:
  400 - Validation errors (amount, categoryId, expenseDate required and valid)
  400 - "No manager assigned. Contact your administrator."
  409 - "Expense can only be submitted from DRAFT or REJECTED status"
  403 - "Only the submitter can submit this expense"
```

##### POST /api/v1/expenses/{id}/approve

Approve a submitted expense. Roles: MANAGER (assigned), ADMIN.

```
Request (optional):
{ "comment": "Looks good" }

Response (200): Updated expense with status APPROVED

Errors:
  403 - Not the assigned manager and not an admin
  409 - "Expense is not in SUBMITTED status"
```

##### POST /api/v1/expenses/{id}/reject

Reject a submitted expense. Roles: MANAGER (assigned), ADMIN.

```
Request (required):
{ "comment": "Missing receipt for the hotel stay" }

Response (200): Updated expense with status REJECTED

Errors:
  400 - Comment is required for rejection
  403 - Not the assigned manager and not an admin
  409 - "Expense is not in SUBMITTED status"
```

##### DELETE /api/v1/expenses/{id}

Delete a draft expense. Only the submitter, only DRAFT status.

```
Response (204): No content

Errors:
  409 - "Only DRAFT expenses can be deleted"
  403 - "Only the submitter can delete this expense"
```

---

#### Receipt Endpoints

##### POST /api/v1/expenses/{id}/receipts

Upload a receipt file (multipart).

```
Request: multipart/form-data
  file: <binary>

Constraints:
  - Content types: image/jpeg, image/png, application/pdf
  - Max size: 5MB
  - Max 3 receipts per expense
  - Expense must be in DRAFT or REJECTED status

Response (201):
{
  "id": "uuid",
  "fileName": "receipt.jpg",
  "contentType": "image/jpeg",
  "fileSize": 245000,
  "createdAt": "2026-03-15T14:30:00Z"
}

Errors:
  400 - Invalid file type
  413 - File too large
  409 - "Maximum 3 receipts per expense" or "Cannot upload to expense in current status"
  403 - Only the submitter can upload receipts
```

##### GET /api/v1/expenses/{expenseId}/receipts/{receiptId}

Download/stream a receipt file.

```
Response (200):
  Content-Type: image/jpeg (or actual type)
  Content-Disposition: inline; filename="receipt.jpg"
  Body: <binary file content>

Access: Submitter, assigned manager, or admin in the same tenant
```

##### DELETE /api/v1/expenses/{expenseId}/receipts/{receiptId}

Delete a receipt. Only for DRAFT expenses, only by submitter.

```
Response (204): No content
```

---

#### Approval Endpoints

##### GET /api/v1/approvals/pending

List pending expenses for the current manager's team. Roles: MANAGER, ADMIN.

```
Query Params:
  submitterId - Filter to a specific employee
  categoryId  - Filter by category
  page, size  - Pagination

Response (200): Paginated list of pending expense summaries
  Note: Admin sees ALL pending expenses in the org.
        Manager sees only expenses where expense.manager_id = currentUser.
```

##### POST /api/v1/approvals/bulk

Bulk approve or reject expenses. Roles: MANAGER, ADMIN.

```
Request:
{
  "action": "APPROVE",
  "expenseIds": ["uuid1", "uuid2", "uuid3"],
  "comment": "All approved for Q1"
}

Constraints:
  - Max 50 expense IDs per request
  - Comment required for REJECT action

Response (200):
{
  "processed": 3,
  "skipped": 0,
  "results": [
    { "expenseId": "uuid1", "status": "SUCCESS" },
    { "expenseId": "uuid2", "status": "SUCCESS" },
    { "expenseId": "uuid3", "status": "SKIPPED", "reason": "Not in SUBMITTED status" }
  ]
}
```

---

#### Category Endpoints

##### GET /api/v1/categories

List active categories for the current tenant. Roles: ALL authenticated.

```
Response (200):
[
  { "id": "uuid", "name": "Travel", "isActive": true },
  { "id": "uuid", "name": "Meals", "isActive": true }
]
```

Note: Non-paginated. Category lists are small enough to return all at once.

##### POST /api/v1/categories

Create a new category. Roles: ADMIN.

```
Request: { "name": "Training" }
Response (201): { "id": "uuid", "name": "Training", "isActive": true }
Errors: 409 - Category name already exists in this org
```

##### PUT /api/v1/categories/{id}

Rename a category. Roles: ADMIN.

```
Request: { "name": "Professional Development" }
Response (200): Updated category
```

##### DELETE /api/v1/categories/{id}

Soft-delete (deactivate) a category. Roles: ADMIN.

```
Response (204): No content
Note: Existing expenses retain the category. Category stops appearing in dropdowns.
```

---

#### Analytics Endpoints (ADMIN only, except my-team)

##### GET /api/v1/analytics/summary

High-level org stats.

```
Query Params: fromDate, toDate (default: current month)

Response (200):
{
  "totalSubmitted": 100,
  "totalApproved": 75,
  "totalRejected": 15,
  "totalPending": 10,
  "totalApprovedAmount": 50000.00,
  "currency": "USD"
}
```

##### GET /api/v1/analytics/by-category

Spend breakdown by category.

```
Query Params: fromDate, toDate
Response (200):
[
  { "categoryId": "uuid", "categoryName": "Travel", "totalAmount": 12500.00, "expenseCount": 23 },
  { "categoryId": "uuid", "categoryName": "Meals", "totalAmount": 3200.50, "expenseCount": 45 }
]
```

##### GET /api/v1/analytics/by-month

Monthly spending trends.

```
Query Params: months (default: 6, max: 12)
Response (200):
[
  { "month": "2026-03", "totalAmount": 8500.00, "expenseCount": 32 },
  { "month": "2026-02", "totalAmount": 7200.00, "expenseCount": 28 }
]
```

##### GET /api/v1/analytics/by-team

Spend breakdown by manager (team).

```
Query Params: fromDate, toDate
Response (200):
[
  { "managerId": "uuid", "managerName": "Jane Smith", "totalAmount": 15000.00, "expenseCount": 45 }
]
```

##### GET /api/v1/analytics/my-team

Same as by-category but scoped to the current manager's direct reports. Roles: MANAGER.

```
Query Params: fromDate, toDate
Response: Same shape as by-category but only includes the calling manager's team data
```

---

## 5. Authentication & Security Architecture

### JWT Flow

```
                         +-----------+                     +-------------+
                         |  Browser  |                     | Spring Boot |
                         +-----------+                     +-------------+
                              |                                  |
  1. User enters email+pass   |                                  |
                              |--- POST /auth/login ------------>|
                              |    { email, password }           |
                              |                                  |
                              |    Validate credentials          |
                              |    Check account lock            |
                              |    Generate access JWT (15min)   |
                              |    Generate refresh token        |
                              |    Store refresh hash in DB      |
                              |                                  |
                              |<-- 200 { accessToken,           -|
                              |         refreshToken, user }     |
                              |                                  |
  2. Store tokens in memory   |                                  |
     (accessToken in variable,|                                  |
      refreshToken in memory) |                                  |
                              |                                  |
  3. Make API call            |                                  |
                              |--- GET /expenses --------------->|
                              |    Authorization: Bearer <AT>    |
                              |                                  |
                              |    JwtAuthFilter validates AT    |
                              |    Sets SecurityContext          |
                              |    Sets TenantContext            |
                              |                                  |
                              |<-- 200 { expenses... } ----------|
                              |                                  |
  4. Access token expires     |                                  |
     (after 15 minutes)       |                                  |
                              |--- GET /expenses --------------->|
                              |    Authorization: Bearer <AT>    |
                              |                                  |
                              |<-- 401 { "Token expired" } ------|
                              |                                  |
  5. Axios interceptor        |                                  |
     catches 401, auto-       |                                  |
     refreshes                |--- POST /auth/refresh ---------->|
                              |    { refreshToken: RT1 }         |
                              |                                  |
                              |    Find RT1 hash in DB           |
                              |    Verify not expired/revoked    |
                              |    Revoke RT1                    |
                              |    Generate new RT2              |
                              |    Set RT1.replaced_by = RT2.id  |
                              |    Generate new access token     |
                              |                                  |
                              |<-- 200 { accessToken: AT2,      -|
                              |         refreshToken: RT2 }      |
                              |                                  |
  6. Retry original request   |                                  |
     with new access token    |--- GET /expenses --------------->|
                              |    Authorization: Bearer <AT2>   |
                              |                                  |
                              |<-- 200 { expenses... } ----------|
```

### Refresh Token Rotation with Reuse Detection

The refresh token mechanism is designed to mitigate token theft. Here is how reuse detection works:

```
NORMAL FLOW (no theft):
  Login  -> RT1 issued, stored in DB
  Refresh -> RT1 revoked, RT2 issued (RT1.replaced_by = RT2)
  Refresh -> RT2 revoked, RT3 issued (RT2.replaced_by = RT3)
  ... each token used exactly once, then discarded

THEFT SCENARIO:
  Login  -> RT1 issued
  Attacker steals RT1

  Legitimate user refreshes first:
    RT1 -> revoked, RT2 issued

  Attacker tries to use RT1:
    Server sees RT1 is REVOKED
    This means RT1 was already used -> REUSE DETECTED
    Server walks the chain: RT1 -> RT2 -> RT3 (revoke all)
    Attacker gets 401
    Legitimate user's RT3 is also revoked -> forced re-login
    User re-authenticates, starting a new token family

  If attacker refreshes first:
    RT1 -> revoked, RT_attacker issued
    Legitimate user tries RT1 -> REUSE DETECTED
    All tokens in family revoked (including RT_attacker)
    User re-authenticates (and attacker's token stops working)
```

**Key design decisions:**
- Refresh tokens are opaque random strings (not JWTs). Only the hash is stored server-side.
- Token family is tracked via `replaced_by_id` linked list.
- On reuse detection, we revoke ALL tokens for that user (nuclear option) to be safe.
- Refresh token lifetime: 7 days. After 7 days of inactivity, the user must re-login.

### Spring Security Filter Chain Order

```
Request
  |
  v
[1] CorsFilter (Spring built-in)
  |  - Validates Origin header against allowed origins
  |  - Handles preflight OPTIONS requests
  |
  v
[2] RateLimitFilter (custom, extends OncePerRequestFilter)
  |  - For /api/v1/auth/* endpoints: per-IP rate limiting (20/min)
  |  - For other endpoints: SKIPPED here (tenant not yet known)
  |
  v
[3] JwtAuthenticationFilter (custom, extends OncePerRequestFilter)
  |  - Extracts Bearer token from Authorization header
  |  - Validates JWT signature, expiry, and structure
  |  - Extracts claims: userId, tenantId, role
  |  - Creates UsernamePasswordAuthenticationToken with authorities
  |  - Sets SecurityContextHolder
  |  - On failure: writes 401 JSON response, returns (no further filters)
  |  - SKIPS: /api/v1/auth/register, /api/v1/auth/login, /api/v1/auth/refresh
  |
  v
[4] TenantContextFilter (custom, extends OncePerRequestFilter)
  |  - Reads tenantId from SecurityContext authentication
  |  - Calls TenantContext.setCurrentTenant(tenantId)
  |  - try { chain.doFilter() } finally { TenantContext.clear() }
  |  - SKIPS: public endpoints (no auth = no tenant)
  |
  v
[5] TenantRateLimitFilter (custom, extends OncePerRequestFilter)
  |  - Reads tenant from TenantContext
  |  - Checks per-tenant token bucket (100/min)
  |  - On limit exceeded: 429 + Retry-After header
  |  - Adds X-RateLimit-* headers to response
  |
  v
[6] AuthorizationFilter (Spring Security built-in)
  |  - Evaluates @PreAuthorize annotations on controller methods
  |  - Maps roles to authorities: ROLE_EMPLOYEE, ROLE_MANAGER, ROLE_ADMIN
  |
  v
Controller Method
```

### RBAC Enforcement Strategy

Authorization is enforced at two levels:

**Level 1: Role-based (filter/annotation level)**

Using `@PreAuthorize` annotations on controller methods:

```java
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> listUsers(...) { ... }

@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
public ResponseEntity<?> getPendingApprovals(...) { ... }

@PreAuthorize("hasAnyRole('EMPLOYEE', 'MANAGER')")
public ResponseEntity<?> createExpense(...) { ... }
```

This provides a coarse-grained first pass. A user with the wrong role is rejected before any business logic runs.

**Level 2: Resource-based (service level)**

Fine-grained authorization checks within service methods:

```java
// Example: only the submitter can edit, only the assigned manager can approve
public ExpenseDto updateExpense(UUID expenseId, UpdateExpenseRequest req) {
    Expense expense = findExpenseInTenant(expenseId);  // tenant check

    if (!expense.getSubmitterId().equals(currentUserId())) {
        throw new ForbiddenException("Only the submitter can edit this expense");
    }
    if (expense.getStatus() != DRAFT && expense.getStatus() != REJECTED) {
        throw new ConflictException("Expense can only be edited in DRAFT or REJECTED status");
    }
    // ... proceed with update
}
```

**Why both levels?** `@PreAuthorize` catches the obvious mismatches early (Employee trying to access admin endpoints). Service-level checks handle the nuanced cases (Manager A trying to approve Manager B's team member's expense).

### Tenant Isolation Enforcement (Defense in Depth)

Tenant isolation is enforced at four layers:

```
Layer 1: JWT (Filter)
  - tenantId is embedded in the JWT at login time
  - Cannot be forged (JWT is signed)
  - TenantContextFilter extracts it and sets ThreadLocal

Layer 2: API (Controller)
  - No endpoint accepts tenantId as a parameter
  - Tenant is ALWAYS derived from the authenticated user's JWT
  - A malicious client cannot specify a different tenant

Layer 3: Service
  - TenantContext.getCurrentTenant() is used in all service methods
  - Business logic passes tenantId to repositories

Layer 4: Repository (Query)
  - Every query includes WHERE tenant_id = :tenantId
  - Even if a bug in the service layer forgets to pass tenantId,
    the repository methods include it by design
  - For entities without tenant_id (e.g., expense_receipts),
    access is through the parent entity (expense), which IS tenant-scoped

Result: For a cross-tenant breach to occur, ALL FOUR layers would need to fail simultaneously.
```

### Account Lockout Mechanism

```
Login attempt:
  1. Find user by email
  2. Check locked_until:
     - If locked_until > NOW() -> return 429 with Retry-After header
     - If locked_until <= NOW() -> reset failed_login_attempts to 0, proceed
  3. Verify password:
     - FAIL: increment failed_login_attempts
       - If failed_login_attempts >= 5:
         Set locked_until = NOW() + 15 minutes
         Return 429 "Account temporarily locked"
       - Else: return 401 "Invalid email or password"
     - SUCCESS: reset failed_login_attempts to 0, clear locked_until
       Issue tokens
```

### File Access Security

Receipt files are never served directly from the filesystem. Access is always mediated through an API endpoint that enforces:

1. **Authentication** -- valid JWT required
2. **Tenant isolation** -- the expense belongs to the user's org
3. **Authorization** -- user is the submitter, the assigned manager, or an admin in the org
4. **Path traversal prevention** -- `file_path` is stored in the DB and resolved against a base uploads directory. User-supplied input never forms part of the file path.

---

## 6. Approval Workflow State Machine

### State Diagram

```
                                     +----------------------------------+
                                     |                                  |
                                     v                                  |
  +--------+    submit     +------------+    approve    +----------+    |
  |        |-------------->|            |-------------->|          |    |
  | DRAFT  |               | SUBMITTED  |               | APPROVED |    |
  |        |               |            |               |          |    |
  +--------+               +------------+               +----------+    |
    |    ^                       |                                      |
    |    |                       | reject                               |
    |    |                       v                                      |
    |    |                 +------------+   edit + resubmit             |
    |    |                 |            |-------------------------------+
    |    |                 | REJECTED   |
    |    |                 |            |
    |    |                 +------------+
    |    |
    |    +-- (edit while in DRAFT)
    |
    | delete (hard delete,
    |         only for DRAFT)
    v
  [DELETED]
  (no record)


  Any Active State
        |
        | (employee deactivated - system trigger)
        v
  +------------+
  | CANCELLED  |
  +------------+
```

### States

| State | Description | Who Can See | Editable? |
|-------|------------|-------------|-----------|
| **DRAFT** | Expense created but not yet submitted. Partially filled fields allowed. | Submitter only | Yes |
| **SUBMITTED** | Expense submitted for approval. Awaiting manager action. | Submitter (read-only), Manager, Admin | No |
| **APPROVED** | Expense approved by manager. Final state. | Submitter, Manager, Admin | No |
| **REJECTED** | Expense rejected by manager with a comment. Can be edited and resubmitted. | Submitter (editable), Manager, Admin | Yes (by submitter) |
| **CANCELLED** | Expense cancelled by system (e.g., employee deactivated). Terminal state. | Admin | No |

### Transitions

| # | From | To | Triggered By | Guards / Preconditions | Side Effects |
|---|------|----|-------------|----------------------|-------------|
| T1 | -- | DRAFT | Employee/Manager creates expense | User has EMPLOYEE or MANAGER role | Audit log: CREATED |
| T2 | DRAFT | SUBMITTED | Employee clicks "Submit" | All required fields valid (amount > 0, categoryId valid, expenseDate not future). Submitter has a manager assigned. | Snapshot manager_id onto expense record. Audit log: SUBMITTED |
| T3 | SUBMITTED | APPROVED | Manager clicks "Approve" | Approver is the expense's assigned manager OR an Admin in the same tenant. Expense status is SUBMITTED. | Set approved_by_id, approved_at. Audit log: APPROVED |
| T4 | SUBMITTED | REJECTED | Manager clicks "Reject" | Same as T3 for approver. Comment is required (non-empty). | Set rejection_comment. Audit log: REJECTED |
| T5 | REJECTED | SUBMITTED | Employee clicks "Resubmit" | Submitter may edit fields before resubmitting. Same validations as T2. | Re-snapshot manager_id (in case of reassignment). Audit log: RESUBMITTED |
| T6 | DRAFT | [DELETED] | Employee clicks "Delete" | Only the submitter. Only DRAFT status. | Hard delete from DB. Receipts deleted from filesystem. No audit trail (record gone). |
| T7 | DRAFT/SUBMITTED | CANCELLED | System (employee deactivation) | Employee is being deactivated by Admin | Audit log: CANCELLED, performed_by = Admin who deactivated |

### Transition Guard Summary

```
Allowed transitions matrix:

         To ->   DRAFT   SUBMITTED   APPROVED   REJECTED   CANCELLED   DELETED
From |
------+------------------------------------------------------------
(new) |           Y
DRAFT |                     Y                                            Y
SUBMITTED |                              Y          Y          Y
APPROVED |                                                    (no)
REJECTED |                   Y                                 Y
CANCELLED |                                                    (terminal)
```

### Implementation Approach

The state machine is enforced in `ExpenseService` using explicit transition validation:

```java
public Expense submitExpense(UUID expenseId) {
    Expense expense = findExpenseInCurrentTenant(expenseId);
    assertSubmitter(expense);  // only the submitter can submit

    if (expense.getStatus() != Status.DRAFT && expense.getStatus() != Status.REJECTED) {
        throw new InvalidStateTransitionException(
            expense.getStatus(), Status.SUBMITTED,
            "Expense can only be submitted from DRAFT or REJECTED status"
        );
    }

    validateForSubmission(expense);  // amount, category, date

    User submitter = getCurrentUser();
    if (submitter.getManagerId() == null) {
        throw new BusinessRuleException("No manager assigned. Contact your administrator.");
    }

    String action = expense.getStatus() == Status.REJECTED ? "RESUBMITTED" : "SUBMITTED";
    expense.setManagerId(submitter.getManagerId());  // snapshot current manager
    expense.setStatus(Status.SUBMITTED);
    expense.setRejectionComment(null);  // clear previous rejection on resubmit

    expense = expenseRepository.save(expense);
    auditLogService.log(expense, action, null);

    return expense;
}
```

**Why not a formal state machine library (Spring Statemachine)?** Our state machine has 5 states and 7 transitions. Spring Statemachine adds significant complexity (factory, configuration, actions, guards, event sourcing) that is warranted for complex workflows with many states and parallel paths. For our linear workflow, explicit `if` checks in the service layer are more readable, more debuggable, and easier for reviewers to understand. This is a deliberate trade-off of "frameworkiness" for clarity.

---

## 7. Multi-Tenancy Architecture

### Chosen Pattern: Shared Database, Shared Schema, Discriminator Column

Every tenant-scoped table has a `tenant_id UUID NOT NULL` column with a foreign key to `organizations.id`. All queries filter on this column.

### Why This Pattern

Three common multi-tenancy patterns exist:

| Pattern | Isolation | Complexity | Cost | Scale |
|---------|-----------|-----------|------|-------|
| **Separate database per tenant** | Strongest | High (connection management, migrations per DB) | High (one DB instance per tenant) | Best for large enterprise tenants with strict compliance needs |
| **Separate schema per tenant** | Strong | Medium (schema management, dynamic routing) | Medium | Good middle ground but operationally complex |
| **Shared schema, tenant_id column** | Application-enforced | Low | Low (single DB) | Best for SaaS with many small-medium tenants |

**We chose shared schema** because:
1. **Simplicity**: One database, one connection pool, one set of migrations. A single Flyway migration applies to all tenants.
2. **Appropriate for MVP**: We are not dealing with regulatory requirements that mandate physical data separation.
3. **Cost-effective**: A single PostgreSQL instance handles all tenants.
4. **Query simplicity**: Cross-tenant analytics (if ever needed at the platform level) are simple joins.

**Acknowledged risk**: A bug in query construction could leak data between tenants. We mitigate this with defense-in-depth (see Section 5) and integration tests that verify isolation.

### Data Isolation at Each Layer

```
+------------------------------------------------------------------+
|  LAYER 1: API BOUNDARY                                           |
|  - No endpoint accepts tenant_id as input                        |
|  - Tenant identity comes ONLY from the JWT                       |
|  - A client cannot impersonate another tenant                    |
+------------------------------------------------------------------+
                              |
                              v
+------------------------------------------------------------------+
|  LAYER 2: FILTER CHAIN                                           |
|  - JwtAuthFilter extracts tenantId from JWT claims               |
|  - TenantContextFilter stores it in ThreadLocal                  |
|  - The ThreadLocal is ALWAYS cleared in a finally block          |
|    (prevents leakage between requests in thread pools)           |
+------------------------------------------------------------------+
                              |
                              v
+------------------------------------------------------------------+
|  LAYER 3: SERVICE LAYER                                          |
|  - Every service method reads TenantContext.getCurrentTenant()    |
|  - Business logic validates cross-references are within tenant   |
|    (e.g., manager_id must be in the same org)                    |
|  - Services never accept tenantId as a method parameter from     |
|    controller -- they read it from TenantContext                  |
+------------------------------------------------------------------+
                              |
                              v
+------------------------------------------------------------------+
|  LAYER 4: REPOSITORY LAYER                                       |
|  - Spring Data JPA queries include tenant_id in WHERE clause     |
|  - Option A: @Query with explicit tenant_id parameter            |
|    @Query("SELECT e FROM Expense e                               |
|            WHERE e.tenantId = :tenantId AND e.id = :id")         |
|  - Option B: Hibernate @Filter on entity                         |
|    @FilterDef(name = "tenantFilter",                             |
|               parameters = @ParamDef(name = "tenantId"...))      |
|  - We use Option A (explicit @Query) for clarity and because     |
|    Hibernate filters can be accidentally disabled                 |
+------------------------------------------------------------------+
                              |
                              v
+------------------------------------------------------------------+
|  LAYER 5: DATABASE                                               |
|  - NOT NULL constraint on tenant_id prevents orphaned rows       |
|  - Foreign key to organizations ensures referential integrity     |
|  - Indexes on (tenant_id, ...) ensure efficient filtered queries |
+------------------------------------------------------------------+
```

### TenantContext Design

```java
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    public static void setCurrentTenant(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID getCurrentTenant() {
        UUID tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new IllegalStateException(
                "No tenant context set. This indicates a bug: " +
                "either the request is unauthenticated or the " +
                "TenantContextFilter was not applied."
            );
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
```

**Why ThreadLocal?**
- Spring MVC processes each request in a single thread. ThreadLocal guarantees that tenant context set by the filter is visible to all service/repository calls within that request.
- The `clear()` call in the filter's `finally` block prevents stale tenant data from leaking when the thread is returned to the pool.

**What about reactive/async?** ThreadLocal does not work with reactive stacks (WebFlux) or `@Async` methods. We are using Spring MVC (servlet-based, one-thread-per-request), so ThreadLocal is correct. If we migrated to WebFlux, we would use Reactor Context instead.

### How Tenant ID Flows Through a Request

```
1. User logs in -> JWT contains claim: "tenantId": "org-uuid-123"

2. Request arrives with Authorization: Bearer <jwt>

3. JwtAuthFilter:
   - Parses JWT, extracts tenantId claim
   - Creates Authentication with tenantId as a detail
   - SecurityContextHolder.getContext().setAuthentication(auth)

4. TenantContextFilter:
   - UUID tenantId = extractTenantId(SecurityContextHolder.getContext())
   - TenantContext.setCurrentTenant(tenantId)
   - try { filterChain.doFilter(request, response) }
     finally { TenantContext.clear() }

5. ExpenseService.listExpenses():
   - UUID tenantId = TenantContext.getCurrentTenant()
   - return expenseRepository.findByTenantId(tenantId, ...)

6. Repository:
   - @Query("SELECT e FROM Expense e WHERE e.tenantId = :tenantId ...")
   - PostgreSQL executes: SELECT * FROM expenses WHERE tenant_id = 'org-uuid-123' ...

7. Response sent. TenantContext.clear() runs in finally block.
```

### Cross-Tenant Access Prevention -- Specific Scenarios

| Scenario | Prevention Mechanism |
|----------|---------------------|
| User crafts a request with another org's expense ID | Repository query includes `tenant_id = currentTenant`. Expense found by ID but wrong tenant -> query returns empty -> 404 response. |
| User manipulates JWT to change tenantId | JWT signature verification fails -> 401. JWTs are signed with a server-side secret. |
| Admin in Org A tries to see Org B's analytics | Analytics queries include `WHERE tenant_id = :tenantId`. Only Org A data returned. |
| Manager in Org A tries to approve Org B's expense | Expense lookup filters by tenant_id. Expense not found -> 404. |
| User registers with Org A, then changes `organizationId` on subsequent requests | Organization is embedded in the JWT at login time and never read from request parameters. |

---

## 8. File Storage Architecture

### Upload Flow

```
Employee Browser                    Spring Boot                     Filesystem
      |                                 |                               |
      |  POST /expenses/{id}/receipts   |                               |
      |  Content-Type: multipart/form   |                               |
      |  [file binary data]             |                               |
      |-------------------------------->|                               |
      |                                 |                               |
      |                   1. Validate:  |                               |
      |                   - Auth (JWT)  |                               |
      |                   - Tenant owns expense                         |
      |                   - User is submitter                           |
      |                   - Expense in DRAFT/REJECTED                   |
      |                   - File type (JPEG/PNG/PDF)                    |
      |                   - File size (< 5MB)                           |
      |                   - Receipt count (< 3)                         |
      |                                 |                               |
      |                   2. Generate:  |                               |
      |                   - UUID for filename                           |
      |                   - Path: uploads/{tenantId}/{expenseId}/{uuid}.ext
      |                                 |                               |
      |                                 |  Write file to disk           |
      |                                 |------------------------------>|
      |                                 |                               |
      |                   3. Create DB record:                          |
      |                   - expense_receipts row                        |
      |                   - file_name (original)                        |
      |                   - file_path (generated path)                  |
      |                   - content_type, file_size                     |
      |                                 |                               |
      |<-- 201 { receipt metadata } ----|                               |
```

### Storage Structure

```
uploads/
  ├── {tenant-uuid-1}/
  │   ├── {expense-uuid-a}/
  │   │   ├── a1b2c3d4-e5f6-7890-abcd-ef1234567890.jpg
  │   │   └── b2c3d4e5-f6a7-8901-bcde-f12345678901.pdf
  │   └── {expense-uuid-b}/
  │       └── c3d4e5f6-a7b8-9012-cdef-123456789012.png
  └── {tenant-uuid-2}/
      └── {expense-uuid-c}/
          └── d4e5f6a7-b8c9-0123-defa-234567890123.jpg
```

**Why this structure?**
- **Tenant isolation at filesystem level**: Each tenant's files live under their own directory. Even if a directory listing were somehow exposed, files from other tenants are in separate trees.
- **Expense grouping**: All receipts for one expense are co-located, making cleanup easy (delete expense -> delete directory).
- **UUID filenames**: Original filenames may conflict or contain unsafe characters. UUIDs eliminate these issues while preserving the original name in the DB for display purposes.

### Access Control for Downloads

```java
@GetMapping("/expenses/{expenseId}/receipts/{receiptId}")
public ResponseEntity<Resource> downloadReceipt(
        @PathVariable UUID expenseId,
        @PathVariable UUID receiptId) {

    // 1. Load expense with tenant check
    Expense expense = expenseService.findByIdInCurrentTenant(expenseId);

    // 2. Verify access: submitter, assigned manager, or admin
    UUID currentUserId = SecurityUtils.getCurrentUserId();
    String currentRole = SecurityUtils.getCurrentRole();

    boolean isSubmitter = expense.getSubmitterId().equals(currentUserId);
    boolean isAssignedManager = expense.getManagerId() != null
                                && expense.getManagerId().equals(currentUserId);
    boolean isAdmin = "ADMIN".equals(currentRole);

    if (!isSubmitter && !isAssignedManager && !isAdmin) {
        throw new ForbiddenException("You do not have access to this receipt");
    }

    // 3. Load receipt metadata
    ExpenseReceipt receipt = receiptRepository.findByIdAndExpenseId(receiptId, expenseId)
        .orElseThrow(() -> new NotFoundException("Receipt not found"));

    // 4. Resolve file path (within base uploads dir, preventing path traversal)
    Path filePath = storageService.resolve(receipt.getFilePath());

    // 5. Stream file with correct content type
    Resource resource = new FileSystemResource(filePath);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(receipt.getContentType()))
        .header(HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"" + receipt.getFileName() + "\"")
        .body(resource);
}
```

### Abstraction Layer for Future Cloud Migration

```java
public interface FileStorageService {

    /**
     * Store a file and return the storage path (relative key).
     */
    String store(UUID tenantId, UUID expenseId, MultipartFile file);

    /**
     * Load a file as a Resource by its storage path.
     */
    Resource load(String storagePath);

    /**
     * Delete a file by its storage path.
     */
    void delete(String storagePath);

    /**
     * Delete all files for an expense.
     */
    void deleteAllForExpense(UUID tenantId, UUID expenseId);
}
```

**MVP implementation: `LocalFileStorageService`** -- stores files on the local filesystem under a configurable `uploads.base-dir` property.

**Future implementation: `S3FileStorageService`** -- would use the AWS SDK to store/retrieve files from S3. The `storagePath` becomes the S3 key. Download would use pre-signed URLs instead of streaming through the backend.

**Switching is a configuration change**: Via Spring `@Profile` or `@ConditionalOnProperty`, the application can be configured to use either implementation without code changes.

---

## 9. Rate Limiting Design

### Algorithm: Token Bucket

We use the **token bucket** algorithm for rate limiting.

```
HOW TOKEN BUCKET WORKS:

  Bucket capacity: 100 tokens
  Refill rate: 100 tokens per minute (1.667 tokens/second)

  +----+----+----+----+----+----+----+----+
  | T  | T  | T  | T  | T  | T  | T  |   |   <- Bucket (capacity: 100)
  +----+----+----+----+----+----+----+----+
    ^                                   ^
    |                                   |
    Full (100 tokens)              Empty (0 tokens)

  Each request consumes 1 token:
  - Token available -> request allowed, decrement count
  - No tokens -> request rejected with 429

  Tokens refill continuously at 1.667/second:
  - If 50 tokens consumed, they're back in ~30 seconds
  - Allows bursts (use all 100 quickly) but enforces average rate

  Implementation uses timestamp math (not a timer thread):
  - On each request: elapsed = now - lastRefill
  - tokensToAdd = elapsed * refillRate
  - tokens = min(capacity, tokens + tokensToAdd)
  - Then try to consume one token
```

**Why token bucket over sliding window?**
- **Token bucket allows bursts**: A tenant can send 50 requests in 1 second if they have tokens, which is fine. Sliding window would reject after the Nth request in a fixed window, which feels more punitive for bursty-but-fair usage.
- **Simple to implement**: A single `AtomicLong` for tokens and a timestamp for last refill. No need to track individual request timestamps.
- **Memory-efficient**: Two values per bucket (token count + last refill time) vs. sliding window which may need a list of request timestamps.

### Scoping

| Scope | Endpoints | Limit | Key | Why |
|-------|-----------|-------|-----|-----|
| Per-IP | `/api/v1/auth/*` (login, register, refresh) | 20 req/min | Client IP address | Auth endpoints are pre-authentication, so tenant ID is not available. Per-IP prevents brute-force attacks. |
| Per-Tenant | All other `/api/v1/*` endpoints | 100 req/min | `tenant_id` from JWT | Prevents one org from monopolizing shared infrastructure. Fair resource allocation. |

### Implementation as Spring Filters

Two separate filters, ordered appropriately in the filter chain:

**AuthRateLimitFilter** (runs BEFORE JwtAuthFilter):
- Matches `/api/v1/auth/**` paths only
- Extracts client IP from request (handles X-Forwarded-For for proxied setups)
- Checks per-IP token bucket
- On limit exceeded: 429 response with JSON body and Retry-After header

**TenantRateLimitFilter** (runs AFTER TenantContextFilter):
- Matches all other `/api/v1/**` paths
- Reads tenant from TenantContext
- Checks per-tenant token bucket
- On limit exceeded: 429 response

### Storage

**MVP: In-memory `ConcurrentHashMap<String, TokenBucket>`**

```java
public class InMemoryRateLimiter {
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitResult tryConsume(String key, int capacity, double refillPerSecond) {
        TokenBucket bucket = buckets.computeIfAbsent(key,
            k -> new TokenBucket(capacity, refillPerSecond));
        return bucket.tryConsume();
    }
}
```

**Trade-off**: In-memory rate limiting does not survive application restarts and does not work across multiple application instances. For a single-instance MVP, this is acceptable.

**Production improvement**: Replace `ConcurrentHashMap` with Redis. Use `MULTI`/`EXEC` or Lua scripts for atomic token bucket operations. This enables rate limiting across a load-balanced cluster.

### Headers and Error Response

**Every response includes rate limit headers:**

```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 73
X-RateLimit-Reset: 1679145600    (Unix timestamp when bucket fully refills)
```

**429 response:**

```
HTTP/1.1 429 Too Many Requests
Retry-After: 30
Content-Type: application/json
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1679145600

{
  "error": "Rate limit exceeded",
  "code": "RATE_LIMIT_EXCEEDED",
  "retryAfter": 30
}
```

---

## 10. Frontend Architecture

### Component Hierarchy

```
<App>
  ├── <AuthProvider>                    // Context: user, tokens, login/logout/refresh
  │   ├── <BrowserRouter>
  │   │   ├── <PublicRoute>
  │   │   │   ├── <LoginPage />
  │   │   │   └── <RegisterPage />
  │   │   │
  │   │   ├── <ProtectedRoute>          // Redirects to /login if not authenticated
  │   │   │   ├── <AppLayout>           // Sidebar + header + content area
  │   │   │   │   ├── <Sidebar>         // Role-aware navigation links
  │   │   │   │   ├── <Header>          // User info, logout button
  │   │   │   │   └── <Outlet>          // Nested route content
  │   │   │   │
  │   │   │   │   // EMPLOYEE + MANAGER routes
  │   │   │   │   ├── <MyExpensesPage>
  │   │   │   │   │   ├── <ExpenseFilterBar />
  │   │   │   │   │   ├── <ExpenseTable />
  │   │   │   │   │   └── <Pagination />
  │   │   │   │   │
  │   │   │   │   ├── <ExpenseFormPage>
  │   │   │   │   │   ├── <ExpenseForm />
  │   │   │   │   │   │   ├── <AmountInput />
  │   │   │   │   │   │   ├── <CategorySelect />
  │   │   │   │   │   │   ├── <DatePicker />
  │   │   │   │   │   │   └── <ReceiptUpload />
  │   │   │   │   │   └── <FormActions />  // Save Draft | Submit
  │   │   │   │   │
  │   │   │   │   ├── <ExpenseDetailPage>
  │   │   │   │   │   ├── <ExpenseInfo />
  │   │   │   │   │   ├── <ReceiptGallery />
  │   │   │   │   │   └── <AuditTimeline />
  │   │   │   │   │
  │   │   │   │   // MANAGER-only routes
  │   │   │   │   ├── <RoleGuard role="MANAGER">
  │   │   │   │   │   ├── <PendingApprovalsPage>
  │   │   │   │   │   │   ├── <ApprovalTable />
  │   │   │   │   │   │   ├── <BulkActions />
  │   │   │   │   │   │   └── <RejectModal />
  │   │   │   │   │   └── <TeamStatsPage>
  │   │   │   │   │       └── <TeamCategoryChart />
  │   │   │   │   │
  │   │   │   │   // ADMIN-only routes
  │   │   │   │   ├── <RoleGuard role="ADMIN">
  │   │   │   │   │   ├── <AdminDashboardPage>
  │   │   │   │   │   │   ├── <SummaryCards />
  │   │   │   │   │   │   ├── <CategoryBarChart />
  │   │   │   │   │   │   ├── <MonthlyTrendLineChart />
  │   │   │   │   │   │   ├── <TeamSpendTable />
  │   │   │   │   │   │   └── <DateRangePicker />
  │   │   │   │   │   │
  │   │   │   │   │   ├── <UserManagementPage>
  │   │   │   │   │   │   ├── <UserTable />
  │   │   │   │   │   │   ├── <RoleChangeModal />
  │   │   │   │   │   │   └── <ManagerAssignModal />
  │   │   │   │   │   │
  │   │   │   │   │   └── <CategoryManagementPage>
  │   │   │   │   │       ├── <CategoryList />
  │   │   │   │   │       └── <CategoryForm />
  │   │   │   │   │
  │   │   │   │   // ALL roles
  │   │   │   │   └── <ProfilePage />
  │   │   │   │
  │   │   │   └── <NotFoundPage />
  │   │   └──
  │   └──
  └── <Toaster />                       // Global toast notification container
```

### State Management: React Context + Custom Hooks

**Chosen: React Context + custom hooks (not Redux)**

**Why:**
- Our state management needs are straightforward: auth state (global), server data (fetched per page), form state (local).
- Redux adds boilerplate (actions, reducers, selectors, middleware) that is not justified for this project's complexity.
- React Context handles the single piece of truly global state: authentication.
- Server state (expenses, categories, analytics) is managed with custom hooks wrapping Axios calls. Each page fetches its own data on mount. No cross-page cache coordination is needed.
- If we needed cross-page caching and background refetching, we would adopt React Query (TanStack Query). For MVP, simple `useEffect` + `useState` hooks are sufficient and transparent.

**Context Providers:**

| Context | State | Purpose |
|---------|-------|---------|
| `AuthContext` | `user`, `accessToken`, `refreshToken`, `isAuthenticated`, `isLoading` | Authentication state. Provides `login()`, `logout()`, `register()` functions. Persists tokens to `localStorage` for session survival across tab closes. |

**Custom Hooks (data fetching):**

| Hook | Usage |
|------|-------|
| `useExpenses(filters)` | Fetches paginated expenses for the current user |
| `useExpense(id)` | Fetches single expense with receipts and audit trail |
| `usePendingApprovals(filters)` | Fetches pending approvals for the manager |
| `useCategories()` | Fetches active categories |
| `useAnalytics(dateRange)` | Fetches analytics data for admin dashboard |
| `useUsers(filters)` | Fetches user list for admin user management |

### Auth Flow: Token Storage and Refresh Interceptor

```
TOKEN STORAGE:
  - accessToken: stored in memory (React state). Lost on page refresh.
    On refresh, we use the refreshToken to get a new accessToken.
  - refreshToken: stored in localStorage.
    Trade-off: localStorage is vulnerable to XSS. Alternatives:
    - httpOnly cookie: more secure but complicates CORS and CSRF.
    - Memory only: forces re-login on every page refresh.
    For MVP, localStorage with XSS mitigations (CSP, input sanitization)
    is acceptable. Production hardening would use httpOnly cookies.

AXIOS INTERCEPTOR (refresh flow):

  const api = axios.create({ baseURL: '/api/v1' });

  // Request interceptor: attach access token
  api.interceptors.request.use(config => {
    const token = authContext.accessToken;
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  });

  // Response interceptor: handle 401 and auto-refresh
  api.interceptors.response.use(
    response => response,
    async error => {
      const originalRequest = error.config;

      if (error.response?.status === 401
          && !originalRequest._retry
          && !originalRequest.url.includes('/auth/')) {

        originalRequest._retry = true;  // prevent infinite loops

        try {
          const { accessToken, refreshToken } =
            await authService.refresh(currentRefreshToken);

          authContext.setTokens(accessToken, refreshToken);
          originalRequest.headers.Authorization = `Bearer ${accessToken}`;

          return api(originalRequest);  // retry original request
        } catch (refreshError) {
          authContext.logout();  // refresh failed, force re-login
          return Promise.reject(refreshError);
        }
      }

      return Promise.reject(error);
    }
  );
```

**Important edge case handled**: If multiple requests fail with 401 simultaneously (e.g., page loads multiple API calls), we must ensure only ONE refresh request is sent. A `refreshPromise` variable deduplicates concurrent refresh attempts:

```typescript
let refreshPromise: Promise<TokenPair> | null = null;

async function refreshTokens(): Promise<TokenPair> {
  if (!refreshPromise) {
    refreshPromise = authService.refresh(getRefreshToken())
      .finally(() => { refreshPromise = null; });
  }
  return refreshPromise;
}
```

### Routing Structure with Role Guards

```
/login                          PUBLIC    LoginPage
/register                       PUBLIC    RegisterPage

/                               AUTH      Redirect based on role:
                                          EMPLOYEE -> /expenses
                                          MANAGER  -> /approvals
                                          ADMIN    -> /dashboard

/expenses                       AUTH      MyExpensesPage (EMPLOYEE, MANAGER)
/expenses/new                   AUTH      ExpenseFormPage (EMPLOYEE, MANAGER)
/expenses/:id                   AUTH      ExpenseDetailPage (EMPLOYEE, MANAGER, ADMIN)
/expenses/:id/edit              AUTH      ExpenseFormPage (EMPLOYEE, MANAGER) [DRAFT/REJECTED only]

/approvals                      MANAGER+  PendingApprovalsPage
/team-stats                     MANAGER+  TeamStatsPage

/dashboard                      ADMIN     AdminDashboardPage
/users                          ADMIN     UserManagementPage
/categories                     ADMIN     CategoryManagementPage

/profile                        AUTH      ProfilePage

/*                              ALL       NotFoundPage
```

**RoleGuard implementation:**

```tsx
function RoleGuard({ allowedRoles, children }: {
  allowedRoles: Role[],
  children: ReactNode
}) {
  const { user } = useAuth();

  if (!allowedRoles.includes(user.role)) {
    return <Navigate to="/" replace />;
  }

  return <>{children}</>;
}
```

### Form Handling Strategy

- **Form state**: `useState` for simple forms, `useReducer` for the expense form (multiple interdependent fields).
- **Validation**: Client-side validation mirrors server-side rules (amount > 0, required fields). Use a validation function that returns a `Record<string, string>` of field-to-error mappings.
- **Submission**: On submit, call API. On success, navigate away. On error, display server validation errors inline.
- **File uploads**: Managed separately from form JSON. Upload endpoint is called after the expense is created. Preview thumbnails for images, filename for PDFs.
- **Optimistic updates**: Not used for this project. Expense operations have server-side validations that could reject the operation, making optimistic updates risky.

### Chart/Analytics Components

| Component | Chart Type | Library Component | Data Source |
|-----------|-----------|-------------------|-------------|
| `CategoryBarChart` | Horizontal bar chart | `<BarChart>` (Recharts) | `/analytics/by-category` |
| `MonthlyTrendLineChart` | Line chart with area fill | `<LineChart>` + `<Area>` | `/analytics/by-month` |
| `TeamSpendTable` | HTML table (not a chart) | Custom `<table>` | `/analytics/by-team` |
| `SummaryCards` | Metric cards (not a chart) | Custom cards | `/analytics/summary` |

Charts use Recharts' responsive container to adapt to viewport width. Each chart component receives data as props and handles its own loading/empty states.

---

## 11. Project Structure

### Backend (Spring Boot)

```
expense-tracker-api/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/expensetracker/
│   │   │   ├── ExpenseTrackerApplication.java
│   │   │   │
│   │   │   ├── config/
│   │   │   │   ├── SecurityConfig.java          // Spring Security filter chain config
│   │   │   │   ├── CorsConfig.java              // CORS configuration
│   │   │   │   ├── JacksonConfig.java           // JSON serialization settings
│   │   │   │   └── StorageConfig.java           // File storage configuration
│   │   │   │
│   │   │   ├── security/
│   │   │   │   ├── JwtAuthenticationFilter.java // Validate JWT, set SecurityContext
│   │   │   │   ├── JwtTokenProvider.java        // Generate/validate/parse JWTs
│   │   │   │   ├── TenantContextFilter.java     // Extract tenant, set ThreadLocal
│   │   │   │   ├── TenantContext.java           // ThreadLocal tenant holder
│   │   │   │   └── SecurityUtils.java           // Helper: get current user/tenant/role
│   │   │   │
│   │   │   ├── filter/
│   │   │   │   ├── AuthRateLimitFilter.java     // Per-IP rate limit for auth endpoints
│   │   │   │   └── TenantRateLimitFilter.java   // Per-tenant rate limit for API
│   │   │   │
│   │   │   ├── ratelimit/
│   │   │   │   ├── TokenBucket.java             // Token bucket algorithm
│   │   │   │   └── InMemoryRateLimiter.java     // ConcurrentHashMap-based store
│   │   │   │
│   │   │   ├── controller/
│   │   │   │   ├── AuthController.java          // register, login, refresh, logout
│   │   │   │   ├── ExpenseController.java       // CRUD + submit
│   │   │   │   ├── ReceiptController.java       // upload, download, delete
│   │   │   │   ├── ApprovalController.java      // pending, approve, reject, bulk
│   │   │   │   ├── UserController.java          // list, role change, manager assign
│   │   │   │   ├── CategoryController.java      // CRUD for expense categories
│   │   │   │   └── AnalyticsController.java     // summary, by-category, by-month, by-team
│   │   │   │
│   │   │   ├── service/
│   │   │   │   ├── AuthService.java             // Registration, login, refresh logic
│   │   │   │   ├── ExpenseService.java          // Expense CRUD + state machine
│   │   │   │   ├── ApprovalService.java         // Approve/reject/bulk logic
│   │   │   │   ├── UserService.java             // User management, role changes
│   │   │   │   ├── CategoryService.java         // Category CRUD
│   │   │   │   ├── AnalyticsService.java        // Aggregation queries
│   │   │   │   ├── AuditLogService.java         // Expense audit trail logging
│   │   │   │   └── FileStorageService.java      // Interface for file storage
│   │   │   │
│   │   │   ├── service/impl/
│   │   │   │   └── LocalFileStorageService.java // Local filesystem implementation
│   │   │   │
│   │   │   ├── repository/
│   │   │   │   ├── OrganizationRepository.java
│   │   │   │   ├── UserRepository.java
│   │   │   │   ├── RefreshTokenRepository.java
│   │   │   │   ├── ExpenseRepository.java
│   │   │   │   ├── ExpenseReceiptRepository.java
│   │   │   │   ├── ExpenseAuditLogRepository.java
│   │   │   │   └── ExpenseCategoryRepository.java
│   │   │   │
│   │   │   ├── model/
│   │   │   │   ├── Organization.java            // @Entity
│   │   │   │   ├── User.java                    // @Entity
│   │   │   │   ├── RefreshToken.java            // @Entity
│   │   │   │   ├── Expense.java                 // @Entity
│   │   │   │   ├── ExpenseReceipt.java          // @Entity
│   │   │   │   ├── ExpenseAuditLog.java         // @Entity
│   │   │   │   ├── ExpenseCategory.java         // @Entity
│   │   │   │   └── enums/
│   │   │   │       ├── Role.java                // EMPLOYEE, MANAGER, ADMIN
│   │   │   │       ├── ExpenseStatus.java       // DRAFT, SUBMITTED, APPROVED, REJECTED, CANCELLED
│   │   │   │       └── AuditAction.java         // CREATED, SUBMITTED, APPROVED, REJECTED, etc.
│   │   │   │
│   │   │   ├── dto/
│   │   │   │   ├── request/
│   │   │   │   │   ├── RegisterRequest.java
│   │   │   │   │   ├── LoginRequest.java
│   │   │   │   │   ├── RefreshRequest.java
│   │   │   │   │   ├── CreateExpenseRequest.java
│   │   │   │   │   ├── UpdateExpenseRequest.java
│   │   │   │   │   ├── ApprovalRequest.java
│   │   │   │   │   ├── BulkApprovalRequest.java
│   │   │   │   │   ├── ChangeRoleRequest.java
│   │   │   │   │   └── AssignManagerRequest.java
│   │   │   │   └── response/
│   │   │   │       ├── AuthResponse.java
│   │   │   │       ├── UserDto.java
│   │   │   │       ├── ExpenseDto.java
│   │   │   │       ├── ExpenseDetailDto.java
│   │   │   │       ├── ExpenseSummaryDto.java
│   │   │   │       ├── ReceiptDto.java
│   │   │   │       ├── AuditLogDto.java
│   │   │   │       ├── CategoryDto.java
│   │   │   │       ├── AnalyticsSummaryDto.java
│   │   │   │       ├── CategorySpendDto.java
│   │   │   │       ├── MonthlySpendDto.java
│   │   │   │       ├── TeamSpendDto.java
│   │   │   │       ├── BulkApprovalResultDto.java
│   │   │   │       └── ErrorResponse.java
│   │   │   │
│   │   │   └── exception/
│   │   │       ├── GlobalExceptionHandler.java  // @ControllerAdvice
│   │   │       ├── ResourceNotFoundException.java
│   │   │       ├── ForbiddenException.java
│   │   │       ├── ConflictException.java
│   │   │       ├── InvalidStateTransitionException.java
│   │   │       ├── BusinessRuleException.java
│   │   │       ├── RateLimitExceededException.java
│   │   │       └── FileStorageException.java
│   │   │
│   │   └── resources/
│   │       ├── application.yml                  // Main config
│   │       ├── application-dev.yml              // Dev profile (local DB, debug logging)
│   │       ├── application-docker.yml           // Docker profile (container DB host)
│   │       └── db/migration/
│   │           ├── V1__create_organizations.sql
│   │           ├── V2__create_users.sql
│   │           ├── V3__create_refresh_tokens.sql
│   │           ├── V4__create_expense_categories.sql
│   │           ├── V5__create_expenses.sql
│   │           ├── V6__create_expense_receipts.sql
│   │           ├── V7__create_expense_audit_log.sql
│   │           └── V8__seed_demo_data.sql
│   │
│   └── test/
│       └── java/com/expensetracker/
│           ├── integration/
│           │   ├── AuthIntegrationTest.java
│           │   ├── ExpenseIntegrationTest.java
│           │   ├── ApprovalIntegrationTest.java
│           │   ├── TenantIsolationTest.java
│           │   └── RateLimitIntegrationTest.java
│           ├── service/
│           │   ├── ExpenseServiceTest.java
│           │   ├── ApprovalServiceTest.java
│           │   └── AuthServiceTest.java
│           └── controller/
│               ├── ExpenseControllerTest.java
│               └── ApprovalControllerTest.java
```

### Frontend (React + TypeScript)

```
expense-tracker-ui/
├── package.json
├── tsconfig.json
├── vite.config.ts
├── tailwind.config.js
├── index.html
├── public/
│   └── favicon.ico
├── src/
│   ├── main.tsx                          // App entry point
│   ├── App.tsx                           // Root component, providers, router
│   │
│   ├── api/
│   │   ├── axiosInstance.ts              // Configured Axios with interceptors
│   │   ├── authApi.ts                    // login, register, refresh, logout
│   │   ├── expenseApi.ts                 // expense CRUD + submit
│   │   ├── receiptApi.ts                 // upload, download
│   │   ├── approvalApi.ts               // pending, approve, reject, bulk
│   │   ├── userApi.ts                    // user management
│   │   ├── categoryApi.ts               // category CRUD
│   │   └── analyticsApi.ts              // analytics endpoints
│   │
│   ├── context/
│   │   └── AuthContext.tsx               // Auth state, login/logout/refresh methods
│   │
│   ├── hooks/
│   │   ├── useAuth.ts                    // Access AuthContext
│   │   ├── useExpenses.ts                // Fetch/paginate expenses
│   │   ├── useExpense.ts                 // Fetch single expense
│   │   ├── usePendingApprovals.ts        // Fetch manager's pending approvals
│   │   ├── useCategories.ts             // Fetch categories
│   │   ├── useAnalytics.ts              // Fetch analytics data
│   │   ├── useUsers.ts                  // Fetch user list
│   │   └── useDebounce.ts              // Debounce for search inputs
│   │
│   ├── pages/
│   │   ├── LoginPage.tsx
│   │   ├── RegisterPage.tsx
│   │   ├── MyExpensesPage.tsx
│   │   ├── ExpenseFormPage.tsx           // Create + Edit (shared form)
│   │   ├── ExpenseDetailPage.tsx
│   │   ├── PendingApprovalsPage.tsx
│   │   ├── TeamStatsPage.tsx
│   │   ├── AdminDashboardPage.tsx
│   │   ├── UserManagementPage.tsx
│   │   ├── CategoryManagementPage.tsx
│   │   ├── ProfilePage.tsx
│   │   └── NotFoundPage.tsx
│   │
│   ├── components/
│   │   ├── layout/
│   │   │   ├── AppLayout.tsx             // Sidebar + header + <Outlet>
│   │   │   ├── Sidebar.tsx               // Role-aware navigation
│   │   │   └── Header.tsx                // User info, logout
│   │   ├── auth/
│   │   │   ├── ProtectedRoute.tsx        // Redirect to login if unauthenticated
│   │   │   └── RoleGuard.tsx             // Redirect if wrong role
│   │   ├── expenses/
│   │   │   ├── ExpenseTable.tsx
│   │   │   ├── ExpenseForm.tsx
│   │   │   ├── ExpenseFilterBar.tsx
│   │   │   ├── ExpenseStatusBadge.tsx
│   │   │   ├── ReceiptUpload.tsx
│   │   │   ├── ReceiptGallery.tsx
│   │   │   └── AuditTimeline.tsx
│   │   ├── approvals/
│   │   │   ├── ApprovalTable.tsx
│   │   │   ├── BulkActions.tsx
│   │   │   └── RejectModal.tsx
│   │   ├── dashboard/
│   │   │   ├── SummaryCards.tsx
│   │   │   ├── CategoryBarChart.tsx
│   │   │   ├── MonthlyTrendLineChart.tsx
│   │   │   ├── TeamSpendTable.tsx
│   │   │   └── DateRangePicker.tsx
│   │   ├── users/
│   │   │   ├── UserTable.tsx
│   │   │   ├── RoleChangeModal.tsx
│   │   │   └── ManagerAssignModal.tsx
│   │   └── common/
│   │       ├── Pagination.tsx
│   │       ├── LoadingSpinner.tsx
│   │       ├── SkeletonLoader.tsx
│   │       ├── Toast.tsx
│   │       ├── ConfirmModal.tsx
│   │       └── EmptyState.tsx
│   │
│   ├── types/
│   │   ├── auth.ts                       // User, LoginRequest, AuthResponse
│   │   ├── expense.ts                    // Expense, ExpenseDetail, CreateExpenseRequest
│   │   ├── approval.ts                   // BulkApprovalRequest, BulkApprovalResult
│   │   ├── category.ts                   // Category
│   │   ├── analytics.ts                  // AnalyticsSummary, CategorySpend, etc.
│   │   ├── user.ts                       // UserProfile, ChangeRoleRequest
│   │   └── common.ts                     // PaginatedResponse, ErrorResponse
│   │
│   └── utils/
│       ├── formatCurrency.ts             // Currency formatting helper
│       ├── formatDate.ts                 // Date formatting helper
│       └── validators.ts                 // Client-side validation rules
│
└── tests/
    ├── setup.ts                          // Test configuration
    ├── pages/
    │   └── LoginPage.test.tsx
    └── components/
        └── ExpenseForm.test.tsx
```

---

## 12. Trade-offs & Alternatives

### 12.1 Multi-Tenancy: Shared Schema vs. Schema-per-Tenant vs. DB-per-Tenant

| | Shared Schema (chosen) | Schema-per-Tenant | DB-per-Tenant |
|---|---|---|---|
| **Isolation** | Application-enforced | Strong (schema boundary) | Strongest (DB boundary) |
| **Migrations** | Single migration applies everywhere | Must run per schema (N schemas) | Must run per DB (N databases) |
| **Connection pooling** | One pool, shared | One pool, schema switching | One pool per tenant or dynamic routing |
| **Query complexity** | Every query needs tenant_id | Clean queries (no tenant_id) | Clean queries |
| **Cross-tenant bugs** | Possible if tenant_id omitted | Harder (wrong schema = error) | Nearly impossible |
| **Cost** | Lowest | Medium | Highest |

**Decision:** Shared schema. The application is an MVP for a homework assignment. The complexity of schema-per-tenant (dynamic schema switching, per-schema migration orchestration) is not justified. We mitigate the isolation risk with defense-in-depth (4 layers of enforcement) and integration tests.

**At scale:** If a tenant required regulatory isolation (e.g., HIPAA, SOC2 per-tenant), we would migrate to schema-per-tenant or DB-per-tenant for that specific tenant while keeping shared schema for standard tenants. This is a common hybrid approach.

### 12.2 JWT Signing: HS256 vs. RS256

| | HS256 (chosen for MVP) | RS256 |
|---|---|---|
| **Key type** | Symmetric (shared secret) | Asymmetric (private/public key pair) |
| **Token issuance** | Only the secret holder can issue AND verify | Private key issues, public key verifies |
| **Performance** | Faster signing/verification | Slower (RSA operations) |
| **Use case** | Single service issues and verifies | Microservices: one issuer, many verifiers |

**Decision:** HS256 for MVP. We have a single monolithic backend that both issues and validates tokens. The simplicity of managing a single secret outweighs RS256's benefits. If we split into microservices where multiple services need to verify tokens without having the signing key, we would switch to RS256.

### 12.3 State Management: Context + Hooks vs. Redux vs. React Query

| | Context + Hooks (chosen) | Redux Toolkit | React Query |
|---|---|---|---|
| **Boilerplate** | Minimal | Moderate (slices, store) | Minimal |
| **Server state caching** | Manual (useState) | Manual (RTK Query adds this) | Built-in (stale-while-revalidate) |
| **Devtools** | React devtools | Redux devtools (excellent) | React Query devtools |
| **Learning curve** | Low | Medium | Low-Medium |
| **Bundle size** | Zero added | ~11KB | ~13KB |

**Decision:** Context + Hooks. Our global state is minimal (just auth). Server data is fetched per-page and does not need cross-page caching. React Query would be a strong choice if we needed background refetching, optimistic updates, or cache invalidation across components -- but for this MVP, the simpler approach reduces abstraction layers and makes the data flow more transparent to reviewers.

### 12.4 File Storage: Local vs. S3 from Day One

| | Local filesystem (chosen) | S3 from day one |
|---|---|---|
| **Setup complexity** | Zero (mkdir) | AWS account, IAM, bucket config, SDK |
| **Dev experience** | Files visible in project dir | Requires localstack or minio for local dev |
| **Cloud readiness** | Requires migration | Ready |
| **Reviewer experience** | Simple to run locally | Adds infrastructure dependency |

**Decision:** Local filesystem with an abstraction interface (`FileStorageService`). The assignment emphasizes "runs in under 10 minutes." Adding S3 would require either AWS credentials or a localstack container, increasing setup friction. The interface ensures swapping to S3 is a class-level change, not a rewrite.

### 12.5 Pagination: Offset-based vs. Cursor-based

| | Offset-based (chosen) | Cursor-based |
|---|---|---|
| **Implementation** | `OFFSET/LIMIT` in SQL | `WHERE id > :lastId LIMIT N` |
| **Performance at scale** | Degrades at high offsets (Postgres scans skipped rows) | Consistent performance |
| **Random page access** | Supported (jump to page 5) | Not easily supported |
| **UI compatibility** | Works with "Page 1, 2, 3..." navigation | Best with "Load More" / infinite scroll |

**Decision:** Offset-based. Our datasets are per-tenant (typically hundreds to low thousands of expenses), not millions. The offset performance issue does not manifest at this scale. Page-number navigation is more intuitive for the table-based UI we are building.

### 12.6 Password Hashing: BCrypt vs. Argon2

| | BCrypt (chosen) | Argon2 |
|---|---|---|
| **Security** | Strong, battle-tested | Stronger (won PHC in 2015) |
| **Spring Support** | Built-in `BCryptPasswordEncoder` | Requires `spring-security-crypto` + Bouncy Castle |
| **Adoption** | Industry standard | Growing but less widespread |
| **Tuning** | Cost factor (rounds) | Memory, parallelism, iterations |

**Decision:** BCrypt with cost factor 12. Spring Security provides it out of the box. Argon2's memory-hard properties provide better resistance to GPU/ASIC attacks, but BCrypt at cost 12 is still considered secure for production use. The simplicity of the built-in integration wins for MVP.

### 12.7 Rate Limiting: In-Memory vs. Redis

| | In-Memory (chosen) | Redis |
|---|---|---|
| **Setup** | Zero dependencies | Redis server required |
| **Multi-instance** | Does not work (each instance has own counters) | Centralized, works across instances |
| **Persistence** | Lost on restart | Survives restart |
| **Latency** | Nanoseconds (HashMap lookup) | ~1ms (network call) |

**Decision:** In-memory for MVP. The application runs as a single instance. Redis would be required in production with load balancing, and is documented as a production improvement.

---

## 13. What I'd Improve With More Time

### Production Hardening (Priority Order)

1. **HttpOnly cookies for refresh tokens**: Move refresh tokens from localStorage to HttpOnly, Secure, SameSite=Strict cookies. This eliminates the XSS vector entirely. Requires CSRF protection (double-submit cookie pattern) since we would be using cookies.

2. **Redis-backed rate limiting**: Replace `ConcurrentHashMap` with Redis for rate limit state. Essential for horizontal scaling.

3. **Redis-backed token blacklist**: Instead of (or in addition to) DB-based refresh token validation, use a Redis blacklist for revoked access tokens. Currently, a stolen access token is valid until expiry (15 min). With a blacklist, we could invalidate access tokens immediately on logout.

4. **Structured logging with correlation IDs**: Add an MDC-based correlation ID to every request (generated in a filter, propagated via ThreadLocal). All log entries for a request share the same correlation ID, making debugging distributed issues tractable.

5. **Health checks and readiness probes**: Spring Boot Actuator `/health` endpoint for container orchestration (Kubernetes liveness/readiness).

6. **Database connection pooling tuning**: Configure HikariCP pool size based on expected concurrency. Add connection leak detection.

7. **Input sanitization**: Add OWASP ESAPI or a custom filter to prevent XSS payloads in text fields (notes, comments, category names).

### Features Deferred to MVP+

1. **Email notifications**: Notify managers when an expense is submitted. Notify employees when approved/rejected. Would use an async event system (Spring Events or a message queue) to decouple notification delivery from the request lifecycle.

2. **Multi-level approval workflows**: Expenses over a threshold require VP approval after manager approval. Would extend the state machine with a PENDING_VP_APPROVAL state and a configurable approval chain per org.

3. **Expense policies**: Per-org configurable rules like "meals over $100 require receipt" or "travel expenses over $500 require VP approval." Would require a policy engine (rules evaluated at submission time).

4. **CSV/PDF export**: Export expense reports for accounting integration. Would use Apache POI (Excel) or OpenPDF for generation, served as a download endpoint.

5. **Approval delegation**: When a manager is on leave, temporarily delegate approval authority to another manager. Would add a `delegated_approver_id` with a date range.

6. **Full-text search**: PostgreSQL full-text search on expense notes and merchant names. Would use `tsvector` columns and `GIN` indexes.

7. **Audit log dashboard**: Admin-visible view of all system actions (not just expense transitions). Would require a broader audit framework beyond expense_audit_log.

8. **Invite flow**: Replace direct org-ID signup with email-based invite tokens. Admin sends invite, user clicks link, completes registration. Adds security by preventing unauthorized org joins.

9. **Multi-currency support**: Allow employees to submit expenses in different currencies with automatic conversion. Requires exchange rate management and a base currency per org.

10. **Real-time notifications**: WebSocket-based push notifications for in-app alerts. Would use Spring WebSocket with STOMP protocol.

### Testing Improvements

1. **End-to-end tests**: Cypress or Playwright tests covering critical user flows (login, submit expense, approve expense, view dashboard).
2. **Load testing**: Gatling or k6 scripts to verify rate limiting under load and identify performance bottlenecks.
3. **Security scanning**: OWASP ZAP automated scan against the running API to identify common vulnerabilities.
4. **Contract testing**: Pact tests between frontend API calls and backend endpoints to catch breaking API changes.

### Infrastructure Improvements

1. **CI/CD pipeline**: GitHub Actions workflow: lint, test, build Docker images, run integration tests, deploy.
2. **Environment separation**: Separate config profiles for dev, staging, production with different DB credentials, JWT secrets, rate limits.
3. **Cloud file storage**: S3 implementation of `FileStorageService` with pre-signed URLs for direct browser uploads (bypass backend for large files).
4. **Database read replicas**: Route analytics queries to a read replica to avoid impacting transactional performance.
5. **API documentation**: OpenAPI/Swagger spec generated from controller annotations. Swagger UI at `/swagger-ui.html`.

---

*This document captures the architectural decisions, their justifications, and the trade-offs made for the Multi-Tenant Expense Tracker. It is intended to serve both as a design guide for implementation and as an evaluation artifact demonstrating depth of thinking about the problem space.*
