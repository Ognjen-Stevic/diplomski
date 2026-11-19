# Diplomski API Postman assets

This directory contains an importable Postman Collection v2.1 and a local environment generated from the current Spring Boot controllers.

## Files

- `Diplomski_API.postman_collection.json`
- `Diplomski_Local.postman_environment.json`

## Import and configure

1. In Postman, select **Import** and import both JSON files.
2. Select the **Diplomski Local** environment.
3. Keep `baseUrl` as `http://localhost:8080`, or change it to the URL of your safe local backend.
4. Never place production passwords or tokens in a shared/exported environment.

## Authentication

There is no refresh-token endpoint. Enter safe local credentials in the role-specific email/password variables:

- `ceoEmail` / `ceoPassword`
- `managerEmail` / `managerPassword`
- `salesRepEmail` / `salesRepPassword`
- `bookkeeperEmail` / `bookkeeperPassword`

Run the four requests in **00 Authentication Setup** individually. A successful response stores the JWT in the matching token variable and updates the generic `accessToken`. Login is rate-limited; repeated failures may return HTTP 429 until the configured window expires.

The generic API reference login uses `loginEmail` and `loginPassword`.

## Role and permission overview

The collection selects a representative role token for each request. The effective production rules are:

| Module | Read access | Write access |
| --- | --- | --- |
| AI analytics | CEO, MANAGER, SALES_REP with `customers.read` | AI activity creation is SALES_REP-only and ownership-scoped |
| Customers | CEO, MANAGER, SALES_REP with `customers.read` | SALES_REP creates/updates; MANAGER changes status or ownership |
| Requisitions | Roles with `requisitions.read` (currently CEO, MANAGER, SALES_REP, BOOKKEEPER) | SALES_REP creates; workflow updates depend on SALES_REP/MANAGER/BOOKKEEPER; email is BOOKKEEPER-only |
| Products | Management list: CEO, BOOKKEEPER; available list: SALES_REP, MANAGER | CEO only |
| Sales reports | CEO, MANAGER, SALES_REP with `sales-reports.read` | No report write endpoints |
| Goods requests | CEO, MANAGER, SALES_REP, BOOKKEEPER with `goods-requests.read` | SALES_REP creates; BOOKKEEPER processes |
| Advertising materials | History/available: CEO, MANAGER, SALES_REP with permission; management list: CEO | CEO manages catalog; SALES_REP records distributions |
| Employees | Security requires `employees.read`; individual service rules further restrict endpoints to CEO or CEO/MANAGER | CEO only |
| Alerts | Any authenticated user | None |

## Required IDs

Populate IDs only from your local test database:

- `customerId`: an accessible customer; write scenarios generally require an APPROVED customer owned by the SALES_REP.
- `productId` and `productCode`: an existing product and an active product code.
- `requisitionId`: an accessible requisition in the state required by the operation.
- `requisitionRevision`: copy `revision`/the expected revision value from the current requisition response before an update.
- `goodsRequestId`: a request with pending items for approval/rejection tests.
- `advertisingMaterialId`: an active material for distribution tests.
- `employeeId`, `salesRepId`, and `managerId`: valid local users in the indicated roles.

Read-only list endpoints can help locate IDs, but visibility is role-scoped.

## Safe GET testing

Start the backend, authenticate, and run requests in **01 API Reference** that use GET. PDF and XLSX requests return binary attachments; use Postman's **Save Response to file** option.

Goods-request and advertising-distribution monthly history endpoints are intentionally unpaginated because that is their current backend contract. Pagination tests exist only for customers, AI risk pages, products, and requisitions.

## Write operations and cleanup

Requests prefixed **[SIDE EFFECT]** are never executed automatically by these artifacts. Run them one at a time only against a disposable local database.

Before sending:

1. Replace example PIBs, emails, product codes, and names if they already exist.
2. Confirm all referenced IDs belong to disposable data.
3. Record created IDs from responses.
4. Clean up using your approved local database process. The REST API has no general DELETE endpoints, and deactivation/status transitions may not be reversible.

The product-create request is multipart. Select an image manually if desired; the image part is optional.

The requisition email request is labeled **[SIDE EFFECT: SENDS EMAIL]**. It uses configured SMTP and can contact a real recipient. Do not send it unless SMTP and recipient data are explicitly safe.

## Automated tests

Each request contains focused Postman tests for applicable status codes and response structure. Pagination requests check metadata and server page limits. Run selected read-only folders with Collection Runner after obtaining tokens.

Do not run the complete collection as a batch: it contains state-changing requests whose prerequisites and ordering depend on local data.

## Authorization scenarios

**90 Authorization Scenarios** contains:

- Authenticated and missing-token checks.
- A positive manager customer-read check.
- Negative role checks expected to return 403.

The negative POST cases are designed to be rejected before service mutation, but should still be run only in a safe local environment.

## Common failures

- **400 Bad Request**: malformed JSON, failed Jakarta validation, invalid enum/query value, or invalid business input.
- **401 Unauthorized**: missing, expired, incorrectly signed, wrong-issuer, or wrong-audience JWT; inactive users are also rejected.
- **403 Forbidden**: authenticated but missing the required role/permission or outside an operation's role rules.
- **404 Not Found**: entity absent or intentionally hidden by ownership scope.
- **409 Conflict**: invalid workflow transition, revision conflict, duplicate data, or incompatible current state.
- **429 Too Many Requests**: login rate limit exceeded.
- **503 Service Unavailable**: AI schema/migrations are unavailable for AI endpoints.

## Manual setup scenarios

Manual data setup is usually required for:

- Customer ownership/team visibility.
- Requisition workflow transitions and revision values.
- Completed requisition PDF generation and email delivery.
- Pending goods requests.
- AI assessments and AI activities.
- Report periods containing data.
- Product image file selection.
- Advertising distributions involving an owned APPROVED customer.

No request was executed against the backend while generating these files.

## Artifact validation

The generated artifacts were checked as JSON, the collection declares the Postman Collection v2.1 schema, all test scripts compile as JavaScript, all example JSON bodies parse after environment substitution, and all referenced variables exist. The 57 unique API-reference requests were compared with the 57 active mappings found across 11 controllers; no missing or duplicate reference endpoint was found.


