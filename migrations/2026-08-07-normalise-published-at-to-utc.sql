-- Repairs the PUBLISHED_AT backfill from 2026-08-07-add-product-published-at.sql.
--
-- That migration set PUBLISHED_AT = CREATED_AT for existing active products. CREATED_AT is written
-- by the application as LocalDateTime.now(), i.e. the server's LOCAL wall clock, whereas
-- PUBLISHED_AT is now mapped as an Instant and therefore read back as UTC. On a server east of
-- Greenwich that makes every backfilled row look newer than it is by the server's offset — five and
-- a half hours in IST — which can hold a product's New badge past the boundary.
--
-- Backfilled rows are identifiable exactly: only they satisfy PUBLISHED_AT = CREATED_AT. A row the
-- application stamped holds the same moment expressed in UTC, so its PUBLISHED_AT differs from its
-- local CREATED_AT by the offset and is left untouched. (Where the server runs at UTC the two are
-- equal, the offset below is zero, and the statement is a no-op — which is also why it is safe to
-- run repeatedly: after the shift the equality no longer holds.)
--
-- TIMESTAMPDIFF(SECOND, UTC_TIMESTAMP(), NOW()) is the server's current UTC offset in seconds,
-- computed without depending on the MySQL timezone tables being loaded (CONVERT_TZ needs those and
-- returns NULL when they are missing, which would have silently blanked the column).
--
-- Caveat worth knowing: this applies TODAY'S offset to historical rows, so a product created on the
-- other side of a DST transition is corrected to within one hour rather than exactly. The dev and
-- test servers here run in a zone without DST, and against a 30-day window an hour only matters for
-- a row sitting within an hour of the boundary.

SET @utc_offset_seconds = TIMESTAMPDIFF(SECOND, UTC_TIMESTAMP(), NOW());

UPDATE PRODUCTS
SET PUBLISHED_AT = PUBLISHED_AT - INTERVAL @utc_offset_seconds SECOND
WHERE PUBLISHED_AT IS NOT NULL
  AND PUBLISHED_AT = CREATED_AT
  AND @utc_offset_seconds <> 0;
