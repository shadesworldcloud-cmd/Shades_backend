-- Public product slugs. Replaces the sequential PRODUCT_ID in the storefront URL
-- (/product/22 -> /product/classic-aviator-sunglasses).
--
-- Why a column rather than deriving the slug from the name on every request: the slug has to stay
-- STABLE across a rename. A derived slug changes the moment an admin fixes a typo in a product
-- name, silently breaking every shared link, bookmark and search-engine result pointing at it.
-- Storing it makes the URL a fact about the product rather than a function of its current title.
--
-- PRODUCT_ID is deliberately kept as the primary key and as the foreign key every other table
-- references. The slug is an additional PUBLIC identifier, not a replacement for the internal one:
-- re-keying 38 tables to gain nothing internally would be a far larger and riskier change, and the
-- brief's requirement is that the id stops appearing in public URLs, not that it stops existing.
--
-- A slug is NOT an access control. Everything under /api/products still authorises on its own —
-- an unguessable URL is not permission. See ProductSlugs' class comment.
--
-- Ordering matters here and is the whole reason this file is not two statements: the column is
-- added NULLable, every row is given a unique value, and only THEN do the UNIQUE index and the
-- NOT NULL constraint go on. Adding the constraint first would reject the ALTER outright on any
-- catalogue with two products of the same name.
--
-- Safe to execute repeatedly: every step is guarded, and the backfill only ever touches rows whose
-- SLUG is still NULL.

-- ── 1. The column ─────────────────────────────────────────────────────────────────────────
SET @add_slug = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PRODUCTS'
             AND COLUMN_NAME = 'SLUG'),
    'SELECT 1',
    'ALTER TABLE PRODUCTS ADD COLUMN SLUG VARCHAR(160) NULL AFTER PRODUCT_NAME'
);
PREPARE slug_statement FROM @add_slug;
EXECUTE slug_statement;
DEALLOCATE PREPARE slug_statement;

-- ── 2. Base slug from the product name ────────────────────────────────────────────────────
-- Lowercase, every run of non-alphanumeric characters collapsed to one hyphen, hyphens trimmed
-- from both ends, then capped at 100 characters to leave room for a suffix.
--
-- Known and accepted divergence from ProductSlugs.toBaseSlug: Java decomposes with NFKD first, so
-- "Café" becomes "cafe" there and "caf" here — MySQL has no Unicode decomposition and emulating it
-- with nested REPLACE() would cover Latin-1 and still miss everything else. Verified irrelevant for
-- the data this runs against: 0 of 5 rows in ECOMMERCE_DB and 0 of 1182 in ECOMMERCE_TEST_DB have a
-- non-ASCII PRODUCT_NAME. Any row it did affect would still get a valid, unique, stable slug — just
-- a slightly less pretty one — and every slug created after this migration comes from the Java rule.
UPDATE PRODUCTS
SET SLUG = TRIM(BOTH '-' FROM LEFT(
        TRIM(BOTH '-' FROM REGEXP_REPLACE(LOWER(PRODUCT_NAME), '[^a-z0-9]+', '-')), 100))
WHERE SLUG IS NULL;

-- ── 3. Slugs that are unusable on their own ───────────────────────────────────────────────
-- Three cases, all of which must fall back to an opaque slug:
--   - empty: a name with no ASCII alphanumerics at all ("太陽眼鏡").
--   - all digits: a product named "2024" would slug to "2024", and /product/2024 could then mean
--     either that slug or the legacy numeric id 2024. The redirect must never have to guess.
--   - reserved: route and API segments, kept in step with ProductSlugs.RESERVED.
SET @alpha = 'bcdfghjkmnpqrstvwxyz23456789';

UPDATE PRODUCTS
SET SLUG = CONCAT(
        IF(SLUG = '', 'sw', SLUG), '-',
        CONCAT(
            SUBSTRING(@alpha, FLOOR(RAND() * 28) + 1, 1), SUBSTRING(@alpha, FLOOR(RAND() * 28) + 1, 1),
            SUBSTRING(@alpha, FLOOR(RAND() * 28) + 1, 1), SUBSTRING(@alpha, FLOOR(RAND() * 28) + 1, 1),
            SUBSTRING(@alpha, FLOOR(RAND() * 28) + 1, 1), SUBSTRING(@alpha, FLOOR(RAND() * 28) + 1, 1)))
WHERE SLUG = ''
   OR SLUG REGEXP '^[0-9]+$'
   OR SLUG IN ('admin', 'api', 'account', 'best-sellers', 'cart', 'categories', 'category',
               'collections', 'checkout', 'images', 'index', 'info', 'my-orders', 'new',
               'notifications', 'null', 'order', 'orders', 'product', 'products', 'search',
               'signin', 'signup', 'shop', 'slug', 'static', 'undefined', 'uploads', 'variants',
               'wishlist');

-- ── 4. Duplicate names ────────────────────────────────────────────────────────────────────
-- The lowest PRODUCT_ID in each group keeps the clean slug; every later row gets a random suffix.
-- Deterministic in the sense that matters — which row keeps the clean slug never changes between
-- runs — while the suffix itself is random, because a suffix computed from PRODUCT_ID is
-- brute-forceable across a catalogue this size and would republish the identifier being removed.
--
-- Run three times rather than once. A single pass can, in principle, draw the same six characters
-- for two rows of the same group (28^6 = 481M, so ~1 in 10^6 for a catalogue of this size); each
-- pass re-rolls whatever is still duplicated. If anything somehow survived all three, step 5's
-- UNIQUE index fails loudly and this file stops — which is the correct outcome, because a silent
-- half-applied slug backfill is far worse than a migration that refuses to finish.
--
-- A temporary table rather than an UPDATE ... JOIN (SELECT ... FROM PRODUCTS): MySQL will not let a
-- statement read the table it is updating.
DROP TEMPORARY TABLE IF EXISTS SLUG_DUPES;
CREATE TEMPORARY TABLE SLUG_DUPES (PRODUCT_ID BIGINT PRIMARY KEY);

INSERT INTO SLUG_DUPES
SELECT PRODUCT_ID FROM (
    SELECT PRODUCT_ID, ROW_NUMBER() OVER (PARTITION BY SLUG ORDER BY PRODUCT_ID) AS RN FROM PRODUCTS
) RANKED WHERE RN > 1;
UPDATE PRODUCTS P JOIN SLUG_DUPES D ON D.PRODUCT_ID = P.PRODUCT_ID
SET P.SLUG = CONCAT(TRIM(BOTH '-' FROM LEFT(P.SLUG, 153)), '-', CONCAT(
        SUBSTRING(@alpha, FLOOR(RAND() * 28) + 1, 1), SUBSTRING(@alpha, FLOOR(RAND() * 28) + 1, 1),
        SUBSTRING(@alpha, FLOOR(RAND() * 28) + 1, 1), SUBSTRING(@alpha, FLOOR(RAND() * 28) + 1, 1),
        SUBSTRING(@alpha, FLOOR(RAND() * 28) + 1, 1), SUBSTRING(@alpha, FLOOR(RAND() * 28) + 1, 1)));

DELETE FROM SLUG_DUPES;
INSERT INTO SLUG_DUPES
SELECT PRODUCT_ID FROM (
    SELECT PRODUCT_ID, ROW_NUMBER() OVER (PARTITION BY SLUG ORDER BY PRODUCT_ID) AS RN FROM PRODUCTS
) RANKED WHERE RN > 1;
UPDATE PRODUCTS P JOIN SLUG_DUPES D ON D.PRODUCT_ID = P.PRODUCT_ID
SET P.SLUG = CONCAT(TRIM(BOTH '-' FROM LEFT(P.SLUG, 153)), '-', CONCAT(
        SUBSTRING(@alpha, FLOOR(RAND() * 28) + 1, 1), SUBSTRING(@alpha, FLOOR(RAND() * 28) + 1, 1),
        SUBSTRING(@alpha, FLOOR(RAND() * 28) + 1, 1), SUBSTRING(@alpha, FLOOR(RAND() * 28) + 1, 1),
        SUBSTRING(@alpha, FLOOR(RAND() * 28) + 1, 1), SUBSTRING(@alpha, FLOOR(RAND() * 28) + 1, 1)));

DELETE FROM SLUG_DUPES;
INSERT INTO SLUG_DUPES
SELECT PRODUCT_ID FROM (
    SELECT PRODUCT_ID, ROW_NUMBER() OVER (PARTITION BY SLUG ORDER BY PRODUCT_ID) AS RN FROM PRODUCTS
) RANKED WHERE RN > 1;
UPDATE PRODUCTS P JOIN SLUG_DUPES D ON D.PRODUCT_ID = P.PRODUCT_ID
SET P.SLUG = CONCAT(TRIM(BOTH '-' FROM LEFT(P.SLUG, 153)), '-', CONCAT(
        SUBSTRING(@alpha, FLOOR(RAND() * 28) + 1, 1), SUBSTRING(@alpha, FLOOR(RAND() * 28) + 1, 1),
        SUBSTRING(@alpha, FLOOR(RAND() * 28) + 1, 1), SUBSTRING(@alpha, FLOOR(RAND() * 28) + 1, 1),
        SUBSTRING(@alpha, FLOOR(RAND() * 28) + 1, 1), SUBSTRING(@alpha, FLOOR(RAND() * 28) + 1, 1)));

DROP TEMPORARY TABLE IF EXISTS SLUG_DUPES;

-- ── 5. Constraints, only now that every row holds a unique value ──────────────────────────
SET @add_slug_index = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PRODUCTS'
             AND INDEX_NAME = 'UQ_PRODUCTS_SLUG'),
    'SELECT 1',
    'ALTER TABLE PRODUCTS ADD CONSTRAINT UQ_PRODUCTS_SLUG UNIQUE (SLUG)'
);
PREPARE slug_index_statement FROM @add_slug_index;
EXECUTE slug_index_statement;
DEALLOCATE PREPARE slug_index_statement;

-- NOT NULL last. The unique index above doubles as the lookup index for
-- ProductRepository.findBySlug, which is the single hottest query the storefront makes, so no
-- separate index is needed.
SET @slug_not_null = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PRODUCTS'
             AND COLUMN_NAME = 'SLUG' AND IS_NULLABLE = 'NO'),
    'SELECT 1',
    'ALTER TABLE PRODUCTS MODIFY COLUMN SLUG VARCHAR(160) NOT NULL'
);
PREPARE slug_null_statement FROM @slug_not_null;
EXECUTE slug_null_statement;
DEALLOCATE PREPARE slug_null_statement;

-- ── Rollback ──────────────────────────────────────────────────────────────────────────────
-- Drops the public identifier and returns the storefront to numeric URLs. No other table
-- references SLUG, so nothing cascades; PRODUCT_ID was never touched, so every relationship in the
-- schema is unaffected. Any link already shared in slug form stops resolving — that is inherent to
-- withdrawing the identifier, not something the rollback can avoid.
--
-- ALTER TABLE PRODUCTS DROP INDEX UQ_PRODUCTS_SLUG;
-- ALTER TABLE PRODUCTS DROP COLUMN SLUG;
