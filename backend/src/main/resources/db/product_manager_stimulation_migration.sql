ALTER TABLE product
    ADD COLUMN IF NOT EXISTS manager_stimulation_per_unit NUMERIC(10, 2);

UPDATE product
SET manager_stimulation_per_unit = 0.00
WHERE manager_stimulation_per_unit IS NULL;

ALTER TABLE product
    ALTER COLUMN manager_stimulation_per_unit SET DEFAULT 0.00,
    ALTER COLUMN manager_stimulation_per_unit SET NOT NULL;

ALTER TABLE product
    DROP CONSTRAINT IF EXISTS chk_product_manager_stimulation_non_negative;

ALTER TABLE product
    ADD CONSTRAINT chk_product_manager_stimulation_non_negative
        CHECK (manager_stimulation_per_unit >= 0);
