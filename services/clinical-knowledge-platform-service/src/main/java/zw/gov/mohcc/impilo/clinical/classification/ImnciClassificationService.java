package zw.gov.mohcc.impilo.clinical.classification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs the IMNCI assess-and-classify tables for a sick child and composes the result.
 *
 * <p>IMNCI's power is that it is <em>integrated</em>: a child is assessed for every condition on
 * every visit, not only for the complaint the carer mentioned. A child brought for cough is also
 * checked for dehydration, malnutrition and anaemia, because the thing that kills the child is
 * often not the thing that brought them. So this service always runs every table that applies to
 * the child's age, and reports the ones that were not applicable as explicitly not applicable
 * rather than omitting them.</p>
 *
 * <p><strong>A classification is not a diagnosis.</strong> The result deliberately carries no
 * diagnosis field. IMNCI classifications are management categories chosen to select treatment and
 * disposition from signs a health worker can elicit without investigations; several of them span
 * multiple diseases by design. Writing one into the record as a diagnosis would assert something
 * no clinician stated. Where a diagnosis is later made, it belongs in a separate field, which is
 * why the encounter form captures {@code guidelineClassification} and {@code workingDiagnosis}
 * separately.</p>
 */
@Service
public class ImnciClassificationService {

    private static final Logger log = LoggerFactory.getLogger(ImnciClassificationService.class);

    static final String CONTENT_PATH = "clinical/imnci-classification-tables.json";
    static final String YOUNG_INFANT_CONTENT_PATH = "clinical/imnci-young-infant-tables.json";

    /**
     * IMNCI splits at two months, and the split is not cosmetic. A young infant's signs of serious
     * illness are few, non-specific and different from an older child's — poor feeding and
     * hypothermia rather than cough and fast breathing at the child thresholds — and deterioration
     * is far quicker. Applying the 2-months-to-5-years tables to a newborn would miss most of what
     * is dangerous about being a newborn.
     */
    private static final int YOUNG_INFANT_MAX_AGE_DAYS = 59;

    private final ClassificationContentLoader contentLoader;

    public ImnciClassificationService(ClassificationContentLoader contentLoader) {
        this.contentLoader = contentLoader;
    }

    /**
     * @param urgentReferralRequired any table classified PINK — the child needs referral now
     * @param incomplete             at least one table could not be resolved; a treatment plan
     *                               built on this is provisional
     * @param outstandingAssessments what to complete, deduplicated across tables
     */
    public record Assessment(
            Integer ageDays,
            String pathway,
            boolean applicable,
            List<ClassificationEngine.Outcome> classifications,
            boolean urgentReferralRequired,
            boolean referralRequired,
            boolean incomplete,
            List<String> outstandingAssessments,
            List<String> unavailableCriteria,
            List<String> treatmentPlan,
            String disposition,
            String contentVersion,
            String contentSource,
            String approvalStatus,
            String note) {
    }

    public Assessment evaluate(Integer ageDays, Map<String, Object> facts) {
        // The pack is chosen by age before anything else, because the young infant and the older
        // child are assessed for different things, not merely at different thresholds.
        boolean youngInfant = ageDays != null && ageDays <= YOUNG_INFANT_MAX_AGE_DAYS;
        ClassificationContentLoader.Pack pack =
                contentLoader.load(youngInfant ? YOUNG_INFANT_CONTENT_PATH : CONTENT_PATH);

        if (ageDays == null) {
            return unusable(null, pack,
                    "Age is unknown. Every IMNCI table is age-scoped and the fast-breathing "
                    + "threshold changes at one year, so no table can be applied safely.");
        }
        Map<String, Object> flat = flatten(facts);
        flat.putIfAbsent("ageDays", ageDays);

        List<ClassificationEngine.Outcome> outcomes = new ArrayList<>();
        for (ClassificationTable table : pack.tables()) {
            if (!table.appliesToAge(ageDays)) {
                continue;
            }
            outcomes.add(ClassificationEngine.classify(table, ageDays, flat));
        }

        boolean urgent = outcomes.stream().anyMatch(o -> "PINK".equals(o.colour()));
        boolean referral = outcomes.stream().anyMatch(ClassificationEngine.Outcome::referralRequired);
        boolean incomplete = outcomes.stream().anyMatch(o ->
                o.status() == ClassificationEngine.Status.INDETERMINATE
                || o.status() == ClassificationEngine.Status.NOT_ASSESSED
                || o.provisional());

        Set<String> outstanding = new LinkedHashSet<>();
        outcomes.forEach(o -> outstanding.addAll(o.missingInputs()));

        // Criteria the content declares optional and that were unavailable here. Reported so the
        // record shows what this facility could not measure, rather than implying it was measured
        // and normal — a clinic without a height board should be visible as such.
        Set<String> unavailable = new LinkedHashSet<>();
        outcomes.forEach(o -> unavailable.addAll(o.waivedCriteria()));

        // The plan is ordered by severity, because a health worker reads from the top and the
        // pink actions are the ones that must happen before the child leaves the room.
        List<String> plan = new ArrayList<>();
        addTreatments(plan, outcomes, "PINK");
        addTreatments(plan, outcomes, "YELLOW");
        addTreatments(plan, outcomes, "GREEN");

        if (incomplete) {
            log.info("IMNCI classification incomplete for a child of {} days: {} outstanding assessment(s)",
                    ageDays, outstanding.size());
        }

        return new Assessment(
                ageDays,
                youngInfant ? "SICK_YOUNG_INFANT_0_TO_2_MONTHS" : "SICK_CHILD_2_MONTHS_TO_5_YEARS",
                true,
                List.copyOf(outcomes),
                urgent,
                referral,
                incomplete,
                List.copyOf(outstanding),
                List.copyOf(unavailable),
                List.copyOf(plan),
                disposition(urgent, referral, incomplete),
                pack.version(),
                pack.provenance(),
                pack.approvalStatus(),
                incomplete
                        ? "One or more classifications could not be completed. Treat this as a partial "
                          + "assessment: the classifications shown are the least severe this child "
                          + "could have, not a conclusion."
                        : null);
    }

    private static String disposition(boolean urgent, boolean referral, boolean incomplete) {
        if (urgent) {
            return "REFER_URGENTLY";
        }
        if (referral) {
            return "REFER";
        }
        // An incomplete assessment is not a discharge. Saying "treat and go home" on the strength
        // of questions nobody asked is the failure this whole engine is built to prevent.
        return incomplete ? "COMPLETE_ASSESSMENT_BEFORE_DISPOSITION" : "TREAT_AT_FACILITY";
    }

    private static void addTreatments(List<String> plan, List<ClassificationEngine.Outcome> outcomes,
                                      String colour) {
        outcomes.stream()
                .filter(o -> colour.equals(o.colour()))
                .forEach(o -> o.treatments().forEach(t -> {
                    if (!plan.contains(t)) {
                        plan.add(t);
                    }
                }));
    }

    private Assessment unusable(Integer ageDays, ClassificationContentLoader.Pack pack, String note) {
        return new Assessment(ageDays, null, false, List.of(), false, false, true, List.of(), List.of(), List.of(),
                "COMPLETE_ASSESSMENT_BEFORE_DISPOSITION",
                pack.version(), pack.provenance(), pack.approvalStatus(), note);
    }

    /**
     * Flattens nested fact objects to dotted keys so content can address {@code vitals.temperature}
     * whether the caller sent it nested or flat. Both shapes arrive in practice — the encounter
     * form nests vitals, while an offline client posts them flat.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> flatten(Map<String, Object> facts) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        if (facts == null) {
            return out;
        }
        facts.forEach((key, value) -> {
            if (value instanceof Map<?, ?> nested) {
                out.put(key, value);
                ((Map<String, Object>) nested).forEach((k, v) -> out.put(key + "." + k, v));
            } else {
                out.put(key, value);
            }
        });
        return out;
    }

    /** Exposed for the content-fixture gate and the catalogue endpoint. */
    public ClassificationContentLoader.Pack pack() {
        return contentLoader.load(CONTENT_PATH);
    }
}
