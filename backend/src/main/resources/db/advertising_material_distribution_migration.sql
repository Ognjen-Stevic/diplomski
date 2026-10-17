BEGIN;

ALTER TABLE IF EXISTS "advertising_material_gift"
    RENAME TO "advertising_material_distribution";

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'advertising_material_distribution'
          AND column_name = 'given_at'
    ) THEN
        ALTER TABLE "advertising_material_distribution"
            RENAME COLUMN "given_at" TO "distributed_at";
    END IF;
END
$$;

DO $$
DECLARE
    rename_pair TEXT[];
BEGIN
    FOREACH rename_pair SLICE 1 IN ARRAY ARRAY[
        ['advertising_material_gift_pkey', 'advertising_material_distribution_pkey'],
        ['advertising_material_gift_customer_id_fkey', 'advertising_material_distribution_customer_id_fkey'],
        ['advertising_material_gift_material_id_fkey', 'advertising_material_distribution_material_id_fkey'],
        ['advertising_material_gift_sales_rep_id_fkey', 'advertising_material_distribution_sales_rep_id_fkey'],
        ['advertising_material_gift_customer_id_not_null', 'advertising_material_distribution_customer_id_not_null'],
        ['advertising_material_gift_given_at_not_null', 'advertising_material_distribution_distributed_at_not_null'],
        ['advertising_material_gift_id_not_null', 'advertising_material_distribution_id_not_null'],
        ['advertising_material_gift_material_id_not_null', 'advertising_material_distribution_material_id_not_null'],
        ['advertising_material_gift_quantity_check', 'advertising_material_distribution_quantity_check'],
        ['advertising_material_gift_quantity_not_null', 'advertising_material_distribution_quantity_not_null'],
        ['advertising_material_gift_sales_rep_id_not_null', 'advertising_material_distribution_sales_rep_id_not_null']
    ]
    LOOP
        IF EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = rename_pair[1]
              AND conrelid = 'advertising_material_distribution'::regclass
        ) THEN
            EXECUTE format(
                'ALTER TABLE advertising_material_distribution RENAME CONSTRAINT %I TO %I',
                rename_pair[1],
                rename_pair[2]
            );
        END IF;
    END LOOP;
END
$$;

ALTER INDEX IF EXISTS "idx_material_gift_customer"
    RENAME TO "idx_material_distribution_customer";
ALTER INDEX IF EXISTS "idx_material_gift_sales_rep"
    RENAME TO "idx_material_distribution_sales_rep";

COMMIT;
