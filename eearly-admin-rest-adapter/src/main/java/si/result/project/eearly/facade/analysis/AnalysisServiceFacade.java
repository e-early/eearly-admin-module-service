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
import si.result.project.eearly.model.analysis.FeedbackState;
import si.result.project.eearly.model.analysis.command.CreateAnalysisCommand;
import si.result.project.eearly.port.algorithm.AlgorithmService;
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
        switch (feedback) {
            case REJECTED -> state = AnalysisState.REJECTED;
            case CONFIRMED -> state = AnalysisState.CONFIRMED;
            default -> {
                return state;
            }
        }
        return state;
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
                chartDisplayOptionsJson
        );

        final var analysis = analysisService.create(command);

        return toAnalysisDto(analysis);
    }

    @Transactional
    public AnalysisDTO updateResult(final UUID analysisId, final AnalysisResultCallbackDTO callback) {
        final Analysis analysis = analysisService.findById(analysisId);
        analysisDetectionBoxService.replaceAlgorithmDetections(
                analysis,
                DetectionBoxDataMapper.toDataList(callback.detections())
        );
        final Analysis saved = analysisService.updateResult(
                analysisId,
                callback.state(),
                callback.detected()
        );
        return toAnalysisDto(saved);
    }

    /**
     * Persists algorithm prediction boxes before updating analysis state so GET never
     * returns a terminal state with an empty detections list.
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
                AnalysisState.COMPLETED,
                detected
        );
    }

    @Transactional
    public AnalysisDTO updateDetectionFeedback(
            final UUID analysisId,
            final UUID detectionId,
            final DetectionFeedbackDTO body) {
        validateDetectionFeedback(body);
        analysisService.findById(analysisId);
        analysisDetectionBoxService.updateCaretakerFeedback(analysisId, detectionId, body.feedback());
        final Analysis saved = analysisService.findById(analysisId);
        return toAnalysisDto(saved);
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
