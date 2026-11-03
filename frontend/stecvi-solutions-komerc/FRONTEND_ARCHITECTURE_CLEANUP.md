# Frontend architecture cleanup - implementation report

## A. Summary

Implemented the targeted cleanup within the existing feature architecture. Fixed CEO dashboard incentive consumption, customer selection/edit lifetime, employee save/revalidation semantics, and authenticated request/session handling. Added durable Vitest + React Testing Library tests, accessible dialog behavior, render-error recovery, and Maps retry.

No backend, schema, product data, workflow rules, strict TypeScript options, or route-level lazy loading were changed in this task. Existing uncommitted backend/report/requisition work predating this task was preserved.

## B. Files changed

Paths below are relative to this frontend project. This inventory describes this task, not every pre-existing uncommitted file in the workspace.

Created:
- FRONTEND_ARCHITECTURE_CLEANUP.md (this report)
- vitest.config.ts
- src/app/error_boundary.tsx
- src/shared/components/session_scope.tsx
- src/shared/hooks/use_session_request.ts
- src/shared/hooks/use_dialog.ts
- src/shared/utils/asset_url.ts
- src/test/setup.ts
- src/test/auth.test.tsx
- src/test/reliability.test.tsx
- src/test/contracts.test.ts
- src/test/http.test.ts
- src/test/requisition-flow.test.tsx
- src/test/maps.test.tsx

Moved and updated:
- src/features/dashboard/components/customer_search.tsx -> src/features/customer/components/customer_search.tsx
- src/shared/hooks/use_products.ts -> src/features/product/hooks/use_products.ts

Modified:
- package.json
- package-lock.json
- src/app/app.tsx
- src/app/routes.ts
- src/features/auth/auth_api.ts
- src/features/auth/auth_context.ts
- src/features/auth/auth_provider.tsx
- src/features/auth/components/auth_route.tsx
- src/features/customer/customer_api.ts
- src/features/customer/use_customer_selector.ts
- src/features/customer/components/google_maps_picker.tsx
- src/features/customer/pages/customers_page.tsx
- src/features/customer/pages/new_customer_page.tsx
- src/features/dashboard/hooks/use_monthly_dashboard_stats.ts
- src/features/dashboard/pages/home_page.tsx
- src/features/employees/employees_api.ts
- src/features/employees/pages/employees_page.tsx
- src/features/employees/components/employee_form.tsx
- src/features/employees/components/employee_status_confirmation.tsx
- src/features/product/product_api.ts
- src/features/product/hooks/use_product_catalog.ts
- src/features/product/pages/products_management_page.tsx
- src/features/product/components/product_form.tsx
- src/features/product/components/product_management_card.tsx
- src/features/product/components/product_deactivation_confirmation.tsx
- src/features/goods_request/hooks/use_monthly_goods_requests.ts
- src/features/goods_request/pages/goods_request_page.tsx
- src/features/goods_request/pages/new_goods_request_page.tsx
- src/features/advertising_materials/hooks/use_advertising_material_catalog.ts
- src/features/advertising_materials/hooks/use_advertising_material_history.ts
- src/features/advertising_materials/hooks/use_available_advertising_materials.ts
- src/features/advertising_materials/pages/advertising_material_management_page.tsx
- src/features/advertising_materials/pages/new_advertising_material_distribution_page.tsx
- src/features/sales_team/hooks/use_team_comparison.ts
- src/features/sales_team/hooks/use_team_summary.ts
- src/features/report/report_api.ts
- src/features/report/types/sales_report.types.ts
- src/features/report/utils/report_navigation.ts
- src/features/report/components/report_table.tsx
- src/features/requisition/pages/new_requisition_page.tsx
- src/features/requisition/pages/edit_requisition_page.tsx
- src/features/requisition/pages/requisition_page.tsx
- src/features/requisition/pages/requisition_history_page.tsx

There are no additional intentional deletions. The two old paths above are replaced by their moved files.

## C. CEO Dashboard

Sales report responses now expose normalized numeric `metrics` alongside existing presentation summaries. Dashboard code reads `metrics.totalIncentive`, `totalLiters`, `totalBottles`, and `totalJugs` directly. It no longer looks up card IDs or requests/adds manager incentives separately. The backend company total already includes representatives and managers, so consuming it directly counts both exactly once.

Sales representative and manager dashboards consume the same role-scoped API totals. Bookkeeper statistics retain their requisition-based behavior. No server incentive formulas were changed. Tests cover company total 300 = 240 + 60, empty presentation summaries, and report mapper consistency.

## D. Customer

The selector associates preselection and searches with the current customer ID, session and query. Old data is hidden before effects run; obsolete requests are aborted. Clearing the ID resets preselection. Search remains debounced by 250ms and errors/loading are exposed to requisition/material consumers. Manual choice overrides preselection without allowing a late preselection result to replace it.

Create/edit form lifetimes are keyed by route ID and opaque session. The editing form is unavailable until its own customer has loaded, so A's fields cannot be submitted with B's ID. Reads are cancellable, existing validation/normalization is retained, and an immediate guard prevents duplicate saves. Late saves do not navigate after unmount.

Customer collection results and selected details are associated with their query context. Search drafts remain local; submitted search and pagination remain URL-driven. Mutations reload authoritative lists without retaining a customer excluded by the refreshed collection.

## E. Employees

One cancellable request loads employees and manager options; initial load and retry use the same path. Save success is finalized before independent list revalidation. On successful write the form closes, then both lists reload. A refresh failure produces list/revalidation feedback rather than a false save failure. Mutation failures keep their relevant form/dialog open. Immediate guards prevent duplicate writes and dismissal during saving.

Creation is driven by `action=new`; edit selection uses `action=edit&employeeId=...`. Opening/closing preserves unrelated parameters and pushes history entries. Selected edit fields are derived from the matching current collection and keyed by URL identity. Role restrictions remain CEO plus existing permission checks. Session boundaries discard old forms/dialogs.

## F. Other features

- Product catalog and available-product hooks now use cancellable, session-associated results with retry and re-enable invalidation. Management forms reset across sessions and late mutations cannot close another session's URL-driven modal.
- Goods requests retain domain operations and period UI. Reads include opaque session identity. Pending decisions and busy state belong to their request context; obsolete mutation completions cannot refresh another context or navigate another session.
- Advertising catalog, availability and history use the same small lifecycle helper while keeping their domain APIs/mutations. Creation navigation is guarded against unmount/session changes.
- Sales-team hooks retain their established operations, cancellation and return shapes; session identity was added to their existing result identities.
- Existing report/requisition lifecycle implementations were retained. Requisition consumers were updated for moved product hooks and route builders. Status transitions and backend authorization were not altered.

## G. Authentication

The provider subscribes to local credential changes and browser storage events through `useSyncExternalStore`. Other-tab logout clears the old authenticated UI; account changes invalidate old user state immediately and restore the new account. Restoration requests are aborted/checked against the current token.

401 restoration invalidates only the matching credential. Network/server failures preserve the credential and expose a Serbian recovery message with retry/logout controls. A completed failed attempt does not automatically loop. Existing authenticated route protection remains in force.

Features receive an opaque `sessionId` object rather than needing the JWT to identify their requests. `SessionScope` resets unsaved UI when that identity changes. Existing token-specific 401 invalidation remains intact.

## H. API and architecture

Customer and employee JSON operations now use the existing shared `apiRequest`; product availability does too. DTO normalization stays in each feature. Customer status mapping uses an explicit enum map and rejects unknown statuses before exposing actions. Existing upload/FormData, binary and no-content behavior is preserved and covered by tests.

Dynamic customer-edit, requisition-edit and customer-history route builders live in `APP_ROUTES`. Existing URLs remain valid. Report year parsing now matches backend 2000-2100 validation.

CustomerSearch belongs to customer. Product catalog loading belongs to product. Product image URL resolution moved to a shared asset utility and callers were updated. No new barrel hierarchy or query/state framework was added.

The small shared request helper handles identity/cancellation/retry only. Domain hooks still choose endpoints, parameters and feature-specific return shapes. Working report/requisition hooks were not replaced wholesale.

## I. Accessibility and recovery

Product/employee forms and their status confirmations adopt shared initial focus, focus containment/restoration, Escape handling and nested scroll-lock behavior. Saving blocks dismissal. Existing Tailwind markup and Serbian labels are retained. Other complex dialogs keep their established implementation to limit regression risk.

A React error boundary catches routed render failures and offers a Serbian fallback with reload/home recovery. Unknown URLs show a not-found page instead of an unexplained home redirect. API errors remain separate from render failures.

The Maps loader removes failed scripts, clears its rejected shared promise and exposes retry. Shared pending loads prevent duplicate scripts; unmounted instances ignore completions/location callbacks.

## J. Tests and verification

Repeatable commands:
- npm test (Vitest + React Testing Library, jsdom)
- npm run test:watch (interactive)
- npx tsc -b --pretty false
- npm run lint
- npm run build
- git diff --check

25 committed tests across six files pass. They cover dashboard numeric totals and renamed/missing card IDs; selector changes/clear/debounce; stale sessions; actual customer edit navigation and duplicate-save prevention; employee save followed by manager-refresh failure; employee modal history; report year/legacy parsing; requisition list reconciliation; failed status confirmation retry; role visibility; dialog focus/Escape; auth restoration/401/cross-tab changes; enum rejection; dashboard alerts; binary Excel cleanup; multipart/no-content; token-specific late 401; asset resolution; and Maps retry.

TypeScript and full ESLint pass. Production build passes with existing bundle-size and plugin-timing warnings. git diff --check passes after whitespace cleanup. Initial test selector/fixture issues and compile/lint findings were corrected before final verification.

No backend changes were made, so Maven was not rerun for this frontend task. These are component/unit tests in jsdom, not real-browser/E2E verification. Live backend authorization, real email delivery, browser downloads, mobile visual layouts and real Google Maps interactions were not exercised.

## K. Remaining concerns

- npm install reported nine dependency advisories overall. A production-only npm audit reports six (one moderate, five high). Broad dependency upgrades/audit fixes were intentionally not mixed into this task.
- Existing complex dialogs outside the incremental migration retain their current behavior; a full accessibility/browser audit remains useful.
- Test coverage targets the highest-risk flows, not every feature/role/endpoint combination. Backend authorization remains authoritative.
- Other existing local filter/period controls were not redesigned; the requested report/requisition/customer navigation and employee modal cases are preserved or corrected.
- Strict compiler changes and route-level lazy loading remain explicitly excluded.
