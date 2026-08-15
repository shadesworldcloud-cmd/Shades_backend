-- ============================================================
-- Seed data for Shades_world Sunglass Store
-- Runs on every startup; uses INSERT IGNORE to be idempotent.
-- ============================================================

-- Roles
INSERT IGNORE INTO ROLES (role_name, description) VALUES ('CUSTOMER', 'Default customer role');
INSERT IGNORE INTO ROLES (role_name, description) VALUES ('ADMIN', 'Full administrative access');
INSERT IGNORE INTO ROLES (role_name, description) VALUES ('SUPPORT', 'Customer support team');
INSERT IGNORE INTO ROLES (role_name, description) VALUES ('INVENTORY_MANAGER', 'Manages product inventory');

-- Permissions
INSERT IGNORE INTO PERMISSIONS (permission_name, description) VALUES ('PRODUCT_CREATE', 'Create products');
INSERT IGNORE INTO PERMISSIONS (permission_name, description) VALUES ('PRODUCT_UPDATE', 'Update products');
INSERT IGNORE INTO PERMISSIONS (permission_name, description) VALUES ('PRODUCT_DELETE', 'Delete products');
INSERT IGNORE INTO PERMISSIONS (permission_name, description) VALUES ('ORDER_VIEW_ALL', 'View all orders');
INSERT IGNORE INTO PERMISSIONS (permission_name, description) VALUES ('ORDER_UPDATE', 'Update order status');
INSERT IGNORE INTO PERMISSIONS (permission_name, description) VALUES ('USER_MANAGE', 'Manage users');
INSERT IGNORE INTO PERMISSIONS (permission_name, description) VALUES ('COUPON_MANAGE', 'Manage coupons');
INSERT IGNORE INTO PERMISSIONS (permission_name, description) VALUES ('INVENTORY_MANAGE', 'Manage inventory');
INSERT IGNORE INTO PERMISSIONS (permission_name, description) VALUES ('REVIEW_MODERATE', 'Moderate reviews');
INSERT IGNORE INTO PERMISSIONS (permission_name, description) VALUES ('RETURN_MANAGE', 'Manage returns');
INSERT IGNORE INTO PERMISSIONS (permission_name, description) VALUES ('REFUND_PROCESS', 'Process refunds');
INSERT IGNORE INTO PERMISSIONS (permission_name, description) VALUES ('SHIPMENT_MANAGE', 'Manage shipments');
INSERT IGNORE INTO PERMISSIONS (permission_name, description) VALUES ('REPORT_VIEW', 'View reports');

-- Role-Permission mappings (ADMIN gets everything)
INSERT IGNORE INTO ROLE_PERMISSIONS (role_id, permission_id)
SELECT r.role_id, p.permission_id FROM ROLES r, PERMISSIONS p WHERE r.role_name = 'ADMIN';

-- SUPPORT permissions
INSERT IGNORE INTO ROLE_PERMISSIONS (role_id, permission_id)
SELECT r.role_id, p.permission_id FROM ROLES r, PERMISSIONS p
WHERE r.role_name = 'SUPPORT' AND p.permission_name IN (
    'ORDER_VIEW_ALL', 'ORDER_UPDATE', 'REVIEW_MODERATE', 'RETURN_MANAGE',
    'REFUND_PROCESS', 'SHIPMENT_MANAGE'
);

-- INVENTORY_MANAGER permissions
INSERT IGNORE INTO ROLE_PERMISSIONS (role_id, permission_id)
SELECT r.role_id, p.permission_id FROM ROLES r, PERMISSIONS p
WHERE r.role_name = 'INVENTORY_MANAGER' AND p.permission_name IN (
    'PRODUCT_CREATE', 'PRODUCT_UPDATE', 'INVENTORY_MANAGE'
);

-- Sample categories
INSERT IGNORE INTO CATEGORIES (category_name, description, is_active) VALUES ('Aviator', 'Classic aviator style sunglasses', true);
INSERT IGNORE INTO CATEGORIES (category_name, description, is_active) VALUES ('Wayfarer', 'Iconic wayfarer frame sunglasses', true);
INSERT IGNORE INTO CATEGORIES (category_name, description, is_active) VALUES ('Round', 'Round frame sunglasses', true);
INSERT IGNORE INTO CATEGORIES (category_name, description, is_active) VALUES ('Sports', 'Sports and performance sunglasses', true);
INSERT IGNORE INTO CATEGORIES (category_name, description, is_active) VALUES ('Cat Eye', 'Stylish cat eye sunglasses', true);
INSERT IGNORE INTO CATEGORIES (category_name, description, is_active) VALUES ('Oversized', 'Oversized frame sunglasses', true);

-- Tax rates
INSERT IGNORE INTO TAX_RATES (state_code, state_name, cgst_rate, sgst_rate, igst_rate, is_active)
VALUES ('MH', 'Maharashtra', 9.00, 9.00, 18.00, true);
INSERT IGNORE INTO TAX_RATES (state_code, state_name, cgst_rate, sgst_rate, igst_rate, is_active)
VALUES ('KA', 'Karnataka', 9.00, 9.00, 18.00, true);
INSERT IGNORE INTO TAX_RATES (state_code, state_name, cgst_rate, sgst_rate, igst_rate, is_active)
VALUES ('DL', 'Delhi', 9.00, 9.00, 18.00, true);

-- App config
INSERT IGNORE INTO CONFIG (config_key, config_value, description)
VALUES ('FREE_SHIPPING_THRESHOLD', '500.00', 'Minimum order amount for free shipping');
INSERT IGNORE INTO CONFIG (config_key, config_value, description)
VALUES ('STANDARD_SHIPPING_RATE', '49.00', 'Standard shipping cost');
INSERT IGNORE INTO CONFIG (config_key, config_value, description)
VALUES ('MAX_CART_ITEMS', '20', 'Maximum items allowed in cart');
INSERT IGNORE INTO CONFIG (config_key, config_value, description)
VALUES ('RETURN_WINDOW_DAYS', '15', 'Number of days allowed for returns');
