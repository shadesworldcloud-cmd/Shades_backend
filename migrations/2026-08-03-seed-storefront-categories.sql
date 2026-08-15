-- Uses the existing CATEGORIES table. Safe to execute repeatedly.
INSERT INTO CATEGORIES (CATEGORY_NAME, DESCRIPTION, IS_ACTIVE, CREATED_AT)
VALUES
    ('Men', 'Eyewear designed for men', TRUE, CURRENT_TIMESTAMP),
    ('Women', 'Eyewear designed for women', TRUE, CURRENT_TIMESTAMP),
    ('Unisex', 'Eyewear designed for everyone', TRUE, CURRENT_TIMESTAMP),
    ('Accessory', 'Eyewear accessories and care products', TRUE, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
    IS_ACTIVE = TRUE,
    DESCRIPTION = VALUES(DESCRIPTION);
