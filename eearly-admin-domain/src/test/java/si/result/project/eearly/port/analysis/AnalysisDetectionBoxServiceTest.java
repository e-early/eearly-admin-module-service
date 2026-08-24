package si.result.project.eearly.port.analysis;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import si.result.project.eearly.model.analysis.Analysis;
import si.result.project.eearly.model.analysis.AnalysisDetectionBox;
import si.result.project.eearly.model.analysis.AnalysisSelection;
import si.result.project.eearly.model.analysis.DetectionBoxAuditStatus;
import si.result.project.eearly.model.analysis.DetectionBoxData;
import si.result.project.eearly.model.analysis.DetectionBoxSources;
import si.result.project.eearly.model.analysis.FeedbackState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisDetectionBoxServiceTest {

    @Mock
    private AnalysisDetectionBoxRepository analysisDetectionBoxRepository;

    @InjectMocks
    private AnalysisDetectionBoxService analysisDetectionBoxService;

    private UUID analysisId;

    @BeforeEach
    void setUp() {
        analysisId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("Find for feedbacks")
    class FindForFeedbacksTest {

        @Test
        @DisplayName("Should query active boxes only when dates are omitted")
        void findForFeedbacks_WithoutDates_ShouldFilterOnlyNonDeleted() {
            when(analysisDetectionBoxRepository.findAll(any())).thenReturn(List.of());

            analysisDetectionBoxService.findForFeedbacks(Optional.empty(), Optional.empty());

            Specification<AnalysisDetectionBox> spec = captureSpecification();
            assertSpecificationFiltersOnlyNonDeleted(spec);
        }

        @Test
        @DisplayName("Diff load should filter by lastModifiedAt and include deleted boxes")
        void findForFeedbacks_WithDates_ShouldFilterByLastModifiedAtOnly() {
            OffsetDateTime start = OffsetDateTime.parse("2026-02-01T00:00:00Z");
            OffsetDateTime end = OffsetDateTime.parse("2026-02-28T23:59:59Z");
            when(analysisDetectionBoxRepository.findAll(any())).thenReturn(List.of());

            analysisDetectionBoxService.findForFeedbacks(Optional.of(start), Optional.of(end));

            Specification<AnalysisDetectionBox> spec = captureSpecification();
            assertSpecificationIncludesLastModifiedAtBetween(spec, start, end);
        }

        @Test
        @DisplayName("Should return repository result")
        void findForFeedbacks_ShouldReturnRepositoryResult() {
            AnalysisDetectionBox box = mock(AnalysisDetectionBox.class);
            when(analysisDetectionBoxRepository.findAll(any())).thenReturn(List.of(box));

            List<AnalysisDetectionBox> result =
                    analysisDetectionBoxService.findForFeedbacks(Optional.empty(), Optional.empty());

            assertEquals(1, result.size());
            assertEquals(box, result.getFirst());
        }
    }

    @Nested
    @DisplayName("Replace algorithm detections")
    class ReplaceAlgorithmDetectionsTest {

        @Test
        @DisplayName("Should soft-delete algorithm boxes and keep caretaker feedback boxes")
        void replaceAlgorithmDetections_ShouldDeleteAlgorithmBoxesOnly() {
            Analysis analysis = mockAnalysis();
            AnalysisDetectionBox algorithmBox = mockBox("MODEL_PREDICTION");
            AnalysisDetectionBox caretakerBox = mockBox(DetectionBoxSources.CARETAKER);

            when(analysisDetectionBoxRepository.findByAnalysisId(analysisId))
                    .thenReturn(List.of(algorithmBox, caretakerBox));

            analysisDetectionBoxService.replaceAlgorithmDetections(analysis, List.of());

            verify(algorithmBox).markDeleted();
            verify(analysisDetectionBoxRepository).save(algorithmBox);
            verify(caretakerBox, never()).markDeleted();
            verify(analysisDetectionBoxRepository, never()).save(caretakerBox);
        }

        @Test
        @DisplayName("Should persist normalized algorithm detections")
        void replaceAlgorithmDetections_ShouldPersistNewBoxes() {
            Analysis analysis = mockAnalysis();
            when(analysisDetectionBoxRepository.findByAnalysisId(analysisId)).thenReturn(List.of());

            OffsetDateTime start = OffsetDateTime.parse("2026-05-07T07:19:01Z");
            OffsetDateTime end = OffsetDateTime.parse("2026-05-07T07:19:15Z");
            DetectionBoxData detection = new DetectionBoxData(
                    null,
                    start,
                    end,
                    0.15,
                    null,
                    "MODEL_PREDICTION"
            );

            analysisDetectionBoxService.replaceAlgorithmDetections(analysis, List.of(detection));

            ArgumentCaptor<AnalysisDetectionBox> captor = ArgumentCaptor.forClass(AnalysisDetectionBox.class);
            verify(analysisDetectionBoxRepository).save(captor.capture());
            AnalysisDetectionBox saved = captor.getValue();
            assertEquals(analysis, saved.getAnalysis());
            assertEquals(start, saved.getStartTimestamp());
            assertEquals(end, saved.getEndTimestamp());
            assertEquals(0.15, saved.getProbability());
            assertNull(saved.getCaretakerFeedback());
            assertEquals(DetectionBoxSources.MODEL_PREDICTION, saved.getSource());
            assertEquals(DetectionBoxAuditStatus.CREATED, saved.getAuditStatus());
        }
    }

    @Nested
    @DisplayName("Update caretaker feedback")
    class UpdateCaretakerFeedbackTest {

        @Test
        @DisplayName("Should update feedback and mark box as edited")
        void updateCaretakerFeedback_ShouldMarkEditedAndPersist() {
            UUID boxId = UUID.randomUUID();
            AnalysisDetectionBox box = mock(AnalysisDetectionBox.class);
            when(analysisDetectionBoxRepository.findByIdAndAnalysisId(boxId, analysisId))
                    .thenReturn(Optional.of(box));
            when(analysisDetectionBoxRepository.save(box)).thenReturn(box);

            AnalysisDetectionBox result = analysisDetectionBoxService.updateCaretakerFeedback(
                    analysisId,
                    boxId,
                    FeedbackState.CONFIRMED
            );

            assertEquals(box, result);
            verify(box).setCaretakerFeedback(FeedbackState.CONFIRMED);
            verify(box).markEdited();
            verify(analysisDetectionBoxRepository).save(box);
        }

        @Test
        @DisplayName("Should throw when detection box is not found")
        void updateCaretakerFeedback_WhenNotFound_ShouldThrow() {
            UUID boxId = UUID.randomUUID();
            when(analysisDetectionBoxRepository.findByIdAndAnalysisId(boxId, analysisId))
                    .thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> analysisDetectionBoxService.updateCaretakerFeedback(
                            analysisId,
                            boxId,
                            FeedbackState.REJECTED
                    )
            );

            assertTrue(ex.getMessage().contains("Detection not found"));
        }
    }

    @Nested
    @DisplayName("Append caretaker feedback boxes")
    class AppendCaretakerFeedbackBoxesTest {

        @Test
        @DisplayName("Should persist caretaker-drawn detection boxes")
        void appendCaretakerFeedbackBoxes_ShouldPersistSelections() {
            Analysis analysis = mock(Analysis.class);
            OffsetDateTime start = OffsetDateTime.parse("2026-05-07T08:00:00Z");
            OffsetDateTime end = OffsetDateTime.parse("2026-05-07T08:30:00Z");
            List<AnalysisSelection> selections = List.of(
                    new AnalysisSelection(start, end, List.of(UUID.randomUUID()))
            );

            analysisDetectionBoxService.appendCaretakerFeedbackBoxes(analysis, selections);

            ArgumentCaptor<AnalysisDetectionBox> captor = ArgumentCaptor.forClass(AnalysisDetectionBox.class);
            verify(analysisDetectionBoxRepository).save(captor.capture());
            AnalysisDetectionBox saved = captor.getValue();
            assertEquals(start, saved.getStartTimestamp());
            assertEquals(end, saved.getEndTimestamp());
            assertEquals(start, saved.getOriginalStartTimestamp());
            assertEquals(end, saved.getOriginalEndTimestamp());
            assertEquals(DetectionBoxSources.CARETAKER, saved.getSource());
            assertEquals(1.0, saved.getProbability());
            assertEquals(FeedbackState.CONFIRMED, saved.getCaretakerFeedback());
            assertEquals(DetectionBoxAuditStatus.CREATED, saved.getAuditStatus());
        }

        @Test
        @DisplayName("Should reject empty selections")
        void appendCaretakerFeedbackBoxes_WhenEmpty_ShouldThrow() {
            Analysis analysis = mock(Analysis.class);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> analysisDetectionBoxService.appendCaretakerFeedbackBoxes(analysis, List.of())
            );
        }
    }

    @Nested
    @DisplayName("Update detection interval")
    class UpdateDetectionIntervalTest {

        @Test
        @DisplayName("Should update displayed interval and persist detection box")
        void updateDetectionInterval_ShouldUpdateDisplayedIntervalAndPersist() {
            UUID boxId = UUID.randomUUID();
            OffsetDateTime start = OffsetDateTime.parse("2026-05-07T08:15:00Z");
            OffsetDateTime end = OffsetDateTime.parse("2026-05-07T08:45:00Z");
            AnalysisDetectionBox box = mock(AnalysisDetectionBox.class);
            when(analysisDetectionBoxRepository.findByIdAndAnalysisId(boxId, analysisId))
                    .thenReturn(Optional.of(box));
            when(analysisDetectionBoxRepository.save(box)).thenReturn(box);

            AnalysisDetectionBox result = analysisDetectionBoxService.updateDetectionInterval(
                    analysisId,
                    boxId,
                    start,
                    end
            );

            assertEquals(box, result);
            verify(box).updateDisplayedInterval(start, end);
            verify(analysisDetectionBoxRepository).save(box);
        }

        @Test
        @DisplayName("Should throw when detection box is not found")
        void updateDetectionInterval_WhenNotFound_ShouldThrow() {
            UUID boxId = UUID.randomUUID();
            when(analysisDetectionBoxRepository.findByIdAndAnalysisId(boxId, analysisId))
                    .thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> analysisDetectionBoxService.updateDetectionInterval(
                            analysisId,
                            boxId,
                            OffsetDateTime.parse("2026-05-07T08:00:00Z"),
                            OffsetDateTime.parse("2026-05-07T08:30:00Z")
                    )
            );

            assertTrue(ex.getMessage().contains("Detection not found"));
        }
    }

    private Analysis mockAnalysis() {
        Analysis analysis = mock(Analysis.class);
        lenient().when(analysis.getId()).thenReturn(analysisId);
        return analysis;
    }

    private static AnalysisDetectionBox mockBox(final String source) {
        AnalysisDetectionBox box = mock(AnalysisDetectionBox.class);
        when(box.getSource()).thenReturn(source);
        return box;
    }

    @SuppressWarnings("unchecked")
    private Specification<AnalysisDetectionBox> captureSpecification() {
        ArgumentCaptor<Specification<AnalysisDetectionBox>> captor =
                ArgumentCaptor.forClass(Specification.class);
        verify(analysisDetectionBoxRepository).findAll(captor.capture());
        return captor.getValue();
    }

    private static void assertSpecificationFiltersOnlyNonDeleted(
            final Specification<AnalysisDetectionBox> spec) {
        Root<AnalysisDetectionBox> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Boolean> isDeletedPath = mock(Path.class);
        Predicate notDeleted = mock(Predicate.class);
        Predicate and = mock(Predicate.class);

        when(root.get("isDeleted")).thenReturn((Path) isDeletedPath);
        when(cb.isFalse(isDeletedPath)).thenReturn(notDeleted);
        when(cb.and(any(Predicate[].class))).thenReturn(and);

        assertEquals(and, spec.toPredicate(root, query, cb));
        verify(cb).isFalse(isDeletedPath);
        verify(cb).and(any(Predicate[].class));
    }

    @SuppressWarnings("unchecked")
    private static void assertSpecificationIncludesLastModifiedAtBetween(
            final Specification<AnalysisDetectionBox> spec,
            final OffsetDateTime start,
            final OffsetDateTime end) {
        Root<AnalysisDetectionBox> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path lastModifiedPath = mock(Path.class);
        Predicate between = mock(Predicate.class);
        Predicate and = mock(Predicate.class);
        final ZonedDateTime rangeStart = start.atZoneSameInstant(ZoneOffset.UTC);
        final ZonedDateTime rangeEnd = end.atZoneSameInstant(ZoneOffset.UTC);

        when(root.get("lastModifiedAt")).thenReturn(lastModifiedPath);
        when(cb.between(lastModifiedPath, rangeStart, rangeEnd)).thenReturn(between);
        when(cb.and(any(Predicate[].class))).thenReturn(and);

        assertEquals(and, spec.toPredicate(root, query, cb));
        verify(root).get("lastModifiedAt");
        verify(cb).between(lastModifiedPath, rangeStart, rangeEnd);
        verify(cb, never()).isFalse(any());
    }
}
