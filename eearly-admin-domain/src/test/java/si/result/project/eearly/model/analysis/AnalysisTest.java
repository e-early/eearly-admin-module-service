package si.result.project.eearly.model.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.analysis.command.CreateAnalysisCommand;
import si.result.project.eearly.model.patient.Patient;
import si.result.spring.boot.bricks.exception.DomainException;

class AnalysisTest {

    private static CreateAnalysisCommand command(final AnalysisTriggerType triggerType) {
        return new CreateAnalysisCommand(
                "Apnea detection",
                AnalysisState.IN_PROGRESS,
                FeedbackState.NONE,
                Boolean.FALSE,
                mock(Patient.class),
                mock(Algorithm.class),
                List.of(),
                null,
                triggerType
        );
    }

    @ParameterizedTest
    @EnumSource(AnalysisTriggerType.class)
    void create_setsTriggerTypeFromCommand(final AnalysisTriggerType triggerType) {
        final Analysis analysis = Analysis.create(command(triggerType));

        assertThat(analysis.getTriggerType()).isEqualTo(triggerType);
    }

    @Test
    void create_withoutTriggerType_throwsDomainException() {
        assertThatThrownBy(() -> Analysis.create(command(null)))
                .isInstanceOf(DomainException.class)
                .extracting("code")
                .isEqualTo(DomainExceptionCode.ANALYSIS_TRIGGER_TYPE_NULL);
    }

    @Test
    void triggerTypeValues_areManualAndScheduled() {
        assertThat(AnalysisTriggerType.values())
                .containsExactly(AnalysisTriggerType.MANUAL, AnalysisTriggerType.SCHEDULED);
    }
}
