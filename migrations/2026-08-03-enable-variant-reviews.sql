-- Moves review uniqueness from one-per-product to one-per-order-item, so a customer can
-- review each variant they actually bought.
--
-- ORDER MATTERS. UQ_USER_PRODUCT_REVIEW (USER_ID, PRODUCT_ID) is the only index leading with
-- USER_ID, so InnoDB uses it to satisfy FK_REVIEW_USER. Dropping it first fails with
-- "ERROR 1553: Cannot drop index 'UQ_USER_PRODUCT_REVIEW': needed in a foreign key constraint",
-- which is why this migration had never been applied to any schema. The replacement unique
-- index also leads with USER_ID, so adding it FIRST keeps the foreign key covered and lets the
-- old index drop cleanly, without leaving a redundant helper index behind.
--
-- ORDER_ITEM_ID is nullable and MySQL permits repeated NULLs in a UNIQUE index, so pre-existing
-- rows without an order item stay legal.
-- Safe to execute repeatedly on the same MySQL schema.
SET @add_variant_review_index = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'REVIEWS'
             AND INDEX_NAME = 'UQ_USER_ORDER_ITEM_REVIEW'),
    'SELECT 1',
    'ALTER TABLE REVIEWS ADD CONSTRAINT UQ_USER_ORDER_ITEM_REVIEW UNIQUE (USER_ID, ORDER_ITEM_ID)'
);
PREPARE review_migration_statement FROM @add_variant_review_index;
EXECUTE review_migration_statement;
DEALLOCATE PREPARE review_migration_statement;

SET @drop_old_review_index = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'REVIEWS'
             AND INDEX_NAME = 'UQ_USER_PRODUCT_REVIEW'),
    'ALTER TABLE REVIEWS DROP INDEX UQ_USER_PRODUCT_REVIEW',
    'SELECT 1'
);
PREPARE review_migration_statement FROM @drop_old_review_index;
EXECUTE review_migration_statement;
DEALLOCATE PREPARE review_migration_statement;
