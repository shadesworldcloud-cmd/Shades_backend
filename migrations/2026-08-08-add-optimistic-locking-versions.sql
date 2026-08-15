-- Adds row version columns for optimistic locking on the two entities customers and admins edit
-- through a read-edit-save cycle: their profile and their saved addresses.
--
-- Why these and not everything: optimistic locking is for records where a second person's edit
-- would silently overwrite the first person's. It is the WRONG tool for inventory — two concurrent
-- stock decrements are both legitimate and must both apply, so they need an atomic conditional
-- update or a row lock, not a conflict. ORDERS is likewise driven by explicit status transitions
-- that already reject illegal moves, so a version there would add conflicts without adding safety.
--
-- NOT NULL DEFAULT 0 makes the backfill implicit and safe: every existing row starts at version 0,
-- and the first update moves it to 1. No data is rewritten and no row is locked for longer than the
-- ALTER itself.
--
-- Reversible: DROP COLUMN VERSION on either table restores the previous behaviour exactly, because
-- nothing else reads the column.
--
-- Safe to execute repeatedly on the same MySQL schema.

SET @add_users_version = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'USERS' AND COLUMN_NAME = 'VERSION'),
    'SELECT 1',
    'ALTER TABLE USERS ADD COLUMN VERSION BIGINT NOT NULL DEFAULT 0'
);
PREPARE users_version_statement FROM @add_users_version;
EXECUTE users_version_statement;
DEALLOCATE PREPARE users_version_statement;

SET @add_addresses_version = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ADDRESSES' AND COLUMN_NAME = 'VERSION'),
    'SELECT 1',
    'ALTER TABLE ADDRESSES ADD COLUMN VERSION BIGINT NOT NULL DEFAULT 0'
);
PREPARE addresses_version_statement FROM @add_addresses_version;
EXECUTE addresses_version_statement;
DEALLOCATE PREPARE addresses_version_statement;

-- Duplicate-payment idempotency.
--
-- PAYMENTS.PROVIDER_REFERENCE is the payment provider's own transaction id. A retried or replayed
-- webhook carries the same one, so a unique index turns "we already processed this callback" from
-- an application-level check that two concurrent requests can both pass into something the database
-- settles. NULL is allowed and is not constrained by a UNIQUE index in MySQL, which is what lets
-- rows created before a provider reference exists coexist.
--
-- Created only when no duplicate values already exist, so a schema holding legacy duplicates is
-- reported by the SELECT below rather than failing the migration halfway.
SET @duplicate_provider_refs = (
    SELECT COUNT(*) FROM (
        SELECT PROVIDER_REFERENCE FROM PAYMENTS
        WHERE PROVIDER_REFERENCE IS NOT NULL AND PROVIDER_REFERENCE <> ''
        GROUP BY PROVIDER_REFERENCE HAVING COUNT(*) > 1
    ) AS duplicates
);

SET @add_payment_reference_unique = IF(
    @duplicate_provider_refs > 0
      OR EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PAYMENTS'
                  AND INDEX_NAME = 'UQ_PAYMENT_PROVIDER_REFERENCE'),
    'SELECT 1',
    'CREATE UNIQUE INDEX UQ_PAYMENT_PROVIDER_REFERENCE ON PAYMENTS (PROVIDER_REFERENCE)'
);
PREPARE payment_reference_statement FROM @add_payment_reference_unique;
EXECUTE payment_reference_statement;
DEALLOCATE PREPARE payment_reference_statement;

-- Reports whether the unique index was skipped because of pre-existing duplicates.
SELECT @duplicate_provider_refs AS DUPLICATE_PROVIDER_REFERENCES_FOUND,
       IF(@duplicate_provider_refs > 0,
          'UQ_PAYMENT_PROVIDER_REFERENCE NOT created - resolve duplicates first',
          'UQ_PAYMENT_PROVIDER_REFERENCE present') AS RESULT;
