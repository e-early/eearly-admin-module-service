-- Organization
CREATE TABLE t_organization
(
    id         UUID PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,

    -- Auditable fields
    created_by VARCHAR(256),
    created_at TIMESTAMPTZ,
    updated_by VARCHAR(256),
    updated_at TIMESTAMPTZ,
    is_deleted BOOLEAN      NOT NULL DEFAULT FALSE,
    version    BIGINT       NOT NULL DEFAULT 1
);
