-- Product image metadata: a real variant relationship, a public identifier, timestamps, ordering
-- indexes, and a database guarantee that a product has at most one primary image.
--
-- ── The defect this fixes ────────────────────────────────────────────────────────────────
-- PRODUCT_IMAGES had no VARIANT_ID. Which variant an image belonged to was recovered by running a
-- regular expression over its URL:
--
--     ProductResponse.ImageSummary.extractVariantId ->  Pattern.compile("/variants/(\\d+)/")
--
-- so the association lived in a storage path. Every consequence of that is bad: moving the upload
-- directory, putting a CDN in front of it, or re-uploading a file under a different key silently
-- unlinks every variant photo in the catalogue; the database cannot enforce that the variant even
-- belongs to the same product; and there is no index, because there is no column. This migration
-- makes the relationship a foreign key and the backfill reads it out of the path one last time.
--
-- ── One primary per product ──────────────────────────────────────────────────────────────
-- Enforced the same way the automatic-offer singleton is: a generated column that is the PRODUCT_ID
-- when IS_PRIMARY = 1 and NULL otherwise, under a UNIQUE index. MySQL does not constrain NULLs, so
-- any number of non-primary images coexist while a second primary for the same product is rejected
-- by the database rather than by whichever service method happened to remember to clear the old one.
--
-- Verified before writing: 0 products in ECOMMERCE_DB and 0 in ECOMMERCE_TEST_DB currently hold
-- more than one primary image, so the index applies without needing to pick a winner.
--
-- Safe to execute repeatedly.

-- ── 1. Public identifier ──────────────────────────────────────────────────────────────────
-- So a gallery response can key an image without publishing IMAGE_ID, for the same reason the
-- product URL no longer publishes PRODUCT_ID. Admin endpoints keep addressing images by IMAGE_ID —
-- they are authorised, and an internal id is not a secret there.
SET @add_public_id = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PRODUCT_IMAGES' AND COLUMN_NAME = 'PUBLIC_ID'),
    'SELECT 1',
    'ALTER TABLE PRODUCT_IMAGES ADD COLUMN PUBLIC_ID VARCHAR(36) NULL AFTER IMAGE_ID'
);
PREPARE image_public_id FROM @add_public_id;
EXECUTE image_public_id;
DEALLOCATE PREPARE image_public_id;

UPDATE PRODUCT_IMAGES SET PUBLIC_ID = UUID() WHERE PUBLIC_ID IS NULL;

SET @add_public_id_index = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PRODUCT_IMAGES'
             AND INDEX_NAME = 'UQ_PRODUCT_IMAGES_PUBLIC_ID'),
    'SELECT 1',
    'ALTER TABLE PRODUCT_IMAGES ADD CONSTRAINT UQ_PRODUCT_IMAGES_PUBLIC_ID UNIQUE (PUBLIC_ID)'
);
PREPARE image_public_id_index FROM @add_public_id_index;
EXECUTE image_public_id_index;
DEALLOCATE PREPARE image_public_id_index;

SET @public_id_not_null = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PRODUCT_IMAGES'
             AND COLUMN_NAME = 'PUBLIC_ID' AND IS_NULLABLE = 'NO'),
    'SELECT 1',
    'ALTER TABLE PRODUCT_IMAGES MODIFY COLUMN PUBLIC_ID VARCHAR(36) NOT NULL'
);
PREPARE image_public_id_null FROM @public_id_not_null;
EXECUTE image_public_id_null;
DEALLOCATE PREPARE image_public_id_null;

-- VARCHAR(36), not CHAR(36). This column was CHAR in the first version of this file, and
-- ddl-auto=validate refused to start the application against it:
--
--   Schema-validation: wrong column type encountered in column [public_id] in table
--   [product_images]; found [char (Types#CHAR)], but expecting [varchar(36) (Types#VARCHAR)]
--
-- Hibernate maps a String column to VARCHAR and compares the JDBC type exactly, so CHAR is a hard
-- startup failure rather than a cosmetic difference. This step repairs a schema that already took
-- the CHAR version; it is a no-op everywhere else.
SET @fix_public_id_type = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PRODUCT_IMAGES'
             AND COLUMN_NAME = 'PUBLIC_ID' AND DATA_TYPE = 'char'),
    'ALTER TABLE PRODUCT_IMAGES MODIFY COLUMN PUBLIC_ID VARCHAR(36) NOT NULL',
    'SELECT 1'
);
PREPARE image_public_id_type FROM @fix_public_id_type;
EXECUTE image_public_id_type;
DEALLOCATE PREPARE image_public_id_type;

-- ── 2. Variant relationship ───────────────────────────────────────────────────────────────
SET @add_variant_id = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PRODUCT_IMAGES' AND COLUMN_NAME = 'VARIANT_ID'),
    'SELECT 1',
    'ALTER TABLE PRODUCT_IMAGES ADD COLUMN VARIANT_ID BIGINT NULL AFTER PRODUCT_ID'
);
PREPARE image_variant_id FROM @add_variant_id;
EXECUTE image_variant_id;
DEALLOCATE PREPARE image_variant_id;

-- The last time this path is ever parsed. NULL stays NULL: an image with no /variants/ segment is
-- a general product photo, which is a meaningful state and not missing data.
UPDATE PRODUCT_IMAGES
SET VARIANT_ID = CAST(REGEXP_SUBSTR(IMAGE_URL, '(?<=/variants/)[0-9]+') AS UNSIGNED)
WHERE VARIANT_ID IS NULL AND IMAGE_URL REGEXP '/variants/[0-9]+/';

-- Two ways the recovered id can be unusable, both of which would make the foreign key below fail:
-- the variant was deleted after the image was uploaded, or the path names a variant belonging to a
-- different product. Both become "general product photo", which is the safe reading — the file is
-- still a picture of the product, it has just lost its colourway association.
UPDATE PRODUCT_IMAGES I
LEFT JOIN PRODUCT_VARIANTS V ON V.VARIANT_ID = I.VARIANT_ID AND V.PRODUCT_ID = I.PRODUCT_ID
SET I.VARIANT_ID = NULL
WHERE I.VARIANT_ID IS NOT NULL AND V.VARIANT_ID IS NULL;

SET @add_variant_fk = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PRODUCT_IMAGES'
             AND CONSTRAINT_NAME = 'FK_PRODUCT_IMAGE_VARIANT'),
    'SELECT 1',
    -- ON DELETE SET NULL, not CASCADE: deleting a colourway should not silently destroy
    -- photographs an admin uploaded. They fall back to being general product images and stay
    -- visible in the gallery, which is recoverable; deletion is not.
    'ALTER TABLE PRODUCT_IMAGES ADD CONSTRAINT FK_PRODUCT_IMAGE_VARIANT
        FOREIGN KEY (VARIANT_ID) REFERENCES PRODUCT_VARIANTS (VARIANT_ID) ON DELETE SET NULL'
);
PREPARE image_variant_fk FROM @add_variant_fk;
EXECUTE image_variant_fk;
DEALLOCATE PREPARE image_variant_fk;

-- ── 3. Timestamps ─────────────────────────────────────────────────────────────────────────
SET @add_created_at = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PRODUCT_IMAGES' AND COLUMN_NAME = 'CREATED_AT'),
    'SELECT 1',
    'ALTER TABLE PRODUCT_IMAGES ADD COLUMN CREATED_AT DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP'
);
PREPARE image_created_at FROM @add_created_at;
EXECUTE image_created_at;
DEALLOCATE PREPARE image_created_at;

SET @add_updated_at = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PRODUCT_IMAGES' AND COLUMN_NAME = 'UPDATED_AT'),
    'SELECT 1',
    'ALTER TABLE PRODUCT_IMAGES ADD COLUMN UPDATED_AT DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP'
);
PREPARE image_updated_at FROM @add_updated_at;
EXECUTE image_updated_at;
DEALLOCATE PREPARE image_updated_at;

-- ── 4. One primary image per product ──────────────────────────────────────────────────────
SET @add_primary_singleton = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PRODUCT_IMAGES'
             AND COLUMN_NAME = 'PRIMARY_SINGLETON'),
    'SELECT 1',
    'ALTER TABLE PRODUCT_IMAGES
        ADD COLUMN PRIMARY_SINGLETON BIGINT
        GENERATED ALWAYS AS (IF(IS_PRIMARY = 1, PRODUCT_ID, NULL)) VIRTUAL'
);
PREPARE image_primary_singleton FROM @add_primary_singleton;
EXECUTE image_primary_singleton;
DEALLOCATE PREPARE image_primary_singleton;

SET @add_primary_index = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PRODUCT_IMAGES'
             AND INDEX_NAME = 'UQ_PRODUCT_IMAGES_PRIMARY'),
    'SELECT 1',
    'ALTER TABLE PRODUCT_IMAGES ADD CONSTRAINT UQ_PRODUCT_IMAGES_PRIMARY UNIQUE (PRIMARY_SINGLETON)'
);
PREPARE image_primary_index FROM @add_primary_index;
EXECUTE image_primary_index;
DEALLOCATE PREPARE image_primary_index;

-- ── 5. Lookup and ordering indexes ────────────────────────────────────────────────────────
-- The gallery reads one product's images in display order; the listing reads only the primary
-- thumbnail. This composite serves both without a filesort, which is the N+1-adjacent cost that
-- shows up once a listing page renders 200 cards.
SET @add_order_index = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PRODUCT_IMAGES'
             AND INDEX_NAME = 'IDX_PRODUCT_IMAGES_ORDER'),
    'SELECT 1',
    'ALTER TABLE PRODUCT_IMAGES ADD INDEX IDX_PRODUCT_IMAGES_ORDER (PRODUCT_ID, DISPLAY_ORDER, IMAGE_ID)'
);
PREPARE image_order_index FROM @add_order_index;
EXECUTE image_order_index;
DEALLOCATE PREPARE image_order_index;

SET @add_variant_index = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PRODUCT_IMAGES'
             AND INDEX_NAME = 'IDX_PRODUCT_IMAGES_VARIANT'),
    'SELECT 1',
    'ALTER TABLE PRODUCT_IMAGES ADD INDEX IDX_PRODUCT_IMAGES_VARIANT (VARIANT_ID, DISPLAY_ORDER)'
);
PREPARE image_variant_index FROM @add_variant_index;
EXECUTE image_variant_index;
DEALLOCATE PREPARE image_variant_index;

-- ── Rollback ──────────────────────────────────────────────────────────────────────────────
-- Restores the previous shape. The variant association reverts to being recoverable only from the
-- URL path, which is what the application did before this migration, so no information is lost that
-- the old code could have used.
--
-- ORDER MATTERS, and this was wrong when first written. Dropping IDX_PRODUCT_IMAGES_VARIANT while
-- FK_PRODUCT_IMAGE_VARIANT still exists fails with
--   ERROR 1553: Cannot drop index 'IDX_PRODUCT_IMAGES_VARIANT': needed in a foreign key constraint
-- because InnoDB uses that index to enforce the constraint. The foreign key therefore goes first.
-- Verified by executing this block against ECOMMERCE_TEST_DB and re-applying the migration.
--
-- ALTER TABLE PRODUCT_IMAGES DROP FOREIGN KEY FK_PRODUCT_IMAGE_VARIANT;
-- ALTER TABLE PRODUCT_IMAGES DROP INDEX IDX_PRODUCT_IMAGES_VARIANT;
-- ALTER TABLE PRODUCT_IMAGES DROP INDEX IDX_PRODUCT_IMAGES_ORDER;
-- ALTER TABLE PRODUCT_IMAGES DROP INDEX UQ_PRODUCT_IMAGES_PRIMARY;
-- ALTER TABLE PRODUCT_IMAGES DROP COLUMN PRIMARY_SINGLETON;
-- ALTER TABLE PRODUCT_IMAGES DROP COLUMN UPDATED_AT;
-- ALTER TABLE PRODUCT_IMAGES DROP COLUMN CREATED_AT;
-- ALTER TABLE PRODUCT_IMAGES DROP COLUMN VARIANT_ID;
-- ALTER TABLE PRODUCT_IMAGES DROP INDEX UQ_PRODUCT_IMAGES_PUBLIC_ID;
-- ALTER TABLE PRODUCT_IMAGES DROP COLUMN PUBLIC_ID;
