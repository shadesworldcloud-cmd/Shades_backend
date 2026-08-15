-- Automatic quantity ("buy N, get X off per complete group") offer, applied without a coupon code.
--
-- Four things are added:
--   1. AUTOMATIC_OFFERS            — the administrator-managed configuration.
--   2. AUTOMATIC_OFFER_PRODUCTS    — eligibility scope when it is a product list.
--   3. AUTOMATIC_OFFER_CATEGORIES  — eligibility scope when it is a category list.
--   4. ORDERS snapshot columns     — the immutable per-order record of what was applied, plus an
--                                    idempotency key so a duplicate checkout cannot discount twice.
--
-- Money uses DECIMAL(12,2), the representation every other money column in this schema already
-- uses. It is a fixed-precision decimal, never a float. The allocation arithmetic converts to
-- integer paise inside the application (AutomaticOfferPricing) so remainders are distributed
-- exactly; only the settled two-decimal values are stored.
--
-- Safe to execute repeatedly on the same MySQL schema: every step is guarded on
-- INFORMATION_SCHEMA, so a partially applied migration completes rather than failing.
--
-- Rollback: see the commented block at the foot of this file. Nothing else in the schema reads
-- these objects, so dropping them restores the previous behaviour exactly.

-- ---------------------------------------------------------------------------------------------
-- 1. Offer configuration.
-- ---------------------------------------------------------------------------------------------
--
-- ACTIVE_SINGLETON is the mechanism that makes "only one automatic offer can be effective at a
-- time" a database guarantee rather than an application convention. It is 1 for a live offer and
-- NULL otherwise, and MySQL's UNIQUE indexes do not constrain NULLs — so any number of drafts,
-- deactivated offers and archived offers can coexist while at most one row can ever be active.
--
-- The trade-off, deliberately taken: two offers cannot both be active even with non-overlapping
-- date windows. Scheduling is expressed by STARTS_AT/ENDS_AT on the single active offer, and
-- swapping offers means deactivating one before activating the next (the service answers 409 and
-- names the offer already holding the slot). A range-overlap rule cannot be expressed as a MySQL
-- constraint, and an application-only check is exactly the ambiguity this column removes.
CREATE TABLE IF NOT EXISTS AUTOMATIC_OFFERS (
    AUTOMATIC_OFFER_ID       BIGINT        NOT NULL AUTO_INCREMENT,
    OFFER_NAME               VARCHAR(120)  NOT NULL,
    BANNER_MESSAGE           VARCHAR(300)  NULL,
    REQUIRED_QUANTITY        INT           NOT NULL,
    DISCOUNT_PER_GROUP       DECIMAL(12,2) NOT NULL,
    MINIMUM_ORDER_SUBTOTAL   DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    SCOPE_TYPE               VARCHAR(24)   NOT NULL DEFAULT 'ALL_PRODUCTS',
    IS_ACTIVE                TINYINT(1)    NOT NULL DEFAULT 0,
    STARTS_AT                DATETIME      NOT NULL,
    ENDS_AT                  DATETIME      NOT NULL,
    PRIORITY                 INT           NOT NULL DEFAULT 0,
    VERSION                  BIGINT        NOT NULL DEFAULT 0,
    ARCHIVED_AT              DATETIME      NULL,
    CREATED_BY_USER_ID       BIGINT        NULL,
    UPDATED_BY_USER_ID       BIGINT        NULL,
    CREATED_AT               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UPDATED_AT               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    ACTIVE_SINGLETON         TINYINT(1)    GENERATED ALWAYS AS
                                 (IF(IS_ACTIVE = 1 AND ARCHIVED_AT IS NULL, 1, NULL)) STORED,
    PRIMARY KEY (AUTOMATIC_OFFER_ID),
    UNIQUE KEY UQ_AUTOMATIC_OFFER_ACTIVE (ACTIVE_SINGLETON),
    -- Covers the only hot read: "which offer is effective right now".
    KEY IDX_AUTOMATIC_OFFERS_EFFECTIVE (IS_ACTIVE, STARTS_AT, ENDS_AT),
    KEY FK_AUTOMATIC_OFFER_CREATED_BY (CREATED_BY_USER_ID),
    KEY FK_AUTOMATIC_OFFER_UPDATED_BY (UPDATED_BY_USER_ID),
    CONSTRAINT FK_AUTOMATIC_OFFER_CREATED_BY FOREIGN KEY (CREATED_BY_USER_ID)
        REFERENCES USERS (USER_ID) ON DELETE SET NULL,
    CONSTRAINT FK_AUTOMATIC_OFFER_UPDATED_BY FOREIGN KEY (UPDATED_BY_USER_ID)
        REFERENCES USERS (USER_ID) ON DELETE SET NULL,
    -- A group of one is not a quantity offer, it is a per-unit discount, and the brief requires at
    -- least two. Enforced here as well as in the request validator so direct SQL cannot create a
    -- configuration the pricing rule would divide by.
    CONSTRAINT CHK_AUTOMATIC_OFFER_REQUIRED_QUANTITY CHECK (REQUIRED_QUANTITY >= 2),
    CONSTRAINT CHK_AUTOMATIC_OFFER_DISCOUNT CHECK (DISCOUNT_PER_GROUP > 0),
    CONSTRAINT CHK_AUTOMATIC_OFFER_MINIMUM CHECK (MINIMUM_ORDER_SUBTOTAL >= 0),
    CONSTRAINT CHK_AUTOMATIC_OFFER_WINDOW CHECK (ENDS_AT > STARTS_AT),
    CONSTRAINT CHK_AUTOMATIC_OFFER_SCOPE CHECK
        (SCOPE_TYPE IN ('ALL_PRODUCTS', 'SELECTED_PRODUCTS', 'SELECTED_CATEGORIES'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------------------------
-- 2 & 3. Eligibility scope. ON DELETE CASCADE on the offer side because a scope row is part of the
-- offer, and on the catalogue side because a scope entry for a deleted product/category is
-- meaningless — the offer simply stops covering it.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS AUTOMATIC_OFFER_PRODUCTS (
    AUTOMATIC_OFFER_ID BIGINT NOT NULL,
    PRODUCT_ID         BIGINT NOT NULL,
    PRIMARY KEY (AUTOMATIC_OFFER_ID, PRODUCT_ID),
    KEY FK_AUTOMATIC_OFFER_PRODUCT_PRODUCT (PRODUCT_ID),
    CONSTRAINT FK_AUTOMATIC_OFFER_PRODUCT_OFFER FOREIGN KEY (AUTOMATIC_OFFER_ID)
        REFERENCES AUTOMATIC_OFFERS (AUTOMATIC_OFFER_ID) ON DELETE CASCADE,
    CONSTRAINT FK_AUTOMATIC_OFFER_PRODUCT_PRODUCT FOREIGN KEY (PRODUCT_ID)
        REFERENCES PRODUCTS (PRODUCT_ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS AUTOMATIC_OFFER_CATEGORIES (
    AUTOMATIC_OFFER_ID BIGINT NOT NULL,
    CATEGORY_ID        BIGINT NOT NULL,
    PRIMARY KEY (AUTOMATIC_OFFER_ID, CATEGORY_ID),
    KEY FK_AUTOMATIC_OFFER_CATEGORY_CATEGORY (CATEGORY_ID),
    CONSTRAINT FK_AUTOMATIC_OFFER_CATEGORY_OFFER FOREIGN KEY (AUTOMATIC_OFFER_ID)
        REFERENCES AUTOMATIC_OFFERS (AUTOMATIC_OFFER_ID) ON DELETE CASCADE,
    CONSTRAINT FK_AUTOMATIC_OFFER_CATEGORY_CATEGORY FOREIGN KEY (CATEGORY_ID)
        REFERENCES CATEGORIES (CATEGORY_ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------------------------
-- 4. Per-order snapshot.
--
-- Copied values, not joins. A historical order must keep the terms it was actually charged under
-- even after the administrator edits the amount, renames the offer, or archives it entirely — so
-- every field the customer was quoted is stored on the order and nothing here is ever updated
-- after creation. AUTO_OFFER_ID is kept only for reporting and is nulled if the offer row is ever
-- deleted; the snapshot columns remain readable without it.
--
-- The per-line share of the discount lives in the existing ORDER_ITEMS.DISCOUNT_AMOUNT column,
-- which already existed and was always zero. Refunds read it, which is what lets a partial return
-- refund the discounted value of the returned units rather than their list price.
-- ---------------------------------------------------------------------------------------------
SET @schema = DATABASE();

SET @sql = IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                     WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'ORDERS'
                       AND COLUMN_NAME = 'AUTO_OFFER_ID'),
    'SELECT 1',
    'ALTER TABLE ORDERS
        ADD COLUMN AUTO_OFFER_ID BIGINT NULL,
        ADD COLUMN AUTO_OFFER_NAME VARCHAR(120) NULL,
        ADD COLUMN AUTO_OFFER_REQUIRED_QUANTITY INT NULL,
        ADD COLUMN AUTO_OFFER_DISCOUNT_PER_GROUP DECIMAL(12,2) NULL,
        ADD COLUMN AUTO_OFFER_ELIGIBLE_QUANTITY INT NULL,
        ADD COLUMN AUTO_OFFER_GROUPS INT NULL,
        ADD COLUMN AUTO_OFFER_DISCOUNT DECIMAL(12,2) NULL');
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @sql = IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
                     WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'ORDERS'
                       AND INDEX_NAME = 'FK_ORDER_AUTO_OFFER'),
    'SELECT 1',
    'ALTER TABLE ORDERS
        ADD CONSTRAINT FK_ORDER_AUTO_OFFER FOREIGN KEY (AUTO_OFFER_ID)
            REFERENCES AUTOMATIC_OFFERS (AUTOMATIC_OFFER_ID) ON DELETE SET NULL');
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

-- Order-creation idempotency.
--
-- A retried checkout (impatient click, lost response, replayed request) must not produce a second
-- order, and therefore must not apply the offer or deduct stock twice. The client sends a key it
-- generates once per checkout attempt; the unique index makes "have I already created this order"
-- a question the database answers under contention rather than a read the second request can pass
-- at the same time as the first. NULL is unconstrained by a MySQL UNIQUE index, so orders created
-- by an older client (or by an internal path with no key) still insert freely.
SET @sql = IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                     WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'ORDERS'
                       AND COLUMN_NAME = 'IDEMPOTENCY_KEY'),
    'SELECT 1',
    'ALTER TABLE ORDERS ADD COLUMN IDEMPOTENCY_KEY VARCHAR(80) NULL');
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @sql = IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
                     WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'ORDERS'
                       AND INDEX_NAME = 'UQ_ORDER_IDEMPOTENCY_KEY'),
    'SELECT 1',
    'CREATE UNIQUE INDEX UQ_ORDER_IDEMPOTENCY_KEY ON ORDERS (IDEMPOTENCY_KEY)');
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SELECT 'AUTOMATIC_OFFERS + scope tables + ORDERS snapshot/idempotency columns present' AS RESULT;

-- ---------------------------------------------------------------------------------------------
-- Rollback (run top to bottom):
--
--   ALTER TABLE ORDERS DROP FOREIGN KEY FK_ORDER_AUTO_OFFER;
--   DROP INDEX UQ_ORDER_IDEMPOTENCY_KEY ON ORDERS;
--   ALTER TABLE ORDERS
--       DROP COLUMN AUTO_OFFER_ID, DROP COLUMN AUTO_OFFER_NAME,
--       DROP COLUMN AUTO_OFFER_REQUIRED_QUANTITY, DROP COLUMN AUTO_OFFER_DISCOUNT_PER_GROUP,
--       DROP COLUMN AUTO_OFFER_ELIGIBLE_QUANTITY, DROP COLUMN AUTO_OFFER_GROUPS,
--       DROP COLUMN AUTO_OFFER_DISCOUNT, DROP COLUMN IDEMPOTENCY_KEY;
--   DROP TABLE AUTOMATIC_OFFER_CATEGORIES;
--   DROP TABLE AUTOMATIC_OFFER_PRODUCTS;
--   DROP TABLE AUTOMATIC_OFFERS;
--
-- ORDER_ITEMS.DISCOUNT_AMOUNT is deliberately not touched by the rollback: it existed before this
-- migration and dropping it would lose data that predates the offer.
-- ---------------------------------------------------------------------------------------------
