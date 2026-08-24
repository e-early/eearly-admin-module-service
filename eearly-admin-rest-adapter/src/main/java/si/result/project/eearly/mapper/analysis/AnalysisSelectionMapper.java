package si.result.project.eearly.mapper.analysis;

import java.util.List;
import si.result.project.eearly.dto.analysis.AnalysisSelectionDTO;
import si.result.project.eearly.model.analysis.AnalysisSelection;

public final class AnalysisSelectionMapper {

    private AnalysisSelectionMapper() {}

    public static List<AnalysisSelection> toDomainList(final List<AnalysisSelectionDTO> selections) {
        if (selections == null || selections.isEmpty()) {
            return List.of();
        }

        return selections.stream()
                .map(AnalysisSelectionMapper::toDomain)
                .toList();
    }

    public static AnalysisSelection toDomain(final AnalysisSelectionDTO selection) {
        return new AnalysisSelection(
                selection.startTimestamp(),
                selection.endTimestamp(),
                selection.measurementIds()
        );
    }
}
