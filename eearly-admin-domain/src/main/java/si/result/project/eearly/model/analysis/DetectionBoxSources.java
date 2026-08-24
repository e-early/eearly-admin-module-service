package si.result.project.eearly.model.analysis;

public final class DetectionBoxSources {

    public static final String MODEL_PREDICTION = "MODEL_PREDICTION";
    public static final String CARETAKER = "CARETAKER";

    private static final String LEGACY_ALGORITHM = "ALGORITHM";
    private static final String LEGACY_CARETAKER_FEEDBACK = "CARETAKER_FEEDBACK";

    private DetectionBoxSources() {}

    public static String resolveSource(final String source) {
        if (CARETAKER.equals(source) || LEGACY_CARETAKER_FEEDBACK.equals(source)) {
            return CARETAKER;
        }
        if (source == null || LEGACY_ALGORITHM.equals(source) || MODEL_PREDICTION.equals(source)) {
            return MODEL_PREDICTION;
        }
        return MODEL_PREDICTION;
    }

    public static boolean isCaretakerSource(final String source) {
        return CARETAKER.equals(resolveSource(source));
    }

    public static String toFeedbackExportSource(final String source) {
        return resolveSource(source);
    }
}
