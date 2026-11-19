UPDATE product
SET picture_url = 'products/placeholder.jpg'
WHERE picture_url = 'products/placeholder.webp';

ALTER TABLE product
    ALTER COLUMN picture_url SET DEFAULT 'products/placeholder.jpg';
