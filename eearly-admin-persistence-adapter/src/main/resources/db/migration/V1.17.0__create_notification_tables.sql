-- In-app notifications and the per-caretaker read state.
CREATE TYPE e_notification_type AS ENUM (
    'SYNC',
    'ERROR',
    'ANALYSIS',
    'MEASUREMENT',
    'ALGORITHM_EXECUTION',
    'THRESHOLD_ALERT'
    );

CREATE TABLE t_notification
(
    id           UUID PRIMARY KEY,
    type         e_notification_type      NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    message      VARCHAR(255)             NOT NULL,
    id_patient   UUID,
    id_caretaker UUID,
    id_reference UUID,
    CONSTRAINT fk_notification_patient
        FOREIGN KEY (id_patient) REFERENCES t_patient (id) ON DELETE RESTRICT,
    CONSTRAINT fk_notification_caretaker
        FOREIGN KEY (id_caretaker) REFERENCES t_caretaker (id) ON DELETE RESTRICT
);

CREATE TABLE t_notification_read
(
    id_caretaker    UUID,
    id_notification UUID REFERENCES t_notification,
    PRIMARY KEY (id_caretaker, id_notification)
);
