package si.result.project.eearly.port.measurement;

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
import si.result.project.eearly.model.measurement.Measurement;
import si.result.project.eearly.model.measurement.MeasurementChartType;
import si.result.project.eearly.model.measurement.MeasurementType;
import si.result.project.eearly.model.measurement.command.CreateMeasurementCommand;
import si.result.project.eearly.model.measurement.command.UpdateMeasurementCommand;
import si.result.spring.boot.bricks.exception.DomainException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeasurementServiceTest {

    @Mock
    private MeasurementRepository measurementRepository;

    @InjectMocks
    private MeasurementService measurementService;

    private Measurement testMeasurement;
    private UUID testId;
    private CreateMeasurementCommand createCommand;
    private UpdateMeasurementCommand updateCommand;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();
        testMeasurement = createTestMeasurement();
        createCommand = createTestCreateCommand();
        updateCommand = createTestUpdateCommand();
    }

    @Nested
    @DisplayName("Find by ID")
    class FindByIdTest {

        @Test
        @DisplayName("Should return measurement when found")
        void findById_WhenMeasurementExists_ShouldReturnMeasurement() {
            when(measurementRepository.findById(testId)).thenReturn(Optional.of(testMeasurement));

            Measurement result = measurementService.findById(testId);

            assertNotNull(result);
            assertEquals(testMeasurement, result);
            verify(measurementRepository).findById(testId);
        }

        @Test
        @DisplayName("Should throw exception when measurement not found")
        void findById_WhenMeasurementNotExists_ShouldThrowException() {
            when(measurementRepository.findById(testId)).thenReturn(Optional.empty());

            DomainException exception = assertThrows(DomainException.class,
                () -> measurementService.findById(testId));

            assertEquals(DomainExceptionCode.MEASUREMENT_NOT_FOUND, exception.getCode());
            verify(measurementRepository).findById(testId);
        }
    }

    @Nested
    @DisplayName("Find all with search")
    class FindAllTest {

        @Test
        @DisplayName("Should return page of measurements with specification")
        void findAll_WithSpecification_ShouldReturnPageOfMeasurements() {
            List<Measurement> measurements = List.of(testMeasurement);
            Page<Measurement> expectedPage = new PageImpl<>(measurements);

            @SuppressWarnings("unchecked")
            Specification<Measurement> specification = mock(Specification.class);
            Pageable pageable = mock(Pageable.class);

            when(measurementRepository.findAll(specification, pageable)).thenReturn(expectedPage);

            Page<Measurement> result = measurementService.findAll(specification, pageable);

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            assertEquals(testMeasurement, result.getContent().get(0));
            verify(measurementRepository).findAll(specification, pageable);
        }

        @Test
        @DisplayName("Should return empty page when no measurements found")
        void findAll_WhenNoMeasurements_ShouldReturnEmptyPage() {
            Page<Measurement> emptyPage = new PageImpl<>(List.of());
            @SuppressWarnings("unchecked")
            Specification<Measurement> specification = mock(Specification.class);
            Pageable pageable = mock(Pageable.class);

            when(measurementRepository.findAll(specification, pageable)).thenReturn(emptyPage);

            Page<Measurement> result = measurementService.findAll(specification, pageable);

            assertNotNull(result);
            assertEquals(0, result.getTotalElements());
            assertTrue(result.getContent().isEmpty());
        }
    }

    @Nested
    @DisplayName("Find by name")
    class FindByNameTest {

        @Test
        @DisplayName("Should return measurement by name")
        void findByName_ShouldReturnMeasurement() {
            String measurementName = "Blood Pressure";
            when(measurementRepository.findFirstByName(measurementName)).thenReturn(Optional.of(testMeasurement));

            Measurement result = measurementService.findByName(measurementName);

            assertNotNull(result);
            assertEquals(testMeasurement, result);
            verify(measurementRepository).findFirstByName(measurementName);
        }

        @Test
        @DisplayName("Should throw exception when measurement not found by name")
        void findByName_WhenMeasurementNotExists_ShouldThrowException() {
            String measurementName = "Non-existent";
            when(measurementRepository.findFirstByName(measurementName)).thenReturn(Optional.empty());

            DomainException exception = assertThrows(DomainException.class,
                () -> measurementService.findByName(measurementName));

            assertEquals(DomainExceptionCode.MEASUREMENT_NOT_FOUND, exception.getCode());
            verify(measurementRepository).findFirstByName(measurementName);
        }
    }

    @Nested
    @DisplayName("Find all by IDs")
    class FindAllByIdTest {

        @Test
        @DisplayName("Should return measurements by IDs")
        void findAllById_ShouldReturnMeasurements() {
            List<UUID> ids = List.of(testId, UUID.randomUUID());
            List<Measurement> measurements = List.of(testMeasurement);
            when(measurementRepository.findAllById(ids)).thenReturn(measurements);

            List<Measurement> result = measurementService.findAllById(ids);

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(testMeasurement, result.get(0));
            verify(measurementRepository).findAllById(ids);
        }
    }

    @Nested
    @DisplayName("Create measurement")
    class CreateMeasurementTest {

        @Test
        @DisplayName("Should create and save measurement")
        void create_ShouldCreateAndSaveMeasurement() {
            when(measurementRepository.save(any(Measurement.class))).thenReturn(testMeasurement);

            Measurement result = measurementService.create(createCommand);

            assertNotNull(result);
            verify(measurementRepository).save(any(Measurement.class));
        }
    }

    @Nested
    @DisplayName("Update measurement")
    class UpdateMeasurementTest {

        @Test
        @DisplayName("Should update existing measurement")
        void update_ShouldUpdateExistingMeasurement() {
            when(measurementRepository.findById(testId)).thenReturn(Optional.of(testMeasurement));
            when(measurementRepository.save(any(Measurement.class))).thenReturn(testMeasurement);

            Measurement result = measurementService.update(updateCommand);

            assertNotNull(result);
            verify(measurementRepository).findById(testId);
            verify(measurementRepository).save(any(Measurement.class));
        }

        @Test
        @DisplayName("Should throw exception when updating non-existent measurement")
        void update_WhenMeasurementNotExists_ShouldThrowException() {
            when(measurementRepository.findById(testId)).thenReturn(Optional.empty());

            DomainException exception = assertThrows(DomainException.class,
                () -> measurementService.update(updateCommand));

            assertEquals(DomainExceptionCode.MEASUREMENT_NOT_FOUND, exception.getCode());
            verify(measurementRepository).findById(testId);
            verify(measurementRepository, never()).save(any(Measurement.class));
        }
    }

    private Measurement createTestMeasurement() {
        return Measurement.create(createTestCreateCommand());
    }

    private CreateMeasurementCommand createTestCreateCommand() {
        return new CreateMeasurementCommand(
            "Blood Pressure",
            MeasurementType.BLOOD_PRESSURE,
            MeasurementChartType.LINE,
            "openEHR-EHR-OBSERVATION.blood_pressure.v2"
        );
    }

    private UpdateMeasurementCommand createTestUpdateCommand() {
        return new UpdateMeasurementCommand(
            testId,
            "Updated Blood Pressure",
            MeasurementType.BLOOD_PRESSURE,
            MeasurementChartType.BAR,
            "openEHR-EHR-OBSERVATION.blood_pressure.v2"
        );
    }
}
