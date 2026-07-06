# Substrata Platform

**Multi-tenant, AI-native data intelligence platform for oilfield operations.**

> Substrata transforms oilfield operational data—from invoices, tickets, AFEs, and well reports—into validated, cost-analyzed, warehouse-integrated intelligence that operators can trust and act on.

---

## Overview

Substrata is a production-grade platform designed for:
- **Multi-tenancy**: Strict isolation guaranteeing customers never see each other's data
- **AI-Powered Extraction**: Vendor-neutral document parsing with human-in-the-loop correction
- **Intelligent QA/QC**: Visual rule builder with field/cross-record/temporal validations
- **Cost Intelligence**: Line-item normalization, budget tracking, variance & anomaly detection
- **Warehouse Integration**: Curated push + hybrid cache for customer-owned Databricks/Snowflake
- **Automation**: Durable pipelines (ingest→validate→cost→export→notify) with retry & observability

---

## Status: Phase 0 Complete ✓

**Current Phase**: Phase 0 - Foundation  
**Next Phase**: Phase 1 - Architecture & Design

### Phase 0 Deliverables

- ✓ LICENSE and NOTICE with IP owner attribution
- ✓ Guardrails system (lint enforcement for name/brand protection)
- ✓ ADR-001: Backend stack (FastAPI/Python) - **Locked**
- ✓ ADR-002: Warehouse connectors (curated push + hybrid cache) - **Locked**
- ✓ Repository structure and CI foundation
- ✓ Hard constraints documented and enforced

### What's Next (Phase 1)

Phase 1 will establish:
- Tenancy model (ADR required)
- Automation engine (ADR required)
- Packaging/tooling decisions (ADR required)
- Auth/SSO architecture (ADR required)
- Complete data model and schemas
- Threat model including reference cache
- UX design system (tokens, components, prototypes)
- High-fidelity mockups of core screens

---

## Hard Constraints (Never Violate)

These constraints are **enforced in CI** and cannot be bypassed:

1. **No company names**: No third-party, vendor, service-company, or employer names in code, UI, docs, sample data, commits, or customer-facing surfaces. Reference external systems generically. Open standards (WITSML, LAS, etc.) are allowed.

2. **IP owner name is licensing-only**: The owner's name appears **only** in LICENSE and NOTICE. Never in UI, product copy, public repos, or customer-visible surfaces.

3. **Guardrail lint must pass**: `python .guardrails/guardrail_lint.py` must exit 0. Runs in CI. Do not weaken guardrails to pass checks.

4. **Tenant isolation is a security boundary**: No customer can ever see, query, or infer data outside their org tree. Enforced at data, API, and UI layers. No query executes without resolved tenant + org scope.

5. **Customer owns warehouse data**: Connections are customer-admin-managed, least-privilege, revocable. Cached reference data is in-scope for isolation, retention, residency, and revoke-purge.

6. **No fabricated domain data**: Every value traces to source document, calculation, or user entry. AI outputs cite sources and never receive cross-tenant context.

---

## Locked Decisions

These ADRs are **accepted and locked**—do not re-litigate:

- **ADR-001**: Core stack is FastAPI/Python. TypeScript for web frontend only. Enforce `ruff` + `mypy --strict` in CI. CPU-bound work in worker processes.
  
- **ADR-002**: v1 writes curated datasets to customer warehouse (audited `ExportRun`) and reads reference tables via scheduled sync into tenant-scoped, encrypted, TTL'd cache. Two least-privilege credentials (READ reference, WRITE export). Instant revoke-purge.

See [`docs/adr/README.md`](./docs/adr/README.md) for full details.

---

## Open Decisions (Require ADRs Before Implementation)

| Decision | Blocks Phase | Status |
|----------|--------------|--------|
| Tenancy model (shared collections vs. DB-per-tenant) | Phase 2 | Open |
| Automation engine (durable workflow vs. task queue) | Phase 6 | Open |
| Packaging/tooling (uv vs. Poetry; OpenAPI codegen) | Phase 1 | Open |
| Auth/SSO provider (build vs. managed; SAML/OIDC/SCIM) | Phase 2 | Open |
| Reference cache store (MongoDB vs. Redis; per-table TTL defaults) | Phase 7 | Open |

---

## Project Structure

```
.
├── .guardrails/           # Name/brand protection enforcement
│   ├── README.md          # Guardrails documentation
│   ├── config.json        # Denylist config (git-ignored)
│   └── guardrail_lint.py  # Linter script (runs in CI)
├── docs/
│   ├── adr/               # Architecture Decision Records
│   │   ├── README.md
│   │   ├── ADR-001-backend-fastapi-python.md
│   │   └── ADR-002-warehouse-connectors.md
│   └── security/          # Threat model, security docs (TBD Phase 1)
├── backend/               # Python API, workers, connectors (TBD Phase 1)
├── web/                   # TypeScript/React frontend (TBD Phase 1)
├── LICENSE                # Apache 2.0 with IP owner
├── NOTICE                 # Legal attribution
└── README.md              # This file
```

---

## Development Workflow

### Prerequisites

- Python 3.11+
- Git
- (Additional tooling TBD in Phase 1 ADR)

### Running Guardrail Checks

Before committing, always run:

```bash
python .guardrails/guardrail_lint.py
```

This check runs in CI and will fail builds on violations. **Do not weaken guardrails to pass**—fix the violation instead.

### Code Quality (Python)

All Python code must pass:

```bash
ruff check .        # Linting
ruff format .       # Formatting
mypy --strict .     # Type checking
```

Enforced in CI with no exceptions.

---

## Security Invariants (Must Never Regress)

- No code path queries data without resolved tenant + org scope
- Tenant context derived from verified auth principal (no client header trust post-Phase 2)
- Cross-tenant data never enters AI prompts; AI outputs cite sources
- Warehouse credentials are least-privilege, per-tenant, vaulted; revoke purges cached data immediately
- Every state-changing action is audit-logged
- System Admin access to customer business data is scoped, audited, gated by step-up auth
- AI inference has per-tenant cost caps

See threat model (TBD Phase 1) for full analysis.

---

## Build Sequence

| Phase | Objective | Status |
|-------|-----------|--------|
| **Phase 0** | Foundation (LICENSE, guardrails, ADR-001, ADR-002) | ✓ Complete |
| **Phase 1** | Architecture & Design (ADRs, data model, threat model, UX design) | Next |
| Phase 2 | Core Platform (auth, tenancy, RBAC, audit) | Planned |
| Phase 3 | Ingestion & Parsing Engine | Planned |
| Phase 4 | QA/QC Rule Builder | Planned |
| Phase 5 | Cost Analysis | Planned |
| Phase 6 | Automation Infrastructure | Planned |
| Phase 7 | Enterprise Connectors (ADR-002) | Planned |
| Phase 8 | AI-Native UX | Planned |
| Phase 9 | Hardening & Compliance | Planned |
| Phase 10 | Beta → GA | Planned |

---

## Quality Gate (Required Before Phase Completion)

Every phase must satisfy:

- [ ] `guardrail_lint` exits 0 (no name/brand violations)
- [ ] `ruff` + `mypy --strict` pass (Python); `typecheck` + `lint` pass (web)
- [ ] Unit/integration/e2e tests for this phase pass in CI
- [ ] Isolation tests prove zero cross-tenant/cross-org access
- [ ] No header-trust tenant context remains (Phase 2+)
- [ ] ADRs for decisions made this phase are Accepted
- [ ] Threat model updated if attack surface changed
- [ ] Phase summary written

---

## Contributing

This is a vendor-neutral, open platform. Contributions must:
- Pass all quality gates
- Respect hard constraints (especially guardrails)
- Include tests and documentation
- Follow the ADR process for architectural decisions

See `CONTRIBUTING.md` (TBD) for full guidelines.

---

## License

Apache License 2.0

See [LICENSE](./LICENSE) and [NOTICE](./NOTICE) for full terms.

---

## Contact & Support

- **Documentation**: (TBD - link to docs site post-GA)
- **Issues**: (TBD - link to issue tracker)
- **Discussions**: (TBD - link to community forum)

---

**Substrata**: Vendor-neutral intelligence for oilfield operations.
