package zw.gov.mohcc.impilo.clinical.growth;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Interprets a child's growth trajectory and says what to do about it.
 *
 * <p><strong>Where the boundary sits.</strong> pct-service owns the growth record and stamps the
 * z-score at the moment of measurement, with the standard and engine version alongside it.
 * {@code libs/paediatric-domain} owns the arithmetic that produces those scores. This engine owns
 * neither: it reads scores that were already computed and decides what they mean and what should
 * happen next. It deliberately does not recompute anything, so a stored score and its
 * interpretation cannot drift apart, and a revision of the growth standard cannot silently
 * reinterpret a measurement taken years earlier.</p>
 *
 * <p><strong>Why interpretation is a separate thing from measurement.</strong> Growth faltering is
 * visible in the change between contacts long before any single measurement crosses a
 * classification boundary. A child tracking steadily down from the median is in trouble while every
 * individual weight still reads as normal. That is a judgement about a series, not a value, and it
 * is the part that governed content should own — because the thresholds and the actions are
 * clinical policy that changes without the arithmetic changing.</p>
 *
 * <p><strong>Data quality outranks clinical interpretation.</strong> Two signals block the rest:
 * an implausible jump between contacts, and the artefact created when a child switches from being
 * measured lying down to standing up. Acting on either would mean investigating a scale error as
 * if it were disease. The engine reports the blocking reason and issues no clinical signal, rather
 * than quietly reporting faltering that is not there.</p>
 *
 * <p><strong>Not every consecutive pair is a pair.</strong> A z-score is a position within a
 * reference population, and the population is not always the same one. An infant born preterm is
 * scored against Fenton 2013 on a postmenstrual axis until the preterm chart ends, and against the
 * WHO 2006 standard on corrected age thereafter. The step in z at that handover is a change of
 * reference, not a change in the child, and it is large enough to run in either direction: it can
 * manufacture faltering that is not there, or clear the implausible-jump threshold and block the
 * interpretation of faltering that is. The engine therefore refuses to subtract one z-score from
 * another that was read against a different standard, and says so, rather than reporting the
 * subtraction as a finding about the child. What remains commensurable across the handover —
 * weight in kilograms — is still interpreted, because a kilogram is a kilogram on any chart.</p>
 */
public final class GrowthIntelligenceEngine {

    /**
     * Standing height measures roughly 0.7 cm less than recumbent length in the same child, and
     * the WHO standards switch at two years to match. Around that switch an apparent loss of
     * stature is expected and means nothing.
     */
    private static final BigDecimal STATURE_ARTEFACT_TOLERANCE_Z = BigDecimal.valueOf(1.5);

    private GrowthIntelligenceEngine() {
    }

    /**
     * One growth contact, with scores as stamped at the time of measurement.
     *
     * @param statureMode           LENGTH or HEIGHT — which way the child was measured, needed to
     *                              recognise the artefact at the two-year switch
     * @param growthStandard        the reference identifier stamped alongside the scores by
     *                              pct-service — {@code WHO_2006_CHILD_GROWTH_STANDARDS} or
     *                              {@code FENTON_2013}. Held as an opaque label on purpose: this
     *                              engine needs to know only whether two contacts were read against
     *                              the same ruler, never how either ruler works
     * @param postmenstrualAgeWeeks completed weeks since the last menstrual period, where the score
     *                              was read on that axis rather than on age since birth; carried so
     *                              a refusal can say where on which chart each contact was read
     */
    public record Point(
            Integer ageDays,
            BigDecimal weightKg,
            BigDecimal weightForAgeZ,
            BigDecimal lengthHeightForAgeZ,
            BigDecimal headCircumferenceForAgeZ,
            String statureMode,
            String growthStandard,
            Integer postmenstrualAgeWeeks) {
    }

    public record Signal(
            String code,
            String name,
            String severity,
            String measure,
            BigDecimal zChange,
            Integer fromAgeDays,
            Integer toAgeDays,
            String action,
            boolean referralRequired,
            String rationale) {
    }

    /**
     * @param zTrendDerivable whether a change in z-score between the two most recent contacts could
     *                        legitimately be derived. False when there is no pair to compare, and
     *                        false when the pair was read against different growth standards — in
     *                        which case an absent faltering signal means "not asked", not "asked and
     *                        answered no", and a caller must not render it as reassurance
     */
    public record Assessment(
            boolean assessable,
            String notAssessableReason,
            boolean interpretationBlocked,
            boolean zTrendDerivable,
            List<Signal> signals,
            boolean referralRequired,
            int contactsConsidered,
            String contentVersion,
            String contentSource,
            String approvalStatus,
            String note) {

        public boolean anyConcern() {
            // Named exclusions rather than a list of concerning severities: an unrecognised severity
            // then reads as a concern, which errs towards a clinician looking at something that did
            // not need it rather than towards silence about something that did.
            return signals.stream().anyMatch(s -> !"REASSURING".equals(s.severity())
                                                  && !"DATA_QUALITY".equals(s.severity())
                                                  && !"NOT_DERIVABLE".equals(s.severity()));
        }
    }

    public static Assessment assess(GrowthIntelligenceContent content, List<Point> history) {
        List<Point> ordered = new ArrayList<>(history == null ? List.of() : history);
        ordered.removeIf(p -> p == null || p.ageDays() == null);
        ordered.sort(Comparator.comparing(Point::ageDays));

        if (ordered.size() < 2) {
            // A trend needs two points. Reporting "no concerns" from one measurement would be a
            // reassurance drawn from an absence of information.
            return new Assessment(false,
                    ordered.isEmpty()
                            ? "No growth measurements have been recorded for this child."
                            : "Only one growth measurement exists. Growth faltering is a change over "
                              + "time, so a single contact cannot show it — weigh again and reassess.",
                    false, false, List.of(), false, ordered.size(),
                    content.version(), content.provenance(), content.approvalStatus(),
                    "Not assessable as a trend.");
        }

        Point previous = ordered.get(ordered.size() - 2);
        Point latest = ordered.get(ordered.size() - 1);
        boolean commensurable = zScoresAreCommensurable(previous, latest);

        List<Signal> signals = new ArrayList<>();
        for (GrowthIntelligenceContent.SignalDefinition def : content.signals()) {
            Signal signal = evaluate(def, previous, latest, ordered, commensurable);
            if (signal != null) {
                signals.add(signal);
            }
        }
        signals.sort(Comparator.comparingInt(s -> priorityOf(content, s.code())));

        // Collapse each family to its most severe applicable signal. Faltering thresholds are a
        // severity ladder, not independent findings: a child crossing 1.5 z satisfies the severe
        // rule, the ordinary rule and the static-weight rule at once, and reporting all three
        // would bury the one action that matters under two weaker restatements of it.
        java.util.Set<String> familiesSeen = new java.util.LinkedHashSet<>();
        signals.removeIf(s -> {
            String family = familyOf(content, s.code());
            return family != null && !familiesSeen.add(family);
        });

        boolean blocked = signals.stream()
                .anyMatch(s -> content.blocksInterpretation(s.code()));
        if (blocked) {
            List<Signal> onlyBlocking = signals.stream()
                    .filter(s -> content.blocksInterpretation(s.code()))
                    .toList();
            return new Assessment(true, null, true, commensurable, onlyBlocking, false, ordered.size(),
                    content.version(), content.provenance(), content.approvalStatus(),
                    "The trend could not be interpreted because the measurements themselves are in "
                    + "question. Resolve that before drawing any conclusion about growth.");
        }

        boolean referral = signals.stream().anyMatch(Signal::referralRequired);
        // "No concern" is only sayable when the comparison was actually made. Across a change of
        // growth standard the z-trend was never derived, so the absence of a faltering signal is
        // silence, and reporting it as reassurance would be the same error as reassuring from a
        // single measurement.
        String note;
        if (!signals.isEmpty()) {
            note = null;
        } else if (commensurable) {
            note = "No growth concern identified across the contacts available.";
        } else {
            note = "No conclusion was drawn about the trend: the two most recent contacts were "
                   + "scored against different growth standards.";
        }
        return new Assessment(true, null, false, commensurable, List.copyOf(signals), referral,
                ordered.size(), content.version(), content.provenance(), content.approvalStatus(),
                note);
    }

    /**
     * Whether a z-score at one contact may be subtracted from a z-score at the other.
     *
     * <p>A z-score locates a child within a reference population. Two scores read against different
     * populations are not two readings on one scale, and their difference describes the distance
     * between the two references far more than anything the child did. Around 50 weeks postmenstrual
     * a preterm infant moves from the Fenton 2013 chart to the WHO 2006 standard; a step appears at
     * that contact whether the baby is thriving or not.</p>
     *
     * <p>Where either contact carries no standard the engine reports them as comparable, because an
     * unstamped record is unknown rather than changed, and refusing every history that predates the
     * stamp would withdraw growth interpretation from the children who most need it. The engine can
     * only see the boundaries it is told about: a caller that drops {@code growth_standard} from the
     * request gets the old, unaware behaviour back, which is why pct-service stamps it on every row
     * and the experience BFF carries it through under {@code derived.standard}.</p>
     */
    private static boolean zScoresAreCommensurable(Point previous, Point latest) {
        String before = normaliseStandard(previous.growthStandard());
        String after = normaliseStandard(latest.growthStandard());
        if (before == null || after == null) {
            return true;
        }
        return before.equals(after);
    }

    private static String normaliseStandard(String standard) {
        if (standard == null || standard.isBlank()) {
            return null;
        }
        return standard.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static Signal evaluate(GrowthIntelligenceContent.SignalDefinition def,
                                   Point previous, Point latest, List<Point> ordered,
                                   boolean commensurable) {
        if (def.maxAgeDays() != null && latest.ageDays() > def.maxAgeDays()) {
            return null;
        }

        if (def.standardChanged()) {
            return commensurable ? null : crossStandard(def, previous, latest);
        }
        if (def.statureModeChanged()) {
            // Also a claim about a difference in z, so it is withheld across a standard boundary
            // for the same reason the faltering thresholds are.
            return commensurable ? statureArtefact(def, previous, latest) : null;
        }
        if (def.absoluteWeightLoss()) {
            if (previous.weightKg() == null || latest.weightKg() == null
                || latest.weightKg().compareTo(previous.weightKg()) >= 0) {
                return null;
            }
            return signal(def, "WEIGHT", null, previous, latest,
                    "Weight fell from " + previous.weightKg() + " kg to " + latest.weightKg() + " kg.");
        }
        if (def.staticWeightMinDays() != null) {
            return staticWeight(def, ordered, latest);
        }

        // Everything below this line is a threshold on a difference between two z-scores. Where the
        // two contacts were read against different references there is no such difference to take —
        // including the implausible-jump guard, whose own threshold the handover step can clear.
        if (!commensurable) {
            return null;
        }

        BigDecimal from = measureOf(def.measure(), previous);
        BigDecimal to = measureOf(def.measure(), latest);
        if (from == null || to == null) {
            return null;
        }
        BigDecimal change = to.subtract(from);

        if (def.zChangeMagnitudeAtLeast() != null
            && change.abs().compareTo(def.zChangeMagnitudeAtLeast()) >= 0) {
            return signal(def, def.measure(), change, previous, latest,
                    "Score moved by " + change + " between contacts.");
        }
        if (def.zDropAtLeast() != null
            && change.negate().compareTo(def.zDropAtLeast()) >= 0) {
            return signal(def, def.measure(), change, previous, latest,
                    "Score fell by " + change.negate() + " between contacts, at or beyond the "
                    + def.zDropAtLeast() + " threshold for a significant downward crossing.");
        }
        if (def.zRiseAtLeast() != null && change.compareTo(def.zRiseAtLeast()) >= 0) {
            return signal(def, def.measure(), change, previous, latest,
                    "Score rose by " + change + " between contacts.");
        }
        return null;
    }

    /**
     * States that the z-trend was not derived, and where the reference changed.
     *
     * <p>Reported unconditionally when the standard differs, not only when a threshold would have
     * been crossed. Whether the withheld comparison would have raised faltering is exactly what the
     * engine does not know, and a silent omission would leave "no signals" meaning two different
     * things.</p>
     *
     * <p>No {@code measure} and no {@code zChange}: this is not a finding about weight or head
     * circumference in particular, it is the reason none of them were compared.</p>
     */
    private static Signal crossStandard(GrowthIntelligenceContent.SignalDefinition def,
                                        Point previous, Point latest) {
        return signal(def, null, null, previous, latest,
                "The earlier contact was scored against " + describeStandard(previous)
                + " and this one against " + describeStandard(latest)
                + ". A z-score is a position within a reference population, so the difference "
                + "between these two is a change of reference, not a change in the child.");
    }

    private static String describeStandard(Point p) {
        String standard = p.growthStandard();
        return p.postmenstrualAgeWeeks() == null
                ? standard
                : standard + " at " + p.postmenstrualAgeWeeks() + " weeks postmenstrual";
    }

    /**
     * The apparent stature loss when a child is first measured standing rather than lying down.
     * Only claimed when the mode actually changed and the drop is within the range the artefact
     * explains — a genuinely large fall around the second birthday is still faltering.
     */
    private static Signal statureArtefact(GrowthIntelligenceContent.SignalDefinition def,
                                          Point previous, Point latest) {
        if (previous.statureMode() == null || latest.statureMode() == null
            || previous.statureMode().equalsIgnoreCase(latest.statureMode())) {
            return null;
        }
        BigDecimal from = previous.lengthHeightForAgeZ();
        BigDecimal to = latest.lengthHeightForAgeZ();
        if (from == null || to == null || to.compareTo(from) >= 0) {
            return null;
        }
        BigDecimal drop = from.subtract(to);
        if (drop.compareTo(STATURE_ARTEFACT_TOLERANCE_Z) > 0) {
            // Too large for the artefact to explain; leave it to the faltering signals.
            return null;
        }
        return signal(def, "LENGTH_HEIGHT_FOR_AGE", to.subtract(from), previous, latest,
                "Measured as " + previous.statureMode().toLowerCase() + " then as "
                + latest.statureMode().toLowerCase() + ", with a fall of " + drop
                + " that the change of method explains.");
    }

    private static Signal staticWeight(GrowthIntelligenceContent.SignalDefinition def,
                                       List<Point> ordered, Point latest) {
        if (latest.weightKg() == null) {
            return null;
        }
        for (int i = ordered.size() - 2; i >= 0; i--) {
            Point earlier = ordered.get(i);
            if (earlier.weightKg() == null) {
                continue;
            }
            int days = latest.ageDays() - earlier.ageDays();
            if (days < def.staticWeightMinDays()) {
                continue;
            }
            // Reached far enough back: has any weight been gained across that span?
            if (latest.weightKg().compareTo(earlier.weightKg()) <= 0) {
                return signal(def, "WEIGHT", null, earlier, latest,
                        "Weight was " + earlier.weightKg() + " kg " + days + " days ago and is "
                        + latest.weightKg() + " kg now.");
            }
            return null;
        }
        return null;
    }

    private static BigDecimal measureOf(String measure, Point p) {
        if (measure == null) {
            return null;
        }
        return switch (measure) {
            case "WEIGHT_FOR_AGE" -> p.weightForAgeZ();
            case "LENGTH_HEIGHT_FOR_AGE" -> p.lengthHeightForAgeZ();
            case "HEAD_CIRCUMFERENCE_FOR_AGE" -> p.headCircumferenceForAgeZ();
            default -> null;
        };
    }

    private static Signal signal(GrowthIntelligenceContent.SignalDefinition def, String measure,
                                 BigDecimal change, Point from, Point to, String rationale) {
        return new Signal(def.code(), def.name(), def.severity(), measure, change,
                from.ageDays(), to.ageDays(), def.action(), def.referralRequired(), rationale);
    }

    private static String familyOf(GrowthIntelligenceContent content, String code) {
        // Resolve the definition first: mapping to family() before findFirst() throws when a
        // signal declares no family, which the data-quality signals deliberately do not.
        return content.signals().stream()
                .filter(d -> d.code().equals(code))
                .findFirst()
                .map(GrowthIntelligenceContent.SignalDefinition::family)
                .orElse(null);
    }

    private static int priorityOf(GrowthIntelligenceContent content, String code) {
        return content.signals().stream()
                .filter(d -> d.code().equals(code))
                .map(GrowthIntelligenceContent.SignalDefinition::priority)
                .findFirst().orElse(Integer.MAX_VALUE);
    }
}
