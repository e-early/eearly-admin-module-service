-- ========================================================================
-- Analysis detection box table (per-detection result records)
-- ========================================================================

CREATE TABLE t_analysis_detection_box (
    id                       UUID PRIMARY KEY,
    id_analysis              UUID                          NOT NULL,
    start_timestamp          TIMESTAMPTZ                   NOT NULL,
    end_timestamp            TIMESTAMPTZ                   NOT NULL,
    original_start_timestamp TIMESTAMPTZ                   NOT NULL,
    original_end_timestamp   TIMESTAMPTZ                   NOT NULL,
    probability              DOUBLE PRECISION,
    caretaker_feedback       e_feedback_type,
    source                   VARCHAR(64),
    audit_status             e_detection_box_audit_status  NOT NULL DEFAULT 'CREATED',
    created_by               VARCHAR(256),
    created_at               TIMESTAMPTZ                   NOT NULL DEFAULT now(),
    updated_by               VARCHAR(256),
    updated_at               TIMESTAMPTZ                   NOT NULL DEFAULT now(),
    is_deleted               BOOLEAN                       NOT NULL DEFAULT FALSE,
    version                  BIGINT                        NOT NULL DEFAULT 1,
    FOREIGN KEY (id_analysis) REFERENCES t_analysis(id) ON DELETE CASCADE
);

CREATE INDEX idx_analysis_detection_box_analysis   ON t_analysis_detection_box(id_analysis);
CREATE INDEX idx_analysis_detection_box_updated_at ON t_analysis_detection_box(updated_at);
CREATE INDEX idx_analysis_detection_box_not_deleted
    ON t_analysis_detection_box(id_analysis) WHERE is_deleted = FALSE;
