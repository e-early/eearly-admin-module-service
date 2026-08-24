package si.result.project.eearly.model.patient.command;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.gender.GenderType;
import si.result.project.eearly.model.measurement.Measurement;
import java.time.LocalDate;
import java.util.List;

@Getter
@EqualsAndHashCode
@RequiredArgsConstructor
public class CreatePatientCommand {

    private final String firstName;
    private final String lastName;
    private final LocalDate dateOfBirth;
    private final GenderType gender;
    private final String healthInsuranceId;
    private final String street;
    private final String city;
    private final String state;
    private final Integer zip;
    private final String country;
    private final String email;
    private final String phoneNumber;

    private final List<Caretaker> caretakers;
    private final List<Measurement> measurements;
    private final List<Algorithm> algorithms;
}
