package si.result.project.eearly.model.measurement.command;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import si.result.project.eearly.model.measurement.MeasurementChartType;
import si.result.project.eearly.model.measurement.MeasurementType;

@Getter
@EqualsAndHashCode
@RequiredArgsConstructor
public class CreateMeasurementCommand {

    private final String name;

    private final MeasurementType type;

    private final MeasurementChartType chartType;

    private final String ehrObservationId;

}