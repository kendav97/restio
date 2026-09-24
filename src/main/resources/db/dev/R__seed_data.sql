-- Development-only sample data (profile: dev). Repeatable migration: must stay idempotent.
-- Credentials (dev only):
--   backoffice: admin@restio.local / admin12345 (ADMIN)
--   POS PIN:    1234 (Camarero Demo, WAITER), 9999 (admin)
--   device:     id 100 (D01), token dev-device-token (POS of restaurant 1)
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO users (id, email, display_name, password_hash, pin_hash, created_at, updated_at,
                   created_by, updated_by)
VALUES (100, 'admin@restio.local', 'Admin Demo', crypt('admin12345', gen_salt('bf', 10)),
        crypt('9999', gen_salt('bf', 10)), now(), now(), 'seed', 'seed'),
       (101, 'waiter@restio.local', 'Camarero Demo', NULL,
        crypt('1234', gen_salt('bf', 10)), now(), now(), 'seed', 'seed')
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
VALUES (100, 1), (101, 3)
ON CONFLICT DO NOTHING;

INSERT INTO user_restaurants (user_id, restaurant_id)
VALUES (100, 1), (101, 1)
ON CONFLICT DO NOTHING;

INSERT INTO devices (id, restaurant_id, uuid, code, name, type, device_token_hash, created_at,
                     updated_at, created_by, updated_by)
VALUES (100, 1, '00000000-0000-4000-8000-000000000100', 'D01', 'TPV Barra', 'POS',
encode(digest('dev-device-token', 'sha256'), 'hex'),
        now(), now(), 'seed', 'seed')
ON CONFLICT (id) DO NOTHING;
