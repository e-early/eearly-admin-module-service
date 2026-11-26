-- ========================================================================
-- Trigger type of analyses and algorithm executions
-- ========================================================================
-- e_trigger_type ('MANUAL', 'SCHEDULED') and t_algorithm_execution.trigger_type already exist
-- (V1.1.0, V1.5.0). Analyses gain the same column; existing rows were all created manually.

ALTER TABLE t_analysis
    ADD COLUMN trigger_type e_trigger_type NOT NULL DEFAULT 'MANUAL';
