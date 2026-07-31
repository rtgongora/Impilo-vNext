package zw.gov.mohcc.impilo.medicine.neuro;

import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

/**
 * The Glasgow Coma Scale.
 *
 * <p><strong>Source.</strong> Teasdale G, Jennett B. <em>Assessment of coma and impaired
 * consciousness: a practical scale.</em> Lancet 1974;2:81–84, with the 2014 structured-approach
 * revision from the Glasgow Structured Approach to Assessment of the Glasgow Coma Scale. The primary
 * texts are not vendored under {@code docs/reference}; the response definitions are transcribed
 * engineering seeds pending MoHCC ratification.</p>
 *
 * <p><strong>An untestable component does not score one.</strong> This is the single most consequential
 * rule in the scale and the one most often broken in software. An intubated patient cannot make a
 * verbal response; scoring that as V1 — "no verbal response" — produces a total that reports a
 * conversant patient as deeply comatose. The same applies to an eye closed by periorbital swelling.
 * The 2014 guidance is explicit: untestable components are reported as {@code NT}, and no total is
 * derived. This class implements that: each response enum carries its own {@code NOT_TESTABLE}
 * constant, which yields component notation and a refusal for the total, never a substituted 1.</p>
 *
 * <p><strong>The components carry the information; the total loses it.</strong> E2V2M5 and E4V1M4
 * both total 9 and mean different things, and motor score alone carries most of the prognostic
 * weight. The notation is always returned so a surface can show the components rather than a bare
 * number.</p>
 *
 * <p><strong>What this class will not do.</strong> It sums three observed responses. It does not
 * grade head injury severity, does not decide whether to intubate or image, and is not validated in
 * children, where the paediatric adaptation applies — that adaptation belongs in
 * {@code paediatric-domain}, not here.</p>
 */
public final class GlasgowComaScale {

    /** Bumped whenever a response definition or totalling rule changes a published score. */
    public static final String CONTENT_VERSION = "impilo-gcs-1974-2014-1.0.0";

    /** The lowest attainable total when all three components are testable. */
    public static final int MIN_SCORE = 3;

    /** The highest attainable total. */
    public static final int MAX_SCORE = 15;

    private GlasgowComaScale() {
    }

    /** Eye-opening response, E1–E4, or not testable. */
    public enum EyeResponse {

        /** E4 — eyes open spontaneously. */
        SPONTANEOUS(4, "E4", "Opens eyes spontaneously"),

        /** E3 — eyes open to speech or shouted request. */
        TO_SOUND(3, "E3", "Opens eyes to sound"),

        /** E2 — eyes open to fingertip pressure. */
        TO_PRESSURE(2, "E2", "Opens eyes to pressure"),

        /** E1 — eyes do not open to any stimulus. */
        NONE(1, "E1", "No eye opening"),

        /**
         * ENT — the eye cannot be assessed, for example a lid closed by swelling or a dressing.
         * This is not E1. No total is derived when this is present.
         */
        NOT_TESTABLE(null, "ENT", "Eye response not testable");

        private final Integer points;
        private final String notation;
        private final String label;

        EyeResponse(Integer points, String notation, String label) {
            this.points = points;
            this.notation = notation;
            this.label = label;
        }

        /** The response's contribution, or null when not testable. */
        public Integer points() {
            return points;
        }

        /** The published shorthand, for example {@code E4} or {@code ENT}. */
        public String notation() {
            return notation;
        }

        /** The response wording as it should be presented to a clinician. */
        public String label() {
            return label;
        }
    }

    /** Verbal response, V1–V5, or not testable. */
    public enum VerbalResponse {

        /** V5 — oriented to person, place and time. */
        ORIENTED(5, "V5", "Oriented"),

        /** V4 — confused conversation, but able to answer questions. */
        CONFUSED(4, "V4", "Confused"),

        /** V3 — inappropriate words. */
        WORDS(3, "V3", "Words, not conversational"),

        /** V2 — incomprehensible sounds. */
        SOUNDS(2, "V2", "Sounds, not words"),

        /** V1 — no verbal response. */
        NONE(1, "V1", "No verbal response"),

        /**
         * VNT — the patient cannot speak for a reason unrelated to consciousness: intubated,
         * tracheostomised, or with a language barrier the assessor cannot bridge. This is emphatically
         * not V1, and scoring it as V1 is the classic error this class exists to prevent.
         */
        NOT_TESTABLE(null, "VNT", "Verbal response not testable (e.g. intubated)");

        private final Integer points;
        private final String notation;
        private final String label;

        VerbalResponse(Integer points, String notation, String label) {
            this.points = points;
            this.notation = notation;
            this.label = label;
        }

        /** The response's contribution, or null when not testable. */
        public Integer points() {
            return points;
        }

        /** The published shorthand, for example {@code V5} or {@code VNT}. */
        public String notation() {
            return notation;
        }

        /** The response wording as it should be presented to a clinician. */
        public String label() {
            return label;
        }
    }

    /** Motor response, M1–M6, or not testable. */
    public enum MotorResponse {

        /** M6 — obeys commands. */
        OBEYS_COMMANDS(6, "M6", "Obeys commands"),

        /** M5 — localises to pressure. */
        LOCALISING(5, "M5", "Localises to pressure"),

        /** M4 — normal flexion, withdraws from pressure. */
        NORMAL_FLEXION(4, "M4", "Normal flexion (withdraws)"),

        /** M3 — abnormal flexion, decorticate posturing. */
        ABNORMAL_FLEXION(3, "M3", "Abnormal flexion (decorticate)"),

        /** M2 — extension, decerebrate posturing. */
        EXTENSION(2, "M2", "Extension (decerebrate)"),

        /** M1 — no motor response. */
        NONE(1, "M1", "No motor response"),

        /**
         * MNT — the motor response cannot be assessed, for example under neuromuscular blockade or
         * with a spinal cord injury. This is not M1.
         */
        NOT_TESTABLE(null, "MNT", "Motor response not testable (e.g. paralysed, sedated)");

        private final Integer points;
        private final String notation;
        private final String label;

        MotorResponse(Integer points, String notation, String label) {
            this.points = points;
            this.notation = notation;
            this.label = label;
        }

        /** The response's contribution, or null when not testable. */
        public Integer points() {
            return points;
        }

        /** The published shorthand, for example {@code M6} or {@code MNT}. */
        public String notation() {
            return notation;
        }

        /** The response wording as it should be presented to a clinician. */
        public String label() {
            return label;
        }
    }

    /** Conventional severity bands. Only meaningful when all three components were testable. */
    public enum Severity {

        /** Total 13–15. */
        MILD,

        /** Total 9–12. */
        MODERATE,

        /** Total 3–8. */
        SEVERE
    }

    /**
     * GCS from the three observed responses.
     *
     * @param eye    the eye-opening response; null is refused
     * @param verbal the verbal response; null is refused. Use
     *               {@link VerbalResponse#NOT_TESTABLE} for an intubated patient — never
     *               {@link VerbalResponse#NONE}
     * @param motor  the motor response; null is refused
     */
    public static Scoring of(EyeResponse eye, VerbalResponse verbal, MotorResponse motor) {
        if (eye == null || verbal == null || motor == null) {
            String missing = eye == null ? "Eye opening"
                    : verbal == null ? "Verbal response" : "Motor response";
            return new Scoring(null, null, null, null, null, null, false,
                    RefusalReason.INPUT_MISSING,
                    missing + " was not recorded. An unassessed component is not a component that"
                            + " scored one; record it, or record it as not testable.",
                    CONTENT_VERSION);
        }

        String notation = eye.notation() + " " + verbal.notation() + " " + motor.notation();

        boolean anyNotTestable = eye == EyeResponse.NOT_TESTABLE
                || verbal == VerbalResponse.NOT_TESTABLE
                || motor == MotorResponse.NOT_TESTABLE;

        if (anyNotTestable) {
            return new Scoring(null, null, eye.points(), verbal.points(), motor.points(), notation,
                    true,
                    RefusalReason.INSTRUMENT_NOT_APPLICABLE,
                    "One or more components could not be tested, so no total is derived. Report the"
                            + " components as " + notation + ". Substituting 1 for an untestable"
                            + " component — for example scoring an intubated patient's verbal response"
                            + " as V1 — would report a conversant patient as deeply comatose.",
                    CONTENT_VERSION);
        }

        int total = eye.points() + verbal.points() + motor.points();
        Severity severity = total >= 13 ? Severity.MILD : total >= 9 ? Severity.MODERATE : Severity.SEVERE;

        return new Scoring(total, severity, eye.points(), verbal.points(), motor.points(), notation,
                false, null, null, CONTENT_VERSION);
    }

    /**
     * A GCS result, or a stated reason there is no total.
     *
     * <p>When a component was untestable the total is null but {@link #notation()} and the component
     * points are still present, because those remain clinically valid and are what should be recorded.
     * That case is a refusal of the total only, not of the assessment.</p>
     *
     * @param total                the sum 3–15; null when refused or when a component was untestable
     * @param severity             the conventional band; null when there is no total
     * @param eyePoints            the eye component; null when that component was untestable
     * @param verbalPoints         the verbal component; null when that component was untestable
     * @param motorPoints          the motor component; null when that component was untestable
     * @param notation             the published shorthand, for example {@code E4 VNT M6}; always
     *                             present once responses were supplied, and the correct thing to
     *                             display when there is no total
     * @param hasUntestableComponent true when at least one component could not be tested
     * @param reason               machine-readable refusal code; null when a total was computed
     * @param explanation          clinician-readable refusal text; null when a total was computed
     * @param contentVersion       the {@link #CONTENT_VERSION} that produced this result
     */
    public record Scoring(Integer total,
                          Severity severity,
                          Integer eyePoints,
                          Integer verbalPoints,
                          Integer motorPoints,
                          String notation,
                          boolean hasUntestableComponent,
                          RefusalReason reason,
                          String explanation,
                          String contentVersion) {

        /** True when a total is present. False means {@link #reason()} explains why it is not. */
        public boolean computed() {
            return total != null;
        }
    }
}
