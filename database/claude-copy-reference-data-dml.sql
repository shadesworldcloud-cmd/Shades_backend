-- Shades World safe, idempotent bootstrap/reference DML
-- Covers the full permission vocabulary found in the project seed files.
-- Excludes users, credentials, tokens, carts, orders, payments and other private/transactional data.

USE ECOMMERCE_DB;
START TRANSACTION;

INSERT INTO ROLES (ROLE_NAME, DESCRIPTION) VALUES
 ('CUSTOMER', 'Regular e-commerce customer'),
 ('ADMIN', 'Full system administrator'),
 ('SUPPORT', 'Customer support employee'),
 ('INVENTORY_MANAGER', 'Manages products and inventory')
ON DUPLICATE KEY UPDATE DESCRIPTION = VALUES(DESCRIPTION);

INSERT INTO PERMISSIONS (PERMISSION_NAME, DESCRIPTION) VALUES
 ('PRODUCT_READ', 'View products'),
 ('PRODUCT_WRITE', 'Create and update products'),
 ('PRODUCT_CREATE', 'Create products'),
 ('PRODUCT_UPDATE', 'Update products'),
 ('PRODUCT_DELETE', 'Delete products'),
 ('ORDER_CREATE', 'Place an order'),
 ('ORDER_READ_OWN', 'View own orders'),
 ('ORDER_READ_ALL', 'View every order'),
 ('ORDER_VIEW_ALL', 'View all orders'),
 ('ORDER_UPDATE', 'Update order status'),
 ('USER_READ', 'View user details'),
 ('USER_UPDATE', 'Update own user details'),
 ('USER_MANAGE', 'Manage users'),
 ('RETURN_CREATE', 'Request a return'),
 ('RETURN_MANAGE', 'Approve or reject returns'),
 ('REFUND_PROCESS', 'Process refunds'),
 ('INVENTORY_MANAGE', 'Manage inventory'),
 ('COUPON_MANAGE', 'Manage coupons and offers'),
 ('REVIEW_MODERATE', 'Moderate reviews'),
 ('SHIPMENT_MANAGE', 'Manage shipments'),
 ('REPORT_VIEW', 'View reports')
ON DUPLICATE KEY UPDATE DESCRIPTION = VALUES(DESCRIPTION);

-- ADMIN receives every current permission.
INSERT IGNORE INTO ROLE_PERMISSIONS (ROLE_ID, PERMISSION_ID)
SELECT r.ROLE_ID, p.PERMISSION_ID
FROM ROLES r CROSS JOIN PERMISSIONS p
WHERE r.ROLE_NAME = 'ADMIN';

-- Customer self-service permissions.
INSERT IGNORE INTO ROLE_PERMISSIONS (ROLE_ID, PERMISSION_ID)
SELECT r.ROLE_ID, p.PERMISSION_ID
FROM ROLES r JOIN PERMISSIONS p
  ON p.PERMISSION_NAME IN ('PRODUCT_READ','ORDER_CREATE','ORDER_READ_OWN','USER_UPDATE','RETURN_CREATE')
WHERE r.ROLE_NAME = 'CUSTOMER';

-- Support workflow permissions.
INSERT IGNORE INTO ROLE_PERMISSIONS (ROLE_ID, PERMISSION_ID)
SELECT r.ROLE_ID, p.PERMISSION_ID
FROM ROLES r JOIN PERMISSIONS p
  ON p.PERMISSION_NAME IN ('PRODUCT_READ','ORDER_READ_ALL','ORDER_VIEW_ALL','ORDER_UPDATE',
                           'USER_READ','RETURN_MANAGE','REFUND_PROCESS','REVIEW_MODERATE',
                           'SHIPMENT_MANAGE')
WHERE r.ROLE_NAME = 'SUPPORT';

-- Catalogue and stock permissions.
INSERT IGNORE INTO ROLE_PERMISSIONS (ROLE_ID, PERMISSION_ID)
SELECT r.ROLE_ID, p.PERMISSION_ID
FROM ROLES r JOIN PERMISSIONS p
  ON p.PERMISSION_NAME IN ('PRODUCT_READ','PRODUCT_WRITE','PRODUCT_CREATE','PRODUCT_UPDATE',
                           'INVENTORY_MANAGE')
WHERE r.ROLE_NAME = 'INVENTORY_MANAGER';

INSERT INTO CATEGORIES (CATEGORY_NAME, DESCRIPTION, IS_ACTIVE) VALUES
 ('Men', 'Eyewear designed for men', TRUE),
 ('Women', 'Eyewear designed for women', TRUE),
 ('Unisex', 'Eyewear designed for everyone', TRUE),
 ('Accessory', 'Eyewear accessories and care products', TRUE)
ON DUPLICATE KEY UPDATE DESCRIPTION = VALUES(DESCRIPTION), IS_ACTIVE = VALUES(IS_ACTIVE);

-- TAX_RATES has no natural-key constraint, so insert a baseline only when absent.
INSERT INTO TAX_RATES (TAX_NAME, COUNTRY, STATE, RATE_PERCENT, IS_ACTIVE)
SELECT 'GST 18%', 'India', NULL, 18.0000, TRUE
WHERE NOT EXISTS (
  SELECT 1 FROM TAX_RATES WHERE TAX_NAME='GST 18%' AND COUNTRY='India' AND STATE IS NULL
);

INSERT INTO CONFIG (CONFIG_SHORT_CODE, CONFIG_VALUE, DESCRIPTION) VALUES
 ('FREE_SHIPPING_THRESHOLD', '500.00', 'Minimum order amount for free shipping'),
 ('STANDARD_SHIPPING_RATE', '49.00', 'Standard shipping cost'),
 ('MAX_CART_ITEMS', '20', 'Maximum items allowed in cart'),
 ('RETURN_WINDOW_DAYS', '15', 'Number of days allowed for returns')
ON DUPLICATE KEY UPDATE CONFIG_VALUE=VALUES(CONFIG_VALUE), DESCRIPTION=VALUES(DESCRIPTION);

COMMIT;
