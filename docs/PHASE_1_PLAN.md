# Phase 1 Plan: Architecture & Design

**Status**: Ready for Approval  
**Date**: 2026-07-06  
**Phase Duration**: TBD (see note on timelines)  
**Prerequisite**: Phase 0 Complete ✓

---

## Overview

Phase 1 establishes the architectural foundation and design system for the Substrata platform. This phase focuses on **planning and design** rather than implementation, ensuring all critical decisions are resolved before building the core platform in Phase 2+.

**Note on Timelines**: Per project guidelines, we do not estimate calendar time (days/weeks). Instead, this plan details which components must be designed, what decisions must be resolved, and what dependencies exist.

---

## Phase 1 Objectives

1. **Resolve all open architectural decisions** via ADRs (5 proposals ready for review)
2. **Design complete data model** (MongoDB schemas for all collections)
3. **Create threat model** (including reference cache as in-scope customer data asset)
4. **Establish UX design system** (design tokens, component library, interaction patterns)
5. **Build high-fidelity prototypes** (core screens: dashboard, document review, rule builder, cost view, admin)
6. **Review isolation model** against threat model (verify no cross-tenant attack paths)
7. **Configure development tooling** (implement ADR-005: uv + ruff + mypy + pre-commit)

---

## Phase 1 Deliverables

### 1. Architecture Decision Records (5 ADRs)

**Status**: Proposals written ✓, awaiting review and approval

#### ADR-003: Tenancy Model and Data Isolation

**Blocks**: Phase 2 (Core Platform)

**Proposal Summary**:
- **Hybrid tenancy model**: Shared MongoDB collections (default) + dedicated database per tenant (enterprise tier)
- Middleware enforces tenant scope on every query
- Compound indexes: `{ tenantId: 1, orgPath: 1, ... }`
- Migration path between shared ↔ dedicated

**Decision Required**: Approve hybrid model or propose alternative

**Review Focus**:
- Security: Does middleware enforcement satisfy isolation invariant?
- Performance: Will shared collections scale to 1,000+ tenants?
- Operations: Can we manage both shared + dedicated databases?

#### ADR-004: Automation Engine Architecture

**Blocks**: Phase 6 (Automation Infrastructure)

**Proposal Summary**:
- **Temporal** (durable workflow engine) for multi-step pipelines
- Python workflows/activities using Temporal SDK
- Supports long-running, human-in-the-loop, retry/idempotency, replay
- Perfect fit for ADR-002 warehouse sync jobs

**Decision Required**: Approve Temporal or choose alternative (Prefect, Celery, custom)

**Review Focus**:
- Complexity: Is Temporal's operational overhead acceptable?
- Fit: Does it satisfy all automation requirements (human-in-the-loop, durability, observability)?
- Alternatives: Should we prototype Prefect as simpler alternative?

#### ADR-005: Packaging and Tooling Strategy

**Blocks**: Phase 1 (immediate - needed to finalize Phase 1 tooling setup)

**Proposal Summary**:
- **uv** for Python dependency management (10-100x faster than Poetry)
- **openapi-typescript-codegen** for TypeScript API client generation
- **Makefile** for common tasks + **pre-commit** for git hooks
- Single `pyproject.toml` (monorepo for v1)

**Decision Required**: Approve uv + openapi-typescript-codegen or choose alternatives

**Review Focus**:
- Maturity: Is uv stable enough for production?
- Team adoption: Will team adapt to uv workflow?
- Fallback: Can we easily switch to Poetry if uv proves problematic?

#### ADR-006: Authentication and Authorization

**Blocks**: Phase 2 (Core Platform)

**Proposal Summary**:
- **Auth0** (managed identity provider) for authentication
- JWT with custom claims (`tenantId`, `orgPath`, `roles`, `permissions`)
- Enterprise SSO (SAML/OIDC) + SCIM provisioning via Auth0
- RBAC + ABAC enforced in backend
- API keys for automation/integration

**Decision Required**: Approve Auth0 or choose alternative (Okta, FusionAuth, custom)

**Review Focus**:
- Cost: Is Auth0 pricing acceptable?
- Vendor lock-in: Are we comfortable with Auth0 dependency?
- Security: Does JWT enrichment satisfy security invariants?

#### ADR-007: Reference Data Cache Store

**Blocks**: Phase 7 (Enterprise Connectors)

**Proposal Summary**:
- **MongoDB** for reference data cache (reuse primary database)
- `reference_cache` collection with `tenantId` + `referenceType` indexes
- TTL indexes for automatic expiration
- Purge workflow for revoke scenario

**Decision Required**: Approve MongoDB or choose alternative (Redis, PostgreSQL)

**Review Focus**:
- Performance: Is MongoDB fast enough for cache use case?
- Simplicity: Does operational simplicity outweigh Redis's speed?
- Storage: Can MongoDB handle 10 GB per tenant reference data?

---

### 2. Data Model and MongoDB Schemas

**Deliverable**: Complete schema definitions for all collections

#### Collections to Design

**Tenant-Scoped Collections** (require `tenantId` + `orgPath`):

1. **documents** - Uploaded/parsed documents
   - Fields: `_id`, `tenantId`, `orgPath`, `fileName`, `fileType`, `storageUrl`, `uploadedBy`, `uploadedAt`, `classification`, `extractedFields`, `status`, `lineage`
   - Indexes: `{ tenantId: 1, orgPath: 1, uploadedAt: -1 }`, `{ tenantId: 1, status: 1 }`

2. **qa_rules** - QA/QC rules and rule sets
   - Fields: `_id`, `tenantId`, `orgPath`, `ruleName`, `ruleType`, `conditions`, `severity`, `actions`, `version`, `createdBy`, `createdAt`
   - Indexes: `{ tenantId: 1, orgPath: 1, ruleType: 1 }`, `{ tenantId: 1, status: 1 }`

3. **cost_items** - Line items and aggregations
   - Fields: `_id`, `tenantId`, `orgPath`, `documentId`, `lineItem`, `amount`, `costCode`, `afe`, `date`, `vendor`, `category`
   - Indexes: `{ tenantId: 1, orgPath: 1, date: -1 }`, `{ tenantId: 1, afe: 1 }`

4. **export_runs** - Warehouse export audit trail (ADR-002)
   - Fields: `_id`, `tenantId`, `exportedAt`, `exportedBy`, `targetTable`, `recordCount`, `schemaVersion`, `status`
   - Indexes: `{ tenantId: 1, exportedAt: -1 }`

5. **reference_cache** - Cached customer reference data (ADR-007)
   - Fields: `_id`, `tenantId`, `referenceType`, `warehouseTable`, `syncedAt`, `expiresAt`, `rowCount`, `schema`, `data`, `checksumSHA256`
   - Indexes: `{ tenantId: 1, referenceType: 1 }`, `{ expiresAt: 1 }` (TTL)

6. **audit_log** - State changes and actions
   - Fields: `_id`, `tenantId`, `userId`, `action`, `resourceType`, `resourceId`, `changes`, `timestamp`, `ipAddress`
   - Indexes: `{ tenantId: 1, timestamp: -1 }`, `{ userId: 1, timestamp: -1 }`

**System Collections** (no tenant scope):

7. **tenants** - Tenant metadata and configuration
   - Fields: `_id`, `tenantId`, `name`, `tier`, `isolationMode`, `cluster`, `createdAt`, `status`, `settings`
   - Indexes: `{ tenantId: 1 }` (unique)

8. **users** - User accounts (references `tenantId`)
   - Fields: `_id`, `userId`, `email`, `tenantId`, `orgPath`, `roles`, `permissions`, `createdAt`, `lastLoginAt`
   - Indexes: `{ userId: 1 }` (unique), `{ email: 1 }` (unique), `{ tenantId: 1 }`

9. **auth_sessions** - Authentication sessions (if not using Auth0 session management)
   - Fields: `_id`, `sessionId`, `userId`, `tenantId`, `createdAt`, `expiresAt`, `refreshToken`
   - Indexes: `{ sessionId: 1 }` (unique), `{ expiresAt: 1 }` (TTL)

**Task**: Document each schema in `docs/architecture/data-model.md` with:
- Full field definitions (types, constraints, descriptions)
- Validation rules (Pydantic models)
- Indexes (compound, unique, TTL)
- Relationships between collections
- Migration strategy (schema versioning)

---

### 3. Threat Model

**Deliverable**: `docs/security/threat-model.md`

#### Assets

1. **Customer business data** (documents, QA results, cost analysis)
2. **Reference data cache** (ADR-002 - in-scope customer data)
3. **Warehouse credentials** (least-privilege service accounts)
4. **User credentials** (passwords, MFA secrets, API keys)
5. **AI inference prompts/outputs** (must not leak cross-tenant data)
6. **Audit logs** (sensitive for forensics)

#### Threat Categories

1. **Cross-tenant data leakage** (highest priority)
2. **Credential compromise** (warehouse, user, API keys)
3. **AI prompt injection** (cross-tenant context in prompts)
4. **Cache poisoning** (malicious reference data)
5. **Insider threat** (System Admin abuse)
6. **Data residency violation** (customer data in wrong region)
7. **Insufficient audit trail** (untracked actions)

#### Analysis Per Threat

For each threat:
- **Attack vectors**: How could this happen?
- **Impact**: What's the blast radius?
- **Likelihood**: How feasible is the attack?
- **Mitigations**: What controls prevent/detect/respond?
- **Residual risk**: What remains after mitigations?

**Review Focus**: Isolation model (ADR-003) must be validated against cross-tenant leakage threats.

---

### 4. UX Design System

**Deliverable**: Design system in Figma (or similar) + component library specification

#### Design Tokens

- **Colors**: Primary, secondary, accent, semantic (success, warning, error, info)
- **Typography**: Font families, sizes, weights, line heights
- **Spacing**: 4px base unit, scale (4, 8, 12, 16, 24, 32, 48, 64)
- **Shadows**: Elevation levels (card, modal, dropdown)
- **Borders**: Radii, widths
- **Motion**: Transition durations, easing functions

#### Component Library

**Core Components**:
- Button (primary, secondary, destructive, ghost)
- Input (text, number, date, select, multiselect)
- Table (sortable, filterable, paginated)
- Modal (dialog, drawer, popover)
- Card (content container)
- Badge (status, severity)
- Alert (success, warning, error, info)
- Navigation (sidebar, breadcrumbs, tabs)
- Avatar (user profile)
- Loading (spinner, skeleton, progress bar)

**Domain Components**:
- DocumentCard (thumbnail, metadata, status badge)
- RuleBuilder (visual condition editor)
- CostChart (line item drill-down)
- QAResult (severity badge, source link)
- AuditLogEntry (action, user, timestamp)

**Accessibility**:
- WCAG 2.1 AA compliance
- Keyboard navigation
- Screen reader support
- Color contrast validation

**Documentation**: Storybook or similar for component playground

---

### 5. High-Fidelity Prototypes

**Deliverable**: Interactive prototypes in Figma (or similar)

#### Core Screens

1. **Dashboard** (Landing page after login)
   - Key metrics: documents processed, QA pass rate, cost variance
   - Recent activity feed
   - Quick actions (upload document, run QA, view cost analysis)
   - Tenant/org selector (if user has access to multiple)

2. **Document Review** (Human-in-the-loop correction)
   - Document preview (PDF/image viewer)
   - Extracted fields (editable)
   - Confidence scores per field
   - History (extraction versions, who edited)
   - Approve/reject buttons
   - Lineage (source document → extraction → correction)

3. **QA/QC Rule Builder** (Visual rule authoring)
   - Rule canvas (drag-and-drop conditions)
   - Field selector (from document schema)
   - Condition builder (equals, greater than, regex, etc.)
   - Severity selector (info, warn, error, block)
   - Dry-run simulator (test against historical data)
   - Rule set management (versioning, org inheritance)

4. **Cost Analysis View** (Budget vs. actual)
   - Cost rollup chart (by AFE, cost code, time period)
   - Variance highlighting (over/under budget)
   - Drill-down to line items
   - Anomaly callouts (AI-detected)
   - Export to warehouse button
   - Source document links (click line item → see invoice)

5. **Admin Settings** (Tenant/org management)
   - User management (invite, roles, permissions)
   - Org tree editor (create/edit/delete orgs)
   - Warehouse connection setup (ADR-002)
   - SSO configuration (ADR-006)
   - Reference table mapping (ADR-002)
   - TTL configuration per reference type (ADR-007)

**Prototype Flow**:
- User uploads document → extraction → review → QA pass/fail → cost analysis → export
- Validate user flow makes sense before implementation

---

### 6. Isolation Model Review

**Deliverable**: Security review sign-off document

**Process**:
1. Map all data access paths (API endpoints → database queries)
2. Verify every query includes tenant scope (from ADR-003 design)
3. Identify any path that could bypass tenant scope
4. Propose isolation tests to prove zero cross-tenant access
5. Review against threat model (Section 3)
6. Sign-off from security team

**Acceptance Criteria**:
- Zero identified paths that query data without tenant scope
- Test plan covers all identified attack vectors
- Threat model risks assessed and mitigated

---

### 7. Development Tooling Configuration

**Deliverable**: Fully configured development environment per ADR-005

**Tasks**:
1. Create `pyproject.toml` with uv configuration
2. Define all dependencies (api, workers, warehouse, dev)
3. Configure ruff (`tool.ruff` in `pyproject.toml`)
4. Configure mypy (`tool.mypy` in `pyproject.toml`)
5. Create Makefile with common commands
6. Set up pre-commit hooks (`.pre-commit-config.yaml`)
7. Update CI to use uv (`.github/workflows/ci.yml`)
8. Test end-to-end: `make install && make ci`

**Validation**:
```bash
# Must pass before Phase 1 complete
make install      # Install deps with uv
make guardrails   # Pass guardrail lint
make lint         # Pass ruff
make typecheck    # Pass mypy --strict
make test         # Pass all tests
```

---

## Phase 1 Acceptance Criteria

Phase 1 is complete when:

- ✅ All 5 proposed ADRs reviewed and **Accepted** (status changed from Proposed to Accepted)
- ✅ Data model documented with full schemas, indexes, validation rules
- ✅ Threat model written with all critical threats analyzed
- ✅ Design system established (tokens + components)
- ✅ High-fidelity prototypes approved for core screens
- ✅ Isolation model reviewed and signed off by security team
- ✅ Development tooling fully configured (ADR-005 implemented)
- ✅ Quality gate passed:
  - `guardrail_lint` exits 0
  - `ruff check` exits 0
  - `mypy --strict` exits 0
  - All tests pass

---

## Dependencies and Blockers

### Must Complete Phase 1 Before Phase 2

Phase 2 (Core Platform) **cannot begin** until:
- ADR-003 (Tenancy) Accepted ✓
- ADR-005 (Tooling) Implemented ✓
- ADR-006 (Auth) Accepted ✓
- Data model finalized ✓
- Threat model reviewed ✓

### Phase 1 Internal Dependencies

- ADR-005 (Tooling) must be implemented first (blocks dev environment setup)
- Data model depends on ADR-003 (Tenancy) approval
- Threat model depends on data model completion

---

## Open Questions (Require Decisions During Phase 1)

1. **UX framework choice**: React vs. Vue vs. Svelte for web frontend?
2. **Component library**: Build custom or use Tailwind + Headless UI?
3. **State management**: Zustand vs. React Query vs. Redux?
4. **Testing strategy**: Jest + Testing Library + Playwright?
5. **Monorepo tool**: Single repo or separate frontend/backend repos?

---

## Next Steps

### Immediate (Waiting for Approval)

1. **Review ADR proposals**: Stakeholders review ADR-003 through ADR-007
2. **Approve or request changes**: For each ADR, either approve or propose alternatives
3. **Implement ADR-005**: Once tooling ADR approved, set up uv + ruff + mypy

### After ADR Approval

4. **Document data model**: Write full schemas in `docs/architecture/data-model.md`
5. **Write threat model**: Analyze threats in `docs/security/threat-model.md`
6. **Design UX system**: Create Figma design system + component specs
7. **Build prototypes**: High-fidelity mockups for 5 core screens
8. **Security review**: Isolation model review against threat model

---

## Phase 1 Summary

Phase 1 establishes the **architectural and design foundation** for Substrata. Unlike Phase 0 (which delivered scaffolding), Phase 1 delivers **plans, designs, and decisions**—no production code. The investment in Phase 1 ensures Phase 2+ implementation proceeds efficiently without re-work due to unresolved decisions or design flaws.

**Key Outputs**:
- 5 Accepted ADRs (all open decisions resolved)
- Complete data model (MongoDB schemas)
- Threat model (security analysis)
- UX design system (tokens + components)
- High-fidelity prototypes (core screens)
- Configured development environment (uv + ruff + mypy + pre-commit)

**Phase 1 is critical for v1 success**: Skipping or rushing this phase would lead to costly re-work during implementation.

---

**Status**: Ready for stakeholder review and ADR approval.
