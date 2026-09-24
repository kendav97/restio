-- Table backing the fixtures used by the shared isolation and auditing tests.
CREATE TABLE test_scoped_entities (
    id            BIGINT PRIMARY KEY,
    restaurant_id BIGINT      NOT NULL,
    name          TEXT        NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by    TEXT,
    updated_by    TEXT,
    version       BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_test_scoped_entities_restaurant ON test_scoped_entities (restaurant_id);
