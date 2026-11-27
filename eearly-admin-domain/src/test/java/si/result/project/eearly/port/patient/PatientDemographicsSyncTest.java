package si.result.project.eearly.port.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import si.result.project.eearly.model.gender.GenderType;
import si.result.project.eearly.model.patient.Patient;
import si.result.project.eearly.model.patient.command.CreatePatientCommand;

@ExtendWith(MockitoExtension.class)
class PatientDemographicsSyncTest {

    @Mock
    private PatientService patientService;
    @Mock
    private PatientDemographicsClient patientDemographicsClient;

    @InjectMocks
    private PatientDemographicsSync patientDemographicsSync;

    private static Patient patient(UUID keycloakId, LocalDate dateOfBirth, GenderType gender) {
        Patient patient = Patient.create(new CreatePatientCommand(
                "Test", "1", dateOfBirth, gender,
                "123456789", "Street", "City", "State", 1000,
                "Country", "test@example.com", "+38640000000", null,
                null, null, null, null));
        patient.setKeycloakId(keycloakId);
        return patient;
    }

    @Test
    void sync_sendsThePatientsDateOfBirthGenderAndLanguage() {
        UUID keycloakId = UUID.randomUUID();
        Patient patient = patient(keycloakId, LocalDate.of(2002, 3, 11), GenderType.MALE);
        patient.changeLanguage("en");

        boolean synced = patientDemographicsSync.sync(patient);

        assertThat(synced).isTrue();
        verify(patientDemographicsClient)
                .upsertDemographics(keycloakId, LocalDate.of(2002, 3, 11), GenderType.MALE, "en");
    }

    @Test
    void sync_sendsSlovenianForAPatientWhoseLanguageWasNeverSet() {
        UUID keycloakId = UUID.randomUUID();

        patientDemographicsSync.sync(patient(keycloakId, LocalDate.of(2002, 3, 11), GenderType.MALE));

        verify(patientDemographicsClient)
                .upsertDemographics(keycloakId, LocalDate.of(2002, 3, 11), GenderType.MALE, "sl");
    }

    @Test
    void sync_skipsAPatientWithoutAMobileAccount() {
        boolean synced = patientDemographicsSync.sync(
                patient(null, LocalDate.of(2002, 3, 11), GenderType.MALE));

        assertThat(synced).isFalse();
        verifyNoInteractions(patientDemographicsClient);
    }

    @Test
    void sync_doesNotFailWhenTheMobileBackendCannotBeReached() {
        UUID keycloakId = UUID.randomUUID();
        doThrow(new RuntimeException("UNAVAILABLE")).when(patientDemographicsClient)
                .upsertDemographics(any(), any(), any(), any());

        boolean synced = patientDemographicsSync.sync(
                patient(keycloakId, LocalDate.of(2002, 3, 11), GenderType.FEMALE));

        assertThat(synced).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void syncAll_goesThroughEveryPageAndCarriesOnAfterAFailure() {
        Patient first = patient(UUID.randomUUID(), LocalDate.of(1980, 1, 1), GenderType.MALE);
        Patient failing = patient(UUID.randomUUID(), LocalDate.of(1990, 1, 1), GenderType.FEMALE);
        Patient last = patient(UUID.randomUUID(), LocalDate.of(2000, 1, 1), GenderType.OTHER);
        Pageable firstPage = PageRequest.of(0, 2);
        when(patientService.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, failing), firstPage, 3))
                .thenReturn(new PageImpl<>(List.of(last), firstPage.next(), 3));
        // Lenient: the other patients' calls don't match this stub, and under strict stubbing that
        // would itself throw — which the sync would then count as a failure.
        lenient().doThrow(new RuntimeException("UNAVAILABLE")).when(patientDemographicsClient)
                .upsertDemographics(failing.getKeycloakId(), failing.getDateOfBirth(), failing.getGender(), "sl");

        int synced = patientDemographicsSync.syncAll();

        assertThat(synced).isEqualTo(2);
        verify(patientDemographicsClient)
                .upsertDemographics(first.getKeycloakId(), first.getDateOfBirth(), first.getGender(), "sl");
        verify(patientDemographicsClient)
                .upsertDemographics(last.getKeycloakId(), last.getDateOfBirth(), last.getGender(), "sl");
    }

    @Test
    @SuppressWarnings("unchecked")
    void syncAll_withNoPatients_sendsNothing() {
        when(patientService.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        assertThat(patientDemographicsSync.syncAll()).isZero();
        verify(patientDemographicsClient, never()).upsertDemographics(any(), any(), any(), any());
    }
}
