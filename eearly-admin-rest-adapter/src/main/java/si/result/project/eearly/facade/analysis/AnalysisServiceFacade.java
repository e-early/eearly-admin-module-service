package si.result.project.eearly.facade.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.transaction.annotation.Transactional;
import si.result.project.eearly.dto.analysis.AnalysisDetectionDTO;
import si.result.project.eearly.dto.analysis.AnalysisDTO;
import si.result.project.eearly.dto.analysis.AnalysisFeedbackDTO;
import si.result.project.eearly.dto.analysis.AnalysisLiteDTO;
import si.result.project.eearly.dto.analysis.AnalysisResultCallbackDTO;
import si.result.project.eearly.dto.analysis.CreateAnalysisDTO;
import si.result.project.eearly.dto.analysis.DetectionFeedbackDTO;
import si.result.project.eearly.dto.analysis.DetectionIntervalUpdateDTO;
import si.result.project.eearly.dto.analysis.CaretakerFeedbackSelectionsDTO;
import si.result.project.eearly.dto.feedback.AnalysisFeedbackPayloadDTO;
import si.result.project.eearly.dto.feedback.AnalysisFeedbacksResponseDTO;
import si.result.project.eearly.mapper.analysis.AnalysisFeedbackMapper;
import si.result.project.eearly.mapper.analysis.AnalysisMapper;
import si.result.project.eearly.mapper.analysis.AnalysisSelectionMapper;
import si.result.project.eearly.mapper.analysis.DetectionBoxDataMapper;
import si.result.project.eearly.model.analysis.Analysis;
import si.result.project.eearly.model.analysis.AnalysisDetectionBox;
import si.result.project.eearly.model.analysis.AnalysisState;
import si.result.project.eearly.model.analysis.AnalysisTriggerType;
import si.result.project.eearly.model.analysis.DetectionBoxSources;
import si.result.project.eearly.model.analysis.FeedbackState;
import si.result.project.eearly.model.analysis.command.CreateAnalysisCommand;
import si.result.project.eearly.port.algorithm.AlgorithmService;
import si.result.project.eearly.port.algorithmexecution.AlgorithmExecutionService;
import si.result.project.eearly.port.algorithmexecution.CallbackTokenService;
import si.result.project.eearly.port.analysis.AnalysisDetectionBoxService;
import si.result.project.eearly.port.analysis.AnalysisService;
import si.result.project.eearly.port.patient.PatientService;
import si.result.project.eearly.util.JsonUtils;
import si.result.rest.filter.Filter;
import si.result.spring.boot.bricks.annotation.Facade;

@Slf4j
@Facade
@RequiredArgsConstructor
public class AnalysisServiceFacade {

    private final AnalysisService analysisService;
    private final AlgorithmService algorithmService;
    private final AnalysisMapper analysisMapper;
    private final PatientService patientService;
    private final AnalysisDetectionBoxService analysisDetectionBoxService;
    private final AlgorithmExecutionService algorithmExecutionService;
    private final CallbackTokenService callbackTokenService;

    @Transactional(readOnly = true)
    public PagedModel<AnalysisLiteDTO> getPage(final Filter filter, final Pageable pageable) {
        return new PagedModel<>(
                analysisService.findAll(filter.toSpecification(), pageable)
                        .map(this::mapAnalysisWithStatus));
    }

    private AnalysisLiteDTO mapAnalysisWithStatus(Analysis analysis) {
        return analysisMapper.toLiteDTO(
                analysis,
                getAnalysisState(analysis.getState(), analysis.getFeedback())
        );
    }

    public AnalysisState getAnalysisState(AnalysisState state, FeedbackState feedback) {
        return AnalysisState.fromFeedback(state, feedback);
    }

    @Transactional(readOnly = true)
    public AnalysisDTO getById(final UUID uuid) {
        Analysis analysis = analysisService.findById(uuid);
        return toAnalysisDto(analysis);
    }

    @Transactional
    public AnalysisDTO update(final UUID uuid, final AnalysisFeedbackDTO analysisFeedbackDTO) {
        Analysis updatedAnalysis = analysisService.update(analysisMapper.toUpdateCommand(uuid, analysisFeedbackDTO));
        return toAnalysisDto(updatedAnalysis);
    }

    @Transactional
    public AnalysisDTO create(final CreateAnalysisDTO createAnalysisDTO) {
        final var patient = patientService.findById(createAnalysisDTO.patientId());
        final var algorithm = algorithmService.findById(createAnalysisDTO.algorithmId());
        final var selections = AnalysisSelectionMapper.toDomainList(createAnalysisDTO.selections());

        String chartDisplayOptionsJson = null;
        if (createAnalysisDTO.chartDisplayOptions() != null) {
            try {
                chartDisplayOptionsJson = JsonUtils.toJson(createAnalysisDTO.chartDisplayOptions());
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Invalid chartDisplayOptions", e);
            }
        }

        final var command = new CreateAnalysisCommand(
                createAnalysisDTO.name(),
                AnalysisState.IN_PROGRESS,
                FeedbackState.NONE,
                Boolean.FALSE,
                patient,
                algorithm,
                selections,
                chartDisplayOptionsJson,
                AnalysisTriggerType.MANUAL
        );

        final var analysis = analysisService.create(command);

        return toAnalysisDto(analysis);
    }

    @Transactional
    public AnalysisDTO updateResult(final UUID analysisId, final AnalysisResultCallbackDTO callback) {
        return toAnalysisDto(applyResult(analysisId, callback).analysis());
    }

    /**
     * Result reported from outside, by the HTTP endpoints. If the request was authenticated by a callback token (a
     * non-null {@code callbackToken}) the token is verified against {@code analysisId} and used up, in this very
     * transaction: a result that fails to process rolls the use of the token back, so the provider may retry with
     * the same token. A request authenticated by a Keycloak JWT has no token and is not checked here.
     *
     * <p>The still-active execution of the analysis, if any, is completed (or failed, for a result in the ERROR
     * state) with it, in the same transaction, so that it does not stay RUNNING until the stuck-execution sweep
     * fails it.
     */
    @Transactional
    public AnalysisDTO updateResult(
            final UUID analysisId,
            final AnalysisResultCallbackDTO callback,
            final String callbackToken) {
        if (callbackToken != null) {
            callbackTokenService.verifyAndConsume(callbackToken, analysisId);
        }
        final AppliedResult applied = applyResult(analysisId, callback);
        completeLinkedAlgorithmExecution(analysisId, applied.resolvedState(), callback);
        return toAnalysisDto(applied.analysis());
    }

    private record AppliedResult(Analysis analysis, AnalysisState resolvedState) {
    }

    private AppliedResult applyResult(final UUID analysisId, final AnalysisResultCallbackDTO callback) {
        final Analysis analysis = analysisService.findById(analysisId);
        analysisDetectionBoxService.replaceAlgorithmDetections(
                analysis,
                DetectionBoxDataMapper.toDataList(callback.detections())
        );
        final AnalysisState resolvedState = resolveStateAfterAlgorithmResult(
                callback.state(),
                callback.detections()
        );
        final Analysis saved = analysisService.updateResult(
                analysisId,
                resolvedState,
                resolveDetectedValue(callback)
        );
        return new AppliedResult(saved, resolvedState);
    }

    private void completeLinkedAlgorithmExecution(
            final UUID analysisId, final AnalysisState resolvedState, final AnalysisResultCallbackDTO callback) {
        algorithmExecutionService.findLatestActiveByAnalysisId(analysisId).ifPresent(execution -> {
            if (resolvedState == AnalysisState.ERROR) {
                algorithmExecutionService.failFromCallback(execution.getId(), "Callback reported ERROR state");
                return;
            }
            String resultData = null;
            try {
                resultData = JsonUtils.toJson(callback);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize callback for AlgorithmExecution {}", execution.getId(), e);
            }
            algorithmExecutionService.completeFromCallback(execution.getId(), resultData);
        });
    }

    /**
     * The analysis detected flag is mandatory, but callers of this callback may omit the field. Falls back to
     * whether the callback reported any detections at all, the same signal
     * {@link #resolveStateAfterAlgorithmResult} uses.
     */
    static boolean resolveDetectedValue(final AnalysisResultCallbackDTO callback) {
        if (callback.detected() != null) {
            return callback.detected();
        }
        return callback.detections() != null && !callback.detections().isEmpty();
    }

    /**
     * Persists algorithm prediction boxes before updating analysis state so GET never
     * returns WAITING_FOR_CONFIRMATION with an empty detections list.
     * Zero algorithm boxes resolve to COMPLETED (nothing to confirm).
     */
    @Transactional
    public void persistAlgorithmPredictions(
            final UUID analysisId,
            final boolean detected,
            final List<AnalysisDetectionDTO> detections,
            final Double modelThreshold) {
        final Analysis analysis = analysisService.findById(analysisId);
        if (modelThreshold != null && analysis.getAlgorithm() != null) {
            algorithmService.updateModelThreshold(analysis.getAlgorithm().getId(), modelThreshold);
        }
        analysisDetectionBoxService.replaceAlgorithmDetections(
                analysis,
                DetectionBoxDataMapper.toDataList(detections)
        );
        analysisService.updateResult(
                analysisId,
                resolveStateAfterAlgorithmResult(AnalysisState.WAITING_FOR_CONFIRMATION, detections),
                detected
        );
    }

    /**
     * Successful algorithm finish with detections resolves to WAITING_FOR_CONFIRMATION;
     * no detections resolve to COMPLETED; ERROR stays ERROR.
     */
    static AnalysisState resolveStateAfterAlgorithmResult(
            final AnalysisState requestedState,
            final List<?> detections) {
        if (requestedState == AnalysisState.ERROR) {
            return AnalysisState.ERROR;
        }
        if (detections == null || detections.isEmpty()) {
            return AnalysisState.COMPLETED;
        }
        return AnalysisState.WAITING_FOR_CONFIRMATION;
    }

    @Transactional
    public AnalysisDTO updateDetectionFeedback(
            final UUID analysisId,
            final UUID detectionId,
            final DetectionFeedbackDTO body) {
        validateDetectionFeedback(body);
        final Analysis analysis = analysisService.findById(analysisId);
        analysisDetectionBoxService.updateCaretakerFeedback(analysisId, detectionId, body.feedback());

        final List<AnalysisDetectionBox> boxes =
                analysisDetectionBoxService.findActiveByAnalysisId(analysisId);
        if (shouldCompleteAfterDetectionFeedback(analysis, boxes)) {
            analysisService.updateState(analysisId, AnalysisState.COMPLETED);
        }

        final Analysis saved = analysisService.findById(analysisId);
        return toAnalysisDto(saved);
    }

    /**
     * Completes the analysis when every algorithm detection box has caretaker feedback.
     * Caretaker-created boxes are ignored.
     */
    static boolean shouldCompleteAfterDetectionFeedback(
            final Analysis analysis,
            final List<AnalysisDetectionBox> boxes) {
        if (analysis == null || analysis.getState() != AnalysisState.WAITING_FOR_CONFIRMATION) {
            return false;
        }
        return allAlgorithmDetectionsHaveFeedback(boxes);
    }

    static boolean allAlgorithmDetectionsHaveFeedback(final List<AnalysisDetectionBox> boxes) {
        if (boxes == null || boxes.isEmpty()) {
            return false;
        }
        final List<AnalysisDetectionBox> algorithmBoxes = boxes.stream()
                .filter(box -> !DetectionBoxSources.isCaretakerSource(box.getSource()))
                .toList();
        if (algorithmBoxes.isEmpty()) {
            return false;
        }
        return algorithmBoxes.stream().allMatch(AnalysisServiceFacade::hasCaretakerFeedback);
    }

    private static boolean hasCaretakerFeedback(final AnalysisDetectionBox box) {
        final FeedbackState feedback = box.getCaretakerFeedback();
        return feedback == FeedbackState.CONFIRMED || feedback == FeedbackState.REJECTED;
    }

    @Transactional
    public AnalysisDTO updateDetectionInterval(
            final UUID analysisId,
            final UUID detectionId,
            final DetectionIntervalUpdateDTO body) {
        validateDetectionIntervalUpdate(body);
        analysisService.findById(analysisId);
        analysisDetectionBoxService.updateDetectionInterval(
                analysisId,
                detectionId,
                body.startTimestamp(),
                body.endTimestamp()
        );
        final Analysis saved = analysisService.findById(analysisId);
        return toAnalysisDto(saved);
    }

    @Transactional
    public AnalysisDTO appendCaretakerFeedbackDetections(final UUID analysisId, final CaretakerFeedbackSelectionsDTO body) {
        if (body == null || body.selections() == null || body.selections().isEmpty()) {
            throw new IllegalArgumentException("selections must not be empty");
        }
        final Analysis analysis = analysisService.findById(analysisId);
        analysisDetectionBoxService.appendCaretakerFeedbackBoxes(
                analysis,
                AnalysisSelectionMapper.toDomainList(body.selections())
        );
        final Analysis saved = analysisService.findById(analysisId);
        return toAnalysisDto(saved);
    }

    private AnalysisDTO toAnalysisDto(final Analysis analysis) {
        final List<AnalysisDetectionBox> detectionBoxes =
                analysisDetectionBoxService.findActiveByAnalysisId(analysis.getId());
        return analysisMapper.toDTO(
                analysis,
                getAnalysisState(analysis.getState(), analysis.getFeedback()),
                detectionBoxes
        );
    }

    @Transactional(readOnly = true)
    public AnalysisFeedbacksResponseDTO getAnalysisFeedbacks(
            final Optional<OffsetDateTime> startDate,
            final Optional<OffsetDateTime> endDate) {
        final List<AnalysisDetectionBox> boxes =
                analysisDetectionBoxService.findForFeedbacks(startDate, endDate);
        if (boxes.isEmpty()) {
            return new AnalysisFeedbacksResponseDTO(List.of());
        }

        final Map<UUID, List<AnalysisDetectionBox>> boxesByAnalysisId = new LinkedHashMap<>();
        for (final AnalysisDetectionBox box : boxes) {
            final UUID analysisId = box.getAnalysis().getId();
            boxesByAnalysisId.computeIfAbsent(analysisId, ignored -> new ArrayList<>()).add(box);
        }

        final List<AnalysisFeedbackPayloadDTO> payloads = new ArrayList<>();
        for (final Map.Entry<UUID, List<AnalysisDetectionBox>> entry : boxesByAnalysisId.entrySet()) {
            final Analysis analysis = analysisService.findById(entry.getKey());
            final var payload = AnalysisFeedbackMapper.toFeedbackPayload(analysis, entry.getValue());
            if (payload != null) {
                payloads.add(payload);
            }
        }

        return new AnalysisFeedbacksResponseDTO(payloads);
    }

    private static void validateDetectionFeedback(final DetectionFeedbackDTO body) {
        if (body == null || body.feedback() == null) {
            throw new IllegalArgumentException("Detection feedback must be provided");
        }
        if (body.feedback() == FeedbackState.NONE) {
            throw new IllegalArgumentException("Detection feedback must be CONFIRMED or REJECTED");
        }
    }

    private static void validateDetectionIntervalUpdate(final DetectionIntervalUpdateDTO body) {
        if (body == null || body.startTimestamp() == null || body.endTimestamp() == null) {
            throw new IllegalArgumentException("startTimestamp and endTimestamp must be provided");
        }
        if (!body.startTimestamp().isBefore(body.endTimestamp())) {
            throw new IllegalArgumentException("startTimestamp must be before endTimestamp");
        }
    }

}
