-- Upgrade for instances that applied V001 before capture-coverage auditing was added.
-- Safe after the final V001 as well. Run with ON_ERROR_STOP=1.
BEGIN;
CREATE TABLE IF NOT EXISTS ai_schema_migration(version integer PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now());
INSERT INTO ai_schema_migration(version) VALUES (1),(2) ON CONFLICT DO NOTHING;
CREATE TABLE IF NOT EXISTS ai_capture_run(at timestamptz PRIMARY KEY);
COMMIT;
