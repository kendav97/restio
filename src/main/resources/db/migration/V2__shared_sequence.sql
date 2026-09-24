-- Single sequence shared by every business entity (AuditableEntity). Hibernate reserves blocks of
-- 50 identifiers, so the increment must match the entity's allocationSize.
CREATE SEQUENCE restio_seq START WITH 1 INCREMENT BY 50;
