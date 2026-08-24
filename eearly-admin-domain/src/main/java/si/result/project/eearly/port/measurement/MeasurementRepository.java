package si.result.project.eearly.port.measurement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import si.result.project.eearly.model.measurement.Measurement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MeasurementRepository {

    Page<Measurement> findAll(Specification<Measurement> specification, Pageable pageable);

    List<Measurement> findAllById(Iterable<UUID> ids);

    Optional<Measurement> findById(UUID id);

    Measurement save(Measurement measurement);

    Optional<Measurement> findFirstByName(String name);
}
