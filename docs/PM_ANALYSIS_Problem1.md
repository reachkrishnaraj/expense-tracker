# PM Analysis: Problem 1 — Multi-Tenant Expense Tracker with Approval Workflows

**Date:** 2026-03-18
**Role:** Product Manager — Deep Requirements Analysis
**Goal:** Identify gaps, ambiguities, and decisions needed before technical design begins.

---

## 1. What Is Explicitly Stated (Baseline Requirements)

| # | Requirement | Clarity |
|---|-------------|---------|
| R1 | Users sign up and belong to an organization (multi-tenancy) | Partial — mechanics unclear |
| R2 | Authentication via JWT with refresh token rotation | Clear |
| R3 | Roles: Employee, Manager, Admin — each with different permissions | Partial — permissions not defined |
| R4 | Employees submit expenses (amount, category, receipt upload, notes) | Partial — field details vague |
| R5 | Managers see only their direct team's pending expenses and can approve/reject with comments | Partial — "direct team" undefined |
| R6 | Admins see organization-wide dashboards with spend analytics (totals by category, by month, by team) | Moderate |
| R7 | API rate limiting per tenant | Clear intent, details open |

---

## 2. Identified Gaps — Organized by Domain

### 2.1 Organization & Tenant Lifecycle

| Gap ID | Gap | Impact | Suggested Decision |
|--------|-----|--------|--------------------|
| G-TEN-1 | **How is an organization created?** Self-service signup? Pre-provisioned by a super-admin? | Fundamental to onboarding flow | Self-service: first user who signs up creates the org and becomes its Admin |
| G-TEN-2 | **How do subsequent users join an organization?** Invite-only? Email domain matching? Open join with org code? | Affects security and UX | Invite-by-Admin (email invite link) — simplest secure model |
| G-TEN-3 | **Can a user belong to multiple organizations?** | Schema and auth design impact | No — one user, one org. Simplifies multi-tenancy. Can note as future enhancement. |
| G-TEN-4 | **Tenant-level configuration** — can orgs customize expense categories, approval limits, currency? | Affects schema flexibility | Yes for categories (org-configurable list). Currency: single currency per org. |
| G-TEN-5 | **Tenant deactivation/deletion** — what happens when an org is dissolved? | Data lifecycle | Out of scope for MVP. Soft-delete the org; data retained. |

### 2.2 User Management & Authentication

| Gap ID | Gap | Impact | Suggested Decision |
|--------|-----|--------|--------------------|
| G-USR-1 | **User profile fields** — what beyond email/name? | UI and schema | Name, email, password. Optional: phone, department, employee ID. |
| G-USR-2 | **Password policy** — requirements, reset flow | Security | Minimum 8 chars, 1 uppercase, 1 number. Password reset via email token. |
| G-USR-3 | **Account lockout** — brute force protection | Security | Lock after 5 failed attempts for 15 minutes. |
| G-USR-4 | **Session management** — how many concurrent sessions? What triggers token invalidation? | Security | Allow multiple sessions. Refresh token rotation invalidates the old refresh token on each use. Logout invalidates the refresh token. |
| G-USR-5 | **Who assigns/changes roles?** | Authorization flow | Only Admin can assign/change roles within their org. |
| G-USR-6 | **Can a user hold multiple roles?** E.g., a team lead who is both Manager and Employee | Permissions model | Each user has exactly one role. Managers can also submit their own expenses (role implies Employee capabilities + approval capabilities). |
| G-USR-7 | **Bootstrap problem** — first admin creation | Onboarding | The user who creates the organization is automatically the first Admin. |

### 2.3 Team Structure & Hierarchy

| Gap ID | Gap | Impact | Suggested Decision |
|--------|-----|--------|--------------------|
| G-TEAM-1 | **What defines "direct team"?** Is it a flat list of employees assigned to a manager, or a department/team entity? | Core to approval routing | Simple model: each Employee has a `manager_id` FK pointing to a Manager user. A Manager's "direct team" = all users where `manager_id = that manager`. |
| G-TEAM-2 | **Can there be multiple management levels?** (Manager → Senior Manager → VP) | Workflow complexity | Single-level approval for MVP. One manager approves. Multi-level noted as enhancement. |
| G-TEAM-3 | **Who approves a Manager's own expenses?** | Workflow gap | The Admin approves Manager expenses. If no Admin is set as their manager, the org Admin serves as the fallback approver. |
| G-TEAM-4 | **Who approves Admin's expenses?** | Edge case | Admins can self-approve, or another Admin approves. For MVP: Admin expenses are auto-approved (logged for audit). |
| G-TEAM-5 | **What happens when a manager is reassigned or deactivated?** | Pending approvals orphaned | Pending approvals reassigned to the new manager. If no replacement, escalate to Admin. |
| G-TEAM-6 | **What happens when an employee is deactivated/leaves?** | Open expenses | Pending expenses are auto-cancelled. Historical data retained. |
| G-TEAM-7 | **Can an employee exist without a manager assigned?** | Data integrity | No — Admin must assign a manager before the employee can submit expenses. Show a "no manager assigned" state in UI. |

### 2.4 Expense Submission

| Gap ID | Gap | Impact | Suggested Decision |
|--------|-----|--------|--------------------|
| G-EXP-1 | **Required vs optional fields** | Validation rules | Required: amount, category, date of expense. Optional: receipt, notes, merchant name. |
| G-EXP-2 | **Expense categories** — fixed global list or org-configurable? | Schema design | Org-configurable. Provide a default set on org creation (Travel, Meals, Office Supplies, Software, Equipment, Other). Admin can add/edit/deactivate categories. |
| G-EXP-3 | **Currency** — single or multi-currency? | Complexity | Single currency per organization (set at org creation). Store as BigDecimal. |
| G-EXP-4 | **Receipt upload details** — file types, size limit, multiple files per expense? | Storage design | Allowed: JPEG, PNG, PDF. Max 5MB per file. Up to 3 receipts per expense. |
| G-EXP-5 | **Expense date vs submission date** | Schema | Track both: `expense_date` (when the expense occurred) and `created_at` (when submitted). |
| G-EXP-6 | **Can an expense be edited after submission?** | State machine | Only in DRAFT or REJECTED state. Once SUBMITTED (pending approval), no edits. |
| G-EXP-7 | **Can an expense be deleted?** | Business rule | Only DRAFT expenses can be deleted by the submitter. Submitted/approved/rejected expenses are retained (soft-delete at most). |
| G-EXP-8 | **Draft mode** — can employees save expenses as drafts before submitting? | UX | Yes — DRAFT state allows save-and-return-later. |
| G-EXP-9 | **Duplicate detection** | Business rule | Out of scope for MVP. Could warn if same amount+date+category exists. |
| G-EXP-10 | **Expense amount limits/policies** | Business rule | Out of scope for MVP. Note as future enhancement (e.g., "expenses over $500 require VP approval"). |
| G-EXP-11 | **Merchant/vendor name** | Data richness | Optional field. Useful for analytics but not required. |

### 2.5 Approval Workflow & State Machine

| Gap ID | Gap | Impact | Suggested Decision |
|--------|-----|--------|--------------------|
| G-APR-1 | **Complete list of states** | Core design | **DRAFT → SUBMITTED → APPROVED / REJECTED → (RESUBMITTED → APPROVED / REJECTED)**. See state machine below. |
| G-APR-2 | **Can a rejected expense be resubmitted?** | UX and workflow | Yes. Employee can edit and resubmit a rejected expense (moves to RESUBMITTED state, functionally treated like SUBMITTED). |
| G-APR-3 | **Can an approved expense be reversed/recalled?** | Business rule | No — once approved, it's final. This simplifies the state machine. If needed, a correcting/negative expense can be submitted. |
| G-APR-4 | **Approval comments — required on rejection?** | UX | Required on rejection (must explain why). Optional on approval. |
| G-APR-5 | **Batch approval** — can a manager approve multiple expenses at once? | UX efficiency | Yes — provide a "select all / bulk approve" action on the pending list. |
| G-APR-6 | **Approval delegation** — manager on leave | Business rule | Out of scope for MVP. Admin can reassign employees to a different manager temporarily. |
| G-APR-7 | **Approval SLA/deadline** | Business rule | Out of scope for MVP. Note as future enhancement (e.g., auto-escalate if not acted on in 7 days). |
| G-APR-8 | **Is there a PAID/REIMBURSED state?** | End-to-end flow | Out of scope. The system tracks up to APPROVED. Actual payment/reimbursement is outside this system. |

#### Proposed State Machine

```
                    ┌──────────────────────────────────────────┐
                    │                                          │
                    ▼                                          │
  ┌───────┐    ┌───────────┐    ┌──────────┐              ┌───────────┐
  │ DRAFT │───▶│ SUBMITTED │───▶│ APPROVED │              │ CANCELLED │
  └───────┘    └───────────┘    └──────────┘              └───────────┘
      │              │                                         ▲
      │              │          ┌──────────┐                   │
      │              └─────────▶│ REJECTED │───(edit+resubmit)─┘ (NO — goes back to SUBMITTED)
      │                         └──────────┘
      │                              │
      │                              ▼
      │                     ┌─────────────────┐
      │                     │   SUBMITTED     │ (resubmitted, same state)
      └─(delete)───▶ gone   └─────────────────┘
```

**Transitions:**
| From | To | Triggered By | Conditions |
|------|----|-------------|------------|
| DRAFT | SUBMITTED | Employee clicks "Submit" | All required fields filled |
| DRAFT | (deleted) | Employee clicks "Delete" | Only drafts can be deleted |
| SUBMITTED | APPROVED | Manager clicks "Approve" | Manager is assigned to this employee |
| SUBMITTED | REJECTED | Manager clicks "Reject" | Comment required |
| REJECTED | SUBMITTED | Employee clicks "Resubmit" | Employee may edit fields before resubmitting |
| Any active state | CANCELLED | System | When employee is deactivated |

### 2.6 Receipt & File Storage

| Gap ID | Gap | Impact | Suggested Decision |
|--------|-----|--------|--------------------|
| G-FILE-1 | **Where are receipts stored?** | Infrastructure | Local filesystem for MVP, with an abstraction layer that can swap to S3/cloud. Store files in `uploads/{tenant_id}/{expense_id}/` |
| G-FILE-2 | **How are receipts secured?** | Security | Never serve files directly. API endpoint validates auth + tenant isolation + ownership before streaming the file. Signed URLs if using cloud storage. |
| G-FILE-3 | **Receipt viewing** — inline preview or download only? | UX | Inline preview for images (JPEG/PNG). Download link for PDFs. |
| G-FILE-4 | **Receipt deletion** — when expense is deleted/cancelled? | Data lifecycle | Receipts remain for audit purposes even if expense is cancelled. DRAFT deletions remove receipts. |

### 2.7 Admin Dashboard & Analytics

| Gap ID | Gap | Impact | Suggested Decision |
|--------|-----|--------|--------------------|
| G-DASH-1 | **Date range filtering** | UX | Yes — default to current month, allow custom date range. |
| G-DASH-2 | **Specific metrics beyond what's listed** | Scope | Totals by category, by month, by team (as stated). Add: total pending vs approved vs rejected counts, average approval time. |
| G-DASH-3 | **Chart types** | Frontend | Bar chart for category breakdown, line chart for monthly trends, table for team breakdown. |
| G-DASH-4 | **Export capability** | UX | Out of scope for MVP. Note as enhancement (CSV/PDF export). |
| G-DASH-5 | **Can Managers see analytics for their own team?** | Role capability | Yes — Managers get a mini-dashboard for their team only. Admins see org-wide. |
| G-DASH-6 | **Real-time or periodic refresh?** | Technical | Periodic — data refreshes on page load or manual refresh. No real-time needed for analytics. |

### 2.8 Notifications

| Gap ID | Gap | Impact | Suggested Decision |
|--------|-----|--------|--------------------|
| G-NOT-1 | **Are there any notifications?** (Not mentioned at all) | UX completeness | Minimal for MVP: in-app notification badge. Notify manager when an expense is submitted. Notify employee when approved/rejected. |
| G-NOT-2 | **Email notifications?** | Scope | Out of scope for MVP. In-app only. |

### 2.9 API & Non-Functional Requirements

| Gap ID | Gap | Impact | Suggested Decision |
|--------|-----|--------|--------------------|
| G-API-1 | **Rate limiting specifics** — what limits? | Configuration | 100 requests/minute per tenant for general endpoints. 10 requests/minute for auth endpoints (login/signup). |
| G-API-2 | **Pagination** — list endpoints | Performance | Yes — all list endpoints paginated (default 20, max 100). Cursor-based or offset-based. |
| G-API-3 | **Search & filtering on expense lists** | UX | Filter by: status, category, date range, amount range. Search by: notes text. |
| G-API-4 | **Sorting** | UX | Sort by: date, amount, status. Default: most recent first. |
| G-API-5 | **Audit logging** | Security | Log all state transitions and admin actions. Not a full audit table for MVP, but structured logging. |
| G-API-6 | **Error handling strategy** | API design | Consistent error response format: `{ error: string, code: string, details?: object }`. Proper HTTP status codes. |
| G-API-7 | **API versioning** | Extensibility | URL prefix `/api/v1/`. Simple and sufficient. |

### 2.10 Frontend / UX

| Gap ID | Gap | Impact | Suggested Decision |
|--------|-----|--------|--------------------|
| G-UI-1 | **What pages/views are needed?** | Scope | See Page Inventory below |
| G-UI-2 | **Mobile responsiveness** | UX | Responsive design — functional on tablet/mobile, optimized for desktop. |
| G-UI-3 | **Accessibility** | Quality | Basic accessibility: semantic HTML, keyboard navigation, ARIA labels on interactive elements. |
| G-UI-4 | **Loading states and error handling** | UX | Skeleton loaders for lists, toast notifications for success/error, inline form validation. |

#### Proposed Page Inventory

| Page | Role | Description |
|------|------|-------------|
| Login | All | Email + password login |
| Signup | All | Create account + create or join org |
| Dashboard (Employee) | Employee | List of own expenses, quick stats (pending/approved/total), "New Expense" button |
| New/Edit Expense | Employee | Form: amount, category, date, merchant, notes, receipt upload |
| Expense Detail | All | View expense details, approval history, receipts |
| Pending Approvals | Manager | List of team's pending expenses with approve/reject actions |
| Team Dashboard | Manager | Mini analytics for own team |
| Admin Dashboard | Admin | Org-wide analytics (category, month, team breakdowns) |
| User Management | Admin | Invite users, assign roles, assign managers |
| Category Management | Admin | CRUD for expense categories |
| Profile/Settings | All | User profile, password change |

---

## 3. Sufficiency Assessment

### What's Sufficient (Good to Go)
- Core expense submission flow is clear enough to build
- JWT + refresh token rotation is well-specified
- Three-role RBAC model is appropriate for the scope
- Analytics requirements (by category/month/team) are specific enough
- Rate limiting per tenant is a clear directive

### What's Critically Missing (Must Decide Before Building)
1. **Team structure model** — how employees map to managers (G-TEAM-1)
2. **Approval state machine** — exact states and transitions (G-APR-1)
3. **Org onboarding flow** — how orgs and users are created (G-TEN-1, G-TEN-2)
4. **Manager's own expenses** — who approves them (G-TEAM-3)
5. **Manager reassignment** — handling pending approvals (G-TEAM-5)
6. **Expense editability rules** — when can an expense be modified (G-EXP-6)

### What Can Be Deferred (Document as Future Enhancement)
- Multi-level approval workflows
- Multi-currency support
- Expense policies and limits
- Email notifications
- Approval delegation
- CSV/PDF export
- Duplicate detection
- PAID/REIMBURSED state

---

## 4. Decisions Needed From Stakeholder (You)

Before I proceed to technical story breakdown, I need your input on:

### Decision 1: Organization Onboarding
**DECIDED:** Pre-seeded — org exists in the system, users join via invite or org code.

### Decision 2: Team Structure
**DECIDED:** Flat `manager_id` FK on user — one manager per employee.

### Decision 3: Manager's Own Expenses
**DECIDED:** Admin approves Manager expenses.

### Decision 4: Scope of Notifications
**DECIDED:** No notifications in MVP.

### Decision 5: Multi-Tenancy Model
**DECIDED:** Shared database with `tenant_id` column on all tables.

### Decision 6: Expense Resubmission After Rejection
**DECIDED:** Allow edit + resubmit.

### Decision 7: Draft State
**DECIDED:** Include DRAFT state (save without submitting).

---

## 5. Risk & Complexity Notes

| Area | Risk | Mitigation |
|------|------|------------|
| Multi-tenancy data leakage | Tenant A accesses Tenant B data | Enforce tenant_id at repository/query layer + Spring Security filter + integration tests |
| File upload security | Unauthorized access to receipts | Never expose file paths; auth-gated download endpoint |
| State machine integrity | Invalid transitions (e.g., DRAFT → APPROVED) | Enforce transitions in service layer; reject invalid transitions with 409 Conflict |
| JWT token theft | Session hijacking | Short-lived access tokens (15 min), refresh token rotation with reuse detection |
| Rate limiting bypass | Tenant exhausts shared resources | Token bucket per tenant_id, enforced at filter/middleware level |

---

*Next step: Once decisions are confirmed, produce refined technical stories with acceptance criteria.*
