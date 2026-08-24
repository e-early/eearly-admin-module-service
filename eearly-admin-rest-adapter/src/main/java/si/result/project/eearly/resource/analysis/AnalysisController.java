package si.result.project.eearly.resource.analysis;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import si.result.project.eearly.dto.analysis.AnalysisDTO;
import si.result.project.eearly.dto.analysis.AnalysisFeedbackDTO;
import si.result.project.eearly.dto.analysis.AnalysisLiteDTO;
import si.result.project.eearly.dto.analysis.AnalysisResultCallbackDTO;
import si.result.project.eearly.dto.analysis.CreateAnalysisDTO;
import si.result.project.eearly.dto.analysis.DetectionFeedbackDTO;
import si.result.project.eearly.dto.analysis.DetectionIntervalUpdateDTO;
import si.result.project.eearly.dto.analysis.CaretakerFeedbackSelectionsDTO;
import si.result.project.eearly.dto.feedback.AnalysisFeedbacksResponseDTO;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.facade.analysis.AnalysisServiceFacade;
import si.result.rest.filter.Filter;
import si.result.rest.filter.annotation.PathFilter;
import si.result.spring.boot.bricks.dto.ResponseDTO;
import si.result.spring.boot.bricks.exception.DomainException;

@Tag(name = "Analysis Controller")
@RestController
@RequestMapping("/api/v1/analyses")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisServiceFacade analysisServiceFacade;

    @GetMapping(value = "", produces = "application/json")
    public ResponseEntity<ResponseDTO<PagedModel<AnalysisLiteDTO>>> getPage(
            @ParameterObject @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) final Pageable pageable,
            @PathFilter Filter filter) {
        return new ResponseDTO<>(analysisServiceFacade.getPage(filter, pageable)).ok();
    }

    @GetMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<ResponseDTO<AnalysisDTO>> getById(
            @PathVariable(name = "id") final UUID uuid) {
        return new ResponseDTO<>(analysisServiceFacade.getById(uuid)).ok();
    }

    @PostMapping(value = "", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ResponseDTO<AnalysisDTO>> createAnalysis(
            @RequestBody final CreateAnalysisDTO dto) {
        return new ResponseDTO<>(analysisServiceFacade.create(dto)).ok();
    }

    @PutMapping(value = "/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ResponseDTO<AnalysisDTO>> updateAnalysis(
            @PathVariable(name = "id") final UUID id,
            @RequestBody final AnalysisFeedbackDTO feedbackDTO) {
        return new ResponseDTO<>(analysisServiceFacade.update(id, feedbackDTO)).ok();
    }

    @PutMapping(value = "/{id}/detections/{detectionId}/feedback", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ResponseDTO<AnalysisDTO>> updateDetectionFeedback(
            @PathVariable(name = "id") final UUID id,
            @PathVariable(name = "detectionId") final UUID detectionId,
            @RequestBody final DetectionFeedbackDTO body) {
        return new ResponseDTO<>(analysisServiceFacade.updateDetectionFeedback(id, detectionId, body)).ok();
    }

    @PutMapping(value = "/{id}/detections/{detectionId}/interval", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ResponseDTO<AnalysisDTO>> updateDetectionInterval(
            @PathVariable(name = "id") final UUID id,
            @PathVariable(name = "detectionId") final UUID detectionId,
            @RequestBody final DetectionIntervalUpdateDTO body) {
        return new ResponseDTO<>(analysisServiceFacade.updateDetectionInterval(id, detectionId, body)).ok();
    }

    @Operation(
            summary = "Append caretaker feedback regions",
            description = "Adds new brushed regions as detection boxes with source CARETAKER.")
    @PostMapping(value = "/{id}/caretaker-feedback-detections", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ResponseDTO<AnalysisDTO>> appendCaretakerFeedbackDetections(
            @PathVariable(name = "id") final UUID id,
            @RequestBody final CaretakerFeedbackSelectionsDTO body) {
        return new ResponseDTO<>(analysisServiceFacade.appendCaretakerFeedbackDetections(id, body)).ok();
    }

    @PostMapping(value = "/{id}/result", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ResponseDTO<AnalysisDTO>> updateAnalysisResult(
            @PathVariable(name = "id") final UUID id,
            @RequestBody final AnalysisResultCallbackDTO callback) {
        return new ResponseDTO<>(analysisServiceFacade.updateResult(id, callback)).ok();
    }

    @PostMapping(value = "/results", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ResponseDTO<AnalysisDTO>> updateAnalysisResultNoPath(
            @RequestBody final AnalysisResultCallbackDTO callback) {

        UUID analysisId = callback.analysisId();
        return new ResponseDTO<>(analysisServiceFacade.updateResult(analysisId, callback)).ok();
    }

    @Operation(
            summary = "Get analysis feedback detection boxes for ML sync",
            description = """
                    Initial load (no dates): returns all active detection boxes (is_deleted = false). \
                    Diff load (startDate + endDate): returns every box whose updated_at (lastModifiedAt) falls \
                    in the range, including DELETED boxes, so the ML connector can apply removals.""")
    @GetMapping(value = "/feedbacks", produces = "application/json")
    public ResponseEntity<ResponseDTO<AnalysisFeedbacksResponseDTO>> getAnalysisFeedbacks(
            @Parameter(
                    description = "Start of the date-time range (inclusive), ISO 8601 with offset. "
                            + "Filters detection boxes by updated_at (lastModifiedAt).",
                    example = "2026-01-01T00:00:00+00:00"
            )
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @Parameter(
                    description = "End of the date-time range (inclusive), ISO 8601 with offset. "
                            + "Filters detection boxes by updated_at (lastModifiedAt).",
                    example = "2026-03-31T23:59:59+00:00"
            )
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate) {

        if (startDate != null || endDate != null) {
            if (startDate == null || endDate == null) {
                throw new DomainException(DomainExceptionCode.ANALYSIS_FEEDBACK_DATE_RANGE_INCOMPLETE);
            }
            validateFeedbackDateRange(startDate, endDate);
        }

        return new ResponseDTO<>(
                analysisServiceFacade.getAnalysisFeedbacks(
                        Optional.ofNullable(startDate),
                        Optional.ofNullable(endDate)
                )
        ).ok();
    }

    private void validateFeedbackDateRange(OffsetDateTime startDate, OffsetDateTime endDate) {
        OffsetDateTime now = OffsetDateTime.now();

        if (endDate.isBefore(startDate)) {
            throw new DomainException(DomainExceptionCode.ANALYSIS_FEEDBACK_END_DATE_BEFORE_START_DATE);
        }

        if (endDate.isAfter(now)) {
            throw new DomainException(DomainExceptionCode.ANALYSIS_FEEDBACK_END_DATE_IN_FUTURE);
        }
    }
}
