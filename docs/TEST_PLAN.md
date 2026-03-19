# Test Plan: Multi-Tenant Expense Tracker with Approval Workflows

**Author:** QA Lead
**Date:** 2026-03-18
**Version:** 1.0
**Related Docs:** DESIGN.md, TECHNICAL_STORIES_Problem1.md, PM_ANALYSIS_Problem1.md

---

## Table of Contents

1. [Test Strategy Overview](#1-test-strategy-overview)
2. [Unit Test Cases (Per Module)](#2-unit-test-cases-per-module)
3. [Integration Test Scenarios](#3-integration-test-scenarios)
4. [Security Test Cases](#4-security-test-cases)
5. [Edge Cases & Negative Tests](#5-edge-cases--negative-tests)
6. [Performance/Load Test Considerations](#6-performanceload-test-considerations)
7. [Test Data Requirements](#7-test-data-requirements)
8. [Bug Report Template](#8-bug-report-template)

---

## 1. Test Strategy Overview

### 1.1 Testing Levels

| Level | Scope | Tool(s) | DB | Approximate Count |
|-------|-------|---------|----|--------------------|
| **Unit** | Single class/method in isolation | JUnit 5, Mockito | None (mocked) | ~180 tests |
| **Integration** | Cross-layer (controller-service-repository) | Spring Boot Test, MockMvc, Testcontainers (PostgreSQL) | Real PostgreSQL in Docker | ~80 tests |
| **End-to-End (E2E)** | Full API request-response chains | RestAssured or TestRestTemplate, Testcontainers | Real PostgreSQL in Docker | ~40 tests |
| **Frontend Unit** | React components in isolation | React Testing Library, Vitest | N/A | ~60 tests |
| **Frontend Integration** | Multi-component flows with mocked API | React Testing Library, MSW (Mock Service Worker) | N/A | ~20 tests |

### 1.2 Tools & Frameworks

| Tool | Purpose |
|------|---------|
| **JUnit 5** | Test framework: `@Test`, `@ParameterizedTest`, `@Nested`, lifecycle hooks |
| **Mockito** | Mocking dependencies in unit tests (`@Mock`, `@InjectMocks`, `when/verify`) |
| **Spring Boot Test** | `@SpringBootTest` for full context, `@WebMvcTest` for controller slice, `@DataJpaTest` for repository slice |
| **MockMvc** | Test REST controllers without starting the full server; validate HTTP status, headers, JSON body |
| **Testcontainers** | Spin up real PostgreSQL in Docker for integration tests; ensures Flyway migrations run against real DB |
| **React Testing Library** | Test React components from the user's perspective (query by role, label, text) |
| **Vitest** | Fast test runner for Vite-based frontend projects |
| **MSW (Mock Service Worker)** | Intercept HTTP requests in frontend integration tests to mock backend API responses |

### 1.3 Test Data Strategy

- **Unit tests:** Build entities in-memory using builder/factory methods. No database interaction.
- **Integration tests:** Each test class uses `@Transactional` with rollback, or `@Sql` scripts to load fixtures and `@DirtiesContext` where needed. Testcontainers provides a fresh PostgreSQL per test suite run.
- **Cross-tenant tests:** Always set up two organizations (Org A, Org B) with distinct users and data. Assert isolation by querying as one org and confirming zero visibility into the other.
- **Seed data convention:** A shared `TestFixtures` utility class provides factory methods: `createOrg()`, `createUser()`, `createExpense()`, `createCategory()`, etc., all accepting overrides for tenant and state.

### 1.4 Test Naming Convention

```
test_<MethodUnderTest>_<Scenario>_<ExpectedResult>
```

Examples:
- `test_login_validCredentials_returnsTokens`
- `test_submitExpense_noManagerAssigned_returns400`
- `test_listExpenses_asTenantB_returnsOnlyTenantBData`

### 1.5 Coverage Targets

| Area | Target |
|------|--------|
| Service layer (business logic) | >= 90% line coverage |
| Controller layer | >= 85% line coverage |
| Repository layer | Tested via integration tests (no separate unit coverage target) |
| Security filters | >= 90% branch coverage |
| State machine transitions | 100% of allowed + disallowed transitions |
| Multi-tenancy isolation | 100% of documented cross-tenant vectors |

---

## 2. Unit Test Cases (Per Module)

### 2.1 AuthService

| ID | Test Case | Preconditions | Steps | Expected Result |
|----|-----------|---------------|-------|-----------------|
| UT-AUTH-001 | Register with valid data | Organization "Acme" exists and is active | Call `register(email, password, firstName, lastName, orgId)` | User created with role EMPLOYEE, password hashed with bcrypt, JWT access token + refresh token returned |
| UT-AUTH-002 | Register with duplicate email | User "john@acme.com" already exists | Call `register("john@acme.com", ...)` | Throws `ConflictException` with code `EMAIL_EXISTS` |
| UT-AUTH-003 | Register with invalid organization ID | No org with given UUID | Call `register(email, pass, name, name, invalidOrgId)` | Throws `BadRequestException` — "Invalid or inactive organization" |
| UT-AUTH-004 | Register with inactive organization | Organization exists but `is_active = false` | Call `register(email, pass, name, name, inactiveOrgId)` | Throws `BadRequestException` — "Invalid or inactive organization" |
| UT-AUTH-005 | Register — password too short | None | Call `register(email, "Ab1", ...)` | Throws `ValidationException` — password minimum 8 characters |
| UT-AUTH-006 | Register — password no uppercase | None | Call `register(email, "abcdefg1", ...)` | Throws `ValidationException` — requires at least 1 uppercase |
| UT-AUTH-007 | Register — password no digit | None | Call `register(email, "Abcdefgh", ...)` | Throws `ValidationException` — requires at least 1 digit |
| UT-AUTH-008 | Login with valid credentials | User exists, is active, not locked | Call `login(email, password)` | Returns access token (15-min expiry), refresh token (7-day expiry), user DTO with id/email/name/role/orgId |
| UT-AUTH-009 | Login with wrong password | User exists | Call `login(email, wrongPassword)` | Throws `UnauthorizedException` — "Invalid email or password"; `failed_login_attempts` incremented by 1 |
| UT-AUTH-010 | Login with non-existent email | No user with that email | Call `login("nobody@acme.com", pass)` | Throws `UnauthorizedException` — "Invalid email or password" (same message, no email enumeration) |
| UT-AUTH-011 | Login with deactivated account | User exists, `is_active = false` | Call `login(email, password)` | Throws `UnauthorizedException` — "Account deactivated" |
| UT-AUTH-012 | Login triggers account lockout | User has 4 failed attempts | Call `login(email, wrongPassword)` | `failed_login_attempts` set to 5, `locked_until` set to NOW + 15 min; returns 429 with `Retry-After` header |
| UT-AUTH-013 | Login while account locked | `locked_until` is 10 minutes in the future | Call `login(email, correctPassword)` | Returns 429 with `Retry-After` header; does not validate password |
| UT-AUTH-014 | Login after lock expires | `locked_until` is in the past, `failed_login_attempts = 5` | Call `login(email, correctPassword)` | `failed_login_attempts` reset to 0, `locked_until` cleared; login succeeds |
| UT-AUTH-015 | Login resets failed attempts on success | `failed_login_attempts = 3` | Call `login(email, correctPassword)` | `failed_login_attempts` reset to 0 |
| UT-AUTH-016 | Refresh token — valid token | Refresh token exists in DB, not expired, not revoked | Call `refresh(refreshToken)` | Old refresh token revoked, new access + refresh tokens returned, `replaced_by_id` set on old token |
| UT-AUTH-017 | Refresh token — expired token | Refresh token expired (`expires_at` in the past) | Call `refresh(expiredToken)` | Throws `UnauthorizedException` — "Refresh token expired" |
| UT-AUTH-018 | Refresh token — revoked token (no reuse) | Token revoked but no family chain | Call `refresh(revokedToken)` | Throws `UnauthorizedException` |
| UT-AUTH-019 | Refresh token — reuse detected | Token already used (revoked + has `replaced_by_id`) | Call `refresh(reusedToken)` | ALL tokens in the family revoked; throws `UnauthorizedException` — forces re-login |
| UT-AUTH-020 | Refresh token — not found | Token hash not in DB | Call `refresh(unknownToken)` | Throws `UnauthorizedException` |
| UT-AUTH-021 | Logout — valid refresh token | Token exists and is active | Call `logout(refreshToken)` | Token's `is_revoked` set to true |
| UT-AUTH-022 | Logout — already revoked token | Token already revoked | Call `logout(revokedToken)` | No error; idempotent operation |

### 2.2 JwtTokenProvider

| ID | Test Case | Preconditions | Steps | Expected Result |
|----|-----------|---------------|-------|-----------------|
| UT-JWT-001 | Generate access token with correct claims | Valid user entity | Call `generateAccessToken(user)` | JWT contains `sub` (user ID), `tenantId` (org ID), `role`, `iat`, `exp` (15 min from now) |
| UT-JWT-002 | Validate a correctly signed token | Token generated by this provider | Call `validateToken(token)` | Returns true |
| UT-JWT-003 | Validate token with tampered payload | Token with modified claims but original signature | Call `validateToken(tamperedToken)` | Returns false (signature mismatch) |
| UT-JWT-004 | Validate expired token | Token with `exp` in the past | Call `validateToken(expiredToken)` | Returns false |
| UT-JWT-005 | Validate token signed with wrong key | Token signed with a different secret | Call `validateToken(wrongKeyToken)` | Returns false |
| UT-JWT-006 | Extract userId from token | Valid token | Call `getUserIdFromToken(token)` | Returns correct UUID from `sub` claim |
| UT-JWT-007 | Extract tenantId from token | Valid token | Call `getTenantIdFromToken(token)` | Returns correct tenant UUID |
| UT-JWT-008 | Extract role from token | Valid token | Call `getRoleFromToken(token)` | Returns correct role string |
| UT-JWT-009 | Validate malformed token string | Random non-JWT string | Call `validateToken("not-a-jwt")` | Returns false (does not throw) |
| UT-JWT-010 | Validate null/empty token | Null or empty string | Call `validateToken(null)` | Returns false |
| UT-JWT-011 | Token expiry is exactly 15 minutes | Valid user | Generate token, extract `exp` claim | `exp - iat` equals 900 seconds |

### 2.3 ExpenseService

| ID | Test Case | Preconditions | Steps | Expected Result |
|----|-----------|---------------|-------|-----------------|
| UT-EXP-001 | Create draft expense with all fields | Authenticated EMPLOYEE, category exists | Call `createExpense(amount, categoryId, merchant, date, notes)` | Expense created with status DRAFT, `submitter_id` = current user, `tenant_id` from context; audit log entry CREATED |
| UT-EXP-002 | Create draft expense with minimal fields | Authenticated EMPLOYEE | Call `createExpense(null, null, null, null, null)` | Expense created with status DRAFT; nullable fields are null |
| UT-EXP-003 | Create expense as MANAGER | Authenticated MANAGER | Call `createExpense(...)` | Expense created (Managers can submit expenses too) |
| UT-EXP-004 | Create expense as ADMIN | Authenticated ADMIN | Call `createExpense(...)` | Throws `ForbiddenException` — only EMPLOYEE and MANAGER can create |
| UT-EXP-005 | Update DRAFT expense — valid | Expense in DRAFT status, current user is submitter | Call `updateExpense(expenseId, newAmount, newCategory)` | Expense updated, `updated_at` changed |
| UT-EXP-006 | Update REJECTED expense — valid | Expense in REJECTED status, current user is submitter | Call `updateExpense(expenseId, newAmount)` | Expense updated successfully |
| UT-EXP-007 | Update SUBMITTED expense — rejected | Expense in SUBMITTED status | Call `updateExpense(expenseId, ...)` | Throws `ConflictException` — "Expense can only be edited in DRAFT or REJECTED status" |
| UT-EXP-008 | Update APPROVED expense — rejected | Expense in APPROVED status | Call `updateExpense(expenseId, ...)` | Throws `ConflictException` |
| UT-EXP-009 | Update expense by non-submitter | Different user than submitter | Call `updateExpense(expenseId, ...)` | Throws `ForbiddenException` — "Only the submitter can edit this expense" |
| UT-EXP-010 | Submit DRAFT expense — all fields valid | DRAFT expense with amount > 0, valid category, valid date, submitter has manager | Call `submitExpense(expenseId)` | Status changes to SUBMITTED; `manager_id` snapshot from submitter's current manager; audit log SUBMITTED |
| UT-EXP-011 | Submit expense — amount missing | DRAFT expense with `amount = null` | Call `submitExpense(expenseId)` | Throws `ValidationException` — amount required |
| UT-EXP-012 | Submit expense — amount zero | DRAFT expense with `amount = 0` | Call `submitExpense(expenseId)` | Throws `ValidationException` — amount must be > 0 |
| UT-EXP-013 | Submit expense — amount negative | DRAFT expense with `amount = -10` | Call `submitExpense(expenseId)` | Throws `ValidationException` — amount must be > 0 |
| UT-EXP-014 | Submit expense — category missing | DRAFT expense with `categoryId = null` | Call `submitExpense(expenseId)` | Throws `ValidationException` — categoryId required |
| UT-EXP-015 | Submit expense — category inactive | Category exists but `is_active = false` | Call `submitExpense(expenseId)` | Throws `ValidationException` — category must be active |
| UT-EXP-016 | Submit expense — category from different tenant | Category exists in Org B, expense in Org A | Call `submitExpense(expenseId)` | Throws `ValidationException` — invalid category |
| UT-EXP-017 | Submit expense — future date | `expense_date` is tomorrow | Call `submitExpense(expenseId)` | Throws `ValidationException` — date cannot be in the future |
| UT-EXP-018 | Submit expense — no manager assigned | Submitter's `manager_id = NULL` | Call `submitExpense(expenseId)` | Throws `BusinessRuleException` — "No manager assigned. Contact your administrator." |
| UT-EXP-019 | Submit expense — snapshot manager | Submitter has manager M1 | Call `submitExpense(expenseId)` | Expense's `manager_id` set to M1's user ID (snapshot) |
| UT-EXP-020 | Resubmit REJECTED expense | Expense in REJECTED status, all fields valid, submitter has manager | Call `submitExpense(expenseId)` | Status changes to SUBMITTED; audit log action = RESUBMITTED; `rejection_comment` cleared |
| UT-EXP-021 | Resubmit — manager changed since rejection | Submitter now has manager M2 (was M1) | Call `submitExpense(expenseId)` | Expense's `manager_id` re-snapshot to M2 |
| UT-EXP-022 | Submit from APPROVED status | Expense in APPROVED status | Call `submitExpense(expenseId)` | Throws `ConflictException` — invalid state transition |
| UT-EXP-023 | Submit from CANCELLED status | Expense in CANCELLED status | Call `submitExpense(expenseId)` | Throws `ConflictException` — invalid state transition |
| UT-EXP-024 | Delete DRAFT expense | Expense in DRAFT, current user is submitter | Call `deleteExpense(expenseId)` | Expense hard-deleted from DB; receipts deleted from filesystem |
| UT-EXP-025 | Delete SUBMITTED expense | Expense in SUBMITTED status | Call `deleteExpense(expenseId)` | Throws `ConflictException` — "Only DRAFT expenses can be deleted" |
| UT-EXP-026 | Delete expense by non-submitter | Different user | Call `deleteExpense(expenseId)` | Throws `ForbiddenException` |
| UT-EXP-027 | Get expense by ID — own expense | Expense belongs to current user | Call `getExpense(expenseId)` | Returns expense with receipts and audit trail |
| UT-EXP-028 | Get expense by ID — as assigned manager | Expense's `manager_id` = current user | Call `getExpense(expenseId)` | Returns expense |
| UT-EXP-029 | Get expense by ID — as admin in same tenant | Current user is ADMIN in same tenant | Call `getExpense(expenseId)` | Returns expense |
| UT-EXP-030 | Get expense by ID — unauthorized user | Different employee in same tenant, not manager | Call `getExpense(expenseId)` | Throws `ForbiddenException` or returns 404 |
| UT-EXP-031 | List own expenses — pagination | User has 25 expenses | Call `listExpenses(page=0, size=20)` | Returns first 20 expenses, `totalElements = 25`, `totalPages = 2` |
| UT-EXP-032 | List own expenses — filter by status | User has 5 DRAFT, 3 SUBMITTED | Call `listExpenses(status=DRAFT)` | Returns only 5 DRAFT expenses |
| UT-EXP-033 | List own expenses — filter by date range | Various expense dates | Call `listExpenses(fromDate="2026-03-01", toDate="2026-03-15")` | Returns only expenses within range |
| UT-EXP-034 | List own expenses — filter by category | Various categories | Call `listExpenses(categoryId=travelId)` | Returns only Travel expenses |
| UT-EXP-035 | List own expenses — default sort | Multiple expenses | Call `listExpenses()` | Returns expenses sorted by `created_at DESC` |
| UT-EXP-036 | Manager lists own expenses | Manager has own expenses AND team expenses | Call `listExpenses()` as Manager | Returns only manager's own expenses (not team's) |

### 2.4 ApprovalService

| ID | Test Case | Preconditions | Steps | Expected Result |
|----|-----------|---------------|-------|-----------------|
| UT-APR-001 | Approve expense — assigned manager | Expense SUBMITTED, `manager_id` = current user | Call `approveExpense(expenseId, comment?)` | Status -> APPROVED, `approved_by_id` = current user, `approved_at` = now; audit log APPROVED |
| UT-APR-002 | Approve expense — admin (fallback approver) | Expense SUBMITTED, current user is ADMIN in same tenant | Call `approveExpense(expenseId, comment)` | Status -> APPROVED, `approved_by_id` = admin |
| UT-APR-003 | Approve expense — wrong manager | Expense `manager_id` != current user, current user is MANAGER but not assigned | Call `approveExpense(expenseId)` | Throws `ForbiddenException` — "Not the assigned manager" |
| UT-APR-004 | Approve expense — EMPLOYEE role | Current user is EMPLOYEE | Call `approveExpense(expenseId)` | Throws `ForbiddenException` (role check at controller level via `@PreAuthorize`) |
| UT-APR-005 | Approve expense not in SUBMITTED status | Expense in DRAFT status | Call `approveExpense(expenseId)` | Throws `ConflictException` — "Expense is not in SUBMITTED status" |
| UT-APR-006 | Approve expense in APPROVED status | Expense already APPROVED | Call `approveExpense(expenseId)` | Throws `ConflictException` |
| UT-APR-007 | Reject expense — with comment | Expense SUBMITTED, assigned manager | Call `rejectExpense(expenseId, "Missing receipt")` | Status -> REJECTED, `rejection_comment` = "Missing receipt"; audit log REJECTED with comment |
| UT-APR-008 | Reject expense — empty comment | Expense SUBMITTED | Call `rejectExpense(expenseId, "")` | Throws `ValidationException` — comment required for rejection |
| UT-APR-009 | Reject expense — null comment | Expense SUBMITTED | Call `rejectExpense(expenseId, null)` | Throws `ValidationException` — comment required |
| UT-APR-010 | Reject expense — admin | Expense SUBMITTED, current user is ADMIN | Call `rejectExpense(expenseId, "Duplicate")` | Status -> REJECTED |
| UT-APR-011 | Reject expense — wrong manager | Not the assigned manager | Call `rejectExpense(expenseId, "reason")` | Throws `ForbiddenException` |
| UT-APR-012 | Reject expense not in SUBMITTED status | Expense in DRAFT | Call `rejectExpense(expenseId, "reason")` | Throws `ConflictException` |
| UT-APR-013 | List pending approvals — manager | 3 SUBMITTED expenses assigned to current manager | Call `listPendingApprovals(page, size)` | Returns 3 expenses sorted oldest-first (FIFO) |
| UT-APR-014 | List pending approvals — manager with filter | Various submitters | Call `listPendingApprovals(submitterId=X)` | Returns only expenses from submitter X |
| UT-APR-015 | List pending approvals — admin sees all | 5 SUBMITTED expenses in org, across different managers | Call `listPendingApprovals()` as ADMIN | Returns all 5 pending expenses |
| UT-APR-016 | Bulk approve — all valid | 3 SUBMITTED expenses assigned to current manager | Call `bulkAction(APPROVE, [id1, id2, id3], comment)` | All 3 approved; returns `processed: 3, skipped: 0` |
| UT-APR-017 | Bulk reject — all valid, comment provided | 3 SUBMITTED expenses | Call `bulkAction(REJECT, [id1, id2, id3], "Policy violation")` | All 3 rejected with comment |
| UT-APR-018 | Bulk reject — no comment | 3 SUBMITTED expenses | Call `bulkAction(REJECT, [id1, id2, id3], null)` | Throws `ValidationException` — comment required for REJECT |
| UT-APR-019 | Bulk approve — mixed valid/invalid | 2 SUBMITTED + 1 APPROVED expense | Call `bulkAction(APPROVE, [id1, id2, id3])` | `processed: 2, skipped: 1`; skipped entry includes reason "Not in SUBMITTED status" |
| UT-APR-020 | Bulk approve — expense from different manager | 1 expense assigned to another manager | Call `bulkAction(APPROVE, [othersExpenseId])` | Skipped with reason "Not assigned to you" |
| UT-APR-021 | Bulk approve — exceeds 50 limit | 51 expense IDs | Call `bulkAction(APPROVE, [51 ids])` | Throws `ValidationException` — max 50 per request |
| UT-APR-022 | Bulk approve — empty list | Empty array | Call `bulkAction(APPROVE, [])` | Throws `ValidationException` — at least 1 expense ID required |

### 2.5 UserService

| ID | Test Case | Preconditions | Steps | Expected Result |
|----|-----------|---------------|-------|-----------------|
| UT-USR-001 | List users in tenant | 10 users in Org A | Call `listUsers(page=0, size=20)` as Admin | Returns all 10 users with role, manager info, active status |
| UT-USR-002 | List users — filter by role | 5 EMPLOYEE, 3 MANAGER, 1 ADMIN | Call `listUsers(role=MANAGER)` | Returns 3 managers |
| UT-USR-003 | List users — search by name | User "John Doe" exists | Call `listUsers(search="John")` | Returns users matching substring |
| UT-USR-004 | List users — search by email | User "john@acme.com" exists | Call `listUsers(search="john@")` | Returns matching user |
| UT-USR-005 | Change role — EMPLOYEE to MANAGER | User is EMPLOYEE | Call `changeRole(userId, MANAGER)` | Role updated to MANAGER |
| UT-USR-006 | Change role — MANAGER to EMPLOYEE with no reports | Manager has 0 assigned employees | Call `changeRole(userId, EMPLOYEE)` | Role updated to EMPLOYEE |
| UT-USR-007 | Change role — MANAGER to EMPLOYEE with reports | Manager has 3 assigned employees | Call `changeRole(userId, EMPLOYEE)` | Throws `ConflictException` — "Reassign employees before changing this user's role" |
| UT-USR-008 | Assign manager — valid | Manager M1 exists, role=MANAGER, same tenant | Call `assignManager(userId, M1.id)` | User's `manager_id` set to M1; pending SUBMITTED expenses reassigned to M1 |
| UT-USR-009 | Assign manager — target is EMPLOYEE | Target user has role EMPLOYEE | Call `assignManager(userId, employeeId)` | Throws `BadRequestException` — manager must be MANAGER or ADMIN |
| UT-USR-010 | Assign manager — different tenant | Manager exists in Org B | Call `assignManager(userId, orgBManagerId)` as Org A admin | Throws `BadRequestException` — manager not found in tenant (or 404) |
| UT-USR-011 | Assign manager — reassigns pending expenses | Employee has 2 SUBMITTED expenses assigned to old manager M1 | Call `assignManager(userId, M2.id)` | Both SUBMITTED expenses' `manager_id` updated to M2; audit log entries: REASSIGNED |
| UT-USR-012 | Assign manager — does not reassign approved expenses | Employee has 1 APPROVED expense | Call `assignManager(userId, M2.id)` | APPROVED expense's `manager_id` unchanged (still M1); `approved_by_id` unchanged |
| UT-USR-013 | Deactivate user — employee with no SUBMITTED expenses | Employee, all expenses DRAFT or APPROVED | Call `deactivateUser(userId)` | `is_active = false`; all refresh tokens revoked |
| UT-USR-014 | Deactivate user — employee with SUBMITTED expenses | Employee has 2 SUBMITTED expenses | Call `deactivateUser(userId)` | `is_active = false`; SUBMITTED expenses moved to CANCELLED; refresh tokens revoked |
| UT-USR-015 | Deactivate user — manager with active reports | Manager has 3 employees assigned | Call `deactivateUser(managerId)` | Throws `ConflictException` — "Reassign employees before deactivating" |
| UT-USR-016 | Deactivate user — manager with no reports | Manager has 0 employees assigned | Call `deactivateUser(managerId)` | `is_active = false` |
| UT-USR-017 | Admin deactivates themselves | Admin user | Call `deactivateUser(ownUserId)` | Throws `ConflictException` — "Cannot deactivate yourself" |
| UT-USR-018 | Deactivated user cannot login | User deactivated | Call `login(email, password)` | Returns 401 — "Account deactivated" |

### 2.6 CategoryService

| ID | Test Case | Preconditions | Steps | Expected Result |
|----|-----------|---------------|-------|-----------------|
| UT-CAT-001 | List active categories | 6 categories (5 active, 1 inactive) in tenant | Call `listCategories()` | Returns 5 active categories |
| UT-CAT-002 | Create category — valid | Name "Training" does not exist in tenant | Call `createCategory("Training")` | Category created with `is_active = true`, returns 201 |
| UT-CAT-003 | Create category — duplicate name | "Travel" already exists in tenant | Call `createCategory("Travel")` | Throws `ConflictException` — category name already exists |
| UT-CAT-004 | Create category — duplicate name in different tenant | "Training" exists in Org B but not Org A | Call `createCategory("Training")` in Org A | Category created successfully (uniqueness is per-tenant) |
| UT-CAT-005 | Rename category — valid | Category "Travel" exists | Call `renameCategory(categoryId, "Business Travel")` | Name updated |
| UT-CAT-006 | Rename category — name collision | "Meals" already exists | Call `renameCategory(travelId, "Meals")` | Throws `ConflictException` |
| UT-CAT-007 | Delete (soft) category | Category "Equipment" exists | Call `deleteCategory(equipmentId)` | `is_active = false`; existing expenses retain the category |
| UT-CAT-008 | List categories — non-admin can read | Current user is EMPLOYEE | Call `listCategories()` | Returns active categories (read access for all authenticated users) |

### 2.7 AnalyticsService

| ID | Test Case | Preconditions | Steps | Expected Result |
|----|-----------|---------------|-------|-----------------|
| UT-ANA-001 | Summary — with data | 10 SUBMITTED, 75 APPROVED ($50,000), 15 REJECTED in tenant | Call `getSummary(fromDate, toDate)` | Returns `totalSubmitted=10, totalApproved=75, totalRejected=15, totalPending=10, totalApprovedAmount=50000.00` |
| UT-ANA-002 | Summary — no data in date range | No expenses in given date range | Call `getSummary(futureFrom, futureTo)` | Returns all zeroes: `totalSubmitted=0, totalApproved=0, totalRejected=0, totalPending=0, totalApprovedAmount=0.00` |
| UT-ANA-003 | By-category breakdown | Expenses in Travel ($12,500, 23), Meals ($3,200, 45) | Call `getByCategory(fromDate, toDate)` | Returns array with category name, totalAmount, expenseCount for each |
| UT-ANA-004 | By-category — only APPROVED expenses counted | Mix of SUBMITTED and APPROVED | Call `getByCategory(fromDate, toDate)` | Only APPROVED expenses included in totals |
| UT-ANA-005 | By-month trend | Expenses over 6 months | Call `getByMonth(months=6)` | Returns 6 entries, one per month, with totalAmount and count |
| UT-ANA-006 | By-month — max 12 months | Request 15 months | Call `getByMonth(months=15)` | Capped at 12 months |
| UT-ANA-007 | By-team breakdown | 3 managers with approved expenses | Call `getByTeam(fromDate, toDate)` | Returns 3 entries with managerName, totalAmount, expenseCount |
| UT-ANA-008 | My-team (manager view) | Manager has 3 employees with expenses | Call `getMyTeam(fromDate, toDate)` as Manager | Returns category breakdown for only this manager's direct reports |
| UT-ANA-009 | Analytics — tenant isolation | Org A has $100k in expenses, Org B has $200k | Call `getSummary()` as Org A admin | Returns only Org A totals |

### 2.8 FileStorageService

| ID | Test Case | Preconditions | Steps | Expected Result |
|----|-----------|---------------|-------|-----------------|
| UT-FILE-001 | Upload JPEG receipt | DRAFT expense, 0 existing receipts | Call `store(tenantId, expenseId, jpegFile)` | File saved to `uploads/{tenantId}/{expenseId}/{uuid}.jpg`; returns storage path |
| UT-FILE-002 | Upload PNG receipt | DRAFT expense | Call `store(tenantId, expenseId, pngFile)` | File saved with `.png` extension |
| UT-FILE-003 | Upload PDF receipt | DRAFT expense | Call `store(tenantId, expenseId, pdfFile)` | File saved with `.pdf` extension |
| UT-FILE-004 | Upload invalid content type | File is `text/plain` | Call `store(tenantId, expenseId, textFile)` | Throws `ValidationException` — invalid content type |
| UT-FILE-005 | Upload file exceeding 5MB | 6MB JPEG file | Call `store(tenantId, expenseId, largeFile)` | Throws `ValidationException` or 413 Payload Too Large |
| UT-FILE-006 | Upload 4th receipt | Expense already has 3 receipts | Call `store(...)` | Throws `ConflictException` — "Maximum 3 receipts per expense" |
| UT-FILE-007 | Upload to SUBMITTED expense | Expense in SUBMITTED status | Attempt upload | Throws `ConflictException` — "Cannot upload to expense in current status" |
| UT-FILE-008 | Upload to APPROVED expense | Expense in APPROVED status | Attempt upload | Throws `ConflictException` |
| UT-FILE-009 | Upload to REJECTED expense | Expense in REJECTED status | Upload | Success (REJECTED allows edits/uploads for resubmission) |
| UT-FILE-010 | Download receipt — valid access | User is submitter | Call `load(storagePath)` | Returns `Resource` with correct content type |
| UT-FILE-011 | Delete receipt | DRAFT expense, submitter | Call `delete(storagePath)` | File removed from filesystem; DB record deleted |
| UT-FILE-012 | Delete receipt from non-DRAFT expense | Expense in SUBMITTED status | Attempt delete | Throws `ConflictException` — "Can only delete receipts from DRAFT expenses" |
| UT-FILE-013 | Delete all for expense | Expense with 2 receipts | Call `deleteAllForExpense(tenantId, expenseId)` | Both files and directory removed |
| UT-FILE-014 | Upload by non-submitter | Different user from the expense submitter | Attempt upload | Throws `ForbiddenException` — "Only the submitter can upload receipts" |

### 2.9 RateLimiter (Token Bucket)

| ID | Test Case | Preconditions | Steps | Expected Result |
|----|-----------|---------------|-------|-----------------|
| UT-RL-001 | First request — bucket full | New bucket, capacity 100 | Call `tryConsume(tenantId)` | Returns true; remaining = 99 |
| UT-RL-002 | Request at capacity | Bucket has 1 token left | Call `tryConsume(tenantId)` | Returns true; remaining = 0 |
| UT-RL-003 | Request when empty | Bucket has 0 tokens | Call `tryConsume(tenantId)` | Returns false |
| UT-RL-004 | Bucket refills over time | Bucket drained to 0, 30 seconds elapse | Call `tryConsume(tenantId)` after 30s | Returns true (tokens refilled: ~50 tokens at 1.667/sec) |
| UT-RL-005 | Bucket does not exceed capacity | Bucket at 100, wait 5 minutes | Check token count | Still 100 (capped at capacity) |
| UT-RL-006 | Independent buckets per tenant | Org A drained, Org B fresh | `tryConsume(orgA)` then `tryConsume(orgB)` | Org A returns false, Org B returns true |
| UT-RL-007 | Auth endpoint — IP bucket | IP "1.2.3.4", limit 20/min | Send 20 requests, then 21st | First 20 succeed, 21st returns false |
| UT-RL-008 | Auth endpoint — different IPs | IP "1.2.3.4" at limit, "5.6.7.8" fresh | `tryConsume("5.6.7.8")` | Returns true — independent buckets |
| UT-RL-009 | Response headers | Bucket has 50 tokens remaining | Process request | Response includes `X-RateLimit-Limit: 100`, `X-RateLimit-Remaining: 49`, `X-RateLimit-Reset: <epoch>` |
| UT-RL-010 | 429 response format | Bucket empty | Process request | Returns 429, body: `{"error": "Rate limit exceeded", "retryAfter": N}`, header: `Retry-After: N` |

---

## 3. Integration Test Scenarios

### 3.1 Full Expense Lifecycle (Happy Path)

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| IT-LIFE-001 | Create, submit, approve | 1. EMPLOYEE creates expense (POST /expenses) -> DRAFT<br>2. Upload receipt (POST /expenses/{id}/receipts)<br>3. Submit (POST /expenses/{id}/submit) -> SUBMITTED<br>4. MANAGER views pending (GET /approvals/pending) -> sees expense<br>5. MANAGER approves (POST /expenses/{id}/approve) -> APPROVED<br>6. EMPLOYEE views expense -> status APPROVED, approvedBy, approvedAt set | All steps succeed; status transitions correct; audit trail has 3 entries (CREATED, SUBMITTED, APPROVED) |
| IT-LIFE-002 | Create, submit, reject, edit, resubmit, approve | 1. EMPLOYEE creates and submits expense<br>2. MANAGER rejects with comment "Missing receipt"<br>3. EMPLOYEE views expense -> status REJECTED, sees rejection comment<br>4. EMPLOYEE uploads receipt<br>5. EMPLOYEE edits amount<br>6. EMPLOYEE resubmits (POST /expenses/{id}/submit)<br>7. MANAGER approves | Final status APPROVED; audit trail has 5 entries (CREATED, SUBMITTED, REJECTED, RESUBMITTED, APPROVED); rejection_comment cleared after resubmit |
| IT-LIFE-003 | Create draft, edit multiple times, then submit | 1. Create empty draft<br>2. Update with amount<br>3. Update with category<br>4. Update with date<br>5. Submit | Each update succeeds for DRAFT; submit validates all fields present |
| IT-LIFE-004 | Delete draft expense with receipts | 1. Create draft<br>2. Upload 2 receipts<br>3. Delete expense (DELETE /expenses/{id}) | Expense deleted; receipt files removed from filesystem; DB records gone |

### 3.2 Multi-Tenant Data Isolation (CRITICAL)

| ID | Scenario | Setup | Steps | Expected Result |
|----|----------|-------|-------|-----------------|
| IT-ISO-001 | Employee in Org A cannot list Org B expenses | Create expenses in both Org A and Org B | GET /expenses as Org A employee | Returns only Org A expenses; zero Org B data |
| IT-ISO-002 | Employee in Org A cannot get Org B expense by ID | Expense E1 in Org B | GET /expenses/{E1.id} as Org A employee | 404 Not Found (not 403 — avoids leaking existence) |
| IT-ISO-003 | Manager in Org A cannot approve Org B expense | Expense E1 SUBMITTED in Org B | POST /expenses/{E1.id}/approve as Org A manager | 404 Not Found |
| IT-ISO-004 | Manager in Org A cannot reject Org B expense | Expense E1 SUBMITTED in Org B | POST /expenses/{E1.id}/reject as Org A manager | 404 Not Found |
| IT-ISO-005 | Admin dashboard shows only own org data | Both orgs have approved expenses | GET /analytics/summary as Org A admin | Totals reflect only Org A expenses |
| IT-ISO-006 | Analytics by-category — tenant isolation | Both orgs have "Travel" category with expenses | GET /analytics/by-category as Org A admin | Only Org A Travel amounts |
| IT-ISO-007 | Analytics by-team — tenant isolation | Both orgs have managers with expenses | GET /analytics/by-team as Org A admin | Only Org A managers listed |
| IT-ISO-008 | Analytics by-month — tenant isolation | Both orgs have expenses in March 2026 | GET /analytics/by-month as Org A admin | Only Org A amounts for each month |
| IT-ISO-009 | User list — tenant isolation | Users in both orgs | GET /users as Org A admin | Returns only Org A users |
| IT-ISO-010 | Category list — tenant isolation | Categories in both orgs | GET /categories as Org A user | Returns only Org A categories |
| IT-ISO-011 | Admin in Org A cannot manage Org B users | User U1 in Org B | PUT /users/{U1.id}/role as Org A admin | 404 Not Found |
| IT-ISO-012 | Org A manager cannot see Org B pending approvals | SUBMITTED expenses in Org B | GET /approvals/pending as Org A manager | Returns empty (no Org B expenses) |
| IT-ISO-013 | Receipt download — cross-tenant | Receipt R1 belongs to Org B expense | GET /expenses/{orgBExpenseId}/receipts/{R1.id} as Org A user | 404 Not Found |
| IT-ISO-014 | Assign manager from different tenant | Manager in Org B | PUT /users/{orgAUserId}/manager with Org B manager ID | 400 or 404 — manager not found in tenant |
| IT-ISO-015 | Bulk approve across tenants | Mix of Org A and Org B expense IDs | POST /approvals/bulk as Org A manager | Org B IDs skipped/not found; only Org A IDs processed |
| IT-ISO-016 | Category creation — name uniqueness per tenant | "Training" exists in Org A | POST /categories {"name":"Training"} as Org B admin | Created successfully (uniqueness is per-tenant) |

### 3.3 Authentication Flow

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| IT-AUTH-001 | Full registration to login flow | 1. POST /auth/register -> tokens + user<br>2. Use access token to GET /expenses<br>3. POST /auth/logout with refresh token<br>4. POST /auth/refresh with revoked refresh token | Step 2: 200; Step 3: 200; Step 4: 401 (token revoked) |
| IT-AUTH-002 | Token refresh cycle | 1. Login -> AT1 + RT1<br>2. Wait (or manually expire AT1)<br>3. POST /auth/refresh with RT1 -> AT2 + RT2<br>4. Use AT2 to access API<br>5. POST /auth/refresh with RT1 (reuse) | Step 3: success; Step 4: success; Step 5: 401 + all tokens in family revoked |
| IT-AUTH-003 | Account lockout and recovery | 1. Login with wrong password x5<br>2. Login with correct password -> 429 locked<br>3. Wait 15 minutes (or mock time)<br>4. Login with correct password | Step 1: first 4 return 401, 5th returns 429; Step 2: 429; Step 4: 200 success |
| IT-AUTH-004 | Deactivated user login | 1. Admin deactivates user<br>2. User tries to login | Step 2: 401 "Account deactivated" |
| IT-AUTH-005 | Concurrent sessions | 1. Login from Device A -> AT1, RT1<br>2. Login from Device B -> AT2, RT2<br>3. Use AT1 to access API<br>4. Use AT2 to access API | Both succeed (multiple concurrent sessions allowed) |

### 3.4 Rate Limiting Behavior

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| IT-RL-001 | Tenant rate limit enforcement | Send 100 requests as Org A user, then 101st | First 100: 200; 101st: 429 with `Retry-After` header |
| IT-RL-002 | Rate limit recovery | Send 100 requests (exhaust), wait for refill period, send 1 more | After wait: 200 success |
| IT-RL-003 | Independent tenant limits | Send 100 requests as Org A (exhaust), send 1 request as Org B | Org A: 429; Org B: 200 (independent buckets) |
| IT-RL-004 | Auth endpoint IP rate limit | Send 20 POST /auth/login from same IP, then 21st | First 20 succeed or fail with auth errors; 21st: 429 |
| IT-RL-005 | Rate limit headers present | Send any request | Response includes `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` headers |

### 3.5 File Upload/Download Security

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| IT-FILE-001 | Upload and download cycle | 1. Create draft<br>2. Upload JPEG receipt<br>3. Download receipt via GET endpoint | Download returns same file content with `Content-Type: image/jpeg` |
| IT-FILE-002 | Receipt accessible by assigned manager | 1. Employee uploads receipt to expense<br>2. Submit expense<br>3. Manager downloads receipt | Manager can download (200) |
| IT-FILE-003 | Receipt accessible by admin | Admin downloads receipt from any expense in org | 200 with file content |
| IT-FILE-004 | Receipt not accessible by other employee | Employee B tries to download Employee A's receipt | 403 Forbidden |
| IT-FILE-005 | Receipt not accessible cross-tenant | Org B user tries to download Org A receipt | 404 Not Found |

### 3.6 Manager Reassignment with Pending Expenses

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| IT-MGMT-001 | Reassign manager — pending expenses move | 1. Employee E has manager M1<br>2. E submits expense X (assigned to M1)<br>3. Admin reassigns E to manager M2 | Expense X's `manager_id` updated to M2; M2 sees X in pending approvals; M1 no longer sees X; audit log: REASSIGNED |
| IT-MGMT-002 | Reassign manager — approved expenses unchanged | 1. E submits expense Y, M1 approves<br>2. Admin reassigns E to M2 | Expense Y's `approved_by_id` still M1; `manager_id` still M1 (historical) |
| IT-MGMT-003 | Reassign manager — rejected expenses | 1. E submits expense Z, M1 rejects<br>2. Admin reassigns E to M2<br>3. E edits and resubmits Z | Resubmitted expense Z gets new `manager_id` = M2 (re-snapshot on submit) |

### 3.7 User Deactivation Cascading Effects

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| IT-DEACT-001 | Deactivate employee with SUBMITTED expenses | 1. Employee has 2 SUBMITTED expenses<br>2. Admin deactivates employee | SUBMITTED expenses -> CANCELLED; audit log entries with action CANCELLED; employee `is_active = false`; all refresh tokens revoked |
| IT-DEACT-002 | Deactivate employee — draft expenses unchanged | 1. Employee has 1 DRAFT expense<br>2. Admin deactivates employee | DRAFT expense remains DRAFT (not cancelled — it was never submitted) |
| IT-DEACT-003 | Deactivate manager with reports — blocked | 1. Manager has 3 employees<br>2. Admin tries to deactivate | 409 "Reassign employees before deactivating" |
| IT-DEACT-004 | Deactivate manager after reassigning reports | 1. Admin reassigns all 3 employees to M2<br>2. Admin deactivates original manager | Success: manager deactivated |
| IT-DEACT-005 | Deactivated user's existing tokens rejected | 1. Employee logged in (has valid AT + RT)<br>2. Admin deactivates employee<br>3. Employee uses AT to make request<br>4. Employee tries to refresh | Step 3: Should be rejected (user inactive check in JWT filter or service layer); Step 4: 401 (refresh tokens revoked) |

---

## 4. Security Test Cases

### 4.1 JWT Token Tampering

| ID | Test Case | Steps | Expected Result |
|----|-----------|-------|-----------------|
| SEC-JWT-001 | Tampered JWT payload | Take valid JWT, decode, change `tenantId`, re-encode without re-signing | 401 Unauthorized — signature verification fails |
| SEC-JWT-002 | Tampered JWT — role escalation | Change `role` from EMPLOYEE to ADMIN in payload | 401 Unauthorized — signature mismatch |
| SEC-JWT-003 | JWT signed with "none" algorithm | Create JWT with alg:none | 401 Unauthorized — algorithm not accepted |
| SEC-JWT-004 | Expired JWT | Use a JWT with `exp` in the past | 401 Unauthorized — "Token expired" |
| SEC-JWT-005 | Malformed Authorization header | Send `Authorization: Bear token` (typo) | 401 Unauthorized |
| SEC-JWT-006 | Missing Authorization header | Send request without Authorization header | 401 Unauthorized |
| SEC-JWT-007 | Empty Bearer token | Send `Authorization: Bearer ` (empty) | 401 Unauthorized |
| SEC-JWT-008 | JWT from different signing key | Generate JWT with a different HS256 secret | 401 Unauthorized |

### 4.2 Cross-Tenant Access Attempts (All Vectors)

| ID | Test Case | Attack Vector | Expected Result |
|----|-----------|---------------|-----------------|
| SEC-TEN-001 | Direct expense ID access | Org A user sends GET /expenses/{orgBExpenseId} | 404 (not 403) |
| SEC-TEN-002 | Approval of cross-tenant expense | Org A manager sends POST /expenses/{orgBExpenseId}/approve | 404 |
| SEC-TEN-003 | Rejection of cross-tenant expense | Org A manager sends POST /expenses/{orgBExpenseId}/reject | 404 |
| SEC-TEN-004 | Cross-tenant user management | Org A admin sends PUT /users/{orgBUserId}/role | 404 |
| SEC-TEN-005 | Cross-tenant manager assignment | Org A admin sends PUT /users/{orgAUserId}/manager with Org B managerId | 400 |
| SEC-TEN-006 | Cross-tenant receipt download | Org A user sends GET /expenses/{orgBExpenseId}/receipts/{id} | 404 |
| SEC-TEN-007 | Cross-tenant receipt upload | Org A user sends POST /expenses/{orgBExpenseId}/receipts | 404 |
| SEC-TEN-008 | Cross-tenant category access | Org A user submits expense with Org B categoryId | Validation error or 404 |
| SEC-TEN-009 | Cross-tenant analytics | Org A admin sends GET /analytics/summary | Returns only Org A data (zero Org B) |
| SEC-TEN-010 | Cross-tenant bulk approve | Org A manager includes Org B expense IDs in bulk | Org B IDs skipped/not found |
| SEC-TEN-011 | JWT tenantId manipulation | Modify tenantId claim in JWT | 401 — JWT signature invalid |
| SEC-TEN-012 | Tenant ID in request parameter | Send `?tenantId=orgB` on any endpoint | Ignored — tenant always derived from JWT, never from request |
| SEC-TEN-013 | Cross-tenant user deactivation | Org A admin sends PUT /users/{orgBUserId}/deactivate | 404 |
| SEC-TEN-014 | Cross-tenant category modification | Org A admin sends PUT /categories/{orgBCategoryId} | 404 |

### 4.3 RBAC Enforcement

| ID | Test Case | Endpoint | Role Tested | Expected Result |
|----|-----------|----------|-------------|-----------------|
| SEC-RBAC-001 | EMPLOYEE accesses admin user list | GET /users | EMPLOYEE | 403 Forbidden |
| SEC-RBAC-002 | EMPLOYEE accesses role change | PUT /users/{id}/role | EMPLOYEE | 403 Forbidden |
| SEC-RBAC-003 | EMPLOYEE accesses manager assignment | PUT /users/{id}/manager | EMPLOYEE | 403 Forbidden |
| SEC-RBAC-004 | EMPLOYEE accesses user deactivation | PUT /users/{id}/deactivate | EMPLOYEE | 403 Forbidden |
| SEC-RBAC-005 | EMPLOYEE accesses pending approvals | GET /approvals/pending | EMPLOYEE | 403 Forbidden |
| SEC-RBAC-006 | EMPLOYEE tries to approve expense | POST /expenses/{id}/approve | EMPLOYEE | 403 Forbidden |
| SEC-RBAC-007 | EMPLOYEE tries to reject expense | POST /expenses/{id}/reject | EMPLOYEE | 403 Forbidden |
| SEC-RBAC-008 | EMPLOYEE tries bulk action | POST /approvals/bulk | EMPLOYEE | 403 Forbidden |
| SEC-RBAC-009 | EMPLOYEE accesses admin analytics | GET /analytics/summary | EMPLOYEE | 403 Forbidden |
| SEC-RBAC-010 | EMPLOYEE accesses by-category analytics | GET /analytics/by-category | EMPLOYEE | 403 Forbidden |
| SEC-RBAC-011 | EMPLOYEE accesses by-month analytics | GET /analytics/by-month | EMPLOYEE | 403 Forbidden |
| SEC-RBAC-012 | EMPLOYEE accesses by-team analytics | GET /analytics/by-team | EMPLOYEE | 403 Forbidden |
| SEC-RBAC-013 | EMPLOYEE creates category | POST /categories | EMPLOYEE | 403 Forbidden |
| SEC-RBAC-014 | EMPLOYEE renames category | PUT /categories/{id} | EMPLOYEE | 403 Forbidden |
| SEC-RBAC-015 | EMPLOYEE deletes category | DELETE /categories/{id} | EMPLOYEE | 403 Forbidden |
| SEC-RBAC-016 | MANAGER accesses admin user list | GET /users | MANAGER | 403 Forbidden |
| SEC-RBAC-017 | MANAGER accesses role change | PUT /users/{id}/role | MANAGER | 403 Forbidden |
| SEC-RBAC-018 | MANAGER accesses admin analytics | GET /analytics/summary | MANAGER | 403 Forbidden |
| SEC-RBAC-019 | MANAGER accesses by-team analytics | GET /analytics/by-team | MANAGER | 403 Forbidden |
| SEC-RBAC-020 | MANAGER creates category | POST /categories | MANAGER | 403 Forbidden |
| SEC-RBAC-021 | MANAGER accesses my-team analytics | GET /analytics/my-team | MANAGER | 200 OK (allowed) |
| SEC-RBAC-022 | EMPLOYEE accesses my-team analytics | GET /analytics/my-team | EMPLOYEE | 403 Forbidden |
| SEC-RBAC-023 | ADMIN creates expense | POST /expenses | ADMIN | 403 Forbidden (only EMPLOYEE and MANAGER) |
| SEC-RBAC-024 | MANAGER creates expense | POST /expenses | MANAGER | 201 Created (allowed) |
| SEC-RBAC-025 | EMPLOYEE reads own expenses | GET /expenses | EMPLOYEE | 200 OK |
| SEC-RBAC-026 | MANAGER reads own expenses | GET /expenses | MANAGER | 200 OK (own only, not team) |
| SEC-RBAC-027 | ADMIN views pending approvals | GET /approvals/pending | ADMIN | 200 OK (all pending in org) |
| SEC-RBAC-028 | EMPLOYEE reads categories | GET /categories | EMPLOYEE | 200 OK (read allowed for all authenticated) |
| SEC-RBAC-029 | MANAGER accesses user deactivation | PUT /users/{id}/deactivate | MANAGER | 403 Forbidden |
| SEC-RBAC-030 | MANAGER accesses manager assignment | PUT /users/{id}/manager | MANAGER | 403 Forbidden |

### 4.4 File Security

| ID | Test Case | Steps | Expected Result |
|----|-----------|-------|-----------------|
| SEC-FILE-001 | Path traversal in receipt download | Craft request with `../../etc/passwd` as receiptId | 400 or 404 — path resolved against base dir only |
| SEC-FILE-002 | Direct filesystem access attempt | Guess file URL pattern (e.g., /uploads/tenantId/...) | 404 — no static file serving configured for uploads directory |
| SEC-FILE-003 | Receipt access by unrelated employee | Employee B tries to download Employee A's receipt (same org) | 403 Forbidden (only submitter, assigned manager, or admin) |
| SEC-FILE-004 | Receipt access by non-assigned manager | Manager M2 tries to download receipt for expense assigned to M1 | 403 Forbidden |
| SEC-FILE-005 | Unauthenticated receipt download | No Authorization header on receipt GET | 401 Unauthorized |
| SEC-FILE-006 | Upload with spoofed content type | Send .exe file with Content-Type: image/jpeg | Server validates actual file content/magic bytes OR rejects based on extension mismatch |
| SEC-FILE-007 | Upload receipt to other user's expense | Employee A uploads to Employee B's draft expense | 403 Forbidden — only submitter can upload |

### 4.5 Account Lockout

| ID | Test Case | Steps | Expected Result |
|----|-----------|-------|-----------------|
| SEC-LOCK-001 | Lockout after 5 failures | Send 5 login requests with wrong password | After 5th: 429, `locked_until` = NOW + 15 min, `Retry-After` header present |
| SEC-LOCK-002 | Locked account — correct password | Account locked, try correct password | 429 — still locked (password not checked while locked) |
| SEC-LOCK-003 | Lock expires | Account locked, wait for `locked_until` to pass | Login succeeds with correct password; `failed_login_attempts` reset to 0 |
| SEC-LOCK-004 | Failed counter resets on success | 3 failed attempts, then 1 success | After success: `failed_login_attempts` = 0 |
| SEC-LOCK-005 | Lockout per user, not global | User A locked, User B fresh | User B can login normally |

### 4.6 Refresh Token Reuse Detection

| ID | Test Case | Steps | Expected Result |
|----|-----------|-------|-----------------|
| SEC-RT-001 | Normal rotation | Login -> RT1; Refresh -> RT2; Refresh -> RT3 | Each step succeeds; old token revoked; new token issued |
| SEC-RT-002 | Reuse of revoked token (legitimate user refreshed first) | Login -> RT1; Refresh with RT1 -> RT2; Refresh with RT1 again | 2nd refresh with RT1: 401; ALL tokens in family (including RT2) revoked |
| SEC-RT-003 | Reuse of revoked token (attacker refreshed first) | Login -> RT1; Attacker refreshes RT1 -> RT_attack; User refreshes RT1 | User's refresh with RT1: 401 (reuse detected); RT_attack also revoked |
| SEC-RT-004 | Refresh token after logout | Login -> RT1; Logout (revokes RT1); Refresh with RT1 | 401 Unauthorized |
| SEC-RT-005 | Refresh with expired token | Token past `expires_at` (7 days) | 401 — "Refresh token expired" |

---

## 5. Edge Cases & Negative Tests

### 5.1 Concurrent Operations

| ID | Test Case | Setup | Steps | Expected Result |
|----|-----------|-------|-------|-----------------|
| EDGE-001 | Concurrent approval of same expense | Expense E1 in SUBMITTED status, two managers (assigned + admin) | Both send POST /expenses/{E1}/approve simultaneously | Exactly one succeeds (200 APPROVED); the other gets 409 "Expense is not in SUBMITTED status" (optimistic locking or database-level check) |
| EDGE-002 | Concurrent approve + reject of same expense | Expense E1 SUBMITTED | Manager sends approve, admin sends reject simultaneously | One succeeds, other gets 409 |
| EDGE-003 | Submit expense while being deactivated | Employee submits expense; admin deactivates concurrently | Either: submit succeeds then deactivation cancels it, OR deactivation goes first and submit fails for inactive user |

### 5.2 Missing Dependencies

| ID | Test Case | Setup | Steps | Expected Result |
|----|-----------|-------|-------|-----------------|
| EDGE-004 | Submit expense without manager assigned | Employee with `manager_id = NULL` | POST /expenses/{id}/submit | 400 — "No manager assigned. Contact your administrator." |
| EDGE-005 | Create expense when no categories exist | Org with all categories deactivated | POST /expenses (draft) -> attempt submit | Draft creation succeeds (category optional); submit fails validation (no valid categoryId) |
| EDGE-006 | Assign self as own manager | Admin tries to set own manager_id to own user ID | PUT /users/{ownId}/manager {"managerId": ownId} | 400 — circular reference or self-assignment not allowed |

### 5.3 Manager Lifecycle Edge Cases

| ID | Test Case | Setup | Steps | Expected Result |
|----|-----------|-------|-------|-----------------|
| EDGE-007 | Deactivate manager with pending reports | Manager has 3 employees, 5 SUBMITTED expenses assigned | PUT /users/{managerId}/deactivate | 409 — "Reassign employees before deactivating" |
| EDGE-008 | Demote manager with active employees | Manager has 2 employees | PUT /users/{managerId}/role {"role":"EMPLOYEE"} | 409 — "Reassign employees before changing this user's role" |
| EDGE-009 | Promote employee to manager | Employee with `manager_id` pointing to someone | PUT /users/{empId}/role {"role":"MANAGER"} | Role changed to MANAGER; employee can now be assigned as others' manager |
| EDGE-010 | Delete last admin in org | Org has 1 admin | Attempt to demote or deactivate | Should be blocked (business rule consideration) OR log warning |

### 5.4 Expense State Machine Edge Cases

| ID | Test Case | Setup | Steps | Expected Result |
|----|-----------|-------|-------|-----------------|
| EDGE-011 | Submit APPROVED expense | Expense in APPROVED status | POST /expenses/{id}/submit | 409 — invalid state transition |
| EDGE-012 | Submit CANCELLED expense | Expense in CANCELLED status | POST /expenses/{id}/submit | 409 — invalid state transition |
| EDGE-013 | Approve DRAFT expense | Expense in DRAFT status | POST /expenses/{id}/approve | 409 — "Expense is not in SUBMITTED status" |
| EDGE-014 | Approve REJECTED expense | Expense in REJECTED status | POST /expenses/{id}/approve | 409 — invalid state (must go through SUBMITTED first) |
| EDGE-015 | Reject APPROVED expense | Expense already APPROVED | POST /expenses/{id}/reject | 409 — cannot reverse approval |
| EDGE-016 | Double approve | Expense APPROVED | POST /expenses/{id}/approve again | 409 — already approved |
| EDGE-017 | Edit SUBMITTED expense | Expense in SUBMITTED status | PUT /expenses/{id} | 409 — "Can only be edited in DRAFT or REJECTED status" |
| EDGE-018 | Delete SUBMITTED expense | Expense in SUBMITTED status | DELETE /expenses/{id} | 409 — "Only DRAFT expenses can be deleted" |
| EDGE-019 | Delete APPROVED expense | Expense APPROVED | DELETE /expenses/{id} | 409 |
| EDGE-020 | Delete REJECTED expense | Expense REJECTED | DELETE /expenses/{id} | 409 — only DRAFT can be deleted |

### 5.5 Receipt Edge Cases

| ID | Test Case | Setup | Steps | Expected Result |
|----|-----------|-------|-------|-----------------|
| EDGE-021 | Upload 4th receipt (over limit) | Expense has 3 receipts | POST /expenses/{id}/receipts | 409 — "Maximum 3 receipts per expense" |
| EDGE-022 | Upload oversized file (6MB) | DRAFT expense | Upload 6MB JPEG | 413 Payload Too Large (or 400) |
| EDGE-023 | Upload wrong content type (text/plain) | DRAFT expense | Upload .txt file | 400 — "Invalid file type. Allowed: image/jpeg, image/png, application/pdf" |
| EDGE-024 | Upload wrong content type (application/zip) | DRAFT expense | Upload .zip file | 400 — invalid content type |
| EDGE-025 | Upload to SUBMITTED expense | Expense SUBMITTED | POST /expenses/{id}/receipts | 409 — "Cannot upload to expense in current status" |
| EDGE-026 | Upload to APPROVED expense | Expense APPROVED | POST /expenses/{id}/receipts | 409 |
| EDGE-027 | Delete receipt from SUBMITTED expense | Expense SUBMITTED, has 1 receipt | DELETE /expenses/{id}/receipts/{receiptId} | 409 — "Can only delete receipts from DRAFT expenses" |
| EDGE-028 | Delete receipt from REJECTED expense | Expense REJECTED, has 1 receipt | DELETE /expenses/{id}/receipts/{receiptId} | Depends on design: may allow (since REJECTED is editable) or restrict to DRAFT only |
| EDGE-029 | Upload empty file (0 bytes) | DRAFT expense | Upload empty file | 400 — file cannot be empty |
| EDGE-030 | Upload file with exactly 5MB | DRAFT expense | Upload 5,242,880 byte file | 201 — succeeds (boundary value: <= 5MB) |
| EDGE-031 | Upload file at 5MB + 1 byte | DRAFT expense | Upload 5,242,881 byte file | 413 — exceeds limit |
| EDGE-032 | Delete draft expense with receipts | DRAFT with 2 receipts | DELETE /expenses/{id} | Expense deleted; both receipt files removed from filesystem |

### 5.6 Bulk Operation Edge Cases

| ID | Test Case | Setup | Steps | Expected Result |
|----|-----------|-------|-------|-----------------|
| EDGE-033 | Bulk approve with mixed valid/invalid IDs | 2 SUBMITTED (assigned), 1 APPROVED, 1 non-existent ID | POST /approvals/bulk | `processed: 2, skipped: 2`; errors detail reason for each skip |
| EDGE-034 | Bulk approve with 0 IDs | Empty array | POST /approvals/bulk {"action":"APPROVE","expenseIds":[]} | 400 — at least 1 ID required |
| EDGE-035 | Bulk approve with 51 IDs | 51 valid UUIDs | POST /approvals/bulk | 400 — max 50 per request |
| EDGE-036 | Bulk reject without comment | 3 SUBMITTED expenses | POST /approvals/bulk {"action":"REJECT","expenseIds":[...]} (no comment) | 400 — comment required for REJECT |
| EDGE-037 | Bulk approve all from wrong manager | 3 SUBMITTED, assigned to different manager | POST /approvals/bulk as non-assigned manager | `processed: 0, skipped: 3`; reason: "Not assigned to you" |
| EDGE-038 | Bulk approve with duplicate IDs | Same ID repeated 3 times | POST /approvals/bulk {"expenseIds":["id1","id1","id1"]} | First processes, duplicates skipped ("Already processed") or deduplicated |

### 5.7 Analytics Edge Cases

| ID | Test Case | Setup | Steps | Expected Result |
|----|-----------|-------|-------|-----------------|
| EDGE-039 | Analytics with no data | New org, no expenses | GET /analytics/summary | Returns all zeros: `totalSubmitted: 0, totalApproved: 0, ...` |
| EDGE-040 | Analytics with no approved expenses | All expenses SUBMITTED (none approved) | GET /analytics/by-category | Returns empty array (by-category counts only APPROVED) |
| EDGE-041 | Analytics — future date range | fromDate and toDate in the future | GET /analytics/summary?fromDate=2027-01-01&toDate=2027-12-31 | Returns all zeros (no expenses exist in future) |
| EDGE-042 | Analytics — inverted date range | fromDate > toDate | GET /analytics/summary?fromDate=2026-03-31&toDate=2026-03-01 | 400 — invalid date range, OR returns empty result |
| EDGE-043 | By-month with months=0 | Request 0 months | GET /analytics/by-month?months=0 | 400 — months must be >= 1, OR returns empty array |
| EDGE-044 | By-month with months=13 | Request 13 months (exceeds max 12) | GET /analytics/by-month?months=13 | Capped at 12 months |

### 5.8 Pagination Edge Cases

| ID | Test Case | Setup | Steps | Expected Result |
|----|-----------|-------|-------|-----------------|
| EDGE-045 | Empty page — no results | No expenses for this user | GET /expenses?page=0&size=20 | `content: [], totalElements: 0, totalPages: 0, page: 0` |
| EDGE-046 | Page beyond last page | 15 expenses, page=5, size=20 | GET /expenses?page=5&size=20 | `content: [], totalElements: 15, totalPages: 1, page: 5` (empty page) |
| EDGE-047 | Last page with partial results | 25 expenses, page=1, size=20 | GET /expenses?page=1&size=20 | `content: [5 items], totalElements: 25, totalPages: 2, page: 1` |
| EDGE-048 | Size exceeds max (100) | Request size=200 | GET /expenses?size=200 | Capped at 100, or 400 error |
| EDGE-049 | Negative page number | page=-1 | GET /expenses?page=-1 | 400 — invalid page number |
| EDGE-050 | Size = 0 | size=0 | GET /expenses?size=0 | 400 — size must be >= 1 |
| EDGE-051 | Size = 1 | 10 expenses, size=1 | GET /expenses?size=1 | `totalPages: 10`, each page has 1 item |

### 5.9 Input Validation Edge Cases

| ID | Test Case | Steps | Expected Result |
|----|-----------|-------|-----------------|
| EDGE-052 | Expense amount = DECIMAL overflow | Amount = 99999999999.99 (exceeds DECIMAL(12,2)) | 400 — amount out of range |
| EDGE-053 | Expense amount with > 2 decimal places | Amount = 45.999 | 400 — max 2 decimal places, OR rounded |
| EDGE-054 | Extremely long notes field | Notes with 100,000 characters | 400 — exceeds max length, OR truncated |
| EDGE-055 | Category name — empty string | POST /categories {"name": ""} | 400 — name must not be blank |
| EDGE-056 | Category name — whitespace only | POST /categories {"name": "   "} | 400 — name must not be blank (after trimming) |
| EDGE-057 | Non-UUID ID in path | GET /expenses/not-a-uuid | 400 — invalid path parameter format |
| EDGE-058 | SQL injection in search | GET /users?search='; DROP TABLE users;-- | Returns empty or valid results; no SQL injection (parameterized queries) |
| EDGE-059 | XSS in notes field | Create expense with notes: `<script>alert('xss')</script>` | Stored as text; JSON output escapes HTML entities; not executed in browser |
| EDGE-060 | Registration with malformed email | POST /auth/register with email: "notanemail" | 400 — invalid email format |

---

## 6. Performance/Load Test Considerations

### 6.1 Rate Limiting Under Concurrent Load

| ID | Scenario | Tool | Setup | Acceptance Criteria |
|----|----------|------|-------|---------------------|
| PERF-001 | Tenant rate limit with concurrent clients | JMeter / Gatling | 10 concurrent threads sending requests for Org A, target 100 req/min limit | Exactly 100 requests succeed per minute; subsequent requests get 429; no race conditions in token bucket |
| PERF-002 | Multi-tenant concurrent load | JMeter / Gatling | 5 tenants, each sending 80 req/min (under limit) simultaneously | All tenants get 200 responses; no cross-tenant interference; buckets remain independent |
| PERF-003 | Auth endpoint rate limit — brute force simulation | JMeter / Gatling | 50 concurrent threads hitting POST /auth/login from same IP | First 20 get 401 (wrong password) or 200; remaining get 429; lockout triggers at 5 failures per user |

### 6.2 Analytics Query Performance

| ID | Scenario | Setup | Acceptance Criteria |
|----|----------|-------|---------------------|
| PERF-004 | Summary analytics with 100k expenses | Seed Org A with 100,000 APPROVED expenses | GET /analytics/summary responds in < 500ms |
| PERF-005 | By-category with 100k expenses | 100,000 expenses across 10 categories | GET /analytics/by-category responds in < 500ms |
| PERF-006 | By-month trend with 100k expenses | 100,000 expenses over 12 months | GET /analytics/by-month responds in < 500ms |
| PERF-007 | By-team with 50 managers | 100,000 expenses across 50 managers | GET /analytics/by-team responds in < 1s |
| PERF-008 | Expense listing with 10k per user | User with 10,000 expenses | GET /expenses (paginated, default size 20) responds in < 200ms |

### 6.3 File Upload Performance

| ID | Scenario | Setup | Acceptance Criteria |
|----|----------|-------|---------------------|
| PERF-009 | Upload max-size file (5MB) | 5MB JPEG file | POST /expenses/{id}/receipts completes in < 3s |
| PERF-010 | Concurrent uploads (10 users) | 10 users uploading 5MB files simultaneously | All uploads succeed within 5s; no file corruption |
| PERF-011 | Download receipt under load | 50 concurrent receipt download requests | All return 200 within 1s; no file locking issues |

### 6.4 Database Connection Pool

| ID | Scenario | Setup | Acceptance Criteria |
|----|----------|-------|---------------------|
| PERF-012 | Connection pool under sustained load | 100 concurrent API requests over 5 minutes | No connection pool exhaustion; no 500 errors from DB timeouts |
| PERF-013 | Long-running analytics query | Analytics query on 1M rows while other requests stream | Other requests not blocked; connection pool handles concurrent read load |

---

## 7. Test Data Requirements

### 7.1 Core Entities

| Entity | Fixture Name | Fields | Notes |
|--------|-------------|--------|-------|
| **Organization (Org A)** | `ORG_ACME` | id: UUID, name: "Acme Corp", slug: "acme-corp", currency: "USD", is_active: true | Primary test tenant |
| **Organization (Org B)** | `ORG_GLOBEX` | id: UUID, name: "Globex Inc", slug: "globex-inc", currency: "EUR", is_active: true | Secondary tenant for isolation tests |
| **Organization (Inactive)** | `ORG_DEFUNCT` | id: UUID, name: "Defunct LLC", slug: "defunct-llc", is_active: false | For testing registration against inactive org |

### 7.2 Users Per Organization

**Org A (Acme Corp):**

| Fixture Name | Email | Role | Manager | Active | Notes |
|-------------|-------|------|---------|--------|-------|
| `ACME_ADMIN` | admin@acme.com | ADMIN | null | true | Primary admin for Org A |
| `ACME_MANAGER_1` | manager1@acme.com | MANAGER | ACME_ADMIN | true | Manager with employees assigned |
| `ACME_MANAGER_2` | manager2@acme.com | MANAGER | ACME_ADMIN | true | Secondary manager for reassignment tests |
| `ACME_EMP_1` | emp1@acme.com | EMPLOYEE | ACME_MANAGER_1 | true | Standard employee |
| `ACME_EMP_2` | emp2@acme.com | EMPLOYEE | ACME_MANAGER_1 | true | Second employee under same manager |
| `ACME_EMP_3` | emp3@acme.com | EMPLOYEE | ACME_MANAGER_2 | true | Employee under different manager |
| `ACME_EMP_NO_MGR` | empnomgr@acme.com | EMPLOYEE | null | true | Employee with no manager assigned |
| `ACME_DEACTIVATED` | deactivated@acme.com | EMPLOYEE | ACME_MANAGER_1 | false | Deactivated user |

**Org B (Globex Inc):**

| Fixture Name | Email | Role | Manager | Active |
|-------------|-------|------|---------|--------|
| `GLOBEX_ADMIN` | admin@globex.com | ADMIN | null | true |
| `GLOBEX_MANAGER` | manager@globex.com | MANAGER | GLOBEX_ADMIN | true |
| `GLOBEX_EMP_1` | emp1@globex.com | EMPLOYEE | GLOBEX_MANAGER | true |

### 7.3 Categories Per Organization

**Org A (Acme Corp):**

| Fixture Name | Name | Active |
|-------------|------|--------|
| `ACME_CAT_TRAVEL` | Travel | true |
| `ACME_CAT_MEALS` | Meals | true |
| `ACME_CAT_OFFICE` | Office Supplies | true |
| `ACME_CAT_SOFTWARE` | Software | true |
| `ACME_CAT_EQUIPMENT` | Equipment | true |
| `ACME_CAT_OTHER` | Other | true |
| `ACME_CAT_INACTIVE` | Deprecated Category | false |

**Org B (Globex Inc):**

| Fixture Name | Name | Active |
|-------------|------|--------|
| `GLOBEX_CAT_TRAVEL` | Travel | true |
| `GLOBEX_CAT_MEALS` | Meals | true |

### 7.4 Expenses (Pre-seeded for Integration Tests)

| Fixture Name | Tenant | Submitter | Status | Amount | Category | Manager |
|-------------|--------|-----------|--------|--------|----------|---------|
| `EXP_DRAFT_1` | Acme | ACME_EMP_1 | DRAFT | 45.99 | Meals | null (not yet submitted) |
| `EXP_DRAFT_EMPTY` | Acme | ACME_EMP_1 | DRAFT | null | null | null |
| `EXP_SUBMITTED_1` | Acme | ACME_EMP_1 | SUBMITTED | 250.00 | Travel | ACME_MANAGER_1 |
| `EXP_SUBMITTED_2` | Acme | ACME_EMP_2 | SUBMITTED | 120.00 | Software | ACME_MANAGER_1 |
| `EXP_APPROVED_1` | Acme | ACME_EMP_1 | APPROVED | 75.50 | Meals | ACME_MANAGER_1 |
| `EXP_REJECTED_1` | Acme | ACME_EMP_1 | REJECTED | 500.00 | Equipment | ACME_MANAGER_1 |
| `EXP_CANCELLED_1` | Acme | ACME_DEACTIVATED | CANCELLED | 30.00 | Other | ACME_MANAGER_1 |
| `GLOBEX_EXP_SUB` | Globex | GLOBEX_EMP_1 | SUBMITTED | 100.00 | Travel | GLOBEX_MANAGER |
| `GLOBEX_EXP_APR` | Globex | GLOBEX_EMP_1 | APPROVED | 200.00 | Meals | GLOBEX_MANAGER |

### 7.5 Receipts (Pre-seeded)

| Fixture Name | Expense | File Name | Content Type | Size |
|-------------|---------|-----------|-------------|------|
| `RECEIPT_1` | EXP_DRAFT_1 | receipt1.jpg | image/jpeg | 245,000 bytes |
| `RECEIPT_2` | EXP_SUBMITTED_1 | receipt2.png | image/png | 180,000 bytes |
| `RECEIPT_3` | EXP_SUBMITTED_1 | receipt3.pdf | application/pdf | 500,000 bytes |

### 7.6 Test Files for Upload Tests

| File Name | Type | Size | Purpose |
|-----------|------|------|---------|
| `test-receipt.jpg` | image/jpeg | 100 KB | Valid small JPEG |
| `test-receipt.png` | image/png | 200 KB | Valid small PNG |
| `test-receipt.pdf` | application/pdf | 500 KB | Valid small PDF |
| `test-large.jpg` | image/jpeg | 6 MB | Exceeds 5MB limit |
| `test-exact-5mb.jpg` | image/jpeg | 5,242,880 bytes | Boundary: exactly 5MB |
| `test-over-5mb.jpg` | image/jpeg | 5,242,881 bytes | Boundary: 5MB + 1 byte |
| `test-invalid.txt` | text/plain | 1 KB | Invalid content type |
| `test-invalid.exe` | application/octet-stream | 1 KB | Invalid content type |
| `test-empty.jpg` | image/jpeg | 0 bytes | Empty file |

### 7.7 TestFixtures Utility Class (Proposed API)

```java
public class TestFixtures {

    // Organizations
    public static Organization createOrg(String name, String slug, String currency);
    public static Organization createActiveOrg();    // "Acme Corp"
    public static Organization createSecondOrg();    // "Globex Inc"
    public static Organization createInactiveOrg();  // is_active = false

    // Users
    public static User createEmployee(Organization org, User manager);
    public static User createManager(Organization org);
    public static User createAdmin(Organization org);
    public static User createEmployeeWithoutManager(Organization org);
    public static User createDeactivatedUser(Organization org);

    // Expenses
    public static Expense createDraftExpense(User submitter, Category category);
    public static Expense createSubmittedExpense(User submitter, User manager, Category category);
    public static Expense createApprovedExpense(User submitter, User manager, User approver, Category category);
    public static Expense createRejectedExpense(User submitter, User manager, Category category, String comment);

    // Categories
    public static Category createCategory(Organization org, String name);
    public static List<Category> createDefaultCategories(Organization org);

    // Receipts
    public static MockMultipartFile createValidJpeg();
    public static MockMultipartFile createValidPng();
    public static MockMultipartFile createValidPdf();
    public static MockMultipartFile createOversizedFile();
    public static MockMultipartFile createInvalidTypeFile();

    // Auth
    public static String generateValidJwt(User user);
    public static String generateExpiredJwt(User user);
    public static String generateJwtWithTamperedTenantId(User user, UUID fakeTenantId);
}
```

---

## 8. Bug Report Template

### Bug Report Format

```
+=====================================================+
| BUG REPORT                                          |
+=====================================================+

BUG ID:        BUG-XXXX
TITLE:         [Short, descriptive title]

SEVERITY:      [ ] P0 - Critical (system down, data loss, security breach)
               [ ] P1 - High (major feature broken, no workaround)
               [ ] P2 - Medium (feature partially broken, workaround exists)
               [ ] P3 - Low (cosmetic, minor inconvenience)

PRIORITY:      [ ] Immediate  [ ] Next Sprint  [ ] Backlog

COMPONENT:     [AuthService | ExpenseService | ApprovalService |
                UserService | CategoryService | AnalyticsService |
                FileStorageService | RateLimiter | JwtTokenProvider |
                Frontend | Database | Infrastructure]

TEST CASE REF: [ID from this test plan, e.g., UT-AUTH-012, IT-ISO-003]

+-----------------------------------------------------+
| ENVIRONMENT                                         |
+-----------------------------------------------------+
| Branch:          [feature/xyz or main]               |
| Commit:          [short SHA]                         |
| Java Version:    [17/21]                             |
| Spring Boot:     [3.x.x]                            |
| PostgreSQL:      [15.x]                              |
| OS:              [macOS/Linux/Docker]                 |
+-----------------------------------------------------+

+-----------------------------------------------------+
| DESCRIPTION                                         |
+-----------------------------------------------------+
[1-2 sentence summary of the defect]

+-----------------------------------------------------+
| PRECONDITIONS                                       |
+-----------------------------------------------------+
1. [State of the system before the bug occurs]
2. [User role and tenant context]
3. [Any required data setup]

+-----------------------------------------------------+
| STEPS TO REPRODUCE                                  |
+-----------------------------------------------------+
1. [First step]
2. [Second step]
3. [Continue until bug manifests]

+-----------------------------------------------------+
| EXPECTED RESULT                                     |
+-----------------------------------------------------+
[What should happen according to the spec]

+-----------------------------------------------------+
| ACTUAL RESULT                                       |
+-----------------------------------------------------+
[What actually happens]

+-----------------------------------------------------+
| EVIDENCE                                            |
+-----------------------------------------------------+
- HTTP Request:  [curl command or request details]
- HTTP Response: [status code + response body]
- Logs:          [relevant log snippet]
- Screenshot:    [if applicable, for frontend bugs]

+-----------------------------------------------------+
| IMPACT ANALYSIS                                     |
+-----------------------------------------------------+
- Affected roles:     [EMPLOYEE / MANAGER / ADMIN / ALL]
- Affected tenants:   [Single / All]
- Data integrity:     [Yes / No - is data corrupted?]
- Security impact:    [Yes / No - is data exposed?]
- Workaround:         [Describe or "None"]

+-----------------------------------------------------+
| NOTES                                               |
+-----------------------------------------------------+
[Any additional context, related bugs, or hypotheses
 about root cause]
+=====================================================+
```

### Example Bug Report

```
+=====================================================+
| BUG REPORT                                          |
+=====================================================+

BUG ID:        BUG-0042
TITLE:         Cross-tenant expense visible when queried by ID

SEVERITY:      [X] P0 - Critical (security breach)
PRIORITY:      [X] Immediate

COMPONENT:     ExpenseService
TEST CASE REF: IT-ISO-002, SEC-TEN-001

+-----------------------------------------------------+
| ENVIRONMENT                                         |
+-----------------------------------------------------+
| Branch:          feature/expense-crud                |
| Commit:          a1b2c3d                             |
| Java Version:    17                                  |
| Spring Boot:     3.2.4                               |
| PostgreSQL:      15.6 (Testcontainers)               |
+-----------------------------------------------------+

+-----------------------------------------------------+
| DESCRIPTION                                         |
+-----------------------------------------------------+
A user in Organization A can retrieve an expense belonging
to Organization B by directly specifying the expense UUID
in GET /api/v1/expenses/{id}. The repository query is
missing the tenant_id filter.

+-----------------------------------------------------+
| PRECONDITIONS                                       |
+-----------------------------------------------------+
1. Two organizations exist: Org A and Org B
2. Expense E1 belongs to Org B
3. User U1 is an employee in Org A with a valid JWT

+-----------------------------------------------------+
| STEPS TO REPRODUCE                                  |
+-----------------------------------------------------+
1. Login as U1 (Org A employee) -> obtain access token
2. GET /api/v1/expenses/{E1.id}
   Header: Authorization: Bearer <U1's token>

+-----------------------------------------------------+
| EXPECTED RESULT                                     |
+-----------------------------------------------------+
404 Not Found (expense not visible to Org A user)

+-----------------------------------------------------+
| ACTUAL RESULT                                       |
+-----------------------------------------------------+
200 OK with full expense details from Org B returned

+-----------------------------------------------------+
| EVIDENCE                                            |
+-----------------------------------------------------+
- HTTP Request:
  curl -H "Authorization: Bearer eyJ..." \
    http://localhost:8080/api/v1/expenses/b2c3d4e5-...
- HTTP Response: 200
  {"id":"b2c3d4e5-...","tenantId":"org-b-uuid",...}

+-----------------------------------------------------+
| IMPACT ANALYSIS                                     |
+-----------------------------------------------------+
- Affected roles:     ALL
- Affected tenants:   All (any tenant can see any other)
- Data integrity:     No corruption, but data exposure
- Security impact:    YES - CRITICAL tenant isolation breach
- Workaround:         None

+-----------------------------------------------------+
| NOTES                                               |
+-----------------------------------------------------+
Root cause: ExpenseRepository.findById() uses JPA
default findById which does not include tenant_id.
Fix: Use custom @Query with tenant_id filter, or
add Hibernate @Filter.
+=====================================================+
```

---

## Appendix A: Test Case Summary Counts

| Section | Count |
|---------|-------|
| Unit Tests — AuthService | 22 |
| Unit Tests — JwtTokenProvider | 11 |
| Unit Tests — ExpenseService | 36 |
| Unit Tests — ApprovalService | 22 |
| Unit Tests — UserService | 18 |
| Unit Tests — CategoryService | 8 |
| Unit Tests — AnalyticsService | 9 |
| Unit Tests — FileStorageService | 14 |
| Unit Tests — RateLimiter | 10 |
| **Unit Tests Total** | **150** |
| Integration Tests — Lifecycle | 4 |
| Integration Tests — Tenant Isolation | 16 |
| Integration Tests — Auth Flow | 5 |
| Integration Tests — Rate Limiting | 5 |
| Integration Tests — File Security | 5 |
| Integration Tests — Manager Reassignment | 3 |
| Integration Tests — User Deactivation | 5 |
| **Integration Tests Total** | **43** |
| Security Tests — JWT | 8 |
| Security Tests — Cross-Tenant | 14 |
| Security Tests — RBAC | 30 |
| Security Tests — File | 7 |
| Security Tests — Lockout | 5 |
| Security Tests — Refresh Token | 5 |
| **Security Tests Total** | **69** |
| Edge Cases & Negative Tests | 60 |
| Performance Tests | 13 |
| **Grand Total** | **335** |

---

## Appendix B: Test Execution Priority

**Phase 1 — Foundation (run on every commit):**
- All unit tests (Section 2)
- Tenant isolation integration tests (IT-ISO-*)
- Security JWT tests (SEC-JWT-*)
- RBAC enforcement tests (SEC-RBAC-*)

**Phase 2 — Feature Validation (run on PR merge):**
- All integration tests (Section 3)
- All security tests (Section 4)
- Edge cases for state machine (EDGE-011 through EDGE-020)

**Phase 3 — Release Readiness (run before release):**
- All edge cases and negative tests (Section 5)
- Performance tests (Section 6)
- Full regression suite (all 335 tests)

---

*End of Test Plan*
