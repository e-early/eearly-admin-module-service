package si.result.project.eearly.mapper.analysis;

import java.util.List;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import si.result.project.eearly.dto.analysis.AnalysisDTO;
import si.result.project.eearly.dto.analysis.AnalysisDetectionDTO;
import si.result.project.eearly.dto.analysis.AnalysisFeedbackDTO;
import si.result.project.eearly.dto.analysis.AnalysisLiteDTO;
import si.result.project.eearly.dto.analysis.AnalysisSelectionDTO;
import si.result.project.eearly.dto.analysis.ChartDisplayOptionsDTO;
import si.result.project.eearly.model.analysis.Analysis;
import si.result.project.eearly.model.analysis.AnalysisDetectionBox;
import si.result.project.eearly.model.analysis.AnalysisSelection;
import si.result.project.eearly.model.analysis.AnalysisState;
import si.result.project.eearly.model.analysis.command.UpdateAnalysisCommand;
import si.result.project.eearly.util.JsonUtils;

@Mapper(uses = {
    si.result.project.eearly.mapper.patient.PatientMapper.class
})
public interface AnalysisMapper {

    @Mapping(target = "state", source = "state")
    AnalysisLiteDTO toLiteDTO(final Analysis analysis, final AnalysisState state);

    @Mapping(target = "state", source = "state")
    @Mapping(target = "inputParameters", expression = "java(mapInputParameters(analysis))")
    @Mapping(target = "detections", expression = "java(mapAlgorithmDetections(detectionBoxes))")
    @Mapping(target = "caretakerFeedbackDetections", expression = "java(mapCaretakerFeedbackDetections(detectionBoxes))")
    @Mapping(target = "chartDisplayOptions", expression = "java(mapChartDisplayOptions(analysis))")
    AnalysisDTO toDTO(final Analysis analysis, final AnalysisState state, final List<AnalysisDetectionBox> detectionBoxes);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "feedback", source = "dto.feedback")
    UpdateAnalysisCommand toUpdateCommand(final UUID id, final AnalysisFeedbackDTO dto);

    default List<AnalysisDetectionDTO> mapAlgorithmDetections(final List<AnalysisDetectionBox> detectionBoxes) {
        return AnalysisDetectionBoxMapper.splitBySource(detectionBoxes).detections();
    }

    default List<AnalysisDetectionDTO> mapCaretakerFeedbackDetections(final List<AnalysisDetectionBox> detectionBoxes) {
        return AnalysisDetectionBoxMapper.splitBySource(detectionBoxes).caretakerFeedbackDetections();
    }

    default List<AnalysisSelectionDTO> mapInputParameters(final Analysis analysis) {
        if (analysis == null) {
            return List.of();
        }

        return analysis.getInputParameters().stream()
                .map(this::toSelectionDTO)
                .toList();
    }

    default ChartDisplayOptionsDTO mapChartDisplayOptions(final Analysis analysis) {
        if (analysis == null) return null;
        String json = analysis.getChartDisplayOptions();
        if (json == null || json.isBlank()) return null;
        try {
            return JsonUtils.fromJson(json, ChartDisplayOptionsDTO.class);
        } catch (Exception e) {
            return null;
        }
    }

    private AnalysisSelectionDTO toSelectionDTO(final AnalysisSelection selection) {
        return new AnalysisSelectionDTO(
                selection.startTimestamp(),
                selection.endTimestamp(),
                selection.measurementIds()
        );
    }
}
