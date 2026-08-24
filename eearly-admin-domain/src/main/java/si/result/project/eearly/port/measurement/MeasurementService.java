package si.result.project.eearly.port.measurement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.measurement.command.CreateMeasurementCommand;
import si.result.project.eearly.model.measurement.Measurement;
import si.result.project.eearly.model.measurement.command.UpdateMeasurementCommand;
import si.result.spring.boot.bricks.exception.DomainException;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeasurementService {

    private final MeasurementRepository measurementRepository;

    public Page<Measurement> findAll(Specification<Measurement> specification, Pageable pageable) {
        return measurementRepository.findAll(specification, pageable);
    }

    public Measurement findById(UUID id) {
        return measurementRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Measurement with ID {} not found", id);
                    return new DomainException(DomainExceptionCode.MEASUREMENT_NOT_FOUND);
                });
    }

    public List<Measurement> findAllById(final List<UUID> ids) {
        return measurementRepository.findAllById(ids);
    }

    public Measurement findByName(String name) {
        return measurementRepository.findFirstByName(name).orElseThrow(() -> {
            log.error("Measurement with name {} not found", name);
            return new DomainException(DomainExceptionCode.MEASUREMENT_NOT_FOUND);
        });
    }

    public Measurement create(CreateMeasurementCommand command) {
        log.info("Creating measurement: {}", command);
        final var measurement = Measurement.create(command);
        log.debug("Measurement created: {}", measurement);
        return measurementRepository.save(measurement);
    }

    public Measurement update(UpdateMeasurementCommand command) {
        log.info("Updating measurement: {}", command);
        final var measurement = findById(command.getId());
        measurement.update(command);
        return measurementRepository.save(measurement);
    }
}
