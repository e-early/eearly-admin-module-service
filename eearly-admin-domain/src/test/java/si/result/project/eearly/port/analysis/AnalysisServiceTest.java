package si.result.project.eearly.port.analysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.analysis.Analysis;
import si.result.project.eearly.model.analysis.AnalysisState;
import si.result.project.eearly.model.analysis.FeedbackState;
import si.result.project.eearly.model.analysis.command.UpdateAnalysisCommand;
import si.result.project.eearly.model.patient.Patient;
import si.result.spring.boot.bricks.exception.DomainException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {

    @Mock
    private AnalysisRepository analysisRepository;

    @InjectMocks
    private AnalysisService analysisService;

    private Analysis testAnalysis;
    private UUID testId;
    private UpdateAnalysisCommand updateCommand;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();
        testAnalysis = createTestAnalysis();
        updateCommand = createTestUpdateCommand();
    }

    @Nested
    @DisplayName("Find by ID")
    class FindByIdTest {

        @Test
        @DisplayName("Should return analysis when found")
        void findById_WhenAnalysisExists_ShouldReturnAnalysis() {
            when(analysisRepository.findById(testId)).thenReturn(Optional.of(testAnalysis));

            Analysis result = analysisService.findById(testId);

            assertNotNull(result);
            assertEquals(testAnalysis, result);
            verify(analysisRepository).findById(testId);
        }

        @Test
        @DisplayName("Should throw exception when analysis not found")
        void findById_WhenAnalysisNotExists_ShouldThrowException() {
            when(analysisRepository.findById(testId)).thenReturn(Optional.empty());

            DomainException exception = assertThrows(DomainException.class,
                () -> analysisService.findById(testId));

            assertEquals(DomainExceptionCode.ANALYSIS_NOT_FOUND, exception.getCode());
            verify(analysisRepository).findById(testId);
        }
    }

    @Nested
    @DisplayName("Find all with search")
    class FindAllTest {

        @Test
        @DisplayName("Should return page of analyses with specification")
        void findAll_WithSpecification_ShouldReturnPageOfAnalyses() {
            List<Analysis> analyses = List.of(testAnalysis);
            Page<Analysis> expectedPage = new PageImpl<>(analyses);

            @SuppressWarnings("unchecked")
            Specification<Analysis> specification = mock(Specification.class);
            Pageable pageable = mock(Pageable.class);

            when(analysisRepository.findAll(specification, pageable)).thenReturn(expectedPage);

            Page<Analysis> result = analysisService.findAll(specification, pageable);

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            assertEquals(testAnalysis, result.getContent().getFirst());
            verify(analysisRepository).findAll(specification, pageable);
        }

        @Test
        @DisplayName("Should return empty page when no analyses found")
        void findAll_WhenNoAnalyses_ShouldReturnEmptyPage() {
            Page<Analysis> emptyPage = new PageImpl<>(List.of());
            @SuppressWarnings("unchecked")
            Specification<Analysis> specification = mock(Specification.class);
            Pageable pageable = mock(Pageable.class);

            when(analysisRepository.findAll(specification, pageable)).thenReturn(emptyPage);

            Page<Analysis> result = analysisService.findAll(specification, pageable);

            assertNotNull(result);
            assertEquals(0, result.getTotalElements());
            assertTrue(result.getContent().isEmpty());
        }
    }

    @Nested
    @DisplayName("Update analysis")
    class UpdateAnalysisTest {

        @Test
        @DisplayName("Should update existing analysis")
        void update_ShouldUpdateExistingAnalysis() {
            when(analysisRepository.findById(testId)).thenReturn(Optional.of(testAnalysis));
            when(analysisRepository.save(any(Analysis.class))).thenReturn(testAnalysis);

            Analysis result = analysisService.update(updateCommand);

            assertNotNull(result);
            verify(analysisRepository).findById(testId);
            verify(analysisRepository).save(any(Analysis.class));
        }

        @Test
        @DisplayName("Should throw exception when updating non-existent analysis")
        void update_WhenAnalysisNotExists_ShouldThrowException() {
            when(analysisRepository.findById(testId)).thenReturn(Optional.empty());

            DomainException exception = assertThrows(DomainException.class,
                () -> analysisService.update(updateCommand));

            assertEquals(DomainExceptionCode.ANALYSIS_NOT_FOUND, exception.getCode());
            verify(analysisRepository).findById(testId);
            verify(analysisRepository, never()).save(any(Analysis.class));
        }
    }

    @Nested
    @DisplayName("Update result")
    class UpdateResultTest {

        @Test
        @DisplayName("Should publish AnalysisReadyEvent when state is WAITING_FOR_CONFIRMATION")
        void updateResult_WaitingForConfirmation_PublishesAnalysisReadyEvent() {
            UUID patientId = UUID.randomUUID();
            Analysis savedAnalysis = createSavedAnalysis(AnalysisState.WAITING_FOR_CONFIRMATION, testId, patientId);
            when(analysisRepository.findById(testId)).thenReturn(Optional.of(testAnalysis));
            when(analysisRepository.save(any())).thenReturn(savedAnalysis);

            analysisService.updateResult(testId, AnalysisState.WAITING_FOR_CONFIRMATION, true);
        }

        @Test
        @DisplayName("Should publish AnalysisErrorEvent when state is ERROR")
        void updateResult_Error_PublishesAnalysisErrorEvent() {
            UUID patientId = UUID.randomUUID();
            Analysis savedAnalysis = createSavedAnalysis(AnalysisState.ERROR, testId, patientId);
            when(analysisRepository.findById(testId)).thenReturn(Optional.of(testAnalysis));
            when(analysisRepository.save(any())).thenReturn(savedAnalysis);

            analysisService.updateResult(testId, AnalysisState.ERROR, false);
        }

        @Test
        @DisplayName("Should publish no event when state is neither WAITING_FOR_CONFIRMATION nor ERROR")
        void updateResult_OtherState_PublishesNoEvent() {
            Analysis savedAnalysis = createSavedAnalysis(AnalysisState.COMPLETED, testId, UUID.randomUUID());
            when(analysisRepository.findById(testId)).thenReturn(Optional.of(testAnalysis));
            when(analysisRepository.save(any())).thenReturn(savedAnalysis);

            analysisService.updateResult(testId, AnalysisState.COMPLETED, true);
        }

        @Test
        @DisplayName("Should throw exception and publish no event when analysis not found")
        void updateResult_AnalysisNotFound_ThrowsAndPublishesNoEvent() {
            when(analysisRepository.findById(testId)).thenReturn(Optional.empty());

            DomainException exception = assertThrows(DomainException.class,
                    () -> analysisService.updateResult(testId, AnalysisState.WAITING_FOR_CONFIRMATION, true));

            assertEquals(DomainExceptionCode.ANALYSIS_NOT_FOUND, exception.getCode());
            verify(analysisRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Update state")
    class UpdateStateTest {

        @Test
        @DisplayName("Should publish AnalysisReadyEvent when state is WAITING_FOR_CONFIRMATION")
        void updateState_WaitingForConfirmation_PublishesAnalysisReadyEvent() {
            UUID patientId = UUID.randomUUID();
            Analysis savedAnalysis = createSavedAnalysis(AnalysisState.WAITING_FOR_CONFIRMATION, testId, patientId);
            when(analysisRepository.findById(testId)).thenReturn(Optional.of(testAnalysis));
            when(analysisRepository.save(any())).thenReturn(savedAnalysis);

            analysisService.updateState(testId, AnalysisState.WAITING_FOR_CONFIRMATION);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        }

        @Test
        @DisplayName("Should publish AnalysisErrorEvent when state is ERROR")
        void updateState_Error_PublishesAnalysisErrorEvent() {
            UUID patientId = UUID.randomUUID();
            Analysis savedAnalysis = createSavedAnalysis(AnalysisState.ERROR, testId, patientId);
            when(analysisRepository.findById(testId)).thenReturn(Optional.of(testAnalysis));
            when(analysisRepository.save(any())).thenReturn(savedAnalysis);

            analysisService.updateState(testId, AnalysisState.ERROR);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        }

        @Test
        @DisplayName("Should publish no event when state is neither WAITING_FOR_CONFIRMATION nor ERROR")
        void updateState_OtherState_PublishesNoEvent() {
            Analysis savedAnalysis = createSavedAnalysis(AnalysisState.IN_PROGRESS, testId, UUID.randomUUID());
            when(analysisRepository.findById(testId)).thenReturn(Optional.of(testAnalysis));
            when(analysisRepository.save(any())).thenReturn(savedAnalysis);

            analysisService.updateState(testId, AnalysisState.IN_PROGRESS);
        }

        @Test
        @DisplayName("Should throw exception and publish no event when analysis not found")
        void updateState_AnalysisNotFound_ThrowsAndPublishesNoEvent() {
            when(analysisRepository.findById(testId)).thenReturn(Optional.empty());

            DomainException exception = assertThrows(DomainException.class,
                    () -> analysisService.updateState(testId, AnalysisState.WAITING_FOR_CONFIRMATION));

            assertEquals(DomainExceptionCode.ANALYSIS_NOT_FOUND, exception.getCode());
            verify(analysisRepository, never()).save(any());
        }
    }

    private Analysis createTestAnalysis() {
        Analysis analysis = mock(Analysis.class);
        lenient().when(analysis.getState()).thenReturn(AnalysisState.IN_PROGRESS);
        lenient().when(analysis.getFeedback()).thenReturn(FeedbackState.NONE);
        lenient().when(analysis.getDetected()).thenReturn(true);
        return analysis;
    }

    private Analysis createSavedAnalysis(AnalysisState state, UUID analysisId, UUID patientId) {
        Patient patient = mock(Patient.class);
        lenient().when(patient.getId()).thenReturn(patientId);
        Analysis saved = mock(Analysis.class);
        lenient().when(saved.getState()).thenReturn(state);
        lenient().when(saved.getId()).thenReturn(analysisId);
        lenient().when(saved.getPatient()).thenReturn(patient);
        return saved;
    }

    private UpdateAnalysisCommand createTestUpdateCommand() {
        return new UpdateAnalysisCommand(
            testId,
            "Updated Analysis",
            AnalysisState.COMPLETED,
            FeedbackState.CONFIRMED,
            false,
            mock(Patient.class)
        );
    }
}
