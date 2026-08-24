package si.result.project.eearly.port.patient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.patient.Patient;
import si.result.project.eearly.model.patient.command.CreatePatientCommand;
import si.result.project.eearly.model.patient.command.UpdatePatientCommand;
import si.result.spring.boot.bricks.exception.DomainException;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;

    public Patient findById(final UUID id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Patient with ID {} not found", id);
                    return new DomainException(DomainExceptionCode.PATIENT_NOT_FOUND);
                });
    }

    public Page<Patient> findAll(final Specification<Patient> specification, final Pageable pageable) {
        return patientRepository.findAll(specification, pageable);
    }

    public List<Patient> findAllById(final List<UUID> ids) {
        return patientRepository.findAllById(ids);
    }

    public Patient findByHealthInsuranceId(final String healthInsuranceId) {
        return patientRepository.findByHealthInsuranceId(healthInsuranceId);
    }

    public Patient create(CreatePatientCommand command) {
        log.info("Creating patient: {}", command);
        final var patient = Patient.create(command);
        log.debug("Patient created with ID: {}", patient.getId());
        return  patientRepository.save(patient);
    }

    public Patient update(UpdatePatientCommand command) {
        log.info("Updating patient: {}", command);
        final var patient = findById(command.getId());
        patient.update(command);
        log.debug("Patient updated with ID: {}", patient.getId());
        return patientRepository.save(patient);
    }

    public Patient updateKeycloakId(UUID patientId, UUID keycloakId) {
        log.info("Updating Keycloak ID for patient: {} with Keycloak ID: {}", patientId, keycloakId);
        final var patient = findById(patientId);
        patient.setKeycloakId(keycloakId);
        log.debug("Patient Keycloak ID updated for patient ID: {}", patient.getId());
        return patientRepository.save(patient);
    }
}
