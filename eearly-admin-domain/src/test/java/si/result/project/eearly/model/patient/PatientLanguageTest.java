package si.result.project.eearly.model.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.gender.GenderType;
import si.result.project.eearly.model.patient.command.CreatePatientCommand;
import si.result.project.eearly.model.patient.command.UpdatePatientCommand;
import si.result.spring.boot.bricks.exception.DomainException;

class PatientLanguageTest {

    private static CreatePatientCommand create(String language) {
        return new CreatePatientCommand(
                "Ana", "Novak", LocalDate.of(1990, 1, 1), GenderType.FEMALE,
                "123456789", "Street", "City", "State", 1000,
                "Country", "ana@example.com", "+38640000000", language,
                null, null, null);
    }

    private static UpdatePatientCommand update(String language) {
        return new UpdatePatientCommand(
                UUID.randomUUID(), "Ana", "Novak", LocalDate.of(1990, 1, 1), GenderType.FEMALE,
                "123456789", "Street", "City", "State", 1000,
                "Country", "ana@example.com", "+38640000000", language,
                null, null, null);
    }

    @Test
    void create_withoutALanguage_isSlovenian() {
        assertThat(Patient.create(create(null)).getLanguage()).isEqualTo("sl");
    }

    @Test
    void create_keepsTheChosenLanguage() {
        assertThat(Patient.create(create("en")).getLanguage()).isEqualTo("en");
    }

    @Test
    void create_rejectsAnUnsupportedLanguage() {
        assertThatThrownBy(() -> Patient.create(create("de")))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(DomainExceptionCode.PATIENT_LANGUAGE_UNSUPPORTED);
    }

    @Test
    void update_changesTheLanguage() {
        Patient patient = Patient.create(create("sl"));

        patient.update(update("en"));

        assertThat(patient.getLanguage()).isEqualTo("en");
    }

    @Test
    void update_withoutALanguage_keepsTheCurrentOne() {
        Patient patient = Patient.create(create("en"));

        patient.update(update(null));

        assertThat(patient.getLanguage()).isEqualTo("en");
    }
}
