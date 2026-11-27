-- Catalogue of health conditions offered for patients and algorithms, so that every environment
-- starts with a usable list. Names are codes that a client translates for display.
INSERT INTO t_health_condition (id, name, is_deleted, version)
VALUES
    ('01234567-89ab-cdef-0123-456789abcdef', 'HYPERTENSION', false, 1),
    ('11234567-89ab-cdef-0123-456789abcdef', 'TYPE_2_DIABETES', false, 1),
    ('21234567-89ab-cdef-0123-456789abcdef', 'CORONARY_ARTERY_DISEASE', false, 1),
    ('31234567-89ab-cdef-0123-456789abcdef', 'ASTHMA', false, 1),
    ('41234567-89ab-cdef-0123-456789abcdef', 'CHRONIC_KIDNEY_DISEASE', false, 1),
    ('a7475581-4f5c-487a-b97d-c10b1f67e331', 'SLEEP_APNEA', false, 1)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;
