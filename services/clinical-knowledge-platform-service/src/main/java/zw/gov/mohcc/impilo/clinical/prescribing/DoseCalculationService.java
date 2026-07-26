package zw.gov.mohcc.impilo.clinical.prescribing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calculates weight-based paediatric doses from governed content.
 *
 * <p>Paediatric dosing is the highest-risk arithmetic in clinical practice: the same medicine
 * spans a hundred-fold weight range, the dose is computed under time pressure, and a decimal
 * point in the wrong place is a lethal error that looks entirely ordinary on the page. The
 * platform previously had no dose calculator at all — one hard-coded gentamicin band table
 * returning a string, and free-text dose expressions everywhere else.</p>
 *
 * <p>The safety properties here are structural, not advisory:</p>
 * <ul>
 *   <li><strong>No dose without a verified weight and a known age.</strong> Both are required;
 *       there is no fallback to an estimate, because an estimated weight silently becomes an
 *       estimated dose.</li>
 *   <li><strong>Unknown or ambiguous means no answer.</strong> If no rule matches, or more than
 *       one does, the service says so and calculates nothing. Guessing between two rules is
 *       how a child receives an adult dose.</li>
 *   <li><strong>Maxima are enforced, and the cap is visible.</strong> A calculated dose above
 *       the maximum is reduced to the maximum and reported as capped, so the clinician sees
 *       that the weight-based figure exceeded the ceiling.</li>
 *   <li><strong>Nothing is prescribed.</strong> The output is a suggestion with the arithmetic
 *       shown and the source named, for a clinician to review and authorise.</li>
 * </ul>
 *
 * <p><strong>Engineering seed:</strong> every rule in the shipped content requires MoHCC and
 * paediatric specialist ratification before it is used to prescribe.</p>
 */
@Service
public class DoseCalculationService {

    private static final Logger log = LoggerFactory.getLogger(DoseCalculationService.class);

    static final String CONTENT_PATH = "clinical/paediatric-dosing-rules.json";

    private final List<DosingRule> rules;
    private final String contentVersion;

    public DoseCalculationService(ObjectMapper objectMapper) {
        JsonNode root = read(objectMapper);
        this.contentVersion = root.path("version").asText("unknown");
        this.rules = parseRules(root, objectMapper);
        log.info("Loaded paediatric dosing content version={} rules={}", contentVersion, rules.size());
    }

    public List<DosingRule> rules() {
        return rules;
    }

    public String contentVersion() {
        return contentVersion;
    }

    /**
     * @param medicine   generic name
     * @param indication optional indication code, when the dose depends on what is being treated
     * @param ageDays    age in days; required
     * @param weightKg   the weight measured at this contact; required
     */
    public DoseSuggestion calculate(String medicine, String indication, Integer ageDays, Double weightKg) {
        if (medicine == null || medicine.isBlank()) {
            return DoseSuggestion.notCalculated("NO_MEDICINE", "No medicine was specified.", contentVersion);
        }
        if (weightKg == null || weightKg <= 0) {
            return DoseSuggestion.notCalculated("NO_VERIFIED_WEIGHT",
                    "A paediatric dose cannot be calculated without the child's weight. Weigh the child"
                            + " and record the weight before prescribing.", contentVersion);
        }
        if (ageDays == null) {
            return DoseSuggestion.notCalculated("NO_AGE",
                    "A paediatric dose cannot be calculated without the child's age: dosing rules differ"
                            + " by age even at the same weight.", contentVersion);
        }

        List<DosingRule> matches = rules.stream()
                .filter(rule -> rule.matches(medicine, indication, ageDays, weightKg))
                .toList();

        if (matches.isEmpty()) {
            return DoseSuggestion.notCalculated("NO_DOSING_RULE",
                    "No approved paediatric dosing rule covers " + medicine + " at this age and weight."
                            + " Dose from the national formulary and record the basis.", contentVersion);
        }
        if (matches.size() > 1) {
            // Choosing between rules would be a clinical judgement the content did not make.
            List<String> codes = matches.stream().map(DosingRule::code).toList();
            return DoseSuggestion.notCalculated("AMBIGUOUS_DOSING_RULE",
                    "More than one dosing rule matches (" + String.join(", ", codes)
                            + "). Specify the indication so the correct rule can be applied.", contentVersion);
        }

        return apply(matches.get(0), weightKg);
    }

    private DoseSuggestion apply(DosingRule rule, double weightKg) {
        boolean weightBased = rule.mgPerKgPerDose() != null;
        double rawMg = weightBased
                ? rule.mgPerKgPerDose() * weightKg
                : (rule.fixedDoseMg() == null ? 0 : rule.fixedDoseMg());

        if (rawMg <= 0) {
            return DoseSuggestion.notCalculated("INCOMPLETE_DOSING_RULE",
                    "Dosing rule " + rule.code() + " specifies neither a per-kilogram nor a fixed dose.",
                    contentVersion);
        }

        double rounded = round(rawMg, rule.roundToMg());

        boolean cappedBySingle = false;
        if (rule.maxSingleDoseMg() != null && rounded > rule.maxSingleDoseMg()) {
            rounded = rule.maxSingleDoseMg();
            cappedBySingle = true;
        }

        boolean cappedByDaily = false;
        int dosesPerDay = rule.dosesPerDay() == null ? 1 : rule.dosesPerDay();
        if (rule.maxDailyDoseMg() != null && rounded * dosesPerDay > rule.maxDailyDoseMg()) {
            rounded = round(rule.maxDailyDoseMg() / dosesPerDay, rule.roundToMg());
            cappedByDaily = true;
        }

        List<AdministrableVolume> volumes = new ArrayList<>();
        for (DosingRule.Concentration concentration : rule.concentrations()) {
            if (!concentration.liquid()) {
                continue;
            }
            double ml = rounded / concentration.mgPerMl();
            volumes.add(new AdministrableVolume(
                    concentration.label(),
                    BigDecimal.valueOf(ml).setScale(1, RoundingMode.HALF_UP).doubleValue()));
        }

        String basis = basis(rule, weightKg, rawMg, rounded, dosesPerDay, cappedBySingle || cappedByDaily);

        return new DoseSuggestion(true, rule.code(), rule.genericName(), rule.route(),
                rounded, dosesPerDay, intervalHours(dosesPerDay), rule.durationDays(),
                round(rounded * dosesPerDay, rule.roundToMg()),
                cappedBySingle || cappedByDaily, volumes, basis,
                rule.specialistOnly(), rule.renalCaution(), rule.monitoring(),
                rule.sourceRefs(), rule.approvalStatus(), rule.contentVersion(), null, null);
    }

    /**
     * The arithmetic, in words. A suggestion a clinician cannot check is a suggestion they
     * have to take on trust, which is the opposite of what a dose calculator is for.
     */
    private String basis(DosingRule rule, double weightKg, double rawMg, double finalMg,
                         int dosesPerDay, boolean capped) {
        StringBuilder text = new StringBuilder();
        if (rule.mgPerKgPerDose() != null) {
            text.append(trim(rule.mgPerKgPerDose())).append(" mg/kg/dose × ")
                    .append(trim(weightKg)).append(" kg = ").append(trim(rawMg)).append(" mg");
            if (Math.abs(rawMg - finalMg) > 0.001) {
                text.append(capped ? ", capped at " : ", rounded to ").append(trim(finalMg)).append(" mg");
            }
        } else {
            text.append(trim(finalMg)).append(" mg fixed dose (does not vary with weight)");
        }
        text.append("; ").append(dosesPerDay).append(dosesPerDay == 1 ? " dose" : " doses").append(" per day");
        if (rule.durationDays() != null) {
            text.append(" for ").append(rule.durationDays()).append(" days");
        }
        text.append(". Suggested from ").append(rule.code())
                .append(" (").append(rule.approvalStatus()).append(", version ")
                .append(rule.contentVersion()).append("). Review and authorise.");
        return text.toString();
    }

    private static Integer intervalHours(int dosesPerDay) {
        return dosesPerDay <= 0 ? null : 24 / dosesPerDay;
    }

    private static double round(double value, Double increment) {
        if (increment == null || increment <= 0) {
            return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
        }
        double rounded = Math.round(value / increment) * increment;
        return BigDecimal.valueOf(rounded).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }

    private static String trim(double value) {
        BigDecimal decimal = BigDecimal.valueOf(value).stripTrailingZeros();
        return decimal.scale() <= 0 ? decimal.toBigInteger().toString() : decimal.toPlainString();
    }

    private JsonNode read(ObjectMapper objectMapper) {
        try (InputStream stream = DoseCalculationService.class.getClassLoader().getResourceAsStream(CONTENT_PATH)) {
            if (stream == null) {
                throw new IllegalStateException("Paediatric dosing content not found: " + CONTENT_PATH);
            }
            return objectMapper.readTree(stream);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load paediatric dosing content", e);
        }
    }

    private List<DosingRule> parseRules(JsonNode root, ObjectMapper objectMapper) {
        String packApproval = root.path("approvalStatus").asText("ENGINEERING_SEED");
        String authority = root.path("adaptationAuthority").asText(null);
        String version = root.path("version").asText("unknown");

        List<DosingRule> parsed = new ArrayList<>();
        for (JsonNode node : root.path("rules")) {
            String code = node.path("code").asText(null);
            String generic = node.path("genericName").asText(null);
            if (code == null || generic == null) {
                log.warn("Rejecting dosing rule without a code or generic name");
                continue;
            }
            List<DosingRule.Concentration> concentrations = new ArrayList<>();
            for (JsonNode concentration : node.path("concentrations")) {
                concentrations.add(new DosingRule.Concentration(
                        concentration.path("label").asText(""),
                        concentration.path("mgPerMl").asDouble(0)));
            }
            List<DosingRule.TestCase> testCases = new ArrayList<>();
            for (JsonNode testCase : node.path("testCases")) {
                testCases.add(new DosingRule.TestCase(
                        testCase.path("name").asText("unnamed"),
                        testCase.hasNonNull("ageDays") ? testCase.get("ageDays").asInt() : null,
                        testCase.hasNonNull("weightKg") ? testCase.get("weightKg").asDouble() : null,
                        testCase.hasNonNull("expectMgPerDose") ? testCase.get("expectMgPerDose").asDouble() : null,
                        testCase.path("expectCapped").asBoolean(false)));
            }
            parsed.add(new DosingRule(
                    code, generic,
                    node.path("atcCode").asText(null),
                    node.path("indication").asText(null),
                    node.path("route").asText(null),
                    optionalInt(node, "ageMinDays"), optionalInt(node, "ageMaxDays"),
                    optionalDouble(node, "weightMinKg"), optionalDouble(node, "weightMaxKg"),
                    optionalDouble(node, "mgPerKgPerDose"), optionalDouble(node, "fixedDoseMg"),
                    optionalInt(node, "dosesPerDay"),
                    optionalDouble(node, "maxSingleDoseMg"), optionalDouble(node, "maxDailyDoseMg"),
                    optionalInt(node, "durationDays"), optionalDouble(node, "roundToMg"),
                    node.path("specialistOnly").asBoolean(false),
                    node.path("renalCaution").asBoolean(false),
                    List.copyOf(concentrations),
                    node.path("monitoring").asText(null),
                    strings(node.path("sourceRefs")),
                    node.path("approvalStatus").asText(packApproval),
                    node.path("adaptationAuthority").asText(authority),
                    version,
                    List.copyOf(testCases)));
        }
        return List.copyOf(parsed);
    }

    private static List<String> strings(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(entry -> values.add(entry.asText()));
        }
        return List.copyOf(values);
    }

    private static Integer optionalInt(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asInt() : null;
    }

    private static Double optionalDouble(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asDouble() : null;
    }

    /**
     * @param capped          the weight-based figure exceeded a maximum and was reduced
     * @param basis           the arithmetic in words, for the clinician to check
     * @param notCalculatedReason machine-readable reason when no dose could be produced
     */
    public record DoseSuggestion(
            boolean calculated,
            String ruleCode,
            String genericName,
            String route,
            Double doseMg,
            Integer dosesPerDay,
            Integer intervalHours,
            Integer durationDays,
            Double dailyDoseMg,
            boolean capped,
            List<AdministrableVolume> volumes,
            String basis,
            boolean specialistOnly,
            boolean renalCaution,
            String monitoring,
            List<String> sourceRefs,
            String approvalStatus,
            String contentVersion,
            String notCalculatedReason,
            String explanation) {

        static DoseSuggestion notCalculated(String reason, String explanation, String contentVersion) {
            return new DoseSuggestion(false, null, null, null, null, null, null, null, null,
                    false, List.of(), null, false, false, null, List.of(), null, contentVersion,
                    reason, explanation);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("calculated", calculated);
            if (!calculated) {
                map.put("reason", notCalculatedReason);
                map.put("explanation", explanation);
                map.put("content_version", contentVersion);
                return map;
            }
            map.put("rule_code", ruleCode);
            map.put("generic_name", genericName);
            map.put("route", route);
            map.put("dose_mg", doseMg);
            map.put("doses_per_day", dosesPerDay);
            map.put("interval_hours", intervalHours);
            map.put("duration_days", durationDays);
            map.put("daily_dose_mg", dailyDoseMg);
            map.put("capped_at_maximum", capped);
            map.put("volumes", volumes.stream().map(AdministrableVolume::toMap).toList());
            map.put("basis", basis);
            map.put("specialist_only", specialistOnly);
            map.put("renal_caution", renalCaution);
            map.put("monitoring", monitoring);
            map.put("source_refs", sourceRefs);
            map.put("approval_status", approvalStatus);
            map.put("content_version", contentVersion);
            map.put("requires_authorisation", true);
            return map;
        }
    }

    /** A dose expressed as something that can actually be drawn up or measured. */
    public record AdministrableVolume(String preparation, double millilitres) {
        public Map<String, Object> toMap() {
            return Map.of("preparation", preparation, "millilitres", millilitres);
        }
    }
}
