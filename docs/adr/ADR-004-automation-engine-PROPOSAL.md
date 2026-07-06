# ADR-004: Automation Engine Architecture

**Status**: Proposed 🔄  
**Date**: 2026-07-06  
**Deciders**: Platform Architecture Team, Infrastructure Team  
**Blocks**: Phase 6 (Automation Infrastructure)

## Context

Substrata requires a robust automation engine to orchestrate multi-step data pipelines that can run for minutes to hours. These pipelines coordinate document ingestion, AI-powered extraction, QA/QC validation, cost analysis, and warehouse export—with human-in-the-loop steps, retries, idempotency, and full observability.

### Pipeline Examples

**Document Processing Pipeline:**
1. Upload document → object storage
2. Classify document type (AI)
3. Extract fields with vendor-neutral profile
4. Normalize and validate
5. Human review/correction (wait for user input)
6. Apply QA/QC rules
7. Write to curated warehouse table
8. Send notification

**Scheduled Reference Sync (ADR-002):**
1. Check last sync time per tenant
2. Connect to customer warehouse (per-tenant creds)
3. Read reference tables (wells, AFEs, cost codes)
4. Validate schemas
5. Write to tenant-scoped cache (encrypted)
6. Update sync metadata
7. Retry on transient failures with exponential backoff

### Requirements

1. **Long-running workflows**: Support pipelines running 1 second to 24 hours
2. **Human-in-the-loop**: Pause for user input, resume when ready
3. **Idempotency**: Safe retries with no duplicate side effects
4. **Retry logic**: Automatic retry with exponential backoff for transient failures
5. **Observability**: Full visibility into pipeline state, history, and failures
6. **Replay**: Re-run historical workflows for debugging or reprocessing
7. **Scheduling**: Cron-like triggers + event-driven triggers
8. **Scalability**: Handle 10,000+ concurrent workflows across all tenants
9. **Tenant isolation**: Each tenant's workflows are isolated (security boundary)
10. **Durable state**: Workflow state persists across restarts/failures

### Current Architecture (Phase 0)

None - this is a new capability for Phase 6.

## Decision

We will use a **durable workflow engine** based on **Temporal** (or a similar orchestration framework like Prefect or Dagster).

### Why Durable Workflows?

The requirements strongly favor durable workflows over traditional task queues:

- **Long-running + human-in-the-loop**: Durable workflows natively support pausing for hours/days
- **Idempotency**: Built-in replay semantics ensure idempotent execution
- **Observability**: First-class support for workflow visualization and debugging
- **Retry logic**: Declarative retry policies with exponential backoff
- **Replay**: Workflow history enables debugging and reprocessing
- **ADR-002 sync jobs**: Scheduled warehouse sync needs exactly these capabilities

### Architecture Components

#### 1. Workflow Definitions (Python)

Workflows are Python code using the Temporal SDK:

```python
from temporalio import workflow, activity
from datetime import timedelta

@workflow.defn
class DocumentProcessingWorkflow:
    @workflow.run
    async def run(self, document_id: str, tenant_id: str) -> str:
        # Step 1: Upload to storage
        storage_url = await workflow.execute_activity(
            upload_to_storage,
            args=[document_id, tenant_id],
            start_to_close_timeout=timedelta(minutes=5),
            retry_policy=RetryPolicy(
                maximum_attempts=3,
                backoff_coefficient=2.0,
            ),
        )
        
        # Step 2: Classify document (AI, may be slow)
        doc_type = await workflow.execute_activity(
            classify_document,
            args=[storage_url, tenant_id],
            start_to_close_timeout=timedelta(minutes=10),
        )
        
        # Step 3: Extract fields
        extracted = await workflow.execute_activity(
            extract_fields,
            args=[storage_url, doc_type, tenant_id],
            start_to_close_timeout=timedelta(minutes=15),
        )
        
        # Step 4: Wait for human review (could be hours/days)
        review_signal = workflow.wait_condition(
            lambda: self.review_completed,
            timeout=timedelta(days=7),
        )
        
        # Step 5: Apply QA/QC rules
        qa_results = await workflow.execute_activity(
            apply_qa_rules,
            args=[extracted, tenant_id],
            start_to_close_timeout=timedelta(minutes=5),
        )
        
        # Step 6: Export to warehouse
        if not qa_results.has_blocking_errors:
            await workflow.execute_activity(
                export_to_warehouse,
                args=[extracted, tenant_id],
                start_to_close_timeout=timedelta(minutes=10),
            )
        
        return "completed"
    
    @workflow.signal
    async def review_complete(self, reviewed_data: dict) -> None:
        self.review_completed = True
        self.reviewed_data = reviewed_data
```

#### 2. Activities (Python)

Activities are the individual steps (stateless, idempotent):

```python
@activity.defn
async def upload_to_storage(document_id: str, tenant_id: str) -> str:
    # Idempotent: Check if already uploaded
    existing = await storage.get(document_id)
    if existing:
        return existing.url
    
    # Upload new
    doc = await db.documents.find_one({"_id": document_id, "tenantId": tenant_id})
    url = await storage.upload(doc.content, tenant_scoped=True)
    await db.documents.update_one(
        {"_id": document_id},
        {"$set": {"storageUrl": url}}
    )
    return url
```

#### 3. Temporal Server (Infrastructure)

- **Temporal Server**: Self-hosted or Temporal Cloud
- **Workers**: Python processes executing workflows/activities
- **Database**: Temporal uses PostgreSQL or Cassandra for durable state
- **Visibility**: Elasticsearch for workflow search/analytics

#### 4. Scheduling and Triggers

**Event-driven triggers:**
- Document uploaded → start processing workflow
- User submits review → signal workflow to continue
- Webhook received → start integration workflow

**Scheduled triggers (cron):**
- Every hour → sync reference data for all tenants (ADR-002)
- Daily → generate cost analysis reports
- Weekly → clean up expired cache entries

```python
# Cron schedule
@workflow.defn
class ReferenceDataSyncSchedule:
    @workflow.run
    async def run(self) -> None:
        tenants = await get_all_tenants()
        
        for tenant in tenants:
            # Start child workflow per tenant
            await workflow.start_child_workflow(
                SyncReferenceDataWorkflow,
                args=[tenant.id],
                id=f"sync-{tenant.id}-{timestamp}",
            )
```

#### 5. Tenant Isolation

Every workflow includes `tenantId` in its input and execution context:

- Workflows tagged with `tenantId` for filtering/search
- Activities enforce tenant scope (via request context)
- Observability dashboards scoped to tenant (for customer admins)
- System admins see all workflows with tenant attribution

#### 6. Observability

**Temporal Web UI** provides:
- Live workflow execution view (step-by-step progress)
- Workflow history (full event log)
- Failure analysis (stack traces, retry history)
- Search by tenant, workflow type, status, time range

**Custom dashboards** (Grafana/Datadog):
- Workflows started/completed/failed per tenant
- Average workflow duration by type
- Retry rate and failure reasons
- SLA tracking (e.g., 95% of documents processed within 10 minutes)

## Consequences

### Positive

- **Durability**: Workflows survive restarts, failures, and human-in-the-loop pauses
- **Idempotency**: Built-in replay semantics prevent duplicate side effects
- **Observability**: First-class workflow visualization and debugging
- **Retry logic**: Declarative policies reduce boilerplate
- **Testability**: Workflow replay enables deterministic testing
- **ADR-002 fit**: Perfect match for scheduled sync + retry requirements
- **Developer productivity**: Python SDK integrates with existing backend code

### Negative

- **Infrastructure overhead**: Requires Temporal Server + workers + database
- **Learning curve**: Team must learn Temporal concepts (workflows, activities, signals)
- **Operational complexity**: Additional service to monitor and scale
- **Vendor lock-in**: Temporal-specific APIs (mitigated by abstracting workflow definitions)

### Mitigations

- **Temporal Cloud option**: Reduces operational burden (managed service)
- **Training**: Dedicated onboarding for team on Temporal patterns
- **Abstraction layer**: Wrap Temporal APIs to allow future migration if needed
- **Monitoring**: Comprehensive dashboards and alerts for Temporal health

## Alternatives Considered

### Celery + Redis/RabbitMQ (Task Queue)

**Pros:** Mature, Python-native, widely used  
**Cons:** No built-in workflow orchestration; human-in-the-loop requires custom state management; limited observability; no native replay  
**Rejected:** Too much custom code for workflow orchestration and observability

### Apache Airflow

**Pros:** Mature, Python-based, DAG visualization  
**Cons:** Designed for batch ETL, not long-running human-in-the-loop workflows; heavy infrastructure; workflows are DAGs (not code)  
**Rejected:** Not designed for event-driven, long-running workflows with human interaction

### Prefect

**Pros:** Modern Python workflow engine, good observability, simpler than Temporal  
**Cons:** Smaller community, less mature for production-scale multi-tenancy  
**Decision:** **Strong alternative** - if Temporal proves too complex, Prefect is the fallback

### AWS Step Functions

**Pros:** Fully managed, no infrastructure, good AWS integration  
**Cons:** Vendor lock-in, limited Python expressiveness (JSON DSL), expensive at scale  
**Rejected:** Vendor lock-in unacceptable for open platform

### Dagster

**Pros:** Modern data orchestration, good observability, Python-native  
**Cons:** More data-pipeline-focused than workflow-focused; human-in-the-loop not first-class  
**Rejected:** Better for data engineering than operational workflows

### Custom Event-Driven State Machine

**Pros:** Full control, no external dependencies  
**Cons:** Massive engineering effort, reinventing the wheel, poor observability  
**Rejected:** Unrealistic engineering investment for v1

## Implementation Phases

### Phase 6: Core Infrastructure

1. Deploy Temporal Server (self-hosted or Cloud)
2. Implement Python workers with Temporal SDK
3. Build document processing workflow (no human-in-the-loop)
4. Integrate with FastAPI for workflow triggers
5. Basic observability (Temporal Web UI)

### Phase 6+: Advanced Features

6. Human-in-the-loop workflows (signals for review completion)
7. Scheduled workflows for reference sync (ADR-002)
8. Custom observability dashboards (Grafana/Datadog)
9. Workflow replay for debugging/reprocessing
10. Tenant-scoped observability for customer admins

### Phase 7: Warehouse Sync Integration

11. Reference data sync workflow (per ADR-002)
12. Scheduled sync triggers (cron)
13. On-demand refresh triggers (API endpoint)
14. Retry logic for warehouse connection failures

## Open Questions

- **Temporal Cloud vs. self-hosted**: Cost/benefit analysis
- **Worker scaling**: How many workers per tenant tier?
- **Workflow retention**: How long to keep workflow history?
- **Replay policies**: When to allow admin replay of workflows?

## Security Considerations

- **Tenant isolation**: Workflows tagged with `tenantId`; activities enforce scope
- **Credentials**: Warehouse credentials (ADR-002) passed securely to activities
- **Audit trail**: Workflow history provides full audit of automated actions
- **Rate limiting**: Per-tenant limits on concurrent workflows

## References

- [Temporal.io](https://temporal.io/)
- [Prefect](https://www.prefect.io/)
- [Dagster](https://dagster.io/)
- ADR-002: Warehouse Connectors (scheduled sync use case)

---

**Recommendation**: Use Temporal for durable workflows with Prefect as fallback.

**Next Steps**: Prototype with Temporal, evaluate complexity, approve before Phase 6 implementation.
