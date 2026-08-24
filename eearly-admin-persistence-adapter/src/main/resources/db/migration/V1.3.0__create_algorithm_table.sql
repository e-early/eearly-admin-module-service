-- ========================================================================
-- Algorithm table with all fields (category, status, billing, endpoints)
-- ========================================================================

CREATE TABLE t_algorithm (
    id                    UUID PRIMARY KEY,
    name                  VARCHAR(255)              NOT NULL,
    description           TEXT,
    algorithm_version     VARCHAR(50),
    category              e_algorithm_category_type,
    status                e_algorithm_status_type   NOT NULL DEFAULT 'DRAFT',
    container_name        VARCHAR(255),
    container_version     VARCHAR(128),
    service_endpoint      VARCHAR(512),
    runner_config         JSONB,
    run_endpoint          VARCHAR(255),
    cancel_endpoint       VARCHAR(255),
    health_check_endpoint VARCHAR(255),
    input_schema          JSONB,
    output_schema         JSONB,
    author                VARCHAR(255),
    pricing_model         e_pricing_model_type      NOT NULL DEFAULT 'FREE',
    cost_per_execution    DECIMAL(10, 4)            NOT NULL DEFAULT 0,
    currency              CHAR(3)                   NOT NULL DEFAULT 'EUR',
    billing_code          VARCHAR(50),
    license_type          VARCHAR(100),
    model_threshold       DOUBLE PRECISION,
    created_by            VARCHAR(256),
    created_at            TIMESTAMPTZ,
    updated_by            VARCHAR(256),
    updated_at            TIMESTAMPTZ,
    is_deleted            BOOLEAN NOT NULL DEFAULT FALSE,
    version               BIGINT  NOT NULL DEFAULT 1
);

CREATE TABLE t_patient_algorithm (
    patient_id   UUID NOT NULL,
    algorithm_id UUID NOT NULL,
    PRIMARY KEY (patient_id, algorithm_id),
    FOREIGN KEY (patient_id)   REFERENCES t_patient(id)   ON DELETE CASCADE,
    FOREIGN KEY (algorithm_id) REFERENCES t_algorithm(id) ON DELETE RESTRICT
);

CREATE TABLE t_measurement_algorithm (
    measurement_id UUID NOT NULL,
    algorithm_id   UUID NOT NULL,
    PRIMARY KEY (measurement_id, algorithm_id),
    FOREIGN KEY (measurement_id) REFERENCES t_measurement(id) ON DELETE CASCADE,
    FOREIGN KEY (algorithm_id)   REFERENCES t_algorithm(id)   ON DELETE RESTRICT
);
