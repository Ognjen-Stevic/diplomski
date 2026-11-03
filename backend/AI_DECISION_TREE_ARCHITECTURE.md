# Customer risk analysis: implementation and architecture

## Business problem and scope

This module estimates a categorical **60-day ordering outcome for recently active customers**. It does not predict insolvency, financial loss, calibrated probabilities, or the causal effectiveness of a sales intervention. Recommendations are a separate explicit policy, not learned treatment effects.

The initial operational definition is a proposal that needs business validation: LOW = continued volume, MEDIUM = future paid volume below half the preceding 60-day volume, HIGH = no subsequent completed paid orders observed within 60 days. Insufficient information is not a learned fourth class.

On 2026-10-23 the local database contained 159 customers, 402 COMPLETED/NORMAL requisitions (March–October 2026), and 51 COMPLETED/GRATIS requisitions. These counts do **not** establish training eligibility. Requisition completion/status history is absent, so historical training rows cannot safely be reconstructed from today's status alone. Production training therefore uses prospectively captured feature snapshots and observed completion records. No real model has been trained or activated, and no real-world accuracy is claimed.

## Actual backend structure

All prediction/training runs inside the existing Java 21-targeted Spring Boot application:

```text
com.ognjen.diplomski
  ai/
    CustomerRiskController.java   HTTP binding only
    CustomerRiskService.java      catalog orchestration and activity rules
    RiskStore.java                AI JDBC persistence and scoped queries
    RiskJobs.java                 explicit background capture/training/activation lifecycle
    CustomerFeatures.java         point-in-time features, labels, recommendation policy
    DecisionTreeModel.java        Smile CART adapter, evaluator, bounded artifact codec
    RiskTypes.java                typed feature, evaluation, assessment and API records
  service/CustomerAccessPolicy.java  extracted existing visibility rules
```

CustomerServiceImpl uses the same extracted policy as AI queries; its previous business rules are unchanged. CustomerRepository adds a bulk, entity-graph-backed `findByIdIn` for risk-filtered catalog DTOs. There is no per-row feature extraction or per-row frontend request.

Smile **4.4.0** was selected and compiled successfully with the project's Java 21 target. The [upstream versioned library](https://github.com/haifengl/smile/tree/v4.4.0) supplies real CART classification; current major versions require a newer Java runtime. Only smile-core is an application dependency. The library's GPL licensing should be included in the application's dependency/license inventory. There is no Python prediction runtime, AI service, LLM, or external inference endpoint.

Controllers delegate to services; persistence remains inside the monolith. JDBC is limited to AI tables and read-only consumption of existing business tables. The existing JPA/reporting services remain responsible for their business concepts. ObjectProvider allows the existing database-free context smoke test to boot; using AI without its schema returns a controlled 503.

## Persistence and migrations

There was no existing automatic migration framework. Versioned PostgreSQL scripts are explicit deployment migrations, not Hibernate schema updates:

1. `db/migration/V001__customer_risk.sql`
2. `db/migration/V002__capture_coverage.sql`

Run each with psql `-v ON_ERROR_STOP=1`; both are transactional. V002 also supports the early local V001 revision used during development. `ai_schema_migration` records applied versions. The migrations were applied to the configured local development database during implementation. Other environments must apply them explicitly.

Tables: ai_model, ai_order_observation, ai_risk_snapshot, ai_risk_assessment, ai_risk_history, ai_risk_activity, ai_capture_run and the migration ledger. Foreign keys reference existing customer/requisition/user rows. Latest assessment is unique per customer, each observed requisition is unique, activity references an exact assessment history timestamp, and active model uniqueness is enforced by a partial unique index. Customer/date and risk-level indexes support access patterns. No existing business rows, schemas, incentives or report formulas were modified.

Artifacts are server-generated Smile serialization in PostgreSQL bytea, with SHA-256 and schema version. Loading has a 5 MB limit, depth/reference/array limits, an **exact class allowlist**, a required DecisionTree root type and checksum verification. There is no artifact upload or caller-controlled file path. Only trusted database contents are loaded. Smile upgrades require explicit compatibility tests and an allowlist/schema review; checksums are integrity checks, not signatures protecting against a compromised database administrator.

## Lifecycle and performance

Normal list/detail/summary requests read persisted assessments only. Capture and training are explicit CEO jobs on one background worker, not startup work or synchronous prediction handlers. Capture batches 500 customers, reads observations in batches and aggregates paid requisition items to one order row. Training is capped at 50,000 snapshots and 500,000 order observations; oversized datasets are rejected rather than silently sampled.

Training produces an inactive candidate and evaluation metadata. Activation is separate, transactionally switches the single active model, and reload-validates the artifact. Candidate gates cannot be bypassed through the public API. Refresh is required after activation. Assessments are stale after two days or a change in active model version. Risk increases are recorded only against a prior assessment using the same model. Trends select the latest customer assessment per day and include model versions; current authorization applies to historical reads too.

The worker and job-status retention are process-local (last approximately 20 jobs). Deploy a single capture/training worker instance; distributed job coordination and durable scheduling/recovery are not implemented. Jobs are explicit; configure an authenticated daily capture operation outside normal sales traffic. No automatic retraining occurs.

## REST and security

Base `/api/ai`:

| Method/path | Purpose |
|---|---|
| GET `/customers/risks` | Scoped server pagination/search/risk filter, high-first ordering |
| GET `/customers/catalog` | Existing customer DTOs with global risk filtering before pagination |
| POST `/customers/risks/batch` | At most 100 scoped customer summaries; no frontend N+1 |
| GET `/customers/{id}/risk` | Scoped assessment, features, actual tree path, recommendation |
| GET/POST `/customers/{id}/activities` | Read outcomes / representative records an outcome |
| GET `/summary` | Distinct customer counts, risk distribution, increases, historical high-risk liters, CEO teams |
| GET `/risk-trends` | Last 30 days, daily distinct customers and model versions |
| POST `/admin/refresh`, `/admin/train` | 202 plus jobId; background job |
| GET `/admin/jobs/{id}`, `/admin/models` | Administrative status and actual evaluation metadata |
| POST `/admin/models/{id}/activate` | 204 for an eligible, readable model |

List/summary/trend filters use optional salesRepId and managerId. Managers cannot supply a manager selection; representatives cannot select another representative. All filters narrow the authorized SQL scope. Representative access is own visible customers; manager access is current team; CEO access is company-wide visible customers. Existing deleted/deactivated visibility rules are reused. AI reads require customers.read as well as a supported role. BOOKKEEPER is excluded. Only CEO can run model jobs; only the owning SALES_REP can record activities. Actor ID and assessment reference come from the server, never the client. Missing/inaccessible detail returns 404, invalid input 400, permission failure 403, conflicts 409, missing AI schema 503.

## Limitations and future decisions

The recently-active eligibility policy intentionally leaves new, very sparse and already-long-dormant customers unclassified. Eight features are implemented, not all possible features listed in the original brief. No dispenser equipment or gratis quantities are treated as paid water sales. A separate dormant-customer policy, seasonality, holiday-aware horizons and economic validation need business input. Activity outcomes are recorded, not used as risk labels or causal evidence. There is no planned-task assignment/reminder engine. Browser screenshots and production deployment were not verified.

See AI_MODEL_TRAINING.md and AI_TESTING_AND_EVALUATION.md for methodological and test details; frontend integration is documented in the frontend project.
