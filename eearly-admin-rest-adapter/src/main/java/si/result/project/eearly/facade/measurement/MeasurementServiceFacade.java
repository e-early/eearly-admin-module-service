package si.result.project.eearly.facade.measurement;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.transaction.annotation.Transactional;
import si.result.project.eearly.dto.measurement.MeasurementDTO;
import si.result.project.eearly.mapper.measurement.MeasurementMapper;
import si.result.project.eearly.port.measurement.MeasurementService;
import si.result.rest.filter.Filter;
import si.result.spring.boot.bricks.annotation.Facade;

import java.util.UUID;

@Facade
@RequiredArgsConstructor
public class MeasurementServiceFacade {
    private final MeasurementService measurementService;
    private final MeasurementMapper measurementMapper;

    @Transactional(readOnly = true)
    public PagedModel<MeasurementDTO> getPage(final Filter filter, final Pageable pageable) {
        return new PagedModel<>(
                measurementService.findAll(filter.toSpecification(), pageable)
                        .map(measurementMapper::toDto));
    }

    @Transactional(readOnly = true)
    public MeasurementDTO getById(final UUID uuid) {
        return measurementMapper.toDto(measurementService.findById(uuid));
    }

    @Transactional(readOnly = true)
    public MeasurementDTO getByName(final String name) {
        return measurementMapper.toDto(measurementService.findByName(name));
    }

    @Transactional
    public MeasurementDTO create(final MeasurementDTO measurementDTO) {
        return measurementMapper.toDto(measurementService.create(measurementMapper.toCreateCommand(measurementDTO)));
    }

    @Transactional
    public MeasurementDTO update(final UUID uuid, final MeasurementDTO measurementDTO) {
        return measurementMapper.toDto(measurementService.update(measurementMapper.toUpdateCommand(uuid, measurementDTO)));
    }
}
