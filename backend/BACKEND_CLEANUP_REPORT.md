# Spring Boot backend cleanup report

## A. Summary

Implemented an incremental API cleanup while preserving Controller → Service → Repository, routes, authorization rules, JWT semantics, requisition transitions, row locking/revisions, customer rules, schema and reporting calculations.

- All four report exports now resolve their subject after the existing report service authorizes the requested scope. Headings and filenames identify the company, selected team or representative consistently.
- Added basic Bean Validation at previously inconsistent boundaries, including multipart product JSON. Employee/material activation requires explicit non-null booleans. Null nested list items fail validation instead of reaching service code.
- MVC and Spring Security share an additive JSON error contract. Intentional input exceptions are distinguished from unexpected programming failures.
- Employee/product creation returns 201 with existing response DTOs.
- Added 75 HTTP/JWT security tests and seven focused DTO tests. The existing context smoke test now boots without a live database.
- Added [API_CONTRACT.md](API_CONTRACT.md), covering endpoints, permissions, ownership, workflow, update semantics and error contracts.

No frontend source files, backend dependencies, SQL queries, entity mappings, schemas, incentive formulas or report aggregation methods were changed by this cleanup. Pre-existing uncommitted frontend and backend changes were preserved.

## B. Files changed

Paths below are relative to `backend`. This is the inventory for this task, not all changes already present in the shared working tree.

### Created

| File | Purpose |
|---|---|
| `src/main/java/com/ognjen/diplomski/auth/ApiError.java` | Shared public error record and factory |
| `src/main/java/com/ognjen/diplomski/auth/ApiErrorWriter.java` | Serialize security errors with the application JSON mapper |
| `src/main/java/com/ognjen/diplomski/exception/InvalidRequestException.java` | Explicit intentional client-input exception; extends IllegalArgumentException for existing unit-test/source compatibility |
| `src/main/java/com/ognjen/diplomski/service/ReportSubjectResolver.java` | Resolve display subject after report-service authorization |
| `src/test/java/com/ognjen/diplomski/contorller/ApiHttpSecurityTests.java` | Production filter-chain/JWT MVC tests, real report/requisition services, real Excel generation |
| `src/test/java/com/ognjen/diplomski/dto/RequestValidationTests.java` | Basic validation and compatibility regressions |
| `API_CONTRACT.md` | Endpoint and behavior documentation |
| `BACKEND_CLEANUP_REPORT.md` | This review report |

### Modified

| File under `src/main/java/com/ognjen/diplomski/` unless otherwise specified | Change |
|---|---|
| `auth/ApiExceptionHandler.java` | Consistent codes/field errors; HTTP binding/constraint/security exceptions; safe unexpected-error response |
| `config/SpringSecurityConfig.java` | Replace handwritten error JSON with shared writer; matchers unchanged |
| `contorller/AuthController.java` | Validate login body |
| `contorller/EmployeeController.java` | Validate write bodies; creation 201 |
| `contorller/ProductController.java` | Validate multipart product DTO; creation 201 |
| `contorller/AdvertisingMaterialController.java` | Validate activation body |
| `contorller/SalesReportController.java` | Apply resolver to sales/gratis monthly/YTD headings and filenames; retain excelResponse helper |
| `dto/LoginRequest.java` | Required nonblank credentials with existing public message |
| `dto/EmployeeCreateRequest.java` | Required nonblank first/last name, email, password and role |
| `dto/EmployeeUpdateRequest.java` | Required nonblank first/last name and email |
| `dto/EmployeeActiveRequest.java` | Boolean plus NotNull |
| `dto/AdvertisingMaterialActiveRequest.java` | Boolean plus NotNull |
| `dto/ProductCreateRequest.java` | Required name/code; required positive price/volume and nonnegative incentive rates; Serbian messages retained |
| `dto/RequisitionRequest.java` | Positive customer ID and non-null nested items; existing revision field/constructor preserved |
| `dto/GoodsRequestRequest.java` | Non-null nested items |
| `dto/CreateAdvertisingMaterialDistributionRequest.java` | Non-null nested items |
| `service/impl/EmployeeServiceImpl.java` | Classify deliberate validation rejections; guard nullable activation in direct calls |
| `service/impl/ProductServiceImpl.java` | Classify existing deliberate validation rejections; rules/messages preserved |
| `service/impl/AuthServiceImpl.java` | Classify existing deliberate validation rejections; authentication flow preserved |
| `service/impl/AdvertisingMaterialServiceImpl.java` | Classify existing deliberate validation rejections; guard nullable activation |
| `service/impl/SalesReportExcelServiceImpl.java` | Neutral subject caption `Izveštaj za`; remove redundant company subject overwrite; existing numeric rows unchanged |
| `src/test/java/com/ognjen/diplomski/DiplomskiApplicationTests.java` | Exclude datasource auto-configuration and mock all repositories for database-independent context smoke test |

Deleted files: **none**. No package renames or broad moves.

Already-modified files such as `RequisitionServiceImpl`, `RequisitionRepository`, sales report aggregation and their earlier tests were not edited in this task. Their existing behavior remains covered by the full suite. `RequisitionRequest` and the Excel renderer were changed narrowly as listed above, preserving earlier revision/incentive work.

## C. Excel exports

`SalesReportController` first obtains the report through the existing service. Only after successful scope authorization does `ReportSubjectResolver` resolve presentation names. The resolver does not filter data, calculate incentives or grant access.

| Scope example | Workbook subject and filename prefix |
|---|---|
| CEO, no filters | `Cela kompanija` / `Cela-kompanija-` |
| CEO, `managerId=8` for Mira Manager | `Tim menadžera Mira Manager` / `Tim-menadžera-Mira-Manager-` |
| CEO, `salesRepId=25` for Ana Rep | `Ana Rep` / `Ana-Rep-` |
| CEO, valid manager + representative selection | Selected representative, after team-membership authorization |
| MANAGER Mira Manager, no representative filter | `Tim menadžera Mira Manager` |
| MANAGER, selected own-team representative | That representative's full name |
| SALES_REP Ana Rep, implicit or explicit own ID | `Ana Rep` |

Monthly sales, YTD sales, monthly gratis and YTD gratis all use this resolution. Row 3 uses the neutral caption `Izveštaj za`, including company reports. Filenames retain existing period/type suffixes and safe Unicode filename handling. The requester's identity is not substituted for another subject; no separate “generated by” metadata was introduced.

Existing JSON `salesRep` fields retain their meaning. Company/team JSON responses still have null salesRep; representative responses retain the actual representative metadata. No new JSON subject schema was needed.

Thirty-two parameterized HTTP cases cover eight supported selection combinations × four exports. Each opens the actual workbook with Apache POI and asserts the exact caption and subject value, filename, media type, content length and no-store header. CEO workbooks must not display the requester's `Ceo Owner` name. Selected-manager sales cases verify the actual aggregation repository receives that manager ID.

The aggregation service and SQL were not modified. Existing service/POI regressions still verify company incentives (sales representative + manager), monthly/YTD numeric consistency, gratis totals and scoped calculations. COMPLETED inclusion and createdAt periods are preserved by the unchanged reporting code; the new HTTP suite uses mocked persistence and does not independently execute those SQL predicates.

## D. Validation

- Added Valid to login, employee writes, material activation and the multipart product JSON part.
- Both activation DTOs use nullable Boolean with NotNull. HTTP tests cover true, false, omitted and explicit null for both endpoints. Invalid inputs never reach the mocked mutation services. Services also reject null activation in direct calls.
- Product basic numeric/required-text constraints use existing Serbian messages. Existing precision, digit-count, trim-aware lengths and business rules remain service-owned.
- Employee/email/name/phone normalization and validation deliberately remain in the service where annotations would change accepted padded values or the existing email pattern. Basic nonblank DTO constraints do not duplicate these complex rules. Existing CustomerRequest Email/Size/Pattern constraints were reviewed and preserved.
- Requisition customer IDs must be positive. All three request item lists now reject null elements and retain nested Valid.
- Requisition quantities still accept fractional values with the existing minimum 0.01. No rounding, precision limit, integer-only requirement or quantity formula was introduced.
- Customer defaults/clamping remain page 0, size 50, page >= 0, size 1–100; HTTP regression verifies clamping.

Existing valid frontend field names, bodies and data types on the wire are preserved. Invalid requests may now fail earlier, with fieldErrors available.

## E. API errors

Final shared contract example:

```json
{
  "timestamp": "2026-10-23T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "Active value is required",
  "path": "/api/employees/5/active",
  "fieldErrors": {"active": "Active value is required"}
}
```

Security 401/403 and MVC responses have this structure; fieldErrors is empty when not relevant. Existing frontend-facing status and message fields remain intact. Existing controlled service messages, including Serbian customer messages and deliberate ResponseStatusException reasons, remain public; exception causes/rejected values are not serialized.

| Status | Code(s) / behavior |
|---|---|
| 400 | VALIDATION_FAILED, INVALID_REQUEST, MALFORMED_BODY, INVALID_PARAMETER |
| 401 | UNAUTHORIZED; preserves security/login message distinctions |
| 403 | FORBIDDEN |
| 404 | NOT_FOUND; preserves ownership concealment |
| 409 | CONFLICT; preserves workflow/revision/lock and duplicate-email messages |
| 500 | INTERNAL_ERROR; unclassified unexpected failures receive a generic message |

Framework ErrorResponse exceptions retain their HTTP statuses with safe status-name messages/codes. Intentional public ResponseStatusException reasons remain supported, including existing downstream failures. Known service input rejections now throw InvalidRequestException; unexpected IllegalArgumentException is no longer automatically classified as client input. Internal exception detail remains server-side. No correlation-ID infrastructure was added.

## F. Security

Only the authentication entry point and access-denied response serialization changed. The same ObjectMapper and ApiError shape are used for security errors and advice responses.

Request matcher ordering, roles, authorities, JWT signing/validation, user reloads, active-account checks and deny-by-default behavior are unchanged. No broad introduction of PreAuthorize or replacement of roles with permissions occurred. Report authorization runs before presentation lookup.

## G. HTTP tests

**All 75 cases in ApiHttpSecurityTests use WebMvcTest with filters enabled**, imported production SpringSecurityConfig/JwtAuthenticationFilter/JwtService, signed test JWTs and the actual user-details lookup against a mocked AppUserRepository. No WithMockUser, standalone-only security evidence or mocked SecurityFilterChain is used.

| Endpoints / scenarios | Expected results and tested behavior |
|---|---|
| GET customers | Missing/malformed JWT 401; disabled user 401; missing authority 403; same signed token with current database authority succeeds 200; pagination preserved |
| POST customers | SALES_REP 201; MANAGER/CEO/BOOKKEEPER 403; Serbian PIB validation error and fieldErrors 400 |
| POST employees | CEO 201; other tested roles 403; malformed JSON/blank fields 400; duplicate email 409; existing response fields preserved |
| POST products multipart | CEO 201; BOOKKEEPER/SALES_REP 403; invalid product JSON fields 400; existing response fields preserved |
| GET products | CEO/BOOKKEEPER 200; MANAGER/SALES_REP 403 |
| PATCH employee/material active | Explicit true/false reach operation; omitted/null 400; BOOKKEEPER administration 403 |
| PATCH product deactivate | BOOKKEEPER 403, operation not reached |
| POST login | Blank credentials 400; mocked authentication rejection 401 with compatible contract |
| GET monthly/YTD reports | Representative requesting another ID 403; manager selecting managerId 403; unrelated representative 404; CEO company/selected-manager/selected-representative 200 and correct repository scope/JSON metadata |
| Report exports | 32 real workbook cases described above; forbidden manager selection 403 before lookup/export |
| GET requisition detail | Unrelated representative/manager 404; missing resource 404, through real requisition service |
| PATCH requisition status | Representative manager-approval attempt 409; unrelated manager approval 409; stale revision and simulated database lock rejection 409; no save |
| POST requisition email | SALES_REP/MANAGER/CEO 403; BOOKKEEPER completed requisition 204, stored recipient and PDF forwarded to mocked delivery |
| Bad HTTP inputs | Invalid month/year/query, missing query, malformed customer ID 400; unknown status enum 400 |
| Error distinction | Mocked deliberate InvalidRequestException 400; mocked unexpected IllegalArgumentException 500 with generic message |

Real application components in this HTTP suite: MVC binding/validation, security/JWT/principal resolution, SalesReportServiceImpl, RequisitionServiceImpl, ReportSubjectResolver, SalesReportExcelServiceImpl and error handling.

Mocked boundaries: all repositories; customer/employee/product/material/goods/alert/auth services for their endpoint contract tests; requisition PDF generation and email delivery. Therefore employee/customer/product HTTP tests prove route/binding/response contracts, while their existing unit tests continue to prove business rules. Requisition/report HTTP tests execute actual service rules against controlled repository fixtures.

Existing standalone CustomerControllerValidationTests and direct RequisitionControllerTests were retained; they are not presented as security integration evidence. Requisition concurrency unit tests and the new HTTP lock-failure test simulate repository lock rejection, rather than running concurrent PostgreSQL transactions.

## H. API compatibility

- Only intentional successful status changes: POST employees and POST products, 200 → 201. Bodies remain the same DTOs; HTTP tests assert representative response fields. No new Location headers or GET routes.
- employees_api.ts and product_management_api.ts both use apiRequest. Its success check is Response.ok, not status === 200. Existing JSON, multipart and binary consumers tolerate additive error properties and continue reading message/status. No frontend edit is required.
- Activation DTO Boolean is a Java-side null-safety change; valid JSON remains boolean. Missing/null is now explicitly invalid.
- Required input and nested-item validation rejects malformed payloads earlier. Existing valid forms continue to send the same fields. Customer messages/validation and pagination are preserved.
- Existing optional expectedRevision and all routes/methods remain unchanged. No strict TypeScript flags, lazy routing, health semantics, new pagination, backend schema or dependencies were changed.

## I. Test results

Commands run from backend using the existing Maven wrapper and Java 24.0.1 (project target Java 21):

```powershell
./mvnw.cmd test
./mvnw.cmd '-Dtest=ApiHttpSecurityTests,RequestValidationTests,CustomerControllerValidationTests,CustomerRequestValidationTests,SalesReportServiceImplTests,SalesReportExcelServiceImplTests,RequisitionControllerTests,RequisitionServiceImplTests' test
git diff --check
```

- Full final suite: **210 tests, 0 failures, 0 errors, 0 skipped**.
- Final targeted suite: **142 tests, 0 failures, 0 errors, 0 skipped**.
- Within those results: 75 actual filter-chain HTTP tests (including 32 workbook export cases), 7 new DTO cases, 33 requisition service regressions, 14 sales-report service regressions, 4 existing Excel service tests, 2 requisition controller tests, 2 customer controller validation cases and 5 existing customer DTO cases.
- git diff --check: passed. Git reports a pre-existing frontend auth_provider.tsx LF→CRLF normalization notice; no whitespace errors.
- Frontend TypeScript/build: not rerun because no frontend files were changed in this task. Compatibility was inspected in the shared HTTP client and affected API modules, and backend HTTP response bodies/statuses are tested.
- Warnings: Lombok use of deprecated sun.misc.Unsafe, Mockito/Byte Buddy dynamic agent attachment, and JVM class-data-sharing restrictions. These are existing toolchain warnings, not failed tests. No dependency/toolchain upgrade was introduced.

Intermediate failures were corrected: a Spring test request-builder type mismatch, Mockito restubbing of a throwing method, and the redundant company subject-caption override detected by exact POI assertions. Final counts above reflect the corrected code.

An initial run of the existing context smoke test connected to the configured local PostgreSQL instance. It performed context startup, not targeted SQL/locking assertions. The smoke test was then changed to exclude datasource auto-configuration and mock repositories; the final full and targeted suites do not require a live PostgreSQL database. No schema changes or real email sends were made.

## J. Remaining concerns / unverified scenarios

- No concurrent-transaction PostgreSQL integration test was added. Actual NOWAIT lock behavior and SQL aggregation/date predicates still need a dedicated isolated database test environment; mocked tests must not be treated as proof of database locking execution.
- SMTP delivery, production proxy/container error dispatch, deployment upload limits and a browser-to-backend end-to-end session were not live-tested.
- The display resolver deliberately relies on the preceding authorized service call and resolves names afterward. It is a presentation helper, not a standalone authorization API.
- DTO annotations deliberately do not replace trim-aware employee/product lengths, custom employee email checks, UTF-8 password limits or database-dependent rules. These remain service-owned to avoid changing valid-input semantics.
- Revision preconditions remain optional for legacy callers. Enforcing them for every client is a separate API migration decision.
- No production security-policy, pagination, package-layout or dependency redesign was attempted.
