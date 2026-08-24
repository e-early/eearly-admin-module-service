-- ========================================================================
-- Analysis table (algorithm results awaiting caretaker confirmation)
-- ========================================================================

CREATE TABLE t_analysis (
    id                    UUID PRIMARY KEY,
    name                  VARCHAR(255)           NOT NULL,
    state                 e_analysis_state_type  NOT NULL,
    feedback              e_feedback_type        NOT NULL,
    detected              BOOLEAN                NOT NULL DEFAULT FALSE,
    id_patient            UUID                   NOT NULL,
    id_algorithm          UUID,
    input_parameters      JSONB,
    chart_display_options JSONB,
    created_by            VARCHAR(256),
    created_at            TIMESTAMPTZ,
    updated_by            VARCHAR(256),
    updated_at            TIMESTAMPTZ,
    is_deleted            BOOLEAN NOT NULL DEFAULT FALSE,
    version               BIGINT  NOT NULL DEFAULT 1,
    FOREIGN KEY (id_patient)   REFERENCES t_patient(id)   ON DELETE RESTRICT,
    FOREIGN KEY (id_algorithm) REFERENCES t_algorithm(id) ON DELETE RESTRICT
);

CREATE TABLE t_measurement_analysis (
    measurement_id UUID NOT NULL,
    analysis_id    UUID NOT NULL,
    PRIMARY KEY (measurement_id, analysis_id),
    FOREIGN KEY (measurement_id) REFERENCES t_measurement(id) ON DELETE CASCADE,
    FOREIGN KEY (analysis_id)    REFERENCES t_analysis(id)    ON DELETE CASCADE
);
