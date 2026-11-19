-- Durable autonomous AI lifecycle: outbox, jobs, fingerprints, leases and monitoring state.
BEGIN;

CREATE TABLE ai_outbox_event (
  id bigserial PRIMARY KEY,
  event_type text NOT NULL CHECK (event_type IN ('REQUISITION_COMPLETED')),
  requisition_id bigint NOT NULL UNIQUE REFERENCES requisition(id) ON DELETE CASCADE,
  status text NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FAILED')),
  attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
  available_at timestamptz NOT NULL DEFAULT now(),
  locked_until timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz,
  last_error text
);
CREATE INDEX ai_outbox_pending ON ai_outbox_event(status, available_at, id);

CREATE TABLE ai_lifecycle_lock (
  lock_name text PRIMARY KEY,
  owner_id uuid,
  lease_until timestamptz,
  updated_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO ai_lifecycle_lock(lock_name) VALUES ('TRAINING') ON CONFLICT DO NOTHING;

CREATE TABLE ai_job_execution (
  id uuid PRIMARY KEY,
  job_type text NOT NULL CHECK (job_type IN ('CAPTURE','SNAPSHOT','ASSESSMENT','ELIGIBILITY','TRAINING')),
  status text NOT NULL CHECK (status IN ('RUNNING','COMPLETED','SKIPPED','FAILED','ABANDONED')),
  dataset_fingerprint text,
  owner_id uuid,
  started_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz,
  outcome text,
  details text,
  error_message text
);
CREATE INDEX ai_job_recent ON ai_job_execution(job_type, started_at DESC);

CREATE TABLE ai_training_dataset (
  fingerprint text PRIMARY KEY,
  status text NOT NULL
    CHECK (status IN ('RUNNING','EVALUATED','PROMOTED','REJECTED','FAILED_RETRYABLE','FAILED')),
  mature_count integer NOT NULL,
  training_count integer NOT NULL,
  evaluation_count integer NOT NULL,
  class_distribution text NOT NULL,
  first_snapshot_at timestamptz,
  last_snapshot_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz,
  retry_after timestamptz,
  attempts integer NOT NULL DEFAULT 1,
  model_version uuid REFERENCES ai_model(version) ON DELETE SET NULL,
  decision_reason text
);

ALTER TABLE ai_model ADD COLUMN IF NOT EXISTS dataset_fingerprint text;
ALTER TABLE ai_model ADD COLUMN IF NOT EXISTS promotion_decision text;
ALTER TABLE ai_model ADD COLUMN IF NOT EXISTS promotion_reason text;
ALTER TABLE ai_model ADD COLUMN IF NOT EXISTS promoted_at timestamptz;
ALTER TABLE ai_model ADD COLUMN IF NOT EXISTS compared_to_model uuid REFERENCES ai_model(version);
CREATE UNIQUE INDEX IF NOT EXISTS ai_model_dataset_once
  ON ai_model(dataset_fingerprint) WHERE dataset_fingerprint IS NOT NULL;

CREATE TABLE ai_system_state (
  singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
  operational_status text NOT NULL DEFAULT 'STARTING',
  last_capture_at timestamptz,
  last_snapshot_at timestamptz,
  last_assessment_refresh_at timestamptz,
  last_eligibility_check_at timestamptz,
  last_training_attempt_at timestamptz,
  last_training_outcome text,
  eligible_sample_count integer NOT NULL DEFAULT 0,
  new_mature_sample_count integer NOT NULL DEFAULT 0,
  last_promotion_decision text,
  last_promotion_reason text,
  persistent_error text,
  updated_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO ai_system_state(singleton) VALUES (true) ON CONFLICT DO NOTHING;

INSERT INTO ai_schema_migration(version) VALUES (4) ON CONFLICT DO NOTHING;
COMMIT;
