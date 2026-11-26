package si.result.project.eearly.model.patient;

import java.util.Set;

/**
 * Languages a patient's app, push notifications and emails can be in, as ISO 639-1 codes.
 */
public final class PatientLanguage {

    public static final String SLOVENIAN = "sl";
    public static final String ENGLISH = "en";

    /** All current customers are Slovenian, so a patient is in Slovenian unless set otherwise. */
    public static final String DEFAULT = SLOVENIAN;

    public static final Set<String> SUPPORTED = Set.of(SLOVENIAN, ENGLISH);

    private PatientLanguage() {}

    public static boolean isSupported(final String language) {
        return language != null && SUPPORTED.contains(language);
    }
}
