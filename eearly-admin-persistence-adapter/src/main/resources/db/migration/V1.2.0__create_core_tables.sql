-- ========================================================================
-- Core OSS tables: caretaker, patient, measurement
-- ========================================================================

CREATE TABLE t_caretaker (
    id           UUID PRIMARY KEY,
    keycloak_id  UUID,
    first_name   VARCHAR(50),
    last_name    VARCHAR(50),
    email        VARCHAR(50),
    language     VARCHAR(3),
    created_by   VARCHAR(256),
    created_at   TIMESTAMPTZ,
    updated_by   VARCHAR(256),
    updated_at   TIMESTAMPTZ,
    is_deleted   BOOLEAN NOT NULL DEFAULT FALSE,
    version      BIGINT  NOT NULL DEFAULT 1
);

CREATE TABLE t_patient (
    id                  UUID PRIMARY KEY,
    first_name          VARCHAR(50)    NOT NULL,
    last_name           VARCHAR(50)    NOT NULL,
    date_of_birth       DATE           NOT NULL,
    gender              e_gender_type  NOT NULL,
    health_insurance_id VARCHAR(20)    NOT NULL,
    street              VARCHAR(100)   NOT NULL,
    city                VARCHAR(50)    NOT NULL,
    state               VARCHAR(50)    NOT NULL,
    zip                 INTEGER,
    country             VARCHAR(50)    NOT NULL,
    email               VARCHAR(50)    NOT NULL,
    phone_number        VARCHAR(50)    NOT NULL,
    keycloak_id         UUID,
    timezone            VARCHAR(64)    NOT NULL DEFAULT 'Europe/Ljubljana',
    created_by          VARCHAR(256),
    created_at          TIMESTAMPTZ,
    updated_by          VARCHAR(256),
    updated_at          TIMESTAMPTZ,
    is_deleted          BOOLEAN NOT NULL DEFAULT FALSE,
    version             BIGINT  NOT NULL DEFAULT 1
);

CREATE TABLE t_measurement (
    id                 UUID PRIMARY KEY,
    name               VARCHAR(255)              NOT NULL,
    type               e_measurement_type        NOT NULL,
    chart_type         e_measurement_chart_type  NOT NULL,
    ehr_observation_id VARCHAR(256)              NOT NULL,
    created_by         VARCHAR(256),
    created_at         TIMESTAMPTZ,
    updated_by         VARCHAR(256),
    updated_at         TIMESTAMPTZ,
    is_deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    version            BIGINT  NOT NULL DEFAULT 1
);

INSERT INTO t_measurement (id, name, type, chart_type, ehr_observation_id, is_deleted, version) VALUES
    ('fedcba98-7654-3210-fedc-ba9876543210', 'BLOOD_PRESSURE',   'BLOOD_PRESSURE',   'LINE',  'openEHR-EHR-OBSERVATION.blood_pressure.v2',         false, 1),
    ('fedcba98-7654-3210-fedc-ba9876543211', 'BLOOD_GLUCOSE',    'BLOOD_GLUCOSE',    'LINE',  'openEHR-EHR-OBSERVATION.lab_test-blood_glucose.v1', false, 1),
    ('fedcba98-7654-3210-fedc-ba9876543212', 'HEART_RATE',       'HEART_RATE',       'LINE',  'openEHR-EHR-OBSERVATION.pulse.v2',                  false, 1),
    ('fedcba98-7654-3210-fedc-ba9876543213', 'BODY_TEMPERATURE', 'BODY_TEMPERATURE', 'GAUGE', 'openEHR-EHR-OBSERVATION.body_temperature.v2',       false, 1),
    ('fedcba98-7654-3210-fedc-ba9876543214', 'BODY_WEIGHT',      'WEIGHT',           'BAR',   'openEHR-EHR-OBSERVATION.body_weight.v2',            false, 1),
    ('fedcba98-7654-3210-fedc-ba9876543215', 'BODY_HEIGHT',      'HEIGHT',           'BAR',   'openEHR-EHR-OBSERVATION.height.v2',                 false, 1),
    ('fedcba98-7654-3210-fedc-ba9876543216', 'BLOOD_OXYGEN',     'OXYGEN_SATURATION','BAR',   'openEHR-EHR-OBSERVATION.pulse_oximetry.v1',         false, 1);

CREATE TABLE t_patient_caretaker (
    patient_id   UUID NOT NULL,
    caretaker_id UUID NOT NULL,
    PRIMARY KEY (patient_id, caretaker_id),
    FOREIGN KEY (patient_id)   REFERENCES t_patient(id)   ON DELETE CASCADE,
    FOREIGN KEY (caretaker_id) REFERENCES t_caretaker(id) ON DELETE RESTRICT
);

CREATE TABLE t_patient_measurement (
    patient_id     UUID NOT NULL,
    measurement_id UUID NOT NULL,
    PRIMARY KEY (patient_id, measurement_id),
    FOREIGN KEY (patient_id)     REFERENCES t_patient(id)     ON DELETE CASCADE,
    FOREIGN KEY (measurement_id) REFERENCES t_measurement(id) ON DELETE RESTRICT
);
