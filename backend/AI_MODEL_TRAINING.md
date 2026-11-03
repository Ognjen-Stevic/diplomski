# Decision Tree training and first activation

## Definitions

Feature schema version 1 has eight numerical fields, in fixed order:

| Feature | Definition at snapshot t |
|---|---|
| recencyDays | Elapsed days since last known paid completed order's createdAt |
| intervalDays | Mean interval between paid orders created in the last 90 days |
| orders30, orders60, orders90 | Distinct paid order counts in (t-window, t] |
| liters60 | Paid liters during (t-60d,t] |
| priorLiters60 | Paid liters during (t-120d,t-60d] |
| relationshipDays | Elapsed days since customer createdAt |

Eligibility requires relationship >=90 days, >=3 paid orders in 90 days and positive preceding 60-day liters. These are explicit data-sufficiency rules, not the learned classifier. Missing/insufficient history produces INSUFFICIENT_DATA, not LOW risk. If eligible history exists but there is no validated active model, status is MODEL_UNAVAILABLE. With no capture, status is NOT_ASSESSED.

Only NORMAL/COMPLETED requisitions enter observation collection. Quantities × immutable product volume produces liters; aggregation is per requisition before counting orders. Gratis, goods requests and advertising materials are excluded. Business createdAt and earliest observed completion timestamps are retained separately.

Snapshot features use only orders created and observed by t. Snapshots are recorded now, never backdated. Future outcomes require createdAt in (t,t+60d] and completed observation no later than t+60d. HIGH means no such order; MEDIUM means positive future activity but liters below 50% of preceding liters; LOW otherwise. This is an operational outcome definition, not a claim of objectively universal risk categories. The 60-day horizon and 50% threshold need business review before operational reliance.

## Leakage controls and evaluation

- Current mutable requisition status is not used to invent historical snapshots.
- One customer snapshot is retained approximately every 60 days; the dataset builder additionally removes overlapping examples defensively.
- Labels must mature through their entire 60-day horizon.
- Successful capture coverage must span that horizon with no gap above 36 hours and a final capture within 24 hours of its end. Missing observation coverage excludes the example rather than creating a false HIGH label.
- The sorted 70% timestamp defines the evaluation boundary. Training outcome windows must end strictly before that boundary; examples crossing it are purged. Evaluation examples start at/after it.
- Returning customers may appear in both periods; IDs are not features. This measures future performance for the operating customer population, not generalization to unseen customers. Seasonality and population drift remain limitations.
- At least 150 mature non-overlapping examples are required before splitting; after purging require >=90 training and >=30 evaluation, with >=10 examples per class in both.

Smile CART uses GINI, maximum depth 5, maximum 16 leaves and minimum leaf size 10. No hyperparameter search touches the holdout. The baseline is an explicit recency rule: >60 days HIGH, >30 MEDIUM, otherwise LOW. Metrics include actual confusion matrix, per-class precision/recall/F1, macro-F1, class distributions and sample counts. Metadata records training outcome end, evaluation start, observed-through timestamp and algorithm/version.

Candidate eligibility requires macro-F1 greater than baseline +0.02 and each class recall >=0.4. These are conservative engineering gates, not statistical proof of clinical/business reliability. No confidence interval, calibration or intervention-benefit validation is implemented; probabilities are not exposed. If the tree fails the gates, the candidate and actual metrics are retained but activation is rejected. Insufficient data fails the job without producing a pretend model.

## First production model: exact workflow

1. Apply V001 then V002 with `psql -v ON_ERROR_STOP=1 -f ...` to the intended database. Do not enable Hibernate automatic DDL. Back up and deploy through the application's normal database process.
2. Start the backend and frontend. Log in as CEO. Open AI Analytics → administration.
3. Run **Prikupi podatke / osveži procene**, equivalent to `POST /api/ai/admin/refresh` with the existing bearer JWT. Poll `GET /api/ai/admin/jobs/{jobId}` until COMPLETED. Inspect failures; do not silently skip them.
4. Arrange a successful capture about every 24 hours. This module does not install an external scheduler or auto-train. Early deployment UI correctly shows missing-model or insufficient-data states.
5. Continue collection until mature, non-overlapping chronological train/evaluation data with all classes exists. With three 60-day-separated observation cohorts, allow roughly 180+ days after rollout; elapsed time alone does not guarantee enough data/classes or an eligible model. Existing customer/order age does not remove the prospective collection requirement.
6. Run **Pokreni obuku**, equivalent to `POST /api/ai/admin/train`, and poll the returned job. Review confusion matrix, all class metrics, sample counts, chronological dates and baseline comparison in the actual result and `/api/ai/admin/models`.
7. If validation fails or data is insufficient, do not activate a model. Investigate data coverage and business definition; do not fabricate examples or weaken gates merely to obtain a demo.
8. If eligible, explicitly activate the returned version via `POST /api/ai/admin/models/{version}/activate` or the CEO UI button. Artifact checksum/class validation is repeated inside activation.
9. Run refresh again to compute assessments with the active version. Verify representative/team scopes, stale indicators, intervention outcomes and real operating performance over time.

The synthetic fixture in DecisionTreeRiskTests exists only for automated development verification. It is never stored in ai_model, exposed by an API, or used for real customer assessments. No production accuracy figure is available at delivery.
