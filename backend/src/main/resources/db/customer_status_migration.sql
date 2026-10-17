ALTER TABLE customer
    ADD COLUMN IF NOT EXISTS status VARCHAR(20);

ALTER TABLE customer
    DROP CONSTRAINT IF EXISTS customer_status_check,
    DROP CONSTRAINT IF EXISTS chk_customer_status;

UPDATE customer
SET status = 'APPROVED'
WHERE status IS NULL OR status = 'ACTIVE';

ALTER TABLE customer
    ALTER COLUMN status SET DEFAULT 'DRAFT',
    ALTER COLUMN status SET NOT NULL;

ALTER TABLE customer
    ADD CONSTRAINT chk_customer_status
        CHECK (status IN ('DRAFT', 'APPROVED', 'DEACTIVATED', 'DELETED'));
