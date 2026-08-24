package si.result.project.eearly.port.algorithmexecution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.analysis.AnalysisSelection;
import si.result.project.eearly.model.biometric.BasicBiometricRecord;
import si.result.project.eearly.model.biometric.BiometricRecord;
import si.result.project.eearly.model.biometric.BloodPressureBiometricRecord;
import si.result.project.eearly.model.biometric.command.BiometricFilterCommand;
import si.result.project.eearly.model.biometric.enumeration.BiometricDataAggregationLevel;
import si.result.project.eearly.model.biometric.enumeration.BiometricMeasurementType;
import si.result.project.eearly.model.biometric.measurement.BasicValue;
import si.result.project.eearly.model.biometric.measurement.BloodPressureValue;
import si.result.project.eearly.model.biometric.measurement.MeasurementValue;
import si.result.project.eearly.model.measurement.Measurement;
import si.result.project.eearly.model.measurement.MeasurementType;
import si.result.project.eearly.model.patient.Patient;
import si.result.project.eearly.port.biometric.BiometricRecordService;
import si.result.project.eearly.util.BiometricMeasurementTypeMapper;
import si.result.spring.boot.bricks.exception.DomainException;

@Service
@RequiredArgsConstructor
public class AlgorithmDataPreparationService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter OFFSET_DATE_TIME_WITH_SECONDS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final Comparator<BiometricRecord<?>> RECORD_TIMESTAMP_COMPARATOR =
            Comparator.comparing(BiometricRecord::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder()));
    private static final String SCHEMA_ITEMS_PROPERTY = "items";
    private static final String SCHEMA_PROPERTIES_PROPERTY = "properties";
    private static final String MEASUREMENTS_PROPERTY = "measurements";
    private static final String DATA_POINTS_PROPERTY = "dataPoints";
    private static final String DATASET_OBJECTS_PROPERTY = "datasetObjects";
    private static final String VALUE_PROPERTY = "value";
    private static final String TIMESTAMP_PROPERTY = "timestamp";
    private static final String HIGH_VALUE_PROPERTY = "highValue";
    private static final String LOW_VALUE_PROPERTY = "lowValue";

    private final BiometricRecordService biometricRecordService;

    public String prepareInputData(final Patient patient, final Algorithm algorithm) {
        return prepareInputData(patient, algorithm, List.of(), null, null, null);
    }

    public String prepareInputData(
            final Patient patient,
            final Algorithm algorithm,
            final String startDateTime,
            final String endDateTime
    ) {
        return prepareInputData(patient, algorithm, startDateTime, endDateTime, null, null, null, null);
    }

    public String prepareInputData(
            final Patient patient,
            final Algorithm algorithm,
            final List<AnalysisSelection> selections,
            final UUID analysisId,
            final String algorithmConfigJson,
            final String callbackUrl
    ) {
        return preparePreparedInputData(
                patient,
                algorithm,
                toSelectionWindows(selections),
                analysisId,
                algorithmConfigJson,
                callbackUrl
        );
    }

    public List<PreparedMeasurementData> prepareMeasurementData(
            final Patient patient,
            final Algorithm algorithm,
            final List<AnalysisSelection> selections
    ) {
        if (patient == null || algorithm == null) {
            return List.of();
        }

        final UUID ehrId = resolveEhrId(patient);
        final List<SelectionWindow> selectionWindows = normalizeSelectionWindows(toSelectionWindows(selections));

        return safeMeasurements(algorithm).stream()
                .filter(measurement -> isSelected(measurement, selectionWindows))
                .map(measurement -> {
                    final List<? extends BiometricRecord<?>> records = fetchSortedRecords(
                            ehrId,
                            measurement,
                            selectionWindows
                    );
                    return new PreparedMeasurementData(
                            measurement,
                            resolveMeasurementUnit(records),
                            records
                    );
                })
                .toList();
    }

    public String prepareInputData(
            final Patient patient,
            final Algorithm algorithm,
            final String startDateTime,
            final String endDateTime,
            final UUID analysisId,
            final List<UUID> measurementIds,
            final String algorithmConfigJson,
            final String callbackUrl
    ) {
        return preparePreparedInputData(
                patient,
                algorithm,
                List.of(selectionWindow(startDateTime, endDateTime, measurementIds)),
                analysisId,
                algorithmConfigJson,
                callbackUrl
        );
    }

    private String preparePreparedInputData(
            final Patient patient,
            final Algorithm algorithm,
            final List<SelectionWindow> selectionWindows,
            final UUID analysisId,
            final String algorithmConfigJson,
            final String callbackUrl
    ) {
        final JsonNode inputSchema = parseJsonSchema(algorithm.getInputSchema());
        final List<SelectionWindow> normalizedSelectionWindows = normalizeSelectionWindows(selectionWindows);
        final BuildContext buildContext = BuildContext.root(
                patient,
                algorithm,
                normalizedSelectionWindows,
                analysisId,
                algorithmConfigJson,
                callbackUrl
        );
        final JsonNode payload = buildNode(inputSchema, null, buildContext);
        return writePayload(payload);
    }

    private JsonNode buildNode(
            final JsonNode schemaNode,
            final String propertyName,
            final BuildContext context
    ) {
        if (schemaNode == null || schemaNode.isMissingNode() || schemaNode.isNull()) {
            return null;
        }

        if (isConfigurationObjectProperty(propertyName) && context.algorithmConfigJson() != null
                && !context.algorithmConfigJson().isBlank()) {
            return parseAlgorithmConfig(context.algorithmConfigJson());
        }

        if (isMeasurementCollection(propertyName)) {
            return buildMeasurementArray(propertyName, schemaNode.path(SCHEMA_ITEMS_PROPERTY), context);
        }
        if (isAnalysisPayloadCollection(propertyName)) {
            return buildAnalysisPayloadArray(schemaNode.path(SCHEMA_ITEMS_PROPERTY), context);
        }
        if (isRecordCollection(propertyName)) {
            return buildRecordArray(propertyName, schemaNode.path(SCHEMA_ITEMS_PROPERTY), context);
        }
        if (isRecordValueProperty(propertyName, context)) {
            return serializeRecordValue(context.biometricRecord());
        }

        final JsonNode constantValue = constantPropertyValue(schemaNode);
        if (constantValue != null) {
            return constantValue.deepCopy();
        }

        if (isArraySchema(schemaNode)) {
            return OBJECT_MAPPER.createArrayNode();
        }
        if (isObjectSchema(schemaNode)) {
            return buildObjectNode(propertyName, schemaNode, context);
        }

        return resolvePrimitiveNode(propertyName, context);
    }

    private JsonNode buildObjectNode(
            final String propertyName,
            final JsonNode schemaNode,
            final BuildContext context
    ) {
        final JsonNode propertyDefinitions = schemaNode.path(SCHEMA_PROPERTIES_PROPERTY);
        final BuildContext childContext = context.withContainer(propertyName, schemaNode);
        final ObjectNode objectNode = OBJECT_MAPPER.createObjectNode();

        if ("dateRange".equals(propertyName) && !hasSchemaProperties(schemaNode)) {
            final String from = firstStartDateTime(context.selectionWindows());
            final String to = lastEndDateTime(context.selectionWindows());
            if (from != null) {
                objectNode.put("from", from);
            }
            if (to != null) {
                objectNode.put("to", to);
            }
            return objectNode;
        }

        if (!propertyDefinitions.isObject()) {
            return objectNode;
        }

        final Iterator<String> fieldNames = propertyDefinitions.fieldNames();
        while (fieldNames.hasNext()) {
            final String fieldName = fieldNames.next();
            final JsonNode childNode = buildNode(propertyDefinitions.get(fieldName), fieldName, childContext);
            if (childNode != null) {
                objectNode.set(fieldName, childNode);
                // Testing alias: hosted apnea model expects `doctorsId`; OSS schema uses `caretakersId`.
                if ("analysisPayload".equals(propertyName) && "caretakersId".equals(fieldName)) {
                    objectNode.set("doctorsId", childNode);
                }
            }
        }

        if ("analysisPayload".equals(propertyName) && !objectNode.has("doctorsId")) {
            final String doctorsId = firstCaretakerId(context.patient());
            if (doctorsId != null) {
                objectNode.put("doctorsId", doctorsId);
            } else if (objectNode.hasNonNull("caretakersId")) {
                objectNode.set("doctorsId", objectNode.get("caretakersId"));
            }
        }

        return objectNode;
    }

    private ArrayNode buildAnalysisPayloadArray(final JsonNode itemSchema, final BuildContext context) {
        final ArrayNode payloadArray = OBJECT_MAPPER.createArrayNode();
        payloadArray.add(buildObjectNode("analysisPayload", itemSchema, context));
        return payloadArray;
    }

    private ArrayNode buildMeasurementArray(
            final String propertyName,
            final JsonNode itemSchema,
            final BuildContext context
    ) {
        final ArrayNode measurementNodes = OBJECT_MAPPER.createArrayNode();
        final UUID ehrId = resolveEhrId(context.patient());

        for (Measurement measurement : safeMeasurements(context.algorithm())) {
            if (isSelected(measurement, context.selectionWindows())) {
                final List<? extends BiometricRecord<?>> records = fetchSortedRecords(
                        ehrId,
                        measurement,
                        context.selectionWindows()
                );

                if (!hasSchemaProperties(itemSchema)) {
                    measurementNodes.add(serializeImplicitMeasurement(measurement, records));
                } else {
                    measurementNodes.add(buildObjectNode(
                            propertyName,
                            itemSchema,
                            context.withMeasurement(measurement, records)
                    ));
                }
            }
        }

        return measurementNodes;
    }

    private ArrayNode buildRecordArray(
            final String propertyName,
            final JsonNode itemSchema,
            final BuildContext context
    ) {
        final ArrayNode recordNodes = OBJECT_MAPPER.createArrayNode();
        if (context.records() == null) {
            return recordNodes;
        }

        for (BiometricRecord<?> biometricRecord : context.records()) {
            if (!hasSchemaProperties(itemSchema)) {
                recordNodes.add(DATA_POINTS_PROPERTY.equals(propertyName)
                        ? serializeDataPoint(biometricRecord)
                        : serializeRecord(biometricRecord));
                continue;
            }

            recordNodes.add(buildObjectNode(
                    propertyName,
                    itemSchema,
                    context.withRecord(biometricRecord, DATA_POINTS_PROPERTY.equals(propertyName)
                            ? RecordShape.DATA_POINT
                            : RecordShape.RECORD)
            ));
        }

        return recordNodes;
    }

    private JsonNode resolvePrimitiveNode(final String propertyName, final BuildContext context) {
        if (VALUE_PROPERTY.equals(propertyName)
                && context.biometricRecord() instanceof BasicBiometricRecord basicRecord
                && context.recordShape() == RecordShape.DATA_POINT) {
            final BasicValue value = basicRecord.getValue();
            return value == null || value.value() == null
                    ? OBJECT_MAPPER.getNodeFactory().nullNode()
                    : OBJECT_MAPPER.valueToTree(value.value());
        }

        final Object value = resolvePrimitiveValue(propertyName, context);
        if (value == null) {
            return null;
        }
        return OBJECT_MAPPER.valueToTree(value);
    }

    private Object resolvePrimitiveValue(final String propertyName, final BuildContext context) {
        if (propertyName == null) {
            return null;
        }

        return switch (propertyName) {
            case "correlationId" -> UUID.randomUUID().toString();
            case "analysisId" -> (context.analysisId() != null ? context.analysisId() : UUID.randomUUID()).toString();
            case "patientId" -> uuidValue(context.patient() != null ? context.patient().getId() : null);
            case "caretakersId", "doctorsId" -> firstCaretakerId(context.patient());
            case "callbackUrl" -> blankToNull(context.callbackUrl());
            case "algorithmId" -> uuidValue(context.algorithm() != null ? context.algorithm().getId() : null);
            case "name" -> resolveName(context);
            case "algorithmName" -> context.algorithm() != null ? context.algorithm().getName() : null;
            case "modelName" -> context.algorithm() != null ? context.algorithm().getContainerName() : null;
            case "modelVersion" -> context.algorithm() != null ? context.algorithm().getContainerVersion() : null;
            case "measurementId", "dataPointsId" ->
                    uuidValue(context.measurement() != null ? context.measurement().getId() : null);
            case "ehrObservationId" ->
                    context.measurement() != null ? context.measurement().getEhrObservationId() : null;
            case "type" -> resolveMeasurementTypeValue(context);
            case "unit" -> resolveUnitValue(context);
            case "from" -> isDateRangeContext(context) ? firstStartDateTime(context.selectionWindows()) : null;
            case "to" -> isDateRangeContext(context) ? lastEndDateTime(context.selectionWindows()) : null;
            case "timestampLastEdit" -> latestRecordTimestamp(context.records());
            case TIMESTAMP_PROPERTY -> resolveRecordTimestamp(context);
            case "batchId" -> truncateBatchId(context.biometricRecord() != null
                    ? context.biometricRecord().getBatchId()
                    : null);
            case VALUE_PROPERTY -> resolveRecordPrimitiveValue(context);
            case HIGH_VALUE_PROPERTY -> resolveRecordHighValue(context);
            case LOW_VALUE_PROPERTY -> resolveRecordLowValue(context);
            case "status" -> context.biometricRecord() != null && context.biometricRecord().getStatus() != null
                    ? context.biometricRecord().getStatus().name()
                    : null;
            default -> null;
        };
    }

    private String resolveName(final BuildContext context) {
        if (isMeasurementObjectContext(context)) {
            return context.measurement() != null ? context.measurement().getName() : null;
        }
        if (isAlgorithmObjectContext(context)) {
            return context.algorithm() != null ? context.algorithm().getName() : null;
        }
        return null;
    }

    private String resolveMeasurementTypeValue(final BuildContext context) {
        if (!isMeasurementObjectContext(context) || context.measurement() == null) {
            return null;
        }

        if (DATASET_OBJECTS_PROPERTY.equals(context.currentContainerName()) && context.measurement().getType() != null) {
            return context.measurement().getType().name();
        }

        final JsonNode currentProperties = context.currentContainerSchema() == null
                ? OBJECT_MAPPER.createObjectNode()
                : context.currentContainerSchema().path(SCHEMA_PROPERTIES_PROPERTY);

        if (!currentProperties.isObject()) {
            return context.measurement().getName();
        }

        if (context.measurement().getType() != null && currentProperties.has("name")) {
            return context.measurement().getType().name();
        }

        return context.measurement().getName();
    }

    private String resolveUnitValue(final BuildContext context) {
        if (isMeasurementObjectContext(context)) {
            final String unitFromRecords = resolveMeasurementUnit(
                    context.records() != null ? context.records() : List.of());
            if (unitFromRecords != null) {
                return unitFromRecords;
            }
            return defaultUnitForMeasurement(context.measurement());
        }
        if (context.biometricRecord() != null) {
            final String unit = context.biometricRecord().getUnit();
            if (unit != null && !unit.isBlank()) {
                return unit;
            }
            return defaultUnitForMeasurement(context.measurement());
        }
        return null;
    }

    private String defaultUnitForMeasurement(final Measurement measurement) {
        if (measurement == null || measurement.getType() == null) {
            return null;
        }
        return switch (measurement.getType()) {
            case HEART_RATE -> "BPM";
            case OXYGEN_SATURATION -> "%";
            case BODY_TEMPERATURE -> "°C";
            case WEIGHT -> "kg";
            case HEIGHT -> "cm";
            case BLOOD_GLUCOSE -> "mmol/L";
            case BLOOD_PRESSURE, BLOOD_PRESSURE_SYSTOLIC, BLOOD_PRESSURE_DIASTOLIC -> "mmHg";
            default -> null;
        };
    }

    private String resolveRecordTimestamp(final BuildContext context) {
        if (context.biometricRecord() == null || context.biometricRecord().getTimestamp() == null) {
            return null;
        }

        if (context.recordShape() == RecordShape.DATA_POINT) {
            return context.biometricRecord().getTimestamp().toOffsetDateTime().format(OFFSET_DATE_TIME_WITH_SECONDS);
        }

        return context.biometricRecord().getTimestamp().toInstant().toString();
    }

    private Number resolveRecordPrimitiveValue(final BuildContext context) {
        if (!(context.biometricRecord() instanceof BasicBiometricRecord basicRecord)) {
            return null;
        }

        final BasicValue value = basicRecord.getValue();
        return value != null ? value.value() : null;
    }

    private Number resolveRecordHighValue(final BuildContext context) {
        if (!(context.biometricRecord() instanceof BloodPressureBiometricRecord bloodPressureRecord)) {
            return null;
        }

        final BloodPressureValue value = bloodPressureRecord.getValue();
        return value != null ? value.highValue() : null;
    }

    private Number resolveRecordLowValue(final BuildContext context) {
        if (!(context.biometricRecord() instanceof BloodPressureBiometricRecord bloodPressureRecord)) {
            return null;
        }

        final BloodPressureValue value = bloodPressureRecord.getValue();
        return value != null ? value.lowValue() : null;
    }

    private ObjectNode serializeImplicitMeasurement(
            final Measurement measurement,
            final List<? extends BiometricRecord<?>> records
    ) {
        final ObjectNode measurementNode = OBJECT_MAPPER.createObjectNode();
        measurementNode.set("type", OBJECT_MAPPER.valueToTree(measurement.getName()));

        final ArrayNode recordNodes = measurementNode.putArray("records");
        for (BiometricRecord<?> biometricRecord : records) {
            recordNodes.add(serializeRecord(biometricRecord));
        }

        return measurementNode;
    }

    private ObjectNode serializeRecord(final BiometricRecord<?> biometricRecord) {
        final ObjectNode recordNode = OBJECT_MAPPER.createObjectNode();

        if (biometricRecord.getTimestamp() != null) {
            recordNode.put(TIMESTAMP_PROPERTY, biometricRecord.getTimestamp().toInstant().toString());
        }

        recordNode.set(VALUE_PROPERTY, serializeRecordValue(biometricRecord));

        if (biometricRecord.getUnit() != null) {
            recordNode.put("unit", biometricRecord.getUnit());
        }
        if (biometricRecord.getStatus() != null) {
            recordNode.put("status", biometricRecord.getStatus().name());
        }

        return recordNode;
    }

    private JsonNode serializeRecordValue(final BiometricRecord<?> biometricRecord) {
        if (biometricRecord instanceof BasicBiometricRecord basicRecord) {
            return serializeBasicValue(basicRecord.getValue());
        }
        if (biometricRecord instanceof BloodPressureBiometricRecord bloodPressureRecord) {
            return serializeBloodPressureValue(bloodPressureRecord.getValue());
        }
        return OBJECT_MAPPER.createObjectNode();
    }

    private ObjectNode serializeDataPoint(final BiometricRecord<?> biometricRecord) {
        final ObjectNode dataPointNode = OBJECT_MAPPER.createObjectNode();

        if (biometricRecord.getTimestamp() != null) {
            dataPointNode.put(
                    TIMESTAMP_PROPERTY,
                    biometricRecord.getTimestamp().toOffsetDateTime().format(OFFSET_DATE_TIME_WITH_SECONDS)
            );
        }
        if (biometricRecord.getBatchId() != null) {
            dataPointNode.put("batchId", truncateBatchId(biometricRecord.getBatchId()));
        }

        if (biometricRecord instanceof BasicBiometricRecord basicRecord) {
            final BasicValue value = basicRecord.getValue();
            if (value != null && value.value() != null) {
                dataPointNode.put(VALUE_PROPERTY, value.value().intValue());
            } else {
                dataPointNode.putNull(VALUE_PROPERTY);
            }
            return dataPointNode;
        }

        if (biometricRecord instanceof BloodPressureBiometricRecord bloodPressureRecord) {
            final BloodPressureValue value = bloodPressureRecord.getValue();
            if (value != null && value.highValue() != null) {
                dataPointNode.put(HIGH_VALUE_PROPERTY, value.highValue());
            }
            if (value != null && value.lowValue() != null) {
                dataPointNode.put(LOW_VALUE_PROPERTY, value.lowValue());
            }
            return dataPointNode;
        }

        dataPointNode.putNull(VALUE_PROPERTY);
        return dataPointNode;
    }

    private ObjectNode serializeBasicValue(final BasicValue value) {
        final ObjectNode valueNode = OBJECT_MAPPER.createObjectNode();
        if (value != null && value.value() != null) {
            valueNode.put(VALUE_PROPERTY, value.value());
        }
        return valueNode;
    }

    private ObjectNode serializeBloodPressureValue(final BloodPressureValue value) {
        final ObjectNode valueNode = OBJECT_MAPPER.createObjectNode();
        if (value == null) {
            return valueNode;
        }

        if (value.highValue() != null) {
            valueNode.put(HIGH_VALUE_PROPERTY, value.highValue());
        }
        if (value.lowValue() != null) {
            valueNode.put(LOW_VALUE_PROPERTY, value.lowValue());
        }

        return valueNode;
    }

    private List<? extends BiometricRecord<?>> fetchSortedRecords(
            final UUID ehrId,
            final Measurement measurement,
            final List<SelectionWindow> selectionWindows
    ) {
        final BiometricMeasurementType measurementType = mapMeasurementType(measurement.getType());
        final List<? extends BiometricRecord<?>> sorted = selectionWindows.stream()
                .filter(selectionWindow -> isSelected(measurement, selectionWindow))
                .flatMap(selectionWindow -> fetchRecords(
                        ehrId,
                        measurement,
                        measurementType,
                        selectionWindow.startDateTime(),
                        selectionWindow.endDateTime(),
                        measurementType.valueClass()
                ).stream())
                .distinct()
                .sorted(RECORD_TIMESTAMP_COMPARATOR)
                .toList();

        // Hosted model requires strictly increasing timestamps; collapse duplicate ingest batches.
        final Map<Instant, BiometricRecord<?>> uniqueByTimestamp = new LinkedHashMap<>();
        for (BiometricRecord<?> record : sorted) {
            if (record.getTimestamp() == null) {
                continue;
            }
            uniqueByTimestamp.put(record.getTimestamp().toInstant(), record);
        }
        return List.copyOf(uniqueByTimestamp.values());
    }

    private <T extends MeasurementValue> List<BiometricRecord<T>> fetchRecords(
            final UUID ehrId,
            final Measurement measurement,
            final BiometricMeasurementType measurementType,
            final String startDateTime,
            final String endDateTime,
            final Class<T> valueClass
    ) {
        final BiometricFilterCommand<T> command = BiometricFilterCommand.builderFor(valueClass)
                .ehrId(ehrId)
                .measurementName(measurement.getName())
                .measurementType(measurementType)
                .ehrObservationId(measurement.getEhrObservationId())
                .chartType(measurement.getChartType())
                .startDateTime(startDateTime)
                .endDateTime(endDateTime)
                .aggregationLevel(BiometricDataAggregationLevel.NONE)
                .build();

        return biometricRecordService.findAll(command).toList();
    }

    private BiometricMeasurementType mapMeasurementType(final MeasurementType measurementType) {
        return BiometricMeasurementTypeMapper.fromMeasurementType(measurementType);
    }

    private JsonNode parseJsonSchema(final String inputSchema) {
        if (inputSchema == null || inputSchema.isBlank()) {
            return OBJECT_MAPPER.createObjectNode();
        }

        try {
            return OBJECT_MAPPER.readTree(inputSchema);
        } catch (JsonProcessingException exception) {
            throw new DomainException(DomainExceptionCode.ALGORITHM_INVALID_INPUT_SCHEMA, exception.getOriginalMessage());
        }
    }

    private JsonNode parseAlgorithmConfig(final String algorithmConfigJson) {
        if (algorithmConfigJson == null || algorithmConfigJson.isBlank()) {
            return OBJECT_MAPPER.createObjectNode();
        }

        try {
            return OBJECT_MAPPER.readTree(algorithmConfigJson);
        } catch (JsonProcessingException exception) {
            throw new DomainException(
                    DomainExceptionCode.ALGORITHM_EXECUTION_INVALID_INPUT_PARAMETERS,
                    exception.getOriginalMessage()
            );
        }
    }

    private String writePayload(final JsonNode payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new DomainException(
                    DomainExceptionCode.ALGORITHM_INPUT_PREPARATION_FAILED,
                    exception.getOriginalMessage()
            );
        }
    }

    private UUID resolveEhrId(final Patient patient) {
        return patient.getKeycloakId() != null ? patient.getKeycloakId() : patient.getId();
    }

    private List<Measurement> safeMeasurements(final Algorithm algorithm) {
        final List<Measurement> measurements = algorithm.getMeasurements();
        return measurements == null ? List.of() : measurements;
    }

    private boolean isSelected(final Measurement measurement, final List<SelectionWindow> selectionWindows) {
        return selectionWindows.stream().anyMatch(selectionWindow -> isSelected(measurement, selectionWindow));
    }

    private boolean isSelected(final Measurement measurement, final SelectionWindow selectionWindow) {
        final Set<UUID> selectedMeasurementIds = selectionWindow.measurementIds() == null
                ? Set.of()
                : new HashSet<>(selectionWindow.measurementIds());

        return selectedMeasurementIds.isEmpty()
                || measurement.getId() == null
                || selectedMeasurementIds.contains(measurement.getId());
    }

    private String resolveMeasurementUnit(final List<? extends BiometricRecord<?>> records) {
        return records.stream()
                .map(BiometricRecord::getUnit)
                .filter(unit -> unit != null && !unit.isBlank())
                .findFirst()
                .orElse(null);
    }

    private List<SelectionWindow> normalizeSelectionWindows(final List<SelectionWindow> selectionWindows) {
        if (selectionWindows == null || selectionWindows.isEmpty()) {
            return List.of(selectionWindow(null, null, null));
        }

        return selectionWindows;
    }

    private List<SelectionWindow> toSelectionWindows(final List<AnalysisSelection> selections) {
        if (selections == null || selections.isEmpty()) {
            return List.of();
        }

        return selections.stream()
                .map(selection -> new SelectionWindow(
                        formatOffsetDateTime(selection.startTimestamp()),
                        formatOffsetDateTime(selection.endTimestamp()),
                        selection.measurementIds()
                ))
                .toList();
    }

    private SelectionWindow selectionWindow(
            final String startDateTime,
            final String endDateTime,
            final List<UUID> measurementIds
    ) {
        return new SelectionWindow(startDateTime, endDateTime, measurementIds);
    }

    private String formatOffsetDateTime(final OffsetDateTime offsetDateTime) {
        return offsetDateTime == null ? null : offsetDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private boolean isArraySchema(final JsonNode schemaNode) {
        return hasType(schemaNode, "array") || schemaNode.has(SCHEMA_ITEMS_PROPERTY);
    }

    private boolean isObjectSchema(final JsonNode schemaNode) {
        return hasType(schemaNode, "object") || schemaNode.has(SCHEMA_PROPERTIES_PROPERTY);
    }

    private boolean hasType(final JsonNode schemaNode, final String typeName) {
        final JsonNode typeNode = schemaNode.get("type");
        if (typeNode == null || typeNode.isNull()) {
            return false;
        }
        if (typeNode.isTextual()) {
            return typeName.equals(typeNode.asText());
        }
        if (typeNode.isArray()) {
            for (JsonNode entry : typeNode) {
                if (entry.isTextual() && typeName.equals(entry.asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasSchemaProperties(final JsonNode schemaNode) {
        final JsonNode properties = schemaNode.path(SCHEMA_PROPERTIES_PROPERTY);
        return properties.isObject() && properties.fieldNames().hasNext();
    }

    private boolean isMeasurementCollection(final String propertyName) {
        return MEASUREMENTS_PROPERTY.equals(propertyName) || DATASET_OBJECTS_PROPERTY.equals(propertyName);
    }

    private boolean isAnalysisPayloadCollection(final String propertyName) {
        return "analysisPayload".equals(propertyName);
    }

    private boolean isRecordCollection(final String propertyName) {
        return "records".equals(propertyName) || DATA_POINTS_PROPERTY.equals(propertyName);
    }

    private boolean isConfigurationObjectProperty(final String propertyName) {
        return "algorithmConfig".equals(propertyName) || "modelParameters".equals(propertyName);
    }

    private boolean isRecordValueProperty(final String propertyName, final BuildContext context) {
        return VALUE_PROPERTY.equals(propertyName)
                && context.biometricRecord() != null
                && context.recordShape() == RecordShape.RECORD;
    }

    private boolean isAlgorithmObjectContext(final BuildContext context) {
        return "algorithm".equals(context.currentContainerName());
    }

    private boolean isMeasurementObjectContext(final BuildContext context) {
        return MEASUREMENTS_PROPERTY.equals(context.currentContainerName())
                || DATASET_OBJECTS_PROPERTY.equals(context.currentContainerName());
    }

    private boolean isDateRangeContext(final BuildContext context) {
        return "dateRange".equals(context.currentContainerName());
    }

    private String uuidValue(final UUID value) {
        return value != null ? value.toString() : null;
    }

    private String blankToNull(final String value) {
        return value != null && !value.isBlank() ? value : null;
    }

    private String truncateBatchId(final String batchId) {
        final String normalizedBatchId = blankToNull(batchId);
        if (normalizedBatchId == null) {
            return null;
        }

        final int separatorIndex = normalizedBatchId.indexOf("::");
        return separatorIndex >= 0 ? normalizedBatchId.substring(0, separatorIndex) : normalizedBatchId;
    }

    private String firstCaretakerId(final Patient patient) {
        if (patient == null) {
            return null;
        }

        try {
            final List<?> caretakers = patient.getCaretakers();
            if (caretakers == null || caretakers.isEmpty()) {
                return null;
            }
            final Object caretaker = caretakers.getFirst();
            if (caretaker instanceof si.result.project.eearly.model.caretaker.Caretaker typedCaretaker) {
                return uuidValue(typedCaretaker.getId());
            }
        } catch (RuntimeException ignored) {
            return null;
        }

        return null;
    }

    private String firstStartDateTime(final List<SelectionWindow> selectionWindows) {
        return selectionWindows.stream()
                .map(SelectionWindow::startDateTime)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String lastEndDateTime(final List<SelectionWindow> selectionWindows) {
        return selectionWindows.stream()
                .map(SelectionWindow::endDateTime)
                .filter(value -> value != null && !value.isBlank())
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private String latestRecordTimestamp(final List<? extends BiometricRecord<?>> records) {
        if (records == null) {
            return null;
        }

        return records.stream()
                .map(BiometricRecord::getTimestamp)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .map(timestamp -> timestamp.toOffsetDateTime().format(OFFSET_DATE_TIME_WITH_SECONDS))
                .orElse(null);
    }

    private record SelectionWindow(
            String startDateTime,
            String endDateTime,
            List<UUID> measurementIds
    ) {
    }

    private JsonNode constantPropertyValue(final JsonNode propertyNode) {
        if (propertyNode == null || propertyNode.isMissingNode() || propertyNode.isNull()) {
            return null;
        }

        if (propertyNode.isValueNode() || propertyNode.isArray()) {
            return propertyNode;
        }

        final JsonNode constNode = propertyNode.get("const");
        if (constNode != null && !constNode.isNull()) {
            return constNode;
        }

        final JsonNode defaultNode = propertyNode.get("default");
        if (defaultNode != null && !defaultNode.isNull()) {
            return defaultNode;
        }

        final JsonNode valueNode = propertyNode.get(VALUE_PROPERTY);
        if (valueNode != null && !valueNode.isNull()) {
            return valueNode;
        }

        return null;
    }

    private enum RecordShape {
        RECORD,
        DATA_POINT
    }

    public record PreparedMeasurementData(
            Measurement measurement,
            String unit,
            List<? extends BiometricRecord<?>> records
    ) {
    }

    private record BuildContext(
            Patient patient,
            Algorithm algorithm,
            List<SelectionWindow> selectionWindows,
            UUID analysisId,
            String algorithmConfigJson,
            String callbackUrl,
            String currentContainerName,
            JsonNode currentContainerSchema,
            Measurement measurement,
            List<? extends BiometricRecord<?>> records,
            BiometricRecord<?> biometricRecord,
            RecordShape recordShape,
            String measurementCollectionPropertyName
    ) {
        private static BuildContext root(
                final Patient patient,
                final Algorithm algorithm,
                final List<SelectionWindow> selectionWindows,
                final UUID analysisId,
                final String algorithmConfigJson,
                final String callbackUrl
        ) {
            return new BuildContext(
                    patient,
                    algorithm,
                    selectionWindows,
                    analysisId,
                    algorithmConfigJson,
                    callbackUrl,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    MEASUREMENTS_PROPERTY
            );
        }

        private BuildContext withContainer(final String containerName, final JsonNode containerSchema) {
            return new BuildContext(
                    patient,
                    algorithm,
                    selectionWindows,
                    analysisId,
                    algorithmConfigJson,
                    callbackUrl,
                    containerName,
                    containerSchema,
                    measurement,
                    records,
                    biometricRecord,
                    recordShape,
                    measurementCollectionPropertyName
            );
        }

        private BuildContext withMeasurement(
                final Measurement measurement,
                final List<? extends BiometricRecord<?>> records
        ) {
            return new BuildContext(
                    patient,
                    algorithm,
                    selectionWindows,
                    analysisId,
                    algorithmConfigJson,
                    callbackUrl,
                    currentContainerName,
                    currentContainerSchema,
                    measurement,
                    records,
                    null,
                    null,
                    currentContainerName
            );
        }

        private BuildContext withRecord(final BiometricRecord<?> biometricRecord, final RecordShape recordShape) {
            return new BuildContext(
                    patient,
                    algorithm,
                    selectionWindows,
                    analysisId,
                    algorithmConfigJson,
                    callbackUrl,
                    currentContainerName,
                    currentContainerSchema,
                    measurement,
                    records,
                    biometricRecord,
                    recordShape,
                    measurementCollectionPropertyName
            );
        }
    }
}
