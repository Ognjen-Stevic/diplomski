-- Provenance is descriptive metadata, not a runtime model-availability switch.
BEGIN;
ALTER TABLE ai_model DROP CONSTRAINT IF EXISTS ai_model_provenance_check;
ALTER TABLE ai_model ADD CONSTRAINT ai_model_provenance_check
  CHECK (provenance IN ('PRODUCTION', 'SYNTHETIC_DEMO', 'UNVERIFIED'));
INSERT INTO ai_schema_migration(version) VALUES (5) ON CONFLICT DO NOTHING;
COMMIT;
