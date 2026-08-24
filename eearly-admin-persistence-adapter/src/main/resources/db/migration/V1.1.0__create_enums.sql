-- ========================================================================
-- Enum types for OSS admin analysis flow
-- ========================================================================

CREATE TYPE e_gender_type AS ENUM ('MALE', 'FEMALE', 'OTHER');

CREATE TYPE e_analysis_state_type AS ENUM (
    'ORDERED', 'IN_PROGRESS', 'WAITING_FOR_CONFIRMATION',
    'CONFIRMED', 'REJECTED', 'COMPLETED', 'ERROR'
);

CREATE TYPE e_feedback_type AS ENUM ('NONE', 'CONFIRMED', 'REJECTED');

CREATE TYPE e_measurement_type AS ENUM (
    'BLOOD_PRESSURE', 'BLOOD_PRESSURE_SYSTOLIC', 'BLOOD_PRESSURE_DIASTOLIC',
    'HEART_RATE', 'WEIGHT', 'HEIGHT', 'BODY_TEMPERATURE', 'OXYGEN_SATURATION',
    'BLOOD_GLUCOSE', 'DAILY_STEPS', 'BODY_FAT_PERCENTAGE', 'MUSCLE_MASS',
    'BODY_WATER_PERCENTAGE', 'SLEEP_DURATION', 'SLEEP_QUALITY', 'ECG',
    'BLOOD_PRESSURE_VARIABILITY', 'PEAK_FLOW', 'SKIN_TEMPERATURE',
    'RESPIRATORY_RATE', 'HEART_RATE_VARIABILITY', 'STRESS_LEVEL', 'BASIC_VISION_SCREENING'
);

CREATE TYPE e_measurement_chart_type AS ENUM (
    'LINE', 'BAR', 'SCATTER', 'AREA', 'GAUGE', 'HEATMAP', 'BOX', 'PIE', 'HISTOGRAM', 'RADAR'
);

CREATE TYPE e_algorithm_category_type AS ENUM (
    'CARDIOVASCULAR', 'RESPIRATORY', 'METABOLIC', 'NEUROLOGICAL', 'GENERAL', 'APNEA', 'GLUCOSE'
);

CREATE TYPE e_algorithm_status_type AS ENUM ('DRAFT', 'ACTIVE', 'INACTIVE', 'DEPRECATED');

CREATE TYPE e_pricing_model_type AS ENUM (
    'PER_EXECUTION', 'PER_PATIENT_MONTH', 'FLAT_RATE', 'FREE'
);

CREATE TYPE e_execution_status_type AS ENUM (
    'PENDING', 'SUBMITTED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'
);

CREATE TYPE e_trigger_type AS ENUM ('MANUAL', 'SCHEDULED');

CREATE TYPE e_detection_box_audit_status AS ENUM ('CREATED', 'EDITED', 'DELETED');
