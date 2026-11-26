-- Catalogue rows for the measurement types added to the enum in V1.7.0 and for the enum values
-- that had no catalogue row yet (heart rate variability, stress level, blood pressure components).
-- Kept separate from V1.7.0 because Postgres cannot use a newly added enum value in the
-- transaction that added it. Inserts are idempotent (fixed ids, ON CONFLICT DO NOTHING) and
-- additive: existing rows are never changed.
-- Body composition rows are not assigned to patients automatically; they are shown with body weight.
INSERT INTO t_measurement (id, name, type, chart_type, ehr_observation_id, is_deleted, version)
VALUES
    ('fedcba98-7654-3210-fedc-ba9876543217', 'HEART_RATE_VARIABILITY', 'HEART_RATE_VARIABILITY', 'LINE', 'openEHR-EHR-OBSERVATION.eearly_heart_rate_variability.v0', false, 1),
    ('fedcba98-7654-3210-fedc-ba9876543218', 'STRESS_LEVEL', 'STRESS_LEVEL', 'LINE', 'openEHR-EHR-OBSERVATION.eearly_stress_level.v0', false, 1),
    ('fedcba98-7654-3210-fedc-ba9876543219', 'GLUCOSE_VARIABILITY', 'GLUCOSE_VARIABILITY', 'LINE', 'openEHR-EHR-OBSERVATION.eearly_glucose_variability.v0', false, 1),
    ('fedcba98-7654-3210-fedc-ba9876543220', 'BLOOD_PRESSURE_SYSTOLIC', 'BLOOD_PRESSURE_SYSTOLIC', 'LINE', 'openEHR-EHR-OBSERVATION.blood_pressure.v2.at0004', false, 1),
    ('fedcba98-7654-3210-fedc-ba9876543221', 'BLOOD_PRESSURE_DIASTOLIC', 'BLOOD_PRESSURE_DIASTOLIC', 'LINE', 'openEHR-EHR-OBSERVATION.blood_pressure.v2.at0005', false, 1),
    ('9669cc35-57f1-46f4-9054-791dad1d1b62', 'BODY_MASS_INDEX', 'BODY_MASS_INDEX', 'LINE', 'openEHR-EHR-OBSERVATION.body_mass_index.v2', false, 1),
    ('db2b75d7-d307-4b6b-aef6-b20b8e3416ee', 'FAT_FREE_MASS', 'FAT_FREE_MASS', 'LINE', 'openEHR-EHR-OBSERVATION.eearly_body_composition.v0.at0005', false, 1),
    ('8e304769-9b9e-4406-8c42-44f4bdacafe7', 'FAT_MASS', 'FAT_MASS', 'LINE', 'openEHR-EHR-OBSERVATION.eearly_body_composition.v0.at0006', false, 1),
    ('137782db-4c46-455b-b8ca-9770248d688b', 'BODY_FAT_PERCENTAGE', 'BODY_FAT_PERCENTAGE', 'LINE', 'openEHR-EHR-OBSERVATION.eearly_body_composition.v0.at0007', false, 1),
    ('d6d93275-ed3e-4b50-be14-9c60158e4df1', 'BODY_WATER_PERCENTAGE', 'BODY_WATER_PERCENTAGE', 'LINE', 'openEHR-EHR-OBSERVATION.eearly_body_composition.v0.at0008', false, 1),
    ('51ae2b4f-67a7-476f-a5d8-ad9cf325b6bd', 'SKELETAL_MUSCLE_MASS', 'SKELETAL_MUSCLE_MASS', 'LINE', 'openEHR-EHR-OBSERVATION.eearly_body_composition.v0.at0009', false, 1),
    ('a181a7bd-4d84-43cf-8d07-740eee72d0c1', 'BASAL_METABOLIC_RATE', 'BASAL_METABOLIC_RATE', 'LINE', 'openEHR-EHR-OBSERVATION.eearly_body_composition.v0.at0010', false, 1),
    ('55e7de05-699b-42c8-9f59-a3072c12229f', 'FAT_FREE_MASS_PERCENTAGE', 'FAT_FREE_MASS_PERCENTAGE', 'LINE', 'openEHR-EHR-OBSERVATION.eearly_body_composition.v0.at0012', false, 1),
    ('d14375dc-c87f-45ad-96f2-422572088916', 'SKELETAL_MUSCLE_MASS_PERCENTAGE', 'SKELETAL_MUSCLE_MASS_PERCENTAGE', 'LINE', 'openEHR-EHR-OBSERVATION.eearly_body_composition.v0.at0013', false, 1),
    ('a5d100d6-0edd-4ac3-8f63-d3c523e4cb3b', 'BODY_WATER_MASS', 'BODY_WATER_MASS', 'LINE', 'openEHR-EHR-OBSERVATION.eearly_body_composition.v0.at0014', false, 1)
ON CONFLICT (id) DO NOTHING;
