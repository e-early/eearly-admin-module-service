package si.result.project.eearly.model.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnalysisStateTest {

    @Test
    void fromFeedback_confirmedOverridesAnyState() {
        for (final AnalysisState state : AnalysisState.values()) {
            assertThat(AnalysisState.fromFeedback(state, FeedbackState.CONFIRMED))
                    .isEqualTo(AnalysisState.CONFIRMED);
        }
    }

    @Test
    void fromFeedback_rejectedOverridesAnyState() {
        for (final AnalysisState state : AnalysisState.values()) {
            assertThat(AnalysisState.fromFeedback(state, FeedbackState.REJECTED))
                    .isEqualTo(AnalysisState.REJECTED);
        }
    }

    @Test
    void fromFeedback_noneKeepsCurrentState() {
        for (final AnalysisState state : AnalysisState.values()) {
            assertThat(AnalysisState.fromFeedback(state, FeedbackState.NONE)).isEqualTo(state);
        }
    }
}
