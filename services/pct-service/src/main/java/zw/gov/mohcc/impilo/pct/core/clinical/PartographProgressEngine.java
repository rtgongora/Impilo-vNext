package zw.gov.mohcc.impilo.pct.core.clinical;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The classic WHO partograph, evaluated rather than merely drawn.
 *
 * <p><b>FROZEN AND DEMOTED (2026-07-27).</b> The WHO Labour Care Guide (2020) replaced the classic
 * partograph because the fixed 1 cm/hour alert line labelled many normal labours as obstructed and
 * drove avoidable caesareans. The current monitoring standard is the Labour Care Guide
 * ({@code LabourCareGuideProgressEngine} in the clinical-knowledge platform). This engine remains as
 * a fallback only — for a record created under the classic standard, or a site not yet on the LCG —
 * and its logic is frozen: no threshold here is to be changed, because the reason to change any of it
 * is the reason it was demoted. Every assessment it emits is stamped {@code degraded = true} with
 * {@code monitoringStandard = WHO_PARTOGRAPH_CLASSIC}, so a consumer can never mistake a classic
 * verdict — especially an "action line crossed, decide now" driven by the 1 cm/hour line — for the
 * current standard's reading of the same labour. Exactly one monitoring standard governs any one
 * labour; this engine and the LCG engine must never both be presented as live for the same session.</p>
 *
 * <p>A partograph is a decision instrument, not a chart. Its whole purpose is the alert line and
 * the action line: cervical dilation in established labour is expected to advance at about one
 * centimetre an hour, and a labour that falls behind that rate is the single most useful early
 * warning of obstruction available to a midwife without imaging. Drawing the curve and leaving
 * the reading to the eye is what the paper form already does. Computing the crossing is what a
 * digital record can add, and it is the reason this engine exists in the service rather than in
 * the chart component.</p>
 *
 * <p><strong>Silence is not reassurance.</strong> With no observations, or with dilation never
 * recorded, the verdict is {@link ProgressStatus#INSUFFICIENT_DATA} — never
 * {@code LEFT_OF_ALERT}. A partograph with no points on it means nobody has assessed this
 * labour, which is a more urgent finding than a labour progressing slowly, and it must never
 * render as the reassuring case.</p>
 *
 * <p>The engine is pure: no Spring, no repository, no clock of its own. Everything it concludes
 * is a function of the observations handed to it, so a conclusion is reproducible from the
 * record and can be re-derived during review.</p>
 */
public final class PartographProgressEngine {

    /** Zimbabwe uses the classic WHO partograph, whose alert line rises 1 cm per hour. */
    private static final BigDecimal ALERT_RATE_CM_PER_HOUR = BigDecimal.ONE;

    /** The action line sits four hours to the right of the alert line. */
    private static final int ACTION_LINE_OFFSET_HOURS = 4;

    /** Active labour is recognised at 4 cm on the classic partograph. */
    public static final BigDecimal ACTIVE_PHASE_DILATION_CM = new BigDecimal("4");

    public static final BigDecimal FULL_DILATION_CM = new BigDecimal("10");

    /**
     * WHO observation intervals in active labour: the fetal heart every 30 minutes, maternal
     * pulse every 30 minutes, blood pressure and temperature every 4 hours, dilation every
     * 4 hours. An interval that has lapsed is reported as an outstanding observation, because
     * an unrecorded fetal heart is not a normal fetal heart.
     */
    private static final int FHR_INTERVAL_MINUTES = 30;
    private static final int BP_INTERVAL_MINUTES = 240;
    private static final int DILATION_INTERVAL_MINUTES = 240;

    public static final String CONTENT_VERSION = "who-partograph-classic-1.0.0";
    public static final String CONTENT_SOURCE = "WHO partograph (classic), alert line 1 cm/hour, action line +4 hours";

    /** The demoted standard this engine speaks. Stamped on every assessment so it is never read as current. */
    public static final String MONITORING_STANDARD = "WHO_PARTOGRAPH_CLASSIC";

    private PartographProgressEngine() {
    }

    public enum ProgressStatus {
        /** Progressing at or ahead of the alert line. */
        LEFT_OF_ALERT,
        /** The alert line has been crossed: reassess, and at a peripheral site plan transfer. */
        BETWEEN_ALERT_AND_ACTION,
        /** The action line has been crossed: intervene now. */
        AT_OR_RIGHT_OF_ACTION,
        /** Fully dilated — the partograph's progress question no longer applies. */
        SECOND_STAGE,
        /** Not enough recorded to say. Never to be rendered as the reassuring case. */
        INSUFFICIENT_DATA
    }

    /** One labour observation, reduced to what the progress question needs. */
    public record Observation(
            OffsetDateTime observedAt,
            BigDecimal cervicalDilationCm,
            Integer fetalHeartRateBpm,
            Integer maternalPulseBpm,
            Integer systolicBp,
            BigDecimal temperatureC) {
    }

    public record Assessment(
            ProgressStatus status,
            BigDecimal latestDilationCm,
            OffsetDateTime latestDilationAt,
            BigDecimal expectedDilationCm,
            BigDecimal hoursBehindAlertLine,
            OffsetDateTime alertReferenceAt,
            BigDecimal alertReferenceDilationCm,
            List<String> outstandingObservations,
            List<String> observations,
            String recommendedAction,
            String contentVersion,
            String contentSource,
            String monitoringStandard,
            boolean degraded) {
    }

    /**
     * @param observations every labour observation in the session, in any order
     * @param asOf         the moment the assessment is being made — passed in rather than read
     *                     from a clock so that a review of a past labour reproduces exactly
     */
    public static Assessment assess(List<Observation> observations, OffsetDateTime asOf) {
        List<Observation> ordered = new ArrayList<>(observations == null ? List.of() : observations);
        ordered.removeIf(o -> o == null || o.observedAt() == null);
        ordered.sort(Comparator.comparing(Observation::observedAt));

        List<String> notes = new ArrayList<>();
        List<String> outstanding = outstandingObservations(ordered, asOf);

        List<Observation> dilations = ordered.stream()
                .filter(o -> o.cervicalDilationCm() != null)
                .toList();

        if (dilations.isEmpty()) {
            notes.add(ordered.isEmpty()
                    ? "No labour observations have been recorded in this session."
                    : "Observations exist but cervical dilation has never been recorded, so progress "
                      + "cannot be assessed against the alert line.");
            return new Assessment(ProgressStatus.INSUFFICIENT_DATA, null, null, null, null, null, null,
                    outstanding, notes,
                    "Assess and record cervical dilation to establish progress.",
                    CONTENT_VERSION, CONTENT_SOURCE, MONITORING_STANDARD, true);
        }

        Observation latest = dilations.get(dilations.size() - 1);

        if (latest.cervicalDilationCm().compareTo(FULL_DILATION_CM) >= 0) {
            notes.add("Fully dilated at " + latest.observedAt() + "; the alert and action lines no "
                      + "longer apply and management moves to the second stage.");
            return new Assessment(ProgressStatus.SECOND_STAGE, latest.cervicalDilationCm(),
                    latest.observedAt(), null, null, null, null, outstanding, notes,
                    "Second stage: monitor the fetal heart every five minutes.",
                    CONTENT_VERSION, CONTENT_SOURCE, MONITORING_STANDARD, true);
        }

        // The alert line starts where active labour was first recognised.
        Observation reference = dilations.stream()
                .filter(o -> o.cervicalDilationCm().compareTo(ACTIVE_PHASE_DILATION_CM) >= 0)
                .findFirst()
                .orElse(null);

        if (reference == null) {
            notes.add("Dilation has not yet reached " + ACTIVE_PHASE_DILATION_CM + " cm, so active "
                      + "labour has not been established and the alert line has no starting point. "
                      + "This is the latent phase, where slow progress is expected and is not "
                      + "itself a reason to intervene.");
            return new Assessment(ProgressStatus.INSUFFICIENT_DATA, latest.cervicalDilationCm(),
                    latest.observedAt(), null, null, null, null, outstanding, notes,
                    "Latent phase: continue observation; do not plot against the alert line yet.",
                    CONTENT_VERSION, CONTENT_SOURCE, MONITORING_STANDARD, true);
        }

        BigDecimal hoursSinceReference = hoursBetween(reference.observedAt(), latest.observedAt());
        BigDecimal expected = reference.cervicalDilationCm()
                .add(ALERT_RATE_CM_PER_HOUR.multiply(hoursSinceReference))
                .min(FULL_DILATION_CM);

        // How far behind the alert line, expressed in hours, because that is the axis a clinician
        // reads the partograph along and the unit the action line is defined in.
        BigDecimal deficitCm = expected.subtract(latest.cervicalDilationCm());
        BigDecimal hoursBehind = deficitCm.max(BigDecimal.ZERO)
                .divide(ALERT_RATE_CM_PER_HOUR, 2, RoundingMode.HALF_UP);

        ProgressStatus status;
        String action;
        if (deficitCm.compareTo(BigDecimal.ZERO) <= 0) {
            status = ProgressStatus.LEFT_OF_ALERT;
            action = "Progress is at or ahead of the alert line; continue routine partograph observations.";
            notes.add("Dilation " + latest.cervicalDilationCm() + " cm at " + hoursSinceReference
                      + " h from the " + reference.cervicalDilationCm() + " cm reference, against an "
                      + "expected " + expected + " cm.");
        } else if (hoursBehind.compareTo(BigDecimal.valueOf(ACTION_LINE_OFFSET_HOURS)) < 0) {
            status = ProgressStatus.BETWEEN_ALERT_AND_ACTION;
            action = "Alert line crossed: reassess the woman and the fetus, review contractions and "
                     + "hydration, and at a site without surgical capability begin arranging transfer now "
                     + "rather than after the action line.";
            notes.add("Dilation is " + hoursBehind + " h behind the alert line ("
                      + latest.cervicalDilationCm() + " cm recorded against an expected " + expected + " cm).");
        } else {
            status = ProgressStatus.AT_OR_RIGHT_OF_ACTION;
            action = "Action line crossed: this labour requires a decision now — full reassessment by "
                     + "the most senior clinician available, and definitive management.";
            notes.add("Dilation is " + hoursBehind + " h behind the alert line, which is at or beyond "
                      + "the action line at +" + ACTION_LINE_OFFSET_HOURS + " h.");
        }

        if (dilations.size() >= 2) {
            Observation previous = dilations.get(dilations.size() - 2);
            if (latest.cervicalDilationCm().compareTo(previous.cervicalDilationCm()) < 0) {
                // Cervical dilation does not reverse. One of the two examinations is wrong, and
                // saying so is more useful than plotting an impossible line.
                notes.add("Recorded dilation decreased from " + previous.cervicalDilationCm() + " cm to "
                          + latest.cervicalDilationCm() + " cm. Dilation does not reverse, so one of these "
                          + "examinations is in error; confirm before acting on the trend.");
            }
        }

        return new Assessment(status, latest.cervicalDilationCm(), latest.observedAt(), expected,
                hoursBehind, reference.observedAt(), reference.cervicalDilationCm(),
                outstanding, notes, action, CONTENT_VERSION, CONTENT_SOURCE, MONITORING_STANDARD, true);
    }

    /**
     * Observations whose WHO interval has lapsed. Reported separately from progress because an
     * unmonitored labour and a slow labour are different problems with different responses.
     */
    private static List<String> outstandingObservations(List<Observation> ordered, OffsetDateTime asOf) {
        List<String> outstanding = new ArrayList<>();
        if (asOf == null) {
            return outstanding;
        }
        // An empty session is deliberately NOT short-circuited to an empty list. A partograph with
        // nothing on it has every observation outstanding, and reporting "none outstanding" beside
        // an INSUFFICIENT_DATA verdict would put a reassuring line on the most alarming case.
        checkInterval(ordered, asOf, FHR_INTERVAL_MINUTES, o -> o.fetalHeartRateBpm() != null,
                "fetal heart rate", outstanding);
        checkInterval(ordered, asOf, FHR_INTERVAL_MINUTES, o -> o.maternalPulseBpm() != null,
                "maternal pulse", outstanding);
        checkInterval(ordered, asOf, BP_INTERVAL_MINUTES, o -> o.systolicBp() != null,
                "blood pressure", outstanding);
        checkInterval(ordered, asOf, BP_INTERVAL_MINUTES, o -> o.temperatureC() != null,
                "temperature", outstanding);
        checkInterval(ordered, asOf, DILATION_INTERVAL_MINUTES, o -> o.cervicalDilationCm() != null,
                "cervical dilation", outstanding);
        return outstanding;
    }

    private static void checkInterval(List<Observation> ordered, OffsetDateTime asOf, int intervalMinutes,
                                      java.util.function.Predicate<Observation> present,
                                      String label, List<String> outstanding) {
        OffsetDateTime last = ordered.stream().filter(present)
                .map(Observation::observedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (last == null) {
            outstanding.add(label + " has never been recorded in this session");
            return;
        }
        long minutes = Duration.between(last, asOf).toMinutes();
        if (minutes > intervalMinutes) {
            outstanding.add(label + " last recorded " + minutes + " minutes ago, against a "
                            + intervalMinutes + "-minute interval");
        }
    }

    private static BigDecimal hoursBetween(OffsetDateTime from, OffsetDateTime to) {
        long minutes = Duration.between(from, to).toMinutes();
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
}
