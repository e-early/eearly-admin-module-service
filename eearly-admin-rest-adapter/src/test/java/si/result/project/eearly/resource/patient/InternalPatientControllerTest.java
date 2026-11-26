package si.result.project.eearly.resource.patient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import si.result.project.eearly.dto.patient.PatientLanguageDTO;
import si.result.project.eearly.facade.patient.InternalPatientServiceFacade;

@ExtendWith(MockitoExtension.class)
class InternalPatientControllerTest {

    @Mock
    private InternalPatientServiceFacade internalPatientServiceFacade;

    @InjectMocks
    private InternalPatientController controller;

    @Test
    void changeLanguage_delegatesToTheFacadeAndReturnsNoContent() {
        UUID keycloakId = UUID.randomUUID();
        PatientLanguageDTO body = new PatientLanguageDTO("en");

        ResponseEntity<Void> response = controller.changeLanguage(keycloakId, body);

        assertEquals(204, response.getStatusCode().value());
        verify(internalPatientServiceFacade).changeLanguage(keycloakId, body);
    }
}
