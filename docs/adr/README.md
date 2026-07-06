# Architecture Decision Records (ADRs)

This directory contains Architecture Decision Records for the Substrata platform. ADRs document significant architectural and design decisions, their context, alternatives considered, and consequences.

## Purpose

ADRs serve to:
- Document the reasoning behind important technical decisions
- Provide context for future maintainers
- Create a historical record of architectural evolution
- Facilitate discussion and review before implementation
- Prevent re-litigation of resolved decisions

## Format

Each ADR follows this structure:

```markdown
# ADR-NNN: [Title]

**Status**: [Proposed | Accepted | Deprecated | Superseded by ADR-XXX]
**Date**: YYYY-MM-DD
**Deciders**: [List of people involved]

## Context

What is the issue we're facing? What forces are at play?

## Decision

What is the change we're proposing or have agreed to?

## Consequences

What becomes easier or harder as a result of this decision?
What are the trade-offs?

## Alternatives Considered

What other options did we evaluate? Why were they not chosen?
```

## Process

1. **Propose**: Create a new ADR with status "Proposed"
2. **Discuss**: Review with stakeholders, update based on feedback
3. **Decide**: Change status to "Accepted" once consensus is reached
4. **Implement**: Proceed with implementation guided by the ADR
5. **Evolve**: If superseded, update status and link to new ADR

## Locked Decisions

The following ADRs are **locked** and must not be re-litigated:

- **ADR-001**: Backend stack (FastAPI/Python)
- **ADR-002**: Warehouse connector architecture (curated push + hybrid cache)

Do not propose changes to locked decisions without exceptional justification and stakeholder approval.

## Open Decisions

The following decisions require ADRs before their respective phases can begin:

- **Tenancy model** (blocks Phase 2)
- **Automation engine** (blocks Phase 6)
- **Packaging/tooling** (blocks Phase 1 finalization)
- **Auth/SSO provider** (blocks Phase 2)
- **Reference cache store** (blocks Phase 7)

## Index

- [ADR-001: Backend Stack - FastAPI and Python](./ADR-001-backend-fastapi-python.md) - **Accepted**
- [ADR-002: Warehouse Connector Architecture](./ADR-002-warehouse-connectors.md) - **Accepted**
