-- ========================================================================
-- Algorithm execution table (tracks manual algorithm runs)
-- ========================================================================

CREATE TABLE t_algorithm_execution (
    id                 UUID PRIMARY KEY,
    id_algorithm       UUID                    NOT NULL,
    id_patient         UUID                    NOT NULL,
    id_analysis        UUID,
    execution_status   e_execution_status_type NOT NULL DEFAULT 'PENDING',
    trigger_type       e_trigger_type          NOT NULL,
    external_request_id VARCHAR(128),
    input_parameters   JSONB,
    result_data        JSONB,
    started_at         TIMESTAMPTZ,
    completed_at       TIMESTAMPTZ,
    error_message      TEXT,
    created_by         VARCHAR(256),
    created_at         TIMESTAMPTZ             NOT NULL DEFAULT now(),
    updated_by         VARCHAR(256),
    updated_at         TIMESTAMPTZ             NOT NULL DEFAULT now(),
    is_deleted         BOOLEAN                 NOT NULL DEFAULT FALSE,
    version            BIGINT                  NOT NULL DEFAULT 1,
    FOREIGN KEY (id_algorithm) REFERENCES t_algorithm(id)  ON DELETE RESTRICT,
    FOREIGN KEY (id_patient)   REFERENCES t_patient(id)    ON DELETE CASCADE,
    FOREIGN KEY (id_analysis)  REFERENCES t_analysis(id)   ON DELETE SET NULL
);

CREATE INDEX idx_algorithm_execution_patient   ON t_algorithm_execution(id_patient);
CREATE INDEX idx_algorithm_execution_algorithm ON t_algorithm_execution(id_algorithm);
