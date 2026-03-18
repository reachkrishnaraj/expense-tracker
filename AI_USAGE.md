# AI Usage Documentation

This document describes how AI tools were used during the development of the Multi-Tenant Expense Tracker project, in the spirit of transparency and honest assessment.

## AI Tool Used

**Claude (Anthropic)** via the **Claude Code CLI** -- a terminal-based agent capable of reading, writing, and running code in a local development environment.

## Approach

Development followed a **multi-agent orchestration strategy**:

1. **PM analysis agent** -- Analyzed the problem statement, identified 30+ requirement gaps, and produced a structured gap analysis with suggested decisions (see `PM_ANALYSIS_Problem1.md`).
2. **Design agent** -- Authored the technical design document covering architecture, database schema, API design, security model, and trade-off analysis (see `DESIGN.md`).
3. **Backend agents** -- Specialized agents worked on different modules in parallel: auth/security, expense CRUD, approval workflow, analytics, and rate limiting.
4. **Frontend agents** -- Built API layer, page components, shared UI components, and auth context concurrently.
5. **Integration agent** -- Reconciled outputs from parallel agents, resolved conflicts, and ensured end-to-end consistency.

Each agent operated in an isolated git worktree to prevent conflicts between parallel work streams.

## What AI Did Well

- **Requirements analysis and gap identification.** The PM analysis phase surfaced over 30 gaps in the original problem statement (team structure, approval state transitions, receipt handling rules, edge cases like "who approves a manager's expenses"). This front-loaded analysis prevented rework during implementation.

- **Generating boilerplate code.** Entity classes, DTOs, repository interfaces, controller scaffolding, and Flyway migration SQL were produced quickly with consistent naming conventions and annotation patterns.

- **Consistent API design.** All endpoints follow the same conventions (pagination format, error response structure, HTTP status codes, naming patterns) because they were generated from a single design document.

- **State machine implementation.** The expense approval workflow (DRAFT -> SUBMITTED -> APPROVED/REJECTED, with resubmission) was implemented correctly on the first pass, with proper guard conditions and audit logging at each transition.

- **Security boilerplate.** JWT filter chain, refresh token rotation with reuse detection, BCrypt password hashing, and `@PreAuthorize` annotations were generated correctly from the design spec.

- **Multi-tenancy enforcement.** Tenant-scoped repository queries and the `TenantContext` ThreadLocal pattern were applied consistently across all modules.

## What Required Human Judgment

- **Architecture decisions.** Choosing shared-database multi-tenancy over schema-per-tenant or database-per-tenant required weighing operational complexity against isolation guarantees. AI presented options but the human made the call based on the MVP scope and operational constraints.

- **Security model design.** The decision to use JWT with refresh token rotation (rather than server-side sessions, or OAuth2 with an external IdP) was a human judgment call based on the deployment model and client requirements. Similarly, the refresh token reuse detection strategy (revoke entire token family on reuse) required reasoning about attack scenarios.

- **Scope decisions.** Deciding what to include in MVP versus defer (single-level approval vs. multi-level, no email notifications, no CSV export, no expense policies/limits) required product judgment that AI could inform but not make.

- **Approval workflow edge cases.** Who approves a manager's own expenses? What happens to pending approvals when a manager is deactivated? These required business-context decisions that AI surfaced as questions but could not resolve unilaterally.

- **Database schema trade-offs.** Using VARCHAR with CHECK constraints instead of PostgreSQL ENUMs for role and status columns was a human decision based on migration ergonomics. Storing `manager_id` on the expense (snapshotting at submission time) rather than always resolving at query time was a conscious denormalization decision.

- **Frontend UX flow.** Page layout, navigation structure, which data to show on summary views versus detail views, and the overall user flow required human product sense.

## What Was Rejected or Modified

- **AI initially suggested using H2 for tests.** This was rejected in favor of Testcontainers with real PostgreSQL, since H2 has known behavioral differences (e.g., UUID handling, JSONB support, CHECK constraint enforcement) that can mask bugs.

- **AI proposed an `@TenantFilter` Hibernate filter.** While elegant, this was replaced with explicit `WHERE tenant_id = ?` predicates in repository queries for transparency. Hibernate filters are "magic" that can be accidentally bypassed; explicit queries are reviewable and testable.

- **AI generated overly granular microservice boundaries.** Early suggestions to separate auth, expenses, and approvals into independent services were rejected. A monolith with clear internal module boundaries is the correct choice for an MVP with a single deployment target.

- **AI recommended storing refresh tokens in Redis.** This was simplified to PostgreSQL storage for the MVP to avoid introducing another infrastructure dependency. The token table is small and query patterns are simple (lookup by hash, update revoked flag).

- **AI produced generic error messages.** Several controller error responses were rewritten to provide actionable messages (e.g., "Reassign employees before changing this user's role" instead of "Operation not permitted").

- **AI initially omitted audit logging.** The `expense_audit_log` table and transition logging were added after human review identified that the approval workflow lacked traceability.

## Efficiency Impact

AI accelerated the implementation by an estimated **5-10x** compared to writing everything from scratch. The largest time savings came from:

- Boilerplate generation (entities, DTOs, repositories, migration SQL)
- Consistent application of patterns across modules (tenant scoping, error handling, pagination)
- Parallel development of independent modules via multi-agent orchestration

However, **every AI-generated artifact required review**. The most time-consuming review areas were:

- Security-sensitive code (auth filters, token handling, tenant isolation)
- State machine transition logic (ensuring no invalid paths)
- Cross-module integration points (ensuring DTOs matched between backend and frontend)

## Key Takeaway

AI served as a highly productive implementation partner but required constant architectural oversight and integration coordination. The workflow that produced the best results was:

1. **Human defines the architecture and makes design decisions** (DESIGN.md)
2. **AI implements modules in parallel** against the design spec
3. **Human reviews, integrates, and corrects** the outputs
4. **AI iterates** on feedback

The design document (DESIGN.md) was the critical artifact -- it served as the "contract" that enabled parallel AI agents to produce compatible outputs. Without it, the agents would have made inconsistent assumptions.
