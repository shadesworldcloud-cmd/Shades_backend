-- Lets a product be removed permanently while its ORDER history survives.
--
-- ── The problem ──────────────────────────────────────────────────────────────────────────
-- Deleting a product that had ever been sold failed outright:
--
--   ERROR 1451: Cannot delete or update a parent row: a foreign key constraint fails
--               (`cart_items`, CONSTRAINT `FK_CART_ITEM_VARIANT` ...)
--
-- and the application turned that into "This product has order or inventory history and cannot be
-- permanently removed. Deactivate it instead." Three foreign keys pointed at PRODUCT_VARIANTS with
-- NO ACTION: CART_ITEMS, INVENTORY_MOVEMENTS and ORDER_ITEMS. Everything else already cascaded.
--
-- ── What changes, and what deliberately does not ─────────────────────────────────────────
-- Only ORDER_ITEMS is touched here. Carts and inventory movements are deleted by the application
-- before it removes the product — they are live inventory state, and the requirement is that the
-- product disappears from inventory entirely. Order lines are the opposite: they are a financial
-- record of something a customer actually bought and paid for, and must not change because the
-- catalogue did.
--
-- ORDER_ITEMS.VARIANT_ID becomes NULLable with ON DELETE SET NULL. That is safe precisely because
-- the line already carries its own snapshot of everything an order needs to be read:
--
--   PRODUCT_NAME, SKU, QUANTITY, UNIT_PRICE, TAX_AMOUNT, DISCOUNT_AMOUNT, LINE_TOTAL
--
-- so a past order still renders, still totals, and can still be invoiced, returned and refunded
-- after the product is gone. What is lost is the link back to a live catalogue row — which is
-- correct, because there no longer is one.
--
-- RETURN_ITEMS references ORDER_ITEMS, not PRODUCT_VARIANTS, so returns and refunds are unaffected.
--
-- Safe to execute repeatedly.

-- ── 1. VARIANT_ID must accept NULL before the constraint can set it ───────────────────────
SET @order_item_variant_nullable = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ORDER_ITEMS'
             AND COLUMN_NAME = 'VARIANT_ID' AND IS_NULLABLE = 'YES'),
    'SELECT 1',
    'ALTER TABLE ORDER_ITEMS MODIFY COLUMN VARIANT_ID BIGINT NULL'
);
PREPARE order_item_nullable FROM @order_item_variant_nullable;
EXECUTE order_item_nullable;
DEALLOCATE PREPARE order_item_nullable;

-- ── 2. Replace the NO ACTION constraint with ON DELETE SET NULL ───────────────────────────
-- Dropped and recreated rather than altered: MySQL has no ALTER for a foreign key's delete rule.
-- The index behind it is left in place, so the FK can be recreated immediately.
SET @drop_order_item_fk = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS
           WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'ORDER_ITEMS'
             AND CONSTRAINT_NAME = 'FK_ORDER_ITEM_VARIANT' AND DELETE_RULE <> 'SET NULL'),
    'ALTER TABLE ORDER_ITEMS DROP FOREIGN KEY FK_ORDER_ITEM_VARIANT',
    'SELECT 1'
);
PREPARE drop_order_item_fk FROM @drop_order_item_fk;
EXECUTE drop_order_item_fk;
DEALLOCATE PREPARE drop_order_item_fk;

SET @add_order_item_fk = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS
           WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'ORDER_ITEMS'
             AND CONSTRAINT_NAME = 'FK_ORDER_ITEM_VARIANT'),
    'SELECT 1',
    'ALTER TABLE ORDER_ITEMS ADD CONSTRAINT FK_ORDER_ITEM_VARIANT
        FOREIGN KEY (VARIANT_ID) REFERENCES PRODUCT_VARIANTS (VARIANT_ID) ON DELETE SET NULL'
);
PREPARE add_order_item_fk FROM @add_order_item_fk;
EXECUTE add_order_item_fk;
DEALLOCATE PREPARE add_order_item_fk;

-- ── Rollback ──────────────────────────────────────────────────────────────────────────────
-- Only possible while no ORDER_ITEMS row has a NULL VARIANT_ID — i.e. before any product with
-- order history has actually been deleted. After that, restoring NOT NULL would require inventing
-- a variant for lines whose product no longer exists, so check first:
--
--   SELECT COUNT(*) FROM ORDER_ITEMS WHERE VARIANT_ID IS NULL;   -- must be 0
--
-- ALTER TABLE ORDER_ITEMS DROP FOREIGN KEY FK_ORDER_ITEM_VARIANT;
-- ALTER TABLE ORDER_ITEMS MODIFY COLUMN VARIANT_ID BIGINT NOT NULL;
-- ALTER TABLE ORDER_ITEMS ADD CONSTRAINT FK_ORDER_ITEM_VARIANT
--     FOREIGN KEY (VARIANT_ID) REFERENCES PRODUCT_VARIANTS (VARIANT_ID);
