-- Junction table for patient-healthCondition relationship (many-to-many)
CREATE TABLE t_patient_health_condition
(
    patient_id   UUID NOT NULL,
    condition_id UUID NOT NULL,
    PRIMARY KEY (patient_id, condition_id),
    FOREIGN KEY (patient_id) REFERENCES t_patient (id) ON DELETE CASCADE,
    FOREIGN KEY (condition_id) REFERENCES t_health_condition (id) ON DELETE RESTRICT
);

-- Junction table for health_condition-algorithm relationship (many-to-many)
CREATE TABLE t_health_condition_algorithm
(
    condition_id UUID NOT NULL,
    algorithm_id UUID NOT NULL,
    PRIMARY KEY (condition_id, algorithm_id),
    FOREIGN KEY (condition_id) REFERENCES t_health_condition (id) ON DELETE CASCADE,
    FOREIGN KEY (algorithm_id) REFERENCES t_algorithm (id) ON DELETE RESTRICT
);
