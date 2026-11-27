package si.result.project.eearly.port.algorithmexecution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecution;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecutionTriggerType;
import si.result.project.eearly.model.analysis.Analysis;
import si.result.project.eearly.model.analysis.AnalysisSelection;
import si.result.project.eearly.model.patient.Patient;
import si.result.project.eearly.port.algorithmexecution.ScheduledMeasurementWindowStrategy.MeasurementWindow;

@ExtendWith(MockitoExtension.class)
class AlgorithmExecutionPayloadBuilderTest {

    private static final String DOMAIN = "https://eearly.example.org/";

    @Mock
    private AlgorithmDataPreparationService preparation;
    @Mock
    private CallbackTokenService tokens;

    private final UUID executionId = UUID.randomUUID();
    private final UUID analysisId = UUID.randomUUID();
    private Patient patient;
    private Algorithm algorithm;
    private AlgorithmExecution execution;
    private Analysis analysis;

    @BeforeEach
    void setUp() {
        patient = mock(Patient.class);
        algorithm = mock(Algorithm.class);
        analysis = mock(Analysis.class);
        execution = mock(AlgorithmExecution.class);
        lenient().when(analysis.getId()).thenReturn(analysisId);
        lenient().when(algorithm.getRunnerConfig()).thenReturn("{\"cfg\":1}");
        lenient().when(execution.getId()).thenReturn(executionId);
        lenient().when(execution.getPatient()).thenReturn(patient);
        lenient().when(execution.getAlgorithm()).thenReturn(algorithm);
        lenient().when(execution.getInputSelections()).thenReturn(List.of());
    }

    private AlgorithmExecutionPayloadBuilder builder(final ScheduledMeasurementWindowStrategy strategy) {
        return new AlgorithmExecutionPayloadBuilder(preparation, tokens, strategy, DOMAIN);
    }

    private String callbackUrlPassedToSelectionPath() {
        final ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(preparation).prepareInputData(eq(patient), eq(algorithm), anyList(), eq(analysisId),
                eq("{\"cfg\":1}"), url.capture());
        return url.getValue();
    }

    @Test
    @DisplayName("With selections and tokens on, the callback URL carries a freshly issued token")
    void selectionsWithTokens_buildCallbackUrl() {
        final AnalysisSelection selection = mock(AnalysisSelection.class);
        when(execution.getAnalysis()).thenReturn(analysis);
        when(execution.getInputSelections()).thenReturn(List.of(selection));
        when(tokens.isEnabled()).thenReturn(true);
        when(tokens.issueToken(executionId)).thenReturn("TOKEN.SIG");
        when(preparation.prepareInputData(any(), any(), anyList(), any(), any(), any())).thenReturn("{payload}");

        final String payload = builder(ScheduledMeasurementWindowStrategy.NONE).buildPayload(execution);

        assertThat(payload).isEqualTo("{payload}");
        // the trailing slash of the configured domain is not doubled
        assertThat(callbackUrlPassedToSelectionPath())
                .isEqualTo("https://eearly.example.org/api/v1/analyses/" + analysisId + "/result?token=TOKEN.SIG");
    }

    @Test
    @DisplayName("Without tokens there is no callback URL and no token is issued")
    void selectionsWithoutTokens_haveNoCallbackUrl() {
        final AnalysisSelection selection = mock(AnalysisSelection.class);
        when(execution.getAnalysis()).thenReturn(analysis);
        when(execution.getInputSelections()).thenReturn(List.of(selection));
        when(tokens.isEnabled()).thenReturn(false);
        when(preparation.prepareInputData(any(), any(), anyList(), any(), any(), any())).thenReturn("{payload}");

        builder(ScheduledMeasurementWindowStrategy.NONE).buildPayload(execution);

        assertThat(callbackUrlPassedToSelectionPath()).isNull();
        verify(tokens, never()).issueToken(any());
    }

    @Test
    void callbackUrlsEnabled_followsTheTokenService() {
        when(tokens.isEnabled()).thenReturn(true, false);
        final AlgorithmExecutionPayloadBuilder builder = builder(ScheduledMeasurementWindowStrategy.NONE);

        assertThat(builder.callbackUrlsEnabled()).isTrue();
        assertThat(builder.callbackUrlsEnabled()).isFalse();
    }

    @Test
    @DisplayName("Schema-driven input: no selections, input schema present")
    void schemaPath_usesTheAlgorithmSchema() {
        when(algorithm.getInputSchema()).thenReturn("{\"type\":\"object\"}");
        when(tokens.isEnabled()).thenReturn(false);
        when(preparation.prepareInputData(eq(patient), eq(algorithm), anyList(), isNull(), any(), isNull()))
                .thenReturn("{schema}");

        final String payload = builder(ScheduledMeasurementWindowStrategy.NONE).buildPayload(execution);

        assertThat(payload).isEqualTo("{schema}");
        verify(preparation).prepareInputData(patient, algorithm, List.of(), null, "{\"cfg\":1}", null);
    }

    @Test
    @DisplayName("The schema path also gets a callback URL, when the execution has an analysis and tokens are on")
    void schemaPath_withAnalysis_getsCallbackUrl() {
        when(execution.getAnalysis()).thenReturn(analysis);
        when(algorithm.getInputSchema()).thenReturn("{\"type\":\"object\"}");
        when(tokens.isEnabled()).thenReturn(true);
        when(tokens.issueToken(executionId)).thenReturn("T.S");

        builder(ScheduledMeasurementWindowStrategy.NONE).buildPayload(execution);

        verify(preparation).prepareInputData(patient, algorithm, List.of(), null, "{\"cfg\":1}",
                "https://eearly.example.org/api/v1/analyses/" + analysisId + "/result?token=T.S");
    }

    @Test
    @DisplayName("Without an analysis there is nothing to call back to, even with tokens on")
    void schemaPath_withoutAnalysis_hasNoCallbackUrl() {
        when(algorithm.getInputSchema()).thenReturn("{\"type\":\"object\"}");
        when(tokens.isEnabled()).thenReturn(true);

        builder(ScheduledMeasurementWindowStrategy.NONE).buildPayload(execution);

        verify(preparation).prepareInputData(patient, algorithm, List.of(), null, "{\"cfg\":1}", null);
        verify(tokens, never()).issueToken(any());
    }

    @Test
    @DisplayName("A SCHEDULED execution uses the window of the strategy, when it provides one")
    void scheduled_usesStrategyWindow() {
        when(execution.getTriggerType()).thenReturn(AlgorithmExecutionTriggerType.SCHEDULED);
        when(algorithm.getInputSchema()).thenReturn("{\"type\":\"object\"}");
        when(tokens.isEnabled()).thenReturn(false);
        final ScheduledMeasurementWindowStrategy strategy =
                e -> Optional.of(new MeasurementWindow("2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z"));
        when(preparation.prepareInputData(any(), any(), any(String.class), any(String.class), any(), any(), any(),
                any())).thenReturn("{window}");

        final String payload = builder(strategy).buildPayload(execution);

        assertThat(payload).isEqualTo("{window}");
        verify(preparation).prepareInputData(patient, algorithm, "2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z",
                executionId, null, "{\"cfg\":1}", null);
    }

    @Test
    @DisplayName("The default strategy provides no window, so a SCHEDULED execution takes the plain schema path")
    void scheduled_withDefaultStrategy_fallsBackToSchemaPath() {
        when(execution.getTriggerType()).thenReturn(AlgorithmExecutionTriggerType.SCHEDULED);
        when(algorithm.getInputSchema()).thenReturn("{\"type\":\"object\"}");
        when(tokens.isEnabled()).thenReturn(false);

        builder(ScheduledMeasurementWindowStrategy.NONE).buildPayload(execution);

        verify(preparation).prepareInputData(patient, algorithm, List.of(), null, "{\"cfg\":1}", null);
        verify(preparation, never()).prepareInputData(any(), any(), any(String.class), any(String.class), any(), any(),
                any(), any());
    }

    @Test
    void withoutSchemaOrSelections_theStoredInputParametersAreTheBody() {
        when(algorithm.getInputSchema()).thenReturn(" ");
        when(execution.getInputParameters()).thenReturn("{\"a\":1}");

        assertThat(builder(ScheduledMeasurementWindowStrategy.NONE).buildPayload(execution)).isEqualTo("{\"a\":1}");
    }

    @Test
    void withoutAnything_theBodyIsAnEmptyObject() {
        when(algorithm.getInputSchema()).thenReturn(null);
        when(execution.getInputParameters()).thenReturn(null);

        assertThat(builder(ScheduledMeasurementWindowStrategy.NONE).buildPayload(execution)).isEqualTo("{}");
    }

    @Test
    void defaultStrategy_providesNoWindow() {
        assertThat(ScheduledMeasurementWindowStrategy.NONE.resolve(execution)).isEmpty();
    }
}
