# Backend API contracts and authorization

This documents the existing API after the incremental consistency cleanup. All paths below are relative to `/api`. The `contorller` package name, routes, workflow, role/permission model, database schema, and report calculations are preserved.

## Authentication and authorization

Except for login and health, API requests require a valid bearer JWT. The production JWT filter reloads the user and current authorities from the repository and rejects disabled accounts. Request matchers establish endpoint access; services additionally establish ownership, team membership, permitted workflow transitions, and report scope. Passing a URL matcher never grants access to an unrelated resource.

| Methods and paths | Endpoint gate | Additional service restrictions / purpose |
|---|---|---|
| `POST /auth/login` | Public | Authenticate supplied credentials; blank credentials are invalid input |
| `GET /auth/me` | Authenticated | Current principal |
| `GET /health` | Public | Existing basic response check; unchanged |
| `GET /alerts` | Authenticated | Role-specific alert computation |
| `GET /customers`, `GET /customers/{customerId}` | `customers.read` | Role-specific visibility and ownership; inaccessible resources return 404 |
| `GET /customers/team-sales-representatives` | `customers.read` | MANAGER; active representatives in own team |
| `POST /customers`, `PUT /customers/{customerId}` | SALES_REP | Creation belongs to requester; edits require accessible owned customer |
| `PATCH /customers/{customerId}/status` | MANAGER | Customer must belong to own team |
| `PATCH /customers/{customerId}/sales-representative` | MANAGER | Customer and active target representative must belong to own team |
| `GET /employees`, `GET /employees/manager-options`, `GET /employees/managers` | `employees.read` | CEO administration/overview |
| `GET /employees/team-summary` | `employees.read` | MANAGER own team; CEO supplies `managerId` |
| `POST /employees`, `PATCH /employees/{employeeId}`, `PATCH /employees/{employeeId}/active` | CEO | Managed employee roles only; assignment/deactivation restrictions remain service-owned |
| `GET /products` | CEO or BOOKKEEPER | Management catalog |
| `GET /products/available` | SALES_REP or MANAGER | Active product selection |
| `POST /products` | CEO | Multipart JSON `product` part; optional `image` part; existing storage/content/amount rules |
| `PATCH /products/{productId}/deactivate` | CEO | Existing one-way deactivation operation, no new request body |
| `GET /requisitions`, `GET /requisitions/{requisitionId}`, `GET /requisitions/customer/{customerId}`, `GET /requisitions/{requisitionId}/pdf` | `requisitions.read` | Role, ownership, team and status visibility checked in service |
| `POST /requisitions` | SALES_REP | Own approved customer, existing availability/gratis rules |
| `PUT /requisitions/{requisitionId}` | SALES_REP or MANAGER | Editable state and ownership/team required |
| `PATCH /requisitions/{requisitionId}/status` | SALES_REP, MANAGER or BOOKKEEPER | Only transitions allowed for the role and current state |
| `POST /requisitions/{requisitionId}/email` | BOOKKEEPER | Accessible COMPLETED requisition; stored CUSTOMER or SALES_REP recipient address required |
| `GET /sales-reports/monthly`, `/sales-reports/ytd` | `sales-reports.read` | Authorized company/team/representative scope, described below |
| `GET /sales-reports/gratis-monthly`, `/sales-reports/gratis-ytd` | `sales-reports.read` | Same existing scope restrictions; gratis aggregation unchanged |
| `GET /sales-reports/monthly/export`, `/sales-reports/ytd/export`, `/sales-reports/gratis-monthly/export`, `/sales-reports/gratis-ytd/export` | `sales-reports.read` | Same report service authorization and calculations as JSON; scoped Excel subject |
| `GET /sales-reports/team-comparison` | `sales-reports.read` | MANAGER own team; CEO selects manager |
| `GET /sales-reports/managers-comparison` | `sales-reports.read` | CEO only |
| `GET /goods-requests` | `goods-requests.read` | CEO/BOOKKEEPER company, MANAGER team, SALES_REP own |
| `POST /goods-requests` | SALES_REP | Existing product/material and quantity rules |
| `PATCH /goods-requests/{id}/status` | BOOKKEEPER | Process pending items; existing status/conflict rules |
| `GET /advertising-materials` | CEO | Management catalog |
| `GET /advertising-materials/available` | `advertising-materials.read` | Available material selection |
| `POST /advertising-materials`, `PATCH /advertising-materials/{materialId}/active` | CEO | Existing uniqueness and catalog rules |
| `POST /advertising-materials/distributions` | SALES_REP | Own eligible customer and available materials |
| `GET /advertising-materials/distributions` | `advertising-materials.read` | CEO company, MANAGER team, SALES_REP own |

`/api/auth/register` remains denied. `/products/**` is the existing public static image path, outside `/api`. Unmatched requests remain denied by security. No new endpoint access is granted by this document.

## Periods and pagination

Monthly API inputs require `year` in 2000–2100 and `month` in 1–12. YTD requires the same year range. Report inclusion remains COMPLETED requisitions grouped by `createdAt`; SQL, aggregation, monetary formulas, YTD end-date semantics and company incentive breakdowns were not changed.

Customers retain `page=0`, `size=50` defaults. Negative pages normalize to zero; sizes clamp to 1–100. No new pagination was added to other endpoints.

## Report subject and export metadata

The report service authorizes scope first. `ReportSubjectResolver` then resolves the presentation label, without changing selection or calculation:

| Requester and selection | Subject |
|---|---|
| CEO, no selection | `Cela kompanija` |
| CEO, selected manager | `Tim menadžera {manager firstName} {manager lastName}` |
| CEO, selected representative, with or without that representative's manager filter | Representative's full name |
| MANAGER, no representative selection | Requester's own team |
| MANAGER, selected own-team representative | Representative's full name |
| SALES_REP, no selection or explicit own ID | Requester's full name |

Existing service checks reject unrelated or forbidden selections before subject resolution. In all four export types, row 3 uses `Izveštaj za` and the resolved subject; attachment filenames use that subject with punctuation/spaces normalized. The requester's name is never substituted for a selected report subject. Existing JSON `salesRep` metadata is preserved; no JSON scope fields were added. Company/team reports still have `salesRep: null`.

## Employee PATCH and activation

`PATCH /employees/{employeeId}` retains the existing edit-form semantics: first name, last name and email are required. It is not a generic merge-patch operation. Phone and manager assignment follow existing service rules. Role and password cannot be changed by this operation; non-null values for them are rejected. Creation still restricts roles and uses the existing trim-aware lengths/email pattern and password UTF-8 byte limit.

Both employee and advertising-material `.../{id}/active` requests require an explicit JSON boolean:

```json
{"active": false}
```

`true` and `false` are accepted as explicit inputs. `{}` and `{"active":null}` return 400 before service mutation. Employee deactivation still conflicts when a manager has assigned representatives or a representative has assigned customers. Product deactivation remains its existing bodyless endpoint.

## Requisition workflow and conflicts

Visibility remains role- and status-dependent. Representatives see their own requisitions; managers see their team's non-drafts; bookkeepers see the existing bookkeeping-visible states; CEO visibility remains unchanged.

| Actor / current state | Existing allowed status targets |
|---|---|
| Owning SALES_REP / DRAFT or REJECTED | SUBMITTED |
| Owning MANAGER team / SUBMITTED | APPROVED, REJECTED, CANCELLED_BY_MANAGER |
| Owning MANAGER team / REJECTED_BY_BOOKKEEPER | APPROVED, CANCELLED_BY_MANAGER |
| BOOKKEEPER / APPROVED | COMPLETED, CANCELLED_BY_BOOKKEEPER, REJECTED_BY_BOOKKEEPER, PROCESSING |
| BOOKKEEPER / PROCESSING | COMPLETED, CANCELLED_BY_BOOKKEEPER, REJECTED_BY_BOOKKEEPER |

Rejections require the existing note. Illegal transitions continue to return **409**, including a representative attempting manager approval; the endpoint itself intentionally admits several workflow roles. Ownership-filtered reads may return 404 instead of disclosing resource existence.

Mutation operations retain their existing parent-row pessimistic lock. Lock contention and stale revision tokens produce 409. `expectedRevision` is currently optional for legacy clients: full stale-edit detection requires sending the revision received with the requisition. Locking alone does not replace that precondition. This cleanup does not change revision generation, lock queries or transaction boundaries.

## Success and error responses

- Resource creation: **201** with existing response DTO; employee and product creation changed from 200 to 201.
- Reads, updates and exports: **200** with existing DTO or binary body.
- Successful requisition email: **204**, no body.
- No artificial `Location` headers: employee/product detail GET endpoints were not introduced.

All applicable MVC/security errors use this compatible contract:

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

`fieldErrors` is an empty object when not applicable. The existing `status` and `message` fields remain available. Existing deliberate service rejection reasons, Serbian customer messages, login errors and security messages are retained where applicable. New DTO constraints may reject malformed inputs earlier than before.

| Status | Typical stable codes | Meaning |
|---|---|---|
| 400 | `VALIDATION_FAILED`, `INVALID_REQUEST`, `MALFORMED_BODY`, `INVALID_PARAMETER` | DTO validation, deliberate input rejection, invalid JSON/enum, invalid query/path |
| 401 | `UNAUTHORIZED` | Missing/invalid/disabled-account JWT or failed login |
| 403 | `FORBIDDEN` | Insufficient endpoint authority or explicit service denial |
| 404 | `NOT_FOUND` | Missing or intentionally concealed inaccessible resource |
| 409 | `CONFLICT` | Workflow/revision/lock/deactivation or duplicate-email conflict |
| 500 | `INTERNAL_ERROR` | Unexpected failure with a generic public message |

Framework statuses such as 405, 413 and 415 are preserved with their status-name code. Deliberate `ResponseStatusException` reasons retain their public messages; exception causes are never serialized. Unexpected exceptions, including unclassified `IllegalArgumentException`, return a generic 500. Known intentional input rejections now use `InvalidRequestException`, a compatible subtype of `IllegalArgumentException`. Correlation IDs were not added.

Excel/PDF responses retain media types, attachment headers, content lengths and `Cache-Control: no-store`. JSON and binary frontend clients continue to accept all successful 2xx responses through `Response.ok`.
