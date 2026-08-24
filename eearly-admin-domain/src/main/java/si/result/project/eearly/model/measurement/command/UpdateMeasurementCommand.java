package si.result.project.eearly.model.measurement.command;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import si.result.project.eearly.model.measurement.MeasurementChartType;
import si.result.project.eearly.model.measurement.MeasurementType;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = true)
public class UpdateMeasurementCommand extends CreateMeasurementCommand {

    private final UUID id;

    @SuppressWarnings({"squid:S00107", "Intentionally suppressed warning"})
    public UpdateMeasurementCommand(UUID id, String name, MeasurementType type, MeasurementChartType chartType, String ehrObservationId) {
        super(name, type, chartType, ehrObservationId);
        this.id = id;
    }
}
