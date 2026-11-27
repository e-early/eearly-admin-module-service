package si.result.project.eearly.config.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jwt.JWTClaimsSet;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.web.SpringDataWebAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import si.result.project.eearly.dto.qrcode.QRCodeDTO;
import si.result.project.eearly.dto.qrcode.QRCodeLiteDTO;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.facade.qrcode.QRCodeServiceFacade;
import si.result.project.eearly.mapper.qrcode.QRCodeMapper;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.patient.Patient;
import si.result.project.eearly.model.qrcode.QRCode;
import si.result.project.eearly.model.qrcode.QRCodeStatus;
import si.result.project.eearly.port.caretaker.CaretakerService;
import si.result.project.eearly.port.email.AsyncEmailService;
import si.result.project.eearly.port.patient.PatientService;
import si.result.project.eearly.port.qrcode.QRCodeService;
import si.result.project.eearly.resource.qrcode.QRCodeController;
import si.result.project.eearly.util.qrcode.QRCodeImageService;
import si.result.project.eearly.util.qrcode.QRCodeUtilityService;
import si.result.spring.boot.bricks.auth.AuthenticationProvider;
import si.result.spring.boot.bricks.auth.AuthenticationProviderImpl;
import si.result.spring.boot.bricks.exception.DomainException;

/**
 * Who may call the QR code endpoints: anyone authenticated whose token subject is a caretaker's Keycloak id, for the
 * QR codes of the patients assigned to that caretaker. No client role is needed or sufficient: an administrator or a
 * service account that is no caretaker has no QR codes, and anonymous callers are turned away. The real facade runs
 * here, with the domain services mocked.
 */
@WebMvcTest(QRCodeController.class)
@ContextConfiguration(classes = {SecurityConfig.class, QRCodeController.class, QRCodeServiceFacade.class,
        QRCodeEndpointSecurityTest.Auth.class})
@Import({si.result.spring.boot.bricks.advice.ErrorResponseHandler.class, SpringDataWebAutoConfiguration.class})
@TestPropertySource(properties = {
        "spring.config.name=qrcode-endpoint-security-test",
        // The first test context of a JVM initialises logging for all later ones (the log format
        // characterization test reads service.name), so name the application as the service does.
        "spring.application.name=eearly-admin-service",
        "spring.security.cors.allowed-origins=*",
        "spring.security.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS",
        "admin.keycloak.ui-client-id=test-client"
})
class QRCodeEndpointSecurityTest {

    @TestConfiguration
    static class Auth {
        @Bean
        AuthenticationProvider authenticationProvider() {
            return new AuthenticationProviderImpl();
        }
    }

    private static final UUID CARETAKER_ID = UUID.fromString("50287036-1b84-4e2e-831f-91ab621e52d9");
    private static final UUID KEYCLOAK_ID = UUID.fromString("0c6c3b3e-8d6e-4d9a-9a52-1d6f6a8e2b11");
    private static final UUID OTHER_CARETAKER_ID = UUID.fromString("6f1a1c5e-3a52-4c0f-8c1f-0a0f6f0c7d22");
    private static final UUID OTHER_KEYCLOAK_ID = UUID.fromString("a9d0a7c4-5b19-4e0c-b6f4-3c1d7f9b8e33");
    private static final UUID PATIENT_ID = UUID.fromString("52378678-4cad-4f7b-893e-9e129e47824a");
    private static final UUID QR_CODE_ID = UUID.fromString("7a1d9c0e-2f4b-4d6a-8e35-9b1c3d5f7a91");
    private static final String CREATE_BODY =
            "{\"patientId\":\"" + PATIENT_ID + "\",\"onboardingUrl\":\"https://mobile.example.org/onboarding/1\"}";
    private static final String SEND_BODY = "{\"qrCodeId\":\"" + QR_CODE_ID + "\",\"appName\":\"Care App\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private QRCodeService qrCodeService;

    @MockitoBean
    private PatientService patientService;

    @MockitoBean
    private QRCodeUtilityService qrCodeUtilityService;

    @MockitoBean
    private QRCodeImageService qrCodeImageService;

    @MockitoBean
    private QRCodeMapper qrCodeMapper;

    @MockitoBean
    private AsyncEmailService asyncEmailService;

    @MockitoBean
    private CaretakerService caretakerService;

    @MockitoBean
    private si.result.project.eearly.port.mobile.MobileServiceService mobileServiceService;

    private QRCode qrCode;

    @BeforeEach
    void setUp() throws Exception {
        token("caretaker-token", KEYCLOAK_ID.toString(), "{\"test-client\": {\"roles\": [\"Clinician\"]}}");
        token("no-roles-caretaker-token", KEYCLOAK_ID.toString(), "{}");
        token("other-caretaker-token", OTHER_KEYCLOAK_ID.toString(), "{\"test-client\": {\"roles\": [\"Clinician\"]}}");
        token("admin-token", "admin-1", "{\"test-client\": {\"roles\": [\"Administrator\"]}}");
        token("admin-uuid-token", UUID.randomUUID().toString(), "{\"test-client\": {\"roles\": [\"Administrator\"]}}");
        token("mobile-service-token", "service-account-mobile", "{\"test-client\": {\"roles\": [\"MobileService\"]}}");
        token("other-client-token", UUID.randomUUID().toString(), "{\"account\": {\"roles\": [\"Administrator\"]}}");
        when(jwtDecoder.decode("invalid")).thenThrow(new BadJwtException("invalid"));

        final Caretaker caretaker = caretaker(CARETAKER_ID);
        final Caretaker otherCaretaker = caretaker(OTHER_CARETAKER_ID);
        when(caretakerService.findByKeycloakId(any(UUID.class))).thenAnswer(invocation -> {
            final UUID keycloakId = invocation.getArgument(0);
            if (KEYCLOAK_ID.equals(keycloakId)) {
                return caretaker;
            }
            if (OTHER_KEYCLOAK_ID.equals(keycloakId)) {
                return otherCaretaker;
            }
            throw new DomainException(DomainExceptionCode.CARETAKER_NOT_FOUND);
        });

        final Patient patient = mock(Patient.class);
        when(patient.getId()).thenReturn(PATIENT_ID);
        when(patient.getKeycloakId()).thenReturn(UUID.fromString("c3a1f0d2-6b7e-4f3a-9d58-2e4b8a1c7f90"));
        when(mobileServiceService.issueOnboardingLink(any()))
                .thenReturn("https://mobile.example.org/onboarding/single-use-code");
        when(patientService.findByIdForCaretaker(any(UUID.class), any(UUID.class))).thenAnswer(invocation -> {
            if (PATIENT_ID.equals(invocation.getArgument(0)) && CARETAKER_ID.equals(invocation.getArgument(1))) {
                return patient;
            }
            throw new DomainException(DomainExceptionCode.PATIENT_NOT_FOUND);
        });
        qrCode = mock(QRCode.class);
        when(qrCode.getPatient()).thenReturn(patient);
        when(qrCode.getCaretaker()).thenReturn(caretaker);
        when(qrCode.getStatus()).thenReturn(QRCodeStatus.ACTIVE);
        when(qrCode.getExpiresAt()).thenReturn(OffsetDateTime.now().plusDays(1));
        when(qrCodeService.findById(any(UUID.class))).thenAnswer(invocation -> {
            if (QR_CODE_ID.equals(invocation.getArgument(0))) {
                return qrCode;
            }
            throw new DomainException(DomainExceptionCode.QR_CODE_NOT_FOUND);
        });
        when(qrCodeService.findByPatientId(PATIENT_ID)).thenReturn(qrCode);
        when(qrCodeService.findAllForCaretaker(any(), any(), any(UUID.class))).thenReturn(new PageImpl<>(List.of(qrCode)));
        when(qrCodeService.create(any())).thenReturn(qrCode);
        when(qrCodeMapper.toDTO(any(QRCode.class))).thenReturn(
                new QRCodeDTO(QR_CODE_ID, "token", OffsetDateTime.now().plusDays(1), "image", false, QRCodeStatus.ACTIVE,
                        null, null));
        when(qrCodeMapper.toLiteDTO(any(QRCode.class))).thenReturn(
                new QRCodeLiteDTO(QR_CODE_ID, "token", OffsetDateTime.now().plusDays(1), "image", false,
                        QRCodeStatus.ACTIVE, PATIENT_ID));
    }

    private static Caretaker caretaker(UUID id) {
        final Caretaker caretaker = mock(Caretaker.class);
        when(caretaker.getId()).thenReturn(id);
        return caretaker;
    }

    private void token(String value, String subject, String resourceAccess) throws Exception {
        final String claims = "{\"sub\": \"" + subject + "\", \"resource_access\": " + resourceAccess + "}";
        final var parsed = JWTClaimsSet.parse(claims).getClaims();
        when(jwtDecoder.decode(value)).thenReturn(
                Jwt.withTokenValue(value).header("alg", "RS256").claims(c -> c.putAll(parsed)).build());
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String bearer) throws Exception {
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return mockMvc.perform(request);
    }

    private ResultActions list(String bearer) throws Exception {
        return perform(get("/api/v1/qrcodes"), bearer);
    }

    private ResultActions byId(String bearer) throws Exception {
        return perform(get("/api/v1/qrcodes/" + QR_CODE_ID), bearer);
    }

    private ResultActions byPatient(String bearer) throws Exception {
        return perform(get("/api/v1/qrcodes/patient/" + PATIENT_ID), bearer);
    }

    private ResultActions create(String bearer) throws Exception {
        return perform(post("/api/v1/qrcodes").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY), bearer);
    }

    private ResultActions sendEmail(String bearer) throws Exception {
        return perform(post("/api/v1/qrcodes/send-email").contentType(MediaType.APPLICATION_JSON).content(SEND_BODY),
                bearer);
    }

    private void assertNothingHappened() {
        verify(qrCodeService, never()).create(any());
        verify(qrCodeService, never()).findAllForCaretaker(any(), any(), any());
        verify(qrCodeService, never()).findByPatientId(any());
        verifyNoInteractions(asyncEmailService, qrCodeImageService, qrCodeUtilityService);
        verifyNoInteractions(qrCodeMapper);
    }

    @Test
    @DisplayName("Anonymous callers and invalid tokens get 401 on every QR code endpoint and reach nothing")
    void anonymous_isUnauthorized() throws Exception {
        for (final String bearer : new String[] {null, "invalid"}) {
            list(bearer).andExpect(status().isUnauthorized());
            byId(bearer).andExpect(status().isUnauthorized());
            byPatient(bearer).andExpect(status().isUnauthorized());
            create(bearer).andExpect(status().isUnauthorized());
            sendEmail(bearer).andExpect(status().isUnauthorized());
        }

        verifyNoInteractions(qrCodeService, caretakerService, patientService);
        assertNothingHappened();
    }

    @Test
    @DisplayName("A caretaker lists, reads, creates and sends the QR codes of their assigned patient")
    void caretaker_isAllowedForTheAssignedPatient() throws Exception {
        list("caretaker-token").andExpect(status().isOk());
        byId("caretaker-token").andExpect(status().isOk()).andExpect(jsonPath("$.payload.id").value(QR_CODE_ID.toString()));
        byPatient("caretaker-token").andExpect(status().isOk());
        create("caretaker-token").andExpect(status().isCreated());
        sendEmail("caretaker-token").andExpect(status().isOk());

        verify(qrCodeService).findAllForCaretaker(any(), any(), eq(CARETAKER_ID));
        verify(mobileServiceService).issueOnboardingLink(any());
        verify(asyncEmailService).sendQRCodeEmailAsync(any());
    }

    @Test
    @DisplayName("No client role is needed: the caretaker record is what counts")
    void caretaker_withoutRoles_isAllowed() throws Exception {
        list("no-roles-caretaker-token").andExpect(status().isOk());
        byId("no-roles-caretaker-token").andExpect(status().isOk());
        sendEmail("no-roles-caretaker-token").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Another caretaker gets the same not-found answer for a patient that is not theirs, and nothing is sent")
    void otherCaretaker_isNotFound() throws Exception {
        list("other-caretaker-token").andExpect(status().isOk());
        verify(qrCodeService).findAllForCaretaker(any(), any(), eq(OTHER_CARETAKER_ID));

        byId("other-caretaker-token").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
        byPatient("other-caretaker-token").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
        create("other-caretaker-token").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
        sendEmail("other-caretaker-token").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));

        verify(qrCodeService, never()).create(any());
        verifyNoInteractions(asyncEmailService);
        verify(qrCodeMapper, never()).toDTO(any());
    }

    @Test
    @DisplayName("An administrator, a service account or a token of another client that is no caretaker has no QR codes")
    void nonCaretakers_haveNoQRCodes() throws Exception {
        for (final String bearer : new String[] {"admin-token", "admin-uuid-token", "mobile-service-token",
                "other-client-token"}) {
            list(bearer).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CARETAKER_NOT_FOUND"));
            byId(bearer).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CARETAKER_NOT_FOUND"));
            byPatient(bearer).andExpect(status().isNotFound());
            create(bearer).andExpect(status().isNotFound());
            sendEmail(bearer).andExpect(status().isNotFound());
        }

        verify(qrCodeService, never()).create(any());
        verify(qrCodeService, never()).findAllForCaretaker(any(), any(), any());
        verifyNoInteractions(asyncEmailService, qrCodeMapper, qrCodeImageService, qrCodeUtilityService,
                mobileServiceService);
    }

    @Test
    @DisplayName("The caretaker comes from the token, not from the request")
    void caretaker_isNotTakenFromTheRequest() throws Exception {
        final String body = "{\"patientId\":\"" + PATIENT_ID + "\",\"caretakerId\":\"" + CARETAKER_ID
                + "\",\"onboardingUrl\":\"https://mobile.example.org/onboarding/1\"}";

        perform(post("/api/v1/qrcodes").contentType(MediaType.APPLICATION_JSON).content(body), "other-caretaker-token")
                .andExpect(status().isNotFound());

        verify(qrCodeService, never()).create(any());
        verifyNoInteractions(mobileServiceService);
        verify(patientService, times(1)).findByIdForCaretaker(PATIENT_ID, OTHER_CARETAKER_ID);
    }
}
