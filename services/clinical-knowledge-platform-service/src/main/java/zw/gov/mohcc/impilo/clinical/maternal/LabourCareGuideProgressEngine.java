package zw.gov.mohcc.impilo.clinical.maternal;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Progress of labour under the WHO Labour Care Guide — the permissive replacement for the classic
 * partograph's 1 cm/hour alert line.
 *
 * <p>The classic partograph expected roughly one centimetre of cervical dilation an hour and called
 * a labour obstructed when it fell behind. The evidence was that this labelled a great many normal
 * labours as abnormal and drove avoidable caesareans, and the Labour Care Guide abandoned the fixed
 * rate line for that reason. This engine is the LCG reading: it recognises slow progress, but its
 * response is to look for the cause and support the woman — never to treat slowness itself as a
 * reason to operate.
 *
 * <p><b>The invariant, and it is not a threshold.</b> No verdict this engine produces ever
 * recommends a caesarean for slow progress. Slow progress opens an assessment — of the powers
 * (contractions), the passage (pelvis) and the passenger (position, size) — and offers support,
 * hydration and companionship. A decision to operate comes from that assessment or from the danger-
 * sign layer, never from the progress line alone. The exact dilation reference below is an
 * engineering seed pending ratification against the primary text; the invariant does not depend on
 * it and holds at every threshold.
 *
 * <p><b>Silence is not reassurance.</b> With no cervical dilation recorded the verdict is
 * {@link Status#INSUFFICIENT_DATA}, never {@link Status#PROGRESSING}. An unassessed labour is a more
 * urgent finding than a slow one, and must never render as the reassuring case.
 *
 * <p>Pure and {@code asOf}-parameterised, so a verdict reproduces exactly on review. Carries its
 * {@code monitoringStandard} so a consumer can assert exactly one standard governs the labour — and
 * that it is the Guide, never the demoted classic partograph.
 */
public final class LabourCareGuideProgressEngine {

    public static final String MONITORING_STANDARD = "WHO_LABOUR_CARE_GUIDE";
    public static final String CONTENT_VERSION = "who-labour-care-guide-progress-seed-1.0.0";

    private LabourCareGuideProgressEngine() {
    }

    public enum Status {
        /** Dilating at or above the LCG reference for the time in active labour. */
        PROGRESSING,
        /**
         * Below the reference — slower than expected. This opens an assessment of the cause and a
         * plan of support; it is NOT, on its own, an indication for caesarean section.
         */
        SLOW_PROGRESS_ASSESS,
        /** Before active first stage (below 5 cm): slow progress is expected and is not plotted. */
        LATENT_PHASE,
        /** Fully dilated — the progress question moves to the second stage. */
        SECOND_STAGE,
        /** Not enough recorded to say. Never the reassuring case. */
        INSUFFICIENT_DATA
    }

    /**
     * The LCG progress reference. Active first stage begins at 5 cm (the Guide's threshold, not the
     * classic 4 cm), and the expected rate is deliberately more permissive than the classic
     * 1 cm/hour. Both are engineering seeds pending ratification; they are held here rather than
     * inlined so a ministry parameter can replace them without touching the invariant.
     */
    public record Reference(double activeFirstStageCm, double minimumExpectedCmPerHour) {
        public static Reference seed() {
            // 0.5 cm/hour is deliberately gentler than the classic 1 cm/hour — the whole point of
            // the LCG is that the old rate was too fast and pathologised normal labour.
            return new Reference(5.0, 0.5);
        }
    }

    public record Observation(OffsetDateTime observedAt, Double cervicalDilationCm) {
    }

    public record Assessment(
            Status status,
            String monitoringStandard,
            Double latestDilationCm,
            OffsetDateTime latestDilationAt,
            String action,
            List<String> notes,
            String contentVersion) {
    }

    public static Assessment assess(List<Observation> observations, OffsetDateTime asOf, Reference ref) {
        Reference reference = ref == null ? Reference.seed() : ref;
        List<Observation> dilations = new ArrayList<>(observations == null ? List.of() : observations);
        dilations.removeIf(o -> o == null || o.observedAt() == null || o.cervicalDilationCm() == null);
        dilations.sort(Comparator.comparing(Observation::observedAt));

        if (dilations.isEmpty()) {
            return new Assessment(Status.INSUFFICIENT_DATA, MONITORING_STANDARD, null, null,
                    "Assess and record cervical dilation. No dilation has been recorded, so progress "
                            + "cannot be assessed — this is not a reassuring result.",
                    List.of("No cervical dilation recorded in this session."), CONTENT_VERSION);
        }

        Observation latest = dilations.get(dilations.size() - 1);
        double latestCm = latest.cervicalDilationCm();

        if (latestCm >= 10.0) {
            return new Assessment(Status.SECOND_STAGE, MONITORING_STANDARD, latestCm, latest.observedAt(),
                    "Second stage: support pushing and monitor the fetal heart every five minutes.",
                    List.of("Fully dilated; the first-stage progress question no longer applies."),
                    CONTENT_VERSION);
        }

        Observation activeStart = dilations.stream()
                .filter(o -> o.cervicalDilationCm() >= reference.activeFirstStageCm())
                .findFirst()
                .orElse(null);

        if (activeStart == null) {
            return new Assessment(Status.LATENT_PHASE, MONITORING_STANDARD, latestCm, latest.observedAt(),
                    "Latent phase (below " + reference.activeFirstStageCm() + " cm): support the woman "
                            + "and reassess. Slow progress here is expected and is not plotted for "
                            + "action.",
                    List.of("Active first stage not yet reached."), CONTENT_VERSION);
        }

        double hours = Math.max(0.0,
                Duration.between(activeStart.observedAt(), latest.observedAt()).toMinutes() / 60.0);
        double expected = Math.min(10.0,
                activeStart.cervicalDilationCm() + reference.minimumExpectedCmPerHour() * hours);

        List<String> notes = new ArrayList<>();
        notes.add(String.format(
                "Dilation %.1f cm at %.1f h into active labour, against an LCG reference of %.1f cm "
                        + "(%.2f cm/h from %.1f cm).",
                latestCm, hours, expected, reference.minimumExpectedCmPerHour(),
                activeStart.cervicalDilationCm()));

        if (latestCm + 1e-9 >= expected) {
            return new Assessment(Status.PROGRESSING, MONITORING_STANDARD, latestCm, latest.observedAt(),
                    "Progress is within the Labour Care Guide reference. Continue routine LCG "
                            + "observations and support.",
                    notes, CONTENT_VERSION);
        }

        // Below the reference — and this is the whole reason the LCG exists. The action is
        // assessment and support, and it says in as many words that slow progress alone is not a
        // reason to operate, so no consumer can read this verdict as a caesarean trigger.
        return new Assessment(Status.SLOW_PROGRESS_ASSESS, MONITORING_STANDARD, latestCm, latest.observedAt(),
                "Slower than the Labour Care Guide reference. Assess the cause — the powers "
                        + "(contractions), the passage (pelvis) and the passenger (position and size) "
                        + "— offer support, hydration and companionship, and reassess. Slow progress "
                        + "alone is not an indication for caesarean section.",
                notes, CONTENT_VERSION);
    }
}
