package si.result.project.eearly.port.patient;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import si.result.project.eearly.model.patient.Patient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientRepository {

    Optional<Patient> findById(final UUID id);

    Patient findByHealthInsuranceId(final String healthInsuranceId);

    Page<Patient> findAll(final Specification<Patient> specification, final Pageable pageable);

    List<Patient> findAllById(final Iterable<UUID> patientIds);

    Patient save(Patient patient);

}
