# Contributing to Substrata

Thank you for your interest in contributing to Substrata! This document provides guidelines for contributing to the platform.

## Phase 0 Status

This is an initial placeholder. Full contributing guidelines will be established in Phase 1+.

## Before You Contribute

1. **Read the README**: Understand the platform's purpose, hard constraints, and locked decisions
2. **Review ADRs**: Familiarize yourself with architectural decisions in `docs/adr/`
3. **Respect guardrails**: All contributions must pass guardrail lint (no company names, IP owner name only in LICENSE/NOTICE)
4. **Follow the ADR process**: Propose an ADR for any architectural or significant design decision

## Hard Constraints (Never Violate)

All contributions must respect these constraints (enforced in CI):

1. No third-party company/vendor names in code, UI, docs, or customer-facing surfaces
2. IP owner name appears only in LICENSE and NOTICE
3. All changes must pass `python3 .guardrails/guardrail_lint.py`
4. Tenant isolation is a security boundary (no cross-tenant data access)
5. Customer owns their warehouse data
6. No fabricated domain data (everything must trace to a source)

## Code Quality Requirements

### Python (Backend)
- Pass `ruff check` (no warnings)
- Pass `mypy --strict` (no errors)
- Include tests for new functionality
- Follow existing code patterns

### TypeScript (Frontend, Phase 1+)
- Pass TypeScript strict mode
- Pass configured linter
- Include tests for new functionality
- Follow design system patterns

## Development Workflow

1. **Create a branch**: Use descriptive names (e.g., `feature/add-cost-analysis`)
2. **Make changes**: Follow code quality requirements
3. **Run guardrails**: `python3 .guardrails/guardrail_lint.py`
4. **Run tests**: Ensure all tests pass
5. **Commit**: Use clear, descriptive commit messages
6. **Push**: Push to your branch
7. **Pull request**: Submit PR with clear description

## ADR Process

For architectural or significant design decisions:

1. Create a new ADR file in `docs/adr/` (use next number in sequence)
2. Follow the ADR template (see existing ADRs)
3. Set status to "Proposed"
4. Submit for review
5. Update status to "Accepted" after approval
6. Implement the decision

## Questions?

- Check existing documentation in `docs/`
- Review closed issues/PRs for similar discussions
- Open an issue for questions or clarifications

---

**Note**: These guidelines will expand significantly in Phase 1+ as the platform matures.
