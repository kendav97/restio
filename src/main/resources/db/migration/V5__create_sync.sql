-- Module sync: operational events uploaded by the devices of each restaurant.
-- The primary key is the event's own identifier, so uploads are idempotent end to end.

CREATE TABLE sync_events (
    event_id         UUID         NOT NULL,
    restaurant_id    BIGINT       NOT NULL,
    type             VARCHAR(100) NOT NULL,
    aggregate_id     UUID         NOT NULL,
    device_uuid      UUID         NOT NULL,
    user_id          BIGINT,
    schema_version   INTEGER      NOT NULL,
    payload          JSONB        NOT NULL,
    hlc              VARCHAR(64)  NOT NULL,
    epoch            BIGINT       NOT NULL,
    seq              BIGINT,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    uploaded_by      BIGINT       NOT NULL,
    CONSTRAINT pk_sync_events PRIMARY KEY (event_id),
    CONSTRAINT ck_sync_events_schema_version CHECK (schema_version > 0),
    CONSTRAINT ck_sync_events_epoch CHECK (epoch >= 0)
);

CREATE INDEX idx_sync_events_restaurant_hlc ON sync_events (restaurant_id, hlc);
CREATE INDEX idx_sync_events_aggregate ON sync_events (aggregate_id, hlc);
