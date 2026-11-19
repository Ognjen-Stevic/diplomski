# AI testing, evaluation and delivery report

> **V004 autonomous-lifecycle update:** current verification additionally covers durable
> completion events, NORMAL/GRATIS filtering, deterministic dataset fingerprints, unchanged-data
> suppression, database-backed execution state, removed mutation endpoints, read-only CEO
> monitoring and the absence of lifecycle buttons. The exact latest command results belong in the
> implementation report; the older counts below document the earlier manual-lifecycle delivery.

## Autonomous lifecycle verification strategy

Unit tests exercise point-in-time leakage controls, the unchanged Smile gates, fingerprint changes
when any feature/split changes, capture coverage failure, and idempotent outbox insertion. Service
tests verify that only a transition of a NORMAL requisition to COMPLETED queues capture. MVC tests
call the former train, refresh and activation paths as every role: non-CEO roles remain forbidden
and CEO receives 404 because no handler exists. Read-only analytics remain scoped as before.

PostgreSQL verification must confirm V004, one active model, unique observation per requisition,
unique model per non-null dataset fingerprint, persistent job/dataset history and an unexpired or
free training lease. Scheduler tests run with scheduling disabled and invoke components directly;
live verification uses short configurable intervals without inventing data for offline periods.

## Automated evidence

Commands from backend:

```powershell
./mvnw.cmd test
./mvnw.cmd '-Dtest=DecisionTreeRiskTests,RiskHttpTests' test
git diff --check
```

Commands from frontend/stecvi-solutions-komerc:

```powershell
npm.cmd test
npx.cmd tsc -b --pretty false
npm.cmd run lint
npm.cmd run build
```

Final V004 verification on 2026-11-03: the full backend suite reports **233 tests passed**, 0 failures/errors/skips. The full frontend suite reports **36 tests passed** across seven files. TypeScript, full ESLint and the production build pass; the build includes TypeScript compilation. Final diff whitespace check passes.

Live PostgreSQL verification also exercised the real scheduler with shortened intervals. One new
effective dataset produced one candidate and one persisted evaluation. The candidate was safely
rejected against the active model on the common evaluation partition. Three following periodic
checks and one restart check recorded SKIPPED because the fingerprint was unchanged; model count
did not increase and exactly one active model remained. V001-V004 were present, every database
constraint was validated, the training lease was free, and no orphan outbox or dataset rows were
found.

ML tests verify point-in-time filtering including observed-at leakage, future-only labels, real Smile fitting, chronological training/evaluation counts, exclusion of immature/overlapping samples, metrics, recommendations, incomplete capture coverage, filtered artifact rejection and prediction identity after artifact reload. The fixture is explicitly synthetic and cannot enter the production model registry. Its separability is an algorithm regression, not evidence of customer-risk accuracy.

RiskHttpTests uses actual MVC, production security filters, real signed JWT processing, the real risk application service and scoped query construction. JDBC and user persistence are mocked. Cases cover anonymous 401, BOOKKEEPER 403, non-CEO administrative rejection, schema-unavailable 503, representative/team/company scope predicates, unrelated customer 404, bounded batch/pagination and filtering before pagination. SQL construction tests are not a substitute for PostgreSQL execution.

Frontend tests cover badges, model-unavailable state without confidence percentages, late customer results, session draft clearing, duplicate-guarded activity write and authoritative reload, retryable overview error, BOOKKEEPER restriction, global risk filter parameters and existing customer-page links. Existing cancellation/authentication/report/requisition tests remain.

## Local PostgreSQL smoke verification

The configured local development PostgreSQL database was inspected with read-only aggregate queries, then the new AI-only migrations were applied transactionally. A temporary Spring Boot process on port 18081 used real JWTs and production security; no credentials or tokens were printed or saved. Capture populated real point-in-time observation/assessment data, not synthetic predictions. Existing customer, product, requisition and report data was not modified.

Initial capture processed all **159 customers** with **no active model**. CEO-authorized summary returned **151 visible customers**, all unclassified, and team counts 69+82=151. The difference from 159 is the existing visibility policy, not duplicate counting or missing pages. A trend-query alias problem was found by this real database check and fixed. Final live checks returned 200 for summary, paginated risk list, trends, risk-filtered catalog, selected-manager summary and model list. BOOKKEEPER summary access returned 403, non-CEO training returned 403, a manager selecting another manager returned 403, and a representative's unrelated detail returned 404. A submitted CEO training job returned 202 then FAILED with the explicit mature-snapshot requirement. The model registry remained empty. No real email was sent, and no synthetic activity was written against a real customer. The temporary verification server was stopped.

Migrations/capture are real local changes. Production deployment, real validated-model activation, concurrent multi-instance job execution and a browser-to-backend E2E flow are not verified. Existing row-lock/revision/report regressions still pass, but this feature does not claim new PostgreSQL concurrency coverage for those unrelated workflows.

## Results and honest limitations

There is **no production ML evaluation result**. The audit found completed paid requisitions, but no audited historical feature snapshots/completion chronology. Training must wait for prospectively collected, mature, continuously observed examples. Production refuses to make learned predictions without an eligible active model. No percentage accuracy, calibrated confidence, invented importance or forecast loss is supplied.

The implemented engineering gates compare categorical macro-F1 to a recency baseline, require per-class recall and minimum class/sample counts, and persist true measured results when training becomes possible. These do not establish intervention benefit, seasonality robustness or statistical significance. The horizon/label thresholds and recently-active population are explicit business proposals requiring review. Already-dormant/sparse customers remain insufficient-data rather than receiving fabricated HIGH labels.

Remaining deployment decisions: reliable authenticated daily captures; snapshot/model retention; single-worker hosting or future distributed coordination; artifact compatibility on Smile upgrades; validation on a later temporal cohort; operational review of recommendations and outcome quality. Jobs themselves are transient, while model/snapshot/assessment/activity records are durable. No production model was trained or activated during implementation.

Toolchain warnings remain: Lombok/Unsafe deprecation, Mockito dynamic agent attachment and existing frontend bundle size/Babel timing. No new test framework was introduced.

## Change inventory and first-model instructions

Backend additions: ai package (seven Java files), shared CustomerAccessPolicy, two AI test classes, two SQL migrations and three AI documentation files. Backend modifications: pom.xml (Smile 4.4.0), SpringSecurityConfig (new AI-only rules), CustomerServiceImpl (reuse extracted unchanged policy), CustomerRepository (bulk graph read). Existing uncommitted work from prior tasks was preserved.

Frontend inventory and integration decisions are in AI_FRONTEND_INTEGRATION.md in the frontend project. First deployment/training/activation steps, API operations and exact validation thresholds are in AI_MODEL_TRAINING.md. The actual model feature/target definition, package architecture, schema changes and role matrix are in AI_DECISION_TREE_ARCHITECTURE.md.
