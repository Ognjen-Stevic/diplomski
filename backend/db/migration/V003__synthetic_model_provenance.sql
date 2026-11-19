-- Synthetic model provenance. Required only for the explicit local demo seeder.
BEGIN;
ALTER TABLE ai_model
  ADD COLUMN IF NOT EXISTS provenance text NOT NULL DEFAULT 'PRODUCTION';
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'ai_model_provenance_check'
  ) THEN
    ALTER TABLE ai_model ADD CONSTRAINT ai_model_provenance_check
      CHECK (provenance IN ('PRODUCTION', 'SYNTHETIC_DEMO'));
  END IF;
END $$;
INSERT INTO ai_schema_migration(version) VALUES (3) ON CONFLICT DO NOTHING;
COMMIT;
