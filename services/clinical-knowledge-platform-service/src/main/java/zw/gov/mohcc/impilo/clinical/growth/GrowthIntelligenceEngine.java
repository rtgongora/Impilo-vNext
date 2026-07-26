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
     * @param statureMode LENGTH or HEIGHT — which way the child was measured, needed to recognise
     *                    the artefact at the two-year switch
     */
    public record Point(
            Integer ageDays,
            BigDecimal weightKg,
            BigDecimal weightForAgeZ,
            BigDecimal lengthHeightForAgeZ,
            BigDecimal headCircumferenceForAgeZ,
            String statureMode) {
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

    public record Assessment(
            boolean assessable,
            String notAssessableReason,
            boolean interpretationBlocked,
            List<Signal> signals,
            boolean referralRequired,
            int contactsConsidered,
            String contentVersion,
            String contentSource,
            String approvalStatus,
            String note) {

        public boolean anyConcern() {
            return signals.stream().anyMatch(s -> !"REASSURING".equals(s.severity())
                                                  && !"DATA_QUALITY".equals(s.severity()));
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
                    false, List.of(), false, ordered.size(),
                    content.version(), content.provenance(), content.approvalStatus(),
                    "Not assessable as a trend.");
        }

        Point previous = ordered.get(ordered.size() - 2);
        Point latest = ordered.get(ordered.size() - 1);

        List<Signal> signals = new ArrayList<>();
        for (GrowthIntelligenceContent.SignalDefinition def : content.signals()) {
            Signal signal = evaluate(def, previous, latest, ordered);
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
            return new Assessment(true, null, true, onlyBlocking, false, ordered.size(),
                    content.version(), content.provenance(), content.approvalStatus(),
                    "The trend could not be interpreted because the measurements themselves are in "
                    + "question. Resolve that before drawing any conclusion about growth.");
        }

        boolean referral = signals.stream().anyMatch(Signal::referralRequired);
        return new Assessment(true, null, false, List.copyOf(signals), referral, ordered.size(),
                content.version(), content.provenance(), content.approvalStatus(),
                signals.isEmpty() ? "No growth concern identified across the contacts available." : null);
    }

    private static Signal evaluate(GrowthIntelligenceContent.SignalDefinition def,
                                   Point previous, Point latest, List<Point> ordered) {
        if (def.maxAgeDays() != null && latest.ageDays() > def.maxAgeDays()) {
            return null;
        }

        if (def.statureModeChanged()) {
            return statureArtefact(def, previous, latest);
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
