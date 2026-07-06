"""
Tests for main API application.

Phase 0: Basic health check test.
"""

import pytest
from fastapi.testclient import TestClient
from backend.main import app

client = TestClient(app)


def test_health_check() -> None:
    """Test health check endpoint returns expected response."""
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "healthy"
    assert data["version"] == "0.1.0"
    assert data["phase"] == "Phase 0 - Foundation"


def test_root() -> None:
    """Test root endpoint returns API information."""
    response = client.get("/")
    assert response.status_code == 200
    data = response.json()
    assert "message" in data
    assert "docs" in data
    assert "health" in data


def test_tenant_info_with_header() -> None:
    """Test tenant info endpoint with valid header (Phase 0 placeholder)."""
    response = client.get(
        "/api/v1/tenant-info",
        headers={"x-tenant-id": "test-tenant-123"}
    )
    assert response.status_code == 200
    data = response.json()
    assert data["tenant_id"] == "test-tenant-123"
    assert "warning" in data


def test_tenant_info_without_header() -> None:
    """Test tenant info endpoint rejects requests without tenant header."""
    response = client.get("/api/v1/tenant-info")
    assert response.status_code == 400
    assert "x-tenant-id" in response.json()["detail"].lower()
