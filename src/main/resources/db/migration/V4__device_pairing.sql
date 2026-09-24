-- Module auth: device identity for synchronisation and one-time pairing codes (QR).

-- uuid identifies the device in its events (DomainEvent.deviceId); code is the short, never reused
-- name of its ticket series (D01, D02...).
ALTER TABLE devices ADD COLUMN uuid UUID;
ALTER TABLE devices ADD COLUMN code VARCHAR(8);

UPDATE devices d
SET uuid = gen_random_uuid(),
    code = numbered.code
FROM (SELECT id,
             'D' || lpad(row_number() OVER (PARTITION BY restaurant_id ORDER BY id)::TEXT, 2, '0') AS code
      FROM devices) numbered
WHERE d.id = numbered.id;

ALTER TABLE devices ALTER COLUMN uuid SET NOT NULL;
ALTER TABLE devices ALTER COLUMN code SET NOT NULL;
ALTER TABLE devices ADD CONSTRAINT uq_devices_uuid UNIQUE (uuid);
ALTER TABLE devices ADD CONSTRAINT uq_devices_restaurant_code UNIQUE (restaurant_id, code);

-- A pairing code is shown as a QR by an already linked device or the backoffice and redeemed once
-- by the new device. Only its SHA-256 hash is stored.
CREATE TABLE device_pairings (
    id            BIGINT       NOT NULL,
    restaurant_id BIGINT       NOT NULL,
    code_hash     VARCHAR(64)  NOT NULL,
    device_name   VARCHAR(100) NOT NULL,
    device_type   VARCHAR(20)  NOT NULL,
    expires_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    redeemed_at   TIMESTAMP WITH TIME ZONE,
    device_id     BIGINT,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by    TEXT,
    updated_by    TEXT,
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_device_pairings PRIMARY KEY (id),
    CONSTRAINT uq_device_pairings_code UNIQUE (code_hash),
    CONSTRAINT ck_device_pairings_type CHECK (device_type IN ('POS', 'KDS', 'ADMIN')),
    CONSTRAINT fk_device_pairings_device FOREIGN KEY (device_id) REFERENCES devices (id)
);

CREATE INDEX idx_device_pairings_restaurant ON device_pairings (restaurant_id);
CREATE INDEX idx_device_pairings_device ON device_pairings (device_id);

-- New permission to link and unlink devices.
INSERT INTO role_permissions (role_id, permission)
VALUES (1, 'DEVICE_MANAGE'), (2, 'DEVICE_MANAGE');
