package si.result.project.eearly.model.patient.command;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.gender.GenderType;
import si.result.project.eearly.model.measurement.Measurement;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = true)
public class UpdatePatientCommand extends CreatePatientCommand {
    private final UUID id;

    @SuppressWarnings({"squid:S00107", "Intentionally suppressed warning"})
    public UpdatePatientCommand(UUID id, String firstName, String lastName, LocalDate dateOfBirth,
                                GenderType gender, String healthInsuranceId, String street,
                                String city, String state, Integer zip, String country,
                                String email, String phoneNumber, List<Caretaker> caretakers,
                                List<Measurement> measurements,
                                List<Algorithm> algorithms) {
        super(firstName, lastName, dateOfBirth, gender, healthInsuranceId, street,
                city, state, zip, country, email, phoneNumber,
                caretakers, measurements, algorithms);
        this.id = id;
    }
}
