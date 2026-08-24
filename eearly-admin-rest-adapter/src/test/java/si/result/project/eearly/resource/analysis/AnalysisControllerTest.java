package si.result.project.eearly.resource.analysis;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import si.result.project.eearly.dto.analysis.AnalysisDTO;
import si.result.project.eearly.dto.analysis.DetectionIntervalUpdateDTO;
import si.result.project.eearly.dto.feedback.AnalysisFeedbacksResponseDTO;
import si.result.project.eearly.dto.feedback.AnalysisFeedbackPayloadDTO;
import si.result.project.eearly.dto.feedback.DetectionBoxFeedbackDTO;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.facade.analysis.AnalysisServiceFacade;
import si.result.project.eearly.model.analysis.DetectionBoxAuditStatus;
import si.result.project.eearly.model.analysis.FeedbackState;
import si.result.spring.boot.bricks.dto.ResponseDTO;
import si.result.spring.boot.bricks.exception.DomainException;

@ExtendWith(MockitoExtension.class)
class AnalysisControllerTest {

    @Mock
    private AnalysisServiceFacade analysisServiceFacade;

    @InjectMocks
    private AnalysisController controller;

    @Nested
    @DisplayName("Update Detection Interval")
    class UpdateDetectionIntervalTest {

        @Test
        @DisplayName("Should delegate detection interval update to facade")
        void updateDetectionInterval_ShouldDelegateToFacade() {
            UUID analysisId = UUID.randomUUID();
            UUID detectionId = UUID.randomUUID();
            DetectionIntervalUpdateDTO body = new DetectionIntervalUpdateDTO(
                    OffsetDateTime.parse("2026-05-07T08:00:00Z"),
                    OffsetDateTime.parse("2026-05-07T08:30:00Z")
            );
            AnalysisDTO expected = org.mockito.Mockito.mock(AnalysisDTO.class);

            when(analysisServiceFacade.updateDetectionInterval(analysisId, detectionId, body))
                    .thenReturn(expected);

            ResponseEntity<ResponseDTO<AnalysisDTO>> response =
                    controller.updateDetectionInterval(analysisId, detectionId, body);

            assertEquals(200, response.getStatusCode().value());
            assertEquals(expected, response.getBody().payload());
            verify(analysisServiceFacade).updateDetectionInterval(analysisId, detectionId, body);
        }
    }

    @Nested
    @DisplayName("Get Analysis Feedbacks")
    class GetAnalysisFeedbacksTest {

        @Test
        @DisplayName("Should return feedbacks without date parameters")
        void getAnalysisFeedbacks_WithoutDates_ShouldDelegateToFacade() {
            AnalysisFeedbacksResponseDTO expected = new AnalysisFeedbacksResponseDTO(List.of());
            when(analysisServiceFacade.getAnalysisFeedbacks(Optional.empty(), Optional.empty()))
                    .thenReturn(expected);

            ResponseEntity<ResponseDTO<AnalysisFeedbacksResponseDTO>> response =
                    controller.getAnalysisFeedbacks(null, null);

            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody());
            verify(analysisServiceFacade).getAnalysisFeedbacks(Optional.empty(), Optional.empty());
            verifyNoMoreInteractions(analysisServiceFacade);
        }

        @Test
        @DisplayName("Should pass date range to facade when both dates provided")
        void getAnalysisFeedbacks_WithDates_ShouldDelegateToFacade() {
            OffsetDateTime start = OffsetDateTime.parse("2026-01-01T00:00:00Z");
            OffsetDateTime end = OffsetDateTime.parse("2026-01-31T23:59:59Z");
            AnalysisFeedbacksResponseDTO expected = sampleResponse();

            when(analysisServiceFacade.getAnalysisFeedbacks(
                    eq(Optional.of(start)),
                    eq(Optional.of(end))
            )).thenReturn(expected);

            ResponseEntity<ResponseDTO<AnalysisFeedbacksResponseDTO>> response =
                    controller.getAnalysisFeedbacks(start, end);

            assertEquals(200, response.getStatusCode().value());
            verify(analysisServiceFacade).getAnalysisFeedbacks(
                    eq(Optional.of(start)),
                    eq(Optional.of(end))
            );
        }

        @Test
        @DisplayName("Should reject incomplete date range when only startDate is provided")
        void getAnalysisFeedbacks_WithOnlyStartDate_ShouldThrow() {
            OffsetDateTime start = OffsetDateTime.parse("2026-01-01T00:00:00Z");

            DomainException ex = assertThrows(
                    DomainException.class,
                    () -> controller.getAnalysisFeedbacks(start, null)
            );
            assertEquals(DomainExceptionCode.ANALYSIS_FEEDBACK_DATE_RANGE_INCOMPLETE, ex.getCode());
            verify(analysisServiceFacade, never()).getAnalysisFeedbacks(any(), any());
        }

        @Test
        @DisplayName("Should reject incomplete date range when only endDate is provided")
        void getAnalysisFeedbacks_WithOnlyEndDate_ShouldThrow() {
            OffsetDateTime end = OffsetDateTime.parse("2026-01-31T23:59:59Z");

            DomainException ex = assertThrows(
                    DomainException.class,
                    () -> controller.getAnalysisFeedbacks(null, end)
            );
            assertEquals(DomainExceptionCode.ANALYSIS_FEEDBACK_DATE_RANGE_INCOMPLETE, ex.getCode());
            verify(analysisServiceFacade, never()).getAnalysisFeedbacks(any(), any());
        }

        @Test
        @DisplayName("Should reject when end date is before start date")
        void getAnalysisFeedbacks_WhenEndBeforeStart_ShouldThrow() {
            OffsetDateTime start = OffsetDateTime.parse("2026-02-01T00:00:00Z");
            OffsetDateTime end = OffsetDateTime.parse("2026-01-01T00:00:00Z");

            DomainException ex = assertThrows(
                    DomainException.class,
                    () -> controller.getAnalysisFeedbacks(start, end)
            );
            assertEquals(DomainExceptionCode.ANALYSIS_FEEDBACK_END_DATE_BEFORE_START_DATE, ex.getCode());
            verify(analysisServiceFacade, never()).getAnalysisFeedbacks(any(), any());
        }

        @Test
        @DisplayName("Should reject when end date is in the future")
        void getAnalysisFeedbacks_WhenEndInFuture_ShouldThrow() {
            OffsetDateTime start = OffsetDateTime.now().minusDays(2);
            OffsetDateTime end = OffsetDateTime.now().plusDays(1);

            DomainException ex = assertThrows(
                    DomainException.class,
                    () -> controller.getAnalysisFeedbacks(start, end)
            );
            assertEquals(DomainExceptionCode.ANALYSIS_FEEDBACK_END_DATE_IN_FUTURE, ex.getCode());
            verify(analysisServiceFacade, never()).getAnalysisFeedbacks(any(), any());
        }

        @Test
        @DisplayName("Should return mapped payload in response body")
        void getAnalysisFeedbacks_ShouldReturnPayloadInResponseBody() {
            AnalysisFeedbacksResponseDTO expected = sampleResponse();
            when(analysisServiceFacade.getAnalysisFeedbacks(Optional.empty(), Optional.empty()))
                    .thenReturn(expected);

            ResponseEntity<ResponseDTO<AnalysisFeedbacksResponseDTO>> response =
                    controller.getAnalysisFeedbacks(null, null);

            assertEquals(expected, response.getBody().payload());
            assertEquals(1, response.getBody().payload().analysisPayload().size());
            assertEquals(1, response.getBody().payload().analysisPayload().getFirst().detectionBoxes().size());
        }
    }

    private static AnalysisFeedbacksResponseDTO sampleResponse() {
        return new AnalysisFeedbacksResponseDTO(List.of(
                new AnalysisFeedbackPayloadDTO(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "RESPIRATORY",
                        "AdminApp",
                        "Apnea detection",
                        "2",
                        0.42,
                        List.of(new DetectionBoxFeedbackDTO(
                                UUID.randomUUID(),
                                OffsetDateTime.parse("2026-05-07T07:19:01Z"),
                                OffsetDateTime.parse("2026-05-07T07:19:15Z"),
                                OffsetDateTime.parse("2026-05-07T07:19:01Z"),
                                OffsetDateTime.parse("2026-05-07T07:19:15Z"),
                                0.15,
                                FeedbackState.CONFIRMED,
                                "MODEL_PREDICTION",
                                OffsetDateTime.parse("2026-05-07T09:52:27Z"),
                                OffsetDateTime.parse("2026-05-07T09:52:27Z"),
                                DetectionBoxAuditStatus.CREATED
                        ))
                )
        ));
    }
}
