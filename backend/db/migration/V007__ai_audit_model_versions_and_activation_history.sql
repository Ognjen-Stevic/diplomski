-- Immutable readable model versions and authoritative future activation history.
BEGIN;

CREATE SEQUENCE ai_model_human_version_seq;
ALTER TABLE ai_model ADD COLUMN model_version text;

WITH ordered AS (
  SELECT version,
         row_number() OVER (ORDER BY trained_at, version) AS number
  FROM ai_model
)
UPDATE ai_model model
SET model_version = 'V' || lpad(ordered.number::text, 3, '0')
FROM ordered
WHERE model.version = ordered.version;

SELECT setval(
  'ai_model_human_version_seq',
  GREATEST((SELECT count(*) FROM ai_model), 1),
  (SELECT count(*) FROM ai_model) > 0
);

ALTER TABLE ai_model
  ALTER COLUMN model_version SET DEFAULT
    ('V' || lpad(nextval('ai_model_human_version_seq')::text, 3, '0')),
  ALTER COLUMN model_version SET NOT NULL;
ALTER TABLE ai_model ADD CONSTRAINT ai_model_human_version_unique UNIQUE(model_version);
ALTER TABLE ai_model ADD CONSTRAINT ai_model_human_version_format
  CHECK (model_version ~ '^V[0-9]{3,}$');
ALTER TABLE ai_model ADD COLUMN model_configuration text;

CREATE TABLE ai_model_activation (
  id bigserial PRIMARY KEY,
  model_id uuid NOT NULL REFERENCES ai_model(version),
  model_version text NOT NULL,
  activated_at timestamptz NOT NULL DEFAULT now(),
  deactivated_at timestamptz,
  replaced_model_id uuid REFERENCES ai_model(version),
  replaced_by_model_id uuid REFERENCES ai_model(version),
  reason text NOT NULL,
  CHECK (deactivated_at IS NULL OR deactivated_at >= activated_at)
);
CREATE UNIQUE INDEX ai_model_one_open_activation
  ON ai_model_activation(model_id) WHERE deactivated_at IS NULL;
CREATE INDEX ai_model_activation_timeline
  ON ai_model_activation(activated_at DESC, id DESC);

-- Existing activation times are unknown, so no historical events are fabricated.
INSERT INTO ai_schema_migration(version) VALUES (7) ON CONFLICT DO NOTHING;
COMMIT;
