# Backend Code Review: Multi-Tenant Expense Tracker API

**Reviewer:** Senior Code Review (Automated)
**Date:** 2026-03-18
**Scope:** All backend Java source files in `expense-tracker-api/src/main/java/com/expensetracker/`

---

## Executive Summary

The backend is well-structured with clear separation of concerns, proper tenant isolation patterns, and a solid security filter chain. However, the review uncovered **4 Critical**, **7 High**, **10 Medium**, and **8 Low** severity issues that should be addressed before production deployment.

| Severity | Count | Category |
|----------|-------|----------|
| CRITICAL | 4 | Security |
| HIGH | 7 | Security, Correctness, Data Integrity |
| MEDIUM | 10 | Correctness, API Design, Missing Functionality |
| LOW | 8 | Code Quality |

---

## 1. CRITICAL Issues

### C1. Hardcoded JWT Secret in Configuration

**File:** `src/main/resources/application.yml:35`
```yaml
secret: mySuperSecretKeyForJwtTokenGenerationThatIsAtLeast256BitsLong2024
```

**Impact:** The JWT signing secret is hardcoded in the YAML file checked into version control. Anyone with repository access can forge arbitrary JWT tokens with any user ID, tenant ID, and role -- completely bypassing authentication and authorization.

**Spec Reference:** S2.2 -- "key loaded from config, not hardcoded"

**Fix:** Use an environment variable or external secret:
```yaml
secret: ${JWT_SECRET:}
```
Fail startup if the env var is not set (remove default). Add a `@PostConstruct` validation in `JwtTokenProvider` to reject empty or weak secrets.

---

### C2. Password Validation Missing Uppercase + Digit Requirement

**File:** `dto/request/RegisterRequest.java:25`
```java
@Size(min = 8, message = "Password must be at least 8 characters")
private String password;
```

**Impact:** The spec requires "minimum 8 characters, at least 1 uppercase letter, at least 1 digit" (S2.1). Only the length constraint is enforced. Users can register with weak passwords like `aaaaaaaa`.

**Fix:** Add a `@Pattern` annotation:
```java
@Pattern(regexp = "^(?=.*[A-Z])(?=.*\\d).{8,}$",
         message = "Password must be at least 8 characters with at least 1 uppercase letter and 1 digit")
```

---

### C3. Logout Endpoint Requires Authentication but Is Not in Public Endpoints

**File:** `security/JwtAuthenticationFilter.java:25-29` and `controller/AuthController.java:47`

The `/api/v1/auth/logout` endpoint is NOT in the `PUBLIC_ENDPOINTS` set, so it requires a valid JWT. However, the logout endpoint accepts a `RefreshRequest` body. If a user's access token has expired but they still have a valid refresh token, they cannot call logout. More critically, the `TenantContextFilter` also skips this path, but since the JWT filter DOES process it, there's an inconsistency.

**Impact:** Users with expired access tokens cannot revoke their refresh tokens, which is a security concern since refresh tokens have a 7-day lifetime.

**Fix:** Add `/api/v1/auth/logout` to `PUBLIC_ENDPOINTS` in both `JwtAuthenticationFilter` and `TenantContextFilter`, or make the logout endpoint accept requests with or without a valid JWT (graceful degradation).

---

### C4. Account Lockout Returns 429 Without Retry-After Header

**File:** `service/AuthService.java:98-101`
```java
throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
        "Account is locked. Try again after " + user.getLockedUntil());
```

**Impact:** Per S2.2, lockout should return 429 with a `Retry-After` header. The current code throws a `ResponseStatusException` which is not caught by the `GlobalExceptionHandler` with a handler that sets the `Retry-After` header. The `ResponseStatusException` will be handled by Spring's default handler, which does not add the header and does not produce the expected error JSON format.

**Fix:** Create a dedicated exception (e.g., `AccountLockedException`) and handle it in `GlobalExceptionHandler` with proper `Retry-After` header computation, or use the existing `RateLimitExceededException` with the calculated seconds until unlock.

---

## 2. HIGH Issues

### H1. ResponseStatusException Used Inconsistently -- Bypasses Error Format Contract

**Files:** Multiple services use `ResponseStatusException` while the `GlobalExceptionHandler` handles custom exceptions.

Affected locations:
- `service/AuthService.java:55-56, 89-90, 94, 99-100, 106, 131, 137, 144`
- `service/ExpenseService.java:56, 73-74, 92, 96-97, 102, 111, 263, 267-268, 273, 278-279, 282-283, 288, 291, 295, 298, 303, 307, 322-323, 344, 348-349, 354`
- `service/CategoryService.java:33, 50-51, 53-54, 66-67`
- `service/ApprovalService.java:85, 97, 115, 122, 132-133, 152, 159`

**Impact:** `ResponseStatusException` is handled by Spring's default `ResponseStatusExceptionHandler`, NOT by `GlobalExceptionHandler`. This means the error response format is inconsistent -- some errors return `{ error, code, details, timestamp, path }` (custom exceptions) while others return Spring's default `{ timestamp, status, error, message, path }`. The spec requires consistent JSON error responses (Cross-Cutting Concerns section).

**Fix:** Replace all `ResponseStatusException` usages with the appropriate custom exceptions (`ResourceNotFoundException`, `BusinessRuleException`, `ConflictException`, `ForbiddenException`). Add a handler for `ResponseStatusException` in `GlobalExceptionHandler` as a safety net.

---

### H2. No @Transactional on AuditLogService.log() Calls After Expense Save -- Potential Partial Write

**File:** `service/ExpenseService.java:80-81`

The `createExpense` method is `@Transactional`, and it calls `auditLogService.log()` which is also `@Transactional`. This works correctly because the inner transaction joins the outer one. However, `auditLogService.log()` performs `expenseRepository.findById()` (line 31 of AuditLogService), which is redundant since the caller already has the expense. This creates an unnecessary extra SELECT query per audit log write.

**Impact:** Performance degradation -- every audit log write triggers an additional SELECT on the expenses table.

**Fix:** Refactor `AuditLogService.log()` to accept an `Expense` entity directly instead of looking it up again by ID.

---

### H3. Missing Tenant Isolation in AuditLogService

**File:** `service/AuditLogService.java:31-34`
```java
Expense expense = expenseRepository.findById(expenseId)
        .orElseThrow(...);
User performedBy = userRepository.findById(performedById)
        .orElseThrow(...);
```

**Impact:** `findById()` does not include a tenant_id filter. While the callers typically pass validated IDs, if this method is ever called with an attacker-controlled `expenseId`, it could create an audit log entry linked to a different tenant's expense. This is a defense-in-depth violation.

**Fix:** Use `findByIdAndTenantId()` with the current tenant from `SecurityUtils.getCurrentTenantId()`.

---

### H4. Bulk Approval Is Fully Transactional -- Spec Says Partial Success Is Acceptable

**File:** `service/ApprovalService.java:147-243`
```java
@Transactional
public BulkApprovalResultDto bulkAction(BulkApprovalRequest request) {
```

**Impact:** The entire `bulkAction` method is wrapped in a single `@Transactional`. If any runtime exception occurs (e.g., on expense #47 of 50), the entire transaction rolls back, including the 46 already-processed expenses. The spec (S5.4) explicitly states: "Entire operation does NOT need to be atomic -- partial success is acceptable."

The code does try-catch individual expenses, but a `DataAccessException` or other Spring-level exception could still cause a full rollback.

**Fix:** Remove `@Transactional` from `bulkAction()` and process each expense in its own transaction using a helper method annotated with `@Transactional(propagation = Propagation.REQUIRES_NEW)`. Alternatively, use `TransactionTemplate` for per-item transactions.

---

### H5. Race Condition in Approval Workflow -- No Optimistic Locking

**Files:** `model/Expense.java`, `service/ApprovalService.java:80-107`

The `Expense` entity has no `@Version` field. Two concurrent requests to approve the same expense could both read status=SUBMITTED, both pass the `assertSubmittedStatus` check, and both write APPROVED. While the second write would just re-approve (idempotent for approve), for reject-after-approve or approve-after-reject, this is a real problem.

**Impact:** A concurrent approve + reject on the same expense could result in data corruption where both `approvedBy`/`approvedAt` are set AND `rejectionComment` is set, or the final state depends on a race.

**Fix:** Add a `@Version` field to `Expense`:
```java
@Version
@Column(name = "version")
private Long version;
```
And handle `OptimisticLockException` in the approval/rejection code paths.

---

### H6. Manager Reassignment Audit Log Uses Wrong performedById

**File:** `service/UserService.java:117-124`
```java
auditLogService.log(
        expense.getId(),
        AuditAction.REASSIGNED,
        managerId, // performed by the new manager assignment action
        ...
```

**Impact:** The `performedById` is set to `managerId` (the new manager), but the action is performed by the Admin who called the endpoint. This creates a false audit trail where the manager appears to have reassigned themselves.

**Fix:** Pass the admin's user ID (available via `SecurityUtils.getCurrentUserId()` or a parameter) as `performedById`.

---

### H7. listExpenses in ExpenseService Has No Max Page Size Enforcement

**File:** `controller/ExpenseController.java:66`
```java
@RequestParam(defaultValue = "20") int size
```

**Impact:** There is no upper bound on `size`. A client can request `?size=999999` and potentially dump the entire expenses table, causing memory exhaustion and performance degradation.

**Spec Reference:** S4.5 -- "size (default 20, max 100)"

**Fix:** Enforce the maximum:
```java
int effectiveSize = Math.min(Math.max(1, size), 100);
Pageable pageable = PageRequest.of(page, effectiveSize);
```

---

## 3. MEDIUM Issues

### M1. AnalyticsSummary totalSubmitted Counts ALL Statuses, Not Just Submitted

**File:** `service/AnalyticsService.java:49`
```java
totalSubmitted += count;
```

**Impact:** The `totalSubmitted` field accumulates counts from ALL statuses (SUBMITTED, APPROVED, REJECTED, DRAFT, CANCELLED). The spec (S6.2) defines `totalSubmitted` as a distinct count. The current implementation makes `totalSubmitted` represent "total expenses" rather than "total submitted expenses." The naming is misleading.

**Fix:** Either rename to `totalExpenses` or only count expenses that have passed through the SUBMITTED state (i.e., exclude DRAFT).

---

### M2. Exception Types in ExpenseService -- RuntimeException Instead of Proper HTTP Exceptions

**File:** `service/ExpenseService.java:92, 102, 273, 354`
```java
throw new RuntimeException("Expense not found");
throw new RuntimeException("Expense can only be updated in DRAFT or REJECTED status");
throw new RuntimeException("Expense can only be submitted from DRAFT or REJECTED status");
throw new RuntimeException("Only DRAFT expenses can be deleted");
```

**Impact:** These `RuntimeException` instances will be caught by the generic `Exception` handler in `GlobalExceptionHandler`, returning HTTP 500 Internal Server Error. The correct status codes should be 404, 409, 409, and 409 respectively.

**Fix:** Replace with:
- `new ResourceNotFoundException("Expense", expenseId.toString())` for not-found
- `new ConflictException("message", "INVALID_STATE")` or `new InvalidStateTransitionException(...)` for invalid status transitions

---

### M3. ExpenseDto.receiptCount Always Returns 0

**Files:** `dto/response/ExpenseDto.java:71`, `dto/response/ExpenseSummaryDto.java:43`
```java
.receiptCount(0) // Will be set by service if receipts exist
```

**Impact:** The comment says "Will be set by service if receipts exist" but the service never sets it. `ExpenseDto.from()` and `ExpenseSummaryDto.from()` always return `receiptCount=0`. The only place receipt count is correct is in `ExpenseDetailDto` where it comes from the receipts list size.

**Fix:** Query receipt count in the service layer and set it on the DTO, or add a `@Formula` annotation on the entity to compute it via SQL.

---

### M4. Duplicate CORS Configuration

**Files:** `config/SecurityConfig.java:73-82` and `config/CorsConfig.java:1-18`

Both `SecurityConfig.corsConfigurationSource()` and `CorsConfig.addCorsMappings()` define CORS configuration for `/api/**` with identical settings. Having two CORS configurations can cause unexpected behavior -- Spring MVC CORS and Spring Security CORS can conflict, potentially allowing some preflight requests through one config but not the other.

**Fix:** Remove `CorsConfig.java` entirely and rely solely on the Spring Security CORS configuration in `SecurityConfig`, which is the recommended approach for applications using Spring Security.

---

### M5. Missing @Valid on CreateExpenseRequest and UpdateExpenseRequest

**File:** `controller/ExpenseController.java:40, 48`
```java
public ResponseEntity<ExpenseDto> createExpense(@RequestBody CreateExpenseRequest request) {
public ResponseEntity<ExpenseDto> updateExpense(..., @RequestBody UpdateExpenseRequest request) {
```

**Impact:** Neither `CreateExpenseRequest` nor `UpdateExpenseRequest` has validation annotations, and neither controller method uses `@Valid`. While the spec says all fields are optional for drafts, there is no server-side protection against negative amounts or strings exceeding database column lengths in the request body.

**Fix:** Add `@Valid` to the controller methods and add appropriate validation annotations to the DTOs (e.g., `@DecimalMin("0")` on amount, `@Size(max=200)` on merchantName).

---

### M6. Deactivation Cancels DRAFT Expenses -- Spec Says Only SUBMITTED

**File:** `service/UserService.java:160-162`
```java
List<Expense> expensesToCancel = expenseRepository
        .findByTenantIdAndSubmitterIdAndStatusIn(tenantId, userId,
                List.of(ExpenseStatus.SUBMITTED, ExpenseStatus.DRAFT));
```

**Impact:** The spec (S3.4) says "If user is an Employee with SUBMITTED expenses: those expenses move to CANCELLED state." The current code also cancels DRAFT expenses, which is not specified. DRAFT expenses belong solely to the user and could arguably just be left as-is (soft-deleted user cannot access them anyway).

**Fix:** Decide whether this is intentional behavior. If following the spec strictly, only cancel `ExpenseStatus.SUBMITTED` expenses.

---

### M7. GET /api/v1/expenses/{id} Has No @PreAuthorize -- Any Authenticated User Can Hit It

**File:** `controller/ExpenseController.java:53-56`
```java
@GetMapping("/{id}")
public ResponseEntity<ExpenseDetailDto> getExpense(@PathVariable UUID id) {
```

**Impact:** While the service layer does perform an access check (submitter / assigned manager / admin), the endpoint itself has no `@PreAuthorize` annotation. This is not technically a vulnerability because the service-layer check is correct, but it's inconsistent with the pattern used on other endpoints and violates defense-in-depth.

**Fix:** This is an accepted pattern (service-layer authorization), but document the decision. Alternatively, add a minimal `@PreAuthorize("isAuthenticated()")` for clarity.

---

### M8. Analytics Currency Hardcoded to "USD"

**File:** `service/AnalyticsService.java:58`
```java
.currency("USD")
```

**Impact:** The analytics summary always reports currency as "USD" regardless of the organization's actual currency setting. Multi-tenant organizations with different currencies would see incorrect labels.

**Fix:** Look up the organization's currency from the tenant context and use that value.

---

### M9. Missing Logout Endpoint in PUBLIC_ENDPOINTS of TenantContextFilter

**File:** `security/TenantContextFilter.java:20-24`

The logout endpoint `/api/v1/auth/logout` is not in `PUBLIC_ENDPOINTS`. When a user calls logout, the `TenantContextFilter` will set a tenant context from the JWT. This is benign but inconsistent.

**Fix:** See C3 above -- align all filters on which endpoints are public.

---

### M10. UserService.assignManager Has an Inefficient Query Pattern

**File:** `service/UserService.java:106-126`
```java
List<Expense> submittedExpenses = expenseRepository
        .findByTenantIdAndManagerIdAndStatusIn(tenantId, oldManagerId,
                List.of(ExpenseStatus.SUBMITTED));
// Filter only expenses belonging to this specific user
for (Expense expense : submittedExpenses) {
    if (expense.getSubmitterId().equals(userId)) {
```

**Impact:** The code fetches ALL submitted expenses assigned to the old manager across the entire tenant, then filters in Java for only those belonging to the specific user. For managers with hundreds of direct reports, this loads unnecessary data.

**Fix:** Add a repository method: `findByTenantIdAndSubmitterIdAndManagerIdAndStatus(tenantId, userId, oldManagerId, ExpenseStatus.SUBMITTED)` or use `findByTenantIdAndSubmitterIdAndStatusIn` and then filter by managerId.

---

## 4. LOW Issues

### L1. InMemoryRateLimiter Has No Bucket Cleanup -- Potential Memory Leak

**File:** `ratelimit/InMemoryRateLimiter.java:10`
```java
private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
```

**Impact:** Buckets are never evicted. Over time, the map will grow unboundedly (one entry per unique IP for auth endpoints, one per tenant for API endpoints). For IP-based rate limiting, this is a slow memory leak.

**Fix:** Add a scheduled cleanup task that removes buckets that haven't been accessed for a configurable duration (e.g., 10 minutes), or use a cache with TTL (e.g., Caffeine).

---

### L2. Style Inconsistency -- Mixed Exception Types Across Services

**Impact:** `ExpenseService` uses `ResponseStatusException` and `RuntimeException`. `UserService` uses custom exceptions (`ResourceNotFoundException`, `ConflictException`, `BusinessRuleException`). `CategoryService` uses `ResponseStatusException`. `ApprovalService` uses `ResponseStatusException`. This inconsistency makes the codebase harder to maintain.

**Fix:** Adopt a single pattern: use custom exceptions everywhere. This is partially addressed by fixing H1.

---

### L3. @Data on JPA Entities Can Cause Issues

**Files:** All entity classes use `@Data` (which includes `equals`, `hashCode`, `toString`).

**Impact:** Lombok's `@Data` generates `equals`/`hashCode` based on all fields, including lazy-loaded relationships. This can trigger unexpected lazy loading in `hashCode()` calls (e.g., when entities are put in Sets), cause `LazyInitializationException` outside transaction scope, or produce infinite recursion in `toString()` if bidirectional relationships exist.

**Fix:** Replace `@Data` with `@Getter @Setter` on entities. Implement custom `equals`/`hashCode` using only the `id` field (or use `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` with `@EqualsAndHashCode.Include` on `id`).

---

### L4. X-Forwarded-For Header Trusted Without Validation

**File:** `filter/AuthRateLimitFilter.java:73-78`
```java
private String extractClientIp(HttpServletRequest request) {
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isBlank()) {
        return xForwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
}
```

**Impact:** An attacker can spoof the `X-Forwarded-For` header to bypass IP-based rate limiting by sending a different fake IP on each request.

**Fix:** In a production deployment behind a known reverse proxy, configure Spring's `ForwardedHeaderFilter` or `server.forward-headers-strategy` and use `request.getRemoteAddr()` which will be correctly resolved. Document that the current implementation assumes a trusted proxy or direct connections.

---

### L5. No @NotBlank Validation on BulkApprovalRequest.action

**File:** `dto/request/BulkApprovalRequest.java:21`
```java
@NotNull(message = "Action is required")
@Pattern(regexp = "APPROVE|REJECT", message = "Action must be APPROVE or REJECT")
private String action;
```

**Impact:** `@NotNull` + `@Pattern` does not prevent blank strings. `@Pattern` may match against an empty string depending on the regex engine behavior. While this is unlikely to cause issues because the regex requires specific characters, using `@NotBlank` would be more defensive.

**Fix:** Replace `@NotNull` with `@NotBlank`.

---

### L6. ApprovalController Uses Class-Level @PreAuthorize but Approve/Reject Are Under /expenses/ Path

**File:** `controller/ApprovalController.java:27-28`
```java
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
```

The approve/reject endpoints are under `/api/v1/expenses/{id}/approve` and `/api/v1/expenses/{id}/reject`, mapped in `ApprovalController` rather than `ExpenseController`. This causes a path collision concern: both controllers register handlers under `/api/v1/expenses/`.

**Impact:** While Spring resolves this correctly (more specific path wins), it makes the codebase confusing. The approve/reject endpoints are semantically part of the expense lifecycle but split across controllers.

**Fix:** This is an architectural choice. Document it. Consider moving approve/reject to `ExpenseController` with method-level `@PreAuthorize`, or move them to `/api/v1/approvals/{id}/approve` to keep all approval endpoints together.

---

### L7. AnalyticsService.getSummary Counts DRAFT and CANCELLED in totalSubmitted

**File:** `service/AnalyticsService.java:40-50`

As noted in M1, the `totalSubmitted` counter in the switch statement does `totalSubmitted += count` in the default case, which means DRAFT and CANCELLED expenses are counted in the total. The `default` branch has a comment `/* ignore DRAFT, CANCELLED */` but the line `totalSubmitted += count;` (line 49) is OUTSIDE the switch, so it always executes.

**Fix:** Move the `totalSubmitted += count` inside the specific cases that should be counted, or rename the field.

---

### L8. Inconsistent Use of Builder Pattern Across DTOs

**Impact:** Some DTOs use Lombok `@Builder` (e.g., `ExpenseDto`, `UserDto`), while analytics DTOs (`AnalyticsSummaryDto`, `CategorySpendDto`, `MonthlySpendDto`, `TeamSpendDto`) have hand-written builders. This inconsistency suggests these were possibly generated or written at different times.

**Fix:** Standardize by using Lombok `@Builder` on all DTOs, or keep hand-written builders only if there's a specific reason (e.g., custom build logic).

---

## 5. Missing Functionality vs. Technical Stories

| Story | Acceptance Criteria | Status |
|-------|-------------------|--------|
| S2.2 | Account lockout: Retry-After header | **MISSING** -- see C4 |
| S2.2 | Account lockout: 429 status | Partially implemented -- returns 429 but no header |
| S2.3 | `POST /api/v1/auth/logout` | Implemented but requires valid JWT -- see C3 |
| S3.3 | Reassignment audit log "by Admin Z" | **BUG** -- logs managerId instead of admin ID -- see H6 |
| S3.4 | Deactivation cancels only SUBMITTED | **BUG** -- also cancels DRAFT -- see M6 |
| S4.2 | Expense creation restricted to EMPLOYEE or MANAGER | Implemented via @PreAuthorize |
| S4.5 | Max page size 100 | **MISSING** -- no upper bound -- see H7 |
| S5.4 | Bulk action partial success | **BUG** -- fully transactional -- see H4 |
| S6.2 | Analytics summary field semantics | **BUG** -- totalSubmitted counts all statuses -- see M1/L7 |
| S7.1 | Rate limiting X-RateLimit headers | Implemented |
| S7.1 | Rate limiting 429 + Retry-After | Implemented |

---

## 6. Positive Observations

- **Tenant Isolation:** Consistently enforced via `SecurityUtils.getCurrentTenantId()` + tenant-scoped repository queries. No endpoint accepts `tenant_id` as a request parameter.
- **BCrypt Cost Factor 12:** Correctly configured in `SecurityConfig.passwordEncoder()`.
- **Refresh Token Rotation with Reuse Detection:** Well-implemented in `RefreshTokenService` and `AuthService.refresh()`.
- **Path Traversal Prevention:** Properly implemented in `LocalFileStorageService` with `normalize()` + `startsWith(baseDir)` checks.
- **RBAC via @PreAuthorize:** Consistently applied on controllers (`UserController` ADMIN-only, `ApprovalController` MANAGER+ADMIN, etc.).
- **File Upload Validation:** Content type whitelist, size limit, and per-expense count limit all enforced.
- **State Machine Transitions:** DRAFT -> SUBMITTED -> APPROVED/REJECTED correctly implemented with proper checks.
- **Manager Snapshot on Submit:** Correctly captures the submitter's current manager at submission time.
- **Stateless JWT Architecture:** CSRF disabled, session stateless, proper filter chain ordering.
- **Flyway Migrations + validate DDL:** Good production practice -- ensures schema matches entities without Hibernate auto-DDL.

---

## 7. Summary of Required Actions

### Must Fix Before Production
1. **C1** -- Externalize JWT secret from source code
2. **C2** -- Add password complexity validation
3. **C3** -- Fix logout endpoint accessibility
4. **C4** -- Add Retry-After header to account lockout response
5. **H1** -- Standardize error response format (eliminate ResponseStatusException)
6. **H4** -- Fix bulk approval to support partial success
7. **H5** -- Add optimistic locking to Expense entity

### Should Fix Before Production
8. **H3** -- Add tenant isolation to AuditLogService
9. **H6** -- Fix audit log performedById for manager reassignment
10. **H7** -- Enforce max page size
11. **M2** -- Replace RuntimeException with proper HTTP exceptions
12. **M3** -- Fix receipt count in DTOs

### Nice to Have
13. **M4** -- Remove duplicate CORS config
14. **M5** -- Add validation to expense request DTOs
15. **L1** -- Add rate limiter bucket cleanup
16. **L3** -- Fix @Data on JPA entities
