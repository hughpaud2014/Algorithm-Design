# ADR-007: Reference Data Cache Store

**Status**: Proposed 🔄  
**Date**: 2026-07-06  
**Deciders**: Platform Architecture Team, Infrastructure Team  
**Blocks**: Phase 7 (Enterprise Connectors)

## Context

Per ADR-002 (Warehouse Connectors), Substrata implements a **hybrid architecture** for reading customer reference data from warehouses (Databricks, Snowflake):

1. **Scheduled sync** pulls reference tables (wells, AFEs, cost codes, org hierarchies)
2. **Tenant-scoped encrypted cache** stores synced data with configurable TTL
3. **On-demand refresh** allows manual cache refresh per reference table
4. **Instant revoke-purge** deletes all cached data when customer revokes access

The cache is **in-scope customer data**, subject to:
- Tenant isolation (security boundary)
- Encryption at rest
- Retention policies
- Data residency requirements
- Audit logging

### Requirements

**Functional:**
- Store structured reference data (tabular: columns + rows)
- Per-tenant isolation (enforced at query time)
- Per-table TTL configuration (15 minutes to 24 hours)
- Fast reads (sub-10ms for typical queries)
- Support for filters, projections, and sorting
- Purge all data for a tenant (revoke scenario)
- Purge expired entries (TTL enforcement)

**Non-Functional:**
- **Performance**: 10,000+ reads/sec across all tenants
- **Storage**: Support up to 10 GB per tenant (100 GB total for 10 tenants)
- **Latency**: p99 read latency < 50ms
- **Availability**: 99.9% uptime (cache misses fall back to warehouse query)
- **Encryption**: At-rest encryption with tenant-specific keys
- **Audit**: All cache operations logged (sync, refresh, purge, read)

**Data Characteristics:**
- **Reference data**: Wells (10K rows), AFEs (5K rows), cost codes (1K rows), orgs (500 rows)
- **Update frequency**: Hourly sync (configurable)
- **Read pattern**: High read, low write (10,000:1 ratio)
- **Schema**: Semi-structured (column names + types + rows)

### Cache Schema

Each cached reference table is stored with metadata:

```typescript
{
  cacheId: string,              // Unique cache entry ID
  tenantId: string,             // Tenant scope
  referenceType: string,        // "wells" | "afes" | "cost_codes" | "orgs"
  warehouseTable: string,       // Source table name
  syncedAt: Date,               // Last sync timestamp
  expiresAt: Date,              // TTL expiration
  rowCount: number,             // Number of rows cached
  schema: {                     // Column definitions
    columns: [
      { name: string, type: string, nullable: boolean }
    ]
  },
  data: Array<Record<string, any>>,  // Actual rows
  checksumSHA256: string,       // Data integrity check
}
```

## Decision

We will use **MongoDB** as the reference data cache store, leveraging the existing platform database.

### Rationale

**MongoDB is already the primary platform database** (per ADR-001 context). Using it for reference cache provides:

1. **Operational simplicity**: One database to manage, monitor, backup
2. **Tenant isolation**: Same isolation patterns as other platform data (tenantId + indexes)
3. **Schema flexibility**: Semi-structured reference data fits document model
4. **Query capabilities**: Rich querying (filters, projections, sort) without N+1 queries
5. **TTL indexes**: Native TTL support (`expireAfterSeconds`)
6. **Encryption**: MongoDB at-rest encryption with tenant-scoped keys (via KMIP or AWS KMS)
7. **Audit trail**: Same audit logging infrastructure as other collections

### Architecture

#### 1. MongoDB Collection: `reference_cache`

**Document structure:**
```json
{
  "_id": ObjectId("..."),
  "cacheId": "acme-oil-wells-2026-07-06T10:00:00Z",
  "tenantId": "acme-oil",
  "referenceType": "wells",
  "warehouseTable": "prod.reference.wells",
  "syncedAt": ISODate("2026-07-06T10:00:00Z"),
  "expiresAt": ISODate("2026-07-06T11:00:00Z"),  // TTL = 1 hour
  "rowCount": 1250,
  "schema": {
    "columns": [
      {"name": "well_id", "type": "string", "nullable": false},
      {"name": "well_name", "type": "string", "nullable": false},
      {"name": "api_number", "type": "string", "nullable": true},
      {"name": "operator", "type": "string", "nullable": true},
      {"name": "status", "type": "string", "nullable": false}
    ]
  },
  "data": [
    {"well_id": "W001", "well_name": "Permian-1", "api_number": "42-123-45678", "operator": "Operator A", "status": "active"},
    {"well_id": "W002", "well_name": "Permian-2", "api_number": "42-123-45679", "operator": "Operator B", "status": "active"}
    // ... 1,248 more rows
  ],
  "checksumSHA256": "a3f5c8..."
}
```

**Indexes:**
```javascript
// Tenant + reference type lookup (primary query pattern)
db.reference_cache.createIndex({ tenantId: 1, referenceType: 1 });

// TTL index (automatic expiration)
db.reference_cache.createIndex({ expiresAt: 1 }, { expireAfterSeconds: 0 });

// Tenant isolation (enforce in queries)
db.reference_cache.createIndex({ tenantId: 1 });
```

#### 2. Cache Operations

**Sync reference table (scheduled or on-demand):**
```python
async def sync_reference_table(
    tenant_id: str,
    reference_type: str,
    ttl_seconds: int
) -> CacheEntry:
    # 1. Fetch from warehouse
    warehouse_conn = await get_warehouse_connection(tenant_id)
    table_mapping = await get_reference_mapping(tenant_id, reference_type)
    
    rows = await warehouse_conn.query(f"SELECT * FROM {table_mapping.table_name}")
    schema = infer_schema(rows)
    
    # 2. Create cache entry
    cache_entry = {
        "cacheId": f"{tenant_id}-{reference_type}-{datetime.now().isoformat()}",
        "tenantId": tenant_id,
        "referenceType": reference_type,
        "warehouseTable": table_mapping.table_name,
        "syncedAt": datetime.now(),
        "expiresAt": datetime.now() + timedelta(seconds=ttl_seconds),
        "rowCount": len(rows),
        "schema": schema,
        "data": rows,
        "checksumSHA256": compute_sha256(rows)
    }
    
    # 3. Upsert into cache (replace existing)
    await db.reference_cache.replace_one(
        {"tenantId": tenant_id, "referenceType": reference_type},
        cache_entry,
        upsert=True
    )
    
    # 4. Audit log
    await audit_log.insert({
        "tenantId": tenant_id,
        "action": "reference_sync",
        "referenceType": reference_type,
        "rowCount": len(rows),
        "timestamp": datetime.now()
    })
    
    return cache_entry
```

**Read from cache:**
```python
async def get_reference_data(
    tenant_id: str,
    reference_type: str,
    filters: dict | None = None
) -> list[dict]:
    # 1. Fetch cache entry
    cache_entry = await db.reference_cache.find_one({
        "tenantId": tenant_id,
        "referenceType": reference_type
    })
    
    if not cache_entry:
        raise CacheMissError(f"No cache for {reference_type}")
    
    # 2. Check TTL (MongoDB auto-deletes, but double-check)
    if cache_entry["expiresAt"] < datetime.now():
        raise CacheExpiredError(f"Cache expired for {reference_type}")
    
    # 3. Apply filters (in-memory for simplicity; could use aggregation)
    data = cache_entry["data"]
    
    if filters:
        data = [row for row in data if matches_filters(row, filters)]
    
    return data
```

**Purge tenant cache (revoke scenario):**
```python
async def purge_tenant_cache(tenant_id: str) -> int:
    # 1. Delete all cache entries for tenant
    result = await db.reference_cache.delete_many({"tenantId": tenant_id})
    
    # 2. Audit log
    await audit_log.insert({
        "tenantId": tenant_id,
        "action": "cache_purge",
        "deletedEntries": result.deleted_count,
        "timestamp": datetime.now()
    })
    
    # 3. Verify deletion (security requirement)
    remaining = await db.reference_cache.count_documents({"tenantId": tenant_id})
    
    if remaining > 0:
        raise SecurityError(f"Purge failed: {remaining} entries remain for {tenant_id}")
    
    return result.deleted_count
```

#### 3. TTL Configuration

**Per-reference-type defaults:**
```python
DEFAULT_TTL_SECONDS: dict[str, int] = {
    "wells": 3600,           # 1 hour (relatively stable)
    "afes": 1800,            # 30 minutes (changes frequently)
    "cost_codes": 7200,      # 2 hours (rarely changes)
    "orgs": 14400,           # 4 hours (rarely changes)
}

# Tenant admins can override per reference type
async def set_custom_ttl(
    tenant_id: str,
    reference_type: str,
    ttl_seconds: int
) -> None:
    # Validate range (minimum 15 minutes, maximum 24 hours)
    if ttl_seconds < 900 or ttl_seconds > 86400:
        raise ValueError("TTL must be between 15 minutes and 24 hours")
    
    await db.tenant_settings.update_one(
        {"tenantId": tenant_id},
        {"$set": {f"referenceTTL.{reference_type}": ttl_seconds}},
        upsert=True
    )
```

#### 4. Encryption

**MongoDB at-rest encryption:**
- Use MongoDB native encryption (Enterprise or Atlas)
- Encrypt entire `reference_cache` collection
- Optional: Tenant-specific encryption keys (via AWS KMS or KMIP)

**Encryption at field level (if needed):**
- Encrypt sensitive columns in `data` array (e.g., personal info)
- Use MongoDB Client-Side Field Level Encryption (CSFLE)

## Consequences

### Positive

- **Operational simplicity**: One database, one backup strategy, one monitoring dashboard
- **Tenant isolation**: Same patterns as other platform data (proven in Phase 2)
- **Query flexibility**: MongoDB's rich query language supports filters/projections/sort
- **TTL support**: Native TTL indexes auto-delete expired entries
- **Schema flexibility**: Document model fits semi-structured reference data
- **Cost efficiency**: No additional database service to pay for
- **Audit integration**: Same audit logging as other collections

### Negative

- **Not purpose-built for caching**: MongoDB is not as fast as Redis for pure key-value
- **In-memory limitations**: Large reference tables (10K+ rows) are stored on disk, not RAM
- **Query performance**: Filtering/sorting happens in MongoDB query engine (not application memory)
- **Storage overhead**: BSON format adds ~20% overhead vs. raw JSON

### Mitigations

- **Indexes**: Compound indexes on `tenantId` + `referenceType` ensure fast lookups
- **Data size limits**: Enforce max 10 GB per tenant to prevent performance degradation
- **Monitoring**: Alert on slow queries or large cache entries
- **Fallback**: On cache miss, fall back to direct warehouse query

## Alternatives Considered

### Redis

**Pros:** Purpose-built for caching, in-memory performance, TTL support  
**Cons:** Additional service to manage, limited query capabilities (no filters/sort), tenant isolation requires key prefixing  
**Decision:** MongoDB's operational simplicity outweighs Redis's speed for v1

### PostgreSQL

**Pros:** Strong query capabilities, JSONB for semi-structured data  
**Cons:** Additional service to manage, less natural for document-centric data  
**Decision:** MongoDB already primary database; no need for second SQL database

### DynamoDB

**Pros:** Fully managed, auto-scaling, TTL support  
**Cons:** AWS lock-in, limited query capabilities (no filters without secondary indexes), expensive  
**Decision:** Vendor lock-in and cost unacceptable for open platform

### Embedded Cache (in-memory in application)

**Pros:** Fastest possible (no network hop)  
**Cons:** Cache not shared across API replicas, high memory usage, no durability  
**Decision:** Not viable for multi-replica API deployment

### Redis + MongoDB (Hybrid)

**Pros:** Redis for hot data, MongoDB for fallback  
**Cons:** Two systems to manage, cache invalidation complexity  
**Decision:** Over-engineered for v1; revisit if performance becomes issue

## Implementation Phases

### Phase 7: Core Cache

1. Create `reference_cache` collection with indexes
2. Implement sync logic (warehouse → MongoDB)
3. Implement read logic (MongoDB → API)
4. Configure per-type default TTLs
5. Test cache miss fallback to warehouse

### Phase 7+: Advanced Features

6. Tenant admin UI for custom TTL configuration
7. On-demand refresh button per reference type
8. Purge workflow (revoke scenario)
9. Encryption at rest (MongoDB native)
10. Cache observability dashboard (hit rate, sync latency, storage)

## Open Questions

- **Cache warming**: Pre-warm cache on tenant onboarding?
- **Cache versioning**: Keep multiple versions of cache for rollback?
- **Max row limit**: Enforce limit on reference table size (e.g., 100K rows)?
- **Compression**: Compress `data` array to save storage?

## Security Considerations

- **Tenant isolation**: Enforced via `tenantId` in queries (same as other collections)
- **Encryption**: At-rest encryption with tenant-scoped keys (optional)
- **Audit trail**: All sync/refresh/purge operations logged
- **Purge verification**: Automated test confirms zero rows remain after purge

## References

- ADR-002: Warehouse Connector Architecture
- [MongoDB TTL Indexes](https://www.mongodb.com/docs/manual/core/index-ttl/)
- [MongoDB Encryption at Rest](https://www.mongodb.com/docs/manual/core/security-encryption-at-rest/)

---

**Recommendation**: Use MongoDB for reference data cache as described.

**Next Steps**: Validate with performance testing, approve before Phase 7 implementation.
