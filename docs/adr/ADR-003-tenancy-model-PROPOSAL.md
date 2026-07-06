# ADR-003: Tenancy Model and Data Isolation Architecture

**Status**: Proposed 🔄  
**Date**: 2026-07-06  
**Deciders**: Platform Architecture Team, Security Team  
**Blocks**: Phase 2 (Core Platform)

## Context

Substrata is a multi-tenant SaaS platform where tenant isolation is a **security boundary**. The system must guarantee that no customer can ever see, query, or infer data outside their organization tree. This constraint must be enforced at the data layer, API layer, and UI layer by construction, with no code path that queries data without a resolved tenant + org scope.

### Requirements

1. **Security**: Zero cross-tenant data leakage (by construction, not just by convention)
2. **Performance**: Sub-100ms query response for typical document/rule lookups
3. **Scale**: Support 1,000+ tenants with varying data volumes (10 GB to 10 TB per tenant)
4. **Compliance**: Tenant-specific data residency (some customers require region-specific storage)
5. **Operations**: Simple backup/restore per tenant; easy tenant data export/purge
6. **Cost**: Reasonable infrastructure cost scaling with tenant count

### Tenant Hierarchy

Substrata uses a hierarchical organization model:
- **Tenant** (root): Top-level customer account
- **Organizations** (tree): Nested business units, projects, or cost centers
- **Users**: Belong to one or more organizations within a tenant

All data is scoped to `tenantId` + `orgPath` (e.g., `tenant123/us-ops/permian-basin`).

### Data Access Patterns

- **High frequency**: Document retrieval, rule lookup, reference data reads (cache hits)
- **Medium frequency**: QA/QC execution, cost analysis aggregations
- **Low frequency**: Warehouse sync, export runs, admin operations
- **Isolation boundary**: Every query includes `{ tenantId: "...", orgPath: { $regex: "^..." } }`

## Decision

We will implement a **hybrid tenancy model**:

### Default: Shared Collections with Tenant/Org Scoping

**For most tenants (Standard/Professional tiers):**
- All tenant data lives in **shared MongoDB collections**
- Every document includes `tenantId` (indexed) and `orgPath` (indexed)
- Middleware enforces tenant/org scope on **every** database query
- Compound indexes: `{ tenantId: 1, orgPath: 1, ... }`

**Example document structure:**
```typescript
{
  _id: ObjectId("..."),
  tenantId: "acme-oil",
  orgPath: "/us-ops/permian",
  // ... domain fields
  createdAt: ISODate("..."),
  updatedAt: ISODate("...")
}
```

### Premium: Dedicated Database per Tenant

**For top-tier enterprise tenants (Enterprise+ tier):**
- Each tenant gets a **dedicated MongoDB database**
- Format: `substrata_tenant_<tenantId>`
- Same schema structure (still includes `tenantId` for consistency)
- Complete physical isolation at the database level
- Enables tenant-specific:
  - Backup schedules
  - Data residency (different MongoDB clusters per region)
  - Performance tuning (dedicated indexes, sharding)
  - Compliance requirements (encryption, audit granularity)

### Migration Path

- Tenants can upgrade from shared → dedicated (data migration)
- Tenants can downgrade from dedicated → shared (with agreement and migration)
- Migration is a scheduled maintenance operation with zero data loss

## Implementation Details

### Shared Collections Strategy

**Middleware enforcement:**
```python
class TenantScopeMiddleware:
    async def __call__(self, request: Request, call_next):
        # Extract tenant context from verified auth token (Phase 2+)
        tenant_ctx = extract_tenant_from_auth(request)
        request.state.tenant = tenant_ctx
        return await call_next(request)

def get_tenant_context(request: Request) -> TenantContext:
    """Required for all database operations."""
    if not hasattr(request.state, "tenant"):
        raise SecurityError("No tenant context - isolation violation")
    return request.state.tenant

async def find_documents(
    request: Request,
    filters: dict
) -> list[Document]:
    ctx = get_tenant_context(request)
    # ALWAYS inject tenant/org scope
    scoped_filters = {
        "tenantId": ctx.tenant_id,
        "orgPath": {"$regex": f"^{ctx.org_path}"},
        **filters
    }
    return await db.documents.find(scoped_filters).to_list()
```

**Database indexes:**
```javascript
// Every collection with tenant data
db.documents.createIndex({ tenantId: 1, orgPath: 1, createdAt: -1 });
db.qa_rules.createIndex({ tenantId: 1, orgPath: 1, ruleType: 1 });
db.cost_items.createIndex({ tenantId: 1, orgPath: 1, date: -1 });
```

### Dedicated Database Strategy

**Tenant database resolver:**
```python
def get_database(tenant_id: str) -> Database:
    tenant_config = get_tenant_config(tenant_id)
    
    if tenant_config.isolation_tier == "dedicated":
        db_name = f"substrata_tenant_{tenant_id}"
        cluster = tenant_config.cluster  # May be region-specific
        return mongo_clients[cluster][db_name]
    else:
        return mongo_clients["shared"]["substrata"]
```

**Same query API:**
```python
# Application code doesn't change
async def find_documents(request: Request, filters: dict):
    ctx = get_tenant_context(request)
    db = get_database(ctx.tenant_id)  # Automatic routing
    
    scoped_filters = {
        "tenantId": ctx.tenant_id,  # Still included for consistency
        "orgPath": {"$regex": f"^{ctx.org_path}"},
        **filters
    }
    return await db.documents.find(scoped_filters).to_list()
```

### Collections and Tenancy

**Tenant-scoped collections** (require `tenantId` + `orgPath`):
- `documents` - Uploaded/parsed documents
- `qa_rules` - QA/QC rules and rule sets
- `cost_items` - Line items and aggregations
- `export_runs` - Warehouse export audit trail
- `reference_cache` - Cached customer reference data (ADR-002)
- `audit_log` - State changes and actions

**System collections** (no tenant scope):
- `tenants` - Tenant metadata and configuration
- `users` - User accounts (reference `tenantId`)
- `auth_sessions` - Authentication sessions

## Consequences

### Positive

- **Security**: Middleware + database-level enforcement = defense in depth
- **Flexibility**: Can offer different isolation tiers based on customer needs
- **Performance**: Shared model scales well for most tenants; dedicated for high-volume
- **Compliance**: Dedicated databases enable region-specific data residency
- **Cost efficiency**: Shared model keeps costs low for standard tiers
- **Operational flexibility**: Can tune dedicated databases per tenant

### Negative

- **Complexity**: Two code paths (shared vs. dedicated) increase surface area
- **Migration overhead**: Tenant upgrades require data migration
- **Schema evolution**: Schema changes must work for both models
- **Monitoring**: Need to monitor both shared and per-tenant metrics

### Mitigations

- **Unified API**: Application code uses same query interface regardless of model
- **Automated migration**: Scripted tenant data migration with validation
- **Schema versioning**: All schema changes versioned and tested against both models
- **Comprehensive monitoring**: Dashboard covering all tenants (shared + dedicated)

## Security Analysis

### Threat: Cross-Tenant Data Leakage (Shared Model)

**Attack vectors:**
1. Query without tenant scope injection
2. Tenant ID spoofing in request
3. Index scan bypassing tenant filter
4. Backup restoration with wrong tenant data

**Mitigations:**
1. Middleware enforces tenant scope on **every** query; automated tests prove no unscoped queries exist
2. Tenant context derived from verified auth token (Phase 2+), never from client input
3. Compound indexes always include `tenantId` as first field
4. Backup/restore procedures validate tenant scope before restoration

### Threat: Tenant Enumeration

**Attack vector:** Attacker infers existence/names of other tenants from error messages or timing

**Mitigation:** Generic error messages; constant-time tenant lookups; no tenant info in public endpoints

### Threat: Noisy Neighbor (Shared Model)

**Attack vector:** One tenant's high query volume degrades performance for others

**Mitigation:** Per-tenant rate limiting; query timeout enforcement; proactive monitoring for upgrade to dedicated

### Threat: Data Residency Violation

**Attack vector:** Tenant data stored in wrong geographic region

**Mitigation:** Dedicated databases for compliance-sensitive tenants; cluster assignment per tenant config

## Alternatives Considered

### Database-per-Tenant for All

**Pros:** Maximum isolation, no cross-tenant leakage risk  
**Cons:** High operational overhead (1,000+ databases), expensive, schema evolution complexity  
**Rejected:** Cost and operational burden too high for standard tiers

### Tenant-per-Collection

**Pros:** Strong isolation, simpler than DB-per-tenant  
**Cons:** MongoDB collection limits (thousands per DB), schema evolution harder  
**Rejected:** Doesn't scale to thousands of tenants

### Separate MongoDB Clusters per Tenant

**Pros:** Ultimate isolation and performance  
**Cons:** Prohibitively expensive, massive operational burden  
**Rejected:** Only viable for very few, very large customers

### Schema-Based Multi-Tenancy (PostgreSQL)

**Pros:** Strong isolation, SQL benefits  
**Cons:** We're using MongoDB (ADR-001), schemas are rigid for document-centric data  
**Rejected:** Not applicable with locked MongoDB decision

## Implementation Phases

### Phase 2: Shared Model Only

1. Middleware enforcing tenant scope on all queries
2. Compound indexes with `tenantId` + `orgPath`
3. Automated isolation tests (no cross-tenant access)
4. Tenant metadata collection

### Phase 2+: Add Dedicated Model

5. Database resolver based on tenant configuration
6. Tenant upgrade/downgrade workflow
7. Data migration tooling
8. Multi-cluster support for data residency

## Open Questions

- **Default TTL for tenant data**: How long to retain after account closure?
- **Shared collection sharding**: Shard key strategy (`tenantId` + what?)
- **Backup granularity for shared**: Per-tenant restore from shared backup?

## References

- [MongoDB Multi-Tenancy Patterns](https://www.mongodb.com/docs/manual/core/security-multi-tenancy/)
- Threat model: `docs/security/threat-model.md` (TBD Phase 1)
- OWASP Multi-Tenancy Cheat Sheet

---

**Recommendation**: Implement hybrid model as described. Start with shared (Phase 2), add dedicated support (Phase 2+).

**Next Steps**: Review with security team, run isolation test design review, approve before Phase 2 implementation.
