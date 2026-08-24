package si.result.project.eearly.facade.algorithm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import si.result.project.eearly.dto.algorithm.AlgorithmDTO;
import si.result.project.eearly.dto.algorithm.AlgorithmUpsertDTO;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.mapper.algorithm.AlgorithmMapper;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.measurement.Measurement;
import si.result.project.eearly.model.patient.Patient;
import si.result.project.eearly.port.algorithm.AlgorithmService;
import si.result.project.eearly.port.measurement.MeasurementService;
import si.result.project.eearly.port.patient.PatientService;
import si.result.spring.boot.bricks.exception.DomainException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlgorithmServiceFacadeTest {

    @Mock
    private AlgorithmService algorithmService;
    @Mock
    private PatientService patientService;
    @Mock
    private MeasurementService measurementService;
    @Mock
    private AlgorithmMapper algorithmMapper;

    @InjectMocks
    private AlgorithmServiceFacade algorithmServiceFacade;

    private UUID algorithmId;
    private AlgorithmUpsertDTO upsertDTO;
    private Algorithm algorithm;
    private List<Patient> patients;
    private List<Measurement> measurements;

    @BeforeEach
    void setUp() {
        algorithmId = UUID.randomUUID();
        upsertDTO = mock(AlgorithmUpsertDTO.class);
        algorithm = mock(Algorithm.class);
        patients = List.of(mock(Patient.class));
        measurements = List.of(mock(Measurement.class));
    }

    @Nested
    @DisplayName("Create Algorithm")
    class CreateAlgorithmTest {

        @Test
        @DisplayName("Should create algorithm successfully")
        void create_ShouldReturnUpsertDTO() {
            when(upsertDTO.patientList()).thenReturn(List.of(UUID.randomUUID()));
            when(upsertDTO.measurementList()).thenReturn(List.of(UUID.randomUUID()));

            when(patientService.findAllById(anyList())).thenReturn(patients);
            when(measurementService.findAllById(anyList())).thenReturn(measurements);
            when(algorithmService.create(any())).thenReturn(algorithm);
            when(algorithmMapper.toDTO(algorithm)).thenReturn(mock(AlgorithmDTO.class));

            AlgorithmDTO result = algorithmServiceFacade.create(upsertDTO);

            assertNotNull(result);
            verify(patientService).findAllById(anyList());
            verify(measurementService).findAllById(anyList());
            verify(algorithmMapper).toCreateCommand(eq(upsertDTO), eq(patients), eq(measurements));
            verify(algorithmService).create(any());
            verify(algorithmMapper).toDTO(algorithm);
        }

        @Test
        @DisplayName("Should throw DomainException when patient not found")
        void create_WhenPatientNotFound_ShouldThrow() {
            when(upsertDTO.patientList()).thenReturn(List.of(UUID.randomUUID()));

            when(patientService.findAllById(anyList())).thenReturn(List.of());
            DomainException ex = assertThrows(DomainException.class, () -> algorithmServiceFacade.create(upsertDTO));
            assertEquals(DomainExceptionCode.PATIENT_NOT_FOUND, ex.getCode());
        }

        @Test
        @DisplayName("Should throw DomainException when measurement not found")
        void create_WhenMeasurementNotFound_ShouldThrow() {
            when(upsertDTO.patientList()).thenReturn(List.of(UUID.randomUUID()));
            when(upsertDTO.measurementList()).thenReturn(List.of(UUID.randomUUID()));

            when(patientService.findAllById(anyList())).thenReturn(patients);
            when(measurementService.findAllById(anyList())).thenReturn(List.of());

            DomainException ex = assertThrows(DomainException.class, () -> algorithmServiceFacade.create(upsertDTO));
            assertEquals(DomainExceptionCode.MEASUREMENT_NOT_FOUND, ex.getCode());

            verify(patientService).findAllById(anyList());
            verify(measurementService).findAllById(anyList());
        }
    }

    @Nested
    @DisplayName("Update Algorithm")
    class UpdateAlgorithmTest {
        @Test
        @DisplayName("Should update algorithm successfully")
        void update_ShouldReturnUpsertDTO() {
            when(upsertDTO.id()).thenReturn(algorithmId);

            when(upsertDTO.patientList()).thenReturn(List.of(UUID.randomUUID()));
            when(upsertDTO.measurementList()).thenReturn(List.of(UUID.randomUUID()));

            when(patientService.findAllById(anyList())).thenReturn(patients);
            when(measurementService.findAllById(anyList())).thenReturn(measurements);

            when(algorithmMapper.toUpdateCommand(eq(upsertDTO), eq(patients), eq(measurements)))
                    .thenReturn(mock(si.result.project.eearly.model.algorithm.command.UpdateAlgorithmCommand.class));
            when(algorithmService.update(any())).thenReturn(algorithm);
            when(algorithmMapper.toUpsertDTO(algorithm)).thenReturn(upsertDTO);

            AlgorithmUpsertDTO result = algorithmServiceFacade.update(upsertDTO);

            assertNotNull(result);
            verify(patientService).findAllById(anyList());
            verify(measurementService).findAllById(anyList());
            verify(algorithmMapper).toUpdateCommand(eq(upsertDTO), eq(patients), eq(measurements));
            verify(algorithmService).update(any());
            verify(algorithmMapper).toUpsertDTO(algorithm);
        }

        @Test
        @DisplayName("Should throw DomainException when id is null")
        void update_WhenIdNull_ShouldThrow() {
            when(upsertDTO.id()).thenReturn(null);
            DomainException ex = assertThrows(DomainException.class, () -> algorithmServiceFacade.update(upsertDTO));
            assertEquals(DomainExceptionCode.ALGORITHM_NOT_FOUND, ex.getCode());
        }

        @Test
        @DisplayName("Should throw DomainException when patient not found")
        void update_WhenPatientNotFound_ShouldThrow() {
            when(upsertDTO.id()).thenReturn(algorithmId);

            when(upsertDTO.patientList()).thenReturn(List.of(UUID.randomUUID()));

            when(patientService.findAllById(anyList())).thenReturn(List.of());
            DomainException ex = assertThrows(DomainException.class, () -> algorithmServiceFacade.update(upsertDTO));
            assertEquals(DomainExceptionCode.PATIENT_NOT_FOUND, ex.getCode());
        }

        @Test
        @DisplayName("Should throw DomainException when measurement not found")
        void update_WhenMeasurementNotFound_ShouldThrow() {
            when(upsertDTO.id()).thenReturn(algorithmId);

            when(upsertDTO.patientList()).thenReturn(List.of(UUID.randomUUID()));
            when(upsertDTO.measurementList()).thenReturn(List.of(UUID.randomUUID()));

            when(patientService.findAllById(anyList())).thenReturn(patients);
            when(measurementService.findAllById(anyList())).thenReturn(List.of()); // triggers exception

            DomainException ex = assertThrows(DomainException.class, () -> algorithmServiceFacade.update(upsertDTO));
            assertEquals(DomainExceptionCode.MEASUREMENT_NOT_FOUND, ex.getCode());

            verify(patientService).findAllById(anyList());
            verify(measurementService).findAllById(anyList());
        }
    }

    @Nested
    @DisplayName("Update Algorithm with real mapper")
    class UpdateWithMapperTest {
        private final AlgorithmMapper realMapper = org.mapstruct.factory.Mappers.getMapper(AlgorithmMapper.class);
        private AlgorithmUpsertDTO upsertDTO;


        @Test
        @DisplayName("Should call mapper and validate JSON successfully")
        void update_ShouldCallMapperAndValidateJson() {
            upsertDTO = new AlgorithmUpsertDTO(
                    UUID.randomUUID(),
                    "Test Algorithm",
                    "Algorithm description",
                    "1.0.0",
                    "CARDIOVASCULAR",
                    "DRAFT",
                    "ecg-classifier",
                    "v1.0.0",
                    "http://algorithm-api-host:3000",
                    "/run",
                    "/tasks/{taskId}",
                    "/healthz",
                    "{\"workers\": 2}",
                    "{\"type\":\"input\"}",
                    "{\"type\":\"output\"}",
                    "ACME Corp",
                    "FREE",
                    0.0,
                    "EUR",
                    "BILL-001",
                    "Commercial",
                    List.of(UUID.randomUUID()),
                    List.of(UUID.randomUUID()),
            );

            List<Patient> patients = List.of(mock(Patient.class));
            List<Measurement> measurements = List.of(mock(Measurement.class));
            Algorithm algorithm = mock(Algorithm.class);

            when(patientService.findAllById(anyList())).thenReturn(patients);
            when(measurementService.findAllById(anyList())).thenReturn(measurements);
            when(algorithmService.update(any())).thenReturn(algorithm);

            // Call the real mapper for toUpdateCommand
            var updateCommand = realMapper.toUpdateCommand(upsertDTO, patients, measurements);
            assertNotNull(updateCommand);

            // Pass it to the service facade
            when(algorithmMapper.toUpdateCommand(any(), any(), any())).thenReturn(updateCommand);
            when(algorithmMapper.toUpsertDTO(algorithm)).thenReturn(upsertDTO);

            AlgorithmUpsertDTO result = algorithmServiceFacade.update(upsertDTO);

            assertNotNull(result);
            assertEquals(upsertDTO.inputSchema(), result.inputSchema());
            assertEquals(upsertDTO.outputSchema(), result.outputSchema());

            verify(patientService).findAllById(anyList());
            verify(measurementService).findAllById(anyList());
            verify(algorithmService).update(any());
        }

        @Test
        @DisplayName("Should throw DomainException on invalid input JSON")
        void update_ShouldThrowOnInvalidInputJson() {
            upsertDTO = new AlgorithmUpsertDTO(
                    UUID.randomUUID(),
                    "Test Algorithm",
                    "Algorithm description",
                    "1.0.0",
                    "CARDIOVASCULAR",
                    "DRAFT",
                    "ecg-classifier",
                    "v1.0.0",
                    "http://algorithm-api-host:3000",
                    "/run",
                    "/tasks/{taskId}",
                    "/healthz",
                    "{\"workers\": 2}",
                    "INVALID_JSON",
                    "{\"type\":\"output\"}",
                    "ACME Corp",
                    "FREE",
                    0.0,
                    "EUR",
                    "BILL-001",
                    "Commercial",
                    List.of(UUID.randomUUID()),
                    List.of(UUID.randomUUID()),
            );

            List<Patient> patients = List.of(mock(Patient.class));
            List<Measurement> measurements = List.of(mock(Measurement.class));

            // Call real mapper and trigger validation
            assertThrows(DomainException.class, () -> realMapper.toUpdateCommand(upsertDTO, patients, measurements));
        }

        @Test
        @DisplayName("Should throw DomainException on invalid output JSON")
        void update_ShouldThrowOnInvalidOutputJson() {
            upsertDTO = new AlgorithmUpsertDTO(
                    UUID.randomUUID(),
                    "Test Algorithm",
                    "Algorithm description",
                    "1.0.0",
                    "CARDIOVASCULAR",
                    "DRAFT",
                    "ecg-classifier",
                    "v1.0.0",
                    "http://algorithm-api-host:3000",
                    "/run",
                    "/tasks/{taskId}",
                    "/healthz",
                    "{\"workers\": 2}",
                    "{\"type\":\"input\"}",
                    "INVALID_JSON",
                    "ACME Corp",
                    "FREE",
                    0.0,
                    "EUR",
                    "BILL-001",
                    "Commercial",
                    List.of(UUID.randomUUID()),
                    List.of(UUID.randomUUID()),
            );

            List<Patient> patients = List.of(mock(Patient.class));
            List<Measurement> measurements = List.of(mock(Measurement.class));

            // Call real mapper and trigger validation
            assertThrows(DomainException.class, () -> realMapper.toUpdateCommand(upsertDTO, patients, measurements));
        }

        @Test
        @DisplayName("Should throw DomainException on invalid category")
        void toUpdateCommand_ShouldThrowOnInvalidCategory() {
            AlgorithmUpsertDTO invalidCategoryDTO = new AlgorithmUpsertDTO(
                    UUID.randomUUID(),
                    "Test Algorithm",
                    "Algorithm description",
                    "1.0.0",
                    "INVALID_CATEGORY",
                    "DRAFT",
                    "ecg-classifier",
                    "v1.0.0",
                    "http://algorithm-api-host:3000",
                    "/run",
                    "/tasks/{taskId}",
                    "/healthz",
                    "{\"workers\": 2}",
                    "{\"type\":\"input\"}",
                    "{\"type\":\"output\"}",
                    "ACME Corp",
                    "FREE",
                    0.0,
                    "EUR",
                    "BILL-001",
                    "Commercial",
                    List.of(UUID.randomUUID()),
                    List.of(UUID.randomUUID()),
            );

            List<Patient> patients = List.of(mock(Patient.class));
            List<Measurement> measurements = List.of(mock(Measurement.class));

            DomainException ex = assertThrows(DomainException.class, () ->
                    realMapper.toUpdateCommand(invalidCategoryDTO, patients, measurements));

            assertEquals(DomainExceptionCode.ALGORITHM_INVALID_CATEGORY, ex.getCode());
        }

        @Test
        @DisplayName("Should throw DomainException on invalid status")
        void toUpdateCommand_ShouldThrowOnInvalidStatus() {
            AlgorithmUpsertDTO invalidStatusDTO = new AlgorithmUpsertDTO(
                    UUID.randomUUID(),
                    "Test Algorithm",
                    "Algorithm description",
                    "1.0.0",
                    "CARDIOVASCULAR",
                    "INVALID_STATUS",
                    "ecg-classifier",
                    "v1.0.0",
                    "http://algorithm-api-host:3000",
                    "/run",
                    "/tasks/{taskId}",
                    "/healthz",
                    "{\"workers\": 2}",
                    "{\"type\":\"input\"}",
                    "{\"type\":\"output\"}",
                    "ACME Corp",
                    "FREE",
                    0.0,
                    "EUR",
                    "BILL-001",
                    "Commercial",
                    List.of(UUID.randomUUID()),
                    List.of(UUID.randomUUID()),
            );

            List<Patient> patients = List.of(mock(Patient.class));
            List<Measurement> measurements = List.of(mock(Measurement.class));

            DomainException ex = assertThrows(DomainException.class, () ->
                    realMapper.toUpdateCommand(invalidStatusDTO, patients, measurements));

            assertEquals(DomainExceptionCode.ALGORITHM_INVALID_STATUS, ex.getCode());
        }

        @Test
        @DisplayName("Should throw DomainException on invalid pricing model")
        void toUpdateCommand_ShouldThrowOnInvalidPricingModel() {
            AlgorithmUpsertDTO invalidPricingDTO = new AlgorithmUpsertDTO(
                    UUID.randomUUID(),
                    "Test Algorithm",
                    "Algorithm description",
                    "1.0.0",
                    "CARDIOVASCULAR",
                    "DRAFT",
                    "ecg-classifier",
                    "v1.0.0",
                    "http://algorithm-api-host:3000",
                    "/run",
                    "/tasks/{taskId}",
                    "/healthz",
                    "{\"workers\": 2}",
                    "{\"type\":\"input\"}",
                    "{\"type\":\"output\"}",
                    "ACME Corp",
                    "INVALID_PRICING",
                    0.0,
                    "EUR",
                    "BILL-001",
                    "Commercial",
                    List.of(UUID.randomUUID()),
                    List.of(UUID.randomUUID()),
            );

            List<Patient> patients = List.of(mock(Patient.class));
            List<Measurement> measurements = List.of(mock(Measurement.class));

            DomainException ex = assertThrows(DomainException.class, () ->
                    realMapper.toUpdateCommand(invalidPricingDTO, patients, measurements));

            assertEquals(DomainExceptionCode.ALGORITHM_INVALID_PRICING_MODEL, ex.getCode());
        }

        @Test
        @DisplayName("Should throw DomainException on invalid currency")
        void toUpdateCommand_ShouldThrowOnInvalidCurrency() {
            AlgorithmUpsertDTO invalidCurrencyDTO = new AlgorithmUpsertDTO(
                    UUID.randomUUID(),
                    "Test Algorithm",
                    "Algorithm description",
                    "1.0.0",
                    "CARDIOVASCULAR",
                    "DRAFT",
                    "ecg-classifier",
                    "v1.0.0",
                    "http://algorithm-api-host:3000",
                    "/run",
                    "/tasks/{taskId}",
                    "/healthz",
                    "{\"workers\": 2}",
                    "{\"type\":\"input\"}",
                    "{\"type\":\"output\"}",
                    "ACME Corp",
                    "FREE",
                    0.0,
                    "INVALID",
                    "BILL-001",
                    "Commercial",
                    List.of(UUID.randomUUID()),
                    List.of(UUID.randomUUID()),
            );

            List<Patient> patients = List.of(mock(Patient.class));
            List<Measurement> measurements = List.of(mock(Measurement.class));

            DomainException ex = assertThrows(DomainException.class, () ->
                    realMapper.toUpdateCommand(invalidCurrencyDTO, patients, measurements));

            assertEquals(DomainExceptionCode.ALGORITHM_INVALID_CURRENCY, ex.getCode());
        }

        @Test
        @DisplayName("Should throw when cost is negative")
        void toUpdateCommand_ShouldThrowOnNegativeCost() {
            AlgorithmUpsertDTO dto = new AlgorithmUpsertDTO(
                    UUID.randomUUID(),
                    "Test Algorithm",
                    "Algorithm description",
                    "1.0.0",
                    "CARDIOVASCULAR",
                    "DRAFT",
                    "ecg-classifier",
                    "v1.0.0",
                    "http://algorithm-api-host:3000",
                    "/run",
                    "/tasks/{taskId}",
                    "/healthz",
                    "{\"workers\": 2}",
                    "{\"type\":\"input\"}",
                    "{\"type\":\"output\"}",
                    "ACME Corp",
                    "PER_EXECUTION",
                    -5.0,
                    "EUR",
                    "BILL-001",
                    "Commercial",
                    List.of(UUID.randomUUID()),
                    List.of(UUID.randomUUID()),
            );

            List<Patient> patients = List.of(mock(Patient.class));
            List<Measurement> measurements = List.of(mock(Measurement.class));

            DomainException ex = assertThrows(DomainException.class, () ->
                    realMapper.toUpdateCommand(dto, patients, measurements));

            assertEquals(DomainExceptionCode.ALGORITHM_INVALID_COST, ex.getCode());
        }

        @Test
        @DisplayName("Should map runner config JSON successfully")
        void toUpdateCommand_ShouldMapRunnerConfig() {
            upsertDTO = new AlgorithmUpsertDTO(
                    UUID.randomUUID(),
                    "Test Algorithm",
                    "Algorithm description",
                    "1.0.0",
                    "CARDIOVASCULAR",
                    "DRAFT",
                    "ecg-classifier",
                    "v1.0.0",
                    "http://algorithm-api-host:3000",
                    "/run",
                    "/tasks/{taskId}",
                    "/healthz",
                    "{\"workers\": 2}",
                    "{\"type\":\"input\"}",
                    "{\"type\":\"output\"}",
                    "ACME Corp",
                    "FREE",
                    0.0,
                    "EUR",
                    "BILL-001",
                    "Commercial",
                    List.of(UUID.randomUUID()),
                    List.of(UUID.randomUUID()),
            );

            List<Patient> patients = List.of(mock(Patient.class));
            List<Measurement> measurements = List.of(mock(Measurement.class));

            var command = realMapper.toUpdateCommand(upsertDTO, patients, measurements);

            assertEquals("{\"workers\": 2}", command.runnerConfig());
        }
    }
}
