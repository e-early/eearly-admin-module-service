package si.result.project.eearly.facade.patient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PagedModel;
import si.result.project.eearly.dto.patient.PatientDTO;
import si.result.project.eearly.dto.patient.PatientLiteDTO;
import si.result.project.eearly.dto.patient.PatientMinimalDTO;
import si.result.project.eearly.dto.patient.PatientOnboardingDTO;
import si.result.project.eearly.dto.patient.PatientUpsertDTO;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.mapper.patient.PatientMapper;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.measurement.Measurement;
import si.result.project.eearly.model.measurement.MeasurementStatus;
import si.result.project.eearly.model.mobile.MobileUserResponse;
import si.result.project.eearly.model.patient.Patient;
import si.result.project.eearly.model.patient.PatientStatus;
import si.result.project.eearly.model.patient.command.CreatePatientCommand;
import si.result.project.eearly.port.algorithm.AlgorithmService;
import si.result.project.eearly.port.caretaker.CaretakerService;
import si.result.project.eearly.port.measurement.MeasurementService;
import si.result.project.eearly.port.mobile.MobileKeycloakService;
import si.result.project.eearly.port.mobile.MobileServiceService;
import si.result.project.eearly.port.patient.PatientMeasurementClient;
import si.result.project.eearly.port.patient.PatientService;
import si.result.rest.filter.Filter;
import si.result.spring.boot.bricks.exception.DomainException;
import si.result.project.eearly.model.mobile.MobileUser;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientServiceFacadeTest {

    @Mock
    private PatientService patientService;
    @Mock
    private PatientMapper patientMapper;
    @Mock
    private CaretakerService caretakerService;
    @Mock
    private MeasurementService measurementService;
    @Mock
    private AlgorithmService algorithmService;
    @Mock
    private Filter filter;
    @Mock
    private Pageable pageable;
    @Mock
    private MobileKeycloakService mobileKeycloakService;
    @Mock
    private MobileServiceService mobileServiceService;

    @InjectMocks
    private PatientServiceFacade patientServiceFacade;

    private Patient testPatient;
    private UUID testId;
    private PatientUpsertDTO patientUpsertDTO;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();
        testPatient = createTestPatient();
        patientUpsertDTO = createTestPatientUpsertDTO();
    }

    @Nested
    @DisplayName("Get Page - Virtual Field Sorting")
    class GetPageTest {

        @Test
        @DisplayName("Should return normal page when no virtual field sorting")
        void getPage_WithoutVirtualFieldSorting_ShouldReturnNormalPage() {
            List<Patient> patients = List.of(testPatient);
            Page<Patient> patientPage = new PageImpl<>(patients);
            PatientLiteDTO liteDTO = mock(PatientLiteDTO.class);
            
            @SuppressWarnings("unchecked")
            Specification<Object> specification = mock(Specification.class);
            when(filter.toSpecification()).thenReturn(specification);
            when(pageable.getSort()).thenReturn(Sort.by("firstName"));
            when(patientService.findAll(any(), any(Pageable.class))).thenReturn(patientPage);
            when(patientMapper.toLiteDTO(any(Patient.class), any(PatientStatus.class), any(MeasurementStatus.class))).thenReturn(liteDTO);

            PagedModel<PatientLiteDTO> result = patientServiceFacade.getPage(filter, pageable);

            assertNotNull(result);
            verify(patientService).findAll(any(), any(Pageable.class));
            verify(patientMapper).toLiteDTO(any(Patient.class), any(PatientStatus.class), any(MeasurementStatus.class));
        }

        @Test
        @DisplayName("Should handle virtual field sorting for patientStatus")
        void getPage_WithPatientStatusSorting_ShouldHandleVirtualFieldSorting() {
            List<Patient> patients = List.of(testPatient);
            Page<Patient> patientPage = new PageImpl<>(patients);
            PatientLiteDTO liteDTO = mock(PatientLiteDTO.class);
            
            @SuppressWarnings("unchecked")
            Specification<Object> specification = mock(Specification.class);
            when(filter.toSpecification()).thenReturn(specification);
            when(pageable.getSort()).thenReturn(Sort.by("patientStatus"));
            when(pageable.getPageNumber()).thenReturn(0);
            when(pageable.getPageSize()).thenReturn(10);
            when(pageable.getOffset()).thenReturn(0L);
            when(patientService.findAll(any(), any(Pageable.class))).thenReturn(patientPage);
            when(patientMapper.toLiteDTO(any(Patient.class), any(PatientStatus.class), any(MeasurementStatus.class))).thenReturn(liteDTO);

            PagedModel<PatientLiteDTO> result = patientServiceFacade.getPage(filter, pageable);

            assertNotNull(result);
            verify(patientService).findAll(any(), any(Pageable.class));
        }

        @Test
        @DisplayName("Should handle virtual field sorting for measurementStatus")
        void getPage_WithMeasurementStatusSorting_ShouldHandleVirtualFieldSorting() {
            List<Patient> patients = List.of(testPatient);
            Page<Patient> patientPage = new PageImpl<>(patients);
            PatientLiteDTO liteDTO = mock(PatientLiteDTO.class);
            
            @SuppressWarnings("unchecked")
            Specification<Object> specification = mock(Specification.class);
            when(filter.toSpecification()).thenReturn(specification);
            when(pageable.getSort()).thenReturn(Sort.by("measurementStatus"));
            when(pageable.getPageNumber()).thenReturn(0);
            when(pageable.getPageSize()).thenReturn(10);
            when(pageable.getOffset()).thenReturn(0L);
            when(patientService.findAll(any(), any(Pageable.class))).thenReturn(patientPage);
            when(patientMapper.toLiteDTO(any(Patient.class), any(PatientStatus.class), any(MeasurementStatus.class))).thenReturn(liteDTO);

            PagedModel<PatientLiteDTO> result = patientServiceFacade.getPage(filter, pageable);

            assertNotNull(result);
            verify(patientService).findAll(any(), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("Random Status Generation")
    class RandomStatusTest {

        @Test
        @DisplayName("Should generate random patient status")
        void getRandomPatientStatus_ShouldReturnValidStatus() {
            PatientStatus status = patientServiceFacade.getRandomPatientStatus();
            
            assertNotNull(status);
            assertTrue(List.of(PatientStatus.values()).contains(status));
        }

        @Test
        @DisplayName("Should generate random measurement status")
        void getRandomMeasurementStatus_ShouldReturnValidStatus() {
            MeasurementStatus status = patientServiceFacade.getRandomMeasurementStatus();
            
            assertNotNull(status);
            assertTrue(List.of(MeasurementStatus.values()).contains(status));
        }
    }

    @Nested
    @DisplayName("Get By ID")
    class GetByIdTest {

        @Test
        @DisplayName("Should return patient by ID")
        void getById_ShouldReturnPatient() {
            PatientDTO patientDTO = mock(PatientDTO.class);

            when(patientService.findById(testId)).thenReturn(testPatient);
            when(patientMapper.toDTO(testPatient)).thenReturn(patientDTO);

            PatientDTO result = patientServiceFacade.getById(testId);

            assertNotNull(result);
            assertEquals(patientDTO, result);
            verify(patientService).findById(testId);
            verify(patientMapper).toDTO(testPatient);
        }
    }

    @Nested
    @DisplayName("Create Patient")
    class CreatePatientTest {

        @Test
        @DisplayName("Should create patient with all relations")
        void create_WithAllRelations_ShouldCreatePatient() {
            List<UUID> caretakerIds = List.of(UUID.randomUUID());
            List<UUID> measurementIds = List.of(UUID.randomUUID());
            List<UUID> algorithmIds = List.of(UUID.randomUUID());
            UUID testKeycloakId = UUID.randomUUID();

            when(patientUpsertDTO.caretakerList()).thenReturn(caretakerIds);
            when(patientUpsertDTO.measurementList()).thenReturn(measurementIds);
            when(patientUpsertDTO.algorithmsList()).thenReturn(algorithmIds);
            when(patientUpsertDTO.healthInsuranceId()).thenReturn("123");

            MobileUser mobileUser = new MobileUser(
                    testKeycloakId.toString(),
                    "Test",
                    "User",
                    "test@example.com",
                    "testuser"
            );

            when(mobileServiceService.createUser(any(), any())).thenReturn(
                    new MobileUserResponse(
                            mobileUser,
                            "http://www.onboarding.com/api/v1/onboarding/345783498rfwdh8fg9sef9"));

            when(mobileKeycloakService.getAccessTokenForClient()).thenReturn("54wh4hRandomsge5t234r3qwKeyrfwef");
            when(patientService.findByHealthInsuranceId("123")).thenReturn(null);
            when(caretakerService.findAllById(caretakerIds)).thenReturn(List.of(mock(Caretaker.class)));
            when(measurementService.findAllById(measurementIds)).thenReturn(List.of(mock(Measurement.class)));
            when(algorithmService.findAllById(algorithmIds)).thenReturn(List.of(mock(Algorithm.class)));
            when(patientMapper.toCreateCommand(any(), any(), any(), any())).thenReturn(mock(CreatePatientCommand.class));
            when(patientService.create(any())).thenReturn(testPatient);

            when(testPatient.getId()).thenReturn(testId);
            when(patientService.updateKeycloakId(testId, testKeycloakId)).thenReturn(testPatient);

            when(patientMapper.toOnboardingDTO(eq(testPatient), anyString()))
                    .thenReturn(new PatientOnboardingDTO(testId, "http://www.onboarding.com/api/v1/onboarding/345783498rfwdh8fg9sef9"));

            PatientOnboardingDTO result = patientServiceFacade.create(patientUpsertDTO);

            assertNotNull(result);
            assertNotNull(result.onboardingUrl());
            verify(patientService).findByHealthInsuranceId("123");
            verify(caretakerService).findAllById(caretakerIds);
            verify(measurementService).findAllById(measurementIds);
            verify(algorithmService).findAllById(algorithmIds);
            verify(patientService).create(any());
            verify(patientService).updateKeycloakId(testId, testKeycloakId);
        }

        @Test
        @DisplayName("Should throw exception when health insurance ID is null")
        void create_WhenHealthInsuranceIdNull_ShouldThrowException() {
            when(patientUpsertDTO.healthInsuranceId()).thenReturn(null);

            DomainException exception = assertThrows(DomainException.class,
                () -> patientServiceFacade.create(patientUpsertDTO));

            assertEquals(DomainExceptionCode.PATIENT_HEALTH_INSURANCE_ID_NULL, exception.getCode());
        }

        @Test
        @DisplayName("Should throw exception when health insurance ID already exists")
        void create_WhenHealthInsuranceIdExists_ShouldThrowException() {
            when(patientUpsertDTO.healthInsuranceId()).thenReturn("123");
            when(patientService.findByHealthInsuranceId("123")).thenReturn(testPatient);

            DomainException exception = assertThrows(DomainException.class,
                () -> patientServiceFacade.create(patientUpsertDTO));

            assertEquals(DomainExceptionCode.PATIENT_HEALTH_INSURANCE_ID_FOUND, exception.getCode());
        }

        @Test
        @DisplayName("Should throw exception when caretaker not found")
        void create_WhenCaretakerNotFound_ShouldThrowException() {
            List<UUID> caretakerIds = List.of(UUID.randomUUID());
            
            when(patientUpsertDTO.caretakerList()).thenReturn(caretakerIds);
            when(patientUpsertDTO.healthInsuranceId()).thenReturn("123");
            when(patientService.findByHealthInsuranceId("123")).thenReturn(null);
            when(caretakerService.findAllById(caretakerIds)).thenReturn(List.of());

            DomainException exception = assertThrows(DomainException.class,
                () -> patientServiceFacade.create(patientUpsertDTO));

            assertEquals(DomainExceptionCode.CARETAKER_NOT_FOUND, exception.getCode());
        }

        @Test
        @DisplayName("Should throw exception when measurement not found")
        void create_WhenMeasurementNotFound_ShouldThrowException() {
            List<UUID> measurementIds = List.of(UUID.randomUUID());
            
            when(patientUpsertDTO.measurementList()).thenReturn(measurementIds);
            when(patientUpsertDTO.healthInsuranceId()).thenReturn("123");
            when(patientService.findByHealthInsuranceId("123")).thenReturn(null);
            when(measurementService.findAllById(measurementIds)).thenReturn(List.of());

            DomainException exception = assertThrows(DomainException.class,
                () -> patientServiceFacade.create(patientUpsertDTO));

            assertEquals(DomainExceptionCode.MEASUREMENT_NOT_FOUND, exception.getCode());
        }

        @Test
        @DisplayName("Should throw exception when algorithm not found")
        void create_WhenAlgorithmNotFound_ShouldThrowException() {
            List<UUID> algorithmIds = List.of(UUID.randomUUID());
            
            when(patientUpsertDTO.algorithmsList()).thenReturn(algorithmIds);
            when(patientUpsertDTO.healthInsuranceId()).thenReturn("123");
            when(patientService.findByHealthInsuranceId("123")).thenReturn(null);
            when(algorithmService.findAllById(algorithmIds)).thenReturn(List.of());

            DomainException exception = assertThrows(DomainException.class,
                () -> patientServiceFacade.create(patientUpsertDTO));

            assertEquals(DomainExceptionCode.ALGORITHM_NOT_FOUND, exception.getCode());
        }
    }

    @Nested
    @DisplayName("Update Patient")
    class UpdatePatientTest {

        @Test
        @DisplayName("Should update patient with all relations")
        void update_WithAllRelations_ShouldUpdatePatient() {
            PatientMinimalDTO minimalDTO = mock(PatientMinimalDTO.class);
            List<UUID> caretakerIds = List.of(UUID.randomUUID());
            List<UUID> measurementIds = List.of(UUID.randomUUID());
            List<UUID> algorithmIds = List.of(UUID.randomUUID());

            when(patientUpsertDTO.caretakerList()).thenReturn(caretakerIds);
            when(patientUpsertDTO.measurementList()).thenReturn(measurementIds);
            when(patientUpsertDTO.algorithmsList()).thenReturn(algorithmIds);

            when(caretakerService.findAllById(caretakerIds)).thenReturn(List.of(mock(Caretaker.class)));
            when(measurementService.findAllById(measurementIds)).thenReturn(List.of(mock(Measurement.class)));
            when(algorithmService.findAllById(algorithmIds)).thenReturn(List.of(mock(Algorithm.class)));
            when(patientMapper.toUpdateCommand(any(UUID.class), any(), any(), any(), any())).thenReturn(mock(si.result.project.eearly.model.patient.command.UpdatePatientCommand.class));
            when(patientService.update(any())).thenReturn(testPatient);
            when(patientMapper.toMinimalDTO(testPatient)).thenReturn(minimalDTO);

            PatientMinimalDTO result = patientServiceFacade.update(testId, patientUpsertDTO);

            assertNotNull(result);
            assertEquals(minimalDTO, result);
            verify(caretakerService).findAllById(caretakerIds);
            verify(measurementService).findAllById(measurementIds);
            verify(algorithmService).findAllById(algorithmIds);
            verify(patientService).update(any());
            verify(patientMapper).toMinimalDTO(testPatient);
        }
    }

    private Patient createTestPatient() {
        return mock(Patient.class);
    }

    private PatientUpsertDTO createTestPatientUpsertDTO() {
        return mock(PatientUpsertDTO.class);
    }
}
