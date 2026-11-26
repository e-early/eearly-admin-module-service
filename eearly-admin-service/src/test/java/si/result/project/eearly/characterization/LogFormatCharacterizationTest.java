package si.result.project.eearly.characterization;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Characterization of the console log format configured by {@code si.result.lib:spring-boot-logger}
 * ({@code logback-spring.xml}): outside the {@code development} profile every log event is printed as one
 * ECS JSON object per line, root level INFO.
 *
 * Uses an empty configuration so that none of the application's own beans (and none of the library's HTTP/method
 * logging aspects) take part; only the logging system initialisation is exercised. The service's
 * {@code application.yaml} is on the test classpath: it supplies {@code spring.application.name}
 * ({@code eearly-admin-service}); {@code logging.file.name} is overridden because the yaml value is an
 * environment placeholder.
 */
@SpringBootTest(
        classes = LogFormatCharacterizationTest.EmptyConfiguration.class,
        properties = "logging.file.name=target/characterization-logs/log-format-test.log")
@ExtendWith(OutputCaptureExtension.class)
class LogFormatCharacterizationTest {

    @SpringBootConfiguration
    static class EmptyConfiguration {
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String LOGGER_NAME = "si.result.project.eearly.characterization.LogMarker";

    private static JsonNode findLogLine(CapturedOutput output, String marker) throws Exception {
        List<String> matching = output.getOut().lines().filter(line -> line.contains(marker)).toList();
        assertThat(matching).as("console lines containing " + marker + " in:\n" + output.getOut()).hasSize(1);
        return JSON.readTree(matching.get(0));
    }

    @Test
    void infoEventIsOneEcsJsonObjectPerLineWithExpectedFields(CapturedOutput output) throws Exception {
        Logger log = LoggerFactory.getLogger(LOGGER_NAME);
        log.info("characterization-marker-info {}", "value");

        String line = output.getOut().lines().filter(l -> l.contains("characterization-marker-info")).findFirst().orElseThrow();
        assertThat(line).startsWith("{").endsWith("}");
        JsonNode json = findLogLine(output, "characterization-marker-info");

        assertThat(json.has("@timestamp")).as(line).isTrue();
        assertThat(json.path("@timestamp").asText()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*Z?");
        assertThat(json.path("log.level").asText()).as(line).isEqualTo("INFO");
        assertThat(json.path("message").asText()).as(line).isEqualTo("characterization-marker-info value");
        assertThat(json.path("log.logger").asText()).as(line).isEqualTo(LOGGER_NAME);
        assertThat(json.path("process.thread.name").asText()).as(line).isNotBlank();
        assertThat(json.path("service.name").asText()).as(line).isEqualTo("eearly-admin-service");
        assertThat(json.path("ecs.version").asText()).as(line).isNotBlank();
    }

    @Test
    void warnAndErrorLevelsAreReportedInLogLevelField(CapturedOutput output) throws Exception {
        Logger log = LoggerFactory.getLogger(LOGGER_NAME);
        log.warn("characterization-marker-warn");
        log.error("characterization-marker-error");

        assertThat(findLogLine(output, "characterization-marker-warn").path("log.level").asText()).isEqualTo("WARN");
        assertThat(findLogLine(output, "characterization-marker-error").path("log.level").asText()).isEqualTo("ERROR");
    }

    @Test
    void loggedExceptionIsCarriedInEcsErrorFields(CapturedOutput output) throws Exception {
        LoggerFactory.getLogger(LOGGER_NAME).error("characterization-marker-exception", new IllegalStateException("boom"));

        JsonNode json = findLogLine(output, "characterization-marker-exception\"");

        assertThat(json.path("error.type").asText()).isEqualTo("java.lang.IllegalStateException");
        assertThat(json.path("error.message").asText()).isEqualTo("boom");
        assertThat(json.path("error.stack_trace").asText()).contains("IllegalStateException: boom");
    }

    @Test
    void debugIsOffByDefaultForApplicationPackage(CapturedOutput output) {
        Logger appLogger = LoggerFactory.getLogger(LOGGER_NAME);
        Logger packageLogger = LoggerFactory.getLogger("si.result.project.eearly");

        assertThat(appLogger.isDebugEnabled()).isFalse();
        assertThat(packageLogger.isDebugEnabled()).isFalse();
        assertThat(appLogger.isInfoEnabled()).isTrue();

        appLogger.debug("characterization-marker-debug");
        assertThat(output.getOut()).doesNotContain("characterization-marker-debug");
    }
}
