-- Module auth: users, roles and permissions, registered devices and refresh tokens.

CREATE TABLE roles (
    id          BIGINT      NOT NULL,
    name        VARCHAR(50) NOT NULL,
    description TEXT,
    system      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by  TEXT,
    updated_by  TEXT,
    version     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uq_roles_name UNIQUE (name)
);

CREATE TABLE role_permissions (
    role_id    BIGINT      NOT NULL,
    permission VARCHAR(50) NOT NULL,
    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
);

CREATE TABLE users (
    id              BIGINT       NOT NULL,
    email           VARCHAR(254) NOT NULL,
    display_name    VARCHAR(100) NOT NULL,
    password_hash   VARCHAR(100),
    pin_hash        VARCHAR(100),
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_attempts INTEGER      NOT NULL DEFAULT 0,
    locked_until    TIMESTAMP WITH TIME ZONE,
    last_login_at   TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by      TEXT,
    updated_by      TEXT,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT ck_users_failed_attempts CHECK (failed_attempts >= 0)
);

CREATE UNIQUE INDEX uq_users_email ON users (lower(email));

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

CREATE INDEX idx_user_roles_role ON user_roles (role_id);

-- restaurant_id references the restaurant module (stage 1.1); no FK across modules yet.
CREATE TABLE user_restaurants (
    user_id       BIGINT NOT NULL,
    restaurant_id BIGINT NOT NULL,
    CONSTRAINT pk_user_restaurants PRIMARY KEY (user_id, restaurant_id),
    CONSTRAINT fk_user_restaurants_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_restaurants_restaurant ON user_restaurants (restaurant_id);

CREATE TABLE devices (
    id                  BIGINT       NOT NULL,
    restaurant_id       BIGINT       NOT NULL,
    name                VARCHAR(100) NOT NULL,
    type                VARCHAR(20)  NOT NULL,
    device_token_hash   VARCHAR(64)  NOT NULL,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_pin_attempts INTEGER      NOT NULL DEFAULT 0,
    pin_locked_until    TIMESTAMP WITH TIME ZONE,
    last_seen_at        TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by          TEXT,
    updated_by          TEXT,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_devices PRIMARY KEY (id),
    CONSTRAINT uq_devices_token UNIQUE (device_token_hash),
    CONSTRAINT ck_devices_type CHECK (type IN ('POS', 'KDS', 'ADMIN')),
    CONSTRAINT ck_devices_failed_pin_attempts CHECK (failed_pin_attempts >= 0)
);

CREATE INDEX idx_devices_restaurant ON devices (restaurant_id);

CREATE TABLE refresh_tokens (
    id          BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at  TIMESTAMP WITH TIME ZONE,
    replaced_by BIGINT,
    device_id   BIGINT,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by  TEXT,
    updated_by  TEXT,
    version     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_refresh_tokens_replaced_by FOREIGN KEY (replaced_by) REFERENCES refresh_tokens (id),
    CONSTRAINT fk_refresh_tokens_device FOREIGN KEY (device_id) REFERENCES devices (id)
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_replaced_by ON refresh_tokens (replaced_by);
CREATE INDEX idx_refresh_tokens_device ON refresh_tokens (device_id);

-- The six system roles and their initial permissions (restio-api-conventions).
-- Fixed identifiers keep them stable across environments; the shared sequence is moved past the
-- reserved range 1-999 so generated identifiers never collide with them.
SELECT setval('restio_seq', 1000);

INSERT INTO roles (id, name, description, system, created_at, updated_at, created_by, updated_by)
VALUES (1, 'ADMIN', 'Full access', TRUE, now(), now(), 'system', 'system'),
       (2, 'MANAGER', 'Restaurant manager', TRUE, now(), now(), 'system', 'system'),
       (3, 'WAITER', 'Floor staff', TRUE, now(), now(), 'system', 'system'),
       (4, 'CASHIER', 'Cash desk', TRUE, now(), now(), 'system', 'system'),
       (5, 'COOK', 'Kitchen staff', TRUE, now(), now(), 'system', 'system'),
       (6, 'WAREHOUSE', 'Stock and purchasing', TRUE, now(), now(), 'system', 'system');

INSERT INTO role_permissions (role_id, permission)
SELECT 1, p FROM unnest(ARRAY[
    'RESTAURANT_MANAGE', 'USER_MANAGE', 'MENU_MANAGE', 'TABLE_MANAGE', 'TABLE_OPERATE',
    'RESERVATION_MANAGE', 'ORDER_CREATE', 'ORDER_VOID', 'KITCHEN_OPERATE', 'BILL_CHARGE',
    'DISCOUNT_APPLY', 'CASH_CLOSE', 'INVENTORY_VIEW', 'INVENTORY_MANAGE', 'PURCHASE_MANAGE',
    'STAFF_MANAGE', 'TIME_CLOCK', 'CUSTOMER_VIEW', 'CUSTOMER_MANAGE', 'LOYALTY_MANAGE',
    'ONLINE_MANAGE', 'REPORT_VIEW', 'AUDIT_VIEW']) AS p
UNION ALL
SELECT 2, p FROM unnest(ARRAY[
    'USER_MANAGE', 'MENU_MANAGE', 'TABLE_MANAGE', 'TABLE_OPERATE', 'RESERVATION_MANAGE',
    'ORDER_CREATE', 'ORDER_VOID', 'KITCHEN_OPERATE', 'BILL_CHARGE', 'DISCOUNT_APPLY',
    'CASH_CLOSE', 'INVENTORY_VIEW', 'INVENTORY_MANAGE', 'PURCHASE_MANAGE', 'STAFF_MANAGE',
    'TIME_CLOCK', 'CUSTOMER_VIEW', 'CUSTOMER_MANAGE', 'LOYALTY_MANAGE', 'ONLINE_MANAGE',
    'REPORT_VIEW']) AS p
UNION ALL
SELECT 3, p FROM unnest(ARRAY[
    'TABLE_OPERATE', 'RESERVATION_MANAGE', 'ORDER_CREATE', 'TIME_CLOCK', 'CUSTOMER_VIEW']) AS p
UNION ALL
SELECT 4, p FROM unnest(ARRAY[
    'TABLE_OPERATE', 'ORDER_CREATE', 'BILL_CHARGE', 'DISCOUNT_APPLY', 'CASH_CLOSE',
    'TIME_CLOCK', 'CUSTOMER_VIEW', 'CUSTOMER_MANAGE']) AS p
UNION ALL
SELECT 5, p FROM unnest(ARRAY['KITCHEN_OPERATE', 'INVENTORY_VIEW', 'TIME_CLOCK']) AS p
UNION ALL
SELECT 6, p FROM unnest(ARRAY[
    'INVENTORY_VIEW', 'INVENTORY_MANAGE', 'PURCHASE_MANAGE', 'TIME_CLOCK']) AS p;
