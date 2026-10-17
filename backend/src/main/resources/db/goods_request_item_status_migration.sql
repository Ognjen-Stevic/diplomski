ALTER TABLE goods_request_item
    ADD COLUMN IF NOT EXISTS status VARCHAR(20);

UPDATE goods_request_item
SET status = 'APPROVED'
WHERE status IS NULL;

ALTER TABLE goods_request_item
    ALTER COLUMN status SET DEFAULT 'PENDING',
    ALTER COLUMN status SET NOT NULL;

ALTER TABLE goods_request_item
    DROP CONSTRAINT IF EXISTS chk_goods_request_item_status;

ALTER TABLE goods_request_item
    ADD CONSTRAINT chk_goods_request_item_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'));
