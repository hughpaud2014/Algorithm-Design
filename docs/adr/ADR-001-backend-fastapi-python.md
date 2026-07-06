# ADR-001: Backend Stack - FastAPI and Python

**Status**: Accepted ✓ (Locked Decision)  
**Date**: 2026-07-06  
**Deciders**: Platform Architecture Team

## Context

The Substrata platform requires a backend stack that can handle:
- Multi-tenant data isolation with strong security guarantees
- Document ingestion, parsing, and AI-powered extraction
- Complex QA/QC rule engine with cross-record and temporal validations
- Cost analysis with aggregations and anomaly detection
- Long-running automation pipelines with retry/idempotency
- Integration with customer data warehouses (Databricks, Snowflake)
- High developer productivity for rapid feature iteration

The backend must support CPU-intensive workloads (document parsing, ML inference) without blocking API request handlers, enforce strict type safety, and integrate seamlessly with modern Python data/ML tooling.

## Decision

We will use **Python 3.11+** with **FastAPI** as the core backend stack.

### Key Components

1. **API Layer**: FastAPI with Pydantic v2 for request/response validation
2. **Language**: Python 3.11+ (leveraging performance improvements)
3. **Type Safety**: Enforce `mypy --strict` across all Python code
4. **Code Quality**: Use `ruff` for linting and formatting
5. **Async Runtime**: AsyncIO for I/O-bound operations
6. **Worker Architecture**: CPU-bound work (extraction, ML) runs in separate worker processes, never inline in request handlers
7. **Frontend**: TypeScript-only for web frontend and shared schema contracts

### Boundaries

- **Python**: API server, ingestion/parsing engine, QA/QC rule engine, cost engine, automation workers, data warehouse connectors
- **TypeScript**: Web frontend, OpenAPI-generated API clients, shared type definitions

### Non-Negotiables

1. All Python code must pass `mypy --strict` type checking
2. All Python code must pass `ruff` linting with no warnings
3. CPU-intensive work must not block API request handlers
4. These checks are enforced in CI - no exceptions

## Consequences

### Positive

- **Rich Ecosystem**: Direct access to Python data/ML libraries (pandas, numpy, scikit-learn, etc.)
- **Type Safety**: Pydantic + mypy provide strong type guarantees throughout the stack
- **Developer Productivity**: FastAPI's automatic OpenAPI generation and validation reduce boilerplate
- **Performance**: Python 3.11+ provides significant performance improvements; FastAPI handles high concurrency well
- **Async Support**: Native async/await for I/O-bound operations (database, external APIs)
- **Maintainability**: Single primary language (Python) for backend simplifies hiring and context switching
- **AI Integration**: Native support for AI/ML inference libraries and vector embeddings

### Negative

- **Worker Overhead**: Separate processes for CPU-bound work adds complexity vs. threading
- **Python GIL**: Limits true parallelism within a single process (mitigated by worker architecture)
- **Type System**: Python's type system is less rigorous than Rust/Go, requires discipline with mypy --strict
- **Deployment Size**: Python runtimes and dependencies create larger container images than Go/Rust

### Mitigations

- **Worker Architecture**: Explicitly separate CPU-bound work into worker processes (Celery, Dramatiq, or similar)
- **Strict CI Gates**: Enforce `mypy --strict` and `ruff` in CI to maintain type safety and code quality
- **Async Discipline**: Use async/await for all I/O operations; never block the event loop
- **Performance Monitoring**: Instrument critical paths; optimize hot paths in Cython or Rust if needed (future)

## Alternatives Considered

### Go + Microservices

**Pros**: Better concurrency model, smaller binaries, strong typing  
**Cons**: Smaller data/ML ecosystem, more network complexity, split expertise  
**Rejected**: Operational complexity and loss of Python's data/ML tooling outweigh benefits

### Rust + Python Workers

**Pros**: Maximum performance, memory safety  
**Cons**: Steep learning curve, slower development, split language context  
**Rejected**: Premature optimization; Python 3.11+ is fast enough for v1

### Node.js/TypeScript Full-Stack

**Pros**: Single language, good async support  
**Cons**: Weak data/ML ecosystem, less suitable for numerical computing  
**Rejected**: Insufficient data science tooling for our domain

### Python + Django

**Pros**: Mature ecosystem, built-in admin  
**Cons**: Heavier framework, less async support, slower than FastAPI  
**Rejected**: FastAPI provides better async support and performance

## Implementation Notes

1. **Project Structure**: Monorepo with clear separation between API, workers, and shared libraries
2. **Dependency Management**: TBD - requires ADR (uv vs. Poetry)
3. **Database**: MongoDB for primary data store (requires separate ADR for tenancy model)
4. **Testing**: pytest with async support; enforce coverage thresholds in CI
5. **OpenAPI**: Auto-generated from FastAPI; use for frontend client generation

## References

- [FastAPI Documentation](https://fastapi.tiangolo.com/)
- [Pydantic v2](https://docs.pydantic.dev/)
- [Python 3.11 Performance Improvements](https://docs.python.org/3/whatsnew/3.11.html)
- [mypy strict mode](https://mypy.readthedocs.io/en/stable/command_line.html#cmdoption-mypy-strict)
