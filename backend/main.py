"""
Substrata Platform - Main API Application

FastAPI-based REST API for multi-tenant data intelligence platform.
"""

from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel
from typing import Annotated

app = FastAPI(
    title="Substrata Platform API",
    description="Multi-tenant data intelligence platform for oilfield operations",
    version="0.1.0",
)


class HealthResponse(BaseModel):
    """Health check response model."""
    status: str
    version: str
    phase: str


@app.get("/health", response_model=HealthResponse)
async def health_check() -> HealthResponse:
    """
    Health check endpoint.
    
    Returns platform status and version information.
    """
    return HealthResponse(
        status="healthy",
        version="0.1.0",
        phase="Phase 0 - Foundation"
    )


@app.get("/")
async def root() -> dict[str, str]:
    """Root endpoint with API information."""
    return {
        "message": "Substrata Platform API",
        "docs": "/docs",
        "health": "/health"
    }


# Phase 0: Placeholder tenant context
# NOTE: This header-trust approach is TEMPORARY and will be replaced
# in Phase 2 with auth-derived tenant scope. Security invariant:
# no production deployment with header-trust tenant context.
@app.get("/api/v1/tenant-info")
async def get_tenant_info(
    x_tenant_id: Annotated[str | None, Header()] = None
) -> dict[str, str | None]:
    """
    PHASE 0 PLACEHOLDER: Returns tenant context from header.
    
    WARNING: This endpoint trusts client-provided tenant ID and MUST be
    replaced in Phase 2 with auth-derived tenant scope. This is a
    TEMPORARY scaffold for Phase 0 only.
    """
    if not x_tenant_id:
        raise HTTPException(
            status_code=400,
            detail="x-tenant-id header required (Phase 0 placeholder)"
        )
    
    return {
        "tenant_id": x_tenant_id,
        "warning": "Phase 0 placeholder - header-trust will be removed in Phase 2"
    }
