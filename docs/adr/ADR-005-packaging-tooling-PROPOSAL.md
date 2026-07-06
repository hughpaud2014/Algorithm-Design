# ADR-005: Packaging and Tooling Strategy

**Status**: Proposed 🔄  
**Date**: 2026-07-06  
**Deciders**: Platform Architecture Team  
**Blocks**: Phase 1 (finalization)

## Context

The Substrata platform requires a robust Python dependency management and tooling strategy that supports:

1. **Deterministic builds**: Same code + same lockfile = identical environment
2. **Fast CI**: Quick dependency installation in CI/CD pipelines
3. **Developer productivity**: Simple commands for common tasks
4. **Monorepo support**: Manage multiple Python packages (api, workers, shared)
5. **Type checking**: `mypy --strict` enforcement across all code
6. **Linting/formatting**: `ruff` enforcement with no warnings
7. **OpenAPI codegen**: Generate TypeScript API clients from FastAPI spec

### Current State (Phase 0)

- No dependency management (manual `pip install`)
- No lockfile (non-reproducible builds)
- No OpenAPI client generation
- Tooling commands not standardized

### Key Decisions Required

1. **Dependency manager**: uv vs. Poetry vs. PDM
2. **Monorepo strategy**: Single pyproject.toml vs. per-package
3. **OpenAPI codegen**: openapi-generator vs. openapi-typescript-codegen vs. custom
4. **Task runner**: make vs. just vs. task vs. built-in scripts

## Decision

We will use **uv** for Python dependency management and **openapi-typescript-codegen** for TypeScript client generation.

### 1. Dependency Management: uv

**Rationale:**
- **Speed**: 10-100x faster than pip/Poetry (Rust-based)
- **Simplicity**: Single tool for virtual envs, dependencies, and lockfiles
- **Modern**: Built on modern Python packaging standards (PEP 621)
- **Active development**: Strong community momentum
- **pip-compatible**: Drop-in replacement, easy adoption

**Project structure:**
```toml
# pyproject.toml (root)
[project]
name = "substrata"
version = "0.1.0"
requires-python = ">=3.11"
dependencies = [
    "fastapi>=0.110.0",
    "uvicorn[standard]>=0.27.0",
    "pydantic>=2.6.0",
    "motor>=3.3.0",  # Async MongoDB driver
    "python-multipart>=0.0.9",
    "httpx>=0.26.0",
]

[project.optional-dependencies]
dev = [
    "pytest>=8.0.0",
    "pytest-asyncio>=0.23.0",
    "mypy>=1.8.0",
    "ruff>=0.2.0",
    "pre-commit>=3.6.0",
]
workers = [
    "temporalio>=1.5.0",  # Workflow engine (ADR-004)
]
warehouse = [
    "databricks-sql-connector>=3.0.0",
    "snowflake-connector-python>=3.7.0",
]

[tool.ruff]
target-version = "py311"
line-length = 100
select = ["E", "F", "I", "N", "W", "UP", "B", "A", "C4", "T20"]
ignore = []

[tool.mypy]
python_version = "3.11"
strict = true
warn_return_any = true
warn_unused_configs = true
disallow_untyped_defs = true

[tool.pytest.ini_options]
asyncio_mode = "auto"
testpaths = ["backend/tests"]
```

**Common commands:**
```bash
# Create virtual env and install deps
uv venv
uv pip install -e ".[dev,workers,warehouse]"

# Add new dependency
uv pip install <package>
uv pip freeze > requirements.lock  # Lock dependencies

# Run tests
uv run pytest

# Type check
uv run mypy backend/

# Lint/format
uv run ruff check backend/
uv run ruff format backend/
```

### 2. Monorepo Strategy: Single pyproject.toml

**Rationale:**
- Simpler for v1 (backend + workers are tightly coupled)
- Single dependency tree (no version conflicts between packages)
- Easier CI configuration
- Can split into workspaces later if needed

**Directory structure:**
```
backend/
  main.py         # FastAPI app
  workers/        # Temporal workers
  shared/         # Shared utilities
  tests/          # All tests
pyproject.toml    # Single root config
uv.lock           # Lockfile (future: when uv adds lockfile support)
```

### 3. OpenAPI Client Generation: openapi-typescript-codegen

**Rationale:**
- Generates idiomatic TypeScript from OpenAPI spec
- Supports FastAPI's OpenAPI 3.1 output
- Type-safe API clients with full IntelliSense
- Active maintenance

**Workflow:**
```bash
# 1. FastAPI generates OpenAPI spec
uv run python -c "from backend.main import app; import json; print(json.dumps(app.openapi()))" > openapi.json

# 2. Generate TypeScript client
npx openapi-typescript-codegen \
  --input openapi.json \
  --output web/src/api-client \
  --client fetch

# 3. Use in React/TypeScript
import { DocumentsService } from '@/api-client';

const docs = await DocumentsService.listDocuments({ tenantId: '...' });
```

**Alternative considered:** openapi-generator (Java-based)
- **Rejected**: Slower, requires Java, more complex configuration

### 4. Task Runner: Makefile (simple) + pre-commit (git hooks)

**Makefile for common tasks:**
```makefile
.PHONY: install test lint format typecheck guardrails

install:
	uv venv
	uv pip install -e ".[dev,workers,warehouse]"

test:
	uv run pytest backend/tests/

lint:
	uv run ruff check backend/

format:
	uv run ruff format backend/

typecheck:
	uv run mypy backend/

guardrails:
	python3 .guardrails/guardrail_lint.py

openapi:
	uv run python -c "from backend.main import app; import json; print(json.dumps(app.openapi()))" > openapi.json
	cd web && npx openapi-typescript-codegen --input ../openapi.json --output src/api-client --client fetch

ci: guardrails lint typecheck test

dev:
	cd backend && uv run uvicorn main:app --reload
```

**pre-commit for git hooks:**
```yaml
# .pre-commit-config.yaml
repos:
  - repo: local
    hooks:
      - id: guardrail-lint
        name: Guardrail Lint
        entry: python3 .guardrails/guardrail_lint.py
        language: system
        pass_filenames: false
      
      - id: ruff-check
        name: Ruff Lint
        entry: uv run ruff check
        language: system
        types: [python]
      
      - id: mypy
        name: mypy Type Check
        entry: uv run mypy
        language: system
        types: [python]
        pass_filenames: false
```

**Install hooks:**
```bash
uv pip install pre-commit
pre-commit install
```

## Consequences

### Positive

- **Speed**: uv's Rust-based implementation is 10-100x faster than Poetry
- **Simplicity**: Single tool (uv) for all Python environment needs
- **Modern**: Uses standard `pyproject.toml` (PEP 621)
- **Type-safe clients**: OpenAPI codegen eliminates manual API client code
- **Enforced quality**: pre-commit hooks prevent bad commits
- **CI efficiency**: Fast dependency installation in CI

### Negative

- **uv maturity**: Newer tool (less mature than Poetry)
- **Lockfile**: uv lockfile support is evolving (may need workarounds)
- **OpenAPI codegen**: Requires Node.js in CI for TypeScript generation
- **Team learning**: Team must learn uv commands

### Mitigations

- **uv adoption risk**: uv is pip-compatible; can fall back to pip if needed
- **Lockfile workaround**: Use `uv pip freeze > requirements.lock` until native lockfile support
- **Node.js CI**: Add Node.js setup to CI workflow (standard practice)
- **Documentation**: Create runbook for common uv commands

## Alternatives Considered

### Poetry

**Pros:** Mature, widely used, good lockfile support, monorepo via workspaces  
**Cons:** Slower than uv, custom (non-PEP 621) configuration, heavier  
**Decision:** uv's speed advantage outweighs Poetry's maturity for v1

### PDM

**Pros:** PEP 621-native, good lockfile, faster than Poetry  
**Cons:** Smaller community than Poetry/uv, less momentum  
**Decision:** uv has stronger momentum and is faster

### Rye

**Pros:** All-in-one Python management (versions + deps)  
**Cons:** Less focused than uv, overlaps with pyenv  
**Decision:** uv is more focused on dependency management

### pip-tools

**Pros:** Simple, pip-native  
**Cons:** Manual compilation, no built-in virtual env management, slower  
**Decision:** uv provides better DX with comparable simplicity

### openapi-generator (Java)

**Pros:** Mature, supports many languages  
**Cons:** Requires Java, slower, more complex  
**Decision:** openapi-typescript-codegen is faster and TypeScript-native

### Custom OpenAPI client

**Pros:** Full control  
**Cons:** Massive maintenance burden, reinventing the wheel  
**Decision:** Not justified for v1

## Implementation Phases

### Phase 1: Establish Tooling

1. Create `pyproject.toml` with uv configuration
2. Define all dependencies (api, workers, warehouse, dev)
3. Configure ruff and mypy in `pyproject.toml`
4. Create Makefile with common commands
5. Set up pre-commit hooks
6. Update CI to use uv
7. Document setup in README

### Phase 1+: OpenAPI Codegen

8. Add OpenAPI generation to Makefile
9. Integrate openapi-typescript-codegen into CI
10. Generate TypeScript client on API changes
11. Document client usage for frontend team

## Open Questions

- **Monorepo split**: When to split into separate packages? (Post-v1)
- **Private package registry**: Needed for internal libraries? (Post-v1)
- **Lockfile strategy**: Wait for native uv lockfile or use workaround?

## References

- [uv Documentation](https://github.com/astral-sh/uv)
- [openapi-typescript-codegen](https://github.com/ferdikoomen/openapi-typescript-codegen)
- [PEP 621 - Storing project metadata in pyproject.toml](https://peps.python.org/pep-0621/)
- [pre-commit](https://pre-commit.com/)

---

**Recommendation**: Adopt uv + openapi-typescript-codegen as described.

**Next Steps**: Implement in Phase 1, validate with team, finalize before Phase 2.
