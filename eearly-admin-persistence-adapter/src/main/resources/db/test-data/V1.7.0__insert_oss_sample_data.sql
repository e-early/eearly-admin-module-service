-- ========================================================================
-- OSS sample data: demo caretaker, patient, and apnea detection algorithm
-- Loaded via spring.flyway.locations (development / acceptance only)
-- ========================================================================

INSERT INTO t_caretaker (
    id, keycloak_id, first_name, last_name, email, language, is_deleted, version
) VALUES (
    '987fcdeb-51d2-45e6-8b1a-23456789abcd',
    'a1b2c3d4-e5f6-7890-1234-567890abcdef',
    'Dr. Marko',
    'Kovačič',
    'marko.kovacic@example.com',
    'sl',
    false,
    1
);

INSERT INTO t_patient (
    id,
    first_name,
    last_name,
    date_of_birth,
    gender,
    health_insurance_id,
    street,
    city,
    state,
    zip,
    country,
    email,
    phone_number,
    keycloak_id,
    timezone,
    is_deleted,
    version
) VALUES (
    'f47ac10b-58cc-4372-a567-0e02b2c3d479',
    'John',
    'Smith',
    '1985-03-15',
    'MALE',
    '123456789',
    'Main Street 45',
    'Ljubljana',
    'Central Slovenia',
    1000,
    'Slovenia',
    'john.smith@example.com',
    '+386 1 234 5678',
    'f1e2d3c4-b5a6-7890-abcd-ef1234567890',
    'Europe/Ljubljana',
    false,
    1
);

INSERT INTO t_algorithm (
    id,
    name,
    description,
    algorithm_version,
    model_threshold,
    category,
    status,
    container_name,
    container_version,
    service_endpoint,
    run_endpoint,
    cancel_endpoint,
    health_check_endpoint,
    runner_config,
    input_schema,
    output_schema,
    author,
    pricing_model,
    cost_per_execution,
    currency,
    billing_code,
    license_type,
    is_deleted,
    version
) VALUES (
    '8598bfe7-ce24-4b33-a394-b73c37fb2208',
    'Apnea detection',
    'Detects sleep apnea from heart rate and blood oxygen saturation',
    '1',
    0.5,
    'APNEA',
    'ACTIVE',
    'apnea_model',
    '1',
    '${apnea_service_endpoint}',
    '/predict',
    NULL,
    NULL,
    '{"step_size": 15}'::jsonb,
    $apnea_input_schema$
{
  "type": "object",
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "required": ["analysisPayload"],
  "properties": {
    "analysisPayload": {
      "type": "array",
      "minItems": 1,
      "items": {
        "type": "object",
        "required": [
          "analysisId",
          "patientId",
          "caretakersId",
          "diagnosisType",
          "datasourceType",
          "modelName",
          "modelVersion",
          "modelParameters",
          "datasetObjects"
        ],
        "properties": {
          "analysisId": { "type": "string", "format": "uuid" },
          "patientId": { "type": "string", "format": "uuid" },
          "caretakersId": { "type": "string", "format": "uuid" },
          "callbackUrl": { "type": ["string", "null"], "format": "uri" },
          "diagnosisType": { "type": "string", "const": "APNEA" },
          "datasourceType": { "type": "string", "const": "AdminApp" },
          "modelName": { "type": "string" },
          "modelVersion": { "type": "string" },
          "modelParameters": { "type": "object" },
          "datasetObjects": {
            "type": "array",
            "minItems": 1,
            "items": {
              "type": "object",
              "required": [
                "dataPointsId",
                "datasetType",
                "type",
                "unit",
                "timestampLastEdit",
                "dataPoints"
              ],
              "properties": {
                "dataPointsId": { "type": "string", "format": "uuid" },
                "datasetType": { "type": "string", "const": "feature" },
                "type": { "type": "string" },
                "unit": { "type": "string" },
                "timestampLastEdit": { "type": "string", "format": "date-time" },
                "dataPoints": {
                  "type": "array",
                  "minItems": 1,
                  "items": {
                    "type": "object",
                    "required": ["timestamp", "batchId", "value"],
                    "properties": {
                      "timestamp": { "type": "string", "format": "date-time" },
                      "batchId": { "type": "string" },
                      "value": { "type": ["number", "null"] }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
$apnea_input_schema$::jsonb,
    '{"apneaDetected": true}'::jsonb,
    'OSS',
    'FREE',
    0.0000,
    'EUR',
    NULL,
    NULL,
    false,
    1
);

INSERT INTO t_patient_caretaker (patient_id, caretaker_id) VALUES
    ('f47ac10b-58cc-4372-a567-0e02b2c3d479', '987fcdeb-51d2-45e6-8b1a-23456789abcd');

INSERT INTO t_patient_measurement (patient_id, measurement_id) VALUES
    ('f47ac10b-58cc-4372-a567-0e02b2c3d479', 'fedcba98-7654-3210-fedc-ba9876543212'),
    ('f47ac10b-58cc-4372-a567-0e02b2c3d479', 'fedcba98-7654-3210-fedc-ba9876543216');

INSERT INTO t_patient_algorithm (patient_id, algorithm_id) VALUES
    ('f47ac10b-58cc-4372-a567-0e02b2c3d479', '8598bfe7-ce24-4b33-a394-b73c37fb2208');

INSERT INTO t_measurement_algorithm (measurement_id, algorithm_id) VALUES
    ('fedcba98-7654-3210-fedc-ba9876543212', '8598bfe7-ce24-4b33-a394-b73c37fb2208'),
    ('fedcba98-7654-3210-fedc-ba9876543216', '8598bfe7-ce24-4b33-a394-b73c37fb2208');
