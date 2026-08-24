package si.result.project.eearly.port.patient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.gender.GenderType;
import si.result.project.eearly.model.patient.Patient;
import si.result.project.eearly.model.patient.command.CreatePatientCommand;
import si.result.project.eearly.model.patient.command.UpdatePatientCommand;
import si.result.spring.boot.bricks.exception.DomainException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock
    private PatientRepository patientRepository;

    @InjectMocks
    private PatientService patientService;

    private Patient testPatient;
    private UUID testId;
    private CreatePatientCommand createCommand;
    private UpdatePatientCommand updateCommand;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();
        testPatient = createTestPatient();
        createCommand = createTestCreateCommand();
        updateCommand = createTestUpdateCommand();
    }

    @Nested
    @DisplayName("Find by ID")
    class FindByIdTest {

        @Test
        @DisplayName("Should return patient when found")
        void findById_WhenPatientExists_ShouldReturnPatient() {
            when(patientRepository.findById(testId)).thenReturn(Optional.of(testPatient));

            Patient result = patientService.findById(testId);

            assertNotNull(result);
            assertEquals(testPatient, result);
            verify(patientRepository).findById(testId);
        }

        @Test
        @DisplayName("Should throw exception when patient not found")
        void findById_WhenPatientNotExists_ShouldThrowException() {
            when(patientRepository.findById(testId)).thenReturn(Optional.empty());

            DomainException exception = assertThrows(DomainException.class, 
                () -> patientService.findById(testId));
            
            assertEquals(DomainExceptionCode.PATIENT_NOT_FOUND, exception.getCode());
            verify(patientRepository).findById(testId);
        }
    }

    @Nested
    @DisplayName("Find all with search")
    class FindAllTest {

        @Test
        @DisplayName("Should return page of patients with specification")
        void findAll_WithSpecification_ShouldReturnPageOfPatients() {
            List<Patient> patients = List.of(testPatient);
            Page<Patient> expectedPage = new PageImpl<>(patients);
            
            @SuppressWarnings("unchecked")
            Specification<Patient> specification = mock(Specification.class);
            Pageable pageable = mock(Pageable.class);
            
            when(patientRepository.findAll(specification, pageable)).thenReturn(expectedPage);

            Page<Patient> result = patientService.findAll(specification, pageable);

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            assertEquals(testPatient, result.getContent().get(0));
            verify(patientRepository).findAll(specification, pageable);
        }

        @Test
        @DisplayName("Should return empty page when no patients found")
        void findAll_WhenNoPatients_ShouldReturnEmptyPage() {
            Page<Patient> emptyPage = new PageImpl<>(List.of());
            @SuppressWarnings("unchecked")
            Specification<Patient> specification = mock(Specification.class);
            Pageable pageable = mock(Pageable.class);
            
            when(patientRepository.findAll(specification, pageable)).thenReturn(emptyPage);

            Page<Patient> result = patientService.findAll(specification, pageable);

            assertNotNull(result);
            assertEquals(0, result.getTotalElements());
            assertTrue(result.getContent().isEmpty());
        }
    }

    @Nested
    @DisplayName("Find by health insurance ID")
    class FindByHealthInsuranceIdTest {

        @Test
        @DisplayName("Should return patient by health insurance ID")
        void findByHealthInsuranceId_ShouldReturnPatient() {
            String healthInsuranceId = "123456789";
            when(patientRepository.findByHealthInsuranceId(healthInsuranceId)).thenReturn(testPatient);

            Patient result = patientService.findByHealthInsuranceId(healthInsuranceId);

            assertNotNull(result);
            assertEquals(testPatient, result);
            verify(patientRepository).findByHealthInsuranceId(healthInsuranceId);
        }
    }

    @Nested
    @DisplayName("Create patient")
    class CreatePatientTest {

        @Test
        @DisplayName("Should create and save patient")
        void create_ShouldCreateAndSavePatient() {
            when(patientRepository.save(any(Patient.class))).thenReturn(testPatient);

            Patient result = patientService.create(createCommand);

            assertNotNull(result);
            verify(patientRepository).save(any(Patient.class));
        }
    }

    @Nested
    @DisplayName("Update patient")
    class UpdatePatientTest {

        @Test
        @DisplayName("Should update existing patient")
        void update_ShouldUpdateExistingPatient() {
            when(patientRepository.findById(testId)).thenReturn(Optional.of(testPatient));
            when(patientRepository.save(any(Patient.class))).thenReturn(testPatient);

            Patient result = patientService.update(updateCommand);

            assertNotNull(result);
            verify(patientRepository).findById(testId);
            verify(patientRepository).save(any(Patient.class));
        }

        @Test
        @DisplayName("Should throw exception when updating non-existent patient")
        void update_WhenPatientNotExists_ShouldThrowException() {
            when(patientRepository.findById(testId)).thenReturn(Optional.empty());

            DomainException exception = assertThrows(DomainException.class, 
                () -> patientService.update(updateCommand));
            
            assertEquals(DomainExceptionCode.PATIENT_NOT_FOUND, exception.getCode());
            verify(patientRepository).findById(testId);
            verify(patientRepository, never()).save(any(Patient.class));
        }
    }

    @Nested
    @DisplayName("Find all by IDs")
    class FindAllByIdTest {

        @Test
        @DisplayName("Should return patients by IDs")
        void findAllById_ShouldReturnPatients() {
            List<UUID> ids = List.of(testId, UUID.randomUUID());
            List<Patient> patients = List.of(testPatient);
            when(patientRepository.findAllById(ids)).thenReturn(patients);

            List<Patient> result = patientService.findAllById(ids);

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(testPatient, result.getFirst());
            verify(patientRepository).findAllById(ids);
        }
    }

    private Patient createTestPatient() {
        return Patient.create(createTestCreateCommand());
    }

    private CreatePatientCommand createTestCreateCommand() {
        return new CreatePatientCommand(
            "John", "Doe", LocalDate.of(1990, 1, 1), GenderType.MALE,
            "123456789", "Test Street", "Test City", "Test State", 1000,
            "Test Country", "john.doe@test.com", "+1234567890",
            null, null, null, null, null
        );
    }

    private UpdatePatientCommand createTestUpdateCommand() {
        return new UpdatePatientCommand(
            testId, "Jane", "Smith", LocalDate.of(1995, 5, 15), GenderType.FEMALE,
            "987654321", "Updated Street", "Updated City", "Updated State", 2000,
            "Updated Country", "jane.smith@test.com", "+0987654321",
            null, null, null, null, null
        );
    }
}
