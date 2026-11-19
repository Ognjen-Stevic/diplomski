# Refactoring Plan

## Scope and constraints

This plan is based on a static review of the Spring Boot backend under `backend/` and the React/TypeScript frontend under `frontend/stecvi-solutions-komerc/` on 2026-11-14.

The plan deliberately preserves:

- existing HTTP routes, request/response contracts, status codes, and authorization rules;
- the PostgreSQL schema and existing data;
- requisition, reporting, customer, product, employee, advertising-material, and goods-request business rules;
- the autonomous AI capture, assessment, training, model-validation, promotion, audit, and scheduling behavior;
- existing Serbian user-facing text unless a separate product requirement changes it.

This is an incremental plan, not a rewrite. Each structural change should be delivered in a small pull request with characterization tests first.

## Current strengths to retain

- The frontend is already organized mainly by feature and uses a small shared HTTP client.
- Backend controllers generally depend on service interfaces and DTOs rather than returning entities.
- Product management and customer listing already use server-side pagination.
- Requisition mutations use locking and revision checks.
- AI responsibilities are already separated into `api`, `domain`, `model`, `persistence`, `service`, and `lifecycle` packages.
- The AI lifecycle has durable work, retries, model validation, dataset fingerprints, audit logging, and automated promotion safeguards.
- There is meaningful unit, HTTP-security, workflow, and AI test coverage. The plan should build on it rather than replace it.

## Priority summary

| ID | Area | Priority | Regression risk |
|---|---|---|---|
| RF-01 | Externalize committed secrets and environment configuration | HIGH | MEDIUM |
| RF-02 | Standardize database migrations and deployment verification | HIGH | HIGH |
| RF-03 | Add PostgreSQL integration coverage before query refactoring | HIGH | LOW |
| RF-04 | Split sales-report scope, aggregation, querying, and export responsibilities | HIGH | HIGH |
| RF-05 | Extract requisition access/workflow/item synchronization policies | HIGH | HIGH |
| RF-06 | Consolidate frontend asynchronous request lifecycle handling | HIGH | MEDIUM |
| RF-07 | Correct frontend dependency direction around `shared`, `app`, and features | HIGH | MEDIUM |
| RF-08 | Separate AI assessment calculation, persistence, and work orchestration | MEDIUM | HIGH |
| RF-09 | Split large frontend management pages into page controllers and views | MEDIUM | MEDIUM |
| RF-10 | Unify frontend HTTP errors and file-download handling | MEDIUM | MEDIUM |
| RF-11 | Add route-level code splitting | MEDIUM | LOW |
| RF-12 | Normalize backend validation and authorization boundaries | MEDIUM | MEDIUM |
| RF-13 | Make time and time-zone dependencies explicit | MEDIUM | MEDIUM |
| RF-14 | Fix package naming and adopt incremental feature packaging | MEDIUM | LOW |
| RF-15 | Remove confirmed dead authentication code and unused configuration | LOW | LOW |
| RF-16 | Split oversized test classes by behavior | LOW | LOW |
| RF-17 | Measure before introducing additional abstractions or dependencies | LOW | LOW |

---

## Detailed findings

### RF-01 — Externalize committed secrets and environment configuration

**Priority:** HIGH  
**Regression risk:** MEDIUM (deployment/configuration risk, not business-logic risk)

**Files**

- `backend/src/main/resources/application.properties`
- `frontend/stecvi-solutions-komerc/.env.local`
- `backend/.gitignore`
- `frontend/stecvi-solutions-komerc/.gitignore`

**Problem**

`application.properties` contains concrete local database, JWT, SMTP, and mail-account values. Some properties support environment overrides, but the datasource and mail configuration still contain deployment-specific values in the tracked application configuration. A Vite Google Maps key also exists in local frontend configuration; browser keys are inherently visible, but they still need API and referrer restrictions.

**Why it matters**

- Repository history can retain credentials after the current file is cleaned.
- The same artifact cannot be safely promoted across development, test, and production environments.
- Accidental production use of local defaults is possible.
- Rotating credentials becomes harder because configuration ownership is unclear.

**Recommended change**

1. Replace concrete secrets with required environment placeholders.
2. Keep non-secret defaults in `application.properties`.
3. Add an untracked `application-local.properties.example` or documented environment template with dummy values only.
4. Activate local overrides through a documented Spring profile.
5. Rotate any credential that has existed in repository history.
6. Restrict the browser Google Maps key by allowed HTTP referrers and enabled APIs; do not treat a `VITE_` value as a secret.
7. Add startup validation for mandatory production configuration.

**Tests needed**

- Backend context test with test-only environment properties.
- Startup failure test when a mandatory production secret is absent.
- Login, email, database connectivity, image upload, and AI audit-path smoke tests in the deployment environment.
- Frontend production build with and without optional Vite configuration.

### RF-02 — Standardize database migrations and deployment verification

**Priority:** HIGH  
**Regression risk:** HIGH

**Files**

- `backend/db/migration/V001__customer_risk.sql` through `backend/db/migration/V008__replace_product_placeholder_with_jpg.sql`
- `backend/src/main/resources/db/advertising_material_distribution_migration.sql`
- `backend/src/main/resources/db/customer_email_migration.sql`
- `backend/src/main/resources/db/customer_status_migration.sql`
- `backend/src/main/resources/db/customer_validation_alignment_migration.sql`
- `backend/src/main/resources/db/goods_request_approval_migration.sql`
- `backend/src/main/resources/db/goods_request_item_status_migration.sql`
- `backend/src/main/resources/db/permissions_migration.sql`
- `backend/src/main/resources/db/product_manager_stimulation_migration.sql`
- `backend/src/main/resources/db/product_picture_url_migration.sql`
- `backend/src/main/resources/db/requisition_status_workflow_migration.sql`
- `backend/src/main/java/com/ognjen/diplomski/ai/persistence/RiskStore.java`

**Problem**

Migrations are split between versioned scripts outside the runtime classpath and unversioned scripts in `src/main/resources/db`. There is no single migration runner or ledger covering the complete application. The AI module detects a missing schema at runtime and returns a controlled 503, but other modules do not have equivalent deployment verification.

**Why it matters**

- Environments can silently run different schemas.
- Script ordering and one-time execution are manual and error-prone.
- Refactoring repositories or DTOs is unsafe without knowing which schema version is active.
- The current product placeholder migration demonstrates how code/defaults and live data can drift.

**Recommended change**

1. Inventory the exact production order of every existing script without changing its SQL semantics.
2. Assign versions to the unversioned scripts and place all migrations in one canonical location.
3. Adopt one migration ledger for the whole application. Flyway is a reasonable option, but automatic production execution should be enabled only after baseline/version behavior is tested against a database copy.
4. Add a deployment command that validates pending migrations before application startup.
5. Preserve the AI migration history and checks; do not rewrite already-applied AI migrations.
6. Document backup, rollback, and `ON_ERROR_STOP` procedures.

**Tests needed**

- Apply the complete migration chain to an empty PostgreSQL database.
- Baseline an existing database and verify that no script is re-applied.
- Upgrade a production-like pre-migration database and compare tables, constraints, indexes, defaults, and data counts.
- Boot the application after migration and run repository/API smoke tests.
- Verify AI model, audit, outbox, assessment, and training tables remain intact.

### RF-03 — Add PostgreSQL integration coverage before query refactoring

**Priority:** HIGH  
**Regression risk:** LOW (test-only first step)

**Files**

- `backend/src/main/java/com/ognjen/diplomski/repository/SalesReportRepository.java`
- `backend/src/main/java/com/ognjen/diplomski/repository/RequisitionRepository.java`
- `backend/src/main/java/com/ognjen/diplomski/repository/ProductRepository.java`
- `backend/src/main/java/com/ognjen/diplomski/repository/CustomerRepository.java`
- `backend/src/main/java/com/ognjen/diplomski/repository/AppUserRepository.java`
- `backend/src/main/java/com/ognjen/diplomski/ai/persistence/RiskStore.java`
- `backend/src/test/java/com/ognjen/diplomski/**`

**Problem**

The test suite has strong Mockito and MVC coverage, but no `@DataJpaTest` or equivalent PostgreSQL repository suite was found. Complex JPQL/native projections, authorization scopes, date boundaries, pagination, and AI SQL are therefore not exercised against the database engine that runs in production.

**Why it matters**

- Mock-based service tests cannot detect invalid JPQL, PostgreSQL type behavior, incorrect joins, duplicate rows, lazy-loading failures, or index-unfriendly queries.
- Query refactoring in reporting, requisitions, and AI has a high regression surface.
- Migration correctness and repository correctness currently cannot be verified together.

**Recommended change**

1. Add a small PostgreSQL integration-test profile, preferably using Testcontainers or a controlled disposable database.
2. Apply the same migrations used by deployment.
3. Start with contract tests around the query hotspots listed above.
4. Use compact fixture builders rather than a second synthetic production-data generator.
5. Keep existing fast unit tests; integration tests complement them.

**Tests needed**

- Report totals for sales-representative, manager-team, and CEO scopes.
- Monthly/YTD boundary behavior and package-type aggregation.
- Requisition visibility and allowed status transitions for all four roles.
- Product search/status filtering with stable pagination.
- Customer visibility/status filtering.
- AI list/summary/detail scopes, stale detection, outbox idempotency, and model uniqueness constraints.
- Query-count assertions for list/detail endpoints where practical.

### RF-04 — Split sales-report scope, aggregation, querying, and export responsibilities

**Priority:** HIGH  
**Regression risk:** HIGH

**Files**

- `backend/src/main/java/com/ognjen/diplomski/service/impl/SalesReportServiceImpl.java` (approximately 699 lines)
- `backend/src/main/java/com/ognjen/diplomski/repository/SalesReportRepository.java` (approximately 282 lines)
- `backend/src/main/java/com/ognjen/diplomski/contorller/SalesReportController.java` (approximately 219 lines)
- `backend/src/main/java/com/ognjen/diplomski/service/impl/SalesReportExcelServiceImpl.java` (approximately 430 lines)
- `backend/src/main/java/com/ognjen/diplomski/service/SalesReportService.java`

**Problem**

`SalesReportServiceImpl` resolves role-dependent scope, calculates date ranges, selects repository strategies, aggregates entities, calculates incentives, builds product rows, and constructs multiple response types. The repository contains several projection families and long report queries. The controller handles many near-parallel JSON/export endpoints, while the Excel service combines workbook orchestration, sheet layout, style creation, and serialization.

**Why it matters**

- A change to one role or formula can affect monthly, YTD, gratis, comparison, and export flows.
- In-memory and database aggregation paths can drift.
- Large tests mirror the large class instead of testing isolated policies.
- Query performance is difficult to profile because scope and aggregation decisions are intertwined.

**Recommended change**

Refactor along existing behavior seams, without changing endpoints or formulas:

1. Extract `ReportScopeResolver` for CEO/manager/sales-representative scope validation.
2. Extract a shared `ReportPeriod`/date-range factory using an injected clock and zone.
3. Extract pure `SalesAggregationCalculator` and `GratisAggregationCalculator` classes.
4. Keep repository projections grouped by use case; split the repository only where query families have independent consumers (for example, product totals versus team comparisons).
5. Make JSON and Excel endpoints call the same report use cases so exports cannot calculate different data.
6. Split workbook rendering into sales, gratis, and reusable style/worksheet helpers.
7. Measure entity-loading queries before moving additional aggregation into SQL; do not rewrite all calculations merely to reduce line count.

**Tests needed**

- Characterization tests for every existing response and workbook sheet.
- Golden-value tests for bottles, jugs, liters, sales-representative stimulation, manager stimulation, company totals, and gratis reasons.
- Role/scope matrix tests for CEO, manager, sales representative, and forbidden roles.
- Month/year boundary and current-year truncation tests with a fixed clock.
- Workbook structural tests for sheet names, headers, cells, number formats, and filenames.
- PostgreSQL integration tests comparing projection and in-memory totals.

### RF-05 — Extract requisition access, workflow, and item synchronization policies

**Priority:** HIGH  
**Regression risk:** HIGH

**Files**

- `backend/src/main/java/com/ognjen/diplomski/service/impl/RequisitionServiceImpl.java` (approximately 513 lines)
- `backend/src/main/java/com/ognjen/diplomski/service/CustomerAccessPolicy.java`
- `backend/src/main/java/com/ognjen/diplomski/repository/RequisitionRepository.java`
- `backend/src/main/java/com/ognjen/diplomski/service/RequisitionRevision.java`
- `backend/src/main/java/com/ognjen/diplomski/ai/lifecycle/capture/AiCaptureOutbox.java`

**Problem**

The requisition service handles creation, editing, status workflow, authorization, monthly querying, customer visibility, product synchronization, PDF/email eligibility, revision conflicts, and AI completion notification. Customer access logic is partly duplicated instead of consistently using `CustomerAccessPolicy`. Status-transition rules and role visibility are embedded in private methods and static collections inside the service.

**Why it matters**

- Requisition workflow is central business logic and currently has a large change surface.
- Access rules can diverge between customers, requisitions, reports, and AI.
- Product-edit rules are hard to test without constructing the full service.
- The optional AI outbox dependency can hide a missing integration at runtime.

**Recommended change**

1. Extract a pure `RequisitionWorkflowPolicy` that returns visibility, editability, and allowed transitions.
2. Extract `RequisitionItemSynchronizer` for product lookup, duplicate detection, inactive-product rules, quantity updates, and stimulation snapshots.
3. Reuse `CustomerAccessPolicy` or a shared scoped-access service for customer-history access.
4. Extract a small period factory for monthly queries.
5. Keep transaction boundaries in the application service.
6. Replace optional AI notification wiring with an explicit port/interface whose normal implementation writes to `AiCaptureOutbox`; provide a deliberate no-op only in tests that exclude AI.
7. Preserve locking, revisions, status codes, messages, and the after-commit AI behavior.

**Tests needed**

- Existing requisition service suite retained as characterization coverage.
- Table-driven unit tests for every role/status transition combination.
- Item synchronization tests covering duplicates, inactive existing products, inactive newly added products, removal, and stimulation snapshots.
- Concurrent update/revision and pessimistic-lock conflict tests.
- Integration test proving a transition to `COMPLETED/NORMAL` creates exactly one outbox event after commit.
- Tests proving `GRATIS` and non-completed transitions do not enqueue AI capture.
- PDF/email permission and recipient-email tests.

### RF-06 — Consolidate frontend asynchronous request lifecycle handling

**Priority:** HIGH  
**Regression risk:** MEDIUM

**Files**

- `frontend/stecvi-solutions-komerc/src/shared/hooks/use_session_request.ts`
- `frontend/stecvi-solutions-komerc/src/features/requisition/hooks/use_requisition_data.ts`
- `frontend/stecvi-solutions-komerc/src/features/report/hooks/use_report_data.ts`
- `frontend/stecvi-solutions-komerc/src/features/sales_team/hooks/use_team_summary.ts`
- `frontend/stecvi-solutions-komerc/src/features/sales_team/hooks/use_team_comparison.ts`
- `frontend/stecvi-solutions-komerc/src/features/dashboard/hooks/use_monthly_dashboard_stats.ts`
- `frontend/stecvi-solutions-komerc/src/features/dashboard/components/alerts/dashboard_alerts.tsx`

**Problem**

Several hooks independently implement abort controllers, retry counters, request identities, session-change protection, stale-result suppression, loading state, and error conversion. `useSessionRequest` already supplies most of this behavior, while requisition, report, team, dashboard, and polling code maintain variants.

**Why it matters**

- Small differences create race conditions after logout, navigation, retry, or A→B→A filter changes.
- Loading/error behavior differs between features.
- Every new data screen tends to copy lifecycle code.
- Tests must repeatedly verify the same concurrency behavior.

**Recommended change**

1. Define one documented request-state primitive with `enabled`, identity/key, cancellation, retry/reload, session invalidation, and optional `onSuccess`.
2. Add a separate polling wrapper for dashboard alerts instead of embedding polling in the component.
3. Migrate one low-risk hook first, then reports, team, requisitions, and dashboard.
4. Keep feature-specific mapping and error text in feature hooks; do not create a global business-data store.
5. Do not add React Query solely for this refactor unless caching/deduplication requirements justify the dependency separately.

**Tests needed**

- Request cancellation on unmount and key change.
- Logout/session change while a request is in flight.
- A→B→A navigation and stale-response suppression.
- Retry after failure.
- Disabled-query behavior.
- Polling cleanup and immediate refresh events.
- Existing feature rendering tests for loading, error, empty, and success states.

### RF-07 — Correct frontend dependency direction around `shared`, `app`, and features

**Priority:** HIGH  
**Regression risk:** MEDIUM

**Files**

- `frontend/stecvi-solutions-komerc/src/shared/hooks/use_session_request.ts`
- `frontend/stecvi-solutions-komerc/src/shared/components/session_scope.tsx`
- `frontend/stecvi-solutions-komerc/src/shared/components/customer_selector.tsx`
- `frontend/stecvi-solutions-komerc/src/shared/components/ai/customer_ai_analysis.tsx`
- `frontend/stecvi-solutions-komerc/src/shared/components/ai/customer_ai_panel.tsx`
- `frontend/stecvi-solutions-komerc/src/shared/components/ai/risk_badge.tsx`
- `frontend/stecvi-solutions-komerc/src/shared/components/ai/risk_filter.tsx`
- `frontend/stecvi-solutions-komerc/src/shared/components/ai/risk_overview.tsx`
- `frontend/stecvi-solutions-komerc/src/features/ai/**`
- `frontend/stecvi-solutions-komerc/src/features/auth/**`
- `frontend/stecvi-solutions-komerc/src/features/customer/**`

**Problem**

The `shared` layer imports feature-specific APIs, types, constants, and auth hooks. For example, shared AI components import `features/ai`, and `customer_selector.tsx` imports the customer feature type. This reverses the intended dependency direction and makes the supposedly generic layer impossible to reuse without feature knowledge.

**Why it matters**

- Feature changes can unexpectedly break shared infrastructure.
- Circular dependencies become easier to introduce.
- Ownership is unclear: AI UI appears split between `features/ai` and `shared/components/ai`.
- Tests need broad mocks because low-level modules know high-level features.

**Recommended change**

1. Move AI-specific components back under `features/ai/components`; expose only an intentional feature entry point if dashboard/customer screens consume them.
2. Move the session request primitive and session scope to `app/session` or another application-infrastructure location that may depend on authentication state through a narrow session interface.
3. Move `CustomerSelector` into the customer feature, or make it accept a small local structural type instead of importing the full feature DTO.
4. Define and enforce the direction: `app infrastructure` → no feature UI imports; `shared` → framework/domain-neutral; `features` → app/shared and explicit feature public APIs.
5. Add an ESLint import-boundary rule only after the physical moves are complete.

**Tests needed**

- TypeScript build after each move.
- Route smoke tests for dashboard, AI analytics, customer selection, requisition creation, and customer detail.
- Import-boundary lint test/configuration.
- Existing AI and auth tests retained.

### RF-08 — Separate AI assessment calculation, persistence, and work orchestration

**Priority:** MEDIUM  
**Regression risk:** HIGH

**Files**

- `backend/src/main/java/com/ognjen/diplomski/ai/service/AiAssessmentService.java` (approximately 450 lines)
- `backend/src/main/java/com/ognjen/diplomski/ai/persistence/RiskStore.java`
- `backend/src/main/java/com/ognjen/diplomski/ai/lifecycle/capture/AiCaptureService.java`
- `backend/src/main/java/com/ognjen/diplomski/ai/lifecycle/training/AiTrainingService.java`
- `backend/src/main/java/com/ognjen/diplomski/ai/lifecycle/model/AiModelService.java`

**Problem**

`AiAssessmentService` calculates features and predictions, writes snapshots/current/history records, manages daily jobs, identifies stale customers, enqueues/retries/claims durable work, updates health, and writes audit records. It frequently reaches through `RiskStore.db()` and embeds persistence SQL in the service. Training and capture services use a similar orchestration/persistence/audit mixture.

**Why it matters**

- Assessment correctness and retry mechanics cannot be changed independently.
- SQL details leak beyond the persistence package.
- Failure-path testing requires many collaborators.
- AI is safety-sensitive for this application; large orchestration classes make subtle lifecycle regressions more likely.

**Recommended change**

Refactor only after RF-02 and RF-03:

1. Extract a pure `CustomerAssessmentCalculator` that accepts customer history, feature timestamp, and active model, then returns status, level, features, trace, explanation, and model version.
2. Add persistence components under `ai/persistence` for assessment records, assessment work queue, and job/system state.
3. Keep `AiAssessmentService` as the transaction/use-case coordinator.
4. Retain the current durable SQL semantics (`FOR UPDATE SKIP LOCKED`, generations, retries, leases, idempotent snapshot/history inserts).
5. Apply the same principle selectively to training/capture only where it reduces tested responsibilities; do not rewrite the Decision Tree model or move AI to another runtime.
6. Keep every current audit event name and payload field stable.

**Tests needed**

- Pure calculation tests for sufficient/insufficient data, model unavailable, and all risk predictions.
- PostgreSQL queue tests for claim, lock expiry, generation changes, retry backoff, terminal failure, and recovery.
- Daily snapshot idempotency tests.
- Model-change and two-day stale-refresh tests.
- Audit-event snapshot tests.
- Full autonomous lifecycle tests covering capture → assessment → mature dataset → training → promotion → refresh.

### RF-09 — Split large frontend management pages into page controllers and views

**Priority:** MEDIUM  
**Regression risk:** MEDIUM

**Files**

- `frontend/stecvi-solutions-komerc/src/features/employees/pages/employees_page.tsx` (approximately 306 lines)
- `frontend/stecvi-solutions-komerc/src/features/product/pages/products_management_page.tsx` (approximately 233 lines)
- `frontend/stecvi-solutions-komerc/src/features/advertising_materials/pages/advertising_material_management_page.tsx` (approximately 203 lines)
- `frontend/stecvi-solutions-komerc/src/features/requisition/pages/new_requisition_page.tsx` (approximately 187 lines)
- `frontend/stecvi-solutions-komerc/src/features/customer/pages/new_customer_page.tsx` (approximately 177 lines)

**Problem**

These pages combine authorization redirects, URL-state parsing, data loading, filtering, mutation guards, error ownership, modal state, and large JSX layouts. Employee, product, and advertising-material management repeat similar page headers, search/status filters, cards, create dialogs, status confirmations, and retry handling.

**Why it matters**

- Mutation race handling is mixed with rendering.
- URL behavior and form behavior are difficult to test separately.
- Similar management pages evolve inconsistently.
- Large components encourage broad integration tests rather than focused hook/component tests.

**Recommended change**

1. Extract feature-local controller hooks such as `useEmployeeManagement`, `useProductManagement`, and `useAdvertisingMaterialManagement` for URL state, queries, and mutations.
2. Extract only proven repeated presentational pieces (management header, filter panel, async error banner) after comparing all three pages.
3. Keep cards and business-specific dialogs in their feature folders.
4. Preserve URL query parameters (`action`, IDs, page), role redirects, request counts, and current messages.
5. Do not introduce a generic CRUD framework; the workflows already differ enough to justify feature-specific controllers.

**Tests needed**

- Hook tests for URL open/close/edit state and stale-session mutation protection.
- Page tests for role redirects, filtering, loading, empty, error, create/update/status operations, and pagination.
- Tests that retries do not duplicate mutations.
- Existing mobile navigation and reliability tests retained.

### RF-10 — Unify frontend HTTP errors and file-download handling

**Priority:** MEDIUM  
**Regression risk:** MEDIUM

**Files**

- `frontend/stecvi-solutions-komerc/src/app/api/http_client.ts`
- `frontend/stecvi-solutions-komerc/src/features/auth/auth_api.ts`
- `frontend/stecvi-solutions-komerc/src/features/report/report_api.ts` (approximately 313 lines)
- `frontend/stecvi-solutions-komerc/src/features/requisition/requisition_api.ts` (approximately 194 lines)
- `frontend/stecvi-solutions-komerc/src/features/dashboard/alert_api.ts`

**Problem**

Authentication and normal APIs have different error classes and response parsers. Report Excel download and requisition PDF download duplicate status parsing, `Content-Disposition` filename parsing, blob URL creation, temporary links, and cleanup. Alert loading bypasses `apiRequest` and parses errors separately.

**Why it matters**

- The same backend error can produce different frontend messages.
- Download fixes must be made in more than one feature.
- Session invalidation behavior is harder to reason about.
- `app/api/http_client.ts` imports the auth feature directly, contributing to the dependency-direction issue.

**Recommended change**

1. Extract token/session access behind an app-level session adapter to avoid an app→feature import.
2. Use one API error shape and parser for authenticated, anonymous, JSON, and empty responses.
3. Add a reusable `downloadAuthenticatedFile` helper with filename parsing and abort/session checks.
4. Keep feature-specific fallback messages as parameters.
5. Preserve current 401 invalidation and all public user-facing messages.

**Tests needed**

- JSON error, non-JSON error, network failure, 401, 403, 204, and aborted-request tests.
- Encoded and plain `Content-Disposition` filenames.
- Blob URL creation/revocation and temporary-link cleanup.
- Session changes during download.
- Report Excel and requisition PDF regression tests.

### RF-11 — Add route-level code splitting

**Priority:** MEDIUM  
**Regression risk:** LOW

**Files**

- `frontend/stecvi-solutions-komerc/src/app/app.tsx`
- `frontend/stecvi-solutions-komerc/vite.config.ts`

**Problem**

Every page is imported eagerly in `app.tsx`. The production build currently emits a main JavaScript chunk above Vite's 500 kB warning threshold, so users load employee management, reports, AI analytics, maps, requisitions, and other role-inaccessible pages up front.

**Why it matters**

- Slower initial parsing and execution, especially on mobile devices.
- Users download code for features they cannot access.
- Large chunks make performance regressions harder to attribute.

**Recommended change**

1. Convert page imports to `React.lazy`/dynamic imports.
2. Add one accessible route loading fallback inside the existing error boundary.
3. Keep auth provider, route guards, layout, and small navigation code eager.
4. Measure before manually configuring vendor chunks; route splitting may be sufficient.

**Tests needed**

- Production build and chunk inspection.
- Navigation smoke tests for every route and role.
- Loading-fallback and lazy-import failure tests.
- Browser performance comparison for initial load and first route navigation.

### RF-12 — Normalize backend validation and authorization boundaries

**Priority:** MEDIUM  
**Regression risk:** MEDIUM

**Files**

- `backend/src/main/java/com/ognjen/diplomski/dto/EmployeeCreateRequest.java`
- `backend/src/main/java/com/ognjen/diplomski/dto/EmployeeUpdateRequest.java`
- `backend/src/main/java/com/ognjen/diplomski/service/impl/EmployeeServiceImpl.java`
- `backend/src/main/java/com/ognjen/diplomski/service/impl/AuthServiceImpl.java`
- `backend/src/main/java/com/ognjen/diplomski/auth/ApiExceptionHandler.java`
- `backend/src/main/java/com/ognjen/diplomski/config/SpringSecurityConfig.java`
- `backend/src/main/java/com/ognjen/diplomski/service/CustomerAccessPolicy.java`
- `backend/src/main/java/com/ognjen/diplomski/ai/persistence/RiskScopeFactory.java`

**Problem**

Some syntactic validation is expressed with Jakarta annotations while employee/auth services manually repeat length, email, and blank checks. Authorization is correctly enforced in security configuration and services, but scoped customer/requisition/report/AI rules are implemented in several forms. `@EnableMethodSecurity` is enabled although no method-security annotations were found. Services also use `ResponseStatusException` directly, mixing domain decisions with HTTP representation.

**Why it matters**

- Validation messages and accepted input can drift between create/update flows.
- Access policies are difficult to compare across modules.
- Redundant security mechanisms confuse future maintainers.
- Domain logic is harder to unit-test without HTTP exceptions.

**Recommended change**

1. Put field-level constraints (`@Size`, `@Email`, etc.) on request DTOs; retain cross-field and database-dependent rules in services.
2. Reuse scoped access policies where semantics are genuinely identical; keep deliberately different visibility documented.
3. Decide on one security style: retain centralized request matching and remove unused method-security enablement, or adopt method-level annotations consistently for defense in depth.
4. Introduce small typed application exceptions only where several services share the same error semantics; map them centrally in `ApiExceptionHandler`.
5. Preserve all current status codes and response bodies with contract tests.

**Tests needed**

- DTO boundary tests for blank, length, email, password-byte, phone, and role/manager combinations.
- Full HTTP authorization matrix for every endpoint and role.
- Customer-scope equivalence tests across customer, requisition, reporting, and AI endpoints.
- API error contract snapshots for validation, malformed JSON, forbidden, conflict, not found, and unexpected failures.

### RF-13 — Make time and time-zone dependencies explicit

**Priority:** MEDIUM  
**Regression risk:** MEDIUM

**Files**

- `backend/src/main/java/com/ognjen/diplomski/service/impl/SalesReportServiceImpl.java`
- `backend/src/main/java/com/ognjen/diplomski/service/impl/RequisitionServiceImpl.java`
- `backend/src/main/java/com/ognjen/diplomski/service/impl/GoodsRequestServiceImpl.java`
- `backend/src/main/java/com/ognjen/diplomski/service/impl/AdvertisingMaterialServiceImpl.java`
- `backend/src/main/java/com/ognjen/diplomski/service/impl/EmployeeServiceImpl.java`
- `backend/src/main/java/com/ognjen/diplomski/ai/lifecycle/scheduling/AiLifecycleScheduler.java`
- `frontend/stecvi-solutions-komerc/src/features/dashboard/hooks/use_monthly_dashboard_stats.ts`
- `frontend/stecvi-solutions-komerc/src/features/report/utils/report_navigation.ts`

**Problem**

Business services use `ZoneId.systemDefault()`, `OffsetDateTime.now()`, or `new Date()` directly, while AI scheduling has an explicit configured zone. Tests that depend on the current date need custom workarounds, and server deployments in another host zone can change monthly boundaries.

**Why it matters**

- Month/year reports and status timestamps can differ by deployment host.
- Daylight-saving and end-of-month bugs are difficult to reproduce.
- Time-based tests are less deterministic.

**Recommended change**

1. Define an application business zone and inject a `Clock` into time-dependent backend services.
2. Keep the AI zone configurable and explicitly document whether it must equal the business zone.
3. Pass `now` into frontend date-navigation utilities where possible; use a small time provider only for hooks that need testable current month/year.
4. Preserve stored timestamp types and existing API serialization.

**Tests needed**

- Fixed-clock tests at month/year boundaries.
- Europe/Belgrade DST transition tests.
- Current-year YTD truncation tests.
- Monthly requisition, goods request, employee summary, advertising distribution, dashboard, and report navigation tests.
- AI weekly/daily schedule tests in the configured zone.

### RF-14 — Fix package naming and adopt incremental feature packaging

**Priority:** MEDIUM  
**Regression risk:** LOW for typo correction; MEDIUM for broader moves

**Files**

- `backend/src/main/java/com/ognjen/diplomski/contorller/**`
- `backend/src/test/java/com/ognjen/diplomski/contorller/**`
- `backend/src/main/java/com/ognjen/diplomski/dto/**`
- `backend/src/main/java/com/ognjen/diplomski/service/**`
- `backend/src/main/java/com/ognjen/diplomski/service/impl/**`
- `backend/src/main/java/com/ognjen/diplomski/repository/**`

**Problem**

The controller package is misspelled `contorller`. Non-AI backend code is organized almost entirely by technical layer, leaving roughly sixty unrelated DTOs in one package and placing all domain services/repositories together. AI is already organized more cohesively by subdomain.

**Why it matters**

- Navigation and ownership become difficult as features grow.
- Naming mistakes leak into tests and package-qualified configuration.
- Broad package moves become more expensive over time.

**Recommended change**

1. Rename `contorller` to `controller` as an isolated mechanical change.
2. Do not perform a repository-wide package rewrite in one pull request.
3. When RF-04/RF-05/RF-12 touch a feature, move its controller, DTOs, service, and repository-facing interfaces into a cohesive feature package while keeping entities and shared infrastructure stable.
4. Use explicit imports instead of wildcard DTO imports in controllers.
5. Preserve component scanning under `com.ognjen.diplomski` and every HTTP mapping.

**Tests needed**

- Full backend compilation and test suite after the typo rename.
- Spring context and controller discovery smoke tests.
- HTTP contract/security suite after each feature move.
- Architecture test preventing new classes in the misspelled package and enforcing allowed package dependencies.

### RF-15 — Remove confirmed dead authentication code and unused configuration

**Priority:** LOW  
**Regression risk:** LOW

**Files**

- `backend/src/main/java/com/ognjen/diplomski/service/AuthService.java`
- `backend/src/main/java/com/ognjen/diplomski/service/impl/AuthServiceImpl.java`
- `backend/src/main/java/com/ognjen/diplomski/dto/RegisterRequest.java`
- `backend/src/main/java/com/ognjen/diplomski/contorller/AuthController.java`
- `backend/src/main/java/com/ognjen/diplomski/config/SpringSecurityConfig.java`
- `backend/src/main/resources/application.properties`

**Problem**

`AuthService.register`, `RegisterRequest`, default-role configuration, role lookup, password encoding, and registration validation remain in the backend, but no controller exposes registration and `/api/auth/register` is explicitly denied. `AuthController` also depends on `AuthServiceImpl` instead of the existing `AuthService` interface. `@EnableMethodSecurity` appears unused.

**Why it matters**

- Dead code implies a capability that does not exist.
- Unused dependencies make authentication changes harder to review.
- Depending on an implementation defeats the existing service interface.

**Recommended change**

1. Confirm there is no external/internal registration caller.
2. Remove the registration method, DTO, default-role property, and now-unused collaborators from `AuthServiceImpl`.
3. Make `AuthController` depend on `AuthService`.
4. Retain the explicit deny rule if it is a deliberate security guard for a known route; otherwise document why the route is absent.
5. Remove `@EnableMethodSecurity` only after RF-12 decides the authorization strategy.

**Tests needed**

- Login and `/api/auth/me` tests.
- Security test proving `/api/auth/register` remains unavailable/denied.
- JWT generation and invalidation tests.
- Full Spring context test confirming no missing bean dependency.

### RF-16 — Split oversized test classes by behavior

**Priority:** LOW  
**Regression risk:** LOW

**Files**

- `backend/src/test/java/com/ognjen/diplomski/service/RequisitionServiceImplTests.java` (approximately 968 lines)
- `backend/src/test/java/com/ognjen/diplomski/service/SalesReportServiceImplTests.java` (approximately 479 lines)
- `backend/src/test/java/com/ognjen/diplomski/contorller/ApiHttpSecurityTests.java` (approximately 464 lines)
- `frontend/stecvi-solutions-komerc/src/test/reliability.test.tsx` (approximately 323 lines)
- `frontend/stecvi-solutions-komerc/src/test/ai.test.tsx` (approximately 227 lines)

**Problem**

Large test files cover many unrelated behaviors and require broad fixtures/mocks. They are useful characterization suites, but locating ownership and diagnosing failures is increasingly difficult.

**Why it matters**

- Refactors cause large merge conflicts.
- Shared mutable test setup can hide coupling.
- Developers are less likely to add focused edge-case tests.

**Recommended change**

1. Split requisition tests into creation/editing, access, status workflow, concurrency/revision, and AI-notification suites.
2. Split report tests by monthly/YTD, gratis, scope, and comparisons.
3. Split HTTP security tests by resource while retaining one compact deny-by-default smoke test.
4. Move frontend tests beside features or group them by feature/behavior; keep shared test setup minimal.
5. Do not rewrite assertions during the file move.

**Tests needed**

- The test count and assertions should remain unchanged during the split.
- Run tests before and after and compare reports.
- Add isolation checks so each test file passes independently.

### RF-17 — Measure before introducing additional abstractions or dependencies

**Priority:** LOW  
**Regression risk:** LOW

**Files**

- `backend/pom.xml`
- `frontend/stecvi-solutions-komerc/package.json`
- `frontend/stecvi-solutions-komerc/vite.config.ts`
- all repository and API-client hotspots listed above

**Problem**

No clearly unnecessary production dependency was identified: Smile, JPA, Security, MVC, Validation, Mail, JWT, PostgreSQL, POI, PDFBox, Lombok, React, Router, Tailwind, and Lucide all have direct uses. The React Compiler/Babel build chain is intentionally configured, although it contributes build time. Removing dependencies merely to reduce the dependency count would be speculative.

**Why it matters**

- Premature framework replacement can create more risk than the current duplication.
- Query rewrites and generic CRUD abstractions can obscure business rules.
- Bundle warnings require measurement, not arbitrary package deletion.

**Recommended change**

1. Add dependency-update and vulnerability reporting in CI.
2. Record backend query latency and frontend bundle/route sizes before and after relevant refactors.
3. Evaluate React Compiler build cost separately from runtime benefit before changing it.
4. Prefer existing utilities and focused extraction over adding state-management, mapping, or generic repository frameworks.
5. Re-run unused dependency/static analysis after RF-07, RF-10, and RF-15 because those changes may make dependencies removable.

**Tests needed**

- Maven dependency analysis and test suite.
- Frontend production build, bundle comparison, lint, and full tests.
- Basic performance baseline for dashboard, customers, reports, requisitions, products, and AI analytics.

---

## Recommended implementation order

### Phase 0 — Establish a safe baseline

1. Record current API contracts, test counts, build outputs, and key performance timings.
2. Complete RF-01 secret rotation/externalization.
3. Complete the migration inventory in RF-02 without yet changing production startup behavior.
4. Add RF-03 PostgreSQL integration tests for the highest-risk queries.

**Exit criteria:** no credentials remain in tracked configuration; a clean database can be migrated; core repository contracts run against PostgreSQL; current unit/HTTP/frontend suites pass.

### Phase 1 — Low-risk structural cleanup

1. RF-14 controller package typo rename.
2. RF-15 dead registration removal and controller-interface correction.
3. RF-16 test-file split.
4. Add architecture/import checks that prevent the old structures from returning.

**Exit criteria:** no behavior or API contract changes; builds and test counts remain stable.

### Phase 2 — Backend business seams

1. RF-13 injected clock and zone, because report/requisition refactors need deterministic periods.
2. RF-05 requisition workflow/access/item synchronization extraction.
3. RF-04 report scope and pure aggregation extraction.
4. Refactor repository/query families only after integration tests cover them.
5. RF-12 validation and authorization normalization in the touched features.

**Exit criteria:** service classes coordinate use cases instead of containing all policies; formulas, role scopes, status transitions, response bodies, and exports are unchanged.

### Phase 3 — Frontend infrastructure and boundaries

1. RF-10 app-level session/error/download primitives.
2. RF-06 one request-state primitive and one polling wrapper.
3. RF-07 move AI/customer/session ownership to the correct layers.
4. Add import-boundary lint rules.
5. RF-09 split large management pages using the stabilized hooks.

**Exit criteria:** no duplicated request lifecycle implementation remains without a documented reason; `shared` no longer imports feature APIs/types; key workflows retain the same requests and URL behavior.

### Phase 4 — AI internals

1. Confirm RF-02 migrations and RF-03 AI PostgreSQL tests are stable.
2. Apply RF-08 to assessment calculation and persistence first.
3. Extract training/capture persistence only where tests demonstrate a clear seam.
4. Run the complete autonomous lifecycle and audit suites after every small move.

**Exit criteria:** AI behavior, schedules, dataset fingerprinting, gates, model artifacts, promotion decisions, audit records, and persisted assessments remain byte/field compatible where required.

### Phase 5 — Performance and delivery cleanup

1. RF-11 route-level code splitting.
2. RF-17 dependency/static analysis and performance comparison.
3. Optimize only queries/routes proven to miss agreed targets.

**Exit criteria:** smaller initial frontend payload, no slower critical endpoint, no new dependency without a measured requirement.

## Verification matrix for every refactoring pull request

At minimum, each implementation PR should run the relevant subset plus the final full suite:

### Backend

- `mvnw test`
- PostgreSQL migration and repository integration tests
- HTTP contract/security tests
- role/scope matrix tests
- serialization/error response snapshots
- AI lifecycle tests when any requisition, customer, product, time, transaction, schema, or AI file changes

### Frontend

- `npm run lint`
- `npm run build`
- `npm test`
- route/role smoke tests
- request cancellation/session-change tests when hooks or API clients change
- mobile and desktop workflow tests for modified pages

### Cross-application contracts

- Login and session expiration
- Customer pagination/search/status/risk filters
- Product management pagination and available-product catalog
- Requisition creation/edit/status/PDF/email flows
- Report JSON/export equivalence
- AI capture after completed normal requisitions
- AI list/summary/detail/activity authorization and response shapes

## Changes intentionally not recommended

- Do not move the Java Decision Tree implementation to Python merely to reduce Java package size.
- Do not replace Spring Data/JDBC with a new persistence framework.
- Do not introduce a generic CRUD page framework for the frontend management screens.
- Do not merge all authorization rules into one universal policy when role semantics differ by feature.
- Do not rewrite existing migrations or database tables as part of code organization.
- Do not change report formulas, AI thresholds, training gates, schedules, or model promotion rules during structural refactoring.
- Do not add a global frontend state library until a concrete cross-page state requirement exists.

