# Substrata Platform - Backend

Python-based backend for the Substrata platform using FastAPI.

## Phase 0 Status

This directory contains the initial FastAPI scaffold. In Phase 0, the API provides:
- Health check endpoint
- Basic tenant context placeholder (header-trust, to be replaced in Phase 2)

## Structure

```
backend/
├── main.py          # FastAPI application entry point
├── __init__.py      # Package initialization
└── README.md        # This file
```

## Running Locally (Phase 0)

Install dependencies:
```bash
pip install fastapi uvicorn pydantic
```

Run the development server:
```bash
cd backend
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

Access the API:
- Health check: http://localhost:8000/health
- Interactive docs: http://localhost:8000/docs
- OpenAPI spec: http://localhost:8000/openapi.json

## Phase 0 Limitations

**SECURITY WARNING**: The current implementation uses header-trust for tenant context (`x-tenant-id` header). This is a **temporary scaffold** and violates security invariants. Phase 2 will replace this with auth-derived tenant scope from a verified principal.

**Do not deploy Phase 0 to production.**

## Next Steps (Phase 1+)

- Resolve packaging ADR (uv vs. Poetry)
- Set up proper dependency management
- Configure mypy --strict and ruff
- Establish test framework (pytest)
- Add CI configuration
