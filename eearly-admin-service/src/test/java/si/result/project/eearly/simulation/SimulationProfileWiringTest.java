package si.result.project.eearly.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import si.result.project.eearly.config.algorithm.AlgorithmApiProperties;
import si.result.project.eearly.config.algorithm.AlgorithmHttpClient;
import si.result.project.eearly.facade.algorithmexecution.AlgorithmSimulationService;
import si.result.project.eearly.facade.algorithmexecution.HttpAlgorithmExecutionAdapter;
import si.result.project.eearly.facade.analysis.AnalysisServiceFacade;
import si.result.project.eearly.port.algorithmexecution.AlgorithmDataPreparationService;
import si.result.project.eearly.port.algorithmexecution.AlgorithmExecutionService;
import si.result.project.eearly.port.analysis.AnalysisService;
import si.result.project.eearly.port.container.AlgorithmContainerService;

/**
 * The algorithm simulation exists only with the Spring profile {@code simulation}. Without it the real HTTP adapter
 * is the one and only {@link AlgorithmContainerService}.
 */
class SimulationProfileWiringTest {

    /** Scans exactly the two candidates for {@link AlgorithmContainerService}, with their collaborators mocked. */
    @Configuration
    @ComponentScan(
            basePackageClasses = HttpAlgorithmExecutionAdapter.class,
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = {HttpAlgorithmExecutionAdapter.class, AlgorithmSimulationService.class}))
    static class Candidates {

        @Bean
        AlgorithmExecutionService algorithmExecutionService() {
            return mock(AlgorithmExecutionService.class);
        }

        @Bean
        AlgorithmDataPreparationService algorithmDataPreparationService() {
            return mock(AlgorithmDataPreparationService.class);
        }

        @Bean
        AnalysisService analysisService() {
            return mock(AnalysisService.class);
        }

        @Bean
        AnalysisServiceFacade analysisServiceFacade() {
            return mock(AnalysisServiceFacade.class);
        }

        @Bean
        AlgorithmApiProperties algorithmApiProperties() {
            return new AlgorithmApiProperties();
        }

        @Bean
        AlgorithmHttpClient algorithmHttpClient(final AlgorithmApiProperties properties) {
            return new AlgorithmHttpClient(properties);
        }
    }

    private ApplicationContextRunner runner(final String... profiles) {
        return new ApplicationContextRunner()
                .withUserConfiguration(Candidates.class)
                .withPropertyValues("spring.application.name=eearly-admin-service")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profiles));
    }

    @Test
    @DisplayName("Without the simulation profile there is no simulation bean and the real adapter is used")
    void withoutProfile_shouldUseTheRealAdapter() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AlgorithmSimulationService.class);
            assertThat(context).hasSingleBean(AlgorithmContainerService.class);
            assertThat(context.getBean(AlgorithmContainerService.class))
                    .isInstanceOf(HttpAlgorithmExecutionAdapter.class);
        });
    }

    @Test
    @DisplayName("Other profiles, such as development, do not enable the simulation")
    void withDevelopmentProfile_shouldStillUseTheRealAdapter() {
        runner("development", "unrestricted").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AlgorithmSimulationService.class);
            assertThat(context.getBean(AlgorithmContainerService.class))
                    .isInstanceOf(HttpAlgorithmExecutionAdapter.class);
        });
    }

    @Test
    @DisplayName("With the simulation profile the simulation replaces the real adapter")
    void withSimulationProfile_shouldUseTheSimulationOnly() {
        runner("simulation").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(HttpAlgorithmExecutionAdapter.class);
            assertThat(context).hasSingleBean(AlgorithmContainerService.class);
            assertThat(context.getBean(AlgorithmContainerService.class))
                    .isInstanceOf(AlgorithmSimulationService.class);
        });
    }

    @Test
    @DisplayName("The simulation profile combines with development")
    void withDevelopmentAndSimulationProfiles_shouldUseTheSimulationOnly() {
        runner("development", "simulation").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(HttpAlgorithmExecutionAdapter.class);
            assertThat(context.getBean(AlgorithmContainerService.class))
                    .isInstanceOf(AlgorithmSimulationService.class);
        });
    }
}
