-- Durable assessment retries, categorized health state and unique weekly schedule slots.
BEGIN;

CREATE TABLE ai_assessment_work (
  customer_id bigint PRIMARY KEY REFERENCES customer(id) ON DELETE CASCADE,
  requisition_id bigint REFERENCES requisition(id) ON DELETE SET NULL,
  source text NOT NULL,
  snapshot boolean NOT NULL DEFAULT false,
  generation bigint NOT NULL DEFAULT 1,
  status text NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FAILED')),
  attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
  available_at timestamptz NOT NULL DEFAULT now(),
  locked_until timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz,
  last_error text
);
CREATE INDEX ai_assessment_work_pending
  ON ai_assessment_work(status, available_at, customer_id);

ALTER TABLE ai_job_execution ADD COLUMN IF NOT EXISTS schedule_key text;
ALTER TABLE ai_job_execution DROP CONSTRAINT IF EXISTS ai_job_execution_status_check;
ALTER TABLE ai_job_execution ADD CONSTRAINT ai_job_execution_status_check
  CHECK (status IN ('RUNNING','COMPLETED','SKIPPED','FAILED','ABANDONED','MISSED'));
CREATE UNIQUE INDEX ai_job_weekly_schedule_once
  ON ai_job_execution(job_type, schedule_key) WHERE schedule_key IS NOT NULL;

ALTER TABLE ai_system_state ADD COLUMN IF NOT EXISTS scheduler_heartbeat_at timestamptz;
ALTER TABLE ai_system_state ADD COLUMN IF NOT EXISTS assessment_error text;
ALTER TABLE ai_system_state ADD COLUMN IF NOT EXISTS capture_error text;
ALTER TABLE ai_system_state ADD COLUMN IF NOT EXISTS snapshot_error text;
ALTER TABLE ai_system_state ADD COLUMN IF NOT EXISTS training_error text;
ALTER TABLE ai_system_state ADD COLUMN IF NOT EXISTS other_error text;

UPDATE ai_system_state
SET assessment_error = CASE
      WHEN persistent_error LIKE 'ASSESSMENT_REFRESH_FAILED:%' THEN persistent_error
      ELSE assessment_error
    END,
    other_error = CASE
      WHEN persistent_error IS NOT NULL
       AND persistent_error NOT LIKE 'ASSESSMENT_REFRESH_FAILED:%'
       AND persistent_error NOT LIKE 'MODEL_UNAVAILABLE:%'
      THEN persistent_error
      ELSE other_error
    END;

INSERT INTO ai_schema_migration(version) VALUES (6) ON CONFLICT DO NOTHING;
COMMIT;
