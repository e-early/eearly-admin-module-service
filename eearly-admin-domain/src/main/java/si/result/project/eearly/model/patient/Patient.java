package si.result.project.eearly.model.patient;

import com.google.common.base.Objects;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.analysis.Analysis;
import si.result.project.eearly.model.auditable.Auditable;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.gender.GenderType;
import si.result.project.eearly.model.measurement.Measurement;
import si.result.project.eearly.model.patient.command.CreatePatientCommand;
import si.result.project.eearly.model.patient.command.UpdatePatientCommand;
import si.result.spring.boot.bricks.validation.Condition;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Table(name = "t_patient")
public class Patient extends Auditable {

    @Id
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Setter
    @Column(name = "keycloak_id")
    private UUID keycloakId;

    @Column(name = "first_name", length = 50)
    private String firstName;

    @Column(name = "last_name", length = 50)
    private String lastName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "gender", length = 2)
    private GenderType gender;

    @Column(name = "health_insurance_id", length = 20)
    private String healthInsuranceId;

    @Column(name = "street", length = 100)
    private String street;

    @Column(name = "city", length = 50)
    private String city;

    @Column(name = "state", length = 50)
    private String state;

    @Column(name = "zip")
    private Integer zip;

    @Column(name = "country", length = 50)
    private String country;

    @Column(name = "email", length = 50)
    private String email;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    @Column(name = "timezone", length = 64, nullable = false)
    private String timezone = "Europe/Ljubljana";

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "t_patient_caretaker",
            joinColumns = @JoinColumn(name = "patient_id"),
            inverseJoinColumns = @JoinColumn(name = "caretaker_id")
    )
    private List<Caretaker> caretakers;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "t_patient_measurement",
            joinColumns = @JoinColumn(name = "patient_id"),
            inverseJoinColumns = @JoinColumn(name = "measurement_id")
    )
    private List<Measurement> measurements;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "patient")
    private List<Analysis> analyses;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "t_patient_algorithm",
            joinColumns = @JoinColumn(name = "patient_id"),
            inverseJoinColumns = @JoinColumn(name = "algorithm_id")
    )
    private List<Algorithm> algorithms;

    protected Patient() {}

    public static Patient create(final CreatePatientCommand command) {
        final Patient patient = new Patient();
        patient.setFirstName(command.getFirstName());
        patient.setLastName(command.getLastName());
        patient.setDateOfBirth(command.getDateOfBirth());
        patient.setGender(command.getGender());
        patient.setHealthInsuranceId(command.getHealthInsuranceId());
        patient.setStreet(command.getStreet());
        patient.setCity(command.getCity());
        patient.setState(command.getState());
        patient.setZip(command.getZip());
        patient.setCountry(command.getCountry());
        patient.setEmail(command.getEmail());
        patient.setPhoneNumber(command.getPhoneNumber());
        patient.setCaretakers(command.getCaretakers());
        patient.setMeasurements(command.getMeasurements());
        patient.setAlgorithms(command.getAlgorithms());

        return patient;
    }

    public void update(final UpdatePatientCommand command) {
        setFirstName(command.getFirstName());
        setLastName(command.getLastName());
        setDateOfBirth(command.getDateOfBirth());
        setGender(command.getGender());
        setHealthInsuranceId(command.getHealthInsuranceId());
        setStreet(command.getStreet());
        setCity(command.getCity());
        setState(command.getState());
        setZip(command.getZip());
        setCountry(command.getCountry());
        setEmail(command.getEmail());
        setPhoneNumber(command.getPhoneNumber());
        setCaretakers(command.getCaretakers());
        setMeasurements(command.getMeasurements());
        setAlgorithms(command.getAlgorithms());
    }

    private void setFirstName(final String firstName) {
        Condition.checkNotNull(firstName, DomainExceptionCode.PATIENT_FIRSTNAME_NULL);
        Condition.check(firstName.length() <= 50, DomainExceptionCode.PATIENT_FIRSTNAME_TOO_LONG);
        this.firstName = firstName;
    }

    private void setLastName(final String lastName) {
        Condition.checkNotNull(lastName, DomainExceptionCode.PATIENT_LASTNAME_NULL);
        Condition.check(lastName.length() <= 50, DomainExceptionCode.PATIENT_LASTNAME_TOO_LONG);
        this.lastName = lastName;
    }

    private void setDateOfBirth(final LocalDate dateOfBirth) {
        Condition.checkNotNull(dateOfBirth, DomainExceptionCode.PATIENT_DATE_OF_BIRTH_NULL);
        Condition.check(dateOfBirth.isBefore(LocalDate.now()), DomainExceptionCode.PATIENT_DATE_OF_BIRTH_INVALID);
        this.dateOfBirth = dateOfBirth;
    }

    private void setGender(final GenderType gender) {
        Condition.checkNotNull(gender, DomainExceptionCode.PATIENT_GENDER_NULL);
        this.gender = gender;
    }

    private void setHealthInsuranceId(final String healthInsuranceId) {
        Condition.checkNotNull(healthInsuranceId, DomainExceptionCode.PATIENT_HEALTH_INSURANCE_ID_NULL);
        Condition.check(healthInsuranceId.length() <= 20, DomainExceptionCode.PATIENT_HEALTH_INSURANCE_ID_TOO_LONG);
        this.healthInsuranceId = healthInsuranceId;
    }

    private void setStreet(final String street) {
        Condition.checkNotNull(street, DomainExceptionCode.PATIENT_STREET_NULL);
        Condition.check(street.length() <= 100, DomainExceptionCode.PATIENT_STREET_TOO_LONG);
        this.street = street;
    }

    private void setCity(final String city) {
        Condition.checkNotNull(city, DomainExceptionCode.PATIENT_CITY_NULL);
        Condition.check(city.length() <= 50, DomainExceptionCode.PATIENT_CITY_TOO_LONG);
        this.city = city;
    }

    private void setState(final String state) {
        Condition.checkNotNull(state, DomainExceptionCode.PATIENT_STATE_NULL);
        Condition.check(state.length() <= 50, DomainExceptionCode.PATIENT_STATE_TOO_LONG);
        this.state = state;
    }

    private void setZip(final Integer zip) {
        Condition.checkNotNull(zip, DomainExceptionCode.PATIENT_ZIP_NULL);
        this.zip = zip;
    }

    private void setCountry(final String country) {
        Condition.checkNotNull(country, DomainExceptionCode.PATIENT_COUNTRY_NULL);
        Condition.check(country.length() <= 50, DomainExceptionCode.PATIENT_COUNTRY_TOO_LONG);
        this.country = country;
    }

    private void setEmail(final String email) {
        Condition.checkNotNull(email, DomainExceptionCode.PATIENT_EMAIL_NULL);
        Condition.check(email.length() <= 100, DomainExceptionCode.PATIENT_EMAIL_TOO_LONG);
        this.email = email;
    }

    private void setPhoneNumber(final String phoneNumber) {
        Condition.checkNotNull(phoneNumber, DomainExceptionCode.PATIENT_PHONE_NUMBER_NULL);
        Condition.check(phoneNumber.length() <= 20, DomainExceptionCode.PATIENT_PHONE_NUMBER_TOO_LONG);
        this.phoneNumber = phoneNumber;
    }

    private void setCaretakers(List<Caretaker> caretakers) {
        this.caretakers = caretakers;
    }

    private void setMeasurements(List<Measurement> measurements) {
        this.measurements = measurements;
    }

    private void setAlgorithms(List<Algorithm> algorithms) {
        this.algorithms = algorithms;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Patient patient)) {
            return false;
        }
        return Objects.equal(id, patient.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
