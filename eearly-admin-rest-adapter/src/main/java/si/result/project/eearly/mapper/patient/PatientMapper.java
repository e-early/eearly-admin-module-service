package si.result.project.eearly.mapper.patient;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import si.result.project.eearly.dto.patient.PatientDTO;
import si.result.project.eearly.dto.patient.PatientLiteDTO;
import si.result.project.eearly.dto.patient.PatientMeasurementRowDTO;
import si.result.project.eearly.dto.patient.PatientMinimalDTO;
import si.result.project.eearly.dto.patient.PatientOnboardingDTO;
import si.result.project.eearly.dto.patient.PatientUpsertDTO;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.measurement.Measurement;
import si.result.project.eearly.model.measurement.MeasurementStatus;
import si.result.project.eearly.model.mobile.CreateMobileUserCommand;
import si.result.project.eearly.model.patient.Patient;
import si.result.project.eearly.model.patient.PatientStatus;
import si.result.project.eearly.model.patient.command.CreatePatientCommand;
import si.result.project.eearly.model.patient.command.UpdatePatientCommand;
import si.result.project.eearly.model.patient.measurement.PatientMeasurementRow;

import java.util.List;
import java.util.UUID;

@Mapper
public interface PatientMapper {

  PatientDTO toDTO(final Patient patient);

  @Mapping(target = "patientStatus", source = "patientStatus")
  @Mapping(target = "measurementStatus", source = "measurementStatus")
  PatientLiteDTO toLiteDTO(final Patient patient, final PatientStatus patientStatus,
      final MeasurementStatus measurementStatus);

  PatientMinimalDTO toMinimalDTO(final Patient patient);

  List<PatientMeasurementRowDTO> toMeasurementRowDTOs(final List<PatientMeasurementRow> measurements);

  PatientMeasurementRowDTO toMeasurementRowDTO(final PatientMeasurementRow measurement);

  CreatePatientCommand toCreateCommand(final PatientUpsertDTO dto, final List<Caretaker> caretakers,
      final List<Measurement> measurements, final List<Algorithm> algorithms);

  @Mapping(target = "id", source = "id")
  UpdatePatientCommand toUpdateCommand(final UUID id, final PatientUpsertDTO dto,
      final List<Caretaker> caretakers,
      final List<Measurement> measurements, final List<Algorithm> algorithms);

  CreateMobileUserCommand toCreateCommand(final Patient patient);

  @Mapping(source = "onboardingUrl", target = "onboardingUrl")
  @Mapping(source = "patient.id", target = "id")
  PatientOnboardingDTO toOnboardingDTO(Patient patient, String onboardingUrl);

}
