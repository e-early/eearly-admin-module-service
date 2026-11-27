package si.result.project.eearly.model.analysis;

public enum AnalysisState {
    ORDERED,
    IN_PROGRESS,
    WAITING_FOR_CONFIRMATION,
    REJECTED,
    CONFIRMED,
    COMPLETED,
    ERROR;

    public static AnalysisState fromFeedback(final AnalysisState currentState, final FeedbackState feedback) {
        return switch (feedback) {
            case CONFIRMED -> CONFIRMED;
            case REJECTED -> REJECTED;
            default -> currentState;
        };
    }
}
