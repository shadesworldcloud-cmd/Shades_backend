-- Reviews are published on submission; a moderator's job is now to take an abusive one down,
-- not to let a legitimate one up.
--
-- ORDER MATTERS. REVIEWS carries CHK_REVIEW_STATUS, which permits only PENDING/APPROVED/REJECTED,
-- so the constraint has to accept PUBLISHED before any row can be given that value — and before
-- the application can persist a new review at all. Widening the constraint first, then migrating
-- the data, keeps every intermediate state legal.
--
-- Existing PENDING rows were legitimate reviews from eligible customers that were simply never
-- approved, so they become visible. REJECTED rows are left exactly as they are: a takedown must
-- survive this change. APPROVED rows are left alone too — they are already visible, and the value
-- records that a human explicitly approved them.
--
-- Safe to execute repeatedly on the same MySQL schema.

SET @widen_review_status = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.CHECK_CONSTRAINTS
           WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = 'CHK_REVIEW_STATUS'
             AND CHECK_CLAUSE LIKE '%PUBLISHED%'),
    'SELECT 1',
    'ALTER TABLE REVIEWS DROP CHECK CHK_REVIEW_STATUS'
);
PREPARE review_status_statement FROM @widen_review_status;
EXECUTE review_status_statement;
DEALLOCATE PREPARE review_status_statement;

SET @add_review_status_check = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.CHECK_CONSTRAINTS
           WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = 'CHK_REVIEW_STATUS'),
    'SELECT 1',
    'ALTER TABLE REVIEWS ADD CONSTRAINT CHK_REVIEW_STATUS CHECK (REVIEW_STATUS IN (''PUBLISHED'',''PENDING'',''APPROVED'',''REJECTED''))'
);
PREPARE review_status_statement FROM @add_review_status_check;
EXECUTE review_status_statement;
DEALLOCATE PREPARE review_status_statement;

UPDATE REVIEWS
   SET REVIEW_STATUS = 'PUBLISHED'
 WHERE REVIEW_STATUS = 'PENDING';
