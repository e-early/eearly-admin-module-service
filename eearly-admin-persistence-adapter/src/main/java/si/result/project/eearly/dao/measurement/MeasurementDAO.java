package si.result.project.eearly.dao.measurement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import si.result.project.eearly.model.measurement.Measurement;
import si.result.project.eearly.port.measurement.MeasurementRepository;

import java.util.UUID;

@SuppressWarnings("unused")
public interface MeasurementDAO extends MeasurementRepository,
        JpaRepository<Measurement, UUID>, JpaSpecificationExecutor<Measurement> {}
