# Guardrails System

This directory contains the guardrails enforcement system for the Substrata platform. The guardrails ensure that hard constraints around IP protection, brand isolation, and third-party name exclusion are maintained throughout the codebase.

## Purpose

The guardrails system enforces critical security and legal requirements:

1. **No third-party company names** in code, UI, docs, sample data, commit messages, or any customer-facing surface
2. **IP owner name restricted to LICENSE and NOTICE only** - never in UI, product copy, or customer-visible surfaces
3. **Vendor-neutral references** to external systems (except open standards like WITSML)

## Components

### `guardrail_lint.py`

The primary enforcement script that:
- Scans all tracked files (except those in `.gitignore`)
- Checks against the denylist of forbidden names from `config.json`
- Allows exceptions for `LICENSE` and `NOTICE` files
- Exits with non-zero status on violations (enforced in CI)

### `config.json`

Base configuration file containing:
- `denylist`: Array of forbidden third-party company/vendor names (examples for demonstration)
- `allowed_files`: Files exempted from the denylist (LICENSE, NOTICE)
- `allowed_standards`: Open standards that can be referenced (e.g., "WITSML", "LAS")
- `owner_name`: IP owner name (used to enforce LICENSE/NOTICE-only restriction)

**Base configuration**: `config.json` is committed with example entries for CI and default behavior.

**Local customization**: Create `config.local.json` (git-ignored) to add organization-specific forbidden names. The linter will merge both configs, with local overrides taking precedence.

## Usage

Run the guardrail lint before committing:

```bash
python .guardrails/guardrail_lint.py
```

The script must exit with status 0 before any phase is considered complete.

## CI Integration

This check runs automatically in CI and will fail the build if violations are detected. Do not weaken the guardrails to make a change pass - fix the violation instead.

## Customizing the Denylist

To add organization-specific forbidden names:

1. Copy `config.json` to `config.local.json`
2. Edit `config.local.json` (git-ignored) to add forbidden names to `denylist`
3. Run `guardrail_lint.py` to verify existing code
4. Local changes stay private - `config.local.json` is never committed

The linter will use `config.json` (base) merged with `config.local.json` (if present).

## Philosophy

The guardrails are **preventive, not detective**. They create friction at authoring time to prevent violations from entering the codebase, rather than requiring expensive remediation later.
