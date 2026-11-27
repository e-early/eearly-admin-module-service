package si.result.project.eearly.qrcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import si.result.project.eearly.dao.caretaker.CaretakerDAO;
import si.result.project.eearly.dao.notification.NotificationDAO;
import si.result.project.eearly.dao.notification.NotificationReadDAO;
import si.result.project.eearly.dao.patient.PatientDAO;
import si.result.project.eearly.dao.qrcode.QRCodeDAO;
import si.result.project.eearly.event.notification.NotificationEventListener;
import si.result.project.eearly.facade.qrcode.QRCodeServiceFacade;
import si.result.project.eearly.mapper.caretaker.CaretakerMapper;
import si.result.project.eearly.mapper.caretaker.CaretakerMapperImpl;
import si.result.project.eearly.mapper.qrcode.QRCodeMapper;
import si.result.project.eearly.mapper.qrcode.QRCodeMapperImpl;
import si.result.project.eearly.model.notification.Notification;
import si.result.project.eearly.model.qrcode.command.ConfigureQRCodeCommand;
import si.result.project.eearly.port.caretaker.CaretakerClient;
import si.result.project.eearly.port.caretaker.CaretakerService;
import si.result.project.eearly.port.email.AsyncEmailService;
import si.result.project.eearly.port.email.EmailProperties;
import si.result.project.eearly.port.email.EmailService;
import si.result.project.eearly.port.email.EmailTemplateService;
import si.result.project.eearly.port.notification.NotificationService;
import si.result.project.eearly.port.notification.SseNotificationPublisher;
import si.result.project.eearly.port.patient.PatientService;
import si.result.project.eearly.port.qrcode.QRCodeService;
import si.result.project.eearly.resource.qrcode.QRCodeController;
import si.result.project.eearly.util.qrcode.QRCodeImageService;
import si.result.project.eearly.util.qrcode.QRCodeUtilityService;
import si.result.rest.filter.config.RestFilterArgumentResolver;
import si.result.spring.boot.bricks.advice.ErrorResponseHandler;
import si.result.spring.boot.bricks.auth.AuthenticationProvider;

/**
 * QR codes through the REST controller, facade, domain services and JPA repository against a migrated PostgreSQL
 * database (Hibernate validates the mapping against the schema) and an embedded SMTP server: creating, reading and
 * listing the QR codes of a caretaker's patients, sending one to the patient in the patient's language, the status
 * and the event when the app is configured, and what ends up in the log. The caller is whoever the test says: the
 * token subject is the caretaker's Keycloak id.
 */
@DataJpaTest
@ContextConfiguration(classes = QRCodeRestIntegrationTest.Cfg.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
        "spring.config.name=qrcode-rest-test",
        // The first test context of a JVM initialises logging for all later ones (the log format
        // characterization test reads service.name), so name the application as the service does.
        "spring.application.name=eearly-admin-service",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl"
})
class QRCodeRestIntegrationTest {

    @SpringBootConfiguration
    @EntityScan(basePackages = "si.result.project.eearly.model")
    @EnableJpaRepositories(basePackages = "si.result.project.eearly.dao")
    @EnableJpaAuditing(dateTimeProviderRef = "zonedDateTimeProvider")
    static class Cfg {

        @Bean
        DateTimeProvider zonedDateTimeProvider() {
            return () -> Optional.of(ZonedDateTime.now(ZoneOffset.UTC));
        }

        @Bean
        AuthenticationProvider authenticationProvider() {
            return mock(AuthenticationProvider.class);
        }

        @Bean
        CaretakerClient caretakerClient() {
            return mock(CaretakerClient.class);
        }

        @Bean
        CaretakerService caretakerService(CaretakerDAO caretakerDAO, CaretakerClient caretakerClient) {
            return new CaretakerService(caretakerDAO, caretakerClient);
        }

        @Bean
        PatientService patientService(PatientDAO patientDAO) {
            return new PatientService(patientDAO);
        }

        @Bean
        QRCodeService qrCodeService(QRCodeDAO qrCodeDAO,
                org.springframework.context.ApplicationEventPublisher eventPublisher) {
            return new QRCodeService(qrCodeDAO, eventPublisher);
        }

        @Bean
        QRCodeUtilityService qrCodeUtilityService() {
            return new QRCodeUtilityService();
        }

        @Bean
        QRCodeImageService qrCodeImageService(ResourceLoader resourceLoader) {
            return new QRCodeImageService(resourceLoader);
        }

        @Bean
        CaretakerMapper caretakerMapper() {
            return new CaretakerMapperImpl();
        }

        @Bean
        QRCodeMapper qrCodeMapper() {
            return new QRCodeMapperImpl();
        }

        // Only example.com may receive e-mail, as with email.allowed-recipient-domains=example.com.
        @Bean
        EmailProperties emailProperties() {
            return new EmailProperties("care@example.org", "Care App", "example.com", "https://app.example.org/get",
                    "classpath:images/email-logo.png", new EmailProperties.Templates("classpath:templates/email/"),
                    new EmailProperties.Qrcode("qr-code-email.html"));
        }

        @Bean
        JavaMailSender mailSender() {
            final JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost("127.0.0.1");
            sender.setPort(GREEN_MAIL.getSmtp().getPort());
            return sender;
        }

        @Bean
        EmailTemplateService emailTemplateService(ResourceLoader resourceLoader, EmailProperties properties) {
            return new EmailTemplateService(resourceLoader, properties);
        }

        @Bean
        EmailService emailService(JavaMailSender mailSender, EmailTemplateService templateService,
                ResourceLoader resourceLoader, EmailProperties properties) {
            return new EmailService(mailSender, templateService, resourceLoader, properties);
        }

        // Not on the e-mail thread pool here (no @EnableAsync), so the message is sent when the call returns.
        @Bean
        AsyncEmailService asyncEmailService(EmailService emailService) {
            return new AsyncEmailService(emailService);
        }

        @Bean
        PushRecorder pushRecorder() {
            return new PushRecorder();
        }

        @Bean
        NotificationService notificationService(NotificationDAO notificationDAO, NotificationReadDAO notificationReadDAO) {
            return new NotificationService(notificationDAO, notificationReadDAO);
        }

        // The real listener of the events, so that the QR code's event becomes a notification.
        @Bean
        NotificationEventListener notificationEventListener(NotificationService notificationService,
                PushRecorder pushRecorder, PatientService patientService) {
            return new NotificationEventListener(notificationService, pushRecorder, patientService);
        }

        // Spring-managed facade, so that its transactional methods keep the session open while the
        // QR codes are mapped (as in the running service).
        @Bean
        QRCodeServiceFacade qrCodeServiceFacade(QRCodeService qrCodeService, PatientService patientService,
                QRCodeUtilityService utilityService, QRCodeImageService imageService, QRCodeMapper qrCodeMapper,
                AsyncEmailService asyncEmailService, CaretakerService caretakerService,
                AuthenticationProvider authenticationProvider) {
            return new QRCodeServiceFacade(qrCodeService, patientService, utilityService, imageService, qrCodeMapper,
                    asyncEmailService, caretakerService, authenticationProvider);
        }
    }

    /** Stands in for the push transport and records what would have been pushed. */
    static class PushRecorder implements SseNotificationPublisher {
        final List<Notification> pushed = new CopyOnWriteArrayList<>();

        @Override
        public void broadcast(final Notification notification) {
            pushed.add(notification);
        }
    }

    // Started once for the class (the Spring context, which keeps the SMTP port, is shared by its tests).
    static final GreenMail GREEN_MAIL = new GreenMail(ServerSetupTest.SMTP.dynamicPort());

    @BeforeAll
    static void startMailServer() {
        GREEN_MAIL.start();
    }

    @AfterAll
    static void stopMailServer() {
        GREEN_MAIL.stop();
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DATA_URI_PREFIX = "data:image/png;base64,";
    // Distinctive values, so that any occurrence in the log output is the value itself.
    private static final String PATIENT_FIRST_NAME = "Zorana";
    private static final String PATIENT_LAST_NAME = "Quillfeather";
    private static final String PATIENT_EMAIL = "zorana.quillfeather@example.com";
    private static final String CARETAKER_FIRST_NAME = "Matevz";
    private static final String CARETAKER_LAST_NAME = "Bramblewick";
    private static final String CARETAKER_EMAIL = "matevz.bramblewick@example.org";
    private static final String OTHER_PATIENT_EMAIL = "tobias.fernhollow@example.com";
    private static final String BLOCKED_PATIENT_EMAIL = "ines.thistledown@example.net";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AuthenticationProvider authenticationProvider;

    @Autowired
    private QRCodeService qrCodeService;

    @Autowired
    private QRCodeServiceFacade qrCodeFacade;

    @Autowired
    private PushRecorder pushRecorder;

    private MockMvc mvc;
    // patient (English) and otherPatient (Slovenian) of caretaker; stranger's patient belongs to the other caretaker.
    private UUID patientId;
    private UUID otherPatientId;
    private UUID blockedPatientId;
    private UUID strangersPatientId;
    private UUID caretakerId;
    private UUID caretakerKeycloakId;
    private UUID strangerId;
    private UUID strangerKeycloakId;

    @BeforeEach
    void setUp() throws Exception {
        reset(authenticationProvider);
        pushRecorder.pushed.clear();
        GREEN_MAIL.purgeEmailFromAllMailboxes();
        jdbc.update("DELETE FROM t_notification_read");
        jdbc.update("DELETE FROM t_notification");
        jdbc.update("DELETE FROM t_qr_code");
        jdbc.update("DELETE FROM t_patient_caretaker");
        jdbc.update("DELETE FROM t_patient");
        jdbc.update("DELETE FROM t_caretaker");

        caretakerId = UUID.randomUUID();
        caretakerKeycloakId = UUID.randomUUID();
        strangerId = UUID.randomUUID();
        strangerKeycloakId = UUID.randomUUID();
        insertCaretaker(caretakerId, caretakerKeycloakId, CARETAKER_FIRST_NAME, CARETAKER_LAST_NAME, CARETAKER_EMAIL);
        insertCaretaker(strangerId, strangerKeycloakId, "Other", "Caretaker", "other.caretaker@example.org");
        patientId = insertPatient(PATIENT_FIRST_NAME, PATIENT_LAST_NAME, PATIENT_EMAIL, "en", "P1");
        otherPatientId = insertPatient("Tobias", "Fernhollow", OTHER_PATIENT_EMAIL, "sl", "P2");
        blockedPatientId = insertPatient("Ines", "Thistledown", BLOCKED_PATIENT_EMAIL, "sl", "P3");
        strangersPatientId = insertPatient("Other", "Patient", "other.patient@example.com", "sl", "P4");
        assign(patientId, caretakerId);
        assign(otherPatientId, caretakerId);
        assign(blockedPatientId, caretakerId);
        assign(strangersPatientId, strangerId);

        mvc = MockMvcBuilders
                .standaloneSetup(new QRCodeController(qrCodeFacade))
                .setCustomArgumentResolvers(new RestFilterArgumentResolver(), new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new ErrorResponseHandler())
                .build();
        as(caretakerKeycloakId.toString());
    }

    private void insertCaretaker(UUID id, UUID keycloakId, String firstName, String lastName, String email) {
        jdbc.update("INSERT INTO t_caretaker (id, keycloak_id, first_name, last_name, email, language) "
                + "VALUES (?, ?, ?, ?, ?, 'sl')", id, keycloakId, firstName, lastName, email);
    }

    private UUID insertPatient(String firstName, String lastName, String email, String language, String insuranceId) {
        final UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO t_patient (id, first_name, last_name, date_of_birth, gender, health_insurance_id, "
                + "street, city, state, country, email, phone_number, language) VALUES (?, ?, ?, DATE '1990-01-01', "
                + "'OTHER', ?, 's', 'c', 'st', 'co', ?, '1', ?)", id, firstName, lastName, insuranceId, email, language);
        return id;
    }

    private void assign(UUID patient, UUID caretaker) {
        jdbc.update("INSERT INTO t_patient_caretaker (patient_id, caretaker_id) VALUES (?, ?)", patient, caretaker);
    }

    /** Calls from now on carry a token with the given subject. */
    private void as(String subject) {
        when(authenticationProvider.getId()).thenReturn(subject);
    }

    private static String onboardingUrl(UUID id) {
        return "https://mobile.example.org/api/v1/onboarding/" + id;
    }

    private ResultActions create(UUID patient, String url) throws Exception {
        final Map<String, Object> body = new java.util.HashMap<>();
        body.put("patientId", patient == null ? null : patient.toString());
        // The request names another caretaker: the authenticated one is used.
        body.put("caretakerId", strangerId.toString());
        body.put("onboardingUrl", url);
        return mvc.perform(post("/api/v1/qrcodes").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body)));
    }

    private UUID createdQrCode(UUID patient) throws Exception {
        final String response = create(patient, onboardingUrl(patient)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JSON.readTree(response).get("payload").get("id").asText());
    }

    private ResultActions sendEmail(UUID qrCodeId, String appName) throws Exception {
        final Map<String, Object> body = new java.util.HashMap<>();
        body.put("qrCodeId", qrCodeId == null ? null : qrCodeId.toString());
        body.put("appName", appName);
        return mvc.perform(post("/api/v1/qrcodes/send-email").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body)));
    }

    private Map<String, Object> row(UUID id) {
        return jdbc.queryForMap("SELECT * FROM t_qr_code WHERE id = ?", id);
    }

    private int qrCodeCount() {
        return jdbc.queryForObject("SELECT count(*) FROM t_qr_code", Integer.class);
    }

    private void expire(UUID qrCodeId) {
        jdbc.update("UPDATE t_qr_code SET expires_at = ? WHERE id = ?",
                Timestamp.from(OffsetDateTime.now().minusDays(1).toInstant()), qrCodeId);
    }

    // ---- creating ---------------------------------------------------------------------------

    @Test
    void createStoresAnActiveCodeOfThePatientOnBehalfOfTheAuthenticatedCaretaker() throws Exception {
        final String url = onboardingUrl(patientId);

        final String response = create(patientId, url)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payload.status").value("ACTIVE"))
                .andExpect(jsonPath("$.payload.mobileAppConfigured").value(false))
                .andExpect(jsonPath("$.payload.patientId").value(patientId.toString()))
                .andReturn().getResponse().getContentAsString();
        final JsonNode payload = JSON.readTree(response).get("payload");
        final UUID id = UUID.fromString(payload.get("id").asText());
        final String token = payload.get("token").asText();

        final Map<String, Object> stored = row(id);
        assertThat(stored.get("token")).isEqualTo(token);
        assertThat(stored.get("status")).hasToString("ACTIVE");
        assertThat(stored.get("mobile_app_configured")).isEqualTo(false);
        assertThat(stored.get("id_patient")).isEqualTo(patientId);
        // On behalf of the authenticated caretaker, whatever the request names.
        assertThat(stored.get("id_caretaker")).isEqualTo(caretakerId);
        assertThat(stored.get("onboarding_url")).isEqualTo(url);
        assertThat(stored.get("created_at")).isNotNull();
        assertThat(((java.sql.Timestamp) stored.get("expires_at")).toInstant())
                .isBetween(OffsetDateTime.now().plusDays(30).minusMinutes(1).toInstant(),
                        OffsetDateTime.now().plusDays(30).plusMinutes(1).toInstant());
        // The token: a random UUID and the creation time.
        assertThat(token).matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}-\\d{13}");
        // The image holds the onboarding URL and the token, and nothing about the patient.
        final String image = (String) stored.get("image_url");
        assertThat(image).startsWith(DATA_URI_PREFIX);
        assertThat(decode(Base64.getDecoder().decode(image.substring(DATA_URI_PREFIX.length()))))
                .isEqualTo(url + "?token=" + token);
    }

    @Test
    void aSecondCodeIsRefusedWhileTheFirstIsActiveAndUnexpired() throws Exception {
        createdQrCode(patientId);

        create(patientId, onboardingUrl(patientId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QR_CODE_FOUND"));

        assertThat(qrCodeCount()).isEqualTo(1);
    }

    @Test
    void aNewCodeIsCreatedOnceTheEarlierOneHasExpired_andThePatientsLatestIsReturned() throws Exception {
        final UUID first = createdQrCode(patientId);
        expire(first);

        final UUID second = createdQrCode(patientId);

        assertThat(qrCodeCount()).isEqualTo(2);
        // More than one row of the patient is no error: the most recent one is the patient's code.
        mvc.perform(get("/api/v1/qrcodes/patient/" + patientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.id").value(second.toString()))
                .andExpect(jsonPath("$.payload.status").value("ACTIVE"));
        // The patient's QR code is the one that was not refused; creating again is refused again.
        create(patientId, onboardingUrl(patientId)).andExpect(status().isNotFound());
        assertThat(qrCodeCount()).isEqualTo(2);
    }

    @Test
    void aNewCodeIsCreatedOnceTheEarlierOneHasBeenUsed() throws Exception {
        final UUID first = createdQrCode(patientId);
        qrCodeService.configure(new ConfigureQRCodeCommand(first, true));

        final UUID second = createdQrCode(patientId);

        assertThat(second).isNotEqualTo(first);
        mvc.perform(get("/api/v1/qrcodes/patient/" + patientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.id").value(second.toString()));
    }

    @Test
    void createRequiresAPatientAndAnOnboardingUrl() throws Exception {
        create(null, onboardingUrl(patientId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PATIENT_ID_NOT_PROVIDED"));
        create(patientId, "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QR_CODE_ONBOARDING_URL_BLANK"));
        create(patientId, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QR_CODE_ONBOARDING_URL_NULL"));
        create(UUID.randomUUID(), onboardingUrl(patientId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));

        assertThat(qrCodeCount()).isZero();
    }

    /**
     * Current behaviour, for the record: the onboarding URL is the caller's, and it is encoded as it is. It is not
     * checked against the instance's own onboarding address.
     */
    @Test
    void theOnboardingUrlOfTheRequestIsEncodedAsItIs() throws Exception {
        final String response = create(patientId, "https://other.example.net/anywhere")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        final UUID id = UUID.fromString(JSON.readTree(response).get("payload").get("id").asText());

        final String image = (String) row(id).get("image_url");
        assertThat(decode(Base64.getDecoder().decode(image.substring(DATA_URI_PREFIX.length()))))
                .startsWith("https://other.example.net/anywhere?token=");
    }

    // ---- reading and listing ----------------------------------------------------------------

    @Test
    void getByIdAndByPatientReturnTheCodeWithItsPatientAndCaretaker() throws Exception {
        final UUID id = createdQrCode(patientId);

        mvc.perform(get("/api/v1/qrcodes/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.id").value(id.toString()))
                .andExpect(jsonPath("$.payload.status").value("ACTIVE"))
                .andExpect(jsonPath("$.payload.patient.id").value(patientId.toString()))
                .andExpect(jsonPath("$.payload.patient.firstName").value(PATIENT_FIRST_NAME))
                .andExpect(jsonPath("$.payload.caretaker.id").value(caretakerId.toString()))
                .andExpect(jsonPath("$.payload.imageUrl").value(org.hamcrest.Matchers.startsWith(DATA_URI_PREFIX)));
        mvc.perform(get("/api/v1/qrcodes/patient/" + patientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.id").value(id.toString()));
        mvc.perform(get("/api/v1/qrcodes/patient/" + otherPatientId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QR_CODE_NOT_FOUND"));
        mvc.perform(get("/api/v1/qrcodes/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QR_CODE_NOT_FOUND"));
    }

    @Test
    void theListHoldsTheCodesOfTheCaretakersPatientsOnly() throws Exception {
        final UUID mine = createdQrCode(patientId);
        final UUID mineToo = createdQrCode(otherPatientId);
        as(strangerKeycloakId.toString());
        final UUID strangers = createdQrCode(strangersPatientId);

        mvc.perform(get("/api/v1/qrcodes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.content.length()").value(1))
                .andExpect(jsonPath("$.payload.content[0].id").value(strangers.toString()))
                .andExpect(jsonPath("$.payload.content[0].patientId").value(strangersPatientId.toString()));
        as(caretakerKeycloakId.toString());
        final String response = mvc.perform(get("/api/v1/qrcodes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.content.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).contains(mine.toString(), mineToo.toString()).doesNotContain(strangers.toString());
    }

    @Test
    void aPatientWithSeveralCodesIsListedOncePerCode() throws Exception {
        expire(createdQrCode(patientId));
        createdQrCode(patientId);

        mvc.perform(get("/api/v1/qrcodes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.content.length()").value(2));
    }

    // ---- who may do what --------------------------------------------------------------------

    @Test
    void anotherCaretakersPatientHasNoCodesForThisCaretaker() throws Exception {
        final UUID strangers = createdQrCodeAs(strangerKeycloakId, strangersPatientId);
        final int before = qrCodeCount();

        mvc.perform(get("/api/v1/qrcodes/" + strangers))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
        mvc.perform(get("/api/v1/qrcodes/patient/" + strangersPatientId))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
        create(strangersPatientId, onboardingUrl(strangersPatientId))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
        sendEmail(strangers, null)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));

        assertThat(qrCodeCount()).isEqualTo(before);
        assertThat(GREEN_MAIL.getReceivedMessages()).isEmpty();
    }

    private UUID createdQrCodeAs(UUID keycloakId, UUID patient) throws Exception {
        as(keycloakId.toString());
        try {
            return createdQrCode(patient);
        } finally {
            as(caretakerKeycloakId.toString());
        }
    }

    @Test
    void aTokenWhoseSubjectIsNoCaretakerHasNoCodes() throws Exception {
        final UUID id = createdQrCode(patientId);

        for (final String subject : new String[] {UUID.randomUUID().toString(), "service-account-mobile", ""}) {
            as(subject);
            mvc.perform(get("/api/v1/qrcodes")).andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/qrcodes/" + id))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CARETAKER_NOT_FOUND"));
            mvc.perform(get("/api/v1/qrcodes/patient/" + patientId)).andExpect(status().isNotFound());
            create(otherPatientId, onboardingUrl(otherPatientId)).andExpect(status().isNotFound());
            sendEmail(id, null).andExpect(status().isNotFound());
        }

        assertThat(qrCodeCount()).isEqualTo(1);
        assertThat(GREEN_MAIL.getReceivedMessages()).isEmpty();
    }

    // ---- send-email -------------------------------------------------------------------------

    @Test
    void theEnglishPatientGetsTheEnglishMessageAtTheAddressOfTheirRecord() throws Exception {
        final UUID id = createdQrCode(patientId);
        final String token = (String) row(id).get("token");

        sendEmail(id, "Care App").andExpect(status().isOk());

        assertThat(GREEN_MAIL.getReceivedMessages()).hasSize(1);
        final MimeMessage message = GREEN_MAIL.getReceivedMessages()[0];
        assertThat(message.getAllRecipients()).hasSize(1);
        assertThat(((InternetAddress) message.getAllRecipients()[0]).getAddress()).isEqualTo(PATIENT_EMAIL);
        assertThat(((InternetAddress) message.getFrom()[0]).getAddress()).isEqualTo("care@example.org");
        assertThat(((InternetAddress) message.getFrom()[0]).getPersonal()).isEqualTo("Care App");
        assertThat(((InternetAddress) message.getReplyTo()[0]).getAddress()).isEqualTo(CARETAKER_EMAIL);
        assertThat(message.getSubject()).isEqualTo("Your Healthcare Monitoring QR Code");
        final String html = htmlOf(message);
        assertThat(html).contains("<html lang=\"en\">").contains("Dear " + PATIENT_FIRST_NAME + " " + PATIENT_LAST_NAME)
                .contains(CARETAKER_FIRST_NAME + " " + CARETAKER_LAST_NAME).contains("cid:qrcode");
        // The message carries the QR code of the request, the one with the token.
        assertThat(decode(inlinePart(message, "<qrcode>"))).endsWith("?token=" + token);
    }

    @Test
    void theSlovenianPatientGetsTheSlovenianMessage() throws Exception {
        final UUID id = createdQrCode(otherPatientId);

        sendEmail(id, null).andExpect(status().isOk());

        assertThat(GREEN_MAIL.getReceivedMessages()).hasSize(1);
        final MimeMessage message = GREEN_MAIL.getReceivedMessages()[0];
        assertThat(((InternetAddress) message.getAllRecipients()[0]).getAddress()).isEqualTo(OTHER_PATIENT_EMAIL);
        assertThat(message.getSubject()).isEqualTo("Vaša QR koda za spremljanje zdravja");
        assertThat(htmlOf(message)).contains("<html lang=\"sl\">").contains("Pozdravljeni, Tobias Fernhollow");
        // No application name in the request: the configured one.
        assertThat(((InternetAddress) message.getFrom()[0]).getPersonal()).isEqualTo("Care App");
    }

    @Test
    void theRequestCannotChooseTheRecipient() throws Exception {
        final UUID id = createdQrCode(patientId);

        mvc.perform(post("/api/v1/qrcodes/send-email").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qrCodeId\":\"" + id + "\",\"email\":\"someone@example.com\","
                                + "\"to\":\"someone@example.com\",\"patientEmail\":\"someone@example.com\","
                                + "\"cc\":\"someone@example.com\",\"bcc\":\"someone@example.com\"}"))
                .andExpect(status().isOk());

        assertThat(GREEN_MAIL.getReceivedMessages()).hasSize(1);
        final MimeMessage message = GREEN_MAIL.getReceivedMessages()[0];
        assertThat(message.getAllRecipients()).hasSize(1);
        assertThat(((InternetAddress) message.getAllRecipients()[0]).getAddress()).isEqualTo(PATIENT_EMAIL);
        assertThat(java.util.Arrays.stream(si.result.project.eearly.dto.qrcode.QRCodeSendEmailDTO.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)).containsExactly("qrCodeId", "appName");
    }

    @Test
    void anApplicationNameWithLineBreaksAddsNoRecipientAndNoHeader() throws Exception {
        final UUID id = createdQrCode(patientId);

        sendEmail(id, "Care App\r\nBcc: someone@example.com\r\nX-Injected: yes").andExpect(status().isOk());

        assertThat(GREEN_MAIL.getReceivedMessages()).hasSize(1);
        final MimeMessage message = GREEN_MAIL.getReceivedMessages()[0];
        assertThat(message.getAllRecipients()).hasSize(1);
        assertThat(message.getHeader("Bcc")).isNull();
        assertThat(message.getHeader("X-Injected")).isNull();
        assertThat(((InternetAddress) message.getFrom()[0]).getAddress()).isEqualTo("care@example.org");
    }

    @Test
    void aRecipientOutsideTheAllowedDomainsGetsNothing_andTheLogHasNoAddress(CapturedOutput output) throws Exception {
        final UUID id = createdQrCode(blockedPatientId);

        sendEmail(id, null).andExpect(status().isOk());

        assertThat(GREEN_MAIL.getReceivedMessages()).isEmpty();
        assertThat(output.getAll()).contains("allowed-recipient-domains")
                .doesNotContain(BLOCKED_PATIENT_EMAIL).doesNotContain("thistledown");
    }

    @Test
    void sendEmailNeedsAnExistingQrCode() throws Exception {
        sendEmail(null, null).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("QR_CODE_NOT_FOUND"));
        sendEmail(UUID.randomUUID(), null).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QR_CODE_NOT_FOUND"));

        assertThat(GREEN_MAIL.getReceivedMessages()).isEmpty();
    }

    // ---- configuring ------------------------------------------------------------------------

    @Test
    void configuringTheAppMarksTheCodeUsedAndPublishesTheEventThatBecomesANotification() throws Exception {
        final UUID id = createdQrCode(patientId);

        qrCodeService.configure(new ConfigureQRCodeCommand(id, true));

        final Map<String, Object> stored = row(id);
        assertThat(stored.get("status")).hasToString("USED");
        assertThat(stored.get("mobile_app_configured")).isEqualTo(true);
        // PatientSyncedEvent -> the listener's SYNC notification for the QR code's caretaker, pushed once.
        final List<Map<String, Object>> notifications = jdbc.queryForList(
                "SELECT * FROM t_notification WHERE id_patient = ?", patientId);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).get("type")).hasToString("SYNC");
        assertThat(notifications.get(0).get("message")).isEqualTo("PATIENT_SYNCED");
        assertThat(notifications.get(0).get("id_caretaker")).isEqualTo(caretakerId);
        assertThat(pushRecorder.pushed).hasSize(1);
        mvc.perform(get("/api/v1/qrcodes/" + id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payload.status").value("USED"))
                .andExpect(jsonPath("$.payload.mobileAppConfigured").value(true));
    }

    @Test
    void notConfiguringPublishesNothing() throws Exception {
        final UUID id = createdQrCode(patientId);

        qrCodeService.configure(new ConfigureQRCodeCommand(id, false));

        assertThat(row(id).get("status")).hasToString("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_notification", Integer.class)).isZero();
        assertThat(pushRecorder.pushed).isEmpty();
    }

    @Test
    void configuringAnUnknownCodePublishesNothing() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> qrCodeService.configure(new ConfigureQRCodeCommand(UUID.randomUUID(), true)))
                .isInstanceOf(si.result.spring.boot.bricks.exception.DomainException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_notification", Integer.class)).isZero();
    }

    // ---- logs -------------------------------------------------------------------------------

    @Test
    void theLogHoldsNoTokenNoQrCodeImageNoAddressAndNoName(CapturedOutput output) throws Exception {
        final String url = onboardingUrl(patientId);
        final String created = create(patientId, url).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        final JsonNode payload = JSON.readTree(created).get("payload");
        final UUID id = UUID.fromString(payload.get("id").asText());
        final String token = payload.get("token").asText();
        final String image = payload.get("imageUrl").asText();

        mvc.perform(get("/api/v1/qrcodes")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/qrcodes/" + id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/qrcodes/patient/" + patientId)).andExpect(status().isOk());
        sendEmail(id, "Care App").andExpect(status().isOk());
        sendEmail(createdQrCodeAs(strangerKeycloakId, strangersPatientId), null).andExpect(status().isNotFound());
        create(patientId, url).andExpect(status().isNotFound());
        qrCodeService.configure(new ConfigureQRCodeCommand(id, true));
        assertThatThrownByUnknownToken(token);

        final String log = output.getAll();
        assertThat(log).contains(id.toString());
        assertThat(log).doesNotContain(token);
        assertThat(log).doesNotContain(token.substring(0, 18));
        assertThat(log).doesNotContain(image.substring(DATA_URI_PREFIX.length(), DATA_URI_PREFIX.length() + 60));
        assertThat(log).doesNotContain(PATIENT_EMAIL).doesNotContain(CARETAKER_EMAIL);
        assertThat(log).doesNotContain(PATIENT_FIRST_NAME).doesNotContain(PATIENT_LAST_NAME)
                .doesNotContain(CARETAKER_FIRST_NAME).doesNotContain(CARETAKER_LAST_NAME);
        assertThat(log).doesNotContain(url);
    }

    private void assertThatThrownByUnknownToken(String token) {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> qrCodeService.findByToken(token + "-unknown"))
                .isInstanceOf(si.result.spring.boot.bricks.exception.DomainException.class);
    }

    // ---- helpers ----------------------------------------------------------------------------

    private static String decode(byte[] png) throws Exception {
        final BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        final int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        final BinaryBitmap bitmap = new BinaryBitmap(
                new HybridBinarizer(new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels)));
        return new QRCodeReader().decode(bitmap, Map.of(DecodeHintType.TRY_HARDER, Boolean.TRUE)).getText();
    }

    private static String htmlOf(final Part part) throws Exception {
        if (part.isMimeType("text/html")) {
            return (String) part.getContent();
        }
        if (part.isMimeType("multipart/*")) {
            final Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                final String html = htmlOf(multipart.getBodyPart(i));
                if (html != null) {
                    return html;
                }
            }
        }
        return null;
    }

    /** The bytes of the part with the given Content-ID. */
    private static byte[] inlinePart(final Part part, final String contentId) throws Exception {
        final String[] header = part.getHeader("Content-ID");
        if (header != null && contentId.equals(header[0])) {
            return part.getInputStream().readAllBytes();
        }
        if (part.isMimeType("multipart/*")) {
            final Multipart multipart = (Multipart) part.getContent();
            final List<BodyPart> children = new ArrayList<>();
            for (int i = 0; i < multipart.getCount(); i++) {
                children.add(multipart.getBodyPart(i));
            }
            for (final BodyPart child : children) {
                final byte[] found = inlinePart(child, contentId);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
