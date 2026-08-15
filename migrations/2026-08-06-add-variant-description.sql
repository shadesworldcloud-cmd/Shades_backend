-- Adds a per-variant description so the product page can show copy belonging to the
-- selected variant instead of repeating the product-level description for every colour.
-- Nullable with no backfill: existing variants keep NULL and the UI falls back to the
-- product description, so an un-edited catalogue renders exactly as before.
-- Safe to execute repeatedly on the same MySQL schema.
SET @add_variant_description = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PRODUCT_VARIANTS'
             AND COLUMN_NAME = 'VARIANT_DESCRIPTION'),
    'SELECT 1',
    'ALTER TABLE PRODUCT_VARIANTS ADD COLUMN VARIANT_DESCRIPTION TEXT NULL AFTER VARIANT_NAME'
);
PREPARE variant_description_statement FROM @add_variant_description;
EXECUTE variant_description_statement;
DEALLOCATE PREPARE variant_description_statement;
