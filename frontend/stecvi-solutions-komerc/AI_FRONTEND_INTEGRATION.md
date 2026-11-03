# AI frontend integration

The existing React application now includes `/ai-analitika` via APP_ROUTES.ai and a role/permission-aware applicationNavigation entry in the desktop menu and mobile menu. No second frontend, strict-mode change, chart framework or lazy routing was introduced.

## Created files

Under src/features/ai:

- risk_api.ts: typed API access through the existing authenticated HTTP client.
- risk_labels.ts: Serbian risk and feature labels.
- use_risk_batch.ts: one cancellable batch read per customer page, with session identity.
- risk_components.tsx: badges, role-scoped summary, customer metrics/tree path and recorded outcomes.
- components/ai_customer_filters.tsx: responsive customer search and risk filtering; reuses CustomerSearch and shares AiRiskFilter with the customer list. Search submits on Enter/button and preserves other URL filters.
- risk_trends.tsx: accessible history table with counts, dates and model versions.
- admin_tools.tsx: CEO capture/training/job status/model metrics/explicit activation.
- ai_page.tsx: shared role-scoped analytics, global server filters, priorities, customer links and detail section.

Added src/test/ai.test.tsx with eight focused RTL cases. Existing Vitest setup is reused.

## Modified integration points

- app/routes.ts and app/app.tsx register the page.
- app/navigation/application_navigation.ts defines AI navigation for SALES_REP, MANAGER and CEO with customers.read. The header does not duplicate that link.
- dashboard/pages/home_page.tsx adds own/team/company summary according to the principal.
- customer/customer_api.ts optionally calls the risk-filtered catalog endpoint while preserving the existing DTO mapper and normal endpoint.
- customer/pages/customers_page.tsx adds risk badges, URL risk filter, existing search/pagination and customerId deep-link selection. Filtering occurs before pagination on the server, not just over visible cards.
- customer/components/customer_details_panel.tsx embeds the AI analysis and outcome history.
- report/pages/report_page.tsx keeps sales reports separate from AI Analytics; its former AI summary section has been removed.
- sales_team/pages/team_page.tsx adds company/selected-team overview alongside existing drill-downs.

All paths above are relative to src/features unless prefixed app/. Existing customer editing/status/assignment operations remain in their existing controls. Risk rows link to the existing customer page with search/customerId parameters. CEO team links narrow AI scope; existing team/report screens retain their own drill-down behavior.

## States, safety and meaning

The new UI handles loading, retryable errors, empty filters, missing captures, missing models, insufficient customer histories and stale assessments. INSUFFICIENT_DATA is visually distinct from actual LOW/MEDIUM/HIGH. Missing data is never displayed as a learned risk percentage. Summary high-risk percentages describe the fraction among assessed customers, not model confidence. Historical high-risk liters are clearly labeled as past volume, not predicted financial loss.

useSessionRequest keys every query by customer/filter and opaque session. The customer activity form is inside SessionScope and keyed by customer; switching customer/account discards drafts and late responses cannot navigate/update a different context. An immediate ref guard prevents duplicate activity requests. The server derives actor/assessment references and rechecks ownership. Only representatives see the contact outcome form; managers/CEO can review authorized outcomes. Successful writes reload authoritative history.

CEO operations do not train inside a normal list fetch. They submit an explicit background job, poll status, inspect actual evaluation metadata, then separately activate an eligible model. Job tracking is process-local and may disappear on backend restart; model metadata/artifacts persist. After activation the CEO must refresh assessments.

The trend is a table rather than a new chart primitive. It reports the latest assessment per customer/day and exposes model versions to avoid silently comparing differently trained classifiers. The UI does not implement a planned-task/reminder system, causal treatment recommendations, or learned intervention effectiveness. Outcome recording is append-only. Dedicated forecasted revenue/loss displays were intentionally not invented.

No browser screenshot/E2E run was performed. TypeScript, ESLint, production build and component tests are the verified frontend evidence. The existing main-bundle-size warning remains.
