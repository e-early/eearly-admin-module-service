package si.result.project.eearly.facade.analysis;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PagedModel;

import si.result.project.eearly.dto.analysis.AnalysisDTO;
import si.result.project.eearly.dto.analysis.AnalysisFeedbackDTO;
import si.result.project.eearly.dto.analysis.AnalysisLiteDTO;
import si.result.project.eearly.dto.analysis.DetectionFeedbackDTO;
import si.result.project.eearly.dto.analysis.DetectionIntervalUpdateDTO;
import si.result.project.eearly.dto.feedback.AnalysisFeedbacksResponseDTO;
import si.result.project.eearly.dto.feedback.DetectionBoxFeedbackDTO;
import si.result.project.eearly.mapper.analysis.AnalysisMapper;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.algorithm.AlgorithmCategoryType;
import si.result.project.eearly.model.analysis.Analysis;
import si.result.project.eearly.model.analysis.AnalysisDetectionBox;
import si.result.project.eearly.model.analysis.AnalysisState;
import si.result.project.eearly.model.analysis.DetectionBoxAuditStatus;
import si.result.project.eearly.model.analysis.FeedbackState;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.patient.Patient;
import si.result.project.eearly.port.algorithm.AlgorithmService;
import si.result.project.eearly.port.analysis.AnalysisDetectionBoxService;
import si.result.project.eearly.port.analysis.AnalysisService;
import si.result.rest.filter.Filter;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceFacadeTest {

    @Mock
    private AnalysisService analysisService;
    
    @Mock
    private AnalysisMapper analysisMapper;

    @Mock
    private AnalysisDetectionBoxService analysisDetectionBoxService;

    @Mock
    private Filter filter;
    
    @Mock
    private Pageable pageable;

    @InjectMocks
    private AnalysisServiceFacade analysisServiceFacade;

    private Analysis testAnalysis;
    private UUID testId;
    private AnalysisFeedbackDTO feedbackDTO;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();
        testAnalysis = createTestAnalysis();
        feedbackDTO = createTestFeedbackDTO();
    }

    @Nested
    @DisplayName("Get Analysis State - Workflow Logic")
    class GetAnalysisStateTest {

        @Test
        @DisplayName("Should return REJECTED when feedback is REJECTED")
        void getAnalysisState_WhenFeedbackRejected_ShouldReturnRejected() {
            AnalysisState result = analysisServiceFacade.getAnalysisState(
                AnalysisState.IN_PROGRESS, FeedbackState.REJECTED);
            
            assertEquals(AnalysisState.REJECTED, result);
        }

        @Test
        @DisplayName("Should return CONFIRMED when feedback is CONFIRMED")
        void getAnalysisState_WhenFeedbackConfirmed_ShouldReturnConfirmed() {
            AnalysisState result = analysisServiceFacade.getAnalysisState(
                AnalysisState.IN_PROGRESS, FeedbackState.CONFIRMED);
            
            assertEquals(AnalysisState.CONFIRMED, result);
        }

        @Test
        @DisplayName("Should return original state when feedback is NONE")
        void getAnalysisState_WhenFeedbackNone_ShouldReturnOriginalState() {
            AnalysisState result = analysisServiceFacade.getAnalysisState(
                AnalysisState.IN_PROGRESS, FeedbackState.NONE);
            
            assertEquals(AnalysisState.IN_PROGRESS, result);
        }

        @Test
        @DisplayName("Should handle all AnalysisState combinations with REJECTED feedback")
        void getAnalysisState_AllStatesWithRejectedFeedback_ShouldReturnRejected() {
            AnalysisState[] allStates = {
                AnalysisState.ORDERED,
                AnalysisState.IN_PROGRESS,
                AnalysisState.WAITING_FOR_CONFIRMATION,
                AnalysisState.CONFIRMED,
                AnalysisState.COMPLETED,
                AnalysisState.ERROR
            };

            for (AnalysisState state : allStates) {
                AnalysisState result = analysisServiceFacade.getAnalysisState(state, FeedbackState.REJECTED);
                assertEquals(AnalysisState.REJECTED, result, 
                    "State " + state + " with REJECTED feedback should return REJECTED");
            }
        }

        @Test
        @DisplayName("Should handle all AnalysisState combinations with CONFIRMED feedback")
        void getAnalysisState_AllStatesWithConfirmedFeedback_ShouldReturnConfirmed() {
            AnalysisState[] allStates = {
                AnalysisState.ORDERED,
                AnalysisState.IN_PROGRESS,
                AnalysisState.WAITING_FOR_CONFIRMATION,
                AnalysisState.REJECTED,
                AnalysisState.COMPLETED,
                AnalysisState.ERROR
            };

            for (AnalysisState state : allStates) {
                AnalysisState result = analysisServiceFacade.getAnalysisState(state, FeedbackState.CONFIRMED);
                assertEquals(AnalysisState.CONFIRMED, result, 
                    "State " + state + " with CONFIRMED feedback should return CONFIRMED");
            }
        }
    }

    @Nested
    @DisplayName("Get Page")
    class GetPageTest {

        @Test
        @DisplayName("Should return paged analyses with calculated state")
        void getPage_ShouldReturnPagedAnalyses() {
            List<Analysis> analyses = List.of(testAnalysis);
            Page<Analysis> analysisPage = new PageImpl<>(analyses);
            AnalysisLiteDTO liteDTO = mock(AnalysisLiteDTO.class);
            
            @SuppressWarnings("unchecked")
            Specification<Object> specification = mock(Specification.class);
            when(filter.toSpecification()).thenReturn(specification);
            when(analysisService.findAll(any(), any(Pageable.class))).thenReturn(analysisPage);
            when(analysisMapper.toLiteDTO(any(Analysis.class), any(AnalysisState.class))).thenReturn(liteDTO);

            PagedModel<AnalysisLiteDTO> result = analysisServiceFacade.getPage(filter, pageable);

            assertNotNull(result);
            verify(analysisService).findAll(any(), any(Pageable.class));
            verify(analysisMapper).toLiteDTO(any(Analysis.class), any(AnalysisState.class));
        }

        @Test
        @DisplayName("Should handle empty page results")
        void getPage_WithEmptyResults_ShouldReturnEmptyPage() {
            Page<Analysis> emptyPage = new PageImpl<>(List.of());
            
            @SuppressWarnings("unchecked")
            Specification<Object> specification = mock(Specification.class);
            when(filter.toSpecification()).thenReturn(specification);
            when(analysisService.findAll(any(), any(Pageable.class))).thenReturn(emptyPage);

            PagedModel<AnalysisLiteDTO> result = analysisServiceFacade.getPage(filter, pageable);

            assertNotNull(result);
            verify(analysisService).findAll(any(), any(Pageable.class));
            verify(analysisMapper, never()).toLiteDTO(any(Analysis.class), any(AnalysisState.class));
        }
    }

    @Nested
    @DisplayName("Get By ID")
    class GetByIdTest {

        @Test
        @DisplayName("Should return analysis with calculated state")
        void getById_ShouldReturnAnalysisWithCalculatedState() {
            AnalysisDTO analysisDTO = mock(AnalysisDTO.class);
            
            when(analysisService.findById(testId)).thenReturn(testAnalysis);
            when(analysisDetectionBoxService.findActiveByAnalysisId(any())).thenReturn(List.of());
            when(analysisMapper.toDTO(any(Analysis.class), any(AnalysisState.class), anyList()))
                    .thenReturn(analysisDTO);

            AnalysisDTO result = analysisServiceFacade.getById(testId);

            assertNotNull(result);
            verify(analysisService).findById(testId);
            verify(analysisDetectionBoxService).findActiveByAnalysisId(any());
            verify(analysisMapper).toDTO(any(Analysis.class), any(AnalysisState.class), anyList());
        }
    }

    @Nested
    @DisplayName("Update Analysis")
    class UpdateAnalysisTest {

        @Test
        @DisplayName("Should update analysis and return with calculated state")
        void update_ShouldUpdateAnalysisAndReturnWithCalculatedState() {
            Analysis updatedAnalysis = createTestAnalysis();
            AnalysisDTO analysisDTO = mock(AnalysisDTO.class);
            si.result.project.eearly.model.analysis.command.UpdateAnalysisCommand updateCommand = 
                mock(si.result.project.eearly.model.analysis.command.UpdateAnalysisCommand.class);
            
            when(analysisMapper.toUpdateCommand(testId, feedbackDTO)).thenReturn(updateCommand);
            when(analysisService.update(updateCommand)).thenReturn(updatedAnalysis);
            when(analysisDetectionBoxService.findActiveByAnalysisId(any())).thenReturn(List.of());
            when(analysisMapper.toDTO(any(Analysis.class), any(AnalysisState.class), anyList()))
                    .thenReturn(analysisDTO);

            AnalysisDTO result = analysisServiceFacade.update(testId, feedbackDTO);

            assertNotNull(result);
            verify(analysisMapper).toUpdateCommand(testId, feedbackDTO);
            verify(analysisService).update(updateCommand);
            verify(analysisMapper).toDTO(any(Analysis.class), any(AnalysisState.class), anyList());
        }
    }

    @Nested
    @DisplayName("Update Detection Feedback")
    class UpdateDetectionFeedbackTest {

        @Test
        @DisplayName("Should update detection box feedback in persistent storage")
        void updateDetectionFeedback_ShouldUpdateDetectionBox() {
            UUID detectionId = UUID.randomUUID();
            Analysis analysis = createTestAnalysis();
            AnalysisDTO analysisDTO = mock(AnalysisDTO.class);

            when(analysisService.findById(testId)).thenReturn(analysis);
            when(analysisDetectionBoxService.findActiveByAnalysisId(any())).thenReturn(List.of());
            when(analysisMapper.toDTO(any(Analysis.class), any(AnalysisState.class), anyList()))
                    .thenReturn(analysisDTO);

            AnalysisDTO result = analysisServiceFacade.updateDetectionFeedback(
                    testId,
                    detectionId,
                    new DetectionFeedbackDTO(FeedbackState.CONFIRMED)
            );

            assertNotNull(result);
            verify(analysisDetectionBoxService).updateCaretakerFeedback(testId, detectionId, FeedbackState.CONFIRMED);
            verify(analysisDetectionBoxService).findActiveByAnalysisId(any());
        }
    }

    @Nested
    @DisplayName("Get Analysis Feedbacks")
    class GetAnalysisFeedbacksTest {

        @Test
        @DisplayName("Should return detection boxes grouped by analysis without date filter")
        void getAnalysisFeedbacks_WithoutDates_ShouldReturnDetectionBoxes() {
            UUID analysisId = UUID.randomUUID();
            UUID patientId = UUID.randomUUID();
            UUID caretakerId = UUID.randomUUID();
            UUID boxId = UUID.randomUUID();

            OffsetDateTime start = OffsetDateTime.parse("2026-05-07T07:19:01Z");
            OffsetDateTime end = OffsetDateTime.parse("2026-05-07T07:19:15Z");
            OffsetDateTime lastEdit = OffsetDateTime.parse("2026-05-07T09:52:27.946217438Z");

            Analysis analysis = mock(Analysis.class);
            Patient patient = mock(Patient.class);
            Caretaker caretaker = mock(Caretaker.class);
            Algorithm algorithm = mock(Algorithm.class);
            AnalysisDetectionBox box = mock(AnalysisDetectionBox.class);

            when(box.getAnalysis()).thenReturn(analysis);
            when(box.getId()).thenReturn(boxId);
            when(box.getStartTimestamp()).thenReturn(start);
            when(box.getEndTimestamp()).thenReturn(end);
            when(box.getOriginalStartTimestamp()).thenReturn(start);
            when(box.getOriginalEndTimestamp()).thenReturn(end);
            when(box.getProbability()).thenReturn(0.15);
            when(box.getCaretakerFeedback()).thenReturn(FeedbackState.CONFIRMED);
            when(box.getSource()).thenReturn("MODEL_PREDICTION");
            when(box.getCreatedAt()).thenReturn(lastEdit.atZoneSameInstant(ZoneOffset.UTC));
            when(box.getLastModifiedAt()).thenReturn(lastEdit.atZoneSameInstant(ZoneOffset.UTC));
            when(box.getAuditStatus()).thenReturn(DetectionBoxAuditStatus.CREATED);

            when(analysisDetectionBoxService.findForFeedbacks(Optional.empty(), Optional.empty()))
                    .thenReturn(List.of(box));
            when(analysisService.findById(analysisId)).thenReturn(analysis);
            when(analysis.getId()).thenReturn(analysisId);
            when(analysis.getPatient()).thenReturn(patient);
            when(analysis.getAlgorithm()).thenReturn(algorithm);
            when(patient.getId()).thenReturn(patientId);
            when(patient.getCaretakers()).thenReturn(List.of(caretaker));
            when(caretaker.getId()).thenReturn(caretakerId);
            when(algorithm.getCategory()).thenReturn(AlgorithmCategoryType.RESPIRATORY);
            when(algorithm.getName()).thenReturn("Apnea detection");
            when(algorithm.getAlgorithmVersion()).thenReturn("2");
            when(algorithm.getModelThreshold()).thenReturn(0.42);

            AnalysisFeedbacksResponseDTO result =
                    analysisServiceFacade.getAnalysisFeedbacks(Optional.empty(), Optional.empty());

            assertEquals(1, result.analysisPayload().size());
            assertEquals(analysisId, result.analysisPayload().getFirst().analysisId());
            assertEquals(patientId, result.analysisPayload().getFirst().patientId());
            assertEquals(caretakerId, result.analysisPayload().getFirst().caretakersId());
            assertEquals("RESPIRATORY", result.analysisPayload().getFirst().diagnosisType());
            assertEquals("AdminApp", result.analysisPayload().getFirst().datasourceType());
            assertEquals(0.42, result.analysisPayload().getFirst().modelThreshold());
            assertEquals(1, result.analysisPayload().getFirst().detectionBoxes().size());

            DetectionBoxFeedbackDTO detectionBox = result.analysisPayload().getFirst().detectionBoxes().getFirst();
            assertEquals(boxId, detectionBox.detectionBoxId());
            assertEquals(FeedbackState.CONFIRMED, detectionBox.caretakerFeedback());
            assertEquals("MODEL_PREDICTION", detectionBox.source());
            assertEquals(DetectionBoxAuditStatus.CREATED, detectionBox.status());
        }

        @Test
        @DisplayName("Should return empty payload when no detection boxes match")
        void getAnalysisFeedbacks_WhenNoBoxes_ShouldReturnEmptyPayload() {
            when(analysisDetectionBoxService.findForFeedbacks(any(), any())).thenReturn(List.of());

            AnalysisFeedbacksResponseDTO result = analysisServiceFacade.getAnalysisFeedbacks(
                    Optional.of(OffsetDateTime.parse("2026-02-01T00:00:00Z")),
                    Optional.of(OffsetDateTime.parse("2026-02-28T23:59:59Z"))
            );

            assertEquals(List.of(), result.analysisPayload());
        }

        @Test
        @DisplayName("Should map status timestamps from auditable fields")
        void getAnalysisFeedbacks_ShouldMapStatusTimestamps() {
            UUID analysisId = UUID.randomUUID();
            OffsetDateTime created = OffsetDateTime.parse("2026-05-07T08:00:00Z");
            OffsetDateTime lastEdit = OffsetDateTime.parse("2026-05-07T09:52:27Z");

            Analysis analysis = mock(Analysis.class);
            Patient patient = mock(Patient.class);
            Caretaker caretaker = mock(Caretaker.class);
            Algorithm algorithm = mock(Algorithm.class);
            AnalysisDetectionBox box = mock(AnalysisDetectionBox.class);

            when(box.getAnalysis()).thenReturn(analysis);
            when(box.getId()).thenReturn(UUID.randomUUID());
            when(box.getStartTimestamp()).thenReturn(created);
            when(box.getEndTimestamp()).thenReturn(lastEdit);
            when(box.getOriginalStartTimestamp()).thenReturn(created);
            when(box.getOriginalEndTimestamp()).thenReturn(lastEdit);
            when(box.getProbability()).thenReturn(0.2);
            when(box.getCaretakerFeedback()).thenReturn(null);
            when(box.getSource()).thenReturn("CARETAKER");
            when(box.getCreatedAt()).thenReturn(created.atZoneSameInstant(ZoneOffset.UTC));
            when(box.getLastModifiedAt()).thenReturn(lastEdit.atZoneSameInstant(ZoneOffset.UTC));
            when(box.getAuditStatus()).thenReturn(DetectionBoxAuditStatus.EDITED);

            when(analysisDetectionBoxService.findForFeedbacks(Optional.empty(), Optional.empty()))
                    .thenReturn(List.of(box));
            when(analysisService.findById(analysisId)).thenReturn(analysis);
            when(analysis.getId()).thenReturn(analysisId);
            when(analysis.getPatient()).thenReturn(patient);
            when(analysis.getAlgorithm()).thenReturn(algorithm);
            when(patient.getId()).thenReturn(UUID.randomUUID());
            when(patient.getCaretakers()).thenReturn(List.of(caretaker));
            when(caretaker.getId()).thenReturn(UUID.randomUUID());
            when(algorithm.getCategory()).thenReturn(AlgorithmCategoryType.RESPIRATORY);
            when(algorithm.getName()).thenReturn("Model");
            when(algorithm.getAlgorithmVersion()).thenReturn("1");
            when(algorithm.getModelThreshold()).thenReturn(null);

            AnalysisFeedbacksResponseDTO result =
                    analysisServiceFacade.getAnalysisFeedbacks(Optional.empty(), Optional.empty());

            DetectionBoxFeedbackDTO dto = result.analysisPayload().getFirst().detectionBoxes().getFirst();
            assertEquals("CARETAKER", dto.source());
            assertEquals(created, dto.statusCreatedTimestamp());
            assertEquals(lastEdit, dto.statusLastUpdateTimestamp());
            assertEquals(DetectionBoxAuditStatus.EDITED, dto.status());
        }

        @Test
        @DisplayName("Should skip analyses without patient or algorithm")
        void getAnalysisFeedbacks_WhenPatientOrAlgorithmMissing_ShouldSkipAnalysis() {
            UUID analysisId = UUID.randomUUID();
            Analysis analysis = mock(Analysis.class);
            AnalysisDetectionBox box = mock(AnalysisDetectionBox.class);

            when(box.getAnalysis()).thenReturn(analysis);
            when(analysisDetectionBoxService.findForFeedbacks(Optional.empty(), Optional.empty()))
                    .thenReturn(List.of(box));
            when(analysisService.findById(analysisId)).thenReturn(analysis);
            when(analysis.getId()).thenReturn(analysisId);
            when(analysis.getPatient()).thenReturn(null);
            when(analysis.getAlgorithm()).thenReturn(mock(Algorithm.class));

            AnalysisFeedbacksResponseDTO result =
                    analysisServiceFacade.getAnalysisFeedbacks(Optional.empty(), Optional.empty());

            assertTrue(result.analysisPayload().isEmpty());
        }

        @Test
        @DisplayName("Should skip analyses when patient has no caretaker")
        void getAnalysisFeedbacks_WhenPatientHasNoCaretaker_ShouldSkipAnalysis() {
            UUID analysisId = UUID.randomUUID();
            Analysis analysis = mock(Analysis.class);
            Patient patient = mock(Patient.class);
            AnalysisDetectionBox box = mock(AnalysisDetectionBox.class);

            when(box.getAnalysis()).thenReturn(analysis);
            when(analysisDetectionBoxService.findForFeedbacks(Optional.empty(), Optional.empty()))
                    .thenReturn(List.of(box));
            when(analysisService.findById(analysisId)).thenReturn(analysis);
            when(analysis.getId()).thenReturn(analysisId);
            when(analysis.getPatient()).thenReturn(patient);
            when(analysis.getAlgorithm()).thenReturn(mock(Algorithm.class));
            when(patient.getCaretakers()).thenReturn(List.of());

            AnalysisFeedbacksResponseDTO result =
                    analysisServiceFacade.getAnalysisFeedbacks(Optional.empty(), Optional.empty());

            assertTrue(result.analysisPayload().isEmpty());
        }

        @Test
        @DisplayName("Should pass optional date range to detection box query")
        void getAnalysisFeedbacks_WithDates_ShouldQueryWithDateRange() {
            OffsetDateTime start = OffsetDateTime.parse("2026-02-01T00:00:00Z");
            OffsetDateTime end = OffsetDateTime.parse("2026-02-28T23:59:59Z");

            when(analysisDetectionBoxService.findForFeedbacks(
                    eq(Optional.of(start)),
                    eq(Optional.of(end))
            )).thenReturn(List.of());

            analysisServiceFacade.getAnalysisFeedbacks(Optional.of(start), Optional.of(end));

            verify(analysisDetectionBoxService).findForFeedbacks(
                    eq(Optional.of(start)),
                    eq(Optional.of(end))
            );
        }
    }

    @Nested
    @DisplayName("Update Detection Interval")
    class UpdateDetectionIntervalTest {

        @Test
        @DisplayName("Should update detection interval and return refreshed analysis DTO")
        void updateDetectionInterval_ShouldUpdateAndReturnRefreshedAnalysis() {
            UUID detectionId = UUID.randomUUID();
            OffsetDateTime start = OffsetDateTime.parse("2026-05-07T08:00:00Z");
            OffsetDateTime end = OffsetDateTime.parse("2026-05-07T08:30:00Z");
            DetectionIntervalUpdateDTO body = new DetectionIntervalUpdateDTO(start, end);
            Analysis analysis = createTestAnalysis();
            AnalysisDTO analysisDTO = mock(AnalysisDTO.class);

            when(analysisService.findById(testId)).thenReturn(analysis);
            when(analysisDetectionBoxService.findActiveByAnalysisId(any())).thenReturn(List.of());
            when(analysisMapper.toDTO(any(Analysis.class), any(AnalysisState.class), anyList()))
                    .thenReturn(analysisDTO);

            AnalysisDTO result = analysisServiceFacade.updateDetectionInterval(testId, detectionId, body);

            assertNotNull(result);
            verify(analysisDetectionBoxService).updateDetectionInterval(testId, detectionId, start, end);
        }

        @Test
        @DisplayName("Should reject when timestamps are missing")
        void updateDetectionInterval_WhenTimestampsMissing_ShouldThrow() {
            UUID detectionId = UUID.randomUUID();
            DetectionIntervalUpdateDTO body = new DetectionIntervalUpdateDTO(null, OffsetDateTime.parse("2026-05-07T08:30:00Z"));

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> analysisServiceFacade.updateDetectionInterval(testId, detectionId, body)
            );

            assertEquals("startTimestamp and endTimestamp must be provided", ex.getMessage());
            verify(analysisDetectionBoxService, never()).updateDetectionInterval(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should reject when start timestamp is not before end timestamp")
        void updateDetectionInterval_WhenStartNotBeforeEnd_ShouldThrow() {
            UUID detectionId = UUID.randomUUID();
            OffsetDateTime same = OffsetDateTime.parse("2026-05-07T08:30:00Z");
            DetectionIntervalUpdateDTO body = new DetectionIntervalUpdateDTO(same, same);

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> analysisServiceFacade.updateDetectionInterval(testId, detectionId, body)
            );

            assertEquals("startTimestamp must be before endTimestamp", ex.getMessage());
            verify(analysisDetectionBoxService, never()).updateDetectionInterval(any(), any(), any(), any());
        }
    }

    private Analysis createTestAnalysis() {
        Analysis analysis = mock(Analysis.class);
        lenient().when(analysis.getId()).thenReturn(testId);
        lenient().when(analysis.getState()).thenReturn(AnalysisState.IN_PROGRESS);
        lenient().when(analysis.getFeedback()).thenReturn(FeedbackState.NONE);
        return analysis;
    }

    private AnalysisFeedbackDTO createTestFeedbackDTO() {
        return mock(AnalysisFeedbackDTO.class);
    }
}
