package si.result.project.eearly.port.algorithmexecution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.biometric.BasicBiometricRecord;
import si.result.project.eearly.model.biometric.BiometricRecord;
import si.result.project.eearly.model.biometric.BloodPressureBiometricRecord;
import si.result.project.eearly.model.biometric.command.BiometricFilterCommand;
import si.result.project.eearly.model.biometric.enumeration.BiometricDataAggregationLevel;
import si.result.project.eearly.model.biometric.enumeration.BiometricAnalysisCriticalityType;
import si.result.project.eearly.model.biometric.enumeration.BiometricMeasurementType;
import si.result.project.eearly.model.biometric.measurement.BasicValue;
import si.result.project.eearly.model.biometric.measurement.BloodPressureValue;
import si.result.project.eearly.model.biometric.measurement.MeasurementValue;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.analysis.AnalysisSelection;
import si.result.project.eearly.model.measurement.Measurement;
import si.result.project.eearly.model.measurement.MeasurementChartType;
import si.result.project.eearly.model.measurement.MeasurementType;
import si.result.project.eearly.model.patient.Patient;
import si.result.project.eearly.port.biometric.BiometricRecordService;
import si.result.spring.boot.bricks.exception.DomainException;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlgorithmDataPreparationServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final JsonSchemaFactory JSON_SCHEMA_FACTORY =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    @Mock
    private BiometricRecordService biometricRecordService;

    @InjectMocks
    private AlgorithmDataPreparationService algorithmDataPreparationService;

    @Test
    void shouldPrepareContainerInputWithMeasurementRecords() throws Exception {
        final Measurement bloodPressure = measurement(
                "BLOOD_PRESSURE",
                MeasurementType.BLOOD_PRESSURE,
                "openEHR-EHR-OBSERVATION.blood_pressure.v2"
        );
        final Measurement bloodOxygen = measurement(
                "BLOOD_OXYGEN",
                MeasurementType.OXYGEN_SATURATION,
                "openEHR-EHR-OBSERVATION.pulse_oximetry.v1"
        );
        final Patient patient = patient(UUID.randomUUID(), UUID.randomUUID());
        final Algorithm algorithm = algorithm("""
                {
                  "type": "object",
                  "properties": {
                    "dateRange": { "type": "object" },
                    "measurements": { "type": "array" }
                  }
                }
                """, List.of(bloodPressure, bloodOxygen));
        final UUID analysisId = UUID.randomUUID();
        final String algorithmConfigJson = """
                {
                  "sensitivityLevel": "HIGH",
                  "notifyOnCompletion": true
                }
                """;

        when(biometricRecordService.findAll(anyBiometricFilterCommand()))
                .thenReturn(records(
                        BloodPressureBiometricRecord.builder()
                                .timestamp(ZonedDateTime.of(2026, 3, 4, 8, 30, 0, 0, ZoneOffset.UTC))
                                .value(new BloodPressureValue(87.0, 135.0))
                                .unit("mmHg")
                                .status(BiometricAnalysisCriticalityType.MEDIUM)
                                .build()
                ))
                .thenReturn(records(
                        BasicBiometricRecord.builder()
                                .timestamp(ZonedDateTime.of(2026, 3, 4, 8, 30, 0, 0, ZoneOffset.UTC))
                                .value(new BasicValue(97.5))
                                .unit("%")
                                .status(BiometricAnalysisCriticalityType.NORMAL)
                                .build()
                ));

        final String result = algorithmDataPreparationService.prepareInputData(
                patient,
                algorithm,
                "2025-12-01T00:00:00Z",
                "2026-03-05T23:59:59Z",
                analysisId,
                null,
                algorithmConfigJson,
                null
        );

        final JsonNode json = OBJECT_MAPPER.readTree(result);
        final var commandCaptor = biometricFilterCommandCaptor();

        assertEquals("2025-12-01T00:00:00Z", json.path("dateRange").path("from").asText());
        assertEquals("2026-03-05T23:59:59Z", json.path("dateRange").path("to").asText());
        assertTrue(json.path("analysisId").isMissingNode());
        assertTrue(json.path("algorithmConfig").isMissingNode());
        assertEquals(2, json.path("measurements").size());
        assertEquals("BLOOD_PRESSURE", json.path("measurements").get(0).path("type").asText());
        assertEquals(135.0, json.path("measurements").get(0).path("records").get(0).path("value").path("highValue").asDouble());
        assertEquals("BLOOD_OXYGEN", json.path("measurements").get(1).path("type").asText());
        assertEquals(97.5, json.path("measurements").get(1).path("records").get(0).path("value").path("value").asDouble());

        verify(biometricRecordService, times(2)).findAll(commandCaptor.capture());
        final List<BiometricFilterCommand<?>> capturedCommands = commandCaptor.getAllValues();

        assertEquals(patient.getKeycloakId(), capturedCommands.getFirst().ehrId());
        assertEquals(BiometricMeasurementType.BLOOD_PRESSURE, capturedCommands.getFirst().measurementType());
        assertEquals("BLOOD_PRESSURE", capturedCommands.getFirst().measurementName());
        assertEquals("openEHR-EHR-OBSERVATION.blood_pressure.v2", capturedCommands.getFirst().ehrObservationId());
        assertEquals("2025-12-01T00:00:00Z", capturedCommands.getFirst().startDateTime());
        assertEquals("2026-03-05T23:59:59Z", capturedCommands.getFirst().endDateTime());
        assertEquals(BiometricDataAggregationLevel.NONE, capturedCommands.getFirst().aggregationLevel());

        assertEquals(patient.getKeycloakId(), capturedCommands.get(1).ehrId());
        assertEquals(BiometricMeasurementType.BLOOD_OXYGEN, capturedCommands.get(1).measurementType());
        assertEquals("BLOOD_OXYGEN", capturedCommands.get(1).measurementName());
        assertEquals("openEHR-EHR-OBSERVATION.pulse_oximetry.v1", capturedCommands.get(1).ehrObservationId());
        assertEquals("2025-12-01T00:00:00Z", capturedCommands.get(1).startDateTime());
        assertEquals("2026-03-05T23:59:59Z", capturedCommands.get(1).endDateTime());
        assertEquals(BiometricDataAggregationLevel.NONE, capturedCommands.get(1).aggregationLevel());
    }

    @Test
    void shouldHandlePartialAndMissingMeasurementDataGracefully() throws Exception {
        final Measurement heartRate = measurement(
                "HEART_RATE",
                MeasurementType.HEART_RATE,
                "openEHR-EHR-OBSERVATION.pulse.v2"
        );
        final Measurement bloodOxygen = measurement(
                "BLOOD_OXYGEN",
                MeasurementType.OXYGEN_SATURATION,
                "openEHR-EHR-OBSERVATION.pulse_oximetry.v1"
        );
        final Patient patient = patient(UUID.randomUUID(), null);
        final Algorithm algorithm = algorithm("""
                {
                  "type": "object",
                  "properties": {
                    "measurements": { "type": "array" }
                  }
                }
                """, List.of(heartRate, bloodOxygen));

        when(biometricRecordService.findAll(anyBiometricFilterCommand()))
                .thenReturn(records(
                        BasicBiometricRecord.builder()
                                .timestamp(ZonedDateTime.of(2026, 3, 4, 8, 30, 0, 0, ZoneOffset.UTC))
                                .value(new BasicValue(78.0))
                                .unit("bpm")
                                .status(BiometricAnalysisCriticalityType.NORMAL)
                                .build()
                ))
                .thenReturn(emptyRecords());

        final String result = algorithmDataPreparationService.prepareInputData(patient, algorithm);
        final JsonNode json = OBJECT_MAPPER.readTree(result);

        assertTrue(json.path("analysisId").isMissingNode());
        assertEquals(2, json.path("measurements").size());
        assertEquals(1, json.path("measurements").get(0).path("records").size());
        assertEquals(0, json.path("measurements").get(1).path("records").size());
    }

    @Test
    void shouldFilterMeasurementsWhenSelectionIdsAreProvided() throws Exception {
        final UUID heartRateId = UUID.randomUUID();
        final UUID bloodOxygenId = UUID.randomUUID();
        final Measurement heartRate = measurement(
                heartRateId,
                "HEART_RATE",
                MeasurementType.HEART_RATE,
                "openEHR-EHR-OBSERVATION.pulse.v2"
        );
        final Measurement bloodOxygen = measurement(
                bloodOxygenId,
                "BLOOD_OXYGEN",
                MeasurementType.OXYGEN_SATURATION,
                "openEHR-EHR-OBSERVATION.pulse_oximetry.v1"
        );
        final Patient patient = patient(UUID.randomUUID(), null);
        final Algorithm algorithm = algorithm("""
                {
                  "type": "object",
                  "properties": {
                    "measurements": { "type": "array" }
                  }
                }
                """, List.of(heartRate, bloodOxygen));

        when(biometricRecordService.findAll(anyBiometricFilterCommand()))
                .thenReturn(records(
                        BasicBiometricRecord.builder()
                                .timestamp(ZonedDateTime.of(2026, 3, 4, 8, 30, 0, 0, ZoneOffset.UTC))
                                .value(new BasicValue(78.0))
                                .unit("bpm")
                                .status(BiometricAnalysisCriticalityType.NORMAL)
                                .build()
                ));

        final String result = algorithmDataPreparationService.prepareInputData(
                patient,
                algorithm,
                null,
                null,
                null,
                List.of(heartRateId),
                null,
                null
        );

        final JsonNode json = OBJECT_MAPPER.readTree(result);
        final JsonNode measurements = json.path("measurements");
        final var commandCaptor = biometricFilterCommandCaptor();

        assertEquals(1, measurements.size());
        assertEquals("HEART_RATE", measurements.get(0).path("type").asText());

        verify(biometricRecordService, times(1)).findAll(commandCaptor.capture());
        assertEquals("HEART_RATE", commandCaptor.getValue().measurementName());
    }

    @Test
    void shouldMergeRecordsFromMultipleAnalysisSelectionsForSameMeasurement() throws Exception {
        final UUID heartRateId = UUID.randomUUID();
        final Measurement heartRate = measurement(
                heartRateId,
                "HEART_RATE",
                MeasurementType.HEART_RATE,
                "openEHR-EHR-OBSERVATION.pulse.v2"
        );
        final Patient patient = patient(UUID.randomUUID(), null);
        final Algorithm algorithm = algorithm("""
                {
                  "type": "object",
                  "properties": {
                    "measurements": { "type": "array" }
                  }
                }
                """, List.of(heartRate));

        when(biometricRecordService.findAll(anyBiometricFilterCommand()))
                .thenReturn(records(
                        basicRecord("2026-02-12T08:00:00+01:00", "batch-a", 72.0, "/min")
                ))
                .thenReturn(records(
                        basicRecord("2026-02-12T08:05:00+01:00", "batch-b", 75.0, "/min")
                ));

        final String result = algorithmDataPreparationService.prepareInputData(
                patient,
                algorithm,
                List.of(
                        new AnalysisSelection(
                                OffsetDateTime.parse("2026-02-12T08:00:00+01:00"),
                                OffsetDateTime.parse("2026-02-12T08:01:00+01:00"),
                                List.of(heartRateId)
                        ),
                        new AnalysisSelection(
                                OffsetDateTime.parse("2026-02-12T08:05:00+01:00"),
                                OffsetDateTime.parse("2026-02-12T08:06:00+01:00"),
                                List.of(heartRateId)
                        )
                ),
                null,
                null,
                null
        );

        final JsonNode json = OBJECT_MAPPER.readTree(result);
        final var commandCaptor = biometricFilterCommandCaptor();

        assertEquals(1, json.path("measurements").size());
        assertEquals(2, json.path("measurements").get(0).path("records").size());

        verify(biometricRecordService, times(2)).findAll(commandCaptor.capture());
        final List<BiometricFilterCommand<?>> capturedCommands = commandCaptor.getAllValues();

        assertEquals("2026-02-12T08:00:00+01:00", capturedCommands.get(0).startDateTime());
        assertEquals("2026-02-12T08:01:00+01:00", capturedCommands.get(0).endDateTime());
        assertEquals("2026-02-12T08:05:00+01:00", capturedCommands.get(1).startDateTime());
        assertEquals("2026-02-12T08:06:00+01:00", capturedCommands.get(1).endDateTime());
    }

    @Test
    void shouldPrepareExecutionRequestPayloadWhenSchemaUsesDataPoints() throws Exception {
        final UUID analysisId = UUID.randomUUID();
        final UUID patientId = UUID.randomUUID();
        final UUID algorithmId = UUID.randomUUID();
        final UUID heartRateId = UUID.randomUUID();
        final UUID apneaLabelsId = UUID.randomUUID();
        final String callbackUrl = "https://admin.eearly.example.com/api/v1/analyses/" + analysisId + "/result";

        final Measurement heartRate = measurement(
                heartRateId,
                "Heart rate",
                MeasurementType.HEART_RATE,
                "openEHR-EHR-OBSERVATION.pulse.v2"
        );
        final Measurement apneaLabels = measurement(
                apneaLabelsId,
                "Caretakers labeling apnea",
                MeasurementType.HEART_RATE,
                "openEHR-EHR-OBSERVATION.apnea_labels.v1"
        );
        final Patient patient = patient(patientId, null);
        final Algorithm algorithm = algorithm("""
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "required": ["correlationId", "analysisId", "patientId", "algorithm", "measurements", "callbackUrl"],
                  "properties": {
                    "correlationId": { "type": "string", "format": "uuid" },
                    "analysisId": { "type": "string", "format": "uuid" },
                    "patientId": { "type": "string", "format": "uuid" },
                    "algorithm": {
                      "type": "object",
                      "required": ["algorithmId", "name"],
                      "properties": {
                        "algorithmId": { "type": "string", "format": "uuid" },
                        "name": { "type": "string" }
                      }
                    },
                    "measurements": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "required": ["measurementId", "name", "type", "ehrObservationId", "unit", "dataPoints"],
                        "properties": {
                          "measurementId": { "type": "string", "format": "uuid" },
                          "name": { "type": "string" },
                          "type": { "type": "string" },
                          "ehrObservationId": { "type": "string" },
                          "unit": { "type": "string" },
                          "dataPoints": {
                            "type": "array",
                            "items": {
                              "type": "object",
                              "required": ["timestamp"],
                              "properties": {
                                "timestamp": { "type": "string", "format": "date-time" },
                                "batchId": { "type": "string" },
                                "value": { "type": ["number", "null"] },
                                "highValue": { "type": "number" },
                                "lowValue": { "type": "number" }
                              }
                            }
                          }
                        }
                      }
                    },
                    "callbackUrl": { "type": "string", "format": "uri" }
                  }
                }
                """, List.of(heartRate, apneaLabels), algorithmId, "Apnea detection");

        when(biometricRecordService.findAll(anyBiometricFilterCommand()))
                .thenReturn(records(
                        BasicBiometricRecord.builder()
                                .timestamp(ZonedDateTime.of(2026, 2, 12, 7, 0, 0, 0, ZoneOffset.UTC))
                                .batchId("batch-heart-rate")
                                .value(new BasicValue(72.0))
                                .unit("/min")
                                .status(BiometricAnalysisCriticalityType.NORMAL)
                                .build()
                ))
                .thenReturn(records(
                        BasicBiometricRecord.builder()
                                .timestamp(ZonedDateTime.of(2026, 2, 12, 7, 0, 1, 0, ZoneOffset.UTC))
                                .batchId("batch-labels")
                                .value(new BasicValue(null))
                                .unit("binary")
                                .status(BiometricAnalysisCriticalityType.NORMAL)
                                .build()
                ));

        final String result = algorithmDataPreparationService.prepareInputData(
                patient,
                algorithm,
                "2026-02-12T07:00:00Z",
                "2026-02-12T07:01:20Z",
                analysisId,
                List.of(heartRateId, apneaLabelsId),
                null,
                callbackUrl
        );

        final JsonNode json = OBJECT_MAPPER.readTree(result);

        assertEquals(analysisId.toString(), json.path("analysisId").asText());
        assertEquals(patientId.toString(), json.path("patientId").asText());
        assertEquals(algorithmId.toString(), json.path("algorithm").path("algorithmId").asText());
        assertEquals("Apnea detection", json.path("algorithm").path("name").asText());
        assertEquals(callbackUrl, json.path("callbackUrl").asText());
        assertEquals(2, json.path("measurements").size());
        assertTrue(json.path("correlationId").isTextual());

        assertEquals(heartRateId.toString(), json.path("measurements").get(0).path("measurementId").asText());
        assertEquals("Heart rate", json.path("measurements").get(0).path("name").asText());
        assertEquals("HEART_RATE", json.path("measurements").get(0).path("type").asText());
        assertEquals("/min", json.path("measurements").get(0).path("unit").asText());
        assertEquals("batch-heart-rate",
                json.path("measurements").get(0).path("dataPoints").get(0).path("batchId").asText());
        assertEquals(72.0,
                json.path("measurements").get(0).path("dataPoints").get(0).path("value").asDouble());

        assertEquals(apneaLabelsId.toString(), json.path("measurements").get(1).path("measurementId").asText());
        assertEquals("HEART_RATE", json.path("measurements").get(1).path("type").asText());
        assertTrue(json.path("measurements").get(1).path("dataPoints").get(0).path("value").isNull());
    }

    @Test
    void shouldWriteAlgorithmNameIntoAlgorithmNamePropertyWhenSchemaUsesThatField() throws Exception {
        final UUID algorithmId = UUID.randomUUID();
        final Patient patient = patient(UUID.randomUUID(), null);
        final Algorithm algorithm = algorithm("""
                {
                  "type": "object",
                  "properties": {
                    "algorithm": {
                      "type": "object",
                      "properties": {
                        "algorithmId": { "type": "string", "format": "uuid" },
                        "algorithmName": { "type": "string" }
                      }
                    }
                  }
                }
                """, List.of(), algorithmId, "Apnea detection");

        final String result = algorithmDataPreparationService.prepareInputData(patient, algorithm);
        final JsonNode json = OBJECT_MAPPER.readTree(result);

        assertEquals(algorithmId.toString(), json.path("algorithm").path("algorithmId").asText());
        assertEquals("Apnea detection", json.path("algorithm").path("algorithmName").asText());
        assertTrue(json.path("algorithm").path("name").isMissingNode());
    }

    @Test
    void shouldWriteModelNameAndModelVersionIntoAlgorithmFromSchemaConstProperties() throws Exception {
        final Patient patient = patient(UUID.randomUUID(), null);
        final Algorithm algorithm = algorithm("""
                {
                  "type": "object",
                  "properties": {
                    "algorithm": {
                      "type": "object",
                      "properties": {
                        "modelName": {
                          "type": "string",
                          "const": "apneaModel"
                        },
                        "modelVersion": {
                          "type": "string",
                          "const": "1"
                        }
                      }
                    },
                    "measurements": { "type": "array" }
                  }
                }
                """, List.of());

        final JsonNode json = OBJECT_MAPPER.readTree(algorithmDataPreparationService.prepareInputData(patient, algorithm));

        assertEquals("apneaModel", json.path("algorithm").path("modelName").asText());
        assertEquals("1", json.path("algorithm").path("modelVersion").asText());
    }

    @Test
    void shouldWriteModelNameAndModelVersionIntoAlgorithmFromLiteralSchemaProperties() throws Exception {
        final Patient patient = patient(UUID.randomUUID(), null);
        final Algorithm algorithm = algorithm("""
                {
                  "type": "object",
                  "properties": {
                    "algorithm": {
                      "type": "object",
                      "properties": {
                        "modelName": "apneaModel",
                        "modelVersion": 1
                      }
                    },
                    "measurements": { "type": "array" }
                  }
                }
                """, List.of());

        final JsonNode json = OBJECT_MAPPER.readTree(algorithmDataPreparationService.prepareInputData(patient, algorithm));

        assertEquals("apneaModel", json.path("algorithm").path("modelName").asText());
        assertEquals(1, json.path("algorithm").path("modelVersion").asInt());
    }

    @Test
    void shouldValidateRecordBasedPayloadAgainstProvidedSchema() throws Exception {
        final UUID analysisId = UUID.randomUUID();
        final UUID patientId = UUID.randomUUID();
        final Measurement heartRate = measurement(
                "HEART_RATE",
                MeasurementType.HEART_RATE,
                "openEHR-EHR-OBSERVATION.pulse.v2"
        );
        final Measurement oxygenSaturation = measurement(
                "OXYGEN_SATURATION",
                MeasurementType.OXYGEN_SATURATION,
                "openEHR-EHR-OBSERVATION.pulse_oximetry.v1"
        );
        final Measurement apneaLabels = measurement(
                "APNEA_LABELS",
                MeasurementType.HEART_RATE,
                "openEHR-EHR-OBSERVATION.apnea_labels.v1"
        );
        final Patient patient = patient(patientId, null);
        final String schema = """
                {
                  "type": "object",
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "required": ["analysisId", "dateRange", "measurements"],
                  "properties": {
                    "dateRange": {
                      "type": "object",
                      "required": ["from", "to"],
                      "properties": {
                        "to": { "type": "string", "format": "date-time" },
                        "from": { "type": "string", "format": "date-time" }
                      }
                    },
                    "analysisId": { "type": "string", "format": "uuid" },
                    "measurements": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "required": ["type", "records"],
                        "properties": {
                          "type": { "type": "string" },
                          "records": {
                            "type": "array",
                            "items": {
                              "type": "object",
                              "required": ["timestamp", "value", "unit"],
                              "properties": {
                                "unit": { "type": "string" },
                                "value": { "type": "object" },
                                "status": { "type": "string" },
                                "timestamp": { "type": "string", "format": "date-time" }
                              }
                            }
                          }
                        }
                      }
                    },
                    "algorithmConfig": { "type": "object" }
                  }
                }
                """;
        final Algorithm algorithm = algorithm(schema, List.of(heartRate, oxygenSaturation, apneaLabels));

        when(biometricRecordService.findAll(anyBiometricFilterCommand()))
                .thenReturn(records(
                        basicRecord("2026-02-12T08:00:00+01:00", "8b1e44a2-7d31-4fb3-a912-63208f821683", 72.0, "/min"),
                        basicRecord("2026-02-12T08:00:01+01:00", "8b1e44a2-7d31-4fb3-a912-63208f821683", 73.0, "/min"),
                        basicRecord("2026-02-12T08:00:01+01:00", "8b1e44a2-7d31-4fb3-a912-63208f821683", 74.0, "/min"),
                        basicRecord("2026-02-12T08:00:07+01:00", "8b1e44a2-7d31-4fb3-a912-63208f821683", 79.0, "/min")
                ))
                .thenReturn(records(
                        basicRecord("2026-02-12T08:00:00+01:00", "8b1e44a2-7d31-4fb3-a912-63208f821685", 97.0, "%"),
                        basicRecord("2026-02-12T08:00:01+01:00", "8b1e44a2-7d31-4fb3-a912-63208f821685", 97.0, "%"),
                        basicRecord("2026-02-12T08:00:02+01:00", "8b1e44a2-7d31-4fb3-a912-63208f821685", 96.0, "%")
                ))
                .thenReturn(records(
                        basicRecord("2026-02-12T08:00:00+01:00", "8b1e44a2-7d31-4fb3-a912-63208f821689", null, "binary"),
                        basicRecord("2026-02-12T08:00:01+01:00", "8b1e44a2-7d31-4fb3-a912-63208f821689", null, "binary")
                ));

        final String result = algorithmDataPreparationService.prepareInputData(
                patient,
                algorithm,
                "2026-02-12T07:00:00Z",
                "2026-02-12T07:01:20Z",
                analysisId,
                null,
                "{\"windowSize\": 30}",
                null
        );

        final JsonNode json = OBJECT_MAPPER.readTree(result);

        assertValidAgainstSchema(schema, json);
        assertEquals(analysisId.toString(), json.path("analysisId").asText());
        assertEquals("2026-02-12T07:00:00Z", json.path("dateRange").path("from").asText());
        assertEquals("2026-02-12T07:01:20Z", json.path("dateRange").path("to").asText());
        assertEquals(3, json.path("measurements").size());
        assertEquals("HEART_RATE", json.path("measurements").get(0).path("type").asText());
        assertEquals(4, json.path("measurements").get(0).path("records").size());
        assertEquals("2026-02-12T07:00:01Z",
                json.path("measurements").get(0).path("records").get(1).path("timestamp").asText());
        assertEquals("2026-02-12T07:00:01Z",
                json.path("measurements").get(0).path("records").get(2).path("timestamp").asText());
        assertEquals(74.0,
                json.path("measurements").get(0).path("records").get(2).path("value").path("value").asDouble());
        assertEquals("APNEA_LABELS", json.path("measurements").get(2).path("type").asText());
        assertTrue(json.path("measurements").get(2).path("records").get(0).path("value").isObject());
    }

    @Test
    void shouldSupportMixedMeasurementPropertiesWhenSchemaUsesRecords() throws Exception {
        final UUID measurementId = UUID.randomUUID();
        final Measurement heartRate = measurement(
                measurementId,
                "Heart rate",
                MeasurementType.HEART_RATE,
                "openEHR-EHR-OBSERVATION.pulse.v2"
        );
        final Patient patient = patient(UUID.randomUUID(), null);
        final String schema = """
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "required": ["measurements"],
                  "properties": {
                    "measurements": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "required": ["measurementId", "name", "type", "ehrObservationId", "unit", "records"],
                        "properties": {
                          "measurementId": { "type": "string", "format": "uuid" },
                          "name": { "type": "string" },
                          "type": { "type": "string" },
                          "ehrObservationId": { "type": "string" },
                          "unit": { "type": "string" },
                          "records": {
                            "type": "array",
                            "items": {
                              "type": "object",
                              "required": ["timestamp", "value", "unit"],
                              "properties": {
                                "timestamp": { "type": "string", "format": "date-time" },
                                "value": { "type": "object" },
                                "unit": { "type": "string" }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """;
        final Algorithm algorithm = algorithm(schema, List.of(heartRate));

        when(biometricRecordService.findAll(anyBiometricFilterCommand()))
                .thenReturn(records(
                        basicRecord("2026-02-12T08:00:00+01:00", "batch-heart-rate", 72.0, "/min")
                ));

        final JsonNode json = OBJECT_MAPPER.readTree(algorithmDataPreparationService.prepareInputData(patient, algorithm));

        assertValidAgainstSchema(schema, json);
        assertEquals(measurementId.toString(), json.path("measurements").get(0).path("measurementId").asText());
        assertEquals("Heart rate", json.path("measurements").get(0).path("name").asText());
        assertEquals("HEART_RATE", json.path("measurements").get(0).path("type").asText());
        assertEquals("openEHR-EHR-OBSERVATION.pulse.v2",
                json.path("measurements").get(0).path("ehrObservationId").asText());
        assertEquals("/min", json.path("measurements").get(0).path("unit").asText());
        assertEquals(72.0,
                json.path("measurements").get(0).path("records").get(0).path("value").path("value").asDouble());
    }

    @Test
    void shouldValidateExecutionRequestPayloadAgainstProvidedApneaSchema() throws Exception {
        final UUID analysisId = UUID.fromString("e4f5a6b7-1234-5678-9abc-def012345678");
        final UUID patientId = UUID.fromString("d3c2b1a0-9876-5432-1fed-cba987654321");
        final UUID algorithmId = UUID.fromString("f0e1d2c3-b4a5-6789-0abc-def123456789");
        final UUID heartRateId = UUID.fromString("22222222-aaaa-bbbb-cccc-dddddddddddd");
        final UUID oxygenSaturationId = UUID.fromString("33333333-aaaa-bbbb-cccc-dddddddddddd");
        final UUID apneaLabelsId = UUID.fromString("44444444-aaaa-bbbb-cccc-dddddddddddd");
        final String callbackUrl = "https://admin.eearly.example.com/api/v1/analyses/" + analysisId + "/result";

        final Measurement heartRate = measurement(
                heartRateId,
                "Heart rate",
                MeasurementType.HEART_RATE,
                "openEHR-EHR-OBSERVATION.pulse.v2"
        );
        final Measurement oxygenSaturation = measurement(
                oxygenSaturationId,
                "Blood oxygen saturation",
                MeasurementType.OXYGEN_SATURATION,
                "openEHR-EHR-OBSERVATION.pulse_oximetry.v1"
        );
        final Measurement apneaLabels = measurement(
                apneaLabelsId,
                "Caretakers labeling apnea",
                MeasurementType.HEART_RATE,
                "openEHR-EHR-OBSERVATION.pulse_oximetry.v1"
        );
        final Patient patient = patient(patientId, null);
        final String schema = """
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "required": ["correlationId", "analysisId", "patientId", "algorithm", "measurements", "callbackUrl"],
                  "properties": {
                    "correlationId": { "type": "string", "format": "uuid" },
                    "analysisId": { "type": "string", "format": "uuid" },
                    "patientId": { "type": "string", "format": "uuid" },
                    "algorithm": {
                      "type": "object",
                      "required": ["algorithmId", "name"],
                      "properties": {
                        "algorithmId": { "type": "string", "format": "uuid" },
                        "name": { "type": "string" }
                      }
                    },
                    "measurements": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "required": ["measurementId", "name", "type", "ehrObservationId", "unit", "dataPoints"],
                        "properties": {
                          "measurementId": { "type": "string", "format": "uuid" },
                          "name": { "type": "string" },
                          "type": { "type": "string" },
                          "ehrObservationId": { "type": "string" },
                          "unit": { "type": "string" },
                          "dataPoints": {
                            "type": "array",
                            "items": {
                              "type": "object",
                              "required": ["timestamp", "value"],
                              "properties": {
                                "timestamp": { "type": "string", "format": "date-time" },
                                "batchId": { "type": "string" },
                                "value": { "type": ["number", "null"] }
                              }
                            }
                          }
                        }
                      }
                    },
                    "callbackUrl": { "type": "string", "format": "uri" }
                  }
                }
                """;
        final Algorithm algorithm = algorithm(schema,
                List.of(heartRate, oxygenSaturation, apneaLabels),
                algorithmId,
                "Apnea detection");

        when(biometricRecordService.findAll(anyBiometricFilterCommand()))
                .thenReturn(records(
                        basicRecord("2026-02-12T08:00:00+01:00", "8b1e44a2-7d31-4fb3-a912-63208f821683", 72.0, "/min"),
                        basicRecord("2026-02-12T08:00:01+01:00", "8b1e44a2-7d31-4fb3-a912-63208f821683", 73.0, "/min"),
                        basicRecord("2026-02-12T08:00:01+01:00", "8b1e44a2-7d31-4fb3-a912-63208f821683", 74.0, "/min"),
                        basicRecord("2026-02-12T08:00:02+01:00", "8b1e44a2-7d31-4fb3-a912-63208f821683", 74.0, "/min"),
                        basicRecord("2026-02-12T08:00:03+01:00", "8b1e44a2-7d31-4fb3-a912-63208f821683", 75.0, "/min"),
                        basicRecord("2026-02-12T08:00:07+01:00", "8b1e44a2-7d31-4fb3-a912-63208f821683", 79.0, "/min")
                ))
                .thenReturn(records(
                        basicRecord("2026-02-12T08:00:00+01:00", "8b1e44a2-7d31-4fb3-a912-63208f821685", 97.0, "%"),
                        basicRecord("2026-02-12T08:00:01+01:00", "8b1e44a2-7d31-4fb3-a912-63208f821685", 97.0, "%"),
                        basicRecord("2026-02-12T08:00:02+01:00", "8b1e44a2-7d31-4fb3-a912-63208f821685", 96.0, "%"),
                        basicRecord("2026-02-12T08:00:03+01:00", "8b1e44a2-7d31-4fb3-a912-63208f821685", 96.0, "%")
                ))
                .thenReturn(records(
                        basicRecord("2026-02-12T08:00:00+01:00", "8b1e44a2-7d31-4fb3-a912-63208f821689", null, "binary"),
                        basicRecord("2026-02-12T08:00:01+01:00", "8b1e44a2-7d31-4fb3-a912-63208f821689", null, "binary"),
                        basicRecord("2026-02-12T08:01:19+01:00", "8b1e44a2-7d31-4fb3-a912-63208f821689", null, "binary")
                ));

        final String result = algorithmDataPreparationService.prepareInputData(
                patient,
                algorithm,
                null,
                null,
                analysisId,
                List.of(heartRateId, oxygenSaturationId, apneaLabelsId),
                null,
                callbackUrl
        );

        final JsonNode json = OBJECT_MAPPER.readTree(result);

        assertValidAgainstSchema(schema, json);
        assertEquals(analysisId.toString(), json.path("analysisId").asText());
        assertEquals(patientId.toString(), json.path("patientId").asText());
        assertEquals(algorithmId.toString(), json.path("algorithm").path("algorithmId").asText());
        assertEquals("Apnea detection", json.path("algorithm").path("name").asText());
        assertEquals(callbackUrl, json.path("callbackUrl").asText());
        assertEquals(3, json.path("measurements").size());
        assertTrue(json.path("correlationId").isTextual());

        assertEquals("Heart rate", json.path("measurements").get(0).path("name").asText());
        assertEquals("/min", json.path("measurements").get(0).path("unit").asText());
        assertEquals(6, json.path("measurements").get(0).path("dataPoints").size());
        assertEquals("2026-02-12T08:00:01+01:00",
                json.path("measurements").get(0).path("dataPoints").get(1).path("timestamp").asText());
        assertEquals("2026-02-12T08:00:01+01:00",
                json.path("measurements").get(0).path("dataPoints").get(2).path("timestamp").asText());
        assertEquals(74.0,
                json.path("measurements").get(0).path("dataPoints").get(2).path("value").asDouble());

        assertEquals("Blood oxygen saturation", json.path("measurements").get(1).path("name").asText());
        assertEquals("%", json.path("measurements").get(1).path("unit").asText());

        assertEquals("Caretakers labeling apnea", json.path("measurements").get(2).path("name").asText());
        assertEquals("binary", json.path("measurements").get(2).path("unit").asText());
        assertTrue(json.path("measurements").get(2).path("dataPoints").get(0).path("value").isNull());
        assertEquals("2026-02-12T08:01:19+01:00",
                json.path("measurements").get(2).path("dataPoints").get(2).path("timestamp").asText());
    }

    @Test
    void shouldRejectInvalidInputSchema() {
        final Patient patient = patient(UUID.randomUUID(), null);
        final Algorithm algorithm = algorithm("{", List.of());

        final DomainException exception = assertThrows(
                DomainException.class,
                () -> algorithmDataPreparationService.prepareInputData(patient, algorithm)
        );

        assertEquals(DomainExceptionCode.ALGORITHM_INVALID_INPUT_SCHEMA, exception.getCode());
    }

    @Test
    void shouldPrepareAnalysisPayloadForNewDatasetObjectSchema() throws Exception {
        final UUID analysisId = UUID.randomUUID();
        final UUID patientId = UUID.randomUUID();
        final UUID caretakerId = UUID.randomUUID();
        final UUID algorithmId = UUID.randomUUID();
        final UUID heartRateId = UUID.randomUUID();
        final UUID oxygenSaturationId = UUID.randomUUID();

        final Measurement heartRate = measurement(
                heartRateId,
                "Heart rate",
                MeasurementType.HEART_RATE,
                "openEHR-EHR-OBSERVATION.pulse.v2"
        );
        final Measurement oxygenSaturation = measurement(
                oxygenSaturationId,
                "Blood oxygen saturation",
                MeasurementType.OXYGEN_SATURATION,
                "openEHR-EHR-OBSERVATION.pulse_oximetry.v1"
        );
        final Patient patient = patient(patientId, null, caretakerId);
        final Algorithm algorithm = algorithm(
                """
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
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
                """,
                List.of(heartRate, oxygenSaturation),
                algorithmId,
                "Apnea detection",
                "apnea_model",
                "3"
        );

        when(biometricRecordService.findAll(anyBiometricFilterCommand()))
                .thenReturn(records(
                        basicRecord("2026-02-12T08:00:00+01:00", "d5db0a79-0926-426a-b51b-6c2067520f0b", 67.25, "beats/min"),
                        basicRecord("2026-02-12T08:00:01+01:00", "d5db0a79-0926-426a-b51b-6c2067520f0b", 66.99, "beats/min"),
                        basicRecord("2026-02-12T08:00:02+01:00", "d5db0a79-0926-426a-b51b-6c2067520f0b", 67.48, "beats/min")
                ))
                .thenReturn(records(
                        basicRecord("2026-02-12T08:00:00+01:00", "1369bf35-0cce-4a64-a6a1-ba7d8728e4df", 97.99, "%"),
                        basicRecord("2026-02-12T08:00:01+01:00", "1369bf35-0cce-4a64-a6a1-ba7d8728e4df", 97.99, "%")
                ));

        final String result = algorithmDataPreparationService.prepareInputData(
                patient,
                algorithm,
                "2026-02-12T08:00:00+01:00",
                "2026-02-12T08:01:39+01:00",
                analysisId,
                List.of(heartRateId, oxygenSaturationId),
                "{\"step_size\":15}",
                null
        );

        final JsonNode json = OBJECT_MAPPER.readTree(result);

        assertValidAgainstSchema(algorithm.getInputSchema(), json);
        assertEquals(1, json.path("analysisPayload").size());

        final JsonNode payload = json.path("analysisPayload").get(0);
        assertEquals(analysisId.toString(), payload.path("analysisId").asText());
        assertEquals(patientId.toString(), payload.path("patientId").asText());
        assertEquals(caretakerId.toString(), payload.path("caretakersId").asText());
        assertEquals("APNEA", payload.path("diagnosisType").asText());
        assertEquals("AdminApp", payload.path("datasourceType").asText());
        assertEquals("apnea_model", payload.path("modelName").asText());
        assertEquals("3", payload.path("modelVersion").asText());
        assertEquals(15, payload.path("modelParameters").path("step_size").asInt());
        assertEquals(2, payload.path("datasetObjects").size());

        final JsonNode heartRateDataset = payload.path("datasetObjects").get(0);
        assertEquals(heartRateId.toString(), heartRateDataset.path("dataPointsId").asText());
        assertEquals("feature", heartRateDataset.path("datasetType").asText());
        assertEquals("HEART_RATE", heartRateDataset.path("type").asText());
        assertEquals("beats/min", heartRateDataset.path("unit").asText());
        assertEquals("2026-02-12T08:00:02+01:00", heartRateDataset.path("timestampLastEdit").asText());
        assertEquals(3, heartRateDataset.path("dataPoints").size());
        assertEquals(67.25, heartRateDataset.path("dataPoints").get(0).path("value").asDouble());

        final JsonNode oxygenDataset = payload.path("datasetObjects").get(1);
        assertEquals(oxygenSaturationId.toString(), oxygenDataset.path("dataPointsId").asText());
        assertEquals("OXYGEN_SATURATION", oxygenDataset.path("type").asText());
        assertEquals("%", oxygenDataset.path("unit").asText());
        assertEquals("2026-02-12T08:00:01+01:00", oxygenDataset.path("timestampLastEdit").asText());
    }

    private Patient patient(final UUID patientId, final UUID keycloakId) {
        return patient(patientId, keycloakId, null);
    }

    private Patient patient(final UUID patientId, final UUID keycloakId, final UUID caretakerId) {
        final Patient patient = mock(Patient.class);
        lenient().when(patient.getId()).thenReturn(patientId);
        lenient().when(patient.getKeycloakId()).thenReturn(keycloakId);
        if (caretakerId != null) {
            final Caretaker caretaker = mock(Caretaker.class);
            lenient().when(caretaker.getId()).thenReturn(caretakerId);
            lenient().when(patient.getCaretakers()).thenReturn(List.of(caretaker));
        }
        return patient;
    }

    private Algorithm algorithm(final String inputSchema, final List<Measurement> measurements) {
        return algorithm(inputSchema, measurements, UUID.randomUUID(), "Algorithm");
    }

    private Algorithm algorithm(
            final String inputSchema,
            final List<Measurement> measurements,
            final UUID algorithmId,
            final String algorithmName
    ) {
        return algorithm(inputSchema, measurements, algorithmId, algorithmName, null, null);
    }

    private Algorithm algorithm(
            final String inputSchema,
            final List<Measurement> measurements,
            final UUID algorithmId,
            final String algorithmName,
            final String containerName,
            final String containerVersion
    ) {
        final Algorithm algorithm = mock(Algorithm.class);
        lenient().when(algorithm.getId()).thenReturn(algorithmId);
        lenient().when(algorithm.getName()).thenReturn(algorithmName);
        lenient().when(algorithm.getContainerName()).thenReturn(containerName);
        lenient().when(algorithm.getContainerVersion()).thenReturn(containerVersion);
        lenient().when(algorithm.getInputSchema()).thenReturn(inputSchema);
        lenient().when(algorithm.getMeasurements()).thenReturn(measurements);
        return algorithm;
    }

    private Measurement measurement(
            final UUID id,
            final String name,
            final MeasurementType type,
            final String ehrObservationId
    ) {
        final Measurement measurement = mock(Measurement.class);
        lenient().when(measurement.getId()).thenReturn(id);
        lenient().when(measurement.getName()).thenReturn(name);
        lenient().when(measurement.getType()).thenReturn(type);
        lenient().when(measurement.getEhrObservationId()).thenReturn(ehrObservationId);
        lenient().when(measurement.getChartType()).thenReturn(MeasurementChartType.LINE);
        return measurement;
    }

    private Measurement measurement(
            final String name,
            final MeasurementType type,
            final String ehrObservationId
    ) {
        return measurement(UUID.randomUUID(), name, type, ehrObservationId);
    }

    private ArgumentCaptor<BiometricFilterCommand<?>> biometricFilterCommandCaptor() {
        return (ArgumentCaptor<BiometricFilterCommand<?>>) (ArgumentCaptor<?>)
                ArgumentCaptor.forClass(BiometricFilterCommand.class);
    }

    @SuppressWarnings("unchecked")
    private BiometricFilterCommand<si.result.project.eearly.model.biometric.measurement.MeasurementValue> anyBiometricFilterCommand() {
        return any(BiometricFilterCommand.class);
    }

    @SuppressWarnings("unchecked")
    private Stream<BiometricRecord<MeasurementValue>> records(final BiometricRecord<?>... records) {
        return Stream.of(records)
                .map(record -> (BiometricRecord<MeasurementValue>) record);
    }

    private Stream<BiometricRecord<MeasurementValue>> emptyRecords() {
        return Stream.empty();
    }

    private BasicBiometricRecord basicRecord(
            final String timestamp,
            final String batchId,
            final Double value,
            final String unit
    ) {
        return BasicBiometricRecord.builder()
                .timestamp(ZonedDateTime.parse(timestamp))
                .batchId(batchId)
                .value(new BasicValue(value))
                .unit(unit)
                .status(BiometricAnalysisCriticalityType.NORMAL)
                .build();
    }

    private void assertValidAgainstSchema(final String schemaJson, final JsonNode payload) {
        final JsonSchema schema = JSON_SCHEMA_FACTORY.getSchema(schemaJson);
        final Set<String> validationErrors = schema.validate(
                        payload,
                        executionContext -> executionContext.getExecutionConfig().setFormatAssertionsEnabled(true)
                ).stream()
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(validationErrors.isEmpty(), "Schema validation errors: " + validationErrors);
    }
}
