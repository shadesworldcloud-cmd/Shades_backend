-- Product family redesign: an explicit variant order with a Main Variant, a main image per
-- variant, and optimistic locking on the product row.
--
-- ── The model this creates ──────────────────────────────────────────────────────────────────
-- A product is a family: the PRODUCTS row carries only what every colourway shares (name, slug,
-- categories, brand, publication state), and each PRODUCT_VARIANTS row is one sellable thing with
-- its own SKU, price, stock and photography.
--
--   * POSITION orders a product's variants, 1..N, unique per product. Position 1 IS the Main
--     Product: its photo fronts every listing card and it is the first selection on the product
--     page. There is no separate IS_MAIN flag to drift out of step with the order.
--   * IS_PRIMARY on PRODUCT_IMAGES changes meaning from "the product's one primary image" to
--     "this variant's main image" — at most one per VARIANT, enforced by the database through a
--     generated column, the same construction the old per-product rule used.
--   * VERSION on PRODUCTS backs a read-edit-save conflict check for the admin editor: two
--     administrators editing the same product now get a 409 instead of a silent overwrite.
--
-- ── Ordering inside this file matters ───────────────────────────────────────────────────────
-- Variantless products get a variant BEFORE positions are backfilled (so the new row is position
-- 1), and positions are backfilled BEFORE general images are re-filed (re-filing targets the
-- position-1 variant). The per-variant primary index goes last, after the data it constrains has
-- been normalised.
--
-- Safe to execute repeatedly.

-- ── 1. Optimistic locking on the product row ────────────────────────────────────────────────
-- Same construction as USERS/ADDRESSES (2026-08-08-add-optimistic-locking-versions.sql):
-- NOT NULL DEFAULT 0 makes the backfill implicit.
SET @add_products_version = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PRODUCTS' AND COLUMN_NAME = 'VERSION'),
    'SELECT 1',
    'ALTER TABLE PRODUCTS ADD COLUMN VERSION BIGINT NOT NULL DEFAULT 0'
);
PREPARE products_version FROM @add_products_version;
EXECUTE products_version;
DEALLOCATE PREPARE products_version;

-- ── 2. Every product family has at least one variant ────────────────────────────────────────
-- The application now refuses to create a product without a variant, but legacy rows may predate
-- that. Each such product gets a Default variant at the product's base price with zero stock —
-- zero because inventing sellable stock in a migration would let customers buy something nobody
-- counted. The SKU is namespaced on the product id; the NOT EXISTS guard on SKU means a clashing
-- legacy SKU skips the insert, and the report at the end of this file names any product that is
-- still variantless so it can be repaired by hand rather than silently left invalid.
INSERT INTO PRODUCT_VARIANTS (PRODUCT_ID, SKU, VARIANT_NAME, PRICE, QUANTITY_AVAILABLE,
                              LOW_STOCK_THRESHOLD, IS_ACTIVE, CREATED_AT, UPDATED_AT)
SELECT P.PRODUCT_ID, CONCAT('SW-P', P.PRODUCT_ID, '-MAIN'), 'Default', P.BASE_PRICE, 0, 5, 1, NOW(), NOW()
FROM PRODUCTS P
WHERE NOT EXISTS (SELECT 1 FROM PRODUCT_VARIANTS V WHERE V.PRODUCT_ID = P.PRODUCT_ID)
  AND NOT EXISTS (SELECT 1 FROM PRODUCT_VARIANTS S WHERE S.SKU = CONCAT('SW-P', P.PRODUCT_ID, '-MAIN'));

-- ── 3. Variant position ──────────────────────────────────────────────────────────────────────
SET @add_position = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PRODUCT_VARIANTS' AND COLUMN_NAME = 'POSITION'),
    'SELECT 1',
    'ALTER TABLE PRODUCT_VARIANTS ADD COLUMN POSITION INT NULL AFTER PRODUCT_ID'
);
PREPARE variants_position FROM @add_position;
EXECUTE variants_position;
DEALLOCATE PREPARE variants_position;

-- Backfill in VARIANT_ID order: insertion order, which is both the order the admin UI added
-- colourways in and the order every storefront surface has displayed them to date — so position 1
-- lands on the variant customers already see first, and nothing visibly moves.
UPDATE PRODUCT_VARIANTS V
JOIN (SELECT VARIANT_ID,
             ROW_NUMBER() OVER (PARTITION BY PRODUCT_ID ORDER BY VARIANT_ID) AS RN
      FROM PRODUCT_VARIANTS) NUMBERED ON NUMBERED.VARIANT_ID = V.VARIANT_ID
SET V.POSITION = NUMBERED.RN
WHERE V.POSITION IS NULL;

SET @position_not_null = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PRODUCT_VARIANTS'
             AND COLUMN_NAME = 'POSITION' AND IS_NULLABLE = 'NO'),
    'SELECT 1',
    'ALTER TABLE PRODUCT_VARIANTS MODIFY COLUMN POSITION INT NOT NULL'
);
PREPARE variants_position_null FROM @position_not_null;
EXECUTE variants_position_null;
DEALLOCATE PREPARE variants_position_null;

-- Unique per product, so two variants can never both claim position 1. The service keeps
-- positions contiguous; the database only has to keep them distinct.
SET @add_position_index = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PRODUCT_VARIANTS'
             AND INDEX_NAME = 'UQ_PRODUCT_VARIANTS_POSITION'),
    'SELECT 1',
    'ALTER TABLE PRODUCT_VARIANTS ADD CONSTRAINT UQ_PRODUCT_VARIANTS_POSITION UNIQUE (PRODUCT_ID, POSITION)'
);
PREPARE variants_position_index FROM @add_position_index;
EXECUTE variants_position_index;
DEALLOCATE PREPARE variants_position_index;

-- ── 4. General images become the Main Variant's ─────────────────────────────────────────────
-- The redesigned model files every photograph against exactly one variant; "shown for every
-- colour" no longer exists. Re-filed rows are recorded first so the rollback can restore exactly
-- the set that was moved — nothing else distinguishes "was general" afterwards.
CREATE TABLE IF NOT EXISTS MIGRATION_20260809_GENERAL_IMAGES (
    IMAGE_ID BIGINT NOT NULL PRIMARY KEY
);

INSERT IGNORE INTO MIGRATION_20260809_GENERAL_IMAGES (IMAGE_ID)
SELECT IMAGE_ID FROM PRODUCT_IMAGES WHERE VARIANT_ID IS NULL;

UPDATE PRODUCT_IMAGES I
JOIN PRODUCT_VARIANTS V ON V.PRODUCT_ID = I.PRODUCT_ID AND V.POSITION = 1
SET I.VARIANT_ID = V.VARIANT_ID
WHERE I.VARIANT_ID IS NULL;

-- ── 5. One main image per VARIANT, replacing one primary per PRODUCT ────────────────────────
-- The old constraint guaranteed at most one primary per product, which means at most one per
-- variant already — so the new index applies without picking winners. Old enforcement goes first:
-- with both live, promoting a second variant's main image would violate the per-product rule.
SET @drop_old_primary_index = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PRODUCT_IMAGES'
             AND INDEX_NAME = 'UQ_PRODUCT_IMAGES_PRIMARY'),
    'ALTER TABLE PRODUCT_IMAGES DROP INDEX UQ_PRODUCT_IMAGES_PRIMARY',
    'SELECT 1'
);
PREPARE images_drop_primary_index FROM @drop_old_primary_index;
EXECUTE images_drop_primary_index;
DEALLOCATE PREPARE images_drop_primary_index;

SET @drop_old_singleton = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PRODUCT_IMAGES'
             AND COLUMN_NAME = 'PRIMARY_SINGLETON'),
    'ALTER TABLE PRODUCT_IMAGES DROP COLUMN PRIMARY_SINGLETON',
    'SELECT 1'
);
PREPARE images_drop_singleton FROM @drop_old_singleton;
EXECUTE images_drop_singleton;
DEALLOCATE PREPARE images_drop_singleton;

-- Belt-and-braces: a primary flag on an image with no variant would be invisible to the new
-- index (its generated value is NULL). Step 4 should have left none, but data is data.
UPDATE PRODUCT_IMAGES SET IS_PRIMARY = 0 WHERE IS_PRIMARY = 1 AND VARIANT_ID IS NULL;

-- Every variant that has photographs gets a main image: the one the gallery already led with
-- (lowest DISPLAY_ORDER, IMAGE_ID as the deterministic tie-break). Variants that already own a
-- primary — the old per-product one — are skipped, so nothing an admin chose is overridden.
UPDATE PRODUCT_IMAGES I
JOIN (SELECT IMAGE_ID, VARIANT_ID,
             ROW_NUMBER() OVER (PARTITION BY VARIANT_ID ORDER BY DISPLAY_ORDER, IMAGE_ID) AS RN
      FROM PRODUCT_IMAGES WHERE VARIANT_ID IS NOT NULL) RANKED
  ON RANKED.IMAGE_ID = I.IMAGE_ID AND RANKED.RN = 1
LEFT JOIN (SELECT DISTINCT VARIANT_ID FROM PRODUCT_IMAGES
           WHERE IS_PRIMARY = 1 AND VARIANT_ID IS NOT NULL) OWNED
  ON OWNED.VARIANT_ID = RANKED.VARIANT_ID
SET I.IS_PRIMARY = 1
WHERE OWNED.VARIANT_ID IS NULL;

-- The variant's id when this row is its main image, NULL otherwise. MySQL does not constrain
-- NULLs under a UNIQUE index, so any number of additional images coexist while a second main for
-- the same variant is rejected by the database — same construction as the automatic-offer
-- singleton and the old per-product primary.
SET @add_variant_singleton = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PRODUCT_IMAGES'
             AND COLUMN_NAME = 'VARIANT_PRIMARY_SINGLETON'),
    'SELECT 1',
    'ALTER TABLE PRODUCT_IMAGES
        ADD COLUMN VARIANT_PRIMARY_SINGLETON BIGINT
        GENERATED ALWAYS AS (IF(IS_PRIMARY = 1, VARIANT_ID, NULL)) VIRTUAL'
);
PREPARE images_variant_singleton FROM @add_variant_singleton;
EXECUTE images_variant_singleton;
DEALLOCATE PREPARE images_variant_singleton;

SET @add_variant_primary_index = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PRODUCT_IMAGES'
             AND INDEX_NAME = 'UQ_PRODUCT_IMAGES_VARIANT_PRIMARY'),
    'SELECT 1',
    'ALTER TABLE PRODUCT_IMAGES ADD CONSTRAINT UQ_PRODUCT_IMAGES_VARIANT_PRIMARY UNIQUE (VARIANT_PRIMARY_SINGLETON)'
);
PREPARE images_variant_primary_index FROM @add_variant_primary_index;
EXECUTE images_variant_primary_index;
DEALLOCATE PREPARE images_variant_primary_index;

-- ── 6. Variant label on the order-line snapshot ─────────────────────────────────────────────
-- The line already snapshots PRODUCT_NAME and SKU; the label ("Ocean Blue") is what a customer
-- recognises when the variant itself is later edited, archived or deleted. Nullable, so the
-- pre-redesign application keeps inserting order lines untouched until it is restarted.
SET @add_variant_label = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ORDER_ITEMS' AND COLUMN_NAME = 'VARIANT_LABEL'),
    'SELECT 1',
    'ALTER TABLE ORDER_ITEMS ADD COLUMN VARIANT_LABEL VARCHAR(255) NULL AFTER SKU'
);
PREPARE order_items_label FROM @add_variant_label;
EXECUTE order_items_label;
DEALLOCATE PREPARE order_items_label;

-- Backfill from the live variant while it still exists, with the storefront's own label
-- precedence: the colour attribute, else the variant name. Lines whose variant is already gone
-- stay NULL — their SKU still identifies what was bought.
UPDATE ORDER_ITEMS OI
JOIN PRODUCT_VARIANTS V ON V.VARIANT_ID = OI.VARIANT_ID
LEFT JOIN PRODUCT_ATTRIBUTES A ON A.VARIANT_ID = V.VARIANT_ID AND A.ATTRIBUTE_NAME = 'color'
SET OI.VARIANT_LABEL = COALESCE(A.ATTRIBUTE_VALUE, V.VARIANT_NAME)
WHERE OI.VARIANT_LABEL IS NULL;

-- ── 7. Report anything this migration could not make valid ──────────────────────────────────
-- A non-empty result is a product that is still variantless (its generated SKU collided in step
-- 2). Repair by hand; the application invariant "every family has a Main Variant" does not hold
-- for these rows until then.
SELECT P.PRODUCT_ID, P.PRODUCT_NAME, 'still has no variants - create one manually' AS PROBLEM
FROM PRODUCTS P
WHERE NOT EXISTS (SELECT 1 FROM PRODUCT_VARIANTS V WHERE V.PRODUCT_ID = P.PRODUCT_ID);

-- ── Rollback ──────────────────────────────────────────────────────────────────────────────
-- Order verified the same way the image-metadata migration's was: unique indexes before their
-- generated columns; the general-image restore before the backup table goes.
--
-- ALTER TABLE PRODUCT_IMAGES DROP INDEX UQ_PRODUCT_IMAGES_VARIANT_PRIMARY;
-- ALTER TABLE PRODUCT_IMAGES DROP COLUMN VARIANT_PRIMARY_SINGLETON;
-- UPDATE PRODUCT_IMAGES I JOIN MIGRATION_20260809_GENERAL_IMAGES B ON B.IMAGE_ID = I.IMAGE_ID
--     SET I.VARIANT_ID = NULL, I.IS_PRIMARY = 0;
-- -- Restore one primary per product (the gallery's first frame; the exact pre-migration flag
-- -- holder is not recorded, so this is the same promotion rule the application used):
-- UPDATE PRODUCT_IMAGES I
-- JOIN (SELECT IMAGE_ID, ROW_NUMBER() OVER (PARTITION BY PRODUCT_ID ORDER BY DISPLAY_ORDER, IMAGE_ID) AS RN
--       FROM PRODUCT_IMAGES) RANKED ON RANKED.IMAGE_ID = I.IMAGE_ID
-- SET I.IS_PRIMARY = IF(RANKED.RN = 1, 1, 0);
-- ALTER TABLE PRODUCT_IMAGES
--     ADD COLUMN PRIMARY_SINGLETON BIGINT GENERATED ALWAYS AS (IF(IS_PRIMARY = 1, PRODUCT_ID, NULL)) VIRTUAL;
-- ALTER TABLE PRODUCT_IMAGES ADD CONSTRAINT UQ_PRODUCT_IMAGES_PRIMARY UNIQUE (PRIMARY_SINGLETON);
-- DROP TABLE MIGRATION_20260809_GENERAL_IMAGES;
-- DELETE FROM PRODUCT_VARIANTS WHERE SKU LIKE 'SW-P%-MAIN' AND QUANTITY_AVAILABLE = 0;
-- ALTER TABLE PRODUCT_VARIANTS DROP INDEX UQ_PRODUCT_VARIANTS_POSITION;
-- ALTER TABLE PRODUCT_VARIANTS DROP COLUMN POSITION;
-- ALTER TABLE PRODUCTS DROP COLUMN VERSION;
-- ALTER TABLE ORDER_ITEMS DROP COLUMN VARIANT_LABEL;
