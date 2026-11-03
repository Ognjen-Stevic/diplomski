-- Explicit migration: psql -v ON_ERROR_STOP=1 -f db/migration/V001__customer_risk.sql
-- No automatic Hibernate schema update and no edits to existing business rows.
BEGIN;
CREATE TABLE ai_schema_migration(version integer PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now());
INSERT INTO ai_schema_migration(version) VALUES (1);
CREATE TABLE ai_capture_run(at timestamptz PRIMARY KEY);
CREATE TABLE ai_model (
 version uuid PRIMARY KEY, trained_at timestamptz NOT NULL, schema_version integer NOT NULL CHECK(schema_version=1),
 artifact bytea NOT NULL, sha256 text NOT NULL, metrics text NOT NULL, eligible boolean NOT NULL,
 active boolean NOT NULL DEFAULT false
);
CREATE UNIQUE INDEX ai_one_active_model ON ai_model(active) WHERE active;
CREATE TABLE ai_order_observation (
 requisition_id bigint PRIMARY KEY REFERENCES requisition(id), customer_id bigint NOT NULL REFERENCES customer(id),
 created_at timestamptz NOT NULL, observed_at timestamptz NOT NULL, liters double precision NOT NULL
);
CREATE INDEX ai_observation_customer_date ON ai_order_observation(customer_id, created_at);
CREATE TABLE ai_risk_snapshot (
 id bigserial PRIMARY KEY, customer_id bigint NOT NULL REFERENCES customer(id), at timestamptz NOT NULL,
 features text NOT NULL, UNIQUE(customer_id, at)
);
CREATE INDEX ai_snapshot_customer_date ON ai_risk_snapshot(customer_id, at);
CREATE TABLE ai_risk_assessment (
 customer_id bigint PRIMARY KEY REFERENCES customer(id), at timestamptz NOT NULL,
 level text NOT NULL CHECK(level IN ('LOW','MEDIUM','HIGH','INSUFFICIENT_DATA')),
 status text NOT NULL, model_version uuid REFERENCES ai_model(version), features text NOT NULL,
 explanation text NOT NULL, previous_level text
);
CREATE INDEX ai_assessment_level ON ai_risk_assessment(level, customer_id);
CREATE TABLE ai_risk_history (
 customer_id bigint NOT NULL REFERENCES customer(id), at timestamptz NOT NULL,
 level text NOT NULL, model_version uuid REFERENCES ai_model(version), PRIMARY KEY(customer_id,at)
);
CREATE TABLE ai_risk_activity (
 id bigserial PRIMARY KEY, customer_id bigint NOT NULL REFERENCES customer(id), actor_id bigint NOT NULL REFERENCES app_user(id),
 assessment_at timestamptz NOT NULL, type text NOT NULL CHECK(type IN ('CONTACT','INVESTIGATE','MONITOR')),
 at timestamptz NOT NULL DEFAULT now(), notes varchar(2000), outcome varchar(1000) NOT NULL,
 FOREIGN KEY(customer_id,assessment_at) REFERENCES ai_risk_history(customer_id,at)
);
CREATE INDEX ai_activity_customer_date ON ai_risk_activity(customer_id, at DESC);
COMMIT;
