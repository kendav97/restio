-- Baseline schema: only the infrastructure tables the platform itself needs.
-- Business tables are introduced by their own module migrations (stage 0.2 onwards).

-- Spring Modulith Event Publication Registry (spring-modulith-starter-jpa).
-- Managed by Flyway instead of the starter's schema initialization so that the
-- application always runs with ddl-auto=none and a validated schema.
CREATE TABLE event_publication (
    id                UUID        NOT NULL,
    listener_id       TEXT        NOT NULL,
    event_type        TEXT        NOT NULL,
    serialized_event  TEXT        NOT NULL,
    publication_date  TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date   TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_event_publication PRIMARY KEY (id)
);

CREATE INDEX idx_event_publication_incomplete
    ON event_publication (completion_date)
    WHERE completion_date IS NULL;

CREATE INDEX idx_event_publication_by_listener
    ON event_publication (listener_id, serialized_event);
