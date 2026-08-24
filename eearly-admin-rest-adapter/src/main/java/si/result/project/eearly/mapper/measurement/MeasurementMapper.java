package si.result.project.eearly.mapper.measurement;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import si.result.project.eearly.dto.measurement.MeasurementDTO;
import si.result.project.eearly.dto.measurement.MeasurementLiteDTO;
import si.result.project.eearly.model.measurement.Measurement;
import si.result.project.eearly.model.measurement.command.CreateMeasurementCommand;
import si.result.project.eearly.model.measurement.command.UpdateMeasurementCommand;

import java.util.UUID;

@Mapper
public interface MeasurementMapper {

    MeasurementDTO toDto(final Measurement measurement);

    MeasurementLiteDTO toLiteDto(final Measurement measurement);

    @Mapping(target = "ehrObservationId", source = "ehrObservationId")
    CreateMeasurementCommand toCreateCommand(final MeasurementDTO dto);

    @Mapping(target = "id", source = "id")
    UpdateMeasurementCommand toUpdateCommand(final UUID id, final MeasurementDTO dto);
}
