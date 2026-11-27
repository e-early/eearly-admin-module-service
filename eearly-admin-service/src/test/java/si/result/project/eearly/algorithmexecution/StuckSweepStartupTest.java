package si.result.project.eearly.algorithmexecution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import si.result.project.eearly.EEarlyAdminService;
import si.result.project.eearly.config.AlgorithmExecutionStuckSweepProperties;
import si.result.project.eearly.port.algorithmexecution.AlgorithmExecutionService;
import si.result.project.eearly.port.algorithmexecution.AlgorithmExecutionStuckSweepService;
import si.result.project.eearly.port.algorithmexecution.AlgorithmExecutionStuckSweepSettingsValidator;
import si.result.project.eearly.port.analysis.AnalysisService;

/** The sweep as the running service wires it: properties from the environment, the startup validation, the switch. */
class StuckSweepStartupTest {

    @Configuration
    @EnableConfigurationProperties(AlgorithmExecutionStuckSweepProperties.class)
    static class Cfg {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Cfg.class, AlgorithmExecutionStuckSweepSettingsValidator.class,
                    AlgorithmExecutionStuckSweepService.class)
            .withBean(AlgorithmExecutionService.class, () -> mock(AlgorithmExecutionService.class))
            .withBean(AnalysisService.class, () -> mock(AnalysisService.class));

    @Test
    @DisplayName("The defaults start, and the sweep is on")
    void defaultsStart() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AlgorithmExecutionStuckSweepService.class);
            final var properties = context.getBean(AlgorithmExecutionStuckSweepProperties.class);
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getTimeoutMinutes()).isEqualTo(90);
            assertThat(properties.getPollIntervalMs()).isEqualTo(300_000);
        });
    }

    @Test
    @DisplayName("A sweep timeout not longer than the algorithm HTTP timeout fails the start, naming both settings")
    void timeoutNotLongerThanHttpTimeout_failsTheStart() {
        runner.withPropertyValues("algorithm.api.default-timeout-seconds=7200",
                        "algorithm.stuck-execution-sweep.timeout-minutes=120")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure().getMessage() + rootMessage(context.getStartupFailure()))
                            .contains("algorithm.stuck-execution-sweep.timeout-minutes")
                            .contains("BENTOML_TIMEOUT");
                });
    }

    @Test
    void timeoutLongerThanTheHttpTimeout_starts() {
        runner.withPropertyValues("algorithm.api.default-timeout-seconds=7200",
                        "algorithm.stuck-execution-sweep.timeout-minutes=121")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("The sweep can be switched off, and then it is not validated either")
    void switchedOff_hasNoSweepAndNoValidation() {
        runner.withPropertyValues("algorithm.stuck-execution-sweep.enabled=false",
                        "algorithm.stuck-execution-sweep.timeout-minutes=1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(AlgorithmExecutionStuckSweepService.class);
                    assertThat(context).doesNotHaveBean(AlgorithmExecutionStuckSweepSettingsValidator.class);
                });
    }

    @Test
    void settingsAreBoundFromTheEnvironment() {
        runner.withPropertyValues("algorithm.stuck-execution-sweep.timeout-minutes=200",
                        "algorithm.stuck-execution-sweep.poll-interval-ms=60000")
                .run(context -> {
                    final var properties = context.getBean(AlgorithmExecutionStuckSweepProperties.class);
                    assertThat(properties.getTimeoutMinutes()).isEqualTo(200);
                    assertThat(properties.getPollIntervalMs()).isEqualTo(60_000);
                });
    }

    @Test
    @DisplayName("Scheduling is enabled in the application, so the @Scheduled sweep actually runs")
    void schedulingIsEnabledInTheApplication() {
        assertThat(EEarlyAdminService.class.isAnnotationPresent(EnableScheduling.class)).isTrue();
        assertThat(EEarlyAdminService.class.isAnnotationPresent(EnableAsync.class)).isTrue();
    }

    private static String rootMessage(final Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
