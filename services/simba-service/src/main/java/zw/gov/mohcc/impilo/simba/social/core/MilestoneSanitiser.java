package zw.gov.mohcc.impilo.simba.social.core;

import java.util.regex.Pattern;

/**
 * Sanitises milestone/status text shared to the social feed so it NEVER emits clinical values.
 * Wellness milestones ("Walked 10,000 steps!", "7-day streak!") are welcome; raw clinical readings
 * (BP 150/95, glucose 12 mmol/L, weight in kg, HbA1c) are stripped/blocked. This protects the person
 * from over-sharing sensitive clinical data through a celebratory social surface.
 */
public final class MilestoneSanitiser {

    private MilestoneSanitiser() {
    }

    // Patterns that look like clinical measurements — blocked from social milestone text.
    private static final Pattern BP = Pattern.compile("\\b\\d{2,3}\\s*/\\s*\\d{2,3}\\b");
    private static final Pattern CLINICAL_UNIT = Pattern.compile(
            "\\b\\d+(\\.\\d+)?\\s*(mmol/l|mg/dl|mmhg|kg|hba1c|bpm|beats)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CLINICAL_TERM = Pattern.compile(
            "\\b(blood\\s*pressure|glucose|hba1c|cholesterol|viral\\s*load|cd4|weight\\s*is|bmi\\s*is)\\b",
            Pattern.CASE_INSENSITIVE);

    /** True when the text contains a clinical value that must not be shared socially. */
    public static boolean containsClinicalValue(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return BP.matcher(text).find()
                || CLINICAL_UNIT.matcher(text).find()
                || CLINICAL_TERM.matcher(text).find();
    }

    /**
     * Returns a milestone-safe version of the text. If a clinical value is present, throws — the
     * caller must reject the share with a helpful message rather than silently mangling the post.
     */
    public static String requireSafe(String text) {
        if (containsClinicalValue(text)) {
            throw new IllegalArgumentException(
                    "Milestone shares can celebrate progress but cannot include clinical values "
                            + "(e.g. blood pressure, glucose). Please rephrase without the reading.");
        }
        return text;
    }
}
