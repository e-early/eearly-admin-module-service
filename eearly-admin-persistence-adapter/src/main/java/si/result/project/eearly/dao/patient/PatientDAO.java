package si.result.project.eearly.dao.patient;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import si.result.project.eearly.model.patient.Patient;
import si.result.project.eearly.port.patient.PatientRepository;

import java.util.UUID;

@SuppressWarnings("unused")
public interface PatientDAO extends PatientRepository,
        JpaRepository<Patient, UUID>, JpaSpecificationExecutor<Patient> {}
