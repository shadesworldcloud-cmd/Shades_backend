-- Adds the instant a product first became publicly available. This is the timestamp the "New"
-- badge is measured from.
--
-- Why a new column rather than reusing CREATED_AT: CREATED_AT is when an admin first saved the
-- row, which is not the same event as the product going on sale. A product drafted with
-- IS_ACTIVE = 0 and activated three months later would never be New under CREATED_AT, and that is
-- precisely the case the badge exists for. UPDATED_AT is worse in the opposite direction — it
-- moves on any edit, so a price change would make a two-year-old product New again.
--
-- NULL means "not published yet". Inactive products are deliberately left NULL rather than
-- backfilled, so a draft cannot carry a publicly visible New badge; the application stamps the
-- column the first time a product is activated.
--
-- Backfill: existing ACTIVE products inherit CREATED_AT, which is the best available evidence of
-- when they went live — converted to UTC, because CREATED_AT is the server's LOCAL wall clock while
-- this column is mapped as an Instant and read back as UTC. Without the conversion every backfilled
-- row looks newer than it is by the server's offset. TIMESTAMPDIFF against UTC_TIMESTAMP() gets that
-- offset without needing the MySQL timezone tables, which CONVERT_TZ would require and which return
-- NULL when absent.
--
-- Safe to execute repeatedly on the same MySQL schema.

SET @add_published_at = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PRODUCTS'
             AND COLUMN_NAME = 'PUBLISHED_AT'),
    'SELECT 1',
    'ALTER TABLE PRODUCTS ADD COLUMN PUBLISHED_AT DATETIME NULL AFTER IS_ACTIVE'
);
PREPARE published_at_statement FROM @add_published_at;
EXECUTE published_at_statement;
DEALLOCATE PREPARE published_at_statement;

-- Idempotent by the IS NULL guard: a second run finds nothing left to set.
SET @utc_offset_seconds = TIMESTAMPDIFF(SECOND, UTC_TIMESTAMP(), NOW());
UPDATE PRODUCTS
SET PUBLISHED_AT = CREATED_AT - INTERVAL @utc_offset_seconds SECOND
WHERE IS_ACTIVE = 1 AND PUBLISHED_AT IS NULL;

-- The storefront filters and sorts on this for the New badge and for "newest first".
SET @add_published_at_index = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PRODUCTS'
             AND INDEX_NAME = 'IDX_PRODUCTS_PUBLISHED_AT'),
    'SELECT 1',
    'CREATE INDEX IDX_PRODUCTS_PUBLISHED_AT ON PRODUCTS (PUBLISHED_AT)'
);
PREPARE published_at_index_statement FROM @add_published_at_index;
EXECUTE published_at_index_statement;
DEALLOCATE PREPARE published_at_index_statement;
