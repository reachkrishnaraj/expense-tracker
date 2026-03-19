# Technical Stories: Problem 1 — Multi-Tenant Expense Tracker

**Date:** 2026-03-18
**Decisions locked:** See PM_ANALYSIS_Problem1.md Section 4

---

## Epics Overview

| Epic | Stories | Priority |
|------|---------|----------|
| E1: Multi-Tenancy & Data Isolation | 3 | P0 — Foundation |
| E2: Authentication & Security | 4 | P0 — Foundation |
| E3: User & Role Management | 4 | P0 — Foundation |
| E4: Expense Submission | 5 | P0 — Core |
| E5: Approval Workflow | 4 | P0 — Core |
| E6: Admin Dashboard & Analytics | 3 | P1 — Value-Add |
| E7: API Rate Limiting | 2 | P1 — Value-Add |
| E8: Frontend Shell & Navigation | 3 | P0 — Foundation |

---

## E1: Multi-Tenancy & Data Isolation

### S1.1 — Tenant Data Model & Seed Infrastructure

**As a** system operator,
**I want** organizations to be pre-seeded in the database,
**So that** users can be associated with an organization without self-service org creation.

**Acceptance Criteria:**
- [ ] `organizations` table exists with columns: `id (UUID)`, `name`, `slug (unique)`, `currency (default USD)`, `created_at`, `updated_at`, `is_active (default true)`
- [ ] A seed script / migration inserts at least 2 sample organizations for development/demo
- [ ] All tenant-scoped tables include a `tenant_id` (FK to `organizations.id`) column with a NOT NULL constraint
- [ ] Database indexes exist on `tenant_id` for all tenant-scoped tables

**Technical Notes:**
- Use Flyway or Liquibase for migrations
- `slug` used for human-readable org identification (e.g., invite URLs)

---

### S1.2 — Tenant Isolation at the Service/Repository Layer

**As a** developer,
**I want** all data queries to be automatically scoped to the current user's tenant,
**So that** Tenant A can never access Tenant B's data, even through bugs.

**Acceptance Criteria:**
- [ ] A `TenantContext` (ThreadLocal or request-scoped bean) holds the current tenant ID, populated from the authenticated JWT
- [ ] All repository queries include a `WHERE tenant_id = :currentTenantId` condition — either via a base repository, Hibernate filter, or explicit query parameter
- [ ] Integration test proves: User from Org A cannot retrieve expenses, users, or categories from Org B — returns empty results, not 403
- [ ] No REST endpoint exists that accepts `tenant_id` as a request parameter — tenant is always derived from auth context

**Technical Notes:**
- Hibernate `@Filter` or Spring Data JPA `@Query` with SpEL expressions are good patterns
- Consider a `TenantAwareRepository` base class

---

### S1.3 — Tenant Isolation Integration Tests

**As a** developer,
**I want** automated tests that verify cross-tenant data isolation,
**So that** regressions in tenant isolation are caught before deployment.

**Acceptance Criteria:**
- [ ] Test: Create expenses in Org A and Org B → query as Org A user → only Org A expenses returned
- [ ] Test: Attempt to access Org B expense by ID as Org A user → 404 (not 403, to avoid leaking existence)
- [ ] Test: Attempt to approve Org B expense as Org A manager → 404
- [ ] Test: Admin dashboard for Org A shows zero data from Org B
- [ ] Tests cover: expenses, users, categories

---

## E2: Authentication & Security

### S2.1 — User Registration (Join Existing Organization)

**As a** new user,
**I want** to register an account and join a pre-existing organization,
**So that** I can start using the expense system.

**Acceptance Criteria:**
- [ ] `POST /api/v1/auth/register` accepts: `email`, `password`, `firstName`, `lastName`, `organizationId`
- [ ] Password stored as bcrypt hash (cost factor 12)
- [ ] Password validation: minimum 8 characters, at least 1 uppercase letter, at least 1 digit
- [ ] Email must be unique across the entire system
- [ ] `organizationId` must reference a valid, active organization — else 400 with clear error
- [ ] New users are assigned the `EMPLOYEE` role by default
- [ ] New users have `manager_id = NULL` initially (cannot submit expenses until a manager is assigned by Admin)
- [ ] Returns 201 with JWT access token + refresh token on success
- [ ] Returns 409 if email already exists
- [ ] Returns 400 with validation errors for invalid input

**Technical Notes:**
- Consider whether org join should require an invite token for security — for MVP, direct join by org ID is acceptable. Document as a future hardening item.

---

### S2.2 — Login & JWT Issuance

**As a** registered user,
**I want** to log in with email and password and receive a JWT,
**So that** I can access the system securely.

**Acceptance Criteria:**
- [ ] `POST /api/v1/auth/login` accepts `email` and `password`
- [ ] Returns 200 with: `accessToken` (JWT, 15-min expiry), `refreshToken` (opaque or JWT, 7-day expiry), `user` object (id, email, name, role, organizationId)
- [ ] JWT payload includes: `sub` (user ID), `tenantId` (org ID), `role`, `iat`, `exp`
- [ ] JWT signed with HS256 or RS256 (key loaded from config, not hardcoded)
- [ ] Returns 401 for invalid credentials — generic message ("Invalid email or password"), no indication of which field is wrong
- [ ] Account lockout: after 5 consecutive failed attempts, lock for 15 minutes. Return 429 with `Retry-After` header.
- [ ] Failed attempt counter resets on successful login

---

### S2.3 — Refresh Token Rotation

**As a** logged-in user,
**I want** my session to be extended seamlessly via refresh tokens,
**So that** I don't have to re-login frequently while maintaining security.

**Acceptance Criteria:**
- [ ] `POST /api/v1/auth/refresh` accepts `refreshToken`
- [ ] Returns new `accessToken` + new `refreshToken` (old refresh token is invalidated)
- [ ] Refresh tokens stored in a `refresh_tokens` table with: `id`, `user_id`, `token_hash`, `expires_at`, `is_revoked`, `replaced_by_id`, `created_at`
- [ ] If a revoked/used refresh token is presented (reuse detection), revoke the ENTIRE refresh token family for that user — force re-login
- [ ] Returns 401 if refresh token is expired, revoked, or not found
- [ ] On logout (`POST /api/v1/auth/logout`), the refresh token is revoked

**Technical Notes:**
- Refresh token rotation with reuse detection is critical for security — if an attacker steals a refresh token but the legitimate user refreshes first, the reuse triggers a full revocation.

---

### S2.4 — Spring Security Filter Chain

**As a** developer,
**I want** a properly configured Spring Security filter chain,
**So that** authentication and authorization are enforced consistently across all endpoints.

**Acceptance Criteria:**
- [ ] Custom `JwtAuthenticationFilter` extracts and validates JWT from `Authorization: Bearer <token>` header
- [ ] Filter populates `SecurityContext` with user ID, tenant ID, and role
- [ ] Filter populates `TenantContext` (from S1.2) with the tenant ID
- [ ] Public endpoints excluded from auth: `/api/v1/auth/register`, `/api/v1/auth/login`, `/api/v1/auth/refresh`
- [ ] All other `/api/**` endpoints require a valid JWT
- [ ] CORS configured: allow frontend origin (configurable), credentials allowed
- [ ] CSRF disabled (stateless JWT auth)
- [ ] Proper 401 response for missing/invalid/expired tokens (not Spring's default redirect to /login)
- [ ] Method-level authorization via `@PreAuthorize` for role-specific endpoints

---

## E3: User & Role Management

### S3.1 — User Data Model

**As a** developer,
**I want** a user entity that supports roles and manager assignment,
**So that** RBAC and team structure can be enforced.

**Acceptance Criteria:**
- [ ] `users` table: `id (UUID)`, `tenant_id (FK)`, `email`, `password_hash`, `first_name`, `last_name`, `role (ENUM: EMPLOYEE, MANAGER, ADMIN)`, `manager_id (FK to users.id, nullable)`, `is_active (default true)`, `failed_login_attempts (default 0)`, `locked_until (nullable timestamp)`, `created_at`, `updated_at`
- [ ] Unique constraint on `email`
- [ ] `manager_id` is self-referential FK — a Manager or Admin user's ID
- [ ] Check constraint or app-level validation: `manager_id` must reference a user with role MANAGER or ADMIN within the same tenant
- [ ] Index on `(tenant_id, role)` and `(tenant_id, manager_id)`

---

### S3.2 — Admin: List & Manage Users

**As an** Admin,
**I want** to view all users in my organization and manage their roles and manager assignments,
**So that** the organization structure stays current.

**Acceptance Criteria:**
- [ ] `GET /api/v1/users` — returns paginated list of users in the current tenant (Admin only)
- [ ] Response includes: id, email, firstName, lastName, role, managerId, managerName, isActive, createdAt
- [ ] Supports query params: `role` (filter), `search` (name/email substring), `page`, `size`
- [ ] `PUT /api/v1/users/{id}/role` — Admin changes a user's role (body: `{ "role": "MANAGER" }`)
- [ ] Changing a user FROM Manager role: system checks for employees assigned to this manager. If any exist, return 409 with message "Reassign employees before changing this user's role."
- [ ] `PUT /api/v1/users/{id}/manager` — Admin assigns a manager to a user (body: `{ "managerId": "uuid" }`)
- [ ] `managerId` must reference a MANAGER or ADMIN in the same tenant — else 400
- [ ] All endpoints enforce Admin role via `@PreAuthorize`
- [ ] All endpoints enforce tenant isolation

---

### S3.3 — Admin: Reassign Manager (with Pending Expense Handling)

**As an** Admin,
**I want** to reassign an employee's manager,
**So that** approval workflows continue uninterrupted when org structure changes.

**Acceptance Criteria:**
- [ ] When `manager_id` is updated on a user, all of that user's SUBMITTED (pending) expenses are reassigned to the new manager
- [ ] The reassignment is logged: expense audit trail records "Reassigned from Manager X to Manager Y by Admin Z"
- [ ] Previously APPROVED or REJECTED expenses retain their original approver — history is not rewritten
- [ ] If a Manager user is deactivated, Admin is prompted (via 409 response) to first reassign all their reports

---

### S3.4 — Admin: Deactivate User

**As an** Admin,
**I want** to deactivate a user who has left the organization,
**So that** they can no longer access the system while preserving historical data.

**Acceptance Criteria:**
- [ ] `PUT /api/v1/users/{id}/deactivate` — sets `is_active = false`
- [ ] Deactivated users cannot log in — auth returns 401 "Account deactivated"
- [ ] All active refresh tokens for the user are revoked
- [ ] If user is an Employee with SUBMITTED expenses: those expenses move to CANCELLED state
- [ ] If user is a Manager with active reports: return 409 — "Reassign employees before deactivating"
- [ ] Admin cannot deactivate themselves
- [ ] Historical data (past expenses, approvals) is preserved — soft delete only

---

## E4: Expense Submission

### S4.1 — Expense Data Model

**As a** developer,
**I want** an expense entity with full lifecycle tracking,
**So that** submission, approval, and analytics are well-supported.

**Acceptance Criteria:**
- [ ] `expenses` table: `id (UUID)`, `tenant_id (FK)`, `submitter_id (FK to users)`, `manager_id (FK to users, set at submission time)`, `amount (DECIMAL(12,2))`, `currency (VARCHAR, inherited from org)`, `category_id (FK to expense_categories)`, `merchant_name (nullable)`, `expense_date (DATE)`, `notes (TEXT, nullable)`, `status (ENUM: DRAFT, SUBMITTED, APPROVED, REJECTED, CANCELLED)`, `rejection_comment (TEXT, nullable)`, `approved_by_id (FK to users, nullable)`, `approved_at (TIMESTAMP, nullable)`, `created_at`, `updated_at`
- [ ] `expense_receipts` table: `id (UUID)`, `expense_id (FK)`, `file_name`, `file_path`, `content_type`, `file_size_bytes`, `created_at`
- [ ] `expense_audit_log` table: `id (UUID)`, `expense_id (FK)`, `action (ENUM: CREATED, SUBMITTED, APPROVED, REJECTED, RESUBMITTED, CANCELLED, REASSIGNED)`, `performed_by_id (FK to users)`, `comment (nullable)`, `old_status`, `new_status`, `created_at`
- [ ] `expense_categories` table: `id (UUID)`, `tenant_id (FK)`, `name`, `is_active (default true)`, `created_at`
- [ ] Seed default categories per org: Travel, Meals, Office Supplies, Software, Equipment, Other
- [ ] Indexes on: `(tenant_id, submitter_id)`, `(tenant_id, manager_id, status)`, `(tenant_id, status)`, `(tenant_id, category_id)`

---

### S4.2 — Create & Save Draft Expense

**As an** Employee,
**I want** to create an expense and save it as a draft,
**So that** I can fill it in over time before submitting for approval.

**Acceptance Criteria:**
- [ ] `POST /api/v1/expenses` creates an expense in DRAFT status
- [ ] Request body: `{ amount?, categoryId?, merchantName?, expenseDate?, notes? }` — all fields optional for draft
- [ ] Returns 201 with the created expense object (including generated ID)
- [ ] Only users with role EMPLOYEE or MANAGER can create expenses
- [ ] `tenant_id` set from auth context — never from request body
- [ ] `submitter_id` set to the authenticated user
- [ ] Audit log entry created: action=CREATED
- [ ] `PUT /api/v1/expenses/{id}` — update a DRAFT expense. Same fields. Returns 200.
- [ ] Update only allowed if expense is in DRAFT or REJECTED status — else 409
- [ ] Update only allowed by the original submitter — else 403
- [ ] `GET /api/v1/expenses/{id}` — returns expense with receipts and audit log
- [ ] Tenant isolation enforced — users can only access expenses within their org

---

### S4.3 — Submit Expense for Approval

**As an** Employee,
**I want** to submit a draft expense for manager approval,
**So that** the approval process begins.

**Acceptance Criteria:**
- [ ] `POST /api/v1/expenses/{id}/submit` — transitions DRAFT → SUBMITTED
- [ ] Validation on submit (not on draft save):
  - `amount` must be > 0
  - `categoryId` must reference an active category in the same tenant
  - `expenseDate` must be present and not in the future
- [ ] If the submitter has no `manager_id` assigned, return 400: "No manager assigned. Contact your administrator."
- [ ] On submission, snapshot `manager_id` from the submitter's current manager → stored on the expense record (so later reassignment doesn't silently change the approver)
- [ ] Status changes to SUBMITTED
- [ ] Audit log entry: action=SUBMITTED
- [ ] Returns 200 with updated expense
- [ ] If expense is in REJECTED status, same endpoint transitions REJECTED → SUBMITTED (resubmission). Audit log: action=RESUBMITTED

---

### S4.4 — Upload Receipt to Expense

**As an** Employee,
**I want** to upload receipt images/PDFs to my expense,
**So that** I can provide proof of the expense.

**Acceptance Criteria:**
- [ ] `POST /api/v1/expenses/{id}/receipts` — multipart file upload
- [ ] Allowed content types: `image/jpeg`, `image/png`, `application/pdf`
- [ ] Max file size: 5MB per file
- [ ] Max 3 receipts per expense
- [ ] Returns 400 if type/size/count validation fails
- [ ] Only the expense submitter can upload receipts
- [ ] Receipts can be uploaded to DRAFT or REJECTED expenses only — not SUBMITTED/APPROVED
- [ ] File stored at: `uploads/{tenant_id}/{expense_id}/{uuid}.{ext}` (local filesystem for MVP)
- [ ] Record created in `expense_receipts` table
- [ ] `GET /api/v1/expenses/{id}/receipts/{receiptId}` — streams the file with correct `Content-Type`
- [ ] Receipt download endpoint validates: auth + tenant isolation + (submitter OR assigned manager OR admin)
- [ ] `DELETE /api/v1/expenses/{id}/receipts/{receiptId}` — only for DRAFT expenses, only by submitter

---

### S4.5 — List Own Expenses (Employee View)

**As an** Employee,
**I want** to see all my expenses with filtering and pagination,
**So that** I can track my submission history and status.

**Acceptance Criteria:**
- [ ] `GET /api/v1/expenses` — returns paginated list of the current user's expenses
- [ ] Default sort: `created_at DESC` (most recent first)
- [ ] Query params: `status` (filter), `categoryId` (filter), `fromDate` / `toDate` (filter on expense_date), `page` (default 0), `size` (default 20, max 100)
- [ ] Response shape: `{ content: [...], page: number, size: number, totalElements: number, totalPages: number }`
- [ ] Each expense in the list includes: id, amount, currency, category name, merchantName, expenseDate, status, createdAt, receiptCount
- [ ] Tenant isolation enforced
- [ ] Manager users calling this endpoint see their OWN expenses (not their team's — that's a separate endpoint)

---

## E5: Approval Workflow

### S5.1 — Manager: View Pending Team Expenses

**As a** Manager,
**I want** to see all pending expenses from my direct reports,
**So that** I can review and act on them.

**Acceptance Criteria:**
- [ ] `GET /api/v1/approvals/pending` — returns paginated list of SUBMITTED expenses where `expense.manager_id = currentUserId`
- [ ] Only users with MANAGER or ADMIN role can access — else 403
- [ ] Each item includes: id, submitterName, submitterEmail, amount, currency, category name, merchantName, expenseDate, notes, receiptCount, submittedAt
- [ ] Sort: oldest first (FIFO — first submitted, first reviewed)
- [ ] Query params: `submitterId` (filter to specific employee), `categoryId`, `page`, `size`
- [ ] Tenant isolation enforced
- [ ] Admin calling this endpoint sees ALL pending expenses in the org (acts as fallback approver)

---

### S5.2 — Manager: Approve Expense

**As a** Manager,
**I want** to approve an expense from my direct report,
**So that** the employee knows their expense has been accepted.

**Acceptance Criteria:**
- [ ] `POST /api/v1/expenses/{id}/approve` — transitions SUBMITTED → APPROVED
- [ ] Optional body: `{ "comment": "..." }`
- [ ] Only the assigned `manager_id` on the expense can approve — OR an Admin in the same tenant
- [ ] Sets `approved_by_id` to the current user, `approved_at` to now
- [ ] Audit log entry: action=APPROVED, comment if provided
- [ ] Returns 200 with updated expense
- [ ] If expense is not SUBMITTED, return 409 "Expense is not in a submittable state for approval"
- [ ] Tenant isolation enforced

---

### S5.3 — Manager: Reject Expense

**As a** Manager,
**I want** to reject an expense with a reason,
**So that** the employee understands why and can correct and resubmit.

**Acceptance Criteria:**
- [ ] `POST /api/v1/expenses/{id}/reject` — transitions SUBMITTED → REJECTED
- [ ] Required body: `{ "comment": "reason for rejection" }` — comment must not be empty
- [ ] Only the assigned `manager_id` on the expense can reject — OR an Admin in the same tenant
- [ ] Sets `rejection_comment` on the expense
- [ ] Audit log entry: action=REJECTED, comment
- [ ] Returns 200 with updated expense
- [ ] If expense is not SUBMITTED, return 409
- [ ] Tenant isolation enforced

---

### S5.4 — Manager: Bulk Approve/Reject

**As a** Manager,
**I want** to approve or reject multiple expenses at once,
**So that** I can process my team's expenses efficiently.

**Acceptance Criteria:**
- [ ] `POST /api/v1/approvals/bulk` — body: `{ "action": "APPROVE|REJECT", "expenseIds": ["uuid1", "uuid2", ...], "comment": "..." }`
- [ ] Comment required for REJECT, optional for APPROVE
- [ ] Max 50 expenses per bulk action
- [ ] Only processes expenses where `manager_id = currentUser` (or admin) AND status = SUBMITTED
- [ ] Returns summary: `{ "processed": 8, "skipped": 2, "errors": [{ "expenseId": "...", "reason": "..." }] }`
- [ ] Each processed expense gets its own audit log entry
- [ ] Entire operation does NOT need to be atomic — partial success is acceptable. Report results per expense.
- [ ] Tenant isolation enforced

---

## E6: Admin Dashboard & Analytics

### S6.1 — Expense Categories Management

**As an** Admin,
**I want** to manage expense categories for my organization,
**So that** employees can categorize expenses according to our org's needs.

**Acceptance Criteria:**
- [ ] `GET /api/v1/categories` — returns all active categories for the current tenant (any authenticated role)
- [ ] `POST /api/v1/categories` — Admin creates a new category. Body: `{ "name": "..." }`. Name unique per tenant.
- [ ] `PUT /api/v1/categories/{id}` — Admin renames a category
- [ ] `DELETE /api/v1/categories/{id}` — soft-delete (set `is_active = false`). Expenses with this category are unaffected. Category no longer appears in submission dropdowns.
- [ ] Cannot hard-delete a category that has associated expenses
- [ ] Tenant isolation enforced

---

### S6.2 — Admin Dashboard: Spend Analytics API

**As an** Admin,
**I want** to see organization-wide spend analytics,
**So that** I can understand spending patterns and make informed decisions.

**Acceptance Criteria:**
- [ ] `GET /api/v1/analytics/by-category` — returns total approved spend per category within date range
  - Query params: `fromDate`, `toDate` (default: current month)
  - Response: `[{ "categoryId": "...", "categoryName": "...", "totalAmount": 1234.56, "expenseCount": 12 }]`
- [ ] `GET /api/v1/analytics/by-month` — returns total approved spend per month
  - Query params: `months` (default: 6, max: 12)
  - Response: `[{ "month": "2026-03", "totalAmount": 5678.90, "expenseCount": 45 }]`
- [ ] `GET /api/v1/analytics/by-team` — returns total approved spend per manager (team)
  - Query params: `fromDate`, `toDate`
  - Response: `[{ "managerId": "...", "managerName": "...", "totalAmount": 3456.78, "expenseCount": 23 }]`
- [ ] `GET /api/v1/analytics/summary` — returns high-level stats
  - Response: `{ "totalSubmitted": 100, "totalApproved": 75, "totalRejected": 15, "totalPending": 10, "totalApprovedAmount": 50000.00 }`
  - Query params: `fromDate`, `toDate`
- [ ] All analytics endpoints restricted to ADMIN role
- [ ] All analytics only count expenses within the current tenant
- [ ] Manager role gets a subset: `GET /api/v1/analytics/my-team` — same as by-category but scoped to their direct reports only

---

### S6.3 — Admin Dashboard: Frontend

**As an** Admin,
**I want** a visual dashboard with charts and metrics,
**So that** I can quickly understand org spending at a glance.

**Acceptance Criteria:**
- [ ] Dashboard page accessible only to Admin role
- [ ] Summary cards at top: Total Pending (count), Total Approved (amount), Total Rejected (count), Total This Month (amount)
- [ ] Bar chart: spend by category (current month by default)
- [ ] Line chart: monthly spend trend (last 6 months)
- [ ] Table: spend by team/manager with columns: Manager Name, Total Amount, Expense Count
- [ ] Date range picker to filter all charts/metrics
- [ ] Responsive layout — functional on tablet+
- [ ] Loading states for each widget (skeleton loaders)
- [ ] Use a chart library: Recharts or Chart.js (React-compatible)

---

## E7: API Rate Limiting

### S7.1 — Rate Limiting Middleware

**As a** system operator,
**I want** API rate limiting per tenant,
**So that** no single organization can overwhelm the system.

**Acceptance Criteria:**
- [ ] Rate limiting implemented as a Spring filter/interceptor, evaluated AFTER authentication (so tenant ID is available)
- [ ] Limits:
  - General API endpoints: 100 requests/minute per tenant
  - Auth endpoints (login, register, refresh): 20 requests/minute per IP
- [ ] Algorithm: Token bucket or sliding window counter
- [ ] Storage: In-memory (ConcurrentHashMap) for MVP — document that Redis would be used in production
- [ ] When rate limit exceeded: return 429 Too Many Requests with `Retry-After` header (seconds until bucket refills)
- [ ] Response body: `{ "error": "Rate limit exceeded", "retryAfter": 30 }`
- [ ] Rate limit headers on every response: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`

---

### S7.2 — Rate Limiting Tests

**As a** developer,
**I want** automated tests for rate limiting behavior,
**So that** I can verify limits are enforced correctly.

**Acceptance Criteria:**
- [ ] Test: Send 100 requests → all succeed. Send 101st → 429.
- [ ] Test: After waiting for bucket refill, requests succeed again.
- [ ] Test: Different tenants have independent limits — Org A hitting limit doesn't affect Org B.
- [ ] Test: Auth endpoints have their own stricter limit.

---

## E8: Frontend Shell & Navigation

### S8.1 — App Shell, Routing & Auth State

**As a** user,
**I want** a well-structured SPA with role-based navigation,
**So that** I see only the features relevant to my role.

**Acceptance Criteria:**
- [ ] React + TypeScript + Vite setup
- [ ] React Router for client-side routing
- [ ] Auth context: stores JWT, user info, handles token refresh transparently
- [ ] Protected route wrapper: redirects to login if not authenticated
- [ ] Role-based route guards: Admin routes inaccessible to Employee, etc.
- [ ] Sidebar/nav with role-appropriate links:
  - Employee: My Expenses, New Expense, Profile
  - Manager: My Expenses, New Expense, Pending Approvals, My Team Stats, Profile
  - Admin: Dashboard, Users, Categories, Profile
- [ ] Login and Registration pages (public routes)
- [ ] 404 page for unknown routes
- [ ] Global error boundary

---

### S8.2 — Employee: Expense Management UI

**As an** Employee,
**I want** to create, edit, and view my expenses through a clean UI,
**So that** I can manage my expense submissions easily.

**Acceptance Criteria:**
- [ ] **My Expenses page:** Paginated table with columns: Date, Category, Merchant, Amount, Status, Actions. Status shown as colored badge (Draft=gray, Submitted=blue, Approved=green, Rejected=red, Cancelled=dark gray)
- [ ] Filter bar: status dropdown, category dropdown, date range picker
- [ ] "New Expense" button → navigates to expense form
- [ ] **Expense form:** Fields for amount (number input), category (dropdown from API), merchant name (text), expense date (date picker), notes (textarea), receipt upload (drag-and-drop or file picker, shows previews)
- [ ] Form validation: inline errors, matches server-side rules
- [ ] Two submit buttons: "Save Draft" and "Submit for Approval"
- [ ] **Expense detail page:** Shows all fields, receipts (thumbnails for images, download link for PDFs), audit trail (timeline of status changes)
- [ ] Edit button visible only for DRAFT/REJECTED expenses
- [ ] Resubmit button visible for REJECTED expenses (with rejection comment prominently displayed)

---

### S8.3 — Manager: Approval Queue UI

**As a** Manager,
**I want** an approval queue with quick actions,
**So that** I can efficiently review and process my team's expenses.

**Acceptance Criteria:**
- [ ] **Pending Approvals page:** List/table of pending expenses from direct reports
- [ ] Each row: Submitter name, Date, Category, Amount, quick-action buttons (Approve, Reject)
- [ ] Click row → opens expense detail with receipt viewing
- [ ] Reject action opens a modal requiring a comment
- [ ] Approve action: optional comment, confirms immediately
- [ ] Bulk selection: checkboxes + "Approve Selected" / "Reject Selected" buttons
- [ ] After action, item removed from list with success toast
- [ ] Counter badge showing number of pending approvals

---

## Cross-Cutting Concerns (Apply to All Stories)

### Error Handling
- All API errors return consistent JSON: `{ "error": "Human-readable message", "code": "MACHINE_CODE", "details": {} }`
- HTTP status codes: 400 (validation), 401 (auth), 403 (forbidden), 404 (not found / tenant-isolated not found), 409 (conflict / invalid state transition), 429 (rate limit), 500 (unexpected)
- Frontend: toast notifications for errors, inline validation for forms

### API Design Conventions
- Base path: `/api/v1/`
- Resource naming: plural nouns (`/expenses`, `/users`, `/categories`)
- Pagination: `page` (0-indexed), `size` (default 20)
- Dates: ISO 8601 (`2026-03-18`)
- IDs: UUID v4
- All timestamps in UTC

### Security Checklist (Per Endpoint)
- [ ] JWT validated
- [ ] Tenant isolation enforced (query-level)
- [ ] Role authorization checked
- [ ] Resource ownership verified where applicable
- [ ] Input validated and sanitized

---

## Story Dependency Graph

```
E1 (Multi-Tenancy) ──┐
                      ├──▶ E4 (Expenses) ──▶ E5 (Approvals)
E2 (Auth) ───────────┤                           │
                      ├──▶ E3 (Users/Roles) ──────┘
E8 (Frontend Shell) ──┘                           │
                                                  ▼
                                        E6 (Dashboard/Analytics)

E7 (Rate Limiting) — independent, can be built anytime after E2
```

**Suggested Build Order:**
1. E1 (S1.1) + E2 (S2.1, S2.2, S2.3, S2.4) — Foundation: schema + auth
2. E3 (S3.1, S3.2) — User management
3. E4 (S4.1–S4.5) — Expense CRUD
4. E5 (S5.1–S5.4) — Approval workflow
5. E8 (S8.1–S8.3) — Frontend (can start in parallel with step 3–4)
6. E6 (S6.1–S6.3) — Analytics
7. E7 (S7.1–S7.2) — Rate limiting
8. E1 (S1.2, S1.3) + E3 (S3.3, S3.4) — Hardening & edge cases

---

*Total: 8 Epics, 28 Stories*
*Reference: PM_ANALYSIS_Problem1.md for gap analysis and decisions*
