BEGIN;

ALTER TABLE requisition
    ADD COLUMN IF NOT EXISTS status VARCHAR(40),
    ADD COLUMN IF NOT EXISTS status_note TEXT;

ALTER TABLE requisition
    DROP CONSTRAINT IF EXISTS requisition_status_check,
    DROP CONSTRAINT IF EXISTS chk_requisition_status;

UPDATE requisition
SET status = CASE
    WHEN sent_at IS NULL THEN 'SUBMITTED'
    ELSE 'COMPLETED'
END
WHERE status IS NULL;

ALTER TABLE requisition
    ALTER COLUMN status SET DEFAULT 'DRAFT',
    ALTER COLUMN status SET NOT NULL;

ALTER TABLE requisition
    ADD CONSTRAINT chk_requisition_status CHECK (status IN (
        'DRAFT',
        'SUBMITTED',
        'APPROVED',
        'REJECTED',
        'REJECTED_BY_BOOKKEEPER',
        'PROCESSING',
        'COMPLETED',
        'CANCELLED_BY_MANAGER',
        'CANCELLED_BY_BOOKKEEPER'
    ));

INSERT INTO role (name) VALUES ('BOOKKEEPER')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM role
JOIN permission ON permission.name = 'requisitions.read'
WHERE role.name = 'BOOKKEEPER'
ON CONFLICT DO NOTHING;

COMMIT;
