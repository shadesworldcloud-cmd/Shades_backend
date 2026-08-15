-- Indexes for the Best Sellers aggregate (ProductRepository.findBestSellers).
--
-- The aggregate scans ORDER_ITEMS once and then asks two lookup questions per order:
--   * "does this order have a settled payment"  -> PAYMENTS (ORDER_ID, PAYMENT_STATUS)
--   * "how much of this item came back"         -> RETURN_ITEMS (ORDER_ITEM_ID)
--
-- The FK on PAYMENTS.ORDER_ID already gives an index on ORDER_ID alone, but the EXISTS filters on
-- PAYMENT_STATUS too; the composite lets that be answered from the index instead of reading the
-- row. RETURN_ITEMS.ORDER_ITEM_ID likewise carries an FK index, but it is declared explicitly here
-- so the grouping subquery does not depend on a constraint someone might restructure later.
--
-- Both guarded, so this is safe to execute repeatedly on the same MySQL schema.

SET @add_payment_lookup = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PAYMENTS'
             AND INDEX_NAME = 'IDX_PAYMENTS_ORDER_STATUS'),
    'SELECT 1',
    'CREATE INDEX IDX_PAYMENTS_ORDER_STATUS ON PAYMENTS (ORDER_ID, PAYMENT_STATUS)'
);
PREPARE payment_lookup_statement FROM @add_payment_lookup;
EXECUTE payment_lookup_statement;
DEALLOCATE PREPARE payment_lookup_statement;

SET @add_return_item_lookup = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'RETURN_ITEMS'
             AND INDEX_NAME = 'IDX_RETURN_ITEMS_ORDER_ITEM'),
    'SELECT 1',
    'CREATE INDEX IDX_RETURN_ITEMS_ORDER_ITEM ON RETURN_ITEMS (ORDER_ITEM_ID)'
);
PREPARE return_item_lookup_statement FROM @add_return_item_lookup;
EXECUTE return_item_lookup_statement;
DEALLOCATE PREPARE return_item_lookup_statement;
