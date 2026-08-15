-- Reports stored phone numbers that do not match the new Indian-mobile rule.
--
-- READ-ONLY BY DESIGN. It writes nothing.
--
-- The rule (PhoneNumbers.java / phone.js) is: ten national digits beginning 6-9, stored as
-- +91XXXXXXXXXX. Existing rows predate it and may hold anything the old @Size(max = 20) allowed —
-- landlines, numbers with extensions, foreign numbers, or plain junk.
--
-- Rewriting those automatically is the wrong move and the brief says so explicitly. There is no
-- safe machine transformation from "0221234567" to a mobile number: guessing would attach a
-- customer's order to a phone number that is not theirs, and silently discarding would destroy the
-- only contact detail on an order that may still need delivering. So this reports, a human decides,
-- and the application simply stops accepting new bad values.
--
-- Existing rows are otherwise untouched: nothing reads a stored number back through the validator,
-- so a legacy value keeps working as a display string until someone edits that record, at which
-- point the new rule applies to what they type.
--
-- Safe to run repeatedly.

SELECT 'USERS' AS SOURCE, USER_ID AS RECORD_ID, PHONE_NUMBER AS STORED_VALUE,
       CASE
         WHEN PHONE_NUMBER REGEXP '^\\+91[6-9][0-9]{9}$' THEN 'canonical'
         WHEN PHONE_NUMBER REGEXP '^[6-9][0-9]{9}$'      THEN 'valid mobile, not yet E.164'
         ELSE 'needs review'
       END AS STATUS
FROM USERS
WHERE PHONE_NUMBER IS NOT NULL AND PHONE_NUMBER <> ''
  AND PHONE_NUMBER NOT REGEXP '^\\+91[6-9][0-9]{9}$'

UNION ALL

SELECT 'ADDRESSES', ADDRESS_ID, PHONE_NUMBER,
       CASE
         WHEN PHONE_NUMBER REGEXP '^\\+91[6-9][0-9]{9}$' THEN 'canonical'
         WHEN PHONE_NUMBER REGEXP '^[6-9][0-9]{9}$'      THEN 'valid mobile, not yet E.164'
         ELSE 'needs review'
       END
FROM ADDRESSES
WHERE PHONE_NUMBER IS NOT NULL AND PHONE_NUMBER <> ''
  AND PHONE_NUMBER NOT REGEXP '^\\+91[6-9][0-9]{9}$'

UNION ALL

-- Order shipping phone is a point-in-time copy of the delivery contact. It is historical record,
-- not editable customer data, so it is reported for completeness and must not be rewritten at all.
SELECT 'ORDERS.SHIPPING_PHONE', ORDER_ID, SHIPPING_PHONE,
       CASE
         WHEN SHIPPING_PHONE REGEXP '^\\+91[6-9][0-9]{9}$' THEN 'canonical'
         WHEN SHIPPING_PHONE REGEXP '^[6-9][0-9]{9}$'      THEN 'valid mobile, not yet E.164'
         ELSE 'needs review'
       END
FROM ORDERS
WHERE SHIPPING_PHONE IS NOT NULL AND SHIPPING_PHONE <> ''
  AND SHIPPING_PHONE NOT REGEXP '^\\+91[6-9][0-9]{9}$'

ORDER BY SOURCE, RECORD_ID;
