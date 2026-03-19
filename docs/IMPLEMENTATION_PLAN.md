# Implementation Plan: Multi-Tenant Expense Tracker

**Date:** 2026-03-18
**Author:** Karthik Raj (Tech Lead)
**Status:** Ready for Execution
**Related Docs:** DESIGN.md, TECHNICAL_STORIES_Problem1.md, PM_ANALYSIS_Problem1.md

---

## Table of Contents

1. [Module Breakdown](#1-module-breakdown)
2. [Shared Contracts & Interfaces](#2-shared-contracts--interfaces)
3. [Parallel Execution Plan](#3-parallel-execution-plan)
4. [Agent Assignment Strategy](#4-agent-assignment-strategy)
5. [Integration Points & Merge Strategy](#5-integration-points--merge-strategy)

---

## 1. Module Breakdown

### Phase 1 — Foundation (must complete before Phase 2)

All Phase 1 modules can execute in parallel. They produce the skeleton that all subsequent modules build upon.

---

#### M1: Project Scaffolding

| Field | Value |
|-------|-------|
| **Module ID** | M1 |
| **Module Name** | Project Scaffolding |
| **Description** | Initialize the Spring Boot backend project and React frontend project. Set up build configuration, Docker Compose for PostgreSQL, application.yml profiles, and all base dependencies. This creates the empty project structure that every other module populates. |
| **Stories** | Foundation for all stories (no direct story mapping — this is infrastructure) |
| **Dependencies** | None |
| **Inputs** | Project structure from DESIGN.md Section 11 |
| **Outputs** | Compilable Spring Boot project, runnable React dev server, Docker Compose with PostgreSQL, directory structure for all packages |
| **Estimated Complexity** | M |

**Files to Create:**

Backend:
```
expense-tracker-api/pom.xml
expense-tracker-api/src/main/java/com/expensetracker/ExpenseTrackerApplication.java
expense-tracker-api/src/main/resources/application.yml
expense-tracker-api/src/main/resources/application-dev.yml
expense-tracker-api/src/main/resources/application-docker.yml
```

Frontend:
```
expense-tracker-ui/package.json
expense-tracker-ui/tsconfig.json
expense-tracker-ui/vite.config.ts
expense-tracker-ui/tailwind.config.js
expense-tracker-ui/postcss.config.js
expense-tracker-ui/index.html
expense-tracker-ui/src/main.tsx
expense-tracker-ui/src/App.tsx
expense-tracker-ui/src/index.css
```

Infrastructure:
```
docker-compose.yml
.gitignore
```

**Detailed Instructions:**

1. **Backend (Spring Boot 3.x, Java 17+, Maven):**
   - Generate via Spring Initializr or manually create `pom.xml` with these dependencies:
     - `spring-boot-starter-web`
     - `spring-boot-starter-data-jpa`
     - `spring-boot-starter-security`
     - `spring-boot-starter-validation`
     - `flyway-core` + `flyway-database-postgresql`
     - `postgresql` (runtime)
     - `io.jsonwebtoken:jjwt-api:0.12.6`, `jjwt-impl`, `jjwt-jackson`
     - `spring-boot-starter-test` (test)
     - `org.testcontainers:postgresql` (test)
     - `org.testcontainers:junit-jupiter` (test)
   - Java version: 17
   - Spring Boot version: 3.3.x (latest stable 3.x)
   - Group ID: `com.expensetracker`, Artifact ID: `expense-tracker-api`
   - Create empty package directories matching DESIGN.md Section 11: `config/`, `security/`, `filter/`, `ratelimit/`, `controller/`, `service/`, `service/impl/`, `repository/`, `model/`, `model/enums/`, `dto/request/`, `dto/response/`, `exception/`
   - `application.yml`: Configure Spring datasource for PostgreSQL (`jdbc:postgresql://localhost:5432/expense_tracker`), JPA properties (`hibernate.ddl-auto: validate`, `show-sql: true`), Flyway enabled, server port 8080, multipart max file size 5MB, max request size 10MB
   - `application-dev.yml`: Debug logging, local DB URL
   - `application-docker.yml`: DB host `postgres` (Docker network)
   - `ExpenseTrackerApplication.java`: Standard `@SpringBootApplication` main class

2. **Frontend (React 18, TypeScript 5.x, Vite 5.x):**
   - Initialize with `npm create vite@latest expense-tracker-ui -- --template react-ts`
   - Add dependencies: `react-router-dom@6`, `axios@1`, `recharts@2`
   - Add dev dependencies: `tailwindcss@3`, `postcss`, `autoprefixer`, `@types/react`, `@types/react-dom`
   - Configure Tailwind CSS: init, set content paths, import in `index.css`
   - `vite.config.ts`: Configure proxy for `/api` to `http://localhost:8080` (dev server proxies to backend)
   - Create empty directory structure matching DESIGN.md Section 11: `api/`, `context/`, `hooks/`, `pages/`, `components/layout/`, `components/auth/`, `components/expenses/`, `components/approvals/`, `components/dashboard/`, `components/users/`, `components/common/`, `types/`, `utils/`
   - `App.tsx`: Minimal component rendering `<h1>Expense Tracker</h1>` (placeholder)

3. **Docker Compose:**
   ```yaml
   services:
     postgres:
       image: postgres:15-alpine
       environment:
         POSTGRES_DB: expense_tracker
         POSTGRES_USER: expense_user
         POSTGRES_PASSWORD: expense_pass
       ports:
         - "5432:5432"
       volumes:
         - postgres_data:/var/lib/postgresql/data
   volumes:
     postgres_data:
   ```

4. **Verification:** `mvn compile` succeeds. `npm install && npm run dev` starts Vite. `docker-compose up -d` starts PostgreSQL.

---

#### M2: Database Migrations

| Field | Value |
|-------|-------|
| **Module ID** | M2 |
| **Module Name** | Database Migrations |
| **Description** | All Flyway SQL migrations for the complete schema: organizations, users, refresh_tokens, expense_categories, expenses, expense_receipts, expense_audit_log. Includes seed data for development/demo with 2 sample organizations and demo users. |
| **Stories** | S1.1 (Tenant Data Model & Seed Infrastructure), S3.1 (User Data Model), S4.1 (Expense Data Model) |
| **Dependencies** | None (can run before M1 completes — migrations are plain SQL files) |
| **Inputs** | Schema definitions from DESIGN.md Section 3 |
| **Outputs** | 8 Flyway migration files that produce the complete database schema and demo data |
| **Estimated Complexity** | M |

**Files to Create:**
```
expense-tracker-api/src/main/resources/db/migration/V1__create_organizations.sql
expense-tracker-api/src/main/resources/db/migration/V2__create_users.sql
expense-tracker-api/src/main/resources/db/migration/V3__create_refresh_tokens.sql
expense-tracker-api/src/main/resources/db/migration/V4__create_expense_categories.sql
expense-tracker-api/src/main/resources/db/migration/V5__create_expenses.sql
expense-tracker-api/src/main/resources/db/migration/V6__create_expense_receipts.sql
expense-tracker-api/src/main/resources/db/migration/V7__create_expense_audit_log.sql
expense-tracker-api/src/main/resources/db/migration/V8__seed_demo_data.sql
```

**Detailed Instructions:**

Use the EXACT SQL from DESIGN.md Section 3 for each table. Key points:

1. **V1__create_organizations.sql:**
   - `organizations` table as defined in DESIGN.md
   - Enable `gen_random_uuid()` if needed (PostgreSQL 13+ has it built-in, but add `CREATE EXTENSION IF NOT EXISTS "pgcrypto";` for safety)

2. **V2__create_users.sql:**
   - `users` table with all columns from DESIGN.md Section 3
   - Self-referential FK `manager_id REFERENCES users(id)`
   - CHECK constraint on `role IN ('EMPLOYEE', 'MANAGER', 'ADMIN')`
   - All 4 indexes: `idx_users_tenant_id`, `idx_users_tenant_role`, `idx_users_tenant_manager`, `idx_users_email`

3. **V3__create_refresh_tokens.sql:**
   - `refresh_tokens` table
   - Indexes: `idx_refresh_tokens_user`, `idx_refresh_tokens_hash`

4. **V4__create_expense_categories.sql:**
   - `expense_categories` table with `UNIQUE(tenant_id, name)`
   - Index: `idx_categories_tenant`

5. **V5__create_expenses.sql:**
   - `expenses` table with all columns from DESIGN.md
   - CHECK constraint on `status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','CANCELLED')`
   - All 5 indexes

6. **V6__create_expense_receipts.sql:**
   - `expense_receipts` table
   - Index: `idx_receipts_expense`

7. **V7__create_expense_audit_log.sql:**
   - `expense_audit_log` table with CHECK constraint on `action`
   - Indexes: `idx_audit_expense`, `idx_audit_created`

8. **V8__seed_demo_data.sql:**
   - Insert 2 organizations: `Acme Corp` (slug: `acme-corp`, currency: `USD`) and `Globex Inc` (slug: `globex-inc`, currency: `USD`)
   - For Acme Corp: Insert 1 Admin user, 1 Manager user, 2 Employee users (with manager_id set). Passwords all BCrypt hash of `Password1` (cost 12). Use a pre-computed hash: `$2a$12$LJ3m4ys3uz5uMUimGFnGT.N5BmIXCnGCaF.Ny3sTzqEMz/OoOKBMi`
   - For Globex Inc: Insert 1 Admin user, 1 Manager user, 1 Employee user
   - Insert default expense categories for each org: Travel, Meals, Office Supplies, Software, Equipment, Other
   - Insert a few sample expenses in various states (DRAFT, SUBMITTED, APPROVED, REJECTED) for demo purposes

**Verification:** Start PostgreSQL via Docker Compose, run `mvn spring-boot:run` — Flyway should execute all 8 migrations without errors. Connect to DB and verify all tables exist with correct columns, constraints, and indexes.

---

#### M3: Core Entities & Repositories

| Field | Value |
|-------|-------|
| **Module ID** | M3 |
| **Module Name** | Core Entities & Repositories |
| **Description** | All JPA entity classes, enums (Role, ExpenseStatus, AuditAction), base repository interfaces, TenantContext utility class, SecurityUtils helper, and the TenantAware base entity. These are the shared data layer building blocks that every service module depends on. |
| **Stories** | S1.2 (Tenant Isolation at Service/Repository Layer), S3.1 (User Data Model), S4.1 (Expense Data Model) |
| **Dependencies** | None (can be written before M1/M2 complete; just needs the same package structure) |
| **Inputs** | Schema from DESIGN.md Section 3, entity structure from Section 11 |
| **Outputs** | All entity classes, enums, repository interfaces, TenantContext, SecurityUtils |
| **Estimated Complexity** | M |

**Files to Create:**

Enums:
```
expense-tracker-api/src/main/java/com/expensetracker/model/enums/Role.java
expense-tracker-api/src/main/java/com/expensetracker/model/enums/ExpenseStatus.java
expense-tracker-api/src/main/java/com/expensetracker/model/enums/AuditAction.java
```

Entities:
```
expense-tracker-api/src/main/java/com/expensetracker/model/Organization.java
expense-tracker-api/src/main/java/com/expensetracker/model/User.java
expense-tracker-api/src/main/java/com/expensetracker/model/RefreshToken.java
expense-tracker-api/src/main/java/com/expensetracker/model/Expense.java
expense-tracker-api/src/main/java/com/expensetracker/model/ExpenseReceipt.java
expense-tracker-api/src/main/java/com/expensetracker/model/ExpenseAuditLog.java
expense-tracker-api/src/main/java/com/expensetracker/model/ExpenseCategory.java
```

Repositories:
```
expense-tracker-api/src/main/java/com/expensetracker/repository/OrganizationRepository.java
expense-tracker-api/src/main/java/com/expensetracker/repository/UserRepository.java
expense-tracker-api/src/main/java/com/expensetracker/repository/RefreshTokenRepository.java
expense-tracker-api/src/main/java/com/expensetracker/repository/ExpenseRepository.java
expense-tracker-api/src/main/java/com/expensetracker/repository/ExpenseReceiptRepository.java
expense-tracker-api/src/main/java/com/expensetracker/repository/ExpenseAuditLogRepository.java
expense-tracker-api/src/main/java/com/expensetracker/repository/ExpenseCategoryRepository.java
```

Security utilities:
```
expense-tracker-api/src/main/java/com/expensetracker/security/TenantContext.java
expense-tracker-api/src/main/java/com/expensetracker/security/SecurityUtils.java
```

**Detailed Instructions:**

1. **Enums:**

   ```java
   // Role.java
   public enum Role {
       EMPLOYEE, MANAGER, ADMIN
   }

   // ExpenseStatus.java
   public enum ExpenseStatus {
       DRAFT, SUBMITTED, APPROVED, REJECTED, CANCELLED
   }

   // AuditAction.java
   public enum AuditAction {
       CREATED, SUBMITTED, APPROVED, REJECTED, RESUBMITTED, CANCELLED, REASSIGNED
   }
   ```

2. **Entity classes** — each must:
   - Use `@Entity` and `@Table(name = "...")` annotations
   - Use `UUID` primary key with `@Id` and `@GeneratedValue(strategy = GenerationType.UUID)`
   - Map columns exactly matching the migration SQL (column names, types, nullability)
   - Use `@Enumerated(EnumType.STRING)` for enum fields
   - Include `@Column(name = "created_at", updatable = false)` with `@CreationTimestamp`
   - Include `@Column(name = "updated_at")` with `@UpdateTimestamp` where applicable
   - Use `@Column(name = "tenant_id", nullable = false)` for tenant-scoped entities (NOT on `RefreshToken` or `ExpenseReceipt` or `ExpenseAuditLog`)
   - Map relationships with `@ManyToOne(fetch = FetchType.LAZY)` and `@JoinColumn`

   **Organization entity:**
   - Fields: id, name, slug, currency, isActive, createdAt, updatedAt
   - No tenant_id (this IS the tenant table)

   **User entity:**
   - Fields: id, tenantId, email, passwordHash, firstName, lastName, role (Role enum), managerId (UUID, nullable), isActive, failedLoginAttempts, lockedUntil, createdAt, updatedAt
   - `@ManyToOne(fetch = LAZY)` for organization (tenantId), manager (managerId)

   **RefreshToken entity:**
   - Fields: id, userId, tokenHash, expiresAt, isRevoked, replacedById (self-referential UUID, nullable), createdAt
   - `@ManyToOne(fetch = LAZY)` for user

   **ExpenseCategory entity:**
   - Fields: id, tenantId, name, isActive, createdAt

   **Expense entity:**
   - Fields: id, tenantId, submitterId, managerId, amount (BigDecimal), currency, categoryId, merchantName, expenseDate (LocalDate), notes, status (ExpenseStatus), rejectionComment, approvedById, approvedAt (LocalDateTime), createdAt, updatedAt
   - All FK fields as `@ManyToOne(fetch = LAZY)` relationships

   **ExpenseReceipt entity:**
   - Fields: id, expenseId, fileName, filePath, contentType, fileSize (long), createdAt
   - No tenant_id column

   **ExpenseAuditLog entity:**
   - Fields: id, expenseId, action (AuditAction), performedById, comment, oldStatus, newStatus, createdAt
   - No tenant_id column

3. **Repository interfaces** — each extends `JpaRepository<Entity, UUID>`. Add the following custom query methods:

   **OrganizationRepository:**
   - `Optional<Organization> findBySlug(String slug)`
   - `Optional<Organization> findByIdAndIsActiveTrue(UUID id)`

   **UserRepository:**
   - `Optional<User> findByEmail(String email)`
   - `boolean existsByEmail(String email)`
   - `Optional<User> findByIdAndTenantId(UUID id, UUID tenantId)`
   - `Page<User> findByTenantId(UUID tenantId, Pageable pageable)`
   - `Page<User> findByTenantIdAndRole(UUID tenantId, Role role, Pageable pageable)`
   - `List<User> findByTenantIdAndManagerId(UUID tenantId, UUID managerId)`
   - `long countByTenantIdAndManagerIdAndIsActiveTrue(UUID tenantId, UUID managerId)`

   **RefreshTokenRepository:**
   - `Optional<RefreshToken> findByTokenHash(String tokenHash)`
   - `@Modifying @Query("UPDATE RefreshToken r SET r.isRevoked = true WHERE r.userId = :userId AND r.isRevoked = false")`
     `void revokeAllByUserId(@Param("userId") UUID userId)`

   **ExpenseCategoryRepository:**
   - `List<ExpenseCategory> findByTenantIdAndIsActiveTrue(UUID tenantId)`
   - `Optional<ExpenseCategory> findByIdAndTenantId(UUID id, UUID tenantId)`
   - `boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name)`

   **ExpenseRepository:**
   - `Optional<Expense> findByIdAndTenantId(UUID id, UUID tenantId)`
   - `Page<Expense> findByTenantIdAndSubmitterId(UUID tenantId, UUID submitterId, Pageable pageable)`
   - `Page<Expense> findByTenantIdAndManagerIdAndStatus(UUID tenantId, UUID managerId, ExpenseStatus status, Pageable pageable)`
   - `Page<Expense> findByTenantIdAndStatus(UUID tenantId, ExpenseStatus status, Pageable pageable)`
   - Custom `@Query` methods for filtered listing (with optional status, categoryId, date range)
   - Custom `@Query` methods for analytics (see M9 for details — just define the repository method signatures here):
     ```java
     @Query("SELECT e.category.name, SUM(e.amount), COUNT(e) FROM Expense e " +
            "WHERE e.tenantId = :tenantId AND e.status = 'APPROVED' " +
            "AND e.expenseDate BETWEEN :from AND :to GROUP BY e.category.name")
     List<Object[]> findSpendByCategory(@Param("tenantId") UUID tenantId,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to);
     ```

   **ExpenseReceiptRepository:**
   - `List<ExpenseReceipt> findByExpenseId(UUID expenseId)`
   - `Optional<ExpenseReceipt> findByIdAndExpenseId(UUID id, UUID expenseId)`
   - `long countByExpenseId(UUID expenseId)`

   **ExpenseAuditLogRepository:**
   - `List<ExpenseAuditLog> findByExpenseIdOrderByCreatedAtAsc(UUID expenseId)`

4. **TenantContext** — implement exactly as shown in DESIGN.md Section 7:
   - ThreadLocal-based, with `setCurrentTenant(UUID)`, `getCurrentTenant()`, `clear()`
   - `getCurrentTenant()` throws `IllegalStateException` if no tenant set

5. **SecurityUtils** — static helper class:
   ```java
   public final class SecurityUtils {
       public static UUID getCurrentUserId() { /* extract from SecurityContext */ }
       public static UUID getCurrentTenantId() { return TenantContext.getCurrentTenant(); }
       public static String getCurrentRole() { /* extract from SecurityContext authorities */ }
       public static boolean isAdmin() { return "ADMIN".equals(getCurrentRole()); }
       public static boolean isManager() { return "MANAGER".equals(getCurrentRole()); }
   }
   ```

**Verification:** `mvn compile` succeeds. All entities, enums, repositories, and utility classes are syntactically correct and reference each other properly.

---

### Phase 2 — Backend Services (can be parallelized after Phase 1)

All Phase 2 modules can execute IN PARALLEL with each other. Each module depends on Phase 1 outputs (entities, repositories, TenantContext) but not on other Phase 2 modules.

---

#### M4: Authentication Module

| Field | Value |
|-------|-------|
| **Module ID** | M4 |
| **Module Name** | Authentication Module |
| **Description** | Spring Security configuration, JWT token generation/validation, JwtAuthenticationFilter, TenantContextFilter, AuthController (register, login, refresh, logout), RefreshTokenService, account lockout logic, BCrypt password encoding. |
| **Stories** | S2.1 (User Registration), S2.2 (Login & JWT Issuance), S2.3 (Refresh Token Rotation), S2.4 (Spring Security Filter Chain) |
| **Dependencies** | M1 (project structure), M2 (migrations), M3 (User entity, RefreshToken entity, OrganizationRepository, UserRepository, RefreshTokenRepository, Role enum, TenantContext) |
| **Inputs** | User entity, RefreshToken entity, UserRepository, RefreshTokenRepository, OrganizationRepository, TenantContext, SecurityUtils |
| **Outputs** | Working auth endpoints (`/api/v1/auth/*`), configured Spring Security filter chain, JWT generation/validation, TenantContext population |
| **Estimated Complexity** | L |

**Files to Create/Modify:**
```
expense-tracker-api/src/main/java/com/expensetracker/config/SecurityConfig.java
expense-tracker-api/src/main/java/com/expensetracker/config/CorsConfig.java
expense-tracker-api/src/main/java/com/expensetracker/security/JwtTokenProvider.java
expense-tracker-api/src/main/java/com/expensetracker/security/JwtAuthenticationFilter.java
expense-tracker-api/src/main/java/com/expensetracker/security/TenantContextFilter.java
expense-tracker-api/src/main/java/com/expensetracker/service/AuthService.java
expense-tracker-api/src/main/java/com/expensetracker/service/RefreshTokenService.java
expense-tracker-api/src/main/java/com/expensetracker/controller/AuthController.java
expense-tracker-api/src/main/java/com/expensetracker/dto/request/RegisterRequest.java
expense-tracker-api/src/main/java/com/expensetracker/dto/request/LoginRequest.java
expense-tracker-api/src/main/java/com/expensetracker/dto/request/RefreshRequest.java
expense-tracker-api/src/main/java/com/expensetracker/dto/response/AuthResponse.java
expense-tracker-api/src/main/java/com/expensetracker/dto/response/UserDto.java
```

**Detailed Instructions:**

1. **SecurityConfig.java** (`@Configuration @EnableWebSecurity @EnableMethodSecurity`):
   - Disable CSRF (stateless JWT auth)
   - Set session management to STATELESS
   - Configure CORS (allow frontend origin `http://localhost:5173`, credentials true)
   - Define public endpoints: `/api/v1/auth/register`, `/api/v1/auth/login`, `/api/v1/auth/refresh`
   - All other `/api/**` endpoints require authentication
   - Register custom filters in order:
     1. `JwtAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`
     2. `TenantContextFilter` after `JwtAuthenticationFilter`
   - Configure `BCryptPasswordEncoder` bean (strength 12)
   - Configure `AuthenticationManager` bean

2. **JwtTokenProvider.java:**
   - Properties: `jwt.secret` (from application.yml, min 256-bit), `jwt.access-token-expiry` (15 min), `jwt.refresh-token-expiry` (7 days)
   - `generateAccessToken(UUID userId, UUID tenantId, String role)`: Creates JWT with claims `sub` (userId), `tenantId`, `role`, `iat`, `exp`. Signs with HS256.
   - `validateToken(String token)`: Validates signature, checks expiry, returns boolean
   - `extractUserId(String token)`: Returns UUID from `sub` claim
   - `extractTenantId(String token)`: Returns UUID from `tenantId` claim
   - `extractRole(String token)`: Returns String from `role` claim
   - `generateRefreshToken()`: Returns a secure random 32-byte string, Base64-encoded
   - Use JJWT library (io.jsonwebtoken)
   - Add to `application.yml`:
     ```yaml
     jwt:
       secret: "your-256-bit-secret-key-here-must-be-at-least-32-characters-long"
       access-token-expiry: 900000    # 15 minutes in ms
       refresh-token-expiry: 604800000 # 7 days in ms
     ```

3. **JwtAuthenticationFilter.java** (extends `OncePerRequestFilter`):
   - Skip filter for public endpoints (`/api/v1/auth/register`, `/api/v1/auth/login`, `/api/v1/auth/refresh`)
   - Extract Bearer token from `Authorization` header
   - Validate token using `JwtTokenProvider`
   - Extract userId, tenantId, role from token
   - Create `UsernamePasswordAuthenticationToken` with authorities (`ROLE_` + role)
   - Store userId and tenantId as details on the auth token
   - Set in `SecurityContextHolder`
   - On failure: write JSON 401 response `{"error": "...", "code": "UNAUTHORIZED"}`

4. **TenantContextFilter.java** (extends `OncePerRequestFilter`):
   - Skip for public endpoints
   - Extract tenantId from `SecurityContextHolder.getContext().getAuthentication()`
   - `TenantContext.setCurrentTenant(tenantId)` in try block
   - `TenantContext.clear()` in finally block
   - Always call `filterChain.doFilter()` within the try

5. **AuthService.java:**
   - `register(RegisterRequest)`: Validate org exists and is active, check email uniqueness, hash password (BCrypt 12), create User with EMPLOYEE role and null manager, save, generate tokens, return AuthResponse
   - `login(LoginRequest)`: Find user by email, check account lock (`locked_until > now()` returns 429), verify password, on fail increment `failed_login_attempts` (lock at 5), on success reset attempts and generate tokens
   - `refresh(String refreshToken)`: Hash the token (SHA-256), find in DB, check not expired/revoked, perform rotation (revoke old, create new, link via `replaced_by_id`), detect reuse (if already revoked, revoke all user tokens)
   - `logout(String refreshToken)`: Hash, find, set `is_revoked = true`

6. **RefreshTokenService.java:**
   - `createRefreshToken(UUID userId)`: Generate random token, SHA-256 hash it, save hash to DB with 7-day expiry, return raw token
   - `rotateRefreshToken(RefreshToken oldToken)`: Revoke old, create new, link chain
   - `revokeAllForUser(UUID userId)`: Bulk revoke via repository
   - `detectReuse(RefreshToken token)`: If token is already revoked, walk the chain and revoke all tokens for that user

7. **AuthController.java:**
   - `POST /api/v1/auth/register` → `AuthResponse` (201)
   - `POST /api/v1/auth/login` → `AuthResponse` (200)
   - `POST /api/v1/auth/refresh` → `AuthResponse` (200)
   - `POST /api/v1/auth/logout` → message (200)
   - Use `@Valid` on request bodies
   - Return proper error responses (409 for duplicate email, 401 for bad credentials, 429 for lockout)

8. **DTOs:**
   - `RegisterRequest`: email, password, firstName, lastName, organizationId — all with `@NotBlank`/`@NotNull` validation annotations. Password: `@Size(min = 8)` + custom pattern validator for uppercase + digit.
   - `LoginRequest`: email, password — `@NotBlank`
   - `RefreshRequest`: refreshToken — `@NotBlank`
   - `AuthResponse`: accessToken, refreshToken, UserDto(user)
   - `UserDto`: id, email, firstName, lastName, role, organizationId, organizationName, managerId, managerName, isActive, createdAt

**Verification:**
- Start app, call `POST /api/v1/auth/register` with valid body → 201 with tokens
- Call `POST /api/v1/auth/login` → 200 with tokens
- Call `GET /api/v1/expenses` without token → 401
- Call `GET /api/v1/expenses` with valid token → 200 (or 403 depending on role — endpoint may not be implemented yet, but auth should pass)
- Call `POST /api/v1/auth/refresh` with valid refresh token → 200 with new tokens
- Attempt login 5 times with wrong password → 429 on 6th attempt

---

#### M5: User Management Module

| Field | Value |
|-------|-------|
| **Module ID** | M5 |
| **Module Name** | User Management Module |
| **Description** | UserService and UserController for admin user management: list users, update role, assign manager, deactivate user. Includes manager reassignment logic with pending expense handling. |
| **Stories** | S3.2 (Admin: List & Manage Users), S3.3 (Admin: Reassign Manager), S3.4 (Admin: Deactivate User) |
| **Dependencies** | M1, M2, M3 (entities + repositories) |
| **Inputs** | User entity, UserRepository, ExpenseRepository, RefreshTokenRepository, TenantContext, SecurityUtils |
| **Outputs** | User management endpoints (`/api/v1/users/*`) |
| **Estimated Complexity** | M |

**Files to Create:**
```
expense-tracker-api/src/main/java/com/expensetracker/service/UserService.java
expense-tracker-api/src/main/java/com/expensetracker/controller/UserController.java
expense-tracker-api/src/main/java/com/expensetracker/dto/request/ChangeRoleRequest.java
expense-tracker-api/src/main/java/com/expensetracker/dto/request/AssignManagerRequest.java
```

**Detailed Instructions:**

1. **UserService.java:**
   - `listUsers(UUID tenantId, String roleFilter, String search, Pageable pageable)`: Query users within tenant, support role filter and name/email search, return paginated results
   - `changeRole(UUID tenantId, UUID userId, Role newRole)`:
     - Find user by id and tenantId (404 if not found)
     - If changing FROM Manager: check `countByTenantIdAndManagerIdAndIsActiveTrue(tenantId, userId)` > 0 → throw 409 "Reassign employees before changing this user's role"
     - Update role, save
   - `assignManager(UUID tenantId, UUID userId, UUID managerId)`:
     - Validate managerId references a MANAGER or ADMIN in the same tenant (400 if not)
     - Update user's managerId
     - Reassign all SUBMITTED expenses from old manager to new manager
     - Log each reassignment in expense_audit_log with action=REASSIGNED
   - `deactivateUser(UUID tenantId, UUID userId, UUID performedByUserId)`:
     - Cannot deactivate self (409)
     - If Manager with active reports → 409 "Reassign employees before deactivating"
     - Set is_active = false
     - Revoke all refresh tokens for user
     - Cancel all SUBMITTED expenses (set status = CANCELLED, audit log entry)
     - Cancel all DRAFT expenses (set status = CANCELLED, audit log entry)

2. **UserController.java** — all endpoints `@PreAuthorize("hasRole('ADMIN')")`:
   - `GET /api/v1/users` → paginated UserDto list
   - `PUT /api/v1/users/{id}/role` → updated UserDto
   - `PUT /api/v1/users/{id}/manager` → updated UserDto
   - `PUT /api/v1/users/{id}/deactivate` → updated UserDto

3. **DTOs:**
   - `ChangeRoleRequest`: `role` (String, validated against Role enum)
   - `AssignManagerRequest`: `managerId` (UUID, @NotNull)

**Verification:**
- As Admin: `GET /api/v1/users` → returns users in own org only
- As Admin: `PUT /api/v1/users/{id}/role` with `{"role": "MANAGER"}` → user role updated
- As Admin: `PUT /api/v1/users/{id}/manager` → manager assigned, pending expenses reassigned
- As Employee: `GET /api/v1/users` → 403

---

#### M6: Expense CRUD Module

| Field | Value |
|-------|-------|
| **Module ID** | M6 |
| **Module Name** | Expense CRUD Module |
| **Description** | ExpenseService and ExpenseController for expense create, update, list, get, submit, delete. Also includes CategoryService and CategoryController for expense category management. Implements expense state machine transitions for DRAFT, SUBMITTED, and REJECTED→SUBMITTED. |
| **Stories** | S4.2 (Create & Save Draft), S4.3 (Submit Expense), S4.5 (List Own Expenses), S6.1 (Expense Categories Management) |
| **Dependencies** | M1, M2, M3 (entities + repositories) |
| **Inputs** | Expense entity, ExpenseCategory entity, ExpenseAuditLog entity, User entity, repositories, TenantContext, SecurityUtils |
| **Outputs** | Expense CRUD endpoints (`/api/v1/expenses/*`), Category endpoints (`/api/v1/categories/*`), AuditLogService |
| **Estimated Complexity** | L |

**Files to Create:**
```
expense-tracker-api/src/main/java/com/expensetracker/service/ExpenseService.java
expense-tracker-api/src/main/java/com/expensetracker/service/CategoryService.java
expense-tracker-api/src/main/java/com/expensetracker/service/AuditLogService.java
expense-tracker-api/src/main/java/com/expensetracker/controller/ExpenseController.java
expense-tracker-api/src/main/java/com/expensetracker/controller/CategoryController.java
expense-tracker-api/src/main/java/com/expensetracker/dto/request/CreateExpenseRequest.java
expense-tracker-api/src/main/java/com/expensetracker/dto/request/UpdateExpenseRequest.java
expense-tracker-api/src/main/java/com/expensetracker/dto/response/ExpenseDto.java
expense-tracker-api/src/main/java/com/expensetracker/dto/response/ExpenseDetailDto.java
expense-tracker-api/src/main/java/com/expensetracker/dto/response/ExpenseSummaryDto.java
expense-tracker-api/src/main/java/com/expensetracker/dto/response/CategoryDto.java
expense-tracker-api/src/main/java/com/expensetracker/dto/response/AuditLogDto.java
expense-tracker-api/src/main/java/com/expensetracker/dto/response/ReceiptDto.java
```

**Detailed Instructions:**

1. **AuditLogService.java:**
   - `log(UUID expenseId, AuditAction action, UUID performedById, String comment, ExpenseStatus oldStatus, ExpenseStatus newStatus)`: Create and save ExpenseAuditLog entry
   - `getAuditTrail(UUID expenseId)`: Return ordered list of audit entries

2. **CategoryService.java:**
   - `listActive(UUID tenantId)`: Return all active categories for tenant
   - `create(UUID tenantId, String name)`: Check uniqueness (case-insensitive) within tenant, create
   - `rename(UUID tenantId, UUID categoryId, String newName)`: Find by id+tenantId, rename
   - `deactivate(UUID tenantId, UUID categoryId)`: Soft-delete (is_active = false)

3. **ExpenseService.java** — this is the core business logic module:
   - `createExpense(CreateExpenseRequest req)`:
     - Get tenantId from TenantContext, userId from SecurityUtils
     - Create Expense in DRAFT status
     - Set currency from user's organization
     - Audit log: CREATED
     - Return ExpenseDto

   - `updateExpense(UUID expenseId, UpdateExpenseRequest req)`:
     - Find expense by id + tenantId (404 if not found)
     - Assert current user is submitter (403 if not)
     - Assert status is DRAFT or REJECTED (409 if not)
     - Update fields (only non-null values from request)
     - Audit log (no status change, just update)
     - Return ExpenseDto

   - `getExpense(UUID expenseId)`:
     - Find by id + tenantId (404 if not found)
     - Access check: submitter sees own, manager sees assigned team's, admin sees all in org
     - Return ExpenseDetailDto (with receipts + audit trail)

   - `listExpenses(UUID tenantId, UUID submitterId, ExpenseStatus status, UUID categoryId, LocalDate fromDate, LocalDate toDate, Pageable pageable)`:
     - Build dynamic query with optional filters
     - Return paginated ExpenseSummaryDto list

   - `submitExpense(UUID expenseId)`:
     - Find expense, assert submitter
     - Assert status is DRAFT or REJECTED (409 if not)
     - Validate required fields: amount > 0, categoryId valid + active, expenseDate not null and not future
     - Check submitter has manager_id assigned (400 if null: "No manager assigned")
     - Snapshot manager_id onto expense record
     - Set status = SUBMITTED
     - If was REJECTED: clear rejectionComment, audit action = RESUBMITTED
     - Else: audit action = SUBMITTED
     - Return ExpenseDto

   - `deleteExpense(UUID expenseId)`:
     - Find expense, assert submitter, assert DRAFT status (409 if not)
     - Hard delete from DB
     - Delete associated receipts from filesystem (via FileStorageService — for now just delete DB records if FileStorageService is not available yet; M8 will handle files)
     - No audit log (record is gone)

4. **ExpenseController.java:**
   - `POST /api/v1/expenses` → `@PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER')")` → 201
   - `PUT /api/v1/expenses/{id}` → `@PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER')")` → 200
   - `GET /api/v1/expenses/{id}` → any authenticated → 200
   - `GET /api/v1/expenses` → any authenticated → 200 (paginated)
   - `POST /api/v1/expenses/{id}/submit` → `@PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER')")` → 200
   - `DELETE /api/v1/expenses/{id}` → `@PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER')")` → 204

5. **CategoryController.java:**
   - `GET /api/v1/categories` → any authenticated → 200 (list, non-paginated)
   - `POST /api/v1/categories` → `@PreAuthorize("hasRole('ADMIN')")` → 201
   - `PUT /api/v1/categories/{id}` → `@PreAuthorize("hasRole('ADMIN')")` → 200
   - `DELETE /api/v1/categories/{id}` → `@PreAuthorize("hasRole('ADMIN')")` → 204

6. **DTOs:**
   - `CreateExpenseRequest`: amount (BigDecimal, optional), categoryId (UUID, optional), merchantName (String, optional), expenseDate (LocalDate, optional), notes (String, optional)
   - `UpdateExpenseRequest`: same fields, all optional
   - `ExpenseDto`: id, amount, currency, category (CategoryDto), merchantName, expenseDate, notes, status, submitter (id+name), receiptCount, createdAt, updatedAt
   - `ExpenseDetailDto`: extends ExpenseDto + manager (id+name), approvedBy (id+name), approvedAt, rejectionComment, receipts (List<ReceiptDto>), auditTrail (List<AuditLogDto>)
   - `ExpenseSummaryDto`: id, amount, currency, categoryName, merchantName, expenseDate, status, createdAt, receiptCount
   - `CategoryDto`: id, name, isActive
   - `AuditLogDto`: action, performedBy (name), comment, oldStatus, newStatus, createdAt
   - `ReceiptDto`: id, fileName, contentType, fileSize, createdAt

**Verification:**
- Create expense (DRAFT): `POST /api/v1/expenses` → 201
- Update draft: `PUT /api/v1/expenses/{id}` → 200
- Submit: `POST /api/v1/expenses/{id}/submit` → 200, status = SUBMITTED
- List: `GET /api/v1/expenses?status=DRAFT` → paginated list
- Get detail: `GET /api/v1/expenses/{id}` → includes audit trail
- Category CRUD works for admin
- Tenant isolation: User from Org A cannot see Org B expenses

---

#### M7: Approval Workflow Module

| Field | Value |
|-------|-------|
| **Module ID** | M7 |
| **Module Name** | Approval Workflow Module |
| **Description** | ApprovalController and ApprovalService for expense approval/rejection by managers. Includes pending approvals listing, single approve/reject, and bulk operations. Enforces state machine transitions for SUBMITTED→APPROVED and SUBMITTED→REJECTED. |
| **Stories** | S5.1 (Manager: View Pending), S5.2 (Manager: Approve), S5.3 (Manager: Reject), S5.4 (Bulk Approve/Reject) |
| **Dependencies** | M1, M2, M3 (entities + repositories). Shares AuditLogService with M6 — if M6 is not done yet, agent should create AuditLogService themselves (it is a simple utility). |
| **Inputs** | Expense entity, User entity, ExpenseAuditLog entity, ExpenseRepository, UserRepository, TenantContext, SecurityUtils, AuditLogService |
| **Outputs** | Approval endpoints (`/api/v1/approvals/*`, `/api/v1/expenses/{id}/approve`, `/api/v1/expenses/{id}/reject`) |
| **Estimated Complexity** | M |

**Files to Create:**
```
expense-tracker-api/src/main/java/com/expensetracker/service/ApprovalService.java
expense-tracker-api/src/main/java/com/expensetracker/controller/ApprovalController.java
expense-tracker-api/src/main/java/com/expensetracker/dto/request/ApprovalRequest.java
expense-tracker-api/src/main/java/com/expensetracker/dto/request/BulkApprovalRequest.java
expense-tracker-api/src/main/java/com/expensetracker/dto/response/BulkApprovalResultDto.java
```

**Detailed Instructions:**

1. **ApprovalService.java:**
   - `getPendingApprovals(UUID tenantId, UUID managerId, String role, UUID submitterIdFilter, UUID categoryIdFilter, Pageable pageable)`:
     - If role is ADMIN: return ALL SUBMITTED expenses in the tenant
     - If role is MANAGER: return SUBMITTED expenses where expense.managerId = managerId
     - Support optional filters (submitterId, categoryId)
     - Sort: oldest first (FIFO: `createdAt ASC`)

   - `approveExpense(UUID expenseId, String comment)`:
     - Find expense by id + tenantId (404)
     - Assert status is SUBMITTED (409 if not)
     - Assert current user is the assigned manager on the expense OR is ADMIN in the same tenant (403)
     - Set status = APPROVED, approvedById = currentUserId, approvedAt = now
     - Audit log: action=APPROVED, comment
     - Return updated ExpenseDto

   - `rejectExpense(UUID expenseId, String comment)`:
     - Same authorization checks as approve
     - Assert comment is non-blank (400 "Comment is required for rejection")
     - Set status = REJECTED, rejectionComment = comment
     - Audit log: action=REJECTED, comment
     - Return updated ExpenseDto

   - `bulkAction(BulkApprovalRequest req)`:
     - Validate: max 50 expense IDs, comment required for REJECT
     - For each expenseId:
       - Find expense by id + tenantId
       - Check authorization (manager or admin)
       - Check status is SUBMITTED
       - If valid: approve or reject
       - If invalid: add to skipped/errors list
     - Return BulkApprovalResultDto with processed/skipped/errors counts

2. **ApprovalController.java** — `@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")`:
   - `GET /api/v1/approvals/pending` → paginated pending list
   - `POST /api/v1/expenses/{id}/approve` → updated expense (200)
   - `POST /api/v1/expenses/{id}/reject` → updated expense (200)
   - `POST /api/v1/approvals/bulk` → BulkApprovalResultDto (200)

   Note: The approve/reject endpoints are on ExpenseController's path (`/expenses/{id}/approve`) but controlled by ApprovalController. Alternative: put them on ApprovalController with the `/expenses/{id}` path prefix. Either approach works — consistency with DESIGN.md is preferred. Per the API design, they are listed under "Expense Endpoints" so they can go on ExpenseController, but logically belong to ApprovalController. Choose one approach and be consistent.

   **Recommended:** Put approve/reject on ExpenseController (they act on a specific expense). Put pending list and bulk on ApprovalController.

3. **DTOs:**
   - `ApprovalRequest`: comment (String, optional for approve, required for reject)
   - `BulkApprovalRequest`: action (String: "APPROVE"|"REJECT"), expenseIds (List<UUID>, max 50), comment (String)
   - `BulkApprovalResultDto`: processed (int), skipped (int), results (List<BulkResult>)
     - `BulkResult`: expenseId, status ("SUCCESS"|"SKIPPED"), reason (nullable)

**Verification:**
- As Manager: `GET /api/v1/approvals/pending` → see pending expenses from direct reports only
- As Manager: `POST /api/v1/expenses/{id}/approve` → expense status changes to APPROVED
- As Manager: `POST /api/v1/expenses/{id}/reject` with comment → expense status changes to REJECTED
- As Admin: `GET /api/v1/approvals/pending` → see ALL pending expenses in org
- Bulk approve 3 expenses → 3 processed
- Attempt to approve expense from another manager's team → 403
- Attempt to approve already-approved expense → 409

---

#### M8: File Upload Module

| Field | Value |
|-------|-------|
| **Module ID** | M8 |
| **Module Name** | File Upload Module |
| **Description** | FileStorageService interface and LocalFileStorageService implementation for receipt file management. ReceiptController for upload, download, and delete operations. Content type validation, file size validation, and secure download with authorization checks. |
| **Stories** | S4.4 (Upload Receipt to Expense) |
| **Dependencies** | M1, M2, M3 (entities + repositories) |
| **Inputs** | Expense entity, ExpenseReceipt entity, ExpenseRepository, ExpenseReceiptRepository, TenantContext, SecurityUtils |
| **Outputs** | FileStorageService interface + implementation, receipt endpoints (`/api/v1/expenses/{id}/receipts/*`) |
| **Estimated Complexity** | M |

**Files to Create:**
```
expense-tracker-api/src/main/java/com/expensetracker/service/FileStorageService.java
expense-tracker-api/src/main/java/com/expensetracker/service/impl/LocalFileStorageService.java
expense-tracker-api/src/main/java/com/expensetracker/config/StorageConfig.java
expense-tracker-api/src/main/java/com/expensetracker/controller/ReceiptController.java
expense-tracker-api/src/main/java/com/expensetracker/exception/FileStorageException.java
```

**Detailed Instructions:**

1. **FileStorageService.java** (interface) — exactly as defined in DESIGN.md Section 8:
   ```java
   public interface FileStorageService {
       String store(UUID tenantId, UUID expenseId, MultipartFile file);
       Resource load(String storagePath);
       void delete(String storagePath);
       void deleteAllForExpense(UUID tenantId, UUID expenseId);
   }
   ```

2. **LocalFileStorageService.java** (`@Service` `@Profile("!s3")`):
   - Configurable base directory via `@Value("${uploads.base-dir:./uploads}")`
   - `store()`: Create directory `{baseDir}/{tenantId}/{expenseId}/`, generate UUID filename + original extension, write file, return relative path
   - `load()`: Resolve path against baseDir, check exists, return `FileSystemResource`
   - `delete()`: Resolve path, delete file
   - `deleteAllForExpense()`: Delete directory `{baseDir}/{tenantId}/{expenseId}/` recursively
   - **Path traversal prevention:** Normalize the resolved path and verify it starts with the base dir
   - Add `uploads.base-dir: ./uploads` to `application.yml`

3. **StorageConfig.java:**
   - `@Configuration` — create uploads base directory on startup if it does not exist

4. **ReceiptController.java:**
   - `POST /api/v1/expenses/{expenseId}/receipts` — multipart upload:
     - Validate auth, tenant isolation
     - Find expense by id + tenantId (404)
     - Assert current user is submitter (403)
     - Assert expense is DRAFT or REJECTED (409)
     - Validate content type: `image/jpeg`, `image/png`, `application/pdf` (400)
     - Validate file size <= 5MB (413)
     - Check receipt count < 3 (409)
     - Store file via FileStorageService
     - Create ExpenseReceipt record
     - Return 201 with ReceiptDto

   - `GET /api/v1/expenses/{expenseId}/receipts/{receiptId}` — download:
     - Validate auth, tenant isolation (through expense)
     - Assert access: submitter OR assigned manager OR admin (403)
     - Load file via FileStorageService
     - Stream with correct Content-Type and Content-Disposition headers

   - `DELETE /api/v1/expenses/{expenseId}/receipts/{receiptId}`:
     - Assert submitter (403)
     - Assert expense is DRAFT (409)
     - Delete file from filesystem
     - Delete ExpenseReceipt record
     - Return 204

**Verification:**
- Upload a JPEG to a DRAFT expense → 201, file appears in uploads directory
- Upload a 6MB file → 413
- Upload a .exe file → 400
- Upload a 4th receipt to an expense with 3 → 409
- Download receipt as submitter → file streams correctly
- Download receipt as a different user (not manager, not admin) → 403
- Delete receipt from SUBMITTED expense → 409

---

#### M9: Analytics Module

| Field | Value |
|-------|-------|
| **Module ID** | M9 |
| **Module Name** | Analytics Module |
| **Description** | AnalyticsController and AnalyticsService implementing aggregation queries for admin dashboard: spend by category, by month, by team, and summary stats. Also includes manager's team analytics. |
| **Stories** | S6.2 (Admin Dashboard: Spend Analytics API) |
| **Dependencies** | M1, M2, M3 (entities + repositories) |
| **Inputs** | ExpenseRepository (with analytics queries), TenantContext, SecurityUtils |
| **Outputs** | Analytics endpoints (`/api/v1/analytics/*`) |
| **Estimated Complexity** | M |

**Files to Create:**
```
expense-tracker-api/src/main/java/com/expensetracker/service/AnalyticsService.java
expense-tracker-api/src/main/java/com/expensetracker/controller/AnalyticsController.java
expense-tracker-api/src/main/java/com/expensetracker/dto/response/AnalyticsSummaryDto.java
expense-tracker-api/src/main/java/com/expensetracker/dto/response/CategorySpendDto.java
expense-tracker-api/src/main/java/com/expensetracker/dto/response/MonthlySpendDto.java
expense-tracker-api/src/main/java/com/expensetracker/dto/response/TeamSpendDto.java
```

**Detailed Instructions:**

1. **AnalyticsService.java:**
   - `getSummary(UUID tenantId, LocalDate from, LocalDate to)`:
     - Count expenses by status (SUBMITTED=pending, APPROVED, REJECTED) within date range and tenant
     - Sum approved amounts
     - Return AnalyticsSummaryDto

   - `getSpendByCategory(UUID tenantId, LocalDate from, LocalDate to)`:
     - Group APPROVED expenses by category within date range
     - Return List<CategorySpendDto>

   - `getSpendByMonth(UUID tenantId, int months)`:
     - Group APPROVED expenses by month for last N months
     - Return List<MonthlySpendDto>

   - `getSpendByTeam(UUID tenantId, LocalDate from, LocalDate to)`:
     - Group APPROVED expenses by manager_id, join with user name
     - Return List<TeamSpendDto>

   - `getMyTeamAnalytics(UUID tenantId, UUID managerId, LocalDate from, LocalDate to)`:
     - Same as getSpendByCategory but filtered to `expense.managerId = managerId`
     - Return List<CategorySpendDto>

   **Repository queries needed** (if not already in M3, add them to ExpenseRepository):

   ```java
   // Summary counts
   @Query("SELECT e.status, COUNT(e), COALESCE(SUM(e.amount), 0) FROM Expense e " +
          "WHERE e.tenantId = :tenantId AND e.expenseDate BETWEEN :from AND :to " +
          "GROUP BY e.status")
   List<Object[]> countAndSumByStatus(@Param("tenantId") UUID tenantId,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

   // By category
   @Query("SELECT c.id, c.name, COALESCE(SUM(e.amount), 0), COUNT(e) FROM Expense e " +
          "JOIN e.category c WHERE e.tenantId = :tenantId AND e.status = 'APPROVED' " +
          "AND e.expenseDate BETWEEN :from AND :to GROUP BY c.id, c.name ORDER BY SUM(e.amount) DESC")
   List<Object[]> sumByCategoryApproved(@Param("tenantId") UUID tenantId,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to);

   // By month (use native query for date_trunc)
   @Query(value = "SELECT TO_CHAR(e.expense_date, 'YYYY-MM') as month, " +
                  "COALESCE(SUM(e.amount), 0), COUNT(*) FROM expenses e " +
                  "WHERE e.tenant_id = :tenantId AND e.status = 'APPROVED' " +
                  "AND e.expense_date >= :since GROUP BY TO_CHAR(e.expense_date, 'YYYY-MM') " +
                  "ORDER BY month DESC",
          nativeQuery = true)
   List<Object[]> sumByMonthApproved(@Param("tenantId") UUID tenantId,
                                      @Param("since") LocalDate since);

   // By team (manager)
   @Query("SELECT u.id, CONCAT(u.firstName, ' ', u.lastName), COALESCE(SUM(e.amount), 0), COUNT(e) " +
          "FROM Expense e JOIN User u ON e.managerId = u.id " +
          "WHERE e.tenantId = :tenantId AND e.status = 'APPROVED' " +
          "AND e.expenseDate BETWEEN :from AND :to GROUP BY u.id, u.firstName, u.lastName")
   List<Object[]> sumByTeamApproved(@Param("tenantId") UUID tenantId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);
   ```

2. **AnalyticsController.java:**
   - `GET /api/v1/analytics/summary` → `@PreAuthorize("hasRole('ADMIN')")` → AnalyticsSummaryDto
   - `GET /api/v1/analytics/by-category` → `@PreAuthorize("hasRole('ADMIN')")` → List<CategorySpendDto>
   - `GET /api/v1/analytics/by-month` → `@PreAuthorize("hasRole('ADMIN')")` → List<MonthlySpendDto>
   - `GET /api/v1/analytics/by-team` → `@PreAuthorize("hasRole('ADMIN')")` → List<TeamSpendDto>
   - `GET /api/v1/analytics/my-team` → `@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")` → List<CategorySpendDto>
   - All accept `fromDate`, `toDate` query params (default: first/last day of current month). `by-month` accepts `months` (default 6, max 12).

3. **DTOs:**
   - `AnalyticsSummaryDto`: totalSubmitted (long), totalApproved (long), totalRejected (long), totalPending (long), totalApprovedAmount (BigDecimal), currency (String)
   - `CategorySpendDto`: categoryId (UUID), categoryName (String), totalAmount (BigDecimal), expenseCount (long)
   - `MonthlySpendDto`: month (String, "YYYY-MM"), totalAmount (BigDecimal), expenseCount (long)
   - `TeamSpendDto`: managerId (UUID), managerName (String), totalAmount (BigDecimal), expenseCount (long)

**Verification:**
- As Admin: `GET /api/v1/analytics/summary` → summary stats
- As Admin: `GET /api/v1/analytics/by-category?fromDate=2026-01-01&toDate=2026-03-31` → category breakdown
- As Employee: `GET /api/v1/analytics/summary` → 403
- As Manager: `GET /api/v1/analytics/my-team` → team-scoped category breakdown

---

#### M10: Rate Limiting Module

| Field | Value |
|-------|-------|
| **Module ID** | M10 |
| **Module Name** | Rate Limiting Module |
| **Description** | Implements API rate limiting using the token bucket algorithm. Per-IP limiting for auth endpoints (20/min), per-tenant limiting for all other endpoints (100/min). In-memory storage using ConcurrentHashMap. Adds rate limit response headers. |
| **Stories** | S7.1 (Rate Limiting Middleware) |
| **Dependencies** | M1 (project structure), M3 (TenantContext — for per-tenant limiting after auth), M4 (SecurityConfig — filters must be registered in the correct order) |
| **Inputs** | TenantContext (for tenant ID after auth), SecurityConfig (to register filters in filter chain) |
| **Outputs** | Rate limiting filters, token bucket implementation, rate limit headers on responses |
| **Estimated Complexity** | M |

**Files to Create:**
```
expense-tracker-api/src/main/java/com/expensetracker/ratelimit/TokenBucket.java
expense-tracker-api/src/main/java/com/expensetracker/ratelimit/InMemoryRateLimiter.java
expense-tracker-api/src/main/java/com/expensetracker/ratelimit/RateLimitResult.java
expense-tracker-api/src/main/java/com/expensetracker/filter/AuthRateLimitFilter.java
expense-tracker-api/src/main/java/com/expensetracker/filter/TenantRateLimitFilter.java
expense-tracker-api/src/main/java/com/expensetracker/exception/RateLimitExceededException.java
```

**Detailed Instructions:**

1. **TokenBucket.java:**
   - Fields: `capacity` (int), `tokens` (double), `refillRatePerSecond` (double), `lastRefillTimestamp` (long, nanos)
   - `synchronized boolean tryConsume()`:
     - Calculate elapsed time since last refill
     - Add tokens: `tokensToAdd = elapsed * refillRatePerSecond`
     - Cap at capacity: `tokens = Math.min(capacity, tokens + tokensToAdd)`
     - If tokens >= 1: decrement and return true, else return false
   - `synchronized RateLimitResult check()`: Returns remaining tokens, limit, reset time

2. **RateLimitResult.java:**
   - Fields: `allowed` (boolean), `limit` (int), `remaining` (int), `retryAfterSeconds` (long), `resetTimestamp` (long)

3. **InMemoryRateLimiter.java:**
   - `ConcurrentHashMap<String, TokenBucket> buckets`
   - `RateLimitResult tryConsume(String key, int capacity, double refillPerSecond)`:
     - `computeIfAbsent(key, k -> new TokenBucket(capacity, refillPerSecond))`
     - Call `bucket.tryConsume()` and return result

4. **AuthRateLimitFilter.java** (extends `OncePerRequestFilter`):
   - Only applies to `/api/v1/auth/**` paths
   - Extract client IP (`X-Forwarded-For` header or `request.getRemoteAddr()`)
   - Check per-IP bucket: 20 requests/min
   - On limit exceeded: write 429 JSON response with `Retry-After` header
   - Add rate limit headers to every response: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`

5. **TenantRateLimitFilter.java** (extends `OncePerRequestFilter`):
   - Only applies to non-auth `/api/v1/**` paths
   - Runs AFTER TenantContextFilter (so tenant ID is available)
   - Get tenant ID from `TenantContext.getCurrentTenant()`
   - Check per-tenant bucket: 100 requests/min
   - Same 429 response and headers as AuthRateLimitFilter

6. **Integration with SecurityConfig:** The filters must be registered in the correct order. If M4 is implementing SecurityConfig simultaneously, provide a note in SecurityConfig about where these filters go:
   - AuthRateLimitFilter: BEFORE JwtAuthenticationFilter
   - TenantRateLimitFilter: AFTER TenantContextFilter

   The M10 agent should either:
   - (a) Modify SecurityConfig to register the filters (if SecurityConfig already exists from M4), or
   - (b) Create the filter beans and document in a comment that SecurityConfig must register them

**Verification:**
- Send 20 requests to `/api/v1/auth/login` rapidly → all succeed
- Send 21st request → 429 with Retry-After header
- Send 100 authenticated requests → all succeed
- Send 101st → 429
- Check response headers include `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`
- Different tenants have independent limits

---

#### M11: Global Error Handling & DTOs

| Field | Value |
|-------|-------|
| **Module ID** | M11 |
| **Module Name** | Global Error Handling & DTOs |
| **Description** | Global exception handler (@ControllerAdvice), all custom exception classes, ErrorResponse DTO, and Jackson configuration. Ensures all API errors return consistent JSON format. |
| **Stories** | Cross-cutting concern (Error Handling) from TECHNICAL_STORIES |
| **Dependencies** | M1 (project structure) |
| **Inputs** | Error response format from DESIGN.md Section 4 |
| **Outputs** | GlobalExceptionHandler, all custom exceptions, ErrorResponse DTO, JacksonConfig |
| **Estimated Complexity** | S |

**Files to Create:**
```
expense-tracker-api/src/main/java/com/expensetracker/exception/GlobalExceptionHandler.java
expense-tracker-api/src/main/java/com/expensetracker/exception/ResourceNotFoundException.java
expense-tracker-api/src/main/java/com/expensetracker/exception/ForbiddenException.java
expense-tracker-api/src/main/java/com/expensetracker/exception/ConflictException.java
expense-tracker-api/src/main/java/com/expensetracker/exception/InvalidStateTransitionException.java
expense-tracker-api/src/main/java/com/expensetracker/exception/BusinessRuleException.java
expense-tracker-api/src/main/java/com/expensetracker/exception/RateLimitExceededException.java
expense-tracker-api/src/main/java/com/expensetracker/exception/FileStorageException.java
expense-tracker-api/src/main/java/com/expensetracker/dto/response/ErrorResponse.java
expense-tracker-api/src/main/java/com/expensetracker/config/JacksonConfig.java
```

**Detailed Instructions:**

1. **Custom Exceptions:**
   - `ResourceNotFoundException` extends `RuntimeException` — carries resource type and identifier
   - `ForbiddenException` extends `RuntimeException` — carries message
   - `ConflictException` extends `RuntimeException` — carries message and optional code
   - `InvalidStateTransitionException` extends `RuntimeException` — carries `fromStatus`, `toStatus`, message
   - `BusinessRuleException` extends `RuntimeException` — carries message and code
   - `RateLimitExceededException` extends `RuntimeException` — carries `retryAfterSeconds`
   - `FileStorageException` extends `RuntimeException` — wraps IO exceptions

2. **ErrorResponse DTO:**
   ```java
   public record ErrorResponse(
       String error,
       String code,
       Object details,
       List<FieldError> fieldErrors,
       String timestamp,
       String path
   ) {
       public record FieldError(String field, String message) {}
   }
   ```

3. **GlobalExceptionHandler.java** (`@RestControllerAdvice`):
   - Handle `ResourceNotFoundException` → 404
   - Handle `ForbiddenException` → 403
   - Handle `ConflictException` → 409
   - Handle `InvalidStateTransitionException` → 409
   - Handle `BusinessRuleException` → 400
   - Handle `RateLimitExceededException` → 429 with Retry-After header
   - Handle `FileStorageException` → 500
   - Handle `MethodArgumentNotValidException` (Spring validation) → 400 with fieldErrors array
   - Handle `MaxUploadSizeExceededException` → 413
   - Handle `AccessDeniedException` (Spring Security) → 403
   - Handle `Exception` (catch-all) → 500 with generic message
   - All responses use ErrorResponse format with timestamp and request path

4. **JacksonConfig.java** (`@Configuration`):
   - Configure `ObjectMapper` to:
     - Serialize dates as ISO 8601 strings
     - Not fail on unknown properties
     - Use snake_case or camelCase (match DESIGN.md convention — DESIGN.md uses camelCase in JSON)
     - Register `JavaTimeModule` for Java 8 date/time types

**Verification:**
- Trigger a 404 by requesting non-existent resource → consistent JSON error format
- Trigger a 400 by sending invalid request body → fieldErrors array
- Trigger a 409 by attempting invalid state transition → consistent error format
- All error responses include `error`, `code`, `timestamp`, `path` fields

---

### Phase 3 — Frontend (can start in parallel with Phase 2 backend)

Frontend modules can start as soon as M1 completes (for project scaffolding). They use mocked API responses or the real backend (whichever is available). Frontend modules can also run in parallel with each other.

---

#### M12: Frontend Scaffolding & Auth

| Field | Value |
|-------|-------|
| **Module ID** | M12 |
| **Module Name** | Frontend Scaffolding & Auth |
| **Description** | React app foundation: routing, auth context, login/register pages, Axios interceptor with automatic token refresh, protected route wrapper, role guard component, app layout with sidebar. |
| **Stories** | S8.1 (App Shell, Routing & Auth State) |
| **Dependencies** | M1 (frontend project structure) |
| **Inputs** | Auth API contract from DESIGN.md Section 4, Component hierarchy from DESIGN.md Section 10 |
| **Outputs** | Working auth flow (login, register, auto-refresh), app shell with role-based navigation, protected routes |
| **Estimated Complexity** | L |

**Files to Create:**
```
expense-tracker-ui/src/types/auth.ts
expense-tracker-ui/src/types/common.ts
expense-tracker-ui/src/api/axiosInstance.ts
expense-tracker-ui/src/api/authApi.ts
expense-tracker-ui/src/context/AuthContext.tsx
expense-tracker-ui/src/hooks/useAuth.ts
expense-tracker-ui/src/components/auth/ProtectedRoute.tsx
expense-tracker-ui/src/components/auth/RoleGuard.tsx
expense-tracker-ui/src/components/layout/AppLayout.tsx
expense-tracker-ui/src/components/layout/Sidebar.tsx
expense-tracker-ui/src/components/layout/Header.tsx
expense-tracker-ui/src/components/common/LoadingSpinner.tsx
expense-tracker-ui/src/components/common/Toast.tsx
expense-tracker-ui/src/pages/LoginPage.tsx
expense-tracker-ui/src/pages/RegisterPage.tsx
expense-tracker-ui/src/pages/NotFoundPage.tsx
expense-tracker-ui/src/App.tsx (modify)
```

**Detailed Instructions:**

1. **Types:**
   ```typescript
   // types/auth.ts
   export type Role = 'EMPLOYEE' | 'MANAGER' | 'ADMIN';

   export interface User {
     id: string;
     email: string;
     firstName: string;
     lastName: string;
     role: Role;
     organizationId: string;
     organizationName: string;
   }

   export interface AuthResponse {
     accessToken: string;
     refreshToken: string;
     user: User;
   }

   export interface LoginRequest {
     email: string;
     password: string;
   }

   export interface RegisterRequest {
     email: string;
     password: string;
     firstName: string;
     lastName: string;
     organizationId: string;
   }

   // types/common.ts
   export interface PaginatedResponse<T> {
     content: T[];
     page: number;
     size: number;
     totalElements: number;
     totalPages: number;
   }

   export interface ErrorResponse {
     error: string;
     code: string;
     details?: Record<string, unknown>;
     fieldErrors?: { field: string; message: string }[];
     timestamp: string;
     path: string;
   }
   ```

2. **Axios Instance** (`api/axiosInstance.ts`):
   - Base URL: `/api/v1` (Vite proxy handles forwarding to backend)
   - Request interceptor: attach `Authorization: Bearer <accessToken>` from auth context
   - Response interceptor: on 401 (and not an auth endpoint and not a retry), attempt token refresh:
     - Use shared `refreshPromise` to deduplicate concurrent refresh attempts (see DESIGN.md Section 10)
     - On successful refresh: update tokens in auth context, retry original request
     - On failed refresh: logout user, redirect to login
   - Export configured Axios instance

3. **AuthContext** (`context/AuthContext.tsx`):
   - State: `user`, `accessToken`, `isAuthenticated`, `isLoading`
   - Store `refreshToken` in `localStorage`
   - Store `accessToken` in memory (React state)
   - On mount: check localStorage for refreshToken, attempt auto-refresh to restore session
   - Provide: `login(email, password)`, `register(data)`, `logout()`, `setTokens(access, refresh)`
   - `isLoading`: true during initial auth check (prevents flash of login page)

4. **ProtectedRoute** (`components/auth/ProtectedRoute.tsx`):
   - If `isLoading`: show LoadingSpinner
   - If not authenticated: `<Navigate to="/login" />`
   - If authenticated: render `<Outlet />`

5. **RoleGuard** (`components/auth/RoleGuard.tsx`):
   - Props: `allowedRoles: Role[]`
   - If user's role not in allowedRoles: `<Navigate to="/" />`
   - Else: render children

6. **AppLayout** (`components/layout/AppLayout.tsx`):
   - Sidebar (fixed left, role-aware navigation) + Header (top, user info + logout) + `<Outlet>` for content
   - Responsive: sidebar collapses on mobile (hamburger menu or hidden)

7. **Sidebar** (`components/layout/Sidebar.tsx`):
   - Navigation links based on role:
     - EMPLOYEE: My Expenses, New Expense
     - MANAGER: My Expenses, New Expense, Pending Approvals, Team Stats
     - ADMIN: Dashboard, Users, Categories
     - ALL: Profile
   - Active link highlighting using React Router's `NavLink`

8. **LoginPage** (`pages/LoginPage.tsx`):
   - Form: email, password fields
   - Inline validation (email format, password not empty)
   - Call `login()` from auth context
   - Show error toast on failure
   - Redirect to home on success (based on role: EMPLOYEE→/expenses, MANAGER→/approvals, ADMIN→/dashboard)
   - Link to register page

9. **RegisterPage** (`pages/RegisterPage.tsx`):
   - Form: firstName, lastName, email, password, organizationId (text input or dropdown if we can fetch orgs)
   - Password validation: min 8 chars, 1 uppercase, 1 digit (show inline requirements)
   - Call `register()` from auth context
   - Redirect to home on success

10. **App.tsx** — set up routing:
    ```tsx
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route element={<ProtectedRoute />}>
            <Route element={<AppLayout />}>
              {/* Employee + Manager routes */}
              <Route path="/expenses" element={<MyExpensesPage />} />
              <Route path="/expenses/new" element={<ExpenseFormPage />} />
              <Route path="/expenses/:id" element={<ExpenseDetailPage />} />
              <Route path="/expenses/:id/edit" element={<ExpenseFormPage />} />
              {/* Manager routes */}
              <Route path="/approvals" element={<RoleGuard allowedRoles={['MANAGER','ADMIN']}><PendingApprovalsPage /></RoleGuard>} />
              <Route path="/team-stats" element={<RoleGuard allowedRoles={['MANAGER','ADMIN']}><TeamStatsPage /></RoleGuard>} />
              {/* Admin routes */}
              <Route path="/dashboard" element={<RoleGuard allowedRoles={['ADMIN']}><AdminDashboardPage /></RoleGuard>} />
              <Route path="/users" element={<RoleGuard allowedRoles={['ADMIN']}><UserManagementPage /></RoleGuard>} />
              <Route path="/categories" element={<RoleGuard allowedRoles={['ADMIN']}><CategoryManagementPage /></RoleGuard>} />
              {/* Common */}
              <Route path="/profile" element={<ProfilePage />} />
              <Route path="/" element={<HomeRedirect />} />
            </Route>
          </Route>
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </BrowserRouter>
      <Toaster />
    </AuthProvider>
    ```
    Note: For pages not yet implemented (MyExpensesPage, etc.), create placeholder components that render the page name. These will be filled in by M13, M14, M15.

**Verification:**
- Navigate to `/login` → login form renders
- Login with valid credentials → redirects to role-appropriate home page, sidebar shows correct links
- Navigate to `/expenses` without login → redirects to `/login`
- As EMPLOYEE, navigate to `/dashboard` → redirects to `/`
- Token auto-refresh works: wait 15 min (or shorten access token for testing), next request auto-refreshes

---

#### M13: Employee Expense UI

| Field | Value |
|-------|-------|
| **Module ID** | M13 |
| **Module Name** | Employee Expense UI |
| **Description** | My Expenses page (table with filters, pagination, status badges), New/Edit Expense form (with receipt upload, drag-and-drop), Expense Detail page (with receipt gallery and audit timeline). |
| **Stories** | S8.2 (Employee: Expense Management UI) |
| **Dependencies** | M12 (auth context, layout, routing, axios instance) |
| **Inputs** | Expense API contract from DESIGN.md Section 4, Receipt API contract, Category API for dropdowns |
| **Outputs** | MyExpensesPage, ExpenseFormPage, ExpenseDetailPage, and all associated components |
| **Estimated Complexity** | L |

**Files to Create:**
```
expense-tracker-ui/src/types/expense.ts
expense-tracker-ui/src/types/category.ts
expense-tracker-ui/src/api/expenseApi.ts
expense-tracker-ui/src/api/receiptApi.ts
expense-tracker-ui/src/api/categoryApi.ts
expense-tracker-ui/src/hooks/useExpenses.ts
expense-tracker-ui/src/hooks/useExpense.ts
expense-tracker-ui/src/hooks/useCategories.ts
expense-tracker-ui/src/pages/MyExpensesPage.tsx
expense-tracker-ui/src/pages/ExpenseFormPage.tsx
expense-tracker-ui/src/pages/ExpenseDetailPage.tsx
expense-tracker-ui/src/components/expenses/ExpenseTable.tsx
expense-tracker-ui/src/components/expenses/ExpenseForm.tsx
expense-tracker-ui/src/components/expenses/ExpenseFilterBar.tsx
expense-tracker-ui/src/components/expenses/ExpenseStatusBadge.tsx
expense-tracker-ui/src/components/expenses/ReceiptUpload.tsx
expense-tracker-ui/src/components/expenses/ReceiptGallery.tsx
expense-tracker-ui/src/components/expenses/AuditTimeline.tsx
expense-tracker-ui/src/components/common/Pagination.tsx
expense-tracker-ui/src/components/common/EmptyState.tsx
expense-tracker-ui/src/components/common/SkeletonLoader.tsx
expense-tracker-ui/src/components/common/ConfirmModal.tsx
expense-tracker-ui/src/utils/formatCurrency.ts
expense-tracker-ui/src/utils/formatDate.ts
expense-tracker-ui/src/utils/validators.ts
```

**Detailed Instructions:**

1. **Types:**
   ```typescript
   // types/expense.ts
   export interface Expense {
     id: string;
     amount: number | null;
     currency: string;
     category: { id: string; name: string } | null;
     merchantName: string | null;
     expenseDate: string | null;
     notes: string | null;
     status: 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
     submitter: { id: string; name: string };
     receiptCount: number;
     createdAt: string;
     updatedAt: string;
   }

   export interface ExpenseDetail extends Expense {
     manager: { id: string; name: string } | null;
     approvedBy: { id: string; name: string } | null;
     approvedAt: string | null;
     rejectionComment: string | null;
     receipts: Receipt[];
     auditTrail: AuditEntry[];
   }

   export interface Receipt {
     id: string;
     fileName: string;
     contentType: string;
     fileSize: number;
     createdAt: string;
   }

   export interface AuditEntry {
     action: string;
     performedBy: string;
     comment: string | null;
     oldStatus: string | null;
     newStatus: string;
     createdAt: string;
   }

   export interface CreateExpenseRequest {
     amount?: number;
     categoryId?: string;
     merchantName?: string;
     expenseDate?: string;
     notes?: string;
   }
   ```

2. **API calls** (`api/expenseApi.ts`):
   - `getExpenses(params)` → `GET /expenses`
   - `getExpense(id)` → `GET /expenses/{id}`
   - `createExpense(data)` → `POST /expenses`
   - `updateExpense(id, data)` → `PUT /expenses/{id}`
   - `submitExpense(id)` → `POST /expenses/{id}/submit`
   - `deleteExpense(id)` → `DELETE /expenses/{id}`

3. **MyExpensesPage:**
   - Filter bar: status dropdown (ALL, DRAFT, SUBMITTED, APPROVED, REJECTED), category dropdown (from API), date range picker
   - Paginated table with columns: Date, Category, Merchant, Amount, Status, Actions
   - Status shown as colored badge: DRAFT=gray, SUBMITTED=blue, APPROVED=green, REJECTED=red, CANCELLED=dark gray
   - "New Expense" button → `/expenses/new`
   - Click row → `/expenses/{id}`
   - Loading state: skeleton loader rows
   - Empty state: "No expenses found" illustration

4. **ExpenseFormPage:**
   - Shared for create and edit (check URL: `/expenses/new` vs `/expenses/:id/edit`)
   - If editing: load existing expense data, reject if status is not DRAFT/REJECTED
   - Fields: amount (number input), category (select dropdown from API), merchant name (text), expense date (date input), notes (textarea)
   - Receipt upload section: drag-and-drop zone or file picker, show thumbnails for images / filename for PDFs, max 3 files, max 5MB each
   - Two action buttons: "Save Draft" (POST/PUT without submit) and "Submit for Approval" (POST/PUT + submit)
   - Inline validation matching server rules
   - On REJECTED expense: show rejection comment prominently at top in red banner

5. **ExpenseDetailPage:**
   - Display all expense fields in a clean layout
   - Receipt gallery: thumbnail previews for images, download link for PDFs, click to view full-size
   - Audit timeline: vertical timeline showing status changes with timestamps and actors
   - Action buttons based on status:
     - DRAFT: Edit, Delete, Submit
     - REJECTED: Edit & Resubmit (show rejection comment prominently)
     - SUBMITTED/APPROVED: view only

6. **Utility functions:**
   - `formatCurrency(amount, currency)`: Format number as currency string ($1,234.56)
   - `formatDate(isoString)`: Format ISO date as readable date (Mar 15, 2026)
   - `validators.ts`: Password validation, expense field validation

**Verification:**
- My Expenses page loads with demo data
- Filter by status → table updates
- Create new expense → form renders, save draft works
- Edit draft expense → loads existing data
- Submit expense → status changes to SUBMITTED
- View expense detail → receipts and audit trail visible
- Upload receipt → preview shown
- All pages handle loading and empty states

---

#### M14: Manager Approval UI

| Field | Value |
|-------|-------|
| **Module ID** | M14 |
| **Module Name** | Manager Approval UI |
| **Description** | Pending Approvals page with approve/reject actions, bulk operations, rejection comment modal, and team stats mini-dashboard. |
| **Stories** | S8.3 (Manager: Approval Queue UI) |
| **Dependencies** | M12 (auth context, layout, routing) |
| **Inputs** | Approval API contract from DESIGN.md Section 4, Expense types from M13 (if available, otherwise define locally) |
| **Outputs** | PendingApprovalsPage, TeamStatsPage, and all associated components |
| **Estimated Complexity** | M |

**Files to Create:**
```
expense-tracker-ui/src/types/approval.ts
expense-tracker-ui/src/api/approvalApi.ts
expense-tracker-ui/src/hooks/usePendingApprovals.ts
expense-tracker-ui/src/pages/PendingApprovalsPage.tsx
expense-tracker-ui/src/pages/TeamStatsPage.tsx
expense-tracker-ui/src/components/approvals/ApprovalTable.tsx
expense-tracker-ui/src/components/approvals/BulkActions.tsx
expense-tracker-ui/src/components/approvals/RejectModal.tsx
```

**Detailed Instructions:**

1. **Types:**
   ```typescript
   // types/approval.ts
   export interface BulkApprovalRequest {
     action: 'APPROVE' | 'REJECT';
     expenseIds: string[];
     comment?: string;
   }

   export interface BulkApprovalResult {
     processed: number;
     skipped: number;
     results: { expenseId: string; status: 'SUCCESS' | 'SKIPPED'; reason?: string }[];
   }
   ```

2. **API calls** (`api/approvalApi.ts`):
   - `getPendingApprovals(params)` → `GET /approvals/pending`
   - `approveExpense(id, comment?)` → `POST /expenses/{id}/approve`
   - `rejectExpense(id, comment)` → `POST /expenses/{id}/reject`
   - `bulkAction(data)` → `POST /approvals/bulk`

3. **PendingApprovalsPage:**
   - Table with columns: Submitter, Date, Category, Amount, Actions (Approve/Reject buttons)
   - Click row → navigate to `/expenses/{id}` for detail view
   - Reject button → opens RejectModal requiring a comment
   - Approve button → optional comment, confirm immediately (or quick-confirm dialog)
   - Checkbox column for bulk selection
   - Bulk action bar: "Approve Selected" / "Reject Selected" buttons, appear when items selected
   - After action: item removed from list with success toast
   - Badge in sidebar showing count of pending approvals

4. **RejectModal:**
   - Modal dialog with textarea for rejection reason (required, min 1 char)
   - Cancel and Confirm buttons
   - Shows expense summary (submitter, amount) for context

5. **TeamStatsPage:**
   - Mini-dashboard for manager's team
   - Fetch from `/api/v1/analytics/my-team`
   - Simple bar chart (Recharts) showing team spend by category
   - Summary stats (total pending, total approved this month)

**Verification:**
- As Manager: navigate to `/approvals` → see pending expenses from direct reports
- Approve an expense → it disappears from list, success toast
- Reject an expense → modal appears, enter comment, confirm → expense rejected
- Select multiple → bulk approve → all processed
- As Employee: navigate to `/approvals` → redirected (RoleGuard)

---

#### M15: Admin Dashboard UI

| Field | Value |
|-------|-------|
| **Module ID** | M15 |
| **Module Name** | Admin Dashboard UI |
| **Description** | Admin dashboard with summary cards, charts (category bar chart, monthly trend line chart, team spend table), date range picker. Also includes User Management page and Category Management page. |
| **Stories** | S6.3 (Admin Dashboard: Frontend) |
| **Dependencies** | M12 (auth context, layout, routing) |
| **Inputs** | Analytics API contract from DESIGN.md Section 4, User API contract, Category API contract |
| **Outputs** | AdminDashboardPage, UserManagementPage, CategoryManagementPage, and all associated components |
| **Estimated Complexity** | L |

**Files to Create:**
```
expense-tracker-ui/src/types/analytics.ts
expense-tracker-ui/src/types/user.ts
expense-tracker-ui/src/api/analyticsApi.ts
expense-tracker-ui/src/api/userApi.ts
expense-tracker-ui/src/hooks/useAnalytics.ts
expense-tracker-ui/src/hooks/useUsers.ts
expense-tracker-ui/src/pages/AdminDashboardPage.tsx
expense-tracker-ui/src/pages/UserManagementPage.tsx
expense-tracker-ui/src/pages/CategoryManagementPage.tsx
expense-tracker-ui/src/components/dashboard/SummaryCards.tsx
expense-tracker-ui/src/components/dashboard/CategoryBarChart.tsx
expense-tracker-ui/src/components/dashboard/MonthlyTrendLineChart.tsx
expense-tracker-ui/src/components/dashboard/TeamSpendTable.tsx
expense-tracker-ui/src/components/dashboard/DateRangePicker.tsx
expense-tracker-ui/src/components/users/UserTable.tsx
expense-tracker-ui/src/components/users/RoleChangeModal.tsx
expense-tracker-ui/src/components/users/ManagerAssignModal.tsx
```

**Detailed Instructions:**

1. **Types:**
   ```typescript
   // types/analytics.ts
   export interface AnalyticsSummary {
     totalSubmitted: number;
     totalApproved: number;
     totalRejected: number;
     totalPending: number;
     totalApprovedAmount: number;
     currency: string;
   }

   export interface CategorySpend {
     categoryId: string;
     categoryName: string;
     totalAmount: number;
     expenseCount: number;
   }

   export interface MonthlySpend {
     month: string;
     totalAmount: number;
     expenseCount: number;
   }

   export interface TeamSpend {
     managerId: string;
     managerName: string;
     totalAmount: number;
     expenseCount: number;
   }

   // types/user.ts
   export interface UserProfile {
     id: string;
     email: string;
     firstName: string;
     lastName: string;
     role: 'EMPLOYEE' | 'MANAGER' | 'ADMIN';
     managerId: string | null;
     managerName: string | null;
     isActive: boolean;
     createdAt: string;
   }
   ```

2. **AdminDashboardPage:**
   - Summary cards at top: Total Pending (count, blue), Total Approved (amount, green), Total Rejected (count, red), Total This Month (amount, purple)
   - Bar chart: spend by category (horizontal bars, Recharts `<BarChart>`)
   - Line chart: monthly trend (last 6 months, Recharts `<LineChart>` with `<Area>`)
   - Table: spend by team/manager (sortable)
   - Date range picker to filter all widgets (default: current month)
   - Each widget has its own loading skeleton
   - Responsive grid layout: 2 columns on desktop, 1 on mobile

3. **UserManagementPage:**
   - Paginated table: Name, Email, Role (badge), Manager, Status (active/inactive), Actions
   - Search input (debounced, searches name/email)
   - Role filter dropdown
   - Actions per row: Change Role, Assign Manager, Deactivate
   - Change Role → RoleChangeModal: dropdown with EMPLOYEE, MANAGER, ADMIN
   - Assign Manager → ManagerAssignModal: dropdown of MANAGER/ADMIN users in the org
   - Deactivate → Confirmation dialog with warning about consequences

4. **CategoryManagementPage:**
   - List of categories with active/inactive status
   - Add new category form (inline or modal)
   - Rename category (inline edit or modal)
   - Deactivate category (with confirmation)

5. **Charts (Recharts):**
   - `CategoryBarChart`: `<ResponsiveContainer>` → `<BarChart>` with `<Bar>` for totalAmount, `<XAxis>` for category names, `<YAxis>` for amount, `<Tooltip>` with currency formatting
   - `MonthlyTrendLineChart`: `<ResponsiveContainer>` → `<LineChart>` with `<Line>` and `<Area>` fill, `<XAxis>` for month labels, `<YAxis>` for amount, `<Tooltip>`
   - `TeamSpendTable`: HTML `<table>` with Tailwind styling, sortable columns

6. **DateRangePicker:**
   - Two date inputs (from, to) with preset buttons: "This Month", "Last Month", "Last 3 Months", "This Year"
   - On change: re-fetch all analytics data

**Verification:**
- As Admin: navigate to `/dashboard` → summary cards, charts, and table render
- Change date range → data updates
- Navigate to `/users` → user list loads with correct roles and managers
- Change user role → role updates
- Assign manager → manager updates
- Navigate to `/categories` → category list loads
- Add category → appears in list
- As Employee: navigate to `/dashboard` → redirected

---

### Phase 4 — Integration & Hardening

---

#### M16: Integration Testing

| Field | Value |
|-------|-------|
| **Module ID** | M16 |
| **Module Name** | Integration Testing |
| **Description** | End-to-end integration tests covering tenant isolation, approval workflow, auth flow, and rate limiting. Uses Spring Boot test with Testcontainers for PostgreSQL. |
| **Stories** | S1.3 (Tenant Isolation Tests), S7.2 (Rate Limiting Tests) |
| **Dependencies** | M4, M5, M6, M7, M8, M9, M10 (all backend modules should be complete) |
| **Inputs** | All backend endpoints, test database via Testcontainers |
| **Outputs** | Comprehensive integration test suite |
| **Estimated Complexity** | L |

**Files to Create:**
```
expense-tracker-api/src/test/java/com/expensetracker/integration/AuthIntegrationTest.java
expense-tracker-api/src/test/java/com/expensetracker/integration/ExpenseIntegrationTest.java
expense-tracker-api/src/test/java/com/expensetracker/integration/ApprovalIntegrationTest.java
expense-tracker-api/src/test/java/com/expensetracker/integration/TenantIsolationTest.java
expense-tracker-api/src/test/java/com/expensetracker/integration/RateLimitIntegrationTest.java
expense-tracker-api/src/test/java/com/expensetracker/integration/BaseIntegrationTest.java
expense-tracker-api/src/test/resources/application-test.yml
```

**Detailed Instructions:**

1. **BaseIntegrationTest.java:**
   - `@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)`
   - `@Testcontainers` with PostgreSQL container
   - `@DynamicPropertySource` to set datasource URL from container
   - Helper methods: `registerUser()`, `loginUser()`, `getAuthHeader()`, `createExpenseAsUser()`
   - Setup: Use RestTemplate or MockMvc for HTTP calls

2. **AuthIntegrationTest.java:**
   - Test: Register with valid data → 201, tokens returned
   - Test: Register with existing email → 409
   - Test: Register with invalid password → 400
   - Test: Login with valid credentials → 200, tokens returned
   - Test: Login with invalid password → 401
   - Test: Account lockout after 5 failures → 429
   - Test: Refresh token rotation → new tokens issued, old revoked
   - Test: Refresh token reuse detection → all tokens revoked

3. **TenantIsolationTest.java:**
   - Setup: Create users in both Org A and Org B
   - Test: Org A user lists expenses → only Org A expenses
   - Test: Org A user gets Org B expense by ID → 404
   - Test: Org A manager tries to approve Org B expense → 404
   - Test: Org A admin views analytics → only Org A data
   - Test: Cover expenses, users, categories

4. **ExpenseIntegrationTest.java:**
   - Test: Create draft → 201
   - Test: Update draft → 200
   - Test: Submit with all fields → 200, status SUBMITTED
   - Test: Submit without manager → 400
   - Test: Edit submitted expense → 409
   - Test: Delete draft → 204
   - Test: Delete submitted → 409
   - Test: List with filters → correct results

5. **ApprovalIntegrationTest.java:**
   - Test: Full workflow: create → submit → approve → verify status
   - Test: Full workflow: create → submit → reject → edit → resubmit → approve
   - Test: Manager approves own team's expense → success
   - Test: Manager approves other team's expense → 403
   - Test: Bulk approve → success
   - Test: Audit trail records all transitions

6. **RateLimitIntegrationTest.java:**
   - Test: Send requests up to limit → all succeed
   - Test: Exceed limit → 429 with correct headers
   - Test: Different tenants have independent limits
   - Test: Auth endpoints have stricter limits

**Verification:** `mvn test` → all integration tests pass (green). Tests run against real PostgreSQL via Testcontainers.

---

#### M17: Documentation & Polish

| Field | Value |
|-------|-------|
| **Module ID** | M17 |
| **Module Name** | Documentation & Polish |
| **Description** | README with setup instructions, Docker Compose for full-stack (backend + frontend + PostgreSQL), AI_USAGE.md template, final cleanup and polish. |
| **Stories** | Cross-cutting |
| **Dependencies** | All other modules |
| **Inputs** | Complete application |
| **Outputs** | README.md, updated docker-compose.yml, AI_USAGE.md, Dockerfiles |
| **Estimated Complexity** | S |

**Files to Create/Modify:**
```
README.md
AI_USAGE.md
docker-compose.yml (modify — add backend + frontend services)
expense-tracker-api/Dockerfile
expense-tracker-ui/Dockerfile
expense-tracker-ui/nginx.conf
```

**Detailed Instructions:**

1. **README.md:**
   - Project title and description
   - Architecture overview (link to DESIGN.md)
   - Prerequisites: Docker, Docker Compose (or Java 17, Node 18, PostgreSQL 15)
   - Quick start: `docker-compose up` → app available at `http://localhost:3000`
   - Manual setup instructions for development (backend + frontend separately)
   - Demo credentials (from seed data)
   - API documentation overview
   - Running tests: `mvn test`
   - Project structure summary

2. **Docker Compose (full stack):**
   ```yaml
   services:
     postgres:
       image: postgres:15-alpine
       environment:
         POSTGRES_DB: expense_tracker
         POSTGRES_USER: expense_user
         POSTGRES_PASSWORD: expense_pass
       ports:
         - "5432:5432"
       volumes:
         - postgres_data:/var/lib/postgresql/data
       healthcheck:
         test: ["CMD-SHELL", "pg_isready -U expense_user -d expense_tracker"]
         interval: 5s
         timeout: 5s
         retries: 5

     backend:
       build: ./expense-tracker-api
       ports:
         - "8080:8080"
       environment:
         SPRING_PROFILES_ACTIVE: docker
         SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/expense_tracker
         SPRING_DATASOURCE_USERNAME: expense_user
         SPRING_DATASOURCE_PASSWORD: expense_pass
       depends_on:
         postgres:
           condition: service_healthy
       volumes:
         - uploads:/app/uploads

     frontend:
       build: ./expense-tracker-ui
       ports:
         - "3000:80"
       depends_on:
         - backend

   volumes:
     postgres_data:
     uploads:
   ```

3. **Backend Dockerfile:**
   - Multi-stage: build with Maven, run with JRE
   - `FROM maven:3.9-eclipse-temurin-17 AS build` → `FROM eclipse-temurin:17-jre-alpine`

4. **Frontend Dockerfile:**
   - Multi-stage: build with Node, serve with nginx
   - `FROM node:18-alpine AS build` → `FROM nginx:alpine`
   - nginx.conf: serve static files, proxy `/api` to backend

5. **AI_USAGE.md** — template with sections for documenting AI tool usage

**Verification:** `docker-compose up --build` → all 3 containers start, app accessible at `http://localhost:3000`, can login with demo credentials.

---

## 2. Shared Contracts & Interfaces

This section defines the exact interfaces and data shapes that enable parallel development. Modules depend on these contracts, not on each other's implementations.

### 2.1 Java Interfaces

#### TenantContext API (produced by M3, consumed by all backend modules)

```java
package com.expensetracker.security;

public final class TenantContext {
    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    public static void setCurrentTenant(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID getCurrentTenant() {
        UUID tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant context set.");
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
```

#### SecurityUtils API (produced by M3, consumed by all backend modules)

```java
package com.expensetracker.security;

public final class SecurityUtils {
    public static UUID getCurrentUserId();
    public static UUID getCurrentTenantId();  // delegates to TenantContext
    public static String getCurrentRole();
    public static boolean isAdmin();
    public static boolean isManager();
}
```

#### FileStorageService Interface (produced by M8, consumed by M6 for delete)

```java
package com.expensetracker.service;

public interface FileStorageService {
    String store(UUID tenantId, UUID expenseId, MultipartFile file);
    Resource load(String storagePath);
    void delete(String storagePath);
    void deleteAllForExpense(UUID tenantId, UUID expenseId);
}
```

**Decoupling note:** M6 (Expense CRUD) needs `FileStorageService` only for `deleteExpense()` (to remove receipt files when a DRAFT is deleted). If M8 is not complete yet, M6 should:
- Inject `FileStorageService` as `@Autowired(required = false)` or use `Optional<FileStorageService>`
- If not available, just delete the DB records (files can be orphaned and cleaned up later)

#### AuditLogService (produced by M6, consumed by M7 and M5)

```java
package com.expensetracker.service;

public class AuditLogService {
    public void log(UUID expenseId, AuditAction action, UUID performedById,
                    String comment, ExpenseStatus oldStatus, ExpenseStatus newStatus);

    public List<ExpenseAuditLog> getAuditTrail(UUID expenseId);
}
```

**Decoupling note:** Both M6 and M7 write audit logs. If they are building in parallel, either module can create AuditLogService. It is a simple class with no complex logic — creating it twice and merging is trivial.

### 2.2 DTO Classes — Ownership

| DTO | Owner Module | Consumers |
|-----|-------------|-----------|
| RegisterRequest, LoginRequest, RefreshRequest | M4 | — |
| AuthResponse | M4 | M12 (frontend types mirror this) |
| UserDto | M4 (creates), M5 (uses) | M12, M15 |
| CreateExpenseRequest, UpdateExpenseRequest | M6 | M13 |
| ExpenseDto, ExpenseDetailDto, ExpenseSummaryDto | M6 | M7, M13, M14 |
| ReceiptDto, AuditLogDto, CategoryDto | M6 | M8, M13, M15 |
| ApprovalRequest, BulkApprovalRequest, BulkApprovalResultDto | M7 | M14 |
| AnalyticsSummaryDto, CategorySpendDto, MonthlySpendDto, TeamSpendDto | M9 | M15 |
| ChangeRoleRequest, AssignManagerRequest | M5 | M15 |
| ErrorResponse | M11 | All modules |
| RateLimitResult | M10 | — |

**Conflict resolution:** If two modules need to create the same DTO, the first one to commit it wins. The second module uses the existing one. All DTOs live in `com.expensetracker.dto.request` or `com.expensetracker.dto.response`.

### 2.3 API Endpoint Contracts (for Frontend Mocking)

Frontend modules (M12-M15) should use these contracts to build against mocked data. Each API function should have a mock mode that returns hardcoded data matching these shapes.

**Auth endpoints (M12 consumes):**
```
POST /api/v1/auth/login
  Request:  { email: string, password: string }
  Response: { accessToken: string, refreshToken: string, user: UserDto }

POST /api/v1/auth/register
  Request:  { email, password, firstName, lastName, organizationId }
  Response: Same as login

POST /api/v1/auth/refresh
  Request:  { refreshToken: string }
  Response: { accessToken: string, refreshToken: string }
```

**Expense endpoints (M13 consumes):**
```
GET    /api/v1/expenses?status=...&categoryId=...&fromDate=...&toDate=...&page=0&size=20
POST   /api/v1/expenses          → { ...CreateExpenseRequest }  → ExpenseDto (201)
PUT    /api/v1/expenses/{id}     → { ...UpdateExpenseRequest }  → ExpenseDto (200)
GET    /api/v1/expenses/{id}     → ExpenseDetailDto (200)
POST   /api/v1/expenses/{id}/submit  → ExpenseDto (200)
DELETE /api/v1/expenses/{id}     → 204
POST   /api/v1/expenses/{id}/receipts  → multipart → ReceiptDto (201)
GET    /api/v1/expenses/{id}/receipts/{rid}  → binary file
DELETE /api/v1/expenses/{id}/receipts/{rid}  → 204
GET    /api/v1/categories  → CategoryDto[] (200)
```

**Approval endpoints (M14 consumes):**
```
GET  /api/v1/approvals/pending?page=0&size=20  → PaginatedResponse<ExpenseSummaryDto>
POST /api/v1/expenses/{id}/approve  → { comment? } → ExpenseDto
POST /api/v1/expenses/{id}/reject   → { comment }  → ExpenseDto
POST /api/v1/approvals/bulk  → BulkApprovalRequest → BulkApprovalResultDto
```

**Analytics endpoints (M15 consumes):**
```
GET /api/v1/analytics/summary?fromDate=...&toDate=...     → AnalyticsSummaryDto
GET /api/v1/analytics/by-category?fromDate=...&toDate=... → CategorySpendDto[]
GET /api/v1/analytics/by-month?months=6                   → MonthlySpendDto[]
GET /api/v1/analytics/by-team?fromDate=...&toDate=...     → TeamSpendDto[]
GET /api/v1/analytics/my-team?fromDate=...&toDate=...     → CategorySpendDto[]
```

**User management endpoints (M15 consumes):**
```
GET /api/v1/users?role=...&search=...&page=0&size=20  → PaginatedResponse<UserDto>
PUT /api/v1/users/{id}/role       → { role }      → UserDto
PUT /api/v1/users/{id}/manager    → { managerId } → UserDto
PUT /api/v1/users/{id}/deactivate → UserDto
```

### 2.4 Database Entity Contracts (from M3)

All service modules depend on these entity field names for repository queries:

| Entity | Key Fields for Queries |
|--------|----------------------|
| Organization | id, slug, currency, isActive |
| User | id, tenantId, email, passwordHash, firstName, lastName, role (Role enum), managerId, isActive, failedLoginAttempts, lockedUntil |
| RefreshToken | id, userId, tokenHash, expiresAt, isRevoked, replacedById |
| ExpenseCategory | id, tenantId, name, isActive |
| Expense | id, tenantId, submitterId, managerId, amount, currency, categoryId, merchantName, expenseDate, notes, status (ExpenseStatus enum), rejectionComment, approvedById, approvedAt |
| ExpenseReceipt | id, expenseId, fileName, filePath, contentType, fileSize |
| ExpenseAuditLog | id, expenseId, action (AuditAction enum), performedById, comment, oldStatus, newStatus, createdAt |

---

## 3. Parallel Execution Plan

### 3.1 Phase Timeline

```
                    Week 1                          Week 2
            Day 1-2     Day 3-4     Day 5      Day 1-2     Day 3-4     Day 5
           ┌─────────────────────┐
Phase 1:   │  M1  │  M2  │  M3  │
           │ Scaf │ Migr │ Enti │
           │folding│ ation│ ties │
           └──┬──────┬──────┬────┘
              │      │      │
              ▼      ▼      ▼
           ┌─────────────────────────────────────────┐
Phase 2:   │  M4   │  M5   │  M6   │  M7   │  M8   │
(Backend)  │ Auth  │ User  │Expens │Approv │ File  │
           │Module │ Mgmt  │e CRUD │al WF  │Upload │
           │       │       │       │       │       │
           │  M9   │ M10   │ M11   │       │       │
           │Analyt │ Rate  │ Error │       │       │
           │  ics  │ Limit │Handlng│       │       │
           └──┬──────┬──────┬──────┬───────┬───────┘
              │      │      │      │       │
Phase 3:   ┌──┴──────┴──────┴──────┴───────┴───────┐
(Frontend) │  M12  │  M13  │  M14  │  M15           │
           │ Auth  │Expense│Approv │ Admin           │
           │ Shell │  UI   │al UI  │Dashboard        │
           └──┬──────┬──────┬──────┬────────────────┘
              │      │      │      │
Phase 4:   ┌──┴──────┴──────┴──────┴────────────────┐
           │  M16 Integration Tests │  M17 Polish    │
           └─────────────────────────────────────────┘
```

### 3.2 Gantt-Like ASCII Chart (Parallel Execution Lanes)

```
Agent   Day 1    Day 2    Day 3    Day 4    Day 5    Day 6    Day 7    Day 8    Day 9    Day 10
─────   ──────   ──────   ──────   ──────   ──────   ──────   ──────   ──────   ──────   ──────
  A     [====M1 Scaffolding====]  [=======M4 Auth Module=======]  [===M16 Integration Tests===]
  B     [====M2 Migrations=====]  [====M5 User Management====]   [===M16 contd / M17 Polish===]
  C     [====M3 Entities=======]  [=======M6 Expense CRUD=======][===M16 contd================]
  D                               [======M7 Approval WF======]   [===M17 Documentation========]
  E                               [======M8 File Upload======]
  F                               [======M9 Analytics========]
  G                               [===M10 Rate Limiting===][M11]
  H     [===========M12 Frontend Auth & Shell============]  [====M13 Employee Expense UI====]
  I                                                         [====M14 Manager Approval UI====]
  J                                                         [====M15 Admin Dashboard UI=====]
```

### 3.3 Dependency Graph

```
              M1 (Scaffolding)
              │
    ┌─────────┼─────────┐
    │         │         │
    M2        M3        M12 (FE Scaffolding)
 (Migrate)  (Entities)   │
    │         │          ├──────────────┐
    │    ┌────┴────┐     │              │
    │    │         │     │              │
    └────┤  Phase1 ├─────┘              │
         │ Complete│                    │
         └────┬────┘                    │
              │                         │
    ┌────┬────┼────┬────┬────┬────┐     │
    │    │    │    │    │    │    │     │
    M4   M5   M6   M7   M8   M9  M10   │
   Auth User  Exp  Appr File Anly Rate  │
    │    │    │    │    │    │    │     │
    │    │    │    │    │    │    M11   │
    │    │    │    │    │    │    │     │
    └────┴────┴────┴────┴────┴────┘     │
              │                         │
              │              ┌──────────┤
              │              │          │
              │             M13  M14   M15
              │           ExpUI AprvUI AdminUI
              │              │    │     │
              └──────────────┴────┴─────┘
                         │
                    ┌────┴────┐
                    │         │
                   M16       M17
                  Tests      Docs
```

### 3.4 Critical Path

The critical path (longest sequential chain) determines the minimum completion time:

```
M1 → M3 → M4 → M16 → M17
(2d)  (2d)  (3d)  (3d)   (1d) = 11 days

OR

M1 → M12 → M13 → M16 → M17
(2d)  (3d)  (3d)   (3d)  (1d) = 12 days (if frontend is on critical path)
```

**Optimal parallel execution reduces calendar time to ~10-12 working days** with sufficient agents (4-6 working simultaneously).

---

## 4. Agent Assignment Strategy

### M1: Project Scaffolding

**Context for implementing agent:**
- You are setting up two projects: a Spring Boot 3.x Java 17 backend and a React 18 TypeScript frontend
- Follow the project structure exactly as defined in DESIGN.md Section 11
- Create all directory structures (empty packages/directories) so other agents can drop files into place
- The Docker Compose file should start PostgreSQL immediately for development
- Backend must compile (`mvn compile`), frontend must start (`npm run dev`)
- Read the full tech stack table in DESIGN.md Section 2 for exact versions

**Review checklist:**
- [ ] `mvn compile` succeeds with zero errors
- [ ] `npm install && npm run dev` starts Vite dev server
- [ ] `docker-compose up -d` starts PostgreSQL and it is accessible on port 5432
- [ ] All package directories from DESIGN.md Section 11 exist
- [ ] pom.xml includes ALL required dependencies (Spring Boot starters, JJWT, Flyway, Testcontainers)
- [ ] application.yml has correct datasource, JPA, Flyway, multipart, and server config
- [ ] Vite config has `/api` proxy to `http://localhost:8080`
- [ ] Tailwind CSS is configured and working
- [ ] .gitignore covers target/, node_modules/, .env, uploads/, *.class, *.jar

**Validation commands:**
```bash
cd expense-tracker-api && mvn compile
cd expense-tracker-ui && npm install && npm run build
docker-compose up -d && docker-compose ps  # PostgreSQL running
```

---

### M2: Database Migrations

**Context for implementing agent:**
- You are creating Flyway SQL migration files for PostgreSQL 15+
- Copy the EXACT SQL from DESIGN.md Section 3 (Table Definitions)
- V8 seed data must create realistic demo data for 2 organizations
- The BCrypt hash for `Password1` at cost 12 is: `$2a$12$LJ3m4ys3uz5uMUimGFnGT.N5BmIXCnGCaF.Ny3sTzqEMz/OoOKBMi` (pre-compute and verify this)
- Migration files must be idempotent-safe (use `IF NOT EXISTS` where appropriate)
- Seed data must use fixed UUIDs so other tests/docs can reference them

**Review checklist:**
- [ ] All 8 migration files present and correctly numbered (V1 through V8)
- [ ] SQL matches DESIGN.md exactly (table names, column names, types, constraints)
- [ ] All indexes from DESIGN.md are created
- [ ] CHECK constraints on role, status, and action columns
- [ ] UNIQUE constraints: email (global), (tenant_id, name) on categories
- [ ] V8 seed data includes 2 orgs, users with all 3 roles, default categories, sample expenses
- [ ] Flyway runs successfully against a fresh PostgreSQL database
- [ ] No syntax errors in any migration file

**Validation commands:**
```bash
docker-compose up -d  # ensure PostgreSQL is running
cd expense-tracker-api && mvn spring-boot:run  # Flyway runs on startup
# Then connect to DB and verify: \dt, \d users, etc.
```

---

### M3: Core Entities & Repositories

**Context for implementing agent:**
- You are creating the JPA data layer that ALL backend service modules will use
- Entity field names must match the migration column names exactly (use `@Column(name = "...")` where Java naming differs from SQL)
- Use `@Enumerated(EnumType.STRING)` for all enum fields
- Use `FetchType.LAZY` for all `@ManyToOne` relationships
- Repository query methods must include tenantId in ALL queries that return tenant-scoped data
- TenantContext and SecurityUtils are critical shared utilities — get them right
- Do NOT create services or controllers — only entities, enums, repositories, and the two utility classes

**Review checklist:**
- [ ] All 7 entity classes compile and map correctly to DB tables
- [ ] All 3 enums have correct values
- [ ] All 7 repository interfaces extend JpaRepository with correct generic types
- [ ] Repository methods include tenantId parameter where needed for isolation
- [ ] TenantContext uses ThreadLocal correctly with clear() method
- [ ] SecurityUtils provides methods for current user ID, tenant ID, and role
- [ ] Entity relationships use FetchType.LAZY
- [ ] @CreationTimestamp and @UpdateTimestamp on appropriate fields
- [ ] No circular dependency issues between entities

**Validation commands:**
```bash
cd expense-tracker-api && mvn compile  # entities and repositories compile
```

---

### M4: Authentication Module

**Context for implementing agent:**
- This is the most complex Phase 2 module — it integrates Spring Security, JWT, and the filter chain
- Study DESIGN.md Section 5 in detail — the JWT flow, refresh token rotation, and filter chain order are precisely specified
- The SecurityConfig must define the COMPLETE filter chain order (even if M10's rate limiting filters are not yet available — leave placeholder comments)
- JWT signing uses HS256 with JJWT library (io.jsonwebtoken)
- Refresh tokens are opaque random strings (not JWTs). Store SHA-256 hash in DB.
- Account lockout: 5 failures → 15 minute lock. Use `locked_until` timestamp comparison.
- BCrypt cost factor: 12

**Review checklist:**
- [ ] SecurityConfig disables CSRF, sets stateless session, configures CORS
- [ ] JwtAuthenticationFilter correctly extracts, validates, and parses JWT tokens
- [ ] TenantContextFilter sets and clears ThreadLocal correctly (finally block)
- [ ] Public endpoints (/auth/*) bypass authentication
- [ ] JwtTokenProvider generates tokens with correct claims (sub, tenantId, role, iat, exp)
- [ ] Refresh token rotation works: old token revoked, new token issued, chain linked
- [ ] Reuse detection works: revoked token → all user tokens revoked
- [ ] Account lockout triggers after 5 failures, expires after 15 minutes
- [ ] Registration validates password policy (8 chars, 1 upper, 1 digit)
- [ ] Registration checks email uniqueness (409 on duplicate)
- [ ] All auth endpoints return consistent JSON (not Spring Security defaults)
- [ ] 401 responses are JSON (not redirects or HTML)

**Validation commands:**
```bash
# Start app, then:
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"Password1","firstName":"Test","lastName":"User","organizationId":"<org-uuid>"}'
# Should return 201 with tokens

curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"Password1"}'
# Should return 200 with tokens

curl http://localhost:8080/api/v1/expenses
# Should return 401 (no token)

curl http://localhost:8080/api/v1/expenses \
  -H "Authorization: Bearer <token>"
# Should pass auth (may return empty or 403 depending on role)
```

---

### M5: User Management Module

**Context for implementing agent:**
- Admin-only endpoints for managing users within their organization
- Key complexity: manager reassignment must also reassign pending (SUBMITTED) expenses
- Deactivation has cascading effects: revoke tokens, cancel pending expenses
- Cannot deactivate self, cannot remove Manager role if they have assigned employees
- All queries must be scoped to `TenantContext.getCurrentTenant()`
- You will need AuditLogService for logging expense reassignments — create it if it does not exist yet (simple class, see Shared Contracts section 2.1)

**Review checklist:**
- [ ] All endpoints require ADMIN role (@PreAuthorize)
- [ ] Tenant isolation enforced on all operations
- [ ] Role change validation: cannot demote Manager with active reports (409)
- [ ] Manager assignment validates managerId is MANAGER or ADMIN in same tenant (400)
- [ ] Manager reassignment triggers pending expense reassignment with audit logging
- [ ] Deactivation: revokes tokens, cancels pending expenses, prevents deactivating self
- [ ] Deactivation of Manager with active reports → 409
- [ ] User list supports pagination, role filter, and search

**Validation commands:**
```bash
# Login as admin, then:
curl http://localhost:8080/api/v1/users \
  -H "Authorization: Bearer <admin-token>"
# Returns users in admin's org only

curl -X PUT http://localhost:8080/api/v1/users/<user-id>/role \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{"role": "MANAGER"}'
# Changes role
```

---

### M6: Expense CRUD Module

**Context for implementing agent:**
- This is the core business logic module — expense creation, editing, submission, and the expense state machine
- State machine transitions must be strictly enforced (see DESIGN.md Section 6)
- DRAFT expenses have relaxed validation (all fields optional). SUBMIT validation is strict (amount > 0, category valid, date not future, manager assigned)
- On submission: SNAPSHOT the submitter's current manager_id onto the expense record
- Resubmission (REJECTED → SUBMITTED): same validation, re-snapshot manager_id, clear rejectionComment
- AuditLogService must log all state transitions
- Category CRUD is included in this module for logical grouping
- Pagination format: `{ content: [...], page, size, totalElements, totalPages }`

**Review checklist:**
- [ ] DRAFT creation allows partial fields (all optional)
- [ ] Update only allowed in DRAFT or REJECTED status (409 otherwise)
- [ ] Update only allowed by submitter (403 otherwise)
- [ ] Submit validates all required fields
- [ ] Submit checks manager_id assigned on submitter (400 if null)
- [ ] Submit snapshots manager_id onto expense record
- [ ] Resubmit (from REJECTED): clears rejection comment, logs RESUBMITTED
- [ ] Delete only for DRAFT, only by submitter (hard delete)
- [ ] GET expense detail includes receipts and audit trail
- [ ] List supports filtering by status, categoryId, date range
- [ ] List returns paginated response with correct format
- [ ] Category CRUD: create, rename, soft-delete, list active
- [ ] Category name unique per tenant (409 on duplicate)
- [ ] Tenant isolation on all operations
- [ ] Audit trail records all state changes

**Validation commands:**
```bash
# Login as employee, then:
curl -X POST http://localhost:8080/api/v1/expenses \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"amount": 45.99, "merchantName": "Starbucks"}'
# Returns 201 with DRAFT expense

curl -X POST http://localhost:8080/api/v1/expenses/<id>/submit \
  -H "Authorization: Bearer <token>"
# Returns 200 with SUBMITTED expense (if all fields valid and manager assigned)
```

---

### M7: Approval Workflow Module

**Context for implementing agent:**
- Managers approve/reject expenses from their direct reports only
- Admins can approve/reject any expense in their org (fallback approver)
- Authorization check: `expense.managerId == currentUserId || currentUserIsAdmin`
- Rejection REQUIRES a comment (400 if empty)
- Bulk operations: max 50 per request, partial success is OK, report per-expense results
- State machine: only SUBMITTED expenses can be approved/rejected (409 otherwise)
- Set `approvedById` and `approvedAt` on approval
- Set `rejectionComment` on rejection
- Log every action in audit trail
- You will share AuditLogService with M6 — if it does not exist yet, create it

**Review checklist:**
- [ ] Pending approvals: Manager sees only their team's expenses, Admin sees all
- [ ] Approve sets approvedById, approvedAt, status=APPROVED
- [ ] Reject requires non-empty comment, sets rejectionComment, status=REJECTED
- [ ] Only SUBMITTED expenses can be approved/rejected (409)
- [ ] Authorization: assigned manager OR admin (403)
- [ ] Bulk approve/reject: max 50, partial success, per-expense results
- [ ] Each processed expense gets its own audit log entry
- [ ] Tenant isolation enforced
- [ ] Pending list sorted oldest first (FIFO)

**Validation commands:**
```bash
# Login as manager:
curl http://localhost:8080/api/v1/approvals/pending \
  -H "Authorization: Bearer <manager-token>"
# Returns pending expenses from direct reports

curl -X POST http://localhost:8080/api/v1/expenses/<id>/approve \
  -H "Authorization: Bearer <manager-token>" \
  -H "Content-Type: application/json" \
  -d '{"comment": "Approved"}'
# Returns expense with status APPROVED
```

---

### M8: File Upload Module

**Context for implementing agent:**
- Files stored locally at `./uploads/{tenantId}/{expenseId}/{uuid}.{ext}`
- FileStorageService interface must match the contract in Section 2.1 exactly
- Content type validation: only image/jpeg, image/png, application/pdf
- Size limit: 5MB per file, 3 files per expense
- Upload only to DRAFT or REJECTED expenses
- Download validates: auth + tenant (via expense) + authorization (submitter, assigned manager, or admin)
- Path traversal prevention: normalize resolved path and verify it starts with base dir
- Delete receipt: only from DRAFT expenses, only by submitter

**Review checklist:**
- [ ] FileStorageService interface matches contract
- [ ] LocalFileStorageService creates correct directory structure
- [ ] Path traversal prevention implemented
- [ ] Content type validation (reject .exe, .html, etc.)
- [ ] Size validation (5MB max, return 413)
- [ ] Receipt count validation (max 3 per expense, return 409)
- [ ] Upload only to DRAFT/REJECTED (409 otherwise)
- [ ] Download validates auth + tenant + authorization
- [ ] Download streams correct Content-Type and Content-Disposition
- [ ] Delete only from DRAFT, only by submitter
- [ ] uploads directory created on startup if not exists

**Validation commands:**
```bash
# Upload a receipt:
curl -X POST http://localhost:8080/api/v1/expenses/<id>/receipts \
  -H "Authorization: Bearer <token>" \
  -F "file=@receipt.jpg"
# Returns 201 with receipt metadata

# Download:
curl http://localhost:8080/api/v1/expenses/<id>/receipts/<rid> \
  -H "Authorization: Bearer <token>" --output receipt_download.jpg
# File downloaded
```

---

### M9: Analytics Module

**Context for implementing agent:**
- All analytics queries operate on APPROVED expenses only (except summary which counts all statuses)
- All queries scoped by tenant_id
- Date filtering: default to current month if no dates provided
- by-month: use PostgreSQL `TO_CHAR(expense_date, 'YYYY-MM')` for grouping (native query)
- Manager's team analytics: same structure as by-category but filtered by managerId
- Return empty arrays/zero counts for no-data scenarios (not 404)
- Consider creating the analytics repository queries if they are not yet in ExpenseRepository from M3

**Review checklist:**
- [ ] Summary endpoint returns correct counts per status
- [ ] By-category groups APPROVED expenses with SUM and COUNT
- [ ] By-month returns monthly totals for last N months
- [ ] By-team groups by manager with SUM and COUNT
- [ ] My-team endpoint scoped to calling manager's reports only
- [ ] Date range filtering works correctly
- [ ] Default dates (current month) applied when params missing
- [ ] Admin-only access enforced (except my-team which is Manager+Admin)
- [ ] Tenant isolation enforced
- [ ] Empty data returns empty array, not error

**Validation commands:**
```bash
curl http://localhost:8080/api/v1/analytics/summary \
  -H "Authorization: Bearer <admin-token>"
# Returns summary stats

curl "http://localhost:8080/api/v1/analytics/by-category?fromDate=2026-01-01&toDate=2026-12-31" \
  -H "Authorization: Bearer <admin-token>"
# Returns category breakdown
```

---

### M10: Rate Limiting Module

**Context for implementing agent:**
- Token bucket algorithm with timestamp-based refill (no timer threads)
- Two separate filters: AuthRateLimitFilter (per-IP, 20/min) and TenantRateLimitFilter (per-tenant, 100/min)
- AuthRateLimitFilter runs BEFORE JwtAuthFilter (no tenant context available)
- TenantRateLimitFilter runs AFTER TenantContextFilter (tenant context is set)
- In-memory storage using ConcurrentHashMap (document Redis as production improvement)
- Add rate limit headers to EVERY response (not just 429s)
- 429 response includes Retry-After header and JSON body
- The TokenBucket class must be thread-safe (synchronized)
- You will need to register these filters in SecurityConfig — if M4 has already created it, modify it; otherwise, document the registration instructions

**Review checklist:**
- [ ] TokenBucket algorithm correct: refill based on elapsed time, cap at capacity
- [ ] TokenBucket is thread-safe (synchronized methods)
- [ ] AuthRateLimitFilter applies only to /api/v1/auth/** paths
- [ ] TenantRateLimitFilter applies only to non-auth /api/v1/** paths
- [ ] Per-IP limiting: 20/min for auth endpoints
- [ ] Per-tenant limiting: 100/min for API endpoints
- [ ] 429 response includes Retry-After header and JSON body
- [ ] Rate limit headers on every response: X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset
- [ ] Different tenants/IPs have independent buckets
- [ ] Client IP extraction handles X-Forwarded-For

**Validation commands:**
```bash
# Rapid-fire requests to test limiting:
for i in $(seq 1 25); do
  curl -s -o /dev/null -w "%{http_code}" \
    -X POST http://localhost:8080/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"test@test.com","password":"wrong"}'
  echo " request $i"
done
# First 20 should return 401, last 5 should return 429
```

---

### M11: Global Error Handling & DTOs

**Context for implementing agent:**
- This module is small but critical for consistency
- The error response format MUST match DESIGN.md Section 4 exactly
- GlobalExceptionHandler must handle ALL exception types (custom + Spring + generic)
- JacksonConfig ensures dates serialize as ISO 8601 strings
- ErrorResponse should be a Java record for conciseness
- This module has no dependencies on other Phase 2 modules and can complete quickly

**Review checklist:**
- [ ] All 7 custom exception classes created
- [ ] GlobalExceptionHandler handles every exception type listed
- [ ] Error responses match format: `{ error, code, details, fieldErrors, timestamp, path }`
- [ ] Validation errors (MethodArgumentNotValidException) include fieldErrors array
- [ ] MaxUploadSizeExceededException returns 413
- [ ] AccessDeniedException returns 403 (not Spring's default)
- [ ] Generic Exception returns 500 with safe message (no stack trace in response)
- [ ] JacksonConfig registers JavaTimeModule for LocalDate/LocalDateTime serialization

**Validation commands:**
```bash
# Send invalid request to trigger validation error:
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{}'
# Should return 400 with fieldErrors array in consistent format
```

---

### M12-M15: Frontend Modules

(See individual module sections above for detailed instructions. Additional agent notes:)

**Common context for ALL frontend agents:**
- Use Tailwind CSS for styling (utility classes, no custom CSS files unless necessary)
- Use TypeScript strictly — no `any` types, define proper interfaces for all data
- API calls go through the shared Axios instance from `api/axiosInstance.ts` (M12 creates this)
- If the backend is not available yet, create mock data matching the API contracts in Section 2.3
- Use React Router v6 patterns (`useNavigate`, `useParams`, `useSearchParams`)
- Toast notifications for success/error feedback
- Loading states: skeleton loaders for lists/tables, spinners for actions
- Form validation: inline errors below fields, disable submit button until valid
- Responsive design: test at 1024px (tablet) and 375px (mobile)

---

### M16: Integration Testing

**Context for implementing agent:**
- Use `@SpringBootTest(webEnvironment = RANDOM_PORT)` with Testcontainers PostgreSQL
- Create a `BaseIntegrationTest` superclass with shared setup (container, helper methods)
- Tests should create their own data (not depend on seed data) for isolation
- Use `TestRestTemplate` or `WebTestClient` for HTTP calls
- Each test class should be self-contained and runnable independently
- Focus on business-critical flows: auth, tenant isolation, approval workflow

**Review checklist:**
- [ ] Testcontainers PostgreSQL starts and Flyway migrations run
- [ ] Auth tests cover: register, login, lockout, refresh, reuse detection
- [ ] Tenant isolation tests prove cross-tenant access returns 404 (not data)
- [ ] Expense workflow tests cover full lifecycle (create → submit → approve)
- [ ] Rejection and resubmission flow tested
- [ ] Rate limiting tests verify limits and independence
- [ ] All tests pass with `mvn test`
- [ ] Tests are independent (not dependent on execution order)

**Validation commands:**
```bash
cd expense-tracker-api && mvn test
# All tests should pass (green)
```

---

### M17: Documentation & Polish

**Context for implementing agent:**
- README must enable a reviewer to get the app running in under 10 minutes
- Docker Compose `docker-compose up` should be the primary "quick start"
- Include demo credentials from seed data
- Dockerfiles should use multi-stage builds for small images
- Frontend nginx config must proxy `/api` to the backend container
- AI_USAGE.md is a template — do not fill in fabricated content

**Review checklist:**
- [ ] README covers: prerequisites, quick start, manual setup, demo credentials, testing, project structure
- [ ] `docker-compose up --build` brings up all 3 services and app is accessible
- [ ] Backend Dockerfile builds and runs correctly
- [ ] Frontend Dockerfile builds and serves via nginx
- [ ] nginx config proxies /api to backend
- [ ] AI_USAGE.md template is present

**Validation commands:**
```bash
docker-compose down -v  # clean state
docker-compose up --build -d
# Wait for services to start
curl http://localhost:3000        # frontend loads
curl http://localhost:8080/api/v1/auth/login  # backend responds (400 expected, but JSON)
docker-compose logs backend      # no errors
```

---

## 5. Integration Points & Merge Strategy

### 5.1 Branch Strategy

Each module should be developed on its own branch:

```
main
 ├── feature/m1-scaffolding
 ├── feature/m2-migrations
 ├── feature/m3-entities
 ├── feature/m4-auth
 ├── feature/m5-user-management
 ├── feature/m6-expense-crud
 ├── feature/m7-approval-workflow
 ├── feature/m8-file-upload
 ├── feature/m9-analytics
 ├── feature/m10-rate-limiting
 ├── feature/m11-error-handling
 ├── feature/m12-frontend-auth
 ├── feature/m13-expense-ui
 ├── feature/m14-approval-ui
 ├── feature/m15-admin-dashboard
 ├── feature/m16-integration-tests
 └── feature/m17-documentation
```

### 5.2 Merge Order and Checkpoints

**Checkpoint 1: Foundation Merge (after Phase 1)**

Merge order: M1 → M2 → M3

After merge, verify:
- [ ] `mvn compile` succeeds
- [ ] App starts and Flyway runs all migrations
- [ ] Database has all tables with correct schema
- [ ] Demo data is present
- [ ] Frontend dev server starts

**Checkpoint 2: Backend Services Merge (after Phase 2)**

Merge order: M11 → M4 → M5 → M6 → M7 → M8 → M9 → M10

Rationale for order:
- M11 (error handling) first — provides exception classes all modules use
- M4 (auth) next — provides security config that other modules rely on
- M5, M6, M7, M8, M9 — any order (they are independent), but merge M6 before M7 since M7 may reference ExpenseService methods
- M10 (rate limiting) last — modifies SecurityConfig filter chain

After merge, verify:
- [ ] `mvn compile` succeeds with all modules merged
- [ ] `mvn test` passes (unit tests if any)
- [ ] App starts without errors
- [ ] All endpoints respond correctly (test with curl)
- [ ] No merge conflicts in shared files (SecurityConfig, repositories)

**Resolving conflicts in shared files:**

The most likely merge conflicts will be in:
1. **SecurityConfig.java** — M4 creates it, M10 adds filters. Resolution: merge filter registrations in correct order.
2. **ExpenseRepository.java** — M3 creates it, M6 and M9 add query methods. Resolution: combine all methods.
3. **pom.xml** — Multiple modules may add dependencies. Resolution: deduplicate.

**Strategy:** Use a single integration agent to perform all Phase 2 merges sequentially, resolving conflicts as they arise.

**Checkpoint 3: Frontend Merge (after Phase 3)**

Merge order: M12 → M13 → M14 → M15

Rationale: M12 creates the app shell and routing. M13-M15 add pages into the existing routing structure.

After merge, verify:
- [ ] `npm run build` succeeds
- [ ] All routes render correct pages
- [ ] Login/register works end-to-end (requires backend running)
- [ ] Role-based navigation shows correct links
- [ ] No TypeScript errors

**Checkpoint 4: Final Integration (after Phase 4)**

Merge: M16 → M17

After merge, verify:
- [ ] `mvn test` passes all integration tests
- [ ] `docker-compose up --build` works from clean state
- [ ] Full end-to-end flow works: register → login → create expense → submit → approve → view dashboard
- [ ] README instructions are accurate

### 5.3 Conflict Prevention Strategies

1. **File ownership:** Each module has clearly defined files it creates. No two modules create the same file (except where noted: AuditLogService, SecurityConfig).

2. **Package boundaries:** Backend modules write to different packages. Controllers, services, and DTOs are in separate files per domain (Auth, User, Expense, Approval, Analytics, Receipt, Category).

3. **Repository methods:** M3 creates base repository interfaces. M6, M7, M9 add query methods. Since these are interfaces, methods can be combined trivially (no implementation conflicts).

4. **SecurityConfig:** M4 creates the initial config. M10 adds rate limiting filters. Resolution: M10 agent should add its filter beans and registration to the existing SecurityConfig created by M4.

5. **Frontend routing:** M12 creates App.tsx with placeholder page components. M13, M14, M15 replace placeholders with real implementations in separate files. No routing conflicts since routes are defined in M12 and pages are in separate files.

### 5.4 Smoke Test Script (Post-Integration)

After all merges, run this end-to-end smoke test:

```bash
#!/bin/bash
# smoke-test.sh — Run after full integration

BASE_URL="http://localhost:8080/api/v1"

# 1. Register a new user
echo "=== Register ==="
REGISTER_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"email":"smoke@test.com","password":"Password1","firstName":"Smoke","lastName":"Test","organizationId":"<acme-org-id>"}')
echo "$REGISTER_RESPONSE"
ACCESS_TOKEN=$(echo "$REGISTER_RESPONSE" | jq -r '.accessToken')

# 2. Login
echo "=== Login ==="
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"smoke@test.com","password":"Password1"}')
echo "$LOGIN_RESPONSE"

# 3. Get categories
echo "=== Categories ==="
curl -s "$BASE_URL/categories" -H "Authorization: Bearer $ACCESS_TOKEN"

# 4. Create expense
echo "=== Create Expense ==="
EXPENSE=$(curl -s -X POST "$BASE_URL/expenses" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount": 99.99, "categoryId": "<category-id>", "merchantName": "Test Merchant", "expenseDate": "2026-03-15", "notes": "Smoke test"}')
echo "$EXPENSE"
EXPENSE_ID=$(echo "$EXPENSE" | jq -r '.id')

# 5. List expenses
echo "=== List Expenses ==="
curl -s "$BASE_URL/expenses" -H "Authorization: Bearer $ACCESS_TOKEN"

# 6. Get expense detail
echo "=== Expense Detail ==="
curl -s "$BASE_URL/expenses/$EXPENSE_ID" -H "Authorization: Bearer $ACCESS_TOKEN"

# 7. Submit expense (will fail if no manager assigned — expected for new user)
echo "=== Submit Expense ==="
curl -s -X POST "$BASE_URL/expenses/$EXPENSE_ID/submit" \
  -H "Authorization: Bearer $ACCESS_TOKEN"

# 8. Login as admin and check analytics
echo "=== Admin Analytics ==="
ADMIN_TOKEN=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@acme.com","password":"Password1"}' | jq -r '.accessToken')
curl -s "$BASE_URL/analytics/summary" -H "Authorization: Bearer $ADMIN_TOKEN"

echo ""
echo "=== Smoke test complete ==="
```

---

*This implementation plan defines 17 modules across 4 phases, enabling up to 10 parallel agents during peak execution. The critical path is approximately 10-12 working days with full parallelization. Each module section contains sufficient detail for an implementing agent to work independently without blocking on other modules.*
