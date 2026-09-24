package com.restio.auth;

/**
 * Catalog of permissions (restio-api-conventions). Each one is granted as a Spring Security
 * authority with the same name, so endpoints check them with {@code hasAuthority('ORDER_VOID')}.
 */
public enum Permission {
    RESTAURANT_MANAGE,
    DEVICE_MANAGE,
    USER_MANAGE,
    MENU_MANAGE,
    TABLE_MANAGE,
    TABLE_OPERATE,
    RESERVATION_MANAGE,
    ORDER_CREATE,
    ORDER_VOID,
    KITCHEN_OPERATE,
    BILL_CHARGE,
    DISCOUNT_APPLY,
    CASH_CLOSE,
    INVENTORY_VIEW,
    INVENTORY_MANAGE,
    PURCHASE_MANAGE,
    STAFF_MANAGE,
    TIME_CLOCK,
    CUSTOMER_VIEW,
    CUSTOMER_MANAGE,
    LOYALTY_MANAGE,
    ONLINE_MANAGE,
    REPORT_VIEW,
    AUDIT_VIEW
}
