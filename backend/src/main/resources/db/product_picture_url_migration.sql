ALTER TABLE product
    ADD COLUMN IF NOT EXISTS picture_url VARCHAR(500);

UPDATE product
SET picture_url = 'products/placeholder.webp'
WHERE picture_url IS NULL OR BTRIM(picture_url) = '';

ALTER TABLE product
    ALTER COLUMN picture_url SET DEFAULT 'products/placeholder.webp',
    ALTER COLUMN picture_url SET NOT NULL;
