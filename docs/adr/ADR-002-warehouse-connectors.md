# ADR-002: Warehouse Connector Architecture

**Status**: Accepted ✓ (Locked Decision)  
**Date**: 2026-07-06  
**Deciders**: Platform Architecture Team, Security Team

## Context

Substrata must integrate with customer-owned data warehouses (Databricks, Snowflake) to:
1. **Read customer reference data** (well lists, AFE budgets, vendor master, org hierarchies, cost codes)
2. **Write curated outputs** (validated documents, QA/QC results, cost analysis, aggregated metrics)

Customers require:
- **Data ownership**: Warehouse data stays in customer's account
- **Least-privilege access**: Separate read/write permissions
- **Revocability**: Instant revoke + purge of cached data
- **Auditability**: Full trail of what was accessed and when
- **Residency/compliance**: Some reference data must stay in customer's region

Traditional "read on every query" approaches create:
- **Performance issues**: Repeated warehouse queries slow down UI/API
- **Cost issues**: Warehouse queries are expensive (Databricks DBU, Snowflake credits)
- **Availability coupling**: Platform becomes unavailable when warehouse is down

Traditional "sync everything" approaches create:
- **Data volume issues**: Large reference tables consume platform storage
- **Freshness issues**: Long sync cycles mean stale data
- **Isolation risk**: Copying data increases attack surface

## Decision

We will implement a **hybrid architecture** combining:
1. **Curated push** for Substrata outputs (write-only)
2. **Scheduled sync + on-demand refresh** for customer reference data (read-only with encrypted cache)

### Architecture Components

#### 1. Curated Push (Substrata → Warehouse)

- Substrata writes **curated datasets** to customer-specified warehouse tables
- **Write-only credential**: Warehouse service account with `INSERT`/`CREATE TABLE` on target schema only
- **Schema-versioned tables**: Each table includes `schema_version` and `export_run_id`
- **Audit trail**: Every write logged in `ExportRun` with timestamp, user, records, schema version
- **No reads**: Substrata never reads back data it wrote (append-only)

**Target schemas** (customer-managed):
- `substrata_curated.documents` - Validated documents with lineage
- `substrata_curated.qa_results` - QA/QC findings with severity
- `substrata_curated.cost_analysis` - Line items, rollups, variance
- `substrata_curated.audit_trail` - State changes, approvals, corrections

#### 2. Reference Data Sync (Warehouse → Substrata Cache)

- Customer reference data (wells, AFEs, cost codes) **synced into tenant-scoped encrypted cache**
- **Read-only credential**: Warehouse service account with `SELECT` on mapped reference schemas only
- **Mapping step**: Customer admin maps warehouse tables to Substrata reference types
- **Scheduled sync**: Configurable interval (default 1 hour)
- **On-demand refresh**: Manual "sync now" button per reference table
- **TTL per table**: Customer configures data freshness requirements (15 min to 24 hours)
- **Encryption**: Cache encrypted at rest with tenant-specific keys
- **In-scope data**: Cache is considered customer data - subject to isolation, retention, residency, revoke-purge

### Credentials Management

- **Two separate least-privilege service accounts**:
  1. **Read-only** for reference table sync (`SELECT` on mapped schemas)
  2. **Write-only** for curated exports (`INSERT`/`CREATE TABLE` on target schema)
- **Secrets vault**: Credentials stored in vault (HashiCorp Vault, AWS Secrets Manager, or similar)
- **Per-tenant isolation**: Each tenant's credentials are isolated, never shared
- **Revoke path**: Instant revoke + purge workflow

### Revoke and Purge

When a customer revokes warehouse access:
1. **Immediate**: Credentials deleted from vault, no further sync/export
2. **Audit log**: Revoke action logged with timestamp and user
3. **Cache purge**: Reference data cache deleted within 5 minutes
4. **Purge verification**: Automated test confirms deletion
5. **Export halt**: No further curated push; in-flight exports fail

## Consequences

### Positive

- **Customer control**: Warehouse data stays in customer account; instant revoke
- **Least-privilege**: Separate read/write credentials reduce blast radius
- **Performance**: Cached reference data eliminates repeated warehouse queries
- **Cost efficiency**: Scheduled sync reduces warehouse query costs
- **Auditability**: Full trail of all warehouse operations
- **Availability**: Platform remains functional during warehouse outages (uses cache)
- **Security**: Cache encrypted, scoped to tenant, TTL'd

### Negative

- **Complexity**: Hybrid model is more complex than pure read/write
- **Cache staleness**: Reference data can be out of sync (mitigated by TTL + on-demand refresh)
- **Storage overhead**: Reference cache consumes platform storage (in-scope customer data)
- **Sync failures**: Warehouse downtime during scheduled sync delays freshness
- **Mapping burden**: Customer admin must map reference tables (one-time setup)

### Mitigations

- **Clear TTL controls**: Customer sets acceptable staleness per reference table
- **On-demand refresh**: "Sync now" button for critical workflows
- **Sync observability**: Dashboard shows last sync time, next sync, status
- **Retry logic**: Failed syncs retry with exponential backoff
- **Storage quotas**: Per-tenant reference cache limits prevent abuse
- **Automated purge**: Scheduled cleanup of expired cache entries

## Security Considerations

### Threat: Cross-Tenant Reference Data Leakage

**Mitigation**: Cache entries tagged with `tenantId`; all queries enforce tenant scope; automated isolation tests in CI

### Threat: Credential Compromise

**Mitigation**: Least-privilege service accounts; vault storage; automatic rotation (future); instant revoke workflow

### Threat: Cache Poisoning

**Mitigation**: Sync validates data types/schemas before cache write; source lineage tracked

### Threat: Retention/Residency Violation

**Mitigation**: Cache is in-scope customer data; revoke-purge verified in audit; TTL enforced

### Threat: Export Data Exfiltration

**Mitigation**: Export runs audited with full lineage; export targets are customer-controlled warehouse tables

## Implementation Phases

### Phase 7 - Initial Connector

1. Secrets vault integration
2. Customer admin UI for warehouse connection setup
3. Reference table mapping UI
4. Scheduled sync job (basic)
5. Cache storage (MongoDB collection per tenant)
6. Curated push with `ExportRun` audit

### Phase 7 - Hardening

7. On-demand refresh UI
8. Per-table TTL configuration
9. Revoke + purge workflow with verification
10. Sync observability dashboard
11. Credential rotation (manual)

### Future Enhancements

- Automatic credential rotation
- Multi-region cache for data residency
- Incremental sync (delta detection)
- Schema drift detection and alerts
- Warehouse query pushdown (filter/aggregate in warehouse)

## Alternatives Considered

### Direct Query (No Cache)

**Pros**: Always fresh, no sync complexity, no cache storage  
**Cons**: Slow, expensive, availability coupling, scales poorly  
**Rejected**: Performance and cost unacceptable for production

### Full Replication

**Pros**: Fast reads, simple model  
**Cons**: Huge storage overhead, long sync cycles, stale data  
**Rejected**: Storage cost and freshness issues

### Federated Query Engine

**Pros**: Real-time joins across warehouse and platform  
**Cons**: High complexity, vendor lock-in (Trino/Presto), expensive  
**Rejected**: Over-engineered for v1; revisit post-GA

### Customer-Hosted Agents

**Pros**: No credential handoff, customer maintains connector  
**Cons**: Deployment burden, support complexity, harder onboarding  
**Rejected**: Increases customer operational burden; SaaS model preferred

## Open Questions (Require Future ADRs)

- **Cache storage technology**: MongoDB vs. Redis vs. DynamoDB (blocked Phase 7 implementation)
- **Default TTL values**: Per reference table type (wells = 1 hour, cost codes = 4 hours?)
- **Credential rotation cadence**: Manual vs. 90-day auto-rotation
- **Multi-region caching**: For data residency requirements (post-GA)

## References

- [Databricks Service Principals](https://docs.databricks.com/en/administration-guide/users-groups/service-principals.html)
- [Snowflake Service Accounts](https://docs.snowflake.com/en/user-guide/service-accounts)
- [HashiCorp Vault](https://www.vaultproject.io/)
- Threat model: `docs/security/threat-model.md` (TBD Phase 1)
