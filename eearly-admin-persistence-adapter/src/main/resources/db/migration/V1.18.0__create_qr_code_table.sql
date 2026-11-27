CREATE TYPE e_qr_code_status AS ENUM (
    'ACTIVE',
    'USED',
    'EXPIRED',
    'REVOKED'
    );

-- QR code a patient scans to set up the mobile app
CREATE TABLE t_qr_code
(
    id                    UUID PRIMARY KEY,
    token                 VARCHAR(255) UNIQUE NOT NULL,
    expires_at            TIMESTAMPTZ         NOT NULL,
    image_url             TEXT                NOT NULL,
    mobile_app_configured BOOLEAN                      DEFAULT FALSE,
    onboarding_url        VARCHAR(512),
    status                e_qr_code_status             DEFAULT 'ACTIVE',
    id_patient            UUID                NOT NULL,
    id_caretaker          UUID,

    -- Auditable fields
    created_by            VARCHAR(256),
    created_at            TIMESTAMPTZ,
    updated_by            VARCHAR(256),
    updated_at            TIMESTAMPTZ,
    is_deleted            BOOLEAN             NOT NULL DEFAULT FALSE,
    version               BIGINT              NOT NULL DEFAULT 1,

    CONSTRAINT fk_qr_code_patient
        FOREIGN KEY (id_patient) REFERENCES t_patient (id) ON DELETE CASCADE,
    CONSTRAINT fk_qr_code_caretaker
        FOREIGN KEY (id_caretaker) REFERENCES t_caretaker (id) ON DELETE SET NULL
);

CREATE INDEX idx_qr_code_token ON t_qr_code (token);
CREATE INDEX idx_qr_code_patient ON t_qr_code (id_patient);
CREATE INDEX idx_qr_code_status ON t_qr_code (status);
CREATE INDEX idx_qr_code_expires_at ON t_qr_code (expires_at);
CREATE INDEX idx_qr_code_caretaker_id ON t_qr_code (id_caretaker);
