-- Additional measurement types: glucose variability and body composition values.
-- Only additive; existing values are untouched. A separate migration must be used for any
-- catalogue rows that reference these values, because Postgres does not allow using a newly
-- added enum value in the transaction that added it.
ALTER TYPE e_measurement_type ADD VALUE IF NOT EXISTS 'GLUCOSE_VARIABILITY';
ALTER TYPE e_measurement_type ADD VALUE IF NOT EXISTS 'BODY_MASS_INDEX';
ALTER TYPE e_measurement_type ADD VALUE IF NOT EXISTS 'FAT_FREE_MASS';
ALTER TYPE e_measurement_type ADD VALUE IF NOT EXISTS 'FAT_MASS';
ALTER TYPE e_measurement_type ADD VALUE IF NOT EXISTS 'SKELETAL_MUSCLE_MASS';
ALTER TYPE e_measurement_type ADD VALUE IF NOT EXISTS 'BASAL_METABOLIC_RATE';
ALTER TYPE e_measurement_type ADD VALUE IF NOT EXISTS 'FAT_FREE_MASS_PERCENTAGE';
ALTER TYPE e_measurement_type ADD VALUE IF NOT EXISTS 'SKELETAL_MUSCLE_MASS_PERCENTAGE';
ALTER TYPE e_measurement_type ADD VALUE IF NOT EXISTS 'BODY_WATER_MASS';
